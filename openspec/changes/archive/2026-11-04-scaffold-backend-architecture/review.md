# Artifact review (light): scaffold-backend-architecture

**Verdict:** Ready
**Reviewed:** 2026-11-04, schema `careful-planning`, repo kind `code`

There were no blockers, and all 15 findings have been applied. The table shows the counts as found.

| Pass | Blocker | Major | Minor |
|---|---|---|---|
| Testability | 0 | 5 | 3 |
| Security | 0 | 0 | 1 |
| Hidden assumptions | 0 | 1 | 2 |
| Design contract | 0 | 0 | 3 |

## Findings

### T1: The no-profile safety rule can't fail
**Severity:** major · **Status:** applied
**Location:** `specs/runtime-environments/spec.md` > The environment is chosen explicitly > Nothing selected; `tasks.md` > 4.2
**Current text:** "neither `dev` nor `prod` is active, and the service uses the schema behavior of `prod`" and "Verify `dev` isn't active and Hibernate reports `validate`."
**Failure scenario:** "Schema behavior of `prod`" is not observable, and with no entities `update` and `validate` do the same thing. If the base file wrongly has `ddl-auto: update`, a no-profile run still boots cleanly. The "Prod never changes the schema" requirement names "no profile", but its scenarios only cover `prod`. This is the guarantee D5 exists for. Wrong implementation caught: `update` in the base file with `validate` only in `application-prod.yaml`.
**Fix:** In the spec, change the scenario's THEN to "neither `dev` nor `prod` is active", and add:

```markdown
#### Scenario: No profile never updates the schema
- **WHEN** the service starts with no active profile and the data model has a table the schema lacks
- **THEN** startup fails and the table is not created
```

Replace task 4.2 with: "Start with no profile against a fresh throwaway Postgres 17 container, with one temporary entity on a local, uncommitted edit. Verify `dev` isn't active, startup fails on schema validation, and the table doesn't exist. Remove the entity."

### T2: Every prod scenario passes with Flyway turned off
**Severity:** major · **Status:** applied
**Location:** `specs/runtime-environments/spec.md` > Prod never changes the schema from the application model; `tasks.md` > 4.5
**Current text:** "**THEN** startup completes and no application tables are created"
**Failure scenario:** D3 requires Flyway enabled on prod, but no scenario observes Flyway running. With no migration files, a build with Flyway disabled or its starter missing passes both scenarios and task 4.5. The gap only shows at the first deploy, when the baseline migration silently doesn't run and `validate` fails. Wrong implementation caught: `spring.flyway.enabled: false` in the base file, or `spring-boot-starter-flyway` left out of `cardigan.spring.jpa`.
**Fix:** Add to the spec:

```markdown
#### Scenario: Migrations run on prod
- **WHEN** the service starts with the `prod` profile against an empty database while no migration files and no entities exist
- **THEN** the schemas `user_service` and `board_service` exist, Flyway's history table exists in `user_service`, and no other table exists
```

Add to task 4.5 after "Verify it boots": "and that the two schemas and Flyway's history table exist."

### T3: A missing username or password isn't actually detected
**Severity:** major · **Status:** applied
**Location:** `specs/runtime-environments/spec.md` > Database settings come only from the environment > Value missing; `tasks.md` > 4.3
**Current text:** "**WHEN** the service starts and any of `DB_URL`, `DB_USERNAME` or `DB_PASSWORD` is missing" / "**THEN** startup fails and the service does not begin serving requests"
**Failure scenario:** Spring Boot's property binder leaves an unresolved `${DB_PASSWORD}` in place as literal text. A missing `DB_URL` fails because the value doesn't start with `jdbc`. A missing username or password is sent to Postgres as the literal string `${DB_PASSWORD}`. Startup fails only if the server rejects it, so a Postgres with `trust` auth boots successfully and the scenario is false. The same mechanism is why the design's tests work with no `DB_*` set. Wrong implementation caught: a throwaway container started with `POSTGRES_HOST_AUTH_METHOD=trust` passing the check by accident, or a change that adds default values.
**Fix:** Replace the scenario with:

```markdown
#### Scenario: URL missing
- **WHEN** the service starts and `DB_URL` is missing
- **THEN** startup fails and the service does not begin serving requests

#### Scenario: Credentials missing
- **WHEN** the service starts against a Postgres that requires password authentication and `DB_USERNAME` or `DB_PASSWORD` is missing
- **THEN** startup fails and the service does not begin serving requests
```

