---
name: addressing-pr-feedback
description: Use when a pull request in this repo has review comments or requested changes to address (e.g. "fix the review comments on PR 5", "address feedback", "CI failed on my PR").
argument-hint: <pr-number>
---

# Addressing PR Feedback

## Overview

**REQUIRED BACKGROUND:** superpowers:receiving-code-review. Verify each comment before acting on it. Agreeing without checking, or silently skipping a comment, are both failures.

## Steps

1. **Collect all the feedback.** Run `git status --porcelain` first. If the tree is dirty, ask before running `gh pr checkout <n>`, or use a worktree (superpowers:using-git-worktrees).
   ```bash
   gh pr view <n> --comments                                   # top-level + review summaries
   gh api --paginate repos/{owner}/{repo}/pulls/<n>/comments \
     --jq '.[] | {id, path, line, user: .user.login, body}'    # inline comments
   gh pr checks <n>                                             # CI status
   ```
2. **Triage** each comment into one of the categories below. Show the user this table before making big or disputed changes.

   | Category | Action |
   |---|---|
   | Correct and in scope | Fix it |
   | Correct but out of scope | Propose a follow-up ticket (`create-ticket`) |
   | Unclear | Ask the reviewer, through the user |
   | Incorrect | Explain why, with evidence (code, test, design doc section) |
   | Conflicts with a CLAUDE.md invariant or the design doc (e.g. "rename the exclusion constraint", "use LocalDateTime") | **The user decides.** Present the trade-off. If they accept, change the code **and** the design doc in this PR |

   Route each change to its skill:
   - schema changes → `writing-migrations`;
   - endpoint, DTO or error-code changes → `adding-api-endpoint`;
   - slot or time logic → `working-with-time-and-slots`.

3. **Failing CI:** reproduce it locally with the matching command from `verifying-changes`, then use superpowers:systematic-debugging. Don't push guesses to CI.
4. **Fix** each item with a test where behavior changes.
   - Add **new commits**, e.g. `fix(booking): address review — map 23P01 for customer overlap`.
   - Don't amend or force-push a branch under review.
5. **Verify** with the full checks for the touched layers (`verifying-changes`). Run `review-ticket` again if the changes were substantial.
6. **Push** with `git push`. Reply on GitHub **only if the user asks**. When you reply, do it per thread, saying what changed and in which commit:
   ```bash
   gh api repos/{owner}/{repo}/pulls/<n>/comments/<id>/replies -f body="Fixed in abc1234: …"
   ```
7. **Report** the triage table with outcomes (fixed / follow-up / disputed / asked) and the check results.
