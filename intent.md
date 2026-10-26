# Paralumo: intent

Product-level intent and cross-cutting decisions. Project-specific intent lives in [backend/intent.md](backend/intent.md) and [web/intent.md](web/intent.md). The detailed plan is in [docs/plan.md](docs/plan.md), and engineering conventions are in [docs/engineering.md](docs/engineering.md).

## Architecture / build order
0. Web UI implementation (first, by Alex): implement the web SPA (search, sense cards with "+/✓", "My words") with mock data directly in `web/`. Do not scaffold anything in `backend/` yet. Based on the implemented web UI, we will implement the backend.
1. Shared backend: multi-user, word/sense storage, SRS data model, Java (Spring Boot) implementation of the contract proven by the Web UI.
2. Translator-dictionary integration: connect web to live/fake backend provider.
3. "Match the words" game, built against real per-user vocabulary data from step 2.
4. Android after web is stable (a third project alongside `backend/` and `web/`)
5. iOS only if there's demonstrated interest/traction

## Cross-cutting decisions
- **English → Russian only** for v1. A single pair lets us check the whole search → save → practice loop quickly. The product itself is not an English→Russian dictionary (by Alex): this pair is the first implementation, chosen to get a stable project skeleton, and other pairs follow.
- **Implement Web UI first, backend follows (by Alex):** "we are implementing web ui now. Based on them we will implement backend." Do not scaffold anything in `backend/` before the Web UI is built. Web UI authors its own mock fixtures directly in `web/src/mocks/` to explore and validate UX and data requirements. The backend and API contract are implemented based on the proven Web UI needs.
- **Our API is designed for our app, not for a provider (by Alex):** "we design our own application". The API and data model follow what the clients need (the web UI for now), and providers adapt to it, never the other way round. Provider integration questions (keys, terms, storage rules, mapping) are addressed after the first backend with mocked functionality is built.
- **Segregated implementations, mocks included (by Alex):** every part's code stays separate, and mocks are implementations in their own right, not shortcuts inside another part. Alex rejected letting the web app prototype against the mocked backend instead of its own mock layer because it "mixtures implementations that should be segregated", and because "UI mock can be utilized in the future for UI only tests". So `web/` keeps its own mock layer, independent of the backend's fake provider and mocked functionality. Each implementation (real or fake) sits behind an interface and is chosen by configuration or test setup; production code never branches on "is this a mock". Each part can be built, run and tested on its own.
- **Yandex Dictionary API is the first provider, not the only one (by Alex).** It returns sense-level translations with examples (plain translation APIs don't), and it is strong for pairs involving Russian. Providers sit behind a `DictionaryProvider` interface, and more will be added.
- **Backend and clients meet only at a JSON API** described by a committed OpenAPI spec (`docs/api/openapi.json`). The web app now, and the Android app later, use it unchanged.
- **FSRS scheduling, with every review logged from day one.** Keeping the logs means we can change the algorithm later without losing history.
- **The match game only lightly affects the review schedule.** Matching tests recognition and can be solved by elimination, which makes it a weak memory signal.
- **Separate delivery:** backend and web each have their own pipeline and deploy independently from the same repo.

Stack choices per project: [backend](backend/intent.md) (Java: Spring Boot + Spring Data JPA + Flyway + Postgres, by Alex) and [web](web/intent.md) (React + Vite + TypeScript).

## Open questions
- Yandex integration (deferred until after the first backend with mocked functionality, by Alex): key availability and terms. Known so far: the [terms](https://yandex.com/legal/dictionary_api/) allow only temporary caching and forbid permanently storing the data, cap use at 10,000 requests/day per key, and require a "Powered by Yandex.Dictionary" link on every page showing the data. This matters for saved cards, which keep a copy of the sense.
- Hosting for the backend + Postgres and for the web app is **TBD**. Deploy and production-secrets sections are placeholders until this is decided.
