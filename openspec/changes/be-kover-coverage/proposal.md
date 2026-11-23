# Proposal

## Why

The backend has no way to measure test coverage. We want to generate a coverage report from the terminal for one module, several modules or all of them, and to see coverage for only the lines changed in a chunk of work, such as a branch compared with `master`.

## What Changes

- A new convention plugin, `cardigan.kover`, in `build-logic`. A module opts in to coverage by applying it. `:app`, `:common`, `:user` and `:board` all apply it.
- A root task, `./gradlew coverageReport`, writes an XML and an HTML report and prints a terminal summary of line and branch coverage. `-Pcoverage.modules=user,board` limits the report to those modules. Without it, all opted-in modules are reported. Lines hit by any test count, `:app` E2E tests included.
- Optional `-Pcoverage.minLine=<percent>` and `-Pcoverage.minBranch=<percent>` make `coverageReport` fail when the selection misses them. Nothing sets them by default.
- The application main class and `@Configuration` and `@ConfigurationProperties` classes are excluded from coverage by annotation or name pattern.
- A root task, `./gradlew coverageDiff`, builds the XML report for the same selection and runs diff-cover through `uvx` at a pinned version, with its dependencies resolved as of a pinned date. It shows coverage of the lines changed since the merge base with `origin/master`, without fetching (overridable with `-Pcoverage.compareBranch`), staged, unstaged and untracked changes included. It prints a terminal summary and writes an HTML report under `build/`. It never fails because of low coverage.
- Coverage runs only on demand. Test tasks are instrumented only when a coverage or Kover task is requested, so `./gradlew build` and `test` are neither instrumented nor slowed. A coverage task requested in a way that leaves instrumentation off, such as the abbreviation `./gradlew cR`, fails and asks for the full task name instead of writing an empty report. `check`, the commit gates and CI don't change.
- New build dependency: the Kover Gradle plugin. New local prerequisite for `coverageDiff` only: `uv`.

Not breaking: no API, schema or runtime behavior changes.

## Shared specs

This change implements and depends on no `cardigan-shared-specs` requirement. Coverage policy is not specified anywhere on purpose (see decisions.md D1).

## Capabilities

### New Capabilities
- `test-coverage`: on-demand coverage reports for chosen backend modules, optional command-line thresholds, and coverage of changed lines against a base branch.

### Modified Capabilities

None. CI and the build stay as `delivery-pipeline` describes them.

## Impact

- `build-logic/convention`: a new plugin class and its registration, plus the Kover Gradle plugin as a `compileOnly` dependency.
- `gradle/libs.versions.toml`: the Kover version, plugin artifact and `cardigan.kover` plugin alias.
- Root `build.gradle.kts`: the aggregation and the `coverageReport` and `coverageDiff` tasks, applied through a root convention plugin.
- `app`, `common`, `user`, `board` `build.gradle.kts`: apply `cardigan.kover`.
- `CLAUDE.md`: the coverage commands in the Testing command table, and the `uv` prerequisite.
- No production code, API, OpenAPI snapshot or database change.
