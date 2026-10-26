# Engineering conventions

Shared across `backend/`, `web/` and `e2e/`. Project-specific commands live in each project's AGENTS.md.

## Local development
- **Prerequisite for backend:** JDK 25 (Temurin) with `JAVA_HOME` set. Gradle comes from the wrapper (`./gradlew`).
- **Prerequisite for web:** Node.js 24 LTS, with its folder on `PATH`. Package install scripts run `node` themselves, so calling npm by full path isn't enough: `npm ci` fails with "'node' is not recognized".
- **Only infrastructure runs in Docker.** `docker compose up -d` starts Postgres (it also creates the `paralumo_test` database for integration tests). The API and the web app run natively so debuggers, hot reload and breakpoints work normally.
- **Backend** on `http://localhost:8000` (`./gradlew bootRun`), scaffolded in Step 1.
- **Web** on `http://localhost:5173`. In Step 0 (before backend exists), run with local MSW mocks using `npm run dev:mock` (sets `VITE_API_MOCKS=1`). `main.tsx` dynamically imports `src/mocks/browser` only when this flag is set, tree-shaking MSW from production builds and excluding `public/mockServiceWorker.js` from production output. In Step 1 (with backend), `npm run dev` proxies `/api/*` to the backend (`http://localhost:8000`), so the browser sees one origin without CORS.
- **Offline dictionary.** `DICTIONARY_PROVIDER=fake` makes the backend serve hand-authored data in our own domain shape from `backend/src/main/resources/fake-data/`. This is the default for local work, tests and CI, so development never depends on the network or burns the daily quota. Switch to `yandex` only when you need live data.
- **Debugging.** `.vscode/launch.json` (added with the backend scaffolding) has *Backend: API* (a Java launch of `ParalumoApplication`, needs the Extension Pack for Java) and *Web: Chrome* configs, plus a compound *Full stack* config that runs both at once. Start `npm run dev` first, because the Chrome config attaches to the running dev server. The backend config runs from `backend/` so Spring Boot picks up `backend/.env`. Backend tests are run and debugged from the Java test runner, no launch config needed.

## Dependencies and warnings
- **No warnings (by Alex):** "I don't think it is ok to have warnings in the green field project". Install and build (`npm ci`, `./gradlew build`, including Gradle deprecation warnings and `javac` lint), lint, typecheck, tests and build must finish with no warnings, deprecation notices or console noise. A new warning is a defect to fix in the same change, not something to explain away. Check exit codes and read the full output (or log file), not just the last line.
- **Current majors:** new dependencies start on the current major version. When a tool can't support the latest release yet (e.g. typescript-eslint and TypeScript 7), pin the newest version it supports and note why in the project's `intent.md`.
- **Install scripts:** npm only runs dependency install scripts that are approved in `allowScripts` in `package.json` (`npm approve-scripts <pkg>`). Approve only what's needed, e.g. `msw`, whose script keeps `public/mockServiceWorker.js` in sync.

## Configuration and secrets
- **All configuration comes from environment variables.** The backend binds them to typed `@ConfigurationProperties` records in `backend/src/main/java/com/paralumo/config/`. Nothing else reads environment variables directly: no `System.getenv`, no `@Value`.
- **Fail fast.** Required settings have no defaults (validated with `@Validated` + `@NotBlank` on the properties records). The app refuses to start if one is missing, and never falls back to a dev secret.
- **Files:** `backend/.env.example` and `web/.env.example` are committed and list every variable. The real `.env` files are git-ignored.
- **Web bundle is public.** Only `VITE_`-prefixed variables reach the browser, and they must never hold secrets. The web app needs no secrets at all.
- **Where secrets live:**
  - local: `backend/.env`
  - CI: GitHub Actions secrets. CI uses the fake dictionary provider, so no Yandex key is needed
  - production: the hosting platform's secret store (**TBD** with hosting)
