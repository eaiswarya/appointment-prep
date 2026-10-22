# appointment-prep

Appointment booking app: providers publish availability, customers book time slots, the system prevents conflicts and sends email confirmations.

- **What to build:** `docs/PRODUCT.md` (requirement IDs `FR-n`, stories `C1`/`P1`/`A1`)
- **How to build it:** `docs/TECHNICAL_DESIGN.md` (architecture, schema, API, error codes, milestones M0–M6)
- **Stack:** React 19 + TypeScript (Vite) · Spring Boot 3 / Java 21 · PostgreSQL 16 · Docker Compose

The design doc wins when code and docs disagree. If a change needs the design to change, update the doc in the same PR.

## Workflow

Tickets are **GitHub Issues** on this repo. Each ticket goes through:

1. `create-ticket` (write or refine the issue)
2. `implement-ticket` (branch, TDD, commits)
3. `review-ticket` (self-review against acceptance criteria)
4. `raise-pr`

One ticket = one branch = one PR.

The skills depend on the **superpowers** Claude Code plugin (TDD, verification, planning, worktrees, receiving code review). Install it with `/plugin` before using them.

- **Branches:** `feat/<issue>-<slug>`, `fix/<issue>-<slug>`, `chore/<issue>-<slug>`, `docs/<issue>-<slug>`. For work without a ticket (docs, chores), drop the issue number: `chore/<slug>`.
- **Commits:** Conventional Commits, e.g. `feat(booking): reject overlapping bookings`, with `Refs #<issue>` in the body. Scopes: `auth`, `user`, `provider`, `catalog`, `slot`, `booking`, `notification`, `settings`, `admin`, `frontend`, `infra`, `docs`.
- Never commit to `main` directly. Never force-push a branch someone else has reviewed.

## Non-negotiable invariants

These come from TECHNICAL_DESIGN.md. Breaking one is a blocker in review.

- **Time:** store `timestamptz` in UTC, use `Instant` in Java, send ISO-8601 `Z` over the API. Expand availability rules in the **provider's** `ZoneId`. Show times in the viewer's zone, with the zone label.
- **Clock:** read the current time from the injected `java.time.Clock`. Never call `Instant.now()`, `LocalDateTime.now()` or `new Date()` in backend code.
- **No double bookings:** the DB exclusion constraints `bookings_no_provider_overlap` and `bookings_no_customer_overlap` are the source of truth. App pre-checks only give friendly messages. Never remove or weaken those constraints.
- **Booking status:** change it only through `Booking.transitionTo(...)`, which writes `booking_events`.
- **Emails:** write emails to `email_outbox` in the same transaction (`BEFORE_COMMIT` listener). Never send SMTP inside a request.
- **API:** controllers take and return `record` DTOs, never JPA entities. Errors are RFC 7807 with a `code` from TECHNICAL_DESIGN §9.3.
- **Access to others' data:** when a caller asks for data they don't own, return **404**, not 403.
- **Migrations:** Flyway migrations are append-only. Never edit a `V*__*.sql` that is on `main`.
- **Secrets:** read secrets from env vars only. `.env` is git-ignored.

## Code style

- **Backend:** organize packages by feature (`booking/`, `slot/`, …). Constructor injection. `@Transactional` on service methods, not on controllers.
- **Frontend:** organize by feature (`src/features/*`). Use TanStack Query for server state. Use the generated API types from `src/api/schema.d.ts`, never hand-written duplicates. Map error codes to messages in `src/lib/errors.ts`.
- Match the comment density and naming of the surrounding code.

## Commands

See the `verifying-changes` skill. Commands that don't exist yet (before milestone M0 is done) should be reported as missing, not invented.
