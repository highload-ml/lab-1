# Experiment Tracking — web client

Angular 21 single-page app for the backend in the repository root. Plan: `../Architecture/web-app-plan.md`.

## Requirements
- Node.js 22.12+ (checked with 22.12.0) and npm. npm **11.1.0 has a dependency-resolution bug** that fails with
  `Cannot read properties of null (reading 'edgesOut')`; use a newer npm, e.g. `npx npm@11.20.0 install`.
- Java 17+ only for `npm run api:generate` (OpenAPI Generator is a Java tool).

## Commands
| Command | What it does |
|---|---|
| `npm install` | Install dependencies |
| `npm start` | Dev server on http://localhost:4200; `/api` and `/v3/api-docs` are proxied to http://localhost:8080 (`proxy.conf.json`) |
| `npm test` | Unit and component tests (Vitest, single run) |
| `npm run lint` | ESLint for TypeScript and templates |
| `npx ng build` | Production build into `dist/frontend` |
| `npm run api:fetch [-- <url or file>]` | Save the backend spec to `openapi/openapi.json` (default `http://localhost:8080`), normalizing operation ids |
| `npm run api:generate` | Regenerate the typed client in `src/app/api` from `openapi/openapi.json` |

Run the backend first (from the repository root): `docker compose up -d db && ./gradlew bootRun`.

## Structure
```text
src/app/
├── api/        generated client — do not edit; regenerate after backend API changes
├── core/       session (signed-in user), guards, error handling, layout shell
├── shared/     reusable helpers (pagination)
├── features/   one folder per feature: auth, projects, experiments, tags, runs
└── pages/      app-level pages (not found)
```

## Conventions
- Standalone components, `ChangeDetectionStrategy.OnPush`, signals for state.
- Components never call generated services directly: each feature wraps them in its own `data-access` service.
- Every failed request arrives as an `ApiProblem` (`core/errors`); forms use `applyServerErrors` for 400 validation errors.
- No authentication: signing in by nickname only identifies the user (`SessionService`).
