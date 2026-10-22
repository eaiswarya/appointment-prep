---
name: working-with-time-and-slots
description: Use when touching slot generation, availability rules, time off, buffers, booking start/end times, reminders, timezones, DST, or any date/time display or comparison in this repo, or when a bug involves slots appearing at the wrong time, missing, or overlapping.
---

# Working with Time and Slots

## Overview

Time bugs are the top risk in TECHNICAL_DESIGN §16. Follow three rules:

1. **Points in time are UTC `Instant`s.**
2. **Recurring rules are local times in the provider's `ZoneId`.**
3. **Conversion happens in exactly one place:** slot expansion.

The algorithm is in TECHNICAL_DESIGN §6.1–§6.2. Read it before changing `slot/`.

## Types

| Concept | Java type | DB type | API |
|---|---|---|---|
| Booking start/end, time off, `blocked_until` | `Instant` | `timestamptz` | `"2026-11-03T04:30:00Z"` |
| Availability rule | `DayOfWeek` + `LocalTime` | `smallint` + `time` | `{"dayOfWeek":1,"startTime":"09:00"}` |
| Provider / user zone | `ZoneId` | `varchar` (IANA) | `"Asia/Kolkata"` |
| Durations, buffers | `Duration` (from int minutes) | `int` minutes | minutes |

**Never use** `LocalDateTime` for a real moment, `Date`, `ZoneOffset` instead of `ZoneId` (it loses DST), `ZoneId.systemDefault()`, or `Instant.now()`. Use the injected `Clock`.

## Rules that are easy to get wrong

- **Expand in the provider's zone.** Use `ZonedDateTime.of(date, rule.startTime(), providerZone).toInstant()`. Never use the customer's zone or UTC.
- **DST gap** (local time doesn't exist): `ZonedDateTime.of` shifts it forward. Skip any slot whose `[start, start + D)` would cross the gap.
- **DST overlap** (local time happens twice): use `.withEarlierOffsetAtOverlap()`.
- **Loop over local dates, not 24-hour steps.** DST days are 23 or 25 hours long.
- **Ranges are half-open `[start, end)`.** Overlap is `a.start < b.end && b.start < a.end`. A slot that touches the next one is not a conflict.
- **Buffer.** A candidate blocks `[t, t + D + B)`. Compare it against existing `[start_at, blocked_until)`. The grid still steps by `D`, not `D + B` (§6.2).
- **Clamp to now:** `from' = max(from, now + minNotice)`, `to' = min(to, now + horizon)`. The slots endpoint rejects ranges over 31 days with `RANGE_TOO_LARGE`.
- **Load busy intervals for `[from - 1 day, to + 1 day]`.** Bookings and windows that cross midnight or a zone boundary must not be missed.
- **Frontend.** Format with `date-fns-tz` in the viewer's zone and **always** show the zone label. Group slots by the viewer's local day, not the provider's.

## Required tests for any change here

Use a fixed `Clock` and explicit zones. Never depend on the machine's timezone.

| Case | Example |
|---|---|
| Spring forward | `America/New_York` 2026-03-08, rule 01:00–04:00 → no slot at 02:xx |
| Fall back | `Europe/London` 2026-10-25, rule 00:00–03:00 → no duplicate or overlapping slots |
| Zone far from UTC | Provider `Asia/Kolkata` (+05:30) → a 09:00 local slot is `03:30Z` |
| Day boundary | Customer in `America/Los_Angeles` sees an Asia provider's morning slots on the previous local day |
| Buffer | B = 15, booking 10:00–10:30 → 10:30 slot hidden, 10:45 shown only if on the grid |
| Touching | Booking 09:00–09:30, B = 0 → 09:30 slot is offered |
| Notice/horizon | Slots before `now + minNotice` and after `now + horizon` are excluded |
| Time off | A slot partially overlapping time off is excluded |

The slot generator's core must not do any I/O, so these cases run as fast unit tests. Add one Testcontainers test confirming that the booking API rejects what the generator would not offer.
