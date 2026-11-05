---
name: verifying-changes
description: Use when needing to build, test, lint, run, or start this app locally (backend, frontend, Docker Compose stack, Mailpit), and before claiming any ticket, fix, or PR is done or passing.
---

# Verifying Changes

## Overview

Run the checks for **every layer you touched** and report the real output. **REQUIRED BACKGROUND:** superpowers:verification-before-completion. No "should pass" claims.

If a command below doesn't exist yet (the repo is before or during milestone M0), say so in the report. Don't invent a substitute and call it verified.

## Which checks to run

| You touched | Run |
|---|---|
| `backend/**` | `cd backend && ./mvnw verify` (unit + Testcontainers integration + Spotless) |
| `frontend/**` | `cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build` |
| API contract (controllers/DTOs) | Both of the above, plus: `cd backend && ./mvnw test -Dtest=OpenApiSpecTest`, then `cd frontend && npm run generate:api`. The regenerated `src/api/schema.d.ts` must be committed (`git status` clean for that file) |
| `docker-compose*.yml`, Dockerfiles, `nginx.conf` | `docker compose build && docker compose up -d && docker compose ps` (all services healthy), then smoke test |
| User-facing flow | E2E: `cd e2e && npx playwright test` with the stack running |
| Docs only | Check that links and section references (§n, FR-n) still resolve |

Targeted runs while iterating (always finish with the full run for that layer):

```bash
./mvnw test -Dtest="*BookingServiceTest"
npm test -- --run src/features/booking
```

## Running the stack

```bash
cp .env.example .env              # first time only, then set a real APP_JWT_SECRET:
sed -i "s|^APP_JWT_SECRET=.*|APP_JWT_SECRET=$(openssl rand -base64 48)|" .env
docker compose up --build -d
docker compose logs -f backend    # watch startup / Flyway
docker compose down               # stop (keeps the DB volume)
```

`docker compose up` also loads `docker-compose.override.yml`: the `dev` profile (Swagger UI) and Mailpit. For a production-like run without them, use `docker compose -f docker-compose.yml up --build -d`. Postgres isn't published to the host; use `docker compose exec db psql -U "$POSTGRES_USER" -d appointments`.

| URL | What |
|---|---|
| http://localhost:8080 | App (Nginx → SPA, `/api` → backend) |
| http://localhost:8080/api/docs | Swagger UI (dev profile) |
| http://localhost:8025 | Mailpit: every email the app sends |
| `curl localhost:8080/api/actuator/health` | Backend health |

Dev seed data (admin, 3 providers, services, availability) loads with the `dev` profile. Reset everything with `docker compose down -v`. This **deletes the DB volume**, so tell the user before running it.

## End-to-end tests (Playwright)

```bash
cd e2e && npm ci && npx playwright install chromium   # first time only
docker compose up -d --build --wait                  # from the repo root
cd e2e && npx playwright test                         # E2E_BASE_URL overrides http://localhost:8080
```

- Tests live in `e2e/tests/`. Use role and label locators, and unique emails per run.
- In CI, the `E2E` workflow runs on PRs labelled `e2e`, nightly, and on demand. When it fails, it uploads the Playwright report and the Compose logs as the `e2e-report` artifact.

## Smoke test for booking changes

1. Log in as a seeded customer and book a slot. Expect 201 and a confirmation screen.
2. Mailpit shows the customer and provider emails (they arrive within about 10 seconds; the dispatcher runs every 5 seconds).
3. Book the same slot again from another customer. Expect 409 `SLOT_TAKEN`, and the UI refreshes the slots.

## Environment gotchas (Windows)

- Run `./mvnw` from Git Bash, or `.\mvnw.cmd` from PowerShell.
- Testcontainers needs Docker Desktop running. A "Could not find a valid Docker environment" error means Docker isn't running; it is not a test failure. Report it as such.
- Line-ending warnings (LF→CRLF) are harmless. Spotless failures are not: run `./mvnw spotless:apply`.

## Report format

```
./mvnw verify              → ✅ BUILD SUCCESS (212 tests)
npm run typecheck          → ❌ 2 errors (src/features/booking/Review.tsx:41 …)
docker compose (not run)   → no infra changes
```
