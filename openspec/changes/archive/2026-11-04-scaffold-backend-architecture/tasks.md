# Tasks

Commit after each group once `./gradlew build` passes, naming only that group's paths. The Git index holds unrelated staged skill files that must stay out of these commits. Never push.

## 1. Settle the open prerequisites

- [x] 1.1 Ask the user how `.env` is provided (it's a named pipe) and whether it holds `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` for the Supabase dev session pooler, asking for names only, never values. Verify by recording the answer in `design.md`, including how the app will read it if Spring can't read the pipe.

## 2. Multi-module build

- [x] 2.1 Add `gradle/libs.versions.toml` and `gradle.properties` with versions from `design.md`, looking up the latest stable version of each new library. Verify that no version string appears in any `build.gradle.kts`.
- [x] 2.2 Add `build-logic/` with the four `cardigan.*` convention plugins. Verify with `./gradlew -p build-logic build`.
- [x] 2.3 Replace the root build files, and add `:app`, `:common`, `:user` and `:board` with the plugins and dependencies from `design.md`, removing Spring Data JDBC, the dependency-management plugin, the security starters and the restclient starter. Verify `./gradlew projects` lists exactly those four modules, and `./gradlew :user:dependencies :board:dependencies` shows only `:common` as a project dependency.
- [x] 2.4 Move `CardiganApplication` to `:app`, add `@ConfigurationPropertiesScan`, move `application.yaml` unchanged to `app/src/main/resources/`, and delete the old root `src/`. Verify with `./gradlew :app:compileKotlin`.
- [x] 2.5 Add `.gitkeep` files for the layer packages and `db/migration/user` and `db/migration/board`, with no source files in `:user`, `:board` or `:common`. Verify with `git ls-files` on those modules.

## 3. Runtime configuration, health and the smoke test

- [x] 3.1 Add the base, `dev` and `prod` settings to `application.yaml` and the new profile files as described in `design.md`, including the health settings. Verify that no connection value or secret appears in any committed file.
- [x] 3.2 Set the `bootRun` working directory to the repo root in `app/build.gradle.kts`. Verify `SPRING_PROFILES_ACTIVE=dev ./gradlew :app:bootRun` picks up the `DB_*` values.
- [x] 3.3 Add `PostgresTestConfig` to `:common` test fixtures, add `application-test.yaml`, and replace `CardiganApplicationTests` with the health smoke test on Postgres 17. Verify `./gradlew :app:test` passes with no `DB_*` values set. If the local Docker version is too old for Testcontainers, stop and tell the user.
- [x] 3.4 Run `./gradlew build`. Verify it passes.

## 4. Verify against real databases

Except for 4.1, run from a directory with no `.env`, never through `bootRun`: `./gradlew :app:bootJar`, then from an empty temp directory run `$(/usr/libexec/java_home -v 25)/bin/java -jar <repo>/app/build/libs/<jar>` with `DB_*` set in that command. Run `bootJar` again after adding or removing the temporary entity.

- [x] 4.1 Start with the `dev` profile against the Supabase dev branch. Verify the app boots, `GET /actuator/health` returns 200 with only `status: UP`, `GET /actuator`, `GET /actuator/env` and `GET /api/v1/actuator/health` return 404, and logs appear only on the console. Check with the Supabase MCP that no `flyway_schema_history` table and no new schema exist.
- [x] 4.2 Start with no profile against a fresh throwaway Postgres 17 container, with one temporary entity on a local, uncommitted edit. Verify `dev` isn't active, startup fails on schema validation, and the table doesn't exist. Remove the entity.
- [x] 4.3 Against a throwaway Postgres with password authentication, start once each with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` missing, then with a wrong password. Verify startup fails each time, and that the password never appears in the log.
- [x] 4.4 Start with the `dev` profile against the throwaway Postgres, with one temporary entity on a local, uncommitted edit. Verify the table and its schema are created, no Flyway history table exists, and a row inserted by hand survives a restart. Add a column to the entity and restart. Verify the column exists and the row is unchanged, and then remove the temporary entity.
- [x] 4.5 Start with the `prod` profile against an empty throwaway Postgres. Verify it boots with no application tables, and that the two schemas and Flyway's history table exist. Add the same temporary entity, verify startup then fails on `validate`, and remove the entity.
- [x] 4.6 While the app is running against the throwaway Postgres, stop the container. Verify `GET /actuator/health` still returns 200 with `status: UP`.

## 5. Dockerfile

- [x] 5.1 Add the multi-stage `Dockerfile` and `.dockerignore`. Verify `docker build` succeeds from a fresh clone of the working tree with no prior Gradle build.
- [x] 5.2 Run the image against the throwaway Postgres. Verify the `prod` profile is active, the process isn't root, `GET /actuator/health` returns 200, the image contains no `.env`, neither `docker inspect` nor `docker history` shows a `DB_*` value, and running it without `DB_*` exits with a non-zero exit code.

## 6. CI

- [x] 6.1 Add `.github/workflows/ci.yml` as described in `design.md`, looking up current action versions, and make sure `gradlew` is committed as executable. Verify the YAML parses and that `git ls-files -s gradlew` shows mode `100755`.

## 7. Documentation

- [x] 7.1 Update `AGENTS.md`: the current state, modules `:user` and `:board`, schema handling per environment, test schema handling until the baseline migration exists, `DB_*` including the `DB_URL` form, and how to run locally. State that the baseline migration is required before the first production deploy. Verify that a search for `validate` and `Flyway` in `AGENTS.md` shows no statement contradicting D2, D3 or D11.
- [x] 7.2 Update the shared store's `docs/breakdown/` (`chunks/02-be-scaffolding.md`, `chunks/05-be-common.md`, `OVERVIEW.md`, and `graph.json` or `graph.html` where they repeat the same text) to reflect D1, D2, D3, D6, D8 and the users table belonging to `:user`. That repo is currently on branch `07-api-contract-behavior`, so ask the user which branch to commit to first. Verify with `git diff` in that repo, and commit there locally without pushing.

## 8. Final integration check

- [x] 8.1 Run `./gradlew clean build` and `openspec validate scaffold-backend-architecture --strict`. Verify both pass, and that `git status` shows no secrets and no leftover temporary entity.

## Workflow follow-up

- The user pushes the branch, which is the first real run of the CI workflow. Confirm the run passes on GitHub, including the Testcontainers smoke test.
- Before the first production deploy, write the baseline migration and move tests to real migrations (`decisions.md`, Deferred).
- Archive the change after review.
