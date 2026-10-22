# Runs Spona in Docker, and starts Docker Desktop first when it is not running.
# With another engine (Docker in WSL, Rancher Desktop, a remote host) start that engine yourself first.
# ./spona.ps1 help lists the commands. The bash twin for macOS and Linux is spona.sh.

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('all', 'backend', 'frontend', 'services', 'down', 'logs', 'ps', 'help')]
    [string] $Command = 'all',

    [Parameter(Position = 1, ValueFromRemainingArguments = $true)]
    [string[]] $Service = @()
)

$usage = @'
Usage: ./spona.ps1 [command]

  all        (default) everything in Docker: Postgres, MinIO, Mailpit, backend, frontend
  backend    backend and services in Docker, you run the frontend: npm run dev --prefix frontend
  frontend   frontend and services in Docker, you run the backend: cd backend; ./mvnw spring-boot:run
  services   only Postgres, MinIO and Mailpit, you run both
  down       stops and removes the containers, the data stays in the volumes
  logs       follows the logs, of everything or of the services named: ./spona.ps1 logs backend
  ps         lists the containers
'@

$compose = @('compose', '--file', (Join-Path $PSScriptRoot 'compose.yaml'))
$everything = @('--profile', 'backend', '--profile', 'frontend')
$apps = @{
    all      = @('backend', 'frontend')
    backend  = @('backend')
    frontend = @('frontend')
    services = @()
}

function Fail([string] $Message) {
    Write-Host $Message -ForegroundColor Red
    exit 1
}

function Invoke-Compose([string[]] $Arguments) {
    & docker @compose @Arguments
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Test-Engine {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { return $false }
    $ErrorActionPreference = 'SilentlyContinue'
    docker info *> $null
    $LASTEXITCODE -eq 0
}

function Get-Endpoint {
    $ErrorActionPreference = 'SilentlyContinue'
    docker context inspect --format '{{.Endpoints.docker.Host}}' 2>$null
}

function Assert-Docker {
    $ErrorActionPreference = 'SilentlyContinue'
    if ((docker info --format '{{.OSType}}' 2>$null) -eq 'windows') {
        Fail 'Docker Desktop is running Windows containers. Switch it to Linux containers from its tray icon, then run this again.'
    }
    $version = (docker compose version --short 2>$null) -replace '^v' -replace '[-+].*$'
    if (-not ($version -as [version]) -or [version]$version -lt [version]'2.24') {
        Fail 'Docker Compose 2.24 or newer is needed, update Docker: https://docs.docker.com/get-started/get-docker/'
    }
}

function Start-Engine {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Fail 'Docker is not installed: https://docs.docker.com/get-started/get-docker/' }
    if (Test-Engine) { return }

    # Starting Docker Desktop only helps when the CLI talks to Docker Desktop
    $endpoint = Get-Endpoint
    if ($endpoint -notmatch '^npipe:////\./pipe/(docker_engine|dockerDesktop\w*Engine)$') {
        if (-not $endpoint) { $endpoint = 'the current context' }
        Fail "Docker at $endpoint is not running. Start that engine first, or switch to Docker Desktop: docker context use desktop-linux"
    }

    $installed = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Docker Desktop' -ErrorAction SilentlyContinue).InstallLocation
    $desktop = @(
        $(if ($installed) { Join-Path $installed 'Docker Desktop.exe' }),
        (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe')
    ) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $desktop) { Fail 'The Docker engine is not running and Docker Desktop was not found. Start Docker, then run this again.' }

    Write-Host 'Starting Docker Desktop...'
    Start-Process $desktop
    $deadline = (Get-Date).AddMinutes(3)
    do {
        if ((Get-Date) -gt $deadline) { Fail 'Docker Desktop did not come up within 3 minutes.' }
        Start-Sleep -Seconds 3
    } until (Test-Engine)
}

if ($Command -eq 'help') {
    $usage
    exit 0
}

if ($Command -in 'down', 'logs', 'ps') {
    if (-not (Test-Engine)) {
        Write-Host 'Docker is not running, so neither is Spona.'
        exit 0
    }
    Assert-Docker
    switch ($Command) {
        'down' { Invoke-Compose ($everything + 'down') }
        'logs' { Invoke-Compose ($everything + 'logs' + '--follow' + $Service) }
        'ps' { Invoke-Compose ($everything + 'ps') }
    }
    exit 0
}

Start-Engine
Assert-Docker

$wanted = $apps[$Command]
$idle = @('backend', 'frontend') | Where-Object { $wanted -notcontains $_ }
if ($idle) {
    # Another mode may have left them running, on the ports the copy you start by hand needs
    Invoke-Compose ($everything + 'rm' + '--stop' + '--force' + $idle)
}

Write-Host 'Building and starting the containers. The first run downloads a lot and takes a few minutes.'
$profiles = foreach ($app in $wanted) { '--profile'; $app }
# Without build attestations an unchanged image keeps its ID, so compose leaves its running container alone
$attestations = $env:BUILDX_NO_DEFAULT_ATTESTATIONS
try {
    $env:BUILDX_NO_DEFAULT_ATTESTATIONS = '1'
    & docker @compose @profiles up --detach --build --wait
} finally {
    $env:BUILDX_NO_DEFAULT_ATTESTATIONS = $attestations
}
if ($LASTEXITCODE -ne 0) { Fail 'Spona did not start. See why with: ./spona.ps1 logs' }

$running = switch ($Command) {
    'all' { 'Spona is running' }
    'services' { 'The services are running' }
    default { "The $Command and the services are running" }
}
Write-Host ''
Write-Host $running -ForegroundColor Green
Write-Host '  app       http://localhost:5173   (demo sign-in: ana-petrovic / spona1234)'
Write-Host '  api       http://localhost:8080/api'
Write-Host '  mail      http://localhost:8025'
Write-Host '  storage   http://localhost:9001   (spona / spona-secret)'
Write-Host '  database  localhost:5432, database spona, spona / spona'

$todo = @()
if ($wanted -notcontains 'backend') { $todo += 'cd backend; ./mvnw spring-boot:run' }
if ($wanted -notcontains 'frontend') { $todo += 'npm run dev --prefix frontend' }
if ($todo) {
    Write-Host ''
    Write-Host 'Now start, each in its own terminal from the project root:' -ForegroundColor Yellow
    $todo | ForEach-Object { Write-Host "  $_" }
}
Write-Host ''
Write-Host 'Logs: ./spona.ps1 logs [service]   Stop: ./spona.ps1 down'
