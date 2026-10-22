#!/usr/bin/env bash
# Runs Spona in Docker, and starts Docker Desktop first when it is not running.
# With another engine (Colima, OrbStack, a remote host) start that engine yourself first.
# ./spona.sh help lists the commands. The Windows twin is spona.ps1.
set -eo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Git Bash or Cygwin on Windows: the PowerShell script knows how to start Docker Desktop there
case "$OSTYPE" in
  msys* | cygwin*) exec powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$root/spona.ps1")" "$@" ;;
esac

usage() {
  cat <<'EOF'
Usage: ./spona.sh [command]

  all        (default) everything in Docker: Postgres, MinIO, Mailpit, backend, frontend
  backend    backend and services in Docker, you run the frontend: npm run dev --prefix frontend
  frontend   frontend and services in Docker, you run the backend: cd backend && ./mvnw spring-boot:run
  services   only Postgres, MinIO and Mailpit, you run both
  down       stops and removes the containers, the data stays in the volumes
  logs       follows the logs, of everything or of the services named: ./spona.sh logs backend
  ps         lists the containers
EOF
}

compose=(docker compose --file "$root/compose.yaml")
everything=(--profile backend --profile frontend)

fail() {
  printf '\033[31m%s\033[0m\n' "$1" >&2
  exit 1
}

engine_up() { docker info >/dev/null 2>&1; }

check_compose() {
  # An older Compose fails on compose.yaml with a confusing schema error
  local version major minor
  version="$(docker compose version --short 2>/dev/null || true)"
  version="${version#v}"
  IFS=. read -r major minor _ <<<"${version%%[-+]*}"
  if ! [[ "$major" =~ ^[0-9]+$ && "$minor" =~ ^[0-9]+$ ]] || ((major < 2 || (major == 2 && minor < 24))); then
    fail 'Docker Compose 2.24 or newer is needed: https://docs.docker.com/compose/install/'
  fi
}

start_engine() {
  command -v docker >/dev/null || fail 'Docker is not installed: https://docs.docker.com/get-started/get-docker/'
  engine_up && return

  # Starting Docker Desktop only helps when the CLI talks to Docker Desktop. DOCKER_HOST shows up here too
  local endpoint
  endpoint="$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)"
  case "$endpoint" in
    */.docker/run/docker.sock | */.docker/desktop/docker.sock) ;;
    unix:///var/run/docker.sock)
      # Docker Desktop's on macOS, the system engine's on Linux
      [[ "$OSTYPE" == darwin* ]] ||
        fail 'The Docker engine is not running. Start it (sudo systemctl start docker), then run this again.' ;;
    *) fail "Docker at ${endpoint:-the current context} is not running. Start that engine first, or switch to Docker Desktop: docker context use desktop-linux" ;;
  esac

  echo 'Starting Docker Desktop...'
  docker desktop start --detach >/dev/null 2>&1 || # Docker Desktop 4.37 and newer
    { [[ "$OSTYPE" == darwin* ]] && open -a Docker 2>/dev/null; } ||
    systemctl --user start docker-desktop 2>/dev/null ||
    fail 'Docker Desktop could not be started. Start it, then run this again.'

  local deadline=$((SECONDS + 180))
  until engine_up; do
    ((SECONDS < deadline)) || fail 'Docker did not come up within 3 minutes.'
    sleep 3
  done
}

cmd="${1:-all}"
[[ $# -gt 0 ]] && shift

case "$cmd" in
  all) apps=(backend frontend) ;;
  backend) apps=(backend) ;;
  frontend) apps=(frontend) ;;
  services) apps=() ;;
  down | logs | ps)
    engine_up || { echo 'Docker is not running, so neither is Spona.'; exit 0; }
    check_compose
    case "$cmd" in
      down) "${compose[@]}" "${everything[@]}" down ;;
      logs) "${compose[@]}" "${everything[@]}" logs --follow "$@" ;;
      ps) "${compose[@]}" "${everything[@]}" ps ;;
    esac
    exit ;;
  help | -h | --help) usage; exit 0 ;;
  *) usage >&2; exit 1 ;;
esac

start_engine
check_compose

idle=()
for app in backend frontend; do
  [[ " ${apps[*]} " == *" $app "* ]] || idle+=("$app")
done
if ((${#idle[@]})); then
  # Another mode may have left them running, on the ports the copy you start by hand needs
  "${compose[@]}" "${everything[@]}" rm --stop --force "${idle[@]}"
fi

echo 'Building and starting the containers. The first run downloads a lot and takes a few minutes.'
profiles=()
for app in "${apps[@]}"; do profiles+=(--profile "$app"); done
# Without build attestations an unchanged image keeps its ID, so compose leaves its running container alone
BUILDX_NO_DEFAULT_ATTESTATIONS=1 "${compose[@]}" "${profiles[@]}" up --detach --build --wait ||
  fail 'Spona did not start. See why with: ./spona.sh logs'

case "$cmd" in
  all) running='Spona is running' ;;
  services) running='The services are running' ;;
  *) running="The $cmd and the services are running" ;;
esac
printf '\n\033[32m%s\033[0m\n' "$running"
cat <<'EOF'
  app       http://localhost:5173   (demo sign-in: ana-petrovic / spona1234)
  api       http://localhost:8080/api
  mail      http://localhost:8025
  storage   http://localhost:9001   (spona / spona-secret)
  database  localhost:5432, database spona, spona / spona
EOF

todo=()
[[ " ${apps[*]} " == *" backend "* ]] || todo+=('cd backend && ./mvnw spring-boot:run')
[[ " ${apps[*]} " == *" frontend "* ]] || todo+=('npm run dev --prefix frontend')
if ((${#todo[@]})); then
  printf '\nNow start, each in its own terminal from the project root:\n'
  printf '  %s\n' "${todo[@]}"
fi
printf '\nLogs: ./spona.sh logs [service]   Stop: ./spona.sh down\n'
