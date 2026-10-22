---
name: raise-pr
description: Use when asked to open, raise, create, submit, or update a pull request (PR) for review in this repo.
argument-hint: "[issue-number] [--draft]"
---

# Raise a Pull Request

## Overview

A PR is outward-facing: reviewers see it and it triggers CI. Raise it only when the work is verified, and fill in every section of `.github/pull_request_template.md` truthfully.

## Preconditions (check all; stop and tell the user if any fails)

- You are **not** on `main`. If you are, create a branch first (naming rules in CLAUDE.md).
- There are no uncommitted changes you don't understand (`git status`). Ask before committing stray files.
- Checks from the `verifying-changes` skill pass **now**. Run them; don't rely on an earlier run.
- `review-ticket` has been run on the branch and its verdict is ✅.
  - If the verdict is ⚠️ or ❌ (any Blocker or Major, or any unmet criterion), **fix the findings first**.
  - If the user explicitly wants a PR anyway, open it as `--draft` and list every open finding under "Notes for reviewers".
- You know the ticket number: it is in the branch name, the commit `Refs #n`, or you ask the user. Docs-only, chore or harness PRs may have no ticket. Replace the Closes line with `No ticket: <reason>`.
- **Check whether a PR already exists:** `gh pr list --head <branch> --state open`. If one does, push, then update its body with `gh pr edit <pr> --body-file …` instead of creating a new PR.

## Steps

1. **Sync with main.**
   ```bash
   git fetch origin
   git rebase origin/main     # branch never pushed
   git merge origin/main      # branch already pushed or reviewed: never rewrite shared history
   ```
   Resolve conflicts, then re-run the checks.
2. **Write the title.** Conventional Commit style, under 72 characters, e.g. `feat(booking): create bookings with conflict checks`.
3. **Write the body.**
   1. Copy `.github/pull_request_template.md` to a temp file in the scratchpad.
   2. Fill **every** section:
      - `Closes #<n>`;
      - each acceptance criterion copied from the issue and ticked only if met;
      - the actual commands run and their results;
      - screenshots for UI changes, otherwise "N/A";
      - checklist items ticked only when true. Strike out items that don't apply (`~~item~~ N/A`).
   3. End with the attribution lines the environment requires, if any.
4. **Push and create.**
   ```bash
   git push -u origin HEAD
   gh pr create --base main --title "<title>" --body-file <tmp-file> [--draft]
   ```
5. **Label it** to match the ticket. `type:*` and `area:*` labels come from the `create-ticket` skill:
   ```bash
   gh pr edit <pr> --add-label "type:feature,area:backend"
   ```
6. **Report** the PR URL and anything left in "Notes for reviewers". Do **not** merge, approve, or request reviewers unless asked.

## Common mistakes

| Mistake | Fix |
|---|---|
| Ticking checklist boxes by default | Tick only what you verified; mark the rest N/A or explain. |
| `Fixes #n` placed in the title | Put `Closes #n` in the body, so GitHub links and auto-closes the issue. |
| Force-pushing after review started | Add new commits, or merge main into the branch. |
| A PR that mixes the ticket with drive-by refactors | Split them out, or list them clearly under Notes. |
| Body says "tests pass" with no command | Name the command and the result. |
