---
name: review-ticket
description: "Use when asked to review a ticket's implementation, a branch, or a pull request against its GitHub issue (e.g. 'review #12', 'review PR 5', 'is ticket 7 done?'), and before raising a PR for your own work."
argument-hint: <issue-number | pr-number | branch>
---

# Review a Ticket

## Overview

A review answers two questions, in this order:

1. **Does it do what the ticket says?** Check acceptance criteria, with evidence.
2. **Is it safe to merge?** Check correctness, project invariants and tests.

A green CI run or the author's word is not evidence. Evidence is a file and line, a test name, or command output you ran yourself.

## Steps

1. **Find the ticket and the diff.** Run `git fetch origin` first, and always diff against `origin/main`. A local `main` may be stale.
   - **PR:** run `gh pr view <n> --json number,title,body,headRefName,files`, then `gh pr diff <n>`.
   - **Branch or current work:** run `git diff origin/main...HEAD` and `git log origin/main..HEAD --oneline`.
   - **Find the ticket number**, checking in this order:
     1. the `Closes #x` line in the PR body;
     2. the number in the branch name (`feat/12-…`);
     3. `Refs #x` lines in the commits;
     4. ask the user.

     Then run `gh issue view <ticket> --comments`.
   - **No ticket by design** (docs, harness or chore PRs with "Closes N/A"): review against the PR's Summary and Changes sections, and skip the acceptance-criteria table.
2. **Get the code.** Read the changed files in full, not just the diff hunks. To check out a PR (`gh pr checkout <n>`), first run `git status --porcelain`. If the tree is dirty, ask the user, or review in a separate worktree (superpowers:using-git-worktrees). Don't switch branches under uncommitted work.
3. **Check acceptance criteria.** For each criterion, record *Met*, *Partial* or *Not met*, with evidence (test name, `file:line`).
4. **Check the invariants.** Go through the checklist below, plus anything the ticket's design sections require.
5. **Run the checks** from the `verifying-changes` skill for every touched layer. Report the actual results.
6. **Get an independent pass** on non-trivial diffs (more than about 200 lines, or anything touching `booking/`, `slot/`, `auth/` or migrations). Dispatch the `ticket-reviewer` agent with the ticket number and the diff range. Merge its findings with yours, and drop any you can't confirm.
7. **Report** in the format below. Post to GitHub **only if the user asks**. Write the review file in the scratchpad, never in the repo:
   ```bash
   gh pr review <n> --comment -F <scratchpad>/review.md      # or --request-changes
   ```
   Never `--approve` on the user's behalf unless they explicitly say to.

## Invariant checklist

| Area | Check |
|---|---|
| Scope | No unrelated changes. Nothing from "Out of scope" was built. |
| Time | `Instant` and UTC throughout. Injected `Clock`, no `now()` calls. Availability expanded in the provider's `ZoneId`. |
| Conflicts | Exclusion constraints untouched or strengthened. `23P01` mapped to `SLOT_TAKEN` / `CUSTOMER_OVERLAP`. Booking checks run in the order of TECHNICAL_DESIGN §6.3. |
| State | Status changes go through `Booking.transitionTo`, with a `booking_events` row. |
| Email | Emails go to `email_outbox` in the same transaction. No SMTP call in the request path. |
| API | DTOs are records with validation. Errors are problem+json with a documented `code`. 404 for non-owned resources. |
| Security | Role rule exists in `SecurityFilterChain`. Ownership is checked in the service. No secrets in code or logs. |
| DB | New migration only. Named constraints. `timestamptz`. Indexes for new query paths. |
| Tests | Every criterion has a test. Error paths are tested. Concurrency test exists if booking creation changed. Tests use a fixed `Clock`. |
| Frontend | Uses generated API types. Error code is mapped. Times show a zone label. Keyboard accessible. Works at 360px width. |
| Docs | PRODUCT/TECHNICAL_DESIGN are updated if behavior changed. |

## Report format

```markdown
## Review: #<ticket> <title>   — Verdict: ✅ Ready | ⚠️ Changes needed | ❌ Not ready

### Acceptance criteria
| # | Criterion | Status | Evidence |

### Findings
**Blocker** – breaks a criterion or invariant, loses data, security hole
**Major**   – bug or missing test likely to bite soon
**Minor**   – maintainability, naming, small gaps
**Nit**     – optional
Each item: `file:line` — problem — concrete fix.

### Checks run
<command> → <result>

### Out-of-scope changes / follow-ups
```

**Verdict rule** (`raise-pr` uses the same gate):
- ✅ only if there are no Blockers, no Majors, and every criterion is Met.
- A Major or a Partial criterion makes it ⚠️.
- A Blocker or a Not-met criterion makes it ❌. Don't soften findings to be polite. Don't invent findings to look thorough.
