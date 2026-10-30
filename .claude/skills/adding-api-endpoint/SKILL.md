---
name: adding-api-endpoint
description: Use when adding or changing a REST endpoint, request or response DTO, validation rule, or error code in the Spring Boot API, or when the React frontend needs data or an action the API does not yet provide.
---

# Adding or Changing an API Endpoint

## Overview

An endpoint change touches **five places**:

1. backend code;
2. tests;
3. the security config;
4. the generated frontend client;
5. the docs.

Missing any one causes drift. TECHNICAL_DESIGN §9 is the contract.

## Checklist

1. **Controller** in the feature package (`booking/BookingController.java`, …). Keep it thin: validate, call the service, map to a DTO.
2. **DTOs** are Java `record`s with Bean Validation (`@NotNull`, `@Size`, `@Future`, …), annotated `@Valid` in the controller. Never accept or return JPA entities. Times are `Instant`, serialized as ISO-8601 `Z`.
3. **Service method** owns `@Transactional` and the **ownership check**. A non-owned resource throws `NotFoundException`, which becomes 404, not 403.
4. **Security.**
   - **Path rules** in `SecurityFilterChain`:
     - `/api/admin/**` → ADMIN;
     - `/api/provider/**` → PROVIDER;
     - `/api/auth/**` → public;
     - public `GET` routes (`/api/providers/**`, `/api/services`) listed explicitly;
     - everything else → authenticated.
   - **Role on shared paths:** add `@PreAuthorize("hasRole('CUSTOMER')")` on the controller method (e.g. `POST /api/bookings`, `GET /api/bookings/me`). Mixed-role endpoints like `POST /bookings/{id}/cancel` stay authenticated, and the service decides based on role and ownership.
5. **Errors.**
   - Add new codes to the `ErrorCode` enum with their HTTP status.
   - Throw a `DomainException` that carries an `ErrorCode`. Named subclasses (`SlotTakenException`, `NotFoundException`, …) extend it.
   - `GlobalExceptionHandler` turns it into problem+json. Don't add per-exception handlers.
   - Add the code to the TECHNICAL_DESIGN §9.3 table **and** to `frontend/src/lib/errors.ts`, with a user-facing message.
6. **Lists** are paginated (`page`, `size` ≤ 100) and return `{ items, page, size, total }`.
7. **Tests** (MockMvc or RestAssured), as a minimum:

   | Case | Expect |
   |---|---|
   | Happy path | 2xx + body shape |
   | Invalid body | 400 `VALIDATION_ERROR` with `errors[].field` |
   | No token | 401 |
   | Wrong role | 403 |
   | Another user's resource | 404 |
   | Each domain error the endpoint can raise | Its status + `code` |

8. **Frontend client.**
   1. Make sure Docker is running (the export uses Testcontainers).
   2. Regenerate the client: `cd backend && ./gradlew exportOpenApi` (boots the app on Testcontainers and writes `build/openapi/openapi.json`; no running server needed), then `cd ../frontend && npm run generate:api`.
   3. Commit `src/api/schema.d.ts`. CI regenerates the file and fails if it differs.
   4. Use the generated types.
   5. Add a TanStack Query hook in the feature folder, and invalidate the related query keys after mutations.
9. **Docs.** Update the TECHNICAL_DESIGN §9.1 endpoint table. A new example DTO goes in §9.2 only if the shape is new.

## Example: error code end-to-end

```java
// backend: booking/BookingService.java
if (!provider.offers(serviceId)) throw new DomainException(ErrorCode.SERVICE_NOT_OFFERED);
// ErrorCode.SERVICE_NOT_OFFERED -> HttpStatus.UNPROCESSABLE_ENTITY (422)
```
```ts
// frontend: src/lib/errors.ts
SERVICE_NOT_OFFERED: "This provider doesn't offer that service any more. Please choose another.",
```

## Common mistakes

- Doing the role check only in the frontend route guard. The backend is the authority (FR-4).
- Returning 403 for another customer's booking, which leaks that the ID exists.
- A `PUT` that partially updates. Use `PATCH` for partial updates; `PUT` replaces (as in `PUT /provider/availability`).
- Forgetting to regenerate the client, so the frontend compiles against stale types.
