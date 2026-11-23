# Design

## Context

See proposal.md for the motivation and decisions.md for the settled choices. Facts that shape the approach:

- Convention plugins live in `build-logic/convention`, are written as `Plugin<Project>` classes, and are registered in `libs.versions.toml` with version `unspecified`. They read the catalog through the internal `Project.libs` helper.
- The root `build.gradle.kts` only declares plugins with `apply false`. It has no aggregation logic today.
- The build runs with the configuration cache, the build cache and parallel execution on. Anything new must be configuration-cache compatible.
- `:app` E2E tests exercise most of `:user`, `:board` and `:common`. They need Docker (Testcontainers).

## Goals / Non-Goals

**Goals:**
- One command for a coverage report over any module selection, and one for diff coverage over the same selection.
- No effect on `./gradlew build`, `check`, test times outside coverage runs, or CI.
- Opting in a future module takes one plugin line.

**Non-Goals:**
- Per-module "own tests only" numbers. D3 counts every test everywhere.
- Faster coverage runs for small selections. Every opted-in module's tests run, whatever is selected (see Risks).
- Any enforcement in the build or CI.

## Decisions

### Two convention plugins: `cardigan.kover` for modules, `cardigan.kover.report` for the root

`cardigan.kover` applies `org.jetbrains.kotlinx.kover` to a module. It is the only thing a module adds. It also sets `kover { currentProject { sources { excludedSourceSets.add("testFixtures") } } }`, so only `main` is measured (D15); Kover's default leaves out only `test`. It also lists every `Test` task other than `test` in `instrumentation.disabledForTestTasks`, which keeps them out of the reports' dependencies too. Otherwise a coverage run would also run `:app:updateOpenApiSnapshot`, which rewrites the committed snapshot. `cardigan.kover.report` is applied once in the root `build.gradle.kts`. It applies Kover to the root project, which has no Kotlin plugin and serves as the merge point. It also registers `coverageReport` and `coverageDiff`, and adds every subproject that applies `cardigan.kover` as a `kover(project(...))` dependency of the root. It does this through `subprojects { pluginManager.withPlugin("cardigan.kover") { ... } }`, so opting in needs no edit in the root.

Alternatives:
- **Kover's settings plugin `org.jetbrains.kotlinx.kover.aggregation`.** It has project filters built in and instruments only with `-Pkover`. It is applied in `settings.gradle.kts`, not per module, so it contradicts "opt in with one plugin id". It is also still marked experimental.
- **Listing the modules explicitly in the root build.** Simpler, but a second edit per module.

### Merged report with a per-run class filter

The root Kover report always merges all opted-in modules, both their classes and their test tasks. That is what makes `:app` E2E tests count for `:user` (D3). `coverage.modules` only narrows which classes appear in the report, through a report filter on the root's total report.

- **Primary:** Kover's project filter (`filters.includes.projects`, if the pinned version supports it).
- **Fallback:** a class filter built from the selection as a whole, because Kover excludes always win over includes. If `app` is selected: include `com.plcoding.cardigan.*` and exclude `com.plcoding.cardigan.<m>.*` for every opted-in module `m` other than `app` that is *not* selected. If `app` is not selected: include `com.plcoding.cardigan.<m>.*` for each selected module and exclude nothing.

Task 2.1 checks which one is available before anything else is built on it.

**Result (task 2.1):** Kover 0.9.11's classic plugin supports `filters.includes.projects`, so the primary approach is used, with project paths such as `:user`. The filter is added in `projectsEvaluated` with `filtersAppend`, because a second `filters { }` block replaces the exclusions instead of adding to them.

Alternative: only adding the selected modules as `kover` dependencies. This drops the tests of unselected modules, so E2E coverage would vanish when `app` isn't selected. It breaks D3.

### Validation at configuration time

`coverage.modules`, `coverage.minLine` and `coverage.minBranch` are read with `providers.gradleProperty`. They are parsed in a `gradle.projectsEvaluated` hook in the root plugin, once the set of opted-in modules is known. A bad value throws a `GradleException` with the message the spec requires, before any task, so no test runs.

This also fails other commands that pass a bad `coverage.*` property, for example `./gradlew build -Pcoverage.modules=foo`. That is accepted, because nobody passes these properties to non-coverage commands.

### `coverageReport` is a custom task over Kover's reports

`coverageReport` is a task class in build-logic. It depends on the root `koverXmlReport` and `koverHtmlReport`. Its inputs are the XML file plus the optional thresholds, so it works with the configuration cache. It reads the report-level `LINE` and `BRANCH` counters from the JaCoCo-format XML and prints the summary. It then compares the unrounded percentages with the thresholds and fails if one isn't met.

- The XML parser must not load the external DTD that the JaCoCo DOCTYPE references.
- The task declares no outputs and is never up to date, so the summary always prints.
- Thresholds parse as decimals (`72.5` is valid). Percentages print with one decimal in the summary and with two decimals in a threshold failure message.
- A metric with a total of 0 prints `n/a` and passes its threshold.

Alternative: Kover `verify` rules for the thresholds. Rejected, because Kover wires `koverVerify` into `check`, and the spec keeps coverage out of `check`. It would also need a second summary path anyway.

### `coverageDiff` runs diff-cover through `uvx`

`coverageDiff` is a task class that depends on the root `koverXmlReport` and uses `ExecOperations`. In order, it:

1. Runs `uv --version`. On failure it throws "`uv` is required for coverageDiff: https://docs.astral.sh/uv/".
2. Runs `git rev-parse --verify <ref>^{commit}` for `coverage.compareBranch` (default `origin/master`). On failure it throws a message naming the ref.
3. Runs `uvx --exclude-newer <date> diff-cover==<version> build/reports/kover/report.xml --compare-branch=<ref> --include-untracked --src-roots <src/main/kotlin of every opted-in module> --format html:build/reports/diff-coverage/index.html`. It never passes `--fail-under`, `--ignore-staged` or `--ignore-unstaged`.

