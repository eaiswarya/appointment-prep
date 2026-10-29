## Summary

<!-- 1–3 sentences: what this PR does and why. -->

Closes #<!-- issue number; or replace this line with "No ticket: <reason>" -->



## Changes

<!-- Bullet list of notable changes, grouped by area (backend / frontend / infra / docs). -->

-

## Acceptance criteria

<!-- Copy each acceptance criterion from the ticket and mark it. Unmet criteria must be explained. -->

- [ ]

## How was this tested?

- [ ] Backend: `./gradlew check`
- [ ] Frontend: `npm run lint && npm run typecheck && npm test -- --run && npm run build`
- [ ] API contract: `npm run generate:api` run, and `schema.d.ts` committed <!-- or N/A -->
- [ ] Docker: `docker compose up --build` and all services are healthy <!-- infra changes, or N/A -->
- [ ] E2E / manual: <!-- `npx playwright test` or the manual steps, or "N/A" -->

<!-- New tests added (name the important ones): -->

## Screenshots

<!-- Required for UI changes (desktop + mobile width). Otherwise "N/A". -->

## Checklist

- [ ] Scope matches the ticket. Unrelated changes are left out or listed under "Notes".
- [ ] Times are stored and sent in UTC; the injected `Clock` is used (no `Instant.now()`).
- [ ] DB changes go in a **new** Flyway migration; no applied migration was edited.
- [ ] New or changed error codes are added to TECHNICAL_DESIGN §9.3 and `frontend/src/lib/errors.ts`.
- [ ] API changes: the OpenAPI client is regenerated and `schema.d.ts` committed.
- [ ] Authorization: role rules and ownership checks are tested (404 for others' resources).
- [ ] Docs are updated if behavior differs from PRODUCT.md / TECHNICAL_DESIGN.md.
- [ ] No secrets, credentials, or `.env` files are committed.

## Notes for reviewers

<!-- Risky areas, open questions, follow-up tickets. Otherwise "N/A". -->
