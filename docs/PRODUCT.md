# Appointment Booking — Product Documentation

| | |
|---|---|
| **Status** | Draft v0.1 |
| **Last updated** | 2026-10-22 |
| **Stack** | React (frontend) · Spring Boot (backend API) · PostgreSQL · Docker |

---

## 1. Overview

Appointment Booking is a web application where **providers** (e.g. doctors, consultants, tutors, stylists) publish their availability as time slots, and **customers** find a provider, pick a free slot, and book it. The system guarantees that **no slot is ever double-booked**, and sends **email confirmations** for every booking event.

### 1.1 Problem

Scheduling over phone, chat, or shared spreadsheets causes double bookings, no-shows, and back-and-forth messages. Providers lose time managing calendars; customers can't see what's available.

### 1.2 Goals

1. Customers can book an appointment in under a minute without contacting the provider.
2. Zero double bookings, even under concurrent requests.
3. Every booking, cancellation, and reschedule produces an email to the people involved.
4. Providers control their own availability without admin help.

### 1.3 Non-goals (v1)

- Online payments or deposits
- Video-call integration (Zoom/Meet links)
- Two-way sync with Google/Outlook calendars
- SMS / push notifications
- Native mobile apps (the web app must be mobile-responsive instead)
- Multi-tenant / multiple organizations

---

## 2. Users and roles

| Role | Who | Can do |
|---|---|---|
| **Customer** | Anyone who registers | Browse providers, view free slots, book, cancel, reschedule own bookings, view booking history |
| **Provider** | Staff member offering appointments (account created by Admin) | Define working hours and slots, block time off, view/cancel bookings on their calendar, mark bookings completed / no-show |
| **Admin** | System operator | Manage providers and customers, manage services, configure global booking rules, view all bookings |

A user has exactly one role in v1.

---

## 3. Core concepts

| Term | Definition |
|---|---|
| **Provider** | A person whose time can be booked. Has a profile, a timezone, and one or more services. |
| **Service** | A type of appointment a provider offers, with a name and a fixed duration (e.g. "Initial consultation — 30 min"). |
| **Availability rule** | A recurring weekly window when a provider works, e.g. "Mon–Fri 09:00–17:00". |
| **Time off** | A one-off block when the provider is unavailable (holiday, meeting). Overrides availability rules. |
| **Time slot** | A concrete, bookable interval (start–end) derived from availability rules minus time off minus existing bookings. |
| **Booking** | A customer's reservation of a time slot for a specific service with a specific provider. |
| **Buffer** | Optional gap (minutes) a provider requires between consecutive appointments. |

---

## 4. User stories

### Customer
- **C1** — As a customer, I can sign up and log in with email and password.
- **C2** — As a customer, I can browse providers and filter them by service.
- **C3** — As a customer, I can see a provider's free slots for a chosen service on a calendar/date picker, shown in my local timezone.
- **C4** — As a customer, I can book a free slot and immediately see a confirmation screen.
- **C5** — As a customer, I receive a confirmation email with the appointment details and a calendar attachment (`.ics`).
- **C6** — As a customer, I can view my upcoming and past bookings.
- **C7** — As a customer, I can cancel a booking up to the cancellation cutoff.
- **C8** — As a customer, I can reschedule a booking to another free slot up to the cancellation cutoff.
- **C9** — As a customer, I receive a reminder email 24 hours before my appointment.

### Provider
- **P1** — As a provider, I can set my weekly working hours per day.
- **P2** — As a provider, I can add time off for specific dates/times.
- **P3** — As a provider, I can choose which services I offer.
- **P4** — As a provider, I can see my bookings in a day/week calendar view.
- **P5** — As a provider, I am emailed when a booking is created, cancelled, or rescheduled.
- **P6** — As a provider, I can cancel a booking (with a reason), which notifies the customer.
- **P7** — As a provider, I can mark a past booking as *Completed* or *No-show*.

### Admin
- **A1** — As an admin, I can create, edit, and deactivate provider accounts.
- **A2** — As an admin, I can manage the service catalog (name, duration, description).
- **A3** — As an admin, I can configure global rules (booking horizon, minimum notice, cancellation cutoff).
- **A4** — As an admin, I can view and filter all bookings.

---

## 5. Functional requirements

### 5.1 Authentication
- **FR-1** Email + password sign-up for customers; providers are created by admins and set their password via an emailed invite link.
- **FR-2** Stateless JWT authentication; access token + refresh token.
- **FR-3** Password reset via emailed link (expires after 1 hour).
- **FR-4** Role-based authorization enforced on every API endpoint, not just in the UI.

