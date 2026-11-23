# Tasks

## 1. Versions and catalog

- [x] 1.1 Confirm the latest stable Kover Gradle plugin (0.9.11 at planning time) works with Kotlin 2.3.21 and Gradle 9.8.1 by reading its release notes. Add the `kover` version, the `kover-gradlePlugin` library and the `convention-kover` / `convention-kover-report` plugin aliases to `gradle/libs.versions.toml`. Verify that `./gradlew help` succeeds.
- [x] 1.2 Re-check the diff-cover pin 11.0.4 (the latest on PyPI at planning time, also pinned by web and mobile) and add it as `diff-cover = "11.0.4"` under `[versions]`, plus `diff-cover-exclude-newer` set to a date on or after that release. If a newer version exists, keep 11.0.4 and report it to the user, so the three repos stay aligned. Verify that `uvx --exclude-newer <date> diff-cover==<version> --version` prints that version.

## 2. Coverage report

- [x] 2.1 Add `cardigan.kover` (applies Kover and excludes the `testFixtures` source set) and `cardigan.kover.report` (root aggregation) to `build-logic/convention`, with `compileOnly` on the Kover plugin. Apply `cardigan.kover` in `:app`, `:common`, `:user`, `:board` and `cardigan.kover.report` in the root. Check whether the pinned Kover supports a project filter on reports, and record the result in design.md if the package-filter fallback is used. Verify that `./gradlew koverXmlReport` writes `build/reports/kover/report.xml` containing classes from all four modules (`:app` only before the 2.2 exclusions, see D18) and none of `MutableClock`, `PostgresTestConfig` or `DatabaseCleaner`.
- [x] 2.2 Add the exclusions (annotations `@Configuration`, `@ConfigurationProperties`, `@SpringBootApplication`, pattern `*ApplicationKt`) to the root report. Verify that the XML contains none of `CardiganApplication`, `CardiganApplicationKt`, `WebConfig`, `JwtProperties` or `BoardProperties`, and still contains `RequestSizeLimitFilter`.
- [x] 2.3 Add `coverage.modules` parsing and validation (configuration time) and the module filter. Verify that `-Pcoverage.modules=user` and `=user,board` (also `=:user, board`) produce reports with only those modules' classes, that `=app,user` contains `:user` classes, no `:app` classes (all excluded, D18) and no `:board` or `:common` classes, that `=app` succeeds with line and branch coverage `n/a`, and that `=cards`, `=` and `=user,` fail before any test with the message listing `app, board, common, user`. Verify that a `:user` class covered only by `:app` E2E tests (for example the user controller) shows as covered with `-Pcoverage.modules=user`.
- [x] 2.4 In `cardigan.kover`, set `instrumentation.disabledForAll` unless `startParameter.taskNames` has an entry whose last path segment is `coverageReport` or `diffCoverage`, or starts with `kover`. Verify these:
  - `./gradlew :user:test --rerun` runs the test JVM without the Kover agent: no `-javaagent` with `kover` in the test task's JVM args (inspect with `--info` or a temporary `doFirst` print, removed afterwards), and no `build/kover` binary report.
  - `./gradlew coverageReport` runs the test JVMs with the agent.
  - The timing of `./gradlew test --rerun` matches the time before Kover was added, within noise.
  - `./gradlew build --dry-run` lists no coverage, report or verify task (`koverFindJar` is allowed, D19), and `build` writes nothing under `build/reports/kover`. If `check` depends on `koverVerify`, remove that dependency in the plugins and verify again.
  - If any of these checks can't be guaranteed, stop and report it to the user as a finding instead of working around it silently.
- [x] 2.5 Move the instrumentation decision into one shared build-logic function used by both plugins. In `cardigan.kover.report`, add the `gradle.taskGraph.whenReady` guard that fails with "Coverage needs instrumentation. Run with the full task name, e.g. ./gradlew coverageReport." when instrumentation is off and the graph has `coverageReport`, `diffCoverage` or any Kover report task. Verify these:
  - `./gradlew cR` and `./gradlew dC` fail with that message, no test task runs, and `build/reports/kover` and `build/reports/diff-coverage` are untouched (delete them first, check they don't exist afterwards).
  - `./gradlew kXR` (abbreviated `koverXmlReport`) fails the same way.
  - `./gradlew coverageReport` passes the guard.
  - All of these also hold on a second run with configuration-cache reuse.
- [x] 2.6 Add the `coverageReport` task: it depends on the XML and HTML reports, prints the selected modules, line and branch percentages with covered/total counts (`n/a` for a zero total) and both report paths, and applies `coverage.minLine` / `coverage.minBranch`. The XML parser must have external DTD loading off. Verify with these commands:
  - `./gradlew coverageReport` prints the summary and succeeds.
  - `-Pcoverage.minLine=100` fails after the reports are written, naming line coverage, the actual and the required percentage.
  - `-Pcoverage.minLine=0 -Pcoverage.minBranch=0` succeeds, and `-Pcoverage.minLine=0.5` is accepted as a decimal.
  - `-Pcoverage.minLine=abc` and `-Pcoverage.minBranch=120` fail before any test runs.
  - Running `./gradlew coverageReport` twice prints the summary both times.
  - Repeat with `--configuration-cache` reuse to check there are no configuration-cache problems.

## 3. Diff coverage

- [x] 3.1 Add the `diffCoverage` task: `uv` check, `git rev-parse --verify` of `coverage.compareBranch` (default `origin/master`), then `uvx --exclude-newer <catalog date> diff-cover==<catalog version> --include-untracked`, run from the root project directory, on `build/reports/kover/report.xml` with every opted-in module's `src/main/kotlin` as repo-relative `--src-roots` and the HTML report at `build/reports/diff-coverage/index.html`. Use the HTML flag the pinned version supports. Verify on this branch with a scratch change:
  - With an unstaged edit to a `:user` source file, `./gradlew diffCoverage` lists that file's specific changed line numbers and writes the HTML report.
  - A new, never `git add`-ed `:board` source file appears in the result.
  - `-Pcoverage.modules=board` omits the `:user` file.
  - `-Pcoverage.compareBranch=does-not-exist` fails with a message naming the ref.
  - `-Pcoverage.minLine=100` still succeeds.
  - With `PATH` stripped of `uv`, the task fails with the "`uv` is required" message.
  - Revert the scratch change afterwards.

## 4. Documentation

- [ ] 4.1 Add `coverageReport` and `diffCoverage` to the `CLAUDE.md` Testing command table. Cover the `coverage.modules`, `coverage.minLine`, `coverage.minBranch` and `coverage.compareBranch` properties, the report paths, the `uv` and Docker prerequisites, that every test runs whatever the selection, that untracked files count in `diffCoverage`, that `diffCoverage` compares against the merge base and never fetches (run `git fetch` first for a fresh `origin/master`), and that coverage tasks need their full names (an abbreviation such as `cR` fails with a message saying so). Note `cardigan.kover` in the "Where does X go?" or module setup guidance so a new module opts in. Verify that every documented command runs as written.

## 5. Integration check

- [ ] 5.1 Run `./gradlew build` and confirm it passes with no coverage task in its output, and that `.github/workflows/ci.yml` is unchanged. Run `openspec validate be-kover-coverage --strict` and confirm it passes.

## Workflow follow-up

- Commit each group per the project's Git workflow once `./gradlew build` passes, only after the user's OK to implement.
- Archive the change only when the user says so.