Add to `design.md` > Configuration files: "An unresolved `${DB_*}` stays as literal text. Missing credentials fail only because the server rejects them, so they aren't detected on a server with `trust` auth." Change task 4.3 to: "Against a throwaway Postgres with password authentication, start once each with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` missing, then with a wrong password." If a clear "missing value" failure on any server matters to you, the alternative is a small startup check in `:app`. That's your call, so I didn't make it the fix.

### T4: The hand checks read the real `.env`
**Severity:** major · **Status:** applied
**Location:** `tasks.md` > 4.2 to 4.6, with `design.md` > Running locally
**Current text:** "Start with a missing `DB_PASSWORD`, then with a wrong one."
**Failure scenario:** The only documented way to start is `bootRun`, which now runs from the repo root and imports `.env`. Unsetting `DB_PASSWORD` in the shell doesn't make it missing, so 4.3 tests nothing. If `DB_URL` isn't overridden in 4.2 or 4.5, the app runs against the Supabase dev branch, and on `prod` or no profile Flyway creates schemas and a history table there. Wrong implementation caught: a broken "missing value" path that appears to work because `.env` still supplies the value.
**Fix:** Add at the start of section 4 in `tasks.md`: "Except for 4.1, run from a directory with no `.env`, never through `bootRun`: `./gradlew :app:bootJar`, then from an empty temp directory run `$(/usr/libexec/java_home -v 25)/bin/java -jar <repo>/app/build/libs/<jar>` with `DB_*` set in that command. Run `bootJar` again after adding or removing the temporary entity."

### T5: `GET /actuator` stays exposed
**Severity:** major · **Status:** applied
**Location:** `specs/service-health/spec.md` > Only health is exposed operationally; `design.md` > Health settings
**Current text:** "The service SHALL NOT expose any other operational endpoint over HTTP."
**Failure scenario:** The design's settings leave Actuator's discovery page on by default, so `GET /actuator` returns 200 with a list of links. That breaks the requirement, but the scenario and task 4.1 only try `/actuator/env` and `/actuator/beans`, so the check passes. Wrong implementation caught: the design's current config.
**Fix:** Add to the YAML in `design.md`: `management.endpoints.web.discovery.enabled: false`. Change the scenario's WHEN to "a client calls `GET /actuator`, `GET /actuator/env` or `GET /actuator/beans`". Add `GET /actuator` to the 404 check in task 4.1.

### T6: Dev's "no migrations" and "missing columns" have no check
**Severity:** minor · **Status:** applied
**Location:** `specs/runtime-environments/spec.md` > Dev updates the schema from the application model; `tasks.md` > 4.1
**Current text:** "Check with the Supabase MCP that nothing unexpected was created."
**Failure scenario:** The requirement promises new schemas, new columns and no migrations, but scenarios only cover a new table and retained data, and "nothing unexpected" has no pass condition. Wrong implementation caught: `ddl-auto: create-only`, which adds tables but no columns, or Flyway left on in dev.
**Fix:** Add to the spec:

```markdown
#### Scenario: New column on dev
- **WHEN** an existing table's entity gains a column and the service restarts with the `dev` profile
- **THEN** the column exists and existing rows are unchanged

#### Scenario: No migrations on dev
- **WHEN** the service starts with the `dev` profile against an empty database
- **THEN** no Flyway history table exists
```

Change the end of task 4.1 to: "Check with the Supabase MCP that no `flyway_schema_history` table and no new schema exist." Add a new column to the temporary entity in task 4.4.

### T7: The image checks are narrower than the requirement
**Severity:** minor · **Status:** applied
**Location:** `specs/delivery-pipeline/spec.md` > The service ships as a self-contained container image
**Current text:** "**THEN** it contains no `.env` file" and "**THEN** the container exits with a startup failure"
**Failure scenario:** The requirement says "or other secret", but only `.env` is checked, and "startup failure" has no observable value. Wrong implementation caught: `ENV DB_PASSWORD=...` or a `--build-arg` secret in the Dockerfile.
**Fix:** Change the first THEN to "it contains no `.env` file, and neither `docker inspect` nor `docker history` shows a `DB_*` value". Change the second to "the container exits with a non-zero exit code". Add both to tasks 5.2.

