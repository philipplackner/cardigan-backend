# Proposal

## Why

The repo is still the single-module Spring Initializr skeleton. It can't start without a database configuration, and no feature can be built on the agreed architecture. This is the root backend chunk (`be-scaffolding`), so every later backend chunk waits on it.

## What Changes

- Replace the single-module build with `:app`, `:common`, `:user` and `:board`, using convention plugins in `build-logic/` and a version catalog. Labels and import/export become packages inside `:board` (decisions D1, D14).
- Align dependencies with the project stack: JPA instead of Spring Data JDBC, the `io.spring.dependency-management` plugin removed, and Spring Security removed until `be-common` (D4). No other stack pieces are added (D15).
- Add `dev` and `prod` profiles with no default (D5). The database connection comes from `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` (D7). Dev connects to the Supabase dev branch through the session pooler (D6).
- Dev lets the application update the schema from its model, with migrations off (D2). Prod applies migrations and validates the schema, with no migration files yet (D3).
- Add a public, liveness-only health endpoint (D9) and console-only logging (D10).
- Add a Testcontainers Postgres 17 smoke test (D11), a multi-stage Dockerfile (D13), and a GitHub Actions workflow that runs `./gradlew build` (D12).
- Update `AGENTS.md` to match these decisions (D16), and update the affected breakdown chunks in the shared specs store (D17).

No product behavior, endpoint under `/api/v1`, table or client-visible contract is added. Nothing here is breaking, because no client uses this backend yet.

## Capabilities

### New Capabilities

- `module-boundaries`: which modules the backend has, what they may depend on, and how the data they own is separated.
- `runtime-environments`: how profiles, database configuration, schema handling and logging behave in each environment.
- `service-health`: the public endpoint that reports whether the process is alive.
- `delivery-pipeline`: the checks that run on every change, and the container image the service ships as.

### Modified Capabilities

None. `openspec/specs/` is empty.

## Shared Specs

This change implements no shared-spec requirement. It relies on these:

- `api-conventions`, "All endpoints use a versioned base path": the health endpoint is operational rather than part of the client API, so it stays outside `/api/v1`.
- `board-export-format`, "Import is all or nothing": a reason boards, labels and import/export share the `:board` module.
- `domain-label`, "Deleting a label removes it from cards": a reason cards and labels share the `:board` module.
- `domain-card`, "Card labels are a set sorted by name": a reason cards and labels share the `:board` module.

## Impact

- **Build**: the root `build.gradle.kts`, `settings.gradle.kts` and `src/` move into the new layout. New files are `build-logic/`, `gradle/libs.versions.toml` and `gradle.properties`.
- **Config**: `application.yaml` moves to `:app`, joined by `application-dev.yaml` and `application-prod.yaml`.
- **Running locally**: `SPRING_PROFILES_ACTIVE` and the `DB_*` values must be provided.
- **New files**: `Dockerfile`, `.dockerignore`, `.github/workflows/ci.yml`.
- **Docs**: `AGENTS.md` in this repo, and `docs/breakdown/` in `cardigan-shared-specs`, committed separately in that repo.
- **External systems**: the app connects to the Supabase dev branch. The production branch isn't touched.