- **Secrets inventory:** `AUTH_SECRET` (signs sessions/JWTs), `DATABASE_URL` with `DATABASE_PASSWORD`, `YANDEX_DICT_API_KEY`.
- **Rotation:** `AUTH_SECRET` rotation invalidates sessions. Design for accepting a list of secrets (current + previous) before first production users.

## Testing tiers
| Tier | Where | Needs | Runs in |
|---|---|---|---|
| Unit | `backend/src/test/java` (untagged, `./gradlew test`), `web/src/**/*.test.ts(x)` | nothing external; providers and API mocked | every push, seconds |
| Web UI-only (planned) | `web/` | the MSW mock layer in `web/src/mocks/`, no backend | web CI, once added. Uses the same handlers as the dev mock mode (by Alex) |
| Backend integration | `backend/src/test/java`, tagged `integration` (`./gradlew integrationTest`) | real Postgres (`TEST_DATABASE_URL`), Flyway migrations applied, fake dictionary provider, full Spring context called via MockMvc | backend CI |
| Migrations | an integration test | Postgres | backend CI; applies all migrations to an empty database, then starts with `spring.jpa.hibernate.ddl-auto=validate` so entities and schema must agree. Migrations are forward-only, so there is no downgrade step |
| Contract | CI step | — | backend CI fails if `docs/api/openapi.json` differs from what the backend exports; web CI fails if the generated client differs from what's committed |
| End-to-end | `e2e/` (Playwright) | built web + running API + Postgres, fake dictionary provider | e2e CI on PRs touching any project |

The first e2e test covers the loop from the plan: register, search "run", add two senses, and see them in "My words".

## API contract
- springdoc generates the spec from the backend controllers and DTOs. `./gradlew exportOpenApi` exports it to `docs/api/openapi.json`, which is committed.
- `web` generates its typed client from that file (`npm run gen:api` → `web/src/api/`) once the backend is scaffolded in Step 1.
- An API change touches the backend code/schemas, `openapi.json`, and the web client in the same commit. CI enforces this.

## Delivery
- **Trunk-based:** short-lived branches, PRs into `main`, and `main` is always deployable.
- **Merges keep commit messages:** use rebase or merge commits, not squash, because commit bodies carry agents' reasoning (see [AGENTS.md](../AGENTS.md#commit-messages)).
- **Pipelines** (`.github/workflows/`), each filtered by path:
  - `backend.yml`: format (Spotless), compile with Error Prone + NullAway, unit + integration + migration tests, OpenAPI drift check, Docker image build
  - `web.yml`: lint (eslint), typecheck (tsc), unit tests (vitest), client drift check, production build
  - `e2e.yml`: full-stack Playwright run on PRs touching `backend/`, `web/` or `e2e/`
- **Each pipeline arrives with its project:** `backend.yml` in the commit that scaffolds the backend, `web.yml` with the web app, and `e2e.yml` with the first e2e test. Each is written against the real commands and passes on its first run. Until then, drafts are kept in `AgentsOutput/` (git-ignored).
- **No workflows are committed for now (by Alex):** the first `web.yml` was removed in 67eb91b "while infra is not ready". Don't re-add workflows until Alex says the infrastructure is in place; the checks they would run (lint, typecheck, tests, build) are still required locally before every commit.
- **Deploy: TBD with hosting.** Planned shape: merging to `main` builds and deploys only the changed project, and the backend runs its Flyway migrations as a release step before the new version takes traffic. Migrations must be backward-compatible with the previous backend release (expand → migrate → contract).
- **Environments:** local and production to start. Add staging if we have real users before Android.

## Observability (minimum for v1)
- Structured JSON logs from the backend, with a request ID on every line, returned as the `X-Request-ID` header.
- Every dictionary-provider call is logged with query, status, latency and cache hit/miss, and the daily Yandex quota usage is tracked.
- `GET /api/health` (process up) and `GET /api/health/ready` (DB reachable), used by the host's health checks.