### T8: "Not treated as an endpoint clients depend on" can't be tested
**Severity:** minor · **Status:** applied
**Location:** `specs/service-health/spec.md` > Health is not part of the client API
**Current text:** "SHALL NOT be treated as an endpoint clients depend on."
**Failure scenario:** No test can observe how something is "treated", and the scenario only inspects a path. Wrong implementation caught by the rewrite: `management.endpoints.web.base-path: /api/v1/actuator`.
**Fix:** Replace the requirement text with "The health endpoint SHALL be served at `/actuator/health`, outside the `/api/v1` base path defined by `api-conventions` (\"All endpoints use a versioned base path\")." Change the scenario to: WHEN `GET /api/v1/actuator/health` is called, THEN the response is HTTP 404.

### S1: Nothing states TLS for the database connection
**Severity:** minor · **Status:** applied
**Location:** `design.md` > Risks / Trade-offs
**Current text:** none; the artifacts don't mention it.
**Failure scenario:** The session pooler is reached over the public internet. pgjdbc's default `sslmode=prefer` falls back to plaintext if the server offers no TLS and never checks the certificate. So an attacker on the network path could read the database password. This matters most for prod, whose connection is deferred without TLS on the list.
**Fix:** Add to `design.md` > Risks: "**[DB connection isn't verified]** pgjdbc's default doesn't require TLS or check the certificate. → `DB_URL` for Supabase uses `sslmode=require` in dev. `infra-vps` decides on `verify-full` with Supabase's CA for prod."

### A1: The format of `DB_URL` isn't defined
**Severity:** major · **Status:** applied
**Location:** `specs/runtime-environments/spec.md` > Database settings come only from the environment; `design.md` > Running locally
**Current text:** "The service SHALL read its database connection from `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`"
**Failure scenario:** The specs assume a JDBC URL without credentials. Supabase's dashboard offers a `postgresql://` URI, which fails on boot, and a JDBC string that carries `password=` in the query. If that second string is used, the password sits in a value that can reach error messages and logs, while task 4.3 passes because it only looks for the separate `DB_PASSWORD`. The specs also assume a role that may create schemas, which Flyway and `create_namespaces` both need.
**Fix:** Add to the requirement: "`DB_URL` SHALL be a JDBC URL with no credentials in it. The database role SHALL be allowed to create schemas." Add to `design.md` > Running locally: "`DB_URL` has the form `jdbc:postgresql://<pooler-host>:5432/postgres`. Supabase's dashboard strings don't fit as they are: the URI form lacks `jdbc:`, and the JDBC form includes the password. The session pooler user is `postgres.<project-ref>`."

### A2: `update` can change existing columns
**Severity:** minor · **Status:** applied
**Location:** `specs/runtime-environments/spec.md` > Dev updates the schema from the application model
**Current text:** "Removing or changing existing objects is a manual step."
**Failure scenario:** Since Hibernate 6.2, `update` can alter a column's type on Postgres when the entity's type changes. So a developer who relies on this sentence can have a dev column rewritten at startup. The decision to use `update` stands, but this sentence and the matching wording in D2 are too strong.
**Fix:** Change to: "Dropping existing tables and columns is a manual step. Hibernate may alter a column's type when the entity's type changes."

### A3: IDE runs get a different working directory
**Severity:** minor · **Status:** applied
**Location:** `design.md` > Running locally
**Current text:** "`:app`'s `bootRun` sets its working directory to the repo root"
**Failure scenario:** The design allows an IDE run configuration for the profile, but only `bootRun` gets the working directory fix. An IntelliJ run configuration that uses the `app/` module directory finds no `.env` and fails with a confusing `jdbc` URL error.
**Fix:** Add: "An IDE run configuration must also set its working directory to the repo root."

### D1: Task 2.4 deletes a file that task 3.1 moves
**Severity:** minor · **Status:** applied
**Location:** `tasks.md` > 2.4 and 3.1
**Current text:** "delete the old root `src/`" and "Move `application.yaml` to `:app`"
**Failure scenario:** By the time 3.1 runs, the file is gone from the root and from the working tree, and the `.env` import line it carries could be lost.
**Fix:** In 2.4, add "move `application.yaml` unchanged to `app/src/main/resources/`" before "delete the old root `src/`". Change the start of 3.1 to "Add the base, `dev` and `prod` settings to `application.yaml`".