### 5.2 Time slots
- **FR-5** Slots are **generated on demand** from availability rules, time off, existing bookings, service duration, and buffer — they are not pre-stored rows.
- **FR-6** Slot start times are aligned to the service duration grid starting at the beginning of each availability window (e.g. a 30-min service in a 09:00–12:00 window yields 09:00, 09:30, … 11:30).
- **FR-7** Slots are only offered between **minimum notice** (default 2 hours from now) and **booking horizon** (default 60 days ahead).
- **FR-8** All times are stored in UTC. Each provider has an IANA timezone; availability rules are interpreted in the provider's timezone (so DST is handled correctly). The UI displays times in the viewer's timezone and labels it.
- **FR-9** Changing availability rules or adding time off **does not** cancel existing bookings; the provider is warned in the UI about any bookings that now fall outside availability.

### 5.3 Bookings
- **FR-10** A booking references: customer, provider, service, start time (UTC), end time (UTC), status, optional customer notes (max 500 chars).
- **FR-11** Booking statuses and allowed transitions:

  ```
  CONFIRMED ──► CANCELLED      (by customer before cutoff, by provider/admin any time)
  CONFIRMED ──► COMPLETED      (by provider, after end time)
  CONFIRMED ──► NO_SHOW        (by provider, after start time)
  ```
  Rescheduling is modelled as cancelling the original booking and creating a new one, linked via `rescheduled_from_id`, in a single transaction.
- **FR-12** Bookings are confirmed instantly (no provider approval step in v1).
- **FR-13** Customers can cancel or reschedule only until the **cancellation cutoff** (default 24 hours before start).
- **FR-14** A customer may hold at most **3 future CONFIRMED bookings** with the same provider (configurable), to limit slot hoarding.

### 5.4 Conflict checks
A booking request is rejected if any of the following is true. Each failure returns a specific error code so the UI can show a clear message.

| Code | Rule |
|---|---|
| `SLOT_TAKEN` | Overlaps an existing CONFIRMED booking for the same provider (including buffer). |
| `OUTSIDE_AVAILABILITY` | Not fully inside one of the provider's availability windows. |
| `PROVIDER_TIME_OFF` | Overlaps provider time off. |
| `CUSTOMER_OVERLAP` | Overlaps another CONFIRMED booking of the same customer (with any provider). |
| `TOO_SOON` | Starts earlier than the minimum notice. |
| `TOO_FAR` | Starts beyond the booking horizon. |
| `INVALID_DURATION` | End − start ≠ service duration, or not aligned to the slot grid. |
| `SERVICE_NOT_OFFERED` | Provider doesn't offer the requested service, or provider is inactive. |
| `LIMIT_REACHED` | Customer exceeds the per-provider future booking limit. |

- **FR-15** Overlap is defined as `existing.start < new.end AND new.start < existing.end` (touching end-to-start is allowed).
- **FR-16** **Concurrency guarantee:** double booking must be impossible even if two requests for the same slot arrive at the same millisecond. Enforced at the database level (PostgreSQL exclusion constraint on `(provider_id, tstzrange(start, end))` where status = CONFIRMED), with application-level checks for friendly errors. The losing request receives `SLOT_TAKEN` (HTTP 409).
- **FR-17** Conflict checks run again on reschedule for the new slot.

### 5.5 Email notifications

| Event | Customer | Provider |
|---|---|---|
| Booking created | ✅ Confirmation + `.ics` | ✅ New booking |
| Booking rescheduled | ✅ Updated details + `.ics` | ✅ |
| Booking cancelled by customer | ✅ Cancellation receipt | ✅ |
| Booking cancelled by provider/admin | ✅ With reason | ✅ Receipt |
| Reminder (24h before) | ✅ | — |
| Account invite / password reset | ✅ | ✅ |

- **FR-18** Emails are sent **asynchronously** after the booking transaction commits; a failed email never rolls back or blocks a booking.
- **FR-19** Emails go through an outbox table and are retried with backoff (up to 5 attempts); permanently failed emails are visible to admins.
- **FR-20** Emails include: service, provider name, date/time in the recipient's timezone, and a link to manage the booking.
- **FR-21** HTML templates with a plain-text fallback. SMTP is configurable; local development uses **MailHog/Mailpit** in Docker.

---

## 6. Key screens

| Screen | Role | Purpose |
|---|---|---|
| Login / Sign up / Reset password | All | Authentication |
| Provider directory | Customer | List & filter providers by service |
| Provider page + slot picker | Customer | Choose service → date → free slot |
| Booking review & confirm | Customer | Summary, notes, confirm button |
| Booking confirmation | Customer | Success state with details |
| My bookings | Customer | Upcoming / past, cancel / reschedule actions |
| Provider calendar | Provider | Day/week view of bookings |
| Availability settings | Provider | Weekly hours, time off, services, buffer |
| Admin: providers, services, bookings, settings, failed emails | Admin | Management |

