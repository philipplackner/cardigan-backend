# Design

## Context

See `proposal.md` for the motivation and `decisions.md` for every settled choice (D1–D17).

Current state:

- One Gradle project with versions inline: Kotlin 2.3.21, Spring Boot 4.1.1, Gradle 9.8.1, the dependency-management plugin, and Spring Data JDBC.
- `application.yaml` imports `optional:file:.env[.properties]`, a path relative to the working directory.
- `.env` is a named pipe. Its contents are unknown because it wasn't read during planning.
- The Supabase dev branch runs Postgres 17.11, and `public` has no tables.

## Goals / Non-Goals

**Goals:**

- `./gradlew build` passes on the new layout, on a machine or runner with only JDK 25 and Docker.
- The app boots against the Supabase dev branch with `dev`, and against an empty Postgres with `prod`.
- Adding a feature later means adding code to an existing module, without touching the build logic.

**Non-Goals:**

- `ApplicationConfig`, the `Clock` bean and other beans with no consumer yet. They arrive with their first user.
- Hikari tuning for production. That belongs to `infra-vps`.

## Decisions

### Build layout

The layout follows the `spring-module-structure` skill with `<app>` set to `cardigan`:

- `build-logic/` is an included build with the plugins `cardigan.kotlin.jvm`, `cardigan.spring.module`, `cardigan.spring.jpa` and `cardigan.spring.application`.
- `gradle/libs.versions.toml` holds every version. Kotlin stays at 2.3.21 and Boot at 4.1.1, because upgrading is not part of this change. New versions, such as AssertK, are looked up at implementation time.
- The Boot BOM is applied as `platform()`, not through the dependency-management plugin, as the skill explains.
- `gradle.properties` comes from the skill, including parallel execution, build cache and configuration cache.

| Module | Plugins | Extra dependencies |
|---|---|---|
| `:common` | `spring.module`, `java-test-fixtures` | Test fixtures: Boot Testcontainers, Testcontainers Postgres |
| `:user`, `:board` | `spring.module`, `spring.jpa` | None yet |
| `:app` | `spring.application`, `spring.jpa` | `projects.common`, `projects.user`, `projects.board`, starter-webmvc, starter-actuator, test starters |

**Deviation from the skill's examples:** `:common` doesn't get webmvc or security dependencies yet, and `:app` doesn't get security or the OAuth2 resource server (D4, D15). `spring-boot-starter-restclient` is also dropped until a module needs it. These follow from D4 and D15, and from the goal of adding only what's needed to boot.

`CardiganApplication` moves to `app/src/main/kotlin/com/plcoding/cardigan/` and gets `@ConfigurationPropertiesScan`, as `AGENTS.md` lays out. Its package is the parent of all module packages, so component and entity scanning cover every module.

### Configuration files

| Setting | `application.yaml` (base) | `application-dev.yaml` | `application-prod.yaml` |
|---|---|---|---|
| `spring.datasource.url/username/password` | `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` | — | — |
| `spring.jpa.hibernate.ddl-auto` | `validate` | `update` | `validate` |
| `spring.flyway.enabled` | `true` | `false` | `true` |
| `spring.flyway.schemas` | `user_service`, `board_service` | — | — |
| `spring.flyway.locations` | `db/migration/user`, `db/migration/board` | — | — |
| `spring.jpa.properties.hibernate.hbm2ddl.create_namespaces` | — | `true` | — |
| `spring.datasource.hikari.maximum-pool-size` | — | `5` | — |
| `spring.threads.virtual.enabled` | `true` | — | — |
| `spring.jpa.open-in-view` | `false` | — | — |

- The non-destructive settings live in the base file, so a run with no profile behaves like `prod` (D5). The prod file repeats them on purpose, because they must not change if the base default changes.
- Hibernate `update` doesn't create Postgres schemas by itself, so `create_namespaces` is needed once Flyway is off and entities name their schema.
- The dev pool size of 5 is a technical default, chosen to stay well within the session pooler's per-project limit. It's easy to change.
- An unresolved `${DB_*}` stays as literal text. Missing credentials fail only because the server rejects them, so they aren't detected on a server with `trust` auth.

Health settings in the base file:

```yaml
management:
  endpoints.web:
    exposure.include: health
    discovery.enabled: false
  endpoint.health:
    show-details: never
    probes.enabled: false
  health:
    defaults.enabled: false
    ping.enabled: true
```

Only the built-in ping indicator remains, so the response stays `UP` while the process lives (D9). Boot 4 enables the probe groups by default, which adds a `groups` field to the response and the `/actuator/health/liveness` and `/readiness` paths, so they're turned off.

There's no logging config beyond Boot's defaults (D10).

### Running locally