### D2: Property keys in the config table are incomplete
**Severity:** minor · **Status:** applied
**Location:** `design.md` > Configuration files; Test setup
**Current text:** "`hibernate.hbm2ddl.create_namespaces`"
**Failure scenario:** Written as shown at the top level of the YAML, Spring ignores the key silently. Schemas then aren't created, and the failure only shows in task 3.3 or 4.4.
**Fix:** Change the key to `spring.jpa.properties.hibernate.hbm2ddl.create_namespaces` in the table and in the `application-test.yaml` note.

### D3: Health stays database-free only until someone adds an integration
**Severity:** minor · **Status:** applied
**Location:** `design.md` > Health settings
**Current text:** "Only the built-in ping indicator remains, so the response stays `UP` while the process lives (D9)."
**Failure scenario:** The config turns off two indicators by name. A later starter with its own indicator, such as mail, turns on by itself, and an outage at that provider makes health return 503. That breaks "SHALL NOT depend on the state of the database or other external systems".
**Fix:** Replace the `management.health` block with:

```yaml
  health:
    defaults.enabled: false
    ping.enabled: true
```

## Trust boundaries

- **Public HTTP:** an attacker would probe Actuator for config and internals. The spec stops `env` and `beans`, but not the `/actuator` discovery page (T5). With Spring Security absent there is nothing else to attack. The smoke test will catch `be-common` closing health by accident.
- **Database connection:** an attacker on the network path could intercept credentials. Not stopped (S1).
- **Secrets in the repo, image and logs:** `.env` is git-ignored and in `.dockerignore`, and the spec bans defaults. Checks are narrower than the rule (T7), and a password inside `DB_URL` isn't covered (A1).
- **Dev settings reaching prod:** this needs a wrong profile or missing profile. The design stops the no-profile case, but its check can't fail (T1). Someone setting `dev` explicitly with prod `DB_*` isn't stopped; it takes a deliberate override, and project rules forbid touching prod.
- **Config regressions after this change:** the automated test uses the `test` profile, so a later edit that weakens the base or `prod` schema settings passes CI. No finding, because D11 and D12 settle on one smoke test and hand checks. It's worth moving tests to real migrations on the Deferred list.
- **CI:** read-only token and no secrets, on a private repo. Nothing realistic to report for this change.

## Coverage

- Checked all 19 requirements and 34 scenarios across the four delta specs, plus `proposal.md`, `design.md`, `tasks.md` and `decisions.md`. No artifacts are missing. `openspec validate --strict` passes.
- Also read: `AGENTS.md`, `openspec/config.yaml`, the current build files and `application.yaml`, parts of `spring-module-structure` and `spring-persistence-jpa`, shared `api-conventions`, and breakdown chunk `02-be-scaffolding.md`.
- Not read: the other `spring-*` skills, the other shared specs, and `05-be-common.md` and `OVERVIEW.md`.
- Nothing was run. T2, T3, T5, A1 and A2 rest on my knowledge of Spring Boot, Flyway, Hibernate and Supabase, not on a test. T2's fix assumes Flyway creates its history table when there are no migrations; adjust the wording if task 4.5 shows otherwise. T3 can be confirmed cheaply during task 4.3.

## Applied changes

All findings were applied as recommended.

- **Files changed:** `specs/runtime-environments/spec.md`, `specs/service-health/spec.md`, `specs/delivery-pipeline/spec.md`, `design.md`, `tasks.md`.
- **Added beyond the listed fixes:** task 4.1 also checks `GET /api/v1/actuator/health` (T8), task 4.4 also checks for no Flyway history table (T6), and task 7.1 also documents the `DB_URL` form (A1).
- **Contradiction with `decisions.md`:** D2 says Hibernate "never drops or changes existing tables and columns". The spec now says it may alter a column's type (A2). `decisions.md` wasn't edited, so this wording difference remains. The decision to use `update` is unaffected.
- **T3:** the spec was narrowed. The optional startup check in `:app` wasn't added, so missing credentials go undetected on a server with `trust` auth.
- **S1:** dev uses `sslmode=require`. TLS verification for prod is left to `infra-vps`, and isn't yet on the Deferred list in `decisions.md`.
- **Accepted risks:** none rejected.
- **Stale non-standard artifacts:** none.