---

## 7. Business rules (configurable defaults)

| Setting | Default |
|---|---|
| Minimum notice before booking | 2 hours |
| Booking horizon | 60 days |
| Cancellation / reschedule cutoff | 24 hours |
| Max future bookings per customer per provider | 3 |
| Default buffer between appointments | 0 min (provider-configurable) |
| Reminder lead time | 24 hours |

---

## 8. Non-functional requirements

- **Correctness:** No double bookings under concurrency (FR-16) — covered by an automated concurrent-booking integration test.
- **Performance:** Slot lookup for one provider over 7 days < 300 ms p95; booking creation < 500 ms p95 (excluding email).
- **Security:** Passwords hashed with BCrypt; HTTPS in production; input validation on all endpoints; users can only see their own bookings unless provider/admin; rate limiting on login and booking endpoints.
- **Accessibility:** WCAG 2.1 AA for customer-facing screens; slot picker usable by keyboard.
- **Responsiveness:** Fully usable on mobile widths (≥ 360 px).
- **Auditability:** Every booking status change records who, when, and why.
- **Observability:** Structured logs, Spring Boot Actuator health endpoint, request IDs.
- **Portability:** Whole stack runs locally with one command: `docker compose up`.

---

## 9. Architecture overview

```
┌──────────────┐   REST/JSON   ┌────────────────────┐    JDBC    ┌──────────────┐
│ React SPA    │ ────────────► │ Spring Boot API    │ ─────────► │ PostgreSQL   │
│ (Vite, Nginx)│ ◄──────────── │ auth · slots ·     │            └──────────────┘
└──────────────┘               │ bookings · email   │    SMTP    ┌──────────────┐
                               │ outbox scheduler   │ ─────────► │ Mailpit (dev)│
                               └────────────────────┘            │ SMTP (prod)  │
                                                                 └──────────────┘
```

| Container | Image / tech |
|---|---|
| `frontend` | React + TypeScript (Vite build), served by Nginx |
| `backend` | Spring Boot 3, Java 21, Spring Security, Spring Data JPA, Flyway |
| `db` | PostgreSQL 16 |
| `mail` | Mailpit (dev only) — web UI to inspect sent emails |

### 9.1 Main data entities
`users`, `providers`, `services`, `provider_services`, `availability_rules`, `time_off`, `bookings`, `booking_events` (audit), `email_outbox`, `settings`.

### 9.2 Indicative API

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh` | Public |
| GET | `/api/providers?serviceId=` | Public / Customer |
| GET | `/api/providers/{id}/slots?serviceId=&from=&to=` | Public / Customer |
| POST | `/api/bookings` | Customer |
| GET | `/api/bookings/me` | Customer |
| POST | `/api/bookings/{id}/cancel` | Customer / Provider / Admin |
| POST | `/api/bookings/{id}/reschedule` | Customer |
| GET/PUT | `/api/provider/availability`, `/api/provider/time-off` | Provider |
| GET | `/api/provider/bookings?from=&to=` | Provider |
| PATCH | `/api/bookings/{id}/status` | Provider |
| CRUD | `/api/admin/providers`, `/api/admin/services`, `/api/admin/settings` | Admin |

Errors follow RFC 7807 (`application/problem+json`) with the conflict codes from §5.4.

---

## 10. Release scope

### MVP (v1.0)
Auth (all roles) · provider availability & time off · slot generation · booking create/cancel · all conflict checks incl. DB-level concurrency guard · confirmation & cancellation emails via outbox · customer "My bookings" · provider calendar · basic admin (providers, services) · Docker Compose setup.

### v1.1
Reschedule · 24-hour reminders · `.ics` attachments · admin settings UI · failed-email dashboard · completed / no-show marking.

### Later
Calendar sync (Google/Outlook) · SMS reminders · payments · waitlist for full days · provider approval mode · analytics.

---

## 11. Success metrics

- 0 double bookings in production.
- ≥ 95% of booking emails delivered within 1 minute.
- Median time from landing on a provider page to confirmed booking < 60 s.
- Customer cancellation rate and no-show rate tracked per provider.

---

## 12. Open questions

1. Should customers be able to book as guests (no account), using a magic link to manage the booking?
2. Do services need per-provider durations or prices, or are they global?
3. Should providers be able to require approval for bookings (pending state)?
4. Is a single organization timezone acceptable, or must every provider have their own? *(Current assumption: per-provider.)*
5. Which SMTP provider will be used in production (SES, SendGrid, Mailgun, company SMTP)?