- `:app`'s `bootRun` sets its working directory to the repo root, so `file:.env` keeps resolving to the root `.env` instead of `app/.env`. An IDE run configuration must also set its working directory to the repo root.
- `DB_URL` has the form `jdbc:postgresql://<pooler-host>:5432/postgres`. Supabase's dashboard strings don't fit as they are: the URI form lacks `jdbc:`, and the JDBC form includes the password. The session pooler user is `postgres.<project-ref>`.
- `SPRING_PROFILES_ACTIVE` must come from the shell or the IDE run configuration: `SPRING_PROFILES_ACTIVE=dev ./gradlew :app:bootRun`. A key with that name in `.env` wouldn't work, because Spring only maps that upper-case form from real environment variables.
- The `Test` task keeps its default working directory (`app/`), so tests never see the root `.env`. That prevents local tests from picking up Supabase credentials, and from reading the named pipe.
- `.env` is a named pipe served by a secret manager, confirmed by the user on 2026-11-04. It holds `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` for the Supabase dev session pooler. The app reads it through the existing `optional:file:.env[.properties]` import. If Spring can't read the pipe, `DB_*` are passed as real environment variables from the shell or IDE run configuration instead, which needs no code change. A pipe with no writer blocks whoever opens it, so `.env` is only read through `bootRun` or an IDE run from the repo root.

### Test setup

- `PostgresTestConfig` goes in `:common` test fixtures, as `AGENTS.md` lays out, with a `postgres:17` container and `@ServiceConnection`.
- `app/src/test/resources/application-test.yaml` sets `ddl-auto: create-drop`, Flyway off and `spring.jpa.properties.hibernate.hbm2ddl.create_namespaces: true` (D11).
- The one smoke test is a `@SpringBootTest(RANDOM_PORT)` with `@ActiveProfiles("test")`. It replaces `CardiganApplicationTests`, and calls `GET /actuator/health` through `RestTestClient`.
- `@ServiceConnection` supplies the connection, so the unresolved `${DB_*}` placeholders are never used in tests.

The profile and health scenarios that need a real restart or a stopped database are checked by hand in `tasks.md` against a throwaway Postgres container, because D11 settled on one automated test.

### Dockerfile

- Build stage: `eclipse-temurin:25-jdk`, runs `./gradlew :app:bootJar --no-daemon`.
- Runtime stage: `eclipse-temurin:25-jre`, with a dedicated non-root user, `ENV SPRING_PROFILES_ACTIVE=prod`, and `EXPOSE 8080`.
- `.dockerignore` excludes `.env`, `.git`, `.gradle`, `.idea`, `.kotlin` and all `build` folders. Excluding `.env` also keeps the named pipe out of the build context.

### CI

`.github/workflows/ci.yml` runs on `pull_request` and on `push` to any branch:

- Runner: `ubuntu-latest`, which includes Docker for Testcontainers.
- Steps: checkout, `actions/setup-java` with Temurin 25, `gradle/actions/setup-gradle`, then `./gradlew build`.
- Permissions: `contents: read`. No secrets are used.
- Action versions are looked up at implementation time.

### Commits

The Git index already holds staged, unrelated `.claude/skills/openspec-artifact-review*` files. Each commit in this change must name its own paths, so those files stay staged and out of these commits.

## Risks / Trade-offs

- **[Dev and prod schemas are built differently]** Hibernate `update` on dev can hide a mistake that only shows up when the baseline migration meets `validate`. → The baseline migration is required before the first deploy (D3), and moving tests to real migrations is on the Deferred list.
- **[`update` leaves old objects behind]** Renamed columns stay in the dev database and can mask bugs. → Wipe the dev schemas by hand when they drift (D2). Tests build a fresh schema every run.
- **[Dev database is shared state]** Each running instance applies its own entity changes to the same Supabase dev branch, so branches with different models can interfere. → Accepted for now. `DB_*` can point at a local Postgres (D8).
- **[Health doesn't reflect the database]** A Supabase outage after startup won't show in health (D9). → Accepted. Failed requests and logs show it instead.
- **[DB connection isn't verified]** pgjdbc's default doesn't require TLS or check the certificate. → `DB_URL` for Supabase uses `sslmode=require` in dev. `infra-vps` decides on `verify-full` with Supabase's CA for prod.
- **[Dockerfile unchecked in CI]** It can break unnoticed (D12). → Checked by hand in this change, and CD in `infra-vps` will build it for real.
- **[Empty prod has no tables]** Once entities exist, `prod` won't boot until the baseline migration exists. → Intended. `validate` makes this a clear failure.
- **[`.env` is a named pipe]** Spring may not read a pipe through `file:.env`, may block on it, or may only get its contents once. → Settled with the user as the first implementation task, before any config work relies on it.
- **[Docker 20.10.14 locally]** Testcontainers 2 may need a newer Docker API than this older local install offers. → The smoke test shows it immediately. If it fails, tell the user rather than working around it.

## Migration Plan

There's no data or client to migrate, and nothing is deployed. Rollback is reverting the commits. The dev branch only gains tables once entities exist.

## Open Questions

None that affect the approach. The `.env` question is handled by task 1.1.
