# End-to-end tests

Playwright tests that drive the built web app against a running backend and Postgres. They cover web and backend together, so they live in neither project.

## Run locally
1. `docker compose up -d` at the repo root
2. Backend with `DICTIONARY_PROVIDER=fake`: `cd backend && ./gradlew bootRun` (Flyway migrations run on start)
3. Web: `cd web && npm run build && npm run preview` (or `npm run dev`)
4. `cd e2e && npm ci && npx playwright test`

`BASE_URL` (default http://localhost:5173) selects the target.

## Rules
- Tests always use the fake dictionary provider, so results are deterministic.
- Each test registers its own user, so there's no shared state between tests.
- First test: register → search "run" → add two senses → both appear in "My words".