The diff-cover version is a `[versions]` entry in `libs.versions.toml` (`diff-cover = "11.0.4"`, the same pin as web and mobile). Version bumps then live next to every other version. A second entry, `diff-cover-exclude-newer`, holds the `--exclude-newer` date and is bumped together with the version, so diff-cover's dependencies resolve the same on every machine.

diff-cover runs with the root project directory, the Git top level, as its working directory. `--src-roots` are paths relative to it, such as `user/src/main/kotlin`, because diff-cover matches them against the repo-relative paths from `git diff`. diff-cover compares against the merge base of `HEAD` and the ref. The task never runs `git fetch`.

- `--src-roots` lists every opted-in module, whatever the selection. The XML only contains the selected modules' classes, so diff-cover ignores changed files outside the selection, which have no coverage data.
- If the pinned diff-cover version still uses `--html-report` instead of `--format html:`, task 3.1 adjusts the flag. The output path stays the same.

### Instrumentation is switched off unless coverage is requested

The same rule as mobile, so the platforms behave alike. `cardigan.kover` applies Kover to its module only when `gradle.startParameter.taskNames` contains a coverage task, and otherwise does nothing, so a normal build runs no Kover task at all (D19). The root plugin always applies Kover, so that an abbreviation such as `kXR` resolves and reaches the guard below, and adds the `kover(project(...))` dependencies only when coverage is requested. A name matches when its last path segment (after the last `:`) is `coverageReport` or `coverageDiff`, or starts with `kover`.

- Task names are part of the configuration-cache key, so reading them at configuration time is safe.
- This doesn't rely on Kover's own task-graph behavior, which isn't a documented guarantee.

Separately, `check` must not pull in any Kover report or verify task. Kover 0.9.11 wires `koverVerify` into `check`, so `cardigan.kover` sets `reports.total.verify.onCheck = false`. With Kover applied unconditionally, every test task would also depend on `koverFindJar`, even with instrumentation off. Applying Kover only for coverage runs avoids that: `./gradlew build --dry-run` lists no Kover or coverage task.

Alternative: rely on Kover instrumenting only when a Kover task is in the task graph. Rejected, because mobile uses the explicit switch, and an explicit switch can be checked.

### Coverage tasks refuse to run uninstrumented

Name matching misses Gradle abbreviations: `./gradlew cR` resolves to `coverageReport`, but instrumentation stays off and the report would be empty. To prevent that, the root plugin registers a `gradle.taskGraph.whenReady` check. The instrumentation decision is computed once by a shared function in build-logic, so the module and root plugins can't disagree. If instrumentation is off and the graph contains `coverageReport`, `coverageDiff` or any Kover report task (`kover*Report*`, `koverVerify*`, `koverCachedVerify*`, `koverLog*`, in the root or a module), the check throws a `GradleException`: "Coverage needs instrumentation. Run with the full task name, e.g. ./gradlew coverageReport." It runs once the task graph is built and before the first task executes, so no test runs and no report is written. `taskGraph.whenReady` is supported with the configuration cache.

This is the same rule as mobile's "Coverage tasks refuse to run uninstrumented".

Alternative: a guard task that the report tasks depend on. Rejected, because the test tasks could already run in parallel before it fails.

### Exclusions in one place

A shared function in build-logic sets the report filters on the root's total report:

- `annotatedBy("org.springframework.context.annotation.Configuration", "org.springframework.boot.context.properties.ConfigurationProperties", "org.springframework.boot.autoconfigure.SpringBootApplication")`
- the class pattern `*ApplicationKt` for the top-level `main`

Kover matches only direct annotations, which is why `@SpringBootApplication` is listed on its own even though it is meta-annotated with `@Configuration`. Module plugins don't set filters. Only the root report is part of the contract.

## Risks / Trade-offs

- [Selecting one module still runs every opted-in module's tests, `:app` E2E included, so it needs Docker and takes as long as a full run.] → Required by D3. Documented in the `CLAUDE.md` commands table.
- [`@Configuration` classes such as `WebConfig`, `SecurityConfig` and `JwtConfig` contain small amounts of logic that becomes invisible to coverage.] → Accepted by D7. Nested classes inside them stay measured, but Kotlin compiles lambdas with invokedynamic, so lambda bodies are excluded with their class. As a result `:app` has no reportable classes (D18).
- [The root plugin configures subprojects (`subprojects {}` + `withPlugin`). This is not compatible with Gradle's isolated projects, which this build doesn't use.] → Revisit if isolated projects is turned on.
- [Instrumented and plain test runs have different task inputs, so switching between `build` and `coverageReport` reruns tests.] → The build cache keeps both variants, so repeat runs are cached.
- [The instrumentation switch matches requested task names literally, so a Gradle abbreviation such as `./gradlew cR` leaves instrumentation off.] → The task-graph guard fails such runs with a clear message before anything runs, instead of writing an empty report. Expanding abbreviations ourselves would mean reimplementing Gradle's name matching.
- [`./gradlew build coverageReport` in one invocation instruments the `build` tests too.] → Intended: coverage was requested.
- [`--include-untracked` also counts untracked files that will never be committed, such as scratch files under a source root.] → Accepted by D9. Clean up or `.gitignore` them.
- [diff-cover needs network access the first time `uvx` fetches the pinned version.] → Cached by uv afterwards.
- [Kover's project filter may not exist in the pinned version.] → Fallback package filter described above. Task 2.1 settles it first.

## Migration Plan

Additive build tooling only. Rollback: remove the plugin lines and the catalog entries.
