---
task: e2e-smoke
title: First end-to-end test — register, search "run", save two senses, see them in "My words"
status: in-progress
branch: main
started: 2026-11-06T10:55:00Z
last_updated: 2026-11-06T10:55:00Z
last_commit: 5864b14
agents: [ClaudeC-Fable]
related_decisions: [docs/engineering.md, docs/plan.md, e2e/README.md, docs/handoff/archive/handoff-20261106T105500Z.md]
---

# First end-to-end test — register, search "run", save two senses, see them in "My words"

## Goal and acceptance
Turn `e2e/` into a runnable Playwright project and add the first full-stack test that `docs/engineering.md` (Testing tiers) and `docs/plan.md` (Verification) describe: register a new user, search "run", save two senses, and see both in "My words". It runs against the real backend (`./gradlew bootRun`, fake dictionary provider, Postgres) and the web app, as `e2e/README.md` describes.

Acceptance:
- `cd e2e && npm ci && npx playwright test` passes locally against a running backend and web app, with no install, lint or test warnings (no-warnings rule, `docs/engineering.md`).
- The test registers its own user (unique email per run) so it is repeatable without resetting the database, and uses only the fake provider's words.
- `e2e/AGENTS.md` exists with the commands (install, run, headed/debug) and rules, like `backend/AGENTS.md` and `web/AGENTS.md`; `e2e/README.md` stays accurate. Root `AGENTS.md` repo map links it.
- Dependencies start on current majors (Playwright, TypeScript, Node 24), recorded in `e2e/package.json`.
- No CI workflow: `docs/engineering.md` → Delivery says no workflows are committed for now (by Alex). The `e2e.yml` described there waits for Alex.

## Current state
Not started. Split out of `step1-backend` by its reviewer (2026-11-06), because it is a third project with its own tooling, not part of the Step 1 acceptance criteria. What is already verified by hand (Step 1 review): `./gradlew bootRun` serves the whole contract, and `npm run dev` proxies `/api` to it on http://localhost:5173, so the loop the test automates works today.

## Done
- Nothing yet.

## Tried and rejected
- Nothing yet.

## Decisions made during this task
- Own task, not part of Step 1 (ClaudeC-Fable, 2026-11-06): Step 1's acceptance (P24 + warning-free backend `check`) was met without it, and the e2e project needs its own package, commands and instructions file.

## Open questions
- Next agent: web server for the test. `e2e/README.md` offers `npm run build && npm run preview` or `npm run dev`. `web/vite.config.ts` sets the `/api` proxy under `server.proxy`; Vite's preview server defaults `preview.proxy` to `server.proxy`, but confirm it before relying on `preview`, or use `npm run dev` and say so in the README.
- Next agent: Playwright can start the servers itself (`webServer` in the config) or expect them running, as the README assumes today. Pick one, and keep the README and `e2e/AGENTS.md` in step.
- Next agent: this machine has no Docker. Earlier sessions used a portable JDK 25 and PostgreSQL 16 in `AgentsOutput/ClaudeC-Opus5.5/` (set `JAVA_HOME` to its `jdk/jdk-25.0.5+8`; start the database with `pg/pgsql/bin/pg_ctl -D pgdata -l pg.log start`), with the same users and databases as `docker-compose.yml`. Other agents may read it, but must not rely on it: it isn't in the repo. `backend/.env` must exist (`backend/AGENTS.md` → Prerequisites).

## Next step
1. Scaffold `e2e/`: `package.json` (`@playwright/test` current major, `typescript`), `playwright.config.ts` with `BASE_URL` (default http://localhost:5173), `tsconfig.json`, and `.gitignore` entries if any are missing (root `.gitignore` already ignores `playwright-report/`, `test-results/`, `blob-report/`).
2. Write `tests/save-senses.spec.ts`: register `e2e-<timestamp>@example.com`, search "run" (the URL is `/?q=run`), click "+" on two senses, open "My words", expect both lemmas/translations. Use accessible roles and labels, not CSS classes.
3. Add `e2e/AGENTS.md`, update `e2e/README.md` and the root `AGENTS.md` repo map line; run everything once end to end and record the exact commands that passed.

## Session log
- 2026-11-06T10:55:00Z ClaudeC-Fable: Opened this handoff while reviewing `step1-backend`; no code yet.

## Outcome
<!-- Filled in only when archiving. -->
