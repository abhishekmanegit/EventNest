# EventNest

Discover college events, register before the deadline. A full-stack event discovery
and registration platform with three role experiences — student, college organizer,
and admin.

```
UI (React + Vite)  →  /api  →  Spring Boot API  →  PostgreSQL (Flyway-migrated)
```

## Features

- **Events** — browse by category/city, search, filter, trending/featured wheels, related
  events, booking-style detail pages, bookmarks, and one-tap registration.
- **Organizers** — college profile, event lifecycle (draft → pending review → published →
  completed/cancelled), registration roster with a 3-tap **start → mark attendance** flow
  for walk-ins, and a schedule/prizes editor.
- **Students** — dashboard, my registrations (with status & cancellation), saved events,
  notifications (unread badge, read tracking), and profile.
- **Admins** — platform stats with daily trends, user/college/organizer moderation,
  event moderation (approve → publish, or reject with feedback), category management,
  and a report inbox.
- **Auth** — JWT access + refresh tokens, single-flight auto-refresh, session restore on
  hard refresh, role-based route guarding.

## Tech stack

| Layer      | Tech |
|------------|------|
| Frontend   | React 18, React Router 7, Vite 6, Tailwind 4 |
| Backend    | Java 17, Spring Boot 3.5, Spring Security + JWT, MapStruct |
| Database   | PostgreSQL 16, Flyway migrations |
| Tests      | JUnit 5 + MockMvc E2E (backend), Vitest + Testing Library (frontend), puppeteer-core browser smoke |

## Repository layout

```
backend/        Spring Boot API (controllers, services, Flyway migrations, seed data)
frontend/       React SPA (pages, components, services, utils)
scripts/        Dev helpers (backend.ps1, test*.ps1, db/pg.bat, db/*.sql)
docs/           ARCHITECTURE.md (design notes, API map, DTO shapes)
docker-compose.yml, backend/Dockerfile, frontend/Dockerfile
```

---

## Quick start — Docker (no local toolchain needed)

Requires Docker Desktop with WSL integration enabled.

```bash
docker compose up --build
```

- UI → http://localhost:8081
- API + Swagger → http://localhost:8080/swagger-ui.html

Flyway creates the schema on first boot. Uncomment `SPRING_PROFILES_ACTIVE: dev` in
`docker-compose.yml` to bring up the demo dataset (see accounts below).

## Local development (Windows / WSL2)

Prerequisites: PostgreSQL 16, JDK 17, Maven 3.9, Node 20+, npm. The scripts assume a
Windows-side Postgres on `localhost:5432`.

**1. Database**

```bat
scripts\db\pg.bat scripts\db\01_bootstrap.sql     :: once: create eventnest role + database
:: (optional) wipe and rebuild the dev schema so Flyway re-applies every migration:
scripts\db\pg.bat scripts\db\99_reset_dev_schema.sql
```

**2. Backend**

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # from WSL (JDK 17 + Maven)
```

Or from a Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\backend.ps1 start dev
powershell -ExecutionPolicy Bypass -File scripts\backend.ps1 stop
powershell -ExecutionPolicy Bypass -File scripts\backend.ps1 status
```

The `dev` profile applies Flyway migrations and seeds demo colleges/events/accounts.

**3. Frontend**

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173, proxies /api to localhost:8080
```

No CORS setup is needed in dev — the Vite dev server proxies `/api` to the backend.

## Demo accounts (dev profile / seed)

| Role    | Email | Password |
|---------|-------|----------|
| Admin   | `admin@eventnest.dev` | `Admin@12345` |
| Organizer | `rahul@xyz.edu.in` | `Organizer@123` (APPROVED) |
| Student | `abhishek@student.edu.in` | `Student@123` |

Seed runs only with the `dev` profile; it is never enabled outside dev.

## Tests

```bash
# Backend E2E (auth, events, registration, admin, profile) — needs Postgres up
powershell -ExecutionPolicy Bypass -File scripts\test-all.ps1          # against the running backend
powershell -ExecutionPolicy Bypass -File scripts\test-all.ps1 -Reset   # wipes + reseeds dev schema first (repeatable, CI-friendly)

# Frontend unit + component tests
cd frontend && npm test

# Browser smoke (headless Chrome/Edge against a running dev server; installs puppeteer-core)
cd frontend && node smoke.mjs
```

> Note: the events suite asserts discovery starts empty, so it needs a clean
> events table. `-Reset` handles that automatically; otherwise reset the schema
> manually (`scripts\db\pg.bat scripts\db\99_reset_dev_schema.sql` and restart
> the backend) before each full run.

## Configuration

The backend reads everything from environment variables (see `application.yml`); none of
the committed defaults are secrets.

| Variable | Default (dev) | Purpose |
|----------|---------------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/eventnest` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `eventnest_app` / `eventnest_local_dev` | DB credentials |
| `EVENTNEST_JWT_SECRET` | dev-only placeholder | HS256 signing key — **override in any deployed environment** |
| `EVENTNEST_STORAGE_PATH` | `./uploads` | Uploaded poster/banner/logo storage |
| `EVENTNEST_CORS_ORIGINS` | `http://localhost:5173` | Allowed browser origins |
| `EVENTNEST_NOTIFICATIONS_ENABLED` | `false` | Runs the reminder scheduler |

Get the full API map (every endpoint + DTO) in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).