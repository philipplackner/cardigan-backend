# Spec Delta

## Purpose

Defines the automated checks every change goes through and the container image the service ships as, so a broken build is caught early and the service can run anywhere without the source tree.

## ADDED Requirements

### Requirement: CI builds and tests every change
CI SHALL run the full build, including all tests, on every pull request and every push to `master`. A failing build or test SHALL fail the CI run.

#### Scenario: Pull request
- **WHEN** a pull request is opened or updated
- **THEN** CI compiles all modules and runs all tests

#### Scenario: Push to master
- **WHEN** a commit is pushed to `master`
- **THEN** CI compiles all modules and runs all tests

#### Scenario: Failing test
- **WHEN** any test fails during a CI run
- **THEN** the run is reported as failed

### Requirement: CI needs no external services or secrets
CI SHALL run without access to Supabase and without repository secrets. Tests that need a database SHALL use a disposable Postgres of the same major version as Supabase, started for the test run. CI SHALL NOT build images or deploy anything.

#### Scenario: Clean CI run
- **WHEN** CI runs with no repository secrets configured
- **THEN** the database-backed tests pass against a disposable Postgres 17

#### Scenario: No deployment
- **WHEN** a CI run completes successfully
- **THEN** no image has been published and nothing has been deployed

### Requirement: Tests prove the service boots
The build SHALL include a smoke test that starts the full service against a disposable Postgres and checks its health endpoint over HTTP. The test SHALL NOT use the Supabase database.

#### Scenario: Smoke test
- **WHEN** the build runs its tests
- **THEN** the service starts against the disposable Postgres, and `GET /actuator/health` returns HTTP 200 with `status` equal to `UP`

### Requirement: The service ships as a self-contained container image
The image SHALL build from a clean checkout with no local build step, and SHALL contain no `.env` file or other secret. It SHALL run as a non-root user with the `prod` profile active, and SHALL take `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` at runtime.

#### Scenario: Build from a clean checkout
- **WHEN** `docker build` runs on a fresh clone with no prior Gradle build
- **THEN** the image builds successfully

#### Scenario: Run with database settings
- **WHEN** the image runs with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` pointing at a reachable Postgres
- **THEN** the service starts with the `prod` profile as a non-root user, and `GET /actuator/health` returns HTTP 200

#### Scenario: Run without database settings
- **WHEN** the image runs without `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`
- **THEN** the container exits with a non-zero exit code

#### Scenario: No secrets in the image
- **WHEN** the image's file system is inspected
- **THEN** it contains no `.env` file, and neither `docker inspect` nor `docker history` shows a `DB_*` value
