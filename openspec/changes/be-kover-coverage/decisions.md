# Decisions: be-kover-coverage

Confirmed with Philipp Lackner on 2026-11-23. The orchestrator session `cardigan-3c` asked the user and relayed the answers.

## Summary

The backend gets on-demand test coverage from the terminal. Each module opts in with one convention plugin. One root task writes an XML and an HTML report plus a terminal summary for all modules or any chosen subset. A second task runs diff-cover against the report to show coverage of only the changed lines. Nothing about coverage is enforced: no shared spec, no rule in `./gradlew build`, no CI change. The only thing that can fail is a threshold passed explicitly on the command line.

## Decisions

### D1 — Scope
- **Question**: What does this change cover?
- **Decision**: Only the tooling to generate coverage reports from the terminal. No coverage policy is written down: no shared-spec requirement, no enforced thresholds, no rules in the platform specs.
- **Alternatives**: A shared coverage policy across backend, mobile and web.
- **Why**: "I just want the related setup so we can start generating a report from the terminal."
- **Depends on**: —

### D2 — Which modules opt in
- **Question**: Which modules apply the coverage plugin?
- **Decision**: All four: `:app`, `:common`, `:user`, `:board`. A module opts in by applying one convention plugin id, `cardigan.kover`.
- **Alternatives**: `:common`, `:user`, `:board` only; `:user` and `:board` only.
- **Depends on**: —

### D3 — E2E tests count everywhere
- **Question**: Do `:app` E2E tests count toward the coverage of the other modules?
- **Decision**: Yes. A module's coverage counts lines hit by any test in any opted-in module, E2E tests included, whichever modules are selected.
- **Alternatives**: Only in the combined report; never; two report variants.
- **Depends on**: D2

### D4 — Module selection
- **Question**: How is a module combination selected?
- **Decision**: One root task with an optional property: `./gradlew coverageReport -Pcoverage.modules=user,board`. Without the property, all opted-in modules are reported.
- **Alternatives**: A fixed task per module plus "all"; only Kover's stock tasks.
- **Depends on**: D2

### D5 — Report formats
- **Question**: Which report formats?
- **Decision**: XML and HTML, plus a terminal summary of the total line and branch coverage printed after the report.
- **Alternatives**: XML + HTML only; XML only.
- **Depends on**: —

### D6 — Thresholds
- **Question**: Does anything fail on low coverage?
- **Decision**: Nothing fails by default. `coverageReport` accepts optional thresholds, `-Pcoverage.minLine=<percent>` and `-Pcoverage.minBranch=<percent>`, and fails when the selected modules miss one. They are never set in the build or CI and are not part of `build` or `check`. diff-cover is report-only and is never given `--fail-under`.
- **Alternatives**: A diff-cover `--fail-under`; a Kover verify rule in `check`; both.
- **Depends on**: D4, D13

### D7 — Exclusions
- **Question**: Which classes are excluded from coverage?
- **Decision**: Non-logic classes: the application main class, `@Configuration` classes and `@ConfigurationProperties` classes. They are matched by annotation or name pattern, never by hard-coded class names.
- **Alternatives**: Also DTOs and JPA entities; no exclusions; a project `@ExcludeFromCoverage` annotation.
- **Depends on**: —

### D8 — Diff base
- **Question**: What does diff-cover compare against?
- **Decision**: `origin/master` by default. It can be overridden with `-Pcoverage.compareBranch=<ref>`.
- **Alternatives**: Local `master`; always required as an argument.
- **Depends on**: —

