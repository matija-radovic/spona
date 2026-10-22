# Spona

A jobs and social platform, built as a graduation thesis. People and organizations have profiles, follow each other, post, comment and react; organizations publish jobs and users apply with a CV. Spring Boot 4 (Java 25) and PostgreSQL on the back, React 19 and Vite on the front, Serbian UI.

## Requirements

Docker with Compose 2.24+ runs everything; add JDK 25 and Node 22+ to run the backend or frontend outside Docker. Ports `8080`, `5173`, `5432`, `9000`, `9001`, `1025` and `8025` must be free.

## Install and run

```bash
git clone https://github.com/matija-radovic/spona.git
cd spona
./spona.ps1     # Windows, PowerShell
./spona.sh      # macOS, Linux
```

This starts Postgres, MinIO, Mailpit, the backend and the frontend in Docker and returns once the backend answers. The first run takes a few minutes. Open http://localhost:5173 and sign in as `ana-petrovic` with `spona1234` (every seeded account is verified and uses that password).

`./spona.ps1 down` stops everything and keeps the data. `logs [service]`, `ps` and `services` (only Postgres, MinIO and Mailpit) are the other commands. On Windows, Docker Desktop must be set to Linux containers.

## Local development

Run the part you work on on your machine and let Docker run the rest:

| Command | Docker runs | You run |
|---|---|---|
| `./spona.ps1 backend` | backend, Postgres, MinIO, Mailpit | the frontend |
| `./spona.ps1 frontend` | frontend, Postgres, MinIO, Mailpit | the backend |
| `./spona.ps1 services` | Postgres, MinIO, Mailpit | both |

Switching modes removes the container the new mode leaves to you, so its port is free. The frontend container proxies `/api` to the backend container, or to a backend on your machine at port 8080 when there is none.

**Backend**

```bash
cd backend
cp .env.example .env     # optional, the defaults are the same
./mvnw spring-boot:run   # Windows: .\mvnw.cmd spring-boot:run
```

The `dev` profile is the default. Spring runs `docker compose up` on the root `compose.yaml` itself (so Docker must be running), applies the Flyway migrations and seeds demo data into an empty database. `SPONA_SEED=false` skips the seed.

**Frontend**

```bash
cd frontend
cp .env.example .env.local   # optional
npm install
npm run dev
```

Vite proxies `/api` to the backend and `/storage` to MinIO.

### Frontend scripts

| Script | What it does |
|---|---|
| `npm run dev` | Vite dev server with the API and storage proxies |
| `npm run build` | Type-checks and builds to `dist/` (runs the two generators below first) |
| `npm run preview` | Serves the built `dist/` |
| `npm run lint` | ESLint |
| `npm run gen:mediaqueries` | Generates `src/shared/css/mediaqueries.ts` from `_mediaqueries.scss` |
| `npm run check:error-codes` | Fails if a backend `ErrorCode` has no Serbian translation, or a translation has no code |

### Tests

```bash
cd backend && ./mvnw test        # needs Docker for Testcontainers
cd frontend && npm run lint && npm run build
```

### Local addresses

| What | URL |
|---|---|
| Frontend | http://localhost:5173 |
| API | http://localhost:8080/api |
| Mailpit (verification and reset mails) | http://localhost:8025 |
| MinIO console | http://localhost:9001 (`spona` / `spona-secret`) |
| Postgres | `localhost:5432`, database `spona`, `spona` / `spona` |

## Configuration

Variables and their defaults are in [backend/.env.example](backend/.env.example) and [frontend/.env.example](frontend/.env.example). Outside development set at least `SPONA_FRONTEND_URL` (CORS and links in mails) and `SPONA_JWT_SECRET` (32+ bytes). Spring profiles: `dev` (default, starts Compose, seeds), `docker` (backend container, seeds), `prod` (no defaults, fails on a missing variable) and `smtp-tls` (STARTTLS for a real mail provider).

## More

Plain Compose without the launcher: `docker compose --profile backend --profile frontend up --build`.

## Roadmap

Hosting (the frontend on Vercel, the backend container on a managed host with managed Postgres), a GitHub Pages demo of the frontend running on an in-memory mock backend so it can be tried without Docker, CI on GitHub Actions, moving local file storage from MinIO to RustFS, Swagger UI for the API, and many more small imporvements/features both quality of life and security ones.
