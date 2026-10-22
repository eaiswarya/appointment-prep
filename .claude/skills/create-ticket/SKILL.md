---
name: create-ticket
description: Use when asked to create, write, refine, split, or break down tickets, GitHub issues, or user stories, or to turn a milestone (M0–M6), feature, or bug report into issues for this repo.
argument-hint: "<milestone | feature description | issue-number to refine>"
---

# Create or Refine Tickets

## Overview

A good ticket is a **vertical slice** that fits in one PR. It has acceptance criteria someone else could test without asking the author anything. Tickets are GitHub Issues that follow `.github/ISSUE_TEMPLATE/ticket.md` (or `bug.md`).

## Steps

1. **Collect the source.**
   - **Milestone:** TECHNICAL_DESIGN §15, plus the PRODUCT.md stories and FRs it covers.
   - **Feature request:** the user's words, plus the matching FRs.
   - **Refine an issue:** `gh issue view <n> --comments`.
2. **Check what already exists** so you don't create duplicates: `gh issue list --state all --search "<keywords>"`.
3. **Slice the work.** Aim for at most about one day of work and about 400 changed lines per ticket.
   - Prefer end-to-end slices: DB + API + UI for one behavior.
   - Use horizontal tickets only for foundations, such as M0 skeleton or auth infrastructure.
   - Split large tickets by **behavior**, not by layer. For example, "create booking" and "cancel booking", not "booking backend" and "booking frontend".
4. **Write each ticket** with the template sections:
   - **Context:** cite the IDs (`FR-14`, `C7`, `TECHNICAL_DESIGN §6.3`).
   - **Scope:** concrete endpoints, screens, tables. Include an explicit **Out of scope** list.
   - **Acceptance criteria:** one observable behavior each, in *Given/When/Then* form. Include error paths, using the error codes from TECHNICAL_DESIGN §9.3, and role/ownership cases.
   - **Technical notes:** migrations needed, invariants at risk, edge cases (DST, concurrency).
   - **Dependencies:** `Blocked by #n`.
5. **Show the drafts to the user** as a numbered list (title, labels, criteria) **before creating anything.** Creating issues is visible to the team. Revise until the user approves.
6. **Create the issues.** Make sure the labels exist first (this is idempotent):
   ```bash
   gh label create "type:feature" --color 1d76db --force
   gh label create "type:chore"   --color c5def5 --force
   gh label create "type:bug"     --color d73a4a --force
   gh label create "type:docs"    --color 0075ca --force
   gh label create "e2e"          --color f9d0c4 --force   # PRs with this label run Playwright in CI
   gh label create "area:backend" --color 5319e7 --force
   gh label create "area:frontend" --color 0e8a16 --force
   gh label create "area:infra"   --color fbca04 --force
   gh label create "M<n>"         --color ededed --force
   gh issue create --title "<title>" --body-file <scratchpad>/ticket-<k>.md --label "type:feature,area:backend,M3"
   ```
   - Write body files to the scratchpad directory, never into the repo.
   - Add **every** layer the ticket touches as an `area:*` label.
   - Add `e2e` if the ticket changes a user-facing flow.
   Create issues in dependency order, so `Blocked by #n` can point at real numbers.
7. **Report** the created issue numbers and links as a dependency-ordered list.

## Acceptance criteria quality

| Weak | Strong |
|---|---|
| "Booking works" | Given a free 10:00 slot, when a customer POSTs `/api/bookings`, then 201 and the slot is no longer returned by `/slots`. |
| "Handle conflicts" | Given 20 concurrent requests for the same slot, then exactly one returns 201 and 19 return 409 `SLOT_TAKEN`. |
| "Show times correctly" | Given provider zone `Asia/Kolkata` and viewer zone `Europe/London`, slots display in London time with the zone label. |
| "Secure endpoint" | Given customer B, when they GET customer A's booking, then 404. |

## Title format

`<scope>: <outcome>`, where `<scope>` is a feature scope from CLAUDE.md (`booking`, `slot`, `auth`, …), e.g. `booking: customers can cancel before the cutoff`. For bugs, use `bug: <what is wrong>`.

Don't confuse the two: the title scope is the **feature**, while `area:*` labels are the **layer** (backend, frontend, infra).
