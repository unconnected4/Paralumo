# API contract

`openapi.json` in this folder is the single source of truth for the API between `backend/` and its clients (`web/` now, Android later).

- **Generated, not hand-written:** `cd backend && ./gradlew exportOpenApi` writes this file (once the backend is scaffolded in Step 1)
- **Consumed by web:** `cd web && npm run gen:api` regenerates `web/src/api/`
- **Drift check:** CI fails if `openapi.json` or the generated web client differs from the backend schemas. See [../engineering.md](../engineering.md#api-contract).