### D9 — Uncommitted changes
- **Question**: Do uncommitted changes count as changed lines?
- **Decision**: Yes. Staged, unstaged and untracked changes are included: diff-cover's default plus `--include-untracked`.
- **Alternatives**: Committed changes only; staged and unstaged without untracked files (diff-cover's default).
- **Depends on**: D8

### D10 — diff-cover installation
- **Question**: How is diff-cover installed?
- **Decision**: It runs as `uvx diff-cover==<pinned version>`. The version is pinned in the repo, and the only prerequisite is `uv`.
- **Alternatives**: `pipx install diff-cover`; a Gradle-managed Python venv; a Docker image.
- **Depends on**: —

### D11 — diff-cover invocation
- **Question**: How is the diff check run?
- **Decision**: A Gradle task, `./gradlew coverageDiff [-Pcoverage.modules=…] [-Pcoverage.compareBranch=…]`, builds the Kover XML for the selection and then runs diff-cover on it.
- **Alternatives**: A shell script; documented manual commands.
- **Depends on**: D4, D8, D10

### D12 — diff-cover output
- **Question**: What does the diff check output?
- **Decision**: A terminal summary and an HTML report under `build/`.
- **Alternatives**: Terminal only; terminal + Markdown.
- **Depends on**: D11

### D13 — CI and the build
- **Question**: Does CI or `./gradlew build` run coverage?
- **Decision**: No. Coverage runs only on demand, locally. CI is unchanged, so `delivery-pipeline` gets no delta. Coverage is not part of `./gradlew build` or the commit gates.
- **Alternatives**: CI runs diff-cover on PRs (report-only or failing); `build` always writes reports.
- **Depends on**: D1

### D14 — Tool choices
- **Question**: Which engine and versions?
- **Decision**: Kover with its default engine (not JaCoCo). Add the Kover Gradle plugin to the version catalog at the latest stable version, after checking it against Kotlin 2.3 and Gradle 9. Pin diff-cover to 11.0.4, the same version web and mobile pin. Task 1 re-checks both versions.
- **Alternatives**: The JaCoCo engine through Kover.
- **Depends on**: —

### D15 — Spec placement and name
- **Question**: Where is this specified?
- **Decision**: A new capability, `test-coverage`. Change name `be-kover-coverage`. Test-fixture source sets are not measured.
- **Alternatives**: A delta to `delivery-pipeline`.
- **Depends on**: D13

### D16 — No instrumentation outside coverage runs
- **Question**: How is it guaranteed that normal builds aren't instrumented or slowed by Kover?
- **Decision**: Every opted-in module sets Kover's `instrumentation.disabledForAll` unless the requested task names (`startParameter.taskNames`) include `coverageReport`, `coverageDiff` or a `kover*` task. A task checks this. If it can't be guaranteed, that is reported as a finding. When instrumentation is off, `coverageReport`, `coverageDiff` and every Kover report task refuse to run: they fail with "run with the full task name" before any test runs or report is written. This covers abbreviations such as `./gradlew cR`. This matches the mobile setup.
- **Alternatives**: Rely on Kover's own task-graph behavior.
- **Depends on**: D13

### D17 — Confirmed consequences
- **Question**: Are the consequences of earlier decisions accepted?
- **Decision**: A `coverageReport` limited to some modules still runs all tests, E2E included (Docker needed). The build-logic code is verified by running the commands, with no TestKit or unit tests. With no branches in the selection, branch coverage shows `n/a` and a `minBranch` threshold counts as met. A bad `coverage.*` property fails any Gradle command at configuration time. Module names are accepted with or without a leading `:`. `CLAUDE.md` documents the commands.
- **Alternatives**: —
- **Depends on**: D3, D4, D6

### D18 — `:app` may have no reportable classes
- **Question**: The D7 exclusions remove every `:app` main class (`CardiganApplication`, `CardiganApplicationKt`, `ApplicationConfig`, `SecurityConfig`). Kotlin compiles their lambdas with invokedynamic, so no lambda classes stay measured. What does that mean for `:app`?
- **Decision**: Keep D7 as it is. `:app` stays opted in, so its E2E tests count (D3), but it may have no reportable classes. `-Pcoverage.modules=app` gives an empty report: line and branch coverage `n/a`, exit 0 without minimums.
- **Alternatives**: Narrow D7 so `@Configuration` classes stay measured.
- **Why**: Found during implementation (task 2.1). Answered by the user through `cardigan-3c` on 2026-11-23.
- **Depends on**: D3, D7

### D19 — `koverFindJar` may run in a normal build
- **Question**: Kover makes every test task depend on `koverFindJar`, which copies the agent jar, even with instrumentation off. Is that acceptable in `./gradlew build`?
- **Decision**: Yes. A normal build has no instrumentation and no coverage, report or verify task. `koverFindJar` is allowed.
- **Alternatives**: Remove the dependency by working around Kover internals.
- **Why**: Found during implementation (task 2.4). Answered by the user through `cardigan-3c` on 2026-11-23.
- **Depends on**: D16

### D20 — Convention plugin aliases without a version
- **Question**: Applying `cardigan.kover.report` in the root puts build-logic on the root classpath, so subproject requests with version `unspecified` fail with "already on the classpath with an unknown version". How are the convention aliases declared?
- **Decision**: The `convention-*` entries in `libs.versions.toml` have no `version`. The root also declares the new `kover` plugin alias with `apply false`, like `spring-boot` and the Kotlin plugins.
- **Alternatives**: —
- **Why**: Found during implementation (task 2.1). Accepted by the user through `cardigan-3c` on 2026-11-23.
- **Depends on**: D2

### D21 — The diff task is named `coverageDiff`
- **Question**: What is the diff coverage task called?
- **Decision**: `coverageDiff`, the same name as on mobile. It was `diffCoverage` until implementation. The `coverage.*` properties and the `build/reports/diff-coverage` path don't change.
- **Alternatives**: `diffCoverage`.
- **Why**: On mobile, `dC` was ambiguous with Android's `deviceCheck`. Both repos use the same name. Decided by the user through `cardigan-3c` on 2026-11-23.
- **Depends on**: D11

## Facts

- The modules are `:app`, `:common`, `:user` and `:board` — source: `settings.gradle.kts`.
- The convention plugins live in `build-logic/convention` and are registered in `gradle/libs.versions.toml` with version `unspecified`. The root build applies plugins with `apply false` only — source: `build-logic/convention/build.gradle.kts`, `build.gradle.kts`.
- The build uses Gradle 9.8.1, with the configuration cache, build cache and parallel execution on — source: `gradle/wrapper/gradle-wrapper.properties`, `gradle.properties`.
- Test files: 22 in `:app` (E2E, these exercise `:user`, `:board` and `:common` code), 23 in `:board`, 19 in `:user`, 14 in `:common` — source: `find` over `*Test.kt`.
- CI is one GitHub Actions job running `./gradlew build` on a shallow checkout — source: `.github/workflows/ci.yml`.
- The application main class is `CardiganApplication` (`@SpringBootApplication`) with a top-level `main` in `CardiganApplication.kt`, compiled to `CardiganApplicationKt` — source: `app/src/main/kotlin/com/plcoding/cardigan/CardiganApplication.kt`.
- `@Configuration` and `@ConfigurationProperties` classes exist in all four modules. Some `@Configuration` classes (`WebConfig`, `RequestSizeLimitConfig`, `SecurityConfig`, `JwtConfig`) hold bean wiring with small amounts of logic — source: `grep` over `*/src/main`.
- The latest Kover Gradle plugin found is 0.9.11 (September 2026). The latest diff-cover on PyPI is 11.0.4, which web and mobile also pin — source: plugins.gradle.org, mvnrepository.com, orchestrator session `cardigan-3c` (2026-11-23).
- diff-cover reads JaCoCo XML and needs `--src-roots` to map classes to source files — source: diff-cover README on PyPI.
- `uv` is installed on the dev machine; diff-cover is not — source: `which uv diff-cover`.
- `origin/master` exists locally — source: `git rev-parse --verify origin/master`.

## Out of scope

- Any coverage policy or minimum, in a shared spec or a platform spec.
- CI changes, PR comments, uploaded reports.
- Running coverage as part of `./gradlew build`, `check` or the commit gates.
- Mobile and web coverage.
- diff-cover `--fail-under`.

## Deferred

- The exact Kover and diff-cover versions are confirmed against Kotlin 2.3 and Gradle 9.8 — settle before: implementation (task 1).
