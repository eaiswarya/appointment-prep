---
name: adding-frontend-feature
description: Use when building or changing a React screen, component, form, route, or data hook in frontend/ of this repo, or when fixing UI bugs, accessibility issues, or mobile layout problems.
---

# Adding a Frontend Feature

## Overview

The frontend is a thin, accessible client over a typed API. The server owns the business rules, so the UI never re-implements cutoffs, conflict checks or permissions. Structure and routes are in TECHNICAL_DESIGN §10.

## Checklist

1. **Location.** Put the code in `src/features/<feature>/` (components, hooks, tests together). Promote it to `src/components/` only once it is used by two or more features.
2. **Data.**
   - Use TanStack Query hooks with the generated `openapi-fetch` client from `src/api/`. Use generated types from `schema.d.ts`; never hand-write API types.
   - Query keys go in the shared key factory (`['slots', providerId, serviceId, weekStart]`).
   - Mutations invalidate every affected key. For example, booking create invalidates `slots` and `bookings/me`.
   - Missing endpoint or field? Use the `adding-api-endpoint` skill. Don't work around it on the client.
3. **Server-computed flags.** Show or hide actions with fields like `canCancel` and `canReschedule`. Don't compute cutoffs in the browser.
4. **Forms.** Use React Hook Form + a Zod schema. Server `VALIDATION_ERROR.errors[]` go onto the matching fields.
5. **Errors.** Show every API error through `src/lib/errors.ts` (code → message). `SLOT_TAKEN` refetches the slots and returns the user to the picker.
6. **Time.** Show times in the viewer's zone with the zone label (see `working-with-time-and-slots`).
7. **Auth.** Wrap routes in a `RequireRole` guard for UX only; the backend enforces access.
   - Keep the access token in memory. Never put it in `localStorage`.
   - Exception: a guest's chosen slot may be kept in `sessionStorage` until they log in.
8. **Accessibility (WCAG 2.1 AA).**
   - Use shadcn/Radix primitives.
   - Every input has a label.
   - Focus is visible and moves to the dialog or heading after navigation.
   - Slot grid: arrow keys + Enter.
   - Colour is never the only signal.
9. **Responsive.** It must work at 360px wide with no horizontal scroll. Test 360px and desktop.
10. **States.** Every data view handles loading (skeleton), empty (`EmptyState`), error, and success.

## Tests

Use Vitest + React Testing Library + MSW handlers based on the generated types.

- **What to test:** behavior, not markup. Use `getByRole` / `getByLabelText` queries.
- **Required cases:**
  - happy path;
  - each error code the screen can receive;
  - the empty state;
  - keyboard interaction for interactive widgets.
- **User-facing flows** also get or extend a Playwright test (`e2e/`). Label the ticket and PR `e2e`.

## Common mistakes

- Duplicating server rules (cutoff, limit) in the UI, which drift from the API.
- Showing `new Date(iso).toLocaleString()` without a zone label.
- Using `useEffect` + `fetch` instead of a query hook, which loses caching and invalidation.
- Forgetting to invalidate the `slots` query after a booking mutation, so the UI shows a stale free slot.
