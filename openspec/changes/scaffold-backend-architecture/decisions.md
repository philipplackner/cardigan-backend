# Decisions: scaffold-backend-architecture

Confirmed with Philipp Lackner on 2026-11-04.

## Summary

This change replaces the single-module Initializr skeleton with the agreed modular monolith: `:app`, `:common`, `:user` and `:board`, wired through convention plugins and a version catalog. The app boots against the Supabase dev branch through a `dev` profile, and has a `prod` profile, a liveness health endpoint, console logging, a Dockerfile and a light GitHub Actions build. It adds no product functionality. Dev uses Hibernate-managed schemas instead of Flyway, which changes a written project rule, so `AGENTS.md` is updated too.

## Decisions

### D1 — Gradle modules
- **Question**: Which backend modules should the skeleton create?
- **Decision**: `:app`, `:common`, `:user` and `:board`. Boards, columns, cards, labels and import/export are packages inside `:board`. Schemas are `user_service` and `board_service`.
- **Alternatives**: `:user, :board, :label, :transfer` (the user's first proposal); `:user, :board, :label`; `:user, :board, :transfer`; only `:app` + `:common`.
- **Why**: The user asked for their proposal to be challenged. Cards and labels are tightly coupled, and imports must be all or nothing across boards, columns, cards and labels. `:transfer` would own no data. Separate modules would need synchronous interfaces in `:common` and a transaction across schemas. It can be split later if needed.
- **Depends on**: —

### D2 — Dev schema management
- **Question**: How should the dev schema be managed?
- **Decision**: `ddl-auto: update` with Flyway disabled on the `dev` profile. Dev data survives restarts. Hibernate never drops or changes existing tables and columns, so resetting a drifted dev schema is a manual wipe.
- **Alternatives**: Hibernate `create` (drops tables on every boot); keep Flyway + `validate`.
- **Why**: No migrations are planned for now, and the schema is expected to change often. The user first asked for a destructive schema, then chose `update` when offered the options.
- **Depends on**: —

### D3 — Prod schema management
- **Question**: What should the `prod` profile do with the schema?
- **Decision**: Flyway enabled with `ddl-auto: validate`, with the dependency and config in place but no migration files. A baseline migration must be written from the entities before the first production deploy.
- **Alternatives**: Hibernate `update` on prod; Hibernate `validate` with no Flyway yet.
- **Why**: Keeps the existing project rule for production, and matches "migrations on deploy" in `infra-vps`.
- **Depends on**: D2

### D4 — Spring Security
- **Question**: How should the security starter on the classpath be handled?
- **Decision**: Remove `spring-boot-starter-security` and its test starter until `be-common` adds the real filter chain.
- **Alternatives**: Keep it with a minimal `SecurityConfig` that only opens health.
- **Why**: Security is out of scope for `be-scaffolding` in the breakdown. There are no endpoints to protect yet other than health.
- **Depends on**: —

### D5 — Spring profiles
- **Question**: How is the active profile chosen?
- **Decision**: `dev` and `prod` profiles with no default. `SPRING_PROFILES_ACTIVE` is set explicitly: `dev` for local runs, `prod` in the Docker image. With no profile, the base config stays non-destructive (`validate`) and doesn't fall back to dev settings.
- **Alternatives**: Default to `dev`; default to `prod`.
- **Why**: A misconfigured deployment must never run with dev schema behavior.
- **Depends on**: D2, D3

### D6 — Supabase connection
- **Question**: How is the Supabase dev branch reached?
- **Decision**: Session pooler on port 5432.
- **Alternatives**: Direct connection (IPv6 only without the add-on); transaction pooler on port 6543.
- **Why**: Works over IPv4 on any network and supports prepared statements. The production connection stays open for `infra-vps`.
- **Depends on**: —

### D7 — Database env vars
- **Question**: What are the database env vars called?
- **Decision**: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, referenced from YAML with no default values. They come from `.env` locally and from real env vars in production.
- **Alternatives**: `SUPABASE_DB_*`; `SPRING_DATASOURCE_*`.
- **Why**: Provider-neutral, and the YAML shows which secrets are required.
- **Depends on**: —

### D8 — Local Postgres
- **Question**: How is a local Postgres supported?
- **Decision**: Through config only. Pointing `DB_*` at any Postgres works. No compose file and no extra profile.
- **Alternatives**: docker-compose plus a `local` profile.
- **Why**: Dev runs against the Supabase dev branch, so this adds no extra files to maintain.
- **Depends on**: D7

### D9 — Health endpoint
- **Question**: What should the health endpoint expose?
- **Decision**: Public `/actuator/health`, liveness only with no database check, no details, and no other actuator endpoints exposed. It sits outside `/api/v1` and outside the client API contract.
- **Alternatives**: Status including a DB check; DB check with details on dev.
- **Why**: User's choice. A Supabase outage should not mark the process as down. As a result, health doesn't prove database connectivity; a failed connection shows up as a failed startup.
- **Depends on**: D4

### D10 — Logging
- **Question**: What format should logging use?
- **Decision**: Boot's default text format to the console in every profile. No external service, no file appender, no `logback.xml`.
- **Alternatives**: JSON on prod.
- **Why**: The user asked for pure console logging. Nothing consumes structured logs yet.
- **Depends on**: —

### D11 — Automated test database
- **Question**: What should tests use for the database?
- **Decision**: One context-load smoke test in `:app` on a Testcontainers Postgres 17, with Hibernate `create-drop`. This replaces the rule that tests use real Flyway migrations until the baseline migration exists.
- **Alternatives**: No database tests yet.
- **Why**: There's no CI database, and GitHub's ubuntu runners include Docker. Project rules exclude Supabase and H2 for tests.
- **Depends on**: D2, D3

### D12 — CI
- **Question**: What should the GitHub Actions pipeline do?
- **Decision**: `./gradlew build` on JDK 25 with Gradle caching, on pull requests and pushes to `master`. No image build and no deployment.
- **Alternatives**: Also run `docker build` without pushing.
- **Why**: The user wants a very light pipeline, adjusted later. The accepted trade-off is that the Dockerfile is only checked locally and can break unnoticed.
- **Depends on**: D11

### D13 — Dockerfile
- **Question**: How should the image be built?
- **Decision**: Multi-stage build inside Docker, with a JDK 25 build stage and a JRE 25 runtime stage. It runs as non-root, sets `SPRING_PROFILES_ACTIVE=prod`, and takes `DB_*` at runtime.
- **Alternatives**: Copy a prebuilt boot jar.
- **Why**: `docker build` works from a clean checkout.
- **Depends on**: D5, D7

### D14 — Contents of empty modules
- **Question**: How much should the empty modules contain?
- **Decision**: A build file plus `.gitkeep` files in the top-level layer packages (`api`, `service`, `domain`, `infra`) and `db/migration/<feature>` folders. No placeholder classes.
- **Alternatives**: Build file only; the full sub-package tree.
- **Why**: Git doesn't track empty directories, and the user wants to be ready to add code.
- **Depends on**: D1

### D15 — Other stack pieces
- **Question**: Which other stack pieces should be wired in now?
- **Decision**: None. springdoc and the spec snapshot test, the Modulith event registry, and JWT resource server dependencies all arrive with the first feature that needs them.
- **Alternatives**: Add any of those now.
- **Why**: This change is only about the build, database, health, Docker and CI.
- **Depends on**: —

### D16 — Project rules
- **Question**: Should this change update the written project rules?
- **Decision**: Update `AGENTS.md` only (`CLAUDE.md` is a symlink to it): dev schema, prod schema, test schema, the `:user` and `:board` modules, and the current state. The `spring-*` skills stay generic.
- **Alternatives**: Also edit the skills; leave the rules unchanged.
- **Why**: `AGENTS.md` already wins over the skills. Without the update, future sessions would read outdated rules and might undo this setup.
- **Depends on**: D1, D2, D3, D11

### D17 — Shared breakdown
- **Question**: What should happen to the breakdown chunks that differ from these decisions?
- **Decision**: Add a task to update them in the shared specs store: `02-be-scaffolding.md` (dev database, migrations, resolved open questions), `05-be-common.md` (users table belongs to `:user`), and `OVERVIEW.md` if it contradicts these decisions. Any matching changes to `graph.json` are included.
- **Alternatives**: Leave the store alone and only note the differences here.
- **Why**: Keeps the plan accurate for later backend chunks. It's work in a second repo with its own commit.
- **Depends on**: D1, D2, D3

## Facts

- The repo is a single-module Initializr skeleton: Kotlin 2.3.21, Spring Boot 4.1.1, `io.spring.dependency-management`, `spring-boot-starter-data-jdbc`, Gradle 9.8.1 — source: `build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`.
- The existing `contextLoads` test has no database setup — source: `src/test/kotlin/com/plcoding/cardigan/CardiganApplicationTests.kt`.
- `application.yaml` imports `optional:file:.env[.properties]`, a path relative to the working directory — source: `src/main/resources/application.yaml`.
- `.env` is git-ignored and is a named pipe. It wasn't read during planning, so its keys are unknown — source: `ls -l .env`, `.gitignore`.
- The Supabase dev branch runs PostgreSQL 17.11, and `public` has no tables — source: Supabase MCP query on `hcplzkrgqjckrztvoext`.
- The local machine has Temurin JDK 25 installed, while the default `java` on the path is 21, and Docker 20.10.14 — source: `/usr/libexec/java_home -V`, `java -version`, `docker --version`.
- There's no `.github` directory yet. The repo is private on GitHub with `master` as its default branch — source: repo listing, `gh repo view`.
- The breakdown defines backend work chunks but no backend module split. `be-scaffolding` lists "single module vs. multi-module" as an open question. "Isolated feature modules" refers only to the clients, and "transfer" doesn't appear — source: `docs/breakdown/` in `cardigan-shared-specs`.
- `be-scaffolding` expects local Docker Postgres for development, an initial empty migration, and DB-backed tests in CI — source: `docs/breakdown/chunks/02-be-scaffolding.md`.
- Import must be all or nothing across the board, its columns, its cards and new labels — source: `board-export-format`, "Import is all or nothing".
- Deleting a label removes it from every card and changes those cards' updated times — source: `domain-label`, "Deleting a label removes it from cards".
- Every API endpoint is served under `/api/v1`. This change adds no API endpoint — source: `api-conventions`, "All endpoints use a versioned base path".

## Out of scope

- All product functionality: endpoints, entities, tables, auth flows, error model, validation.
- Spring Security, JWT, springdoc and the OpenAPI snapshot test, and the Modulith event registry.
- Migration files of any kind.
- Connecting to or changing the production Supabase branch, server provisioning and CD (`infra-vps`).
- External logging services and structured logs.
- docker-compose and a `local` profile.
- Building the image in CI.
- Changes to the `spring-*` skills.

## Deferred

- Writing the baseline migration, and moving tests to real migrations with `validate` — settle before: the first production deploy, outside this change.
- Production database connection mode (pooler vs. direct) — settle before: `infra-vps`, outside this change.
- Whether `.env` being a named pipe works with Spring's config import, including its path once the app runs from `:app` — settle before: implementation.
