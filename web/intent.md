# Web: intent

Scope: the browser client, and the fast prototyping surface for every feature before Android. Product context: [../intent.md](../intent.md). Feature detail: [../docs/plan.md](../docs/plan.md).

## Stack
- **React 19 + Vite 8 + TypeScript 6** SPA, on Node.js 24 LTS (`engines` in `package.json`, CI uses the same). TypeScript stays on 6.x until typescript-eslint supports 7
- Styling: **Tailwind CSS 4** (via `@tailwindcss/vite`, no config file) for rapid responsive styling and easy theming
- Routing: React Router (`react-router` package). Server state: TanStack Query (no global store unless a real need appears)
- Typed API client generated from `docs/api/openapi.json` (`openapi-typescript` + `openapi-fetch`)
- Audio pronunciation: Web Speech API (`window.speechSynthesis`) is the primary audio source, with feature detection (`'speechSynthesis' in window`) to hide the button when unsupported. Voices load asynchronously in Chrome, so there is no synchronous voice check: it would hide the button wrongly
- Tests: Vitest + Testing Library for components. Full-flow tests live in `../e2e`

## Pages (steps 0–2)
- Login / register
- Search: results grouped by part of speech, one card per sense (Russian translation, synonyms, English meanings, examples), with a "+" button that turns into ✓ once saved; clicking ✓ unsaves the sense again
- My words: the user's vocabulary list
- Yandex credit line, if the terms require it

Step 3 adds the Match game page.

## Decisions
- The web app holds **no secrets** and talks only to `/api` on the same origin (the Vite proxy locally, and the same arrangement in production).
- The API client is generated from `docs/api/openapi.json` (`npm run gen:api`) once the backend is scaffolded in Step 1. During the initial UI implementation (Step 0, by Alex), `web/` defines its TypeScript models and a hand-written client in `src/api/` that MSW intercepts at `/api/...`, keeping production code decoupled from `src/mocks/`.
- **Outside-in UI implementation with MSW (by Alex):** "we are implementing web ui now. Based on them we will implement backend." Mock fixtures and MSW handlers are authored directly inside `web/src/mocks/` to build and validate the user experience without waiting for backend exports. The MSW handlers maintain an in-memory set of saved sense IDs to overlay `saved: true/false` on search responses dynamically and support mock `POST`/`DELETE /api/cards`. Auth endpoints (`/api/auth/...`) are called through `src/api/` and mocked by MSW with in-memory user sessions, so production code contains no mock logic (by Alex).
- **The search lives in the URL:** `/?q=word` is the only state for the active search. Links, reloads and the browser's back/forward buttons all show the word in the URL, and the page opens on an empty "enter a word" state rather than a hard-coded sample word. The sample chips ("run", "bank", "light") stay as onboarding shortcuts; they exist in the mock data today and must exist in the backend's fake provider from Step 1.
- **The MSW mock layer is a separate, permanent part of `web/` (by Alex):** it is not replaced by running against the backend's mocked functionality, because that "mixtures implementations that should be segregated", and because the "UI mock can be utilized in the future for UI only tests". It lives in its own folder (`src/mocks/`), is switched on only by `VITE_API_MOCKS=1` (via `npm run dev:mock`), and is never imported by production code. `main.tsx` dynamically imports `./mocks/browser` only when enabled, tree-shaking MSW out of production builds and excluding `mockServiceWorker.js` from production distributions. Pages, components and hooks don't know whether they talk to MSW or a real backend. The same handlers are reused later for UI-only tests (see [../docs/engineering.md](../docs/engineering.md#testing-tiers)).
- **Generated client (Step 1):** `npm run gen:api` turns `docs/api/openapi.json` into `src/api/schema.d.ts` (openapi-typescript). `src/api/types.ts` only names the generated schemas, and `src/api/client.ts` keeps the Step 0 functions and `ApiError` but sends every request through `openapi-fetch` typed by the generated paths, so pages, hooks and MSW handlers didn't change. `VITE_API_BASE_URL` is now an origin (the paths carry `/api`); empty still means same origin.
- **The ✓ "Saved" button on the search page unsaves (by Alex, 2026-11-06: "let's implement toggling from saved"):** a saved sense shows a ✓ button, and clicking it removes the card, the same as "Remove" in "My words". The lookup response gives each saved sense the `card_id` of the user's card, and the button calls the existing `DELETE /api/cards/{cardId}` with it. Considered and not chosen: a delete-by-sense endpoint (a second way to delete the same thing), and finding the card in the `GET /api/cards` list on the client (it would tie the search page to having the whole list loaded, which stops working once the list is paged). Saving again restores the card with its notes, so a mistaken click loses nothing.
- **TypeScript 6 peer override:** openapi-typescript 7.13 and openapi-fetch 0.17 (both latest) declare `typescript ^5.x`, and npm refuses to install them next to TypeScript 6. `overrides` in `package.json` points their `typescript` peer at the project's own version. Both only use TypeScript for types, and typecheck, tests and build pass. Drop the overrides once they declare TypeScript 6 support.

## Open questions
- Hosting: static hosting with `/api` routed to the backend, or served by the backend's host. TBD with backend hosting.
- Dependency advisories: `npm audit` (2026-11-06) reports advisories only in dev tooling (vite, vitest, undici, zod, source-map-js, and eslint 10 / typescript-eslint through minimatch). `npm audit fix` covers the first group; the eslint ones offer only a downgrade to eslint 9, which the current-majors rule rules out, so they wait for upstream. A separate dependency-update change, not part of a feature step.
