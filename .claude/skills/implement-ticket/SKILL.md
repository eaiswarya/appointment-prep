---
name: implement-ticket
description: "Use when asked to implement, build, work on, start, or pick up a ticket or GitHub issue in this repo (e.g. 'implement #12', 'work on ticket 7', 'pick up the next M2 ticket')."
argument-hint: <issue-number>
---

# Implement a Ticket

## Overview

The ticket's **acceptance criteria are the contract**. Every criterion ends up as a passing test, and nothing outside the ticket's scope gets built.

## Steps

0. **Check the working tree first:** `git status --porcelain`. If it is not empty, **stop and ask** before switching branches. `git switch` carries uncommitted changes onto the new branch. The user can either commit or stash the changes themselves, or you can work in a separate worktree (superpowers:using-git-worktrees). Never stash or discard their work yourself.
1. **Read the ticket.** Run `gh issue view <n> --comments`.
   - **No ticket at all** (e.g. "quickly fix X")? Draft one with `create-ticket` (use the bug template for bugs), get the user's OK, and continue with that issue.
   - **Stop and ask the user** if the ticket has no acceptance criteria, the criteria contradict the design docs, or a `Blocked by #x` issue is still open. To fix a vague ticket, use the `create-ticket` skill to refine it. Don't guess the requirements.
   - "Pick the next ticket": `gh issue list --label M<n> --state open --search "no:assignee"`. Choose the lowest-numbered issue that has no open blockers.
2. **Read the design.** Read the FR IDs and TECHNICAL_DESIGN sections the ticket cites, plus `CLAUDE.md` invariants. If the ticket and the design disagree, ask the user. Don't silently pick one.
3. **Create the branch.**
   ```bash
   git switch main && git pull --ff-only
   git switch -c feat/<n>-<short-slug>     # fix/ chore/ docs/ by ticket type
   ```
   Assigning the issue is visible to the team. Do it (`gh issue edit <n> --add-assignee @me`) only if the user asks or agrees.
4. **Plan.** Make a checklist that maps **each acceptance criterion → the test(s) that will prove it**. If the ticket touches more than about 3 files or more than one layer, use **superpowers:writing-plans** first.
5. **Build test-first.** **REQUIRED SUB-SKILL:** superpowers:test-driven-development. Work one acceptance criterion at a time: failing test → code → green → refactor.
   - If the change touches the schema, use the `writing-migrations` skill.
   - If the change touches an endpoint, DTO or error code, use the `adding-api-endpoint` skill.
   - If the change touches slots, availability, timezones, buffers or booking times, use the `working-with-time-and-slots` skill.
   - If the change touches React screens or components, use the `adding-frontend-feature` skill.
6. **Commit** in small logical steps. Use the Conventional Commit format from CLAUDE.md, with `Refs #<n>` in the body.
7. **Verify.** Use the `verifying-changes` skill. Run the full checks for every layer you touched, not only the new tests.
8. **Self-review.** Run the `review-ticket` skill on your own branch, and fix every Blocker and Major finding.
9. **Hand off.** Report to the user:
   - which acceptance criteria are met, and which test proves each;
   - the commands you ran and their results;
   - anything deferred.

   Raise a PR (`raise-pr` skill) only if the user asked for one.

## Scope discipline

| Temptation | Do instead |
|---|---|
| "While I'm here, I'll refactor X" | Leave it. Suggest a follow-up ticket in the hand-off. |
| A criterion is hard, so build a simpler version | Stop and tell the user. Never mark a criterion met when it isn't. |
| Ticket needs something from a later milestone | Build the minimum stub the ticket needs and note it. |
| Design doc looks wrong | Ask. If the user agrees, update the doc in this branch. |

## Common mistakes in this codebase

- Calling `Instant.now()` instead of using the injected `Clock`. This makes tests flaky around times.
- Using `LocalDateTime` for booking times. Use `Instant`, and use `ZoneId` only to expand availability rules.
- Testing booking conflicts only through the app pre-check. Also assert that the DB exclusion constraint triggers (Testcontainers).
- Returning entities from controllers, or returning 403 for another user's booking (it should be 404).
- Adding an error code in the backend but not adding it to `frontend/src/lib/errors.ts` and TECHNICAL_DESIGN §9.3.
- Editing an existing Flyway migration instead of adding a new one.
