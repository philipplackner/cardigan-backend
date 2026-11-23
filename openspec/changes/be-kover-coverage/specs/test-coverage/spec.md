# Spec Delta

## Purpose

Lets developers measure backend test coverage from the terminal, on demand, for any chosen set of modules, and see coverage for only the lines changed relative to a base branch. It defines tooling only and sets no coverage policy.

## ADDED Requirements

### Requirement: Modules opt in to coverage with one plugin
A Gradle module SHALL take part in coverage when it applies the `cardigan.kover` plugin and needs no other coverage configuration. `:app`, `:common`, `:user` and `:board` SHALL apply it. Only opted-in modules SHALL appear in coverage reports.

#### Scenario: All backend modules opt in
- **WHEN** `./gradlew coverageReport` runs
- **THEN** the report contains classes from `:app`, `:common`, `:user` and `:board`

#### Scenario: A new module opts in
- **WHEN** a new module applies only `cardigan.kover` in its build file
- **THEN** `./gradlew coverageReport` includes that module's classes and counts its tests

### Requirement: A coverage report covers all modules by default
`./gradlew coverageReport` SHALL run the tests of every opted-in module and write a coverage report of all of them. It SHALL write the XML report to `build/reports/kover/report.xml` and the HTML report to `build/reports/kover/html/index.html` in the root project. Only main source sets SHALL be measured, never test or test-fixture code.

#### Scenario: Report for all modules
- **WHEN** `./gradlew coverageReport` runs without `coverage.modules` and all tests pass
- **THEN** the XML and HTML reports exist at those paths and cover the main classes of all opted-in modules

#### Scenario: Test code is not measured
- **WHEN** the report is generated
- **THEN** it contains no class from a `src/test` or `src/testFixtures` source set

#### Scenario: A failing test
- **WHEN** a test fails during `./gradlew coverageReport`
- **THEN** the command fails

### Requirement: A report can be limited to chosen modules
`coverageReport` SHALL accept `-Pcoverage.modules=<names>`, a comma-separated list of module names, with or without a leading `:` and with surrounding spaces ignored. The report SHALL then contain only classes from those modules. An unknown name, a module that has not opted in, or an empty list SHALL fail the command before any test runs. The failure message names the bad value and the valid module names.

#### Scenario: One module
- **WHEN** `./gradlew coverageReport -Pcoverage.modules=user` runs
- **THEN** the report contains only `:user` classes

#### Scenario: A combination of modules
- **WHEN** `./gradlew coverageReport -Pcoverage.modules=user,board` runs
- **THEN** the report contains only `:user` and `:board` classes

#### Scenario: Unknown module
- **WHEN** `./gradlew coverageReport -Pcoverage.modules=user,cards` runs
- **THEN** the command fails before any test runs, with a message naming `cards` and listing `app`, `board`, `common`, `user`

#### Scenario: Empty selection
- **WHEN** `./gradlew coverageReport -Pcoverage.modules=` runs
- **THEN** the command fails before any test runs, with a message listing the valid module names

#### Scenario: Trailing comma
- **WHEN** `./gradlew coverageReport -Pcoverage.modules=user,` runs
- **THEN** the command fails before any test runs, the same way as for an unknown name, with a message listing the valid module names

#### Scenario: Duplicate names
- **WHEN** `./gradlew coverageReport -Pcoverage.modules=user,:user` runs
- **THEN** the command succeeds and the summary lists `user` once

### Requirement: Lines hit by any test count
A class SHALL count as covered by every test of every opted-in module, whichever modules are selected. `:app` E2E tests SHALL count toward the coverage of `:common`, `:user` and `:board` classes.

#### Scenario: E2E coverage counts for a selected module
- **WHEN** a `:user` class is executed only by an `:app` E2E test and `./gradlew coverageReport -Pcoverage.modules=user` runs
- **THEN** the report shows that class's executed lines as covered

### Requirement: The terminal shows a coverage summary
After writing the reports, `coverageReport` SHALL print the selected modules, the total line coverage and the total branch coverage of the selection, each as a percentage with covered and total counts, and the paths of the XML and HTML reports. When the selection has no branches, branch coverage SHALL be shown as `n/a`.

#### Scenario: Summary after the report
- **WHEN** `./gradlew coverageReport -Pcoverage.modules=board` completes
- **THEN** the terminal shows the selected module, line coverage as a percentage with covered/total lines, branch coverage as a percentage with covered/total branches, and both report paths

### Requirement: Optional thresholds fail the report command
`coverageReport` SHALL accept `-Pcoverage.minLine=<percent>` and `-Pcoverage.minBranch=<percent>`, numbers from 0 to 100. When a given threshold is above the selection's total coverage for that metric, the command SHALL fail after writing the reports, naming the metric, the actual and the required percentage. Without these properties it SHALL never fail on coverage.

#### Scenario: Line threshold not met
- **WHEN** line coverage of the selection is 72.4% and `./gradlew coverageReport -Pcoverage.minLine=80` runs
- **THEN** the reports are written and the command fails, naming line coverage, 72.4% and 80%

#### Scenario: Threshold met
- **WHEN** line coverage is 80.0% and branch coverage 65.0%, and `./gradlew coverageReport -Pcoverage.minLine=80 -Pcoverage.minBranch=60` runs
- **THEN** the command succeeds

#### Scenario: No threshold given
- **WHEN** `./gradlew coverageReport` runs without threshold properties and all tests pass
- **THEN** the command succeeds whatever the coverage

#### Scenario: Invalid threshold
- **WHEN** `./gradlew coverageReport -Pcoverage.minLine=abc` or `-Pcoverage.minBranch=120` runs
- **THEN** the command fails before any test runs, with a message naming the property and the allowed range

#### Scenario: No branches in the selection
- **WHEN** the selection has no branches and `-Pcoverage.minBranch=50` is given
- **THEN** the branch threshold counts as met and the summary shows branch coverage as `n/a`

### Requirement: Threshold values are decimals compared unrounded
`coverage.minLine` and `coverage.minBranch` SHALL accept integers and decimals from 0 to 100 inclusive, for example `80` or `72.5`. The comparison SHALL use the unrounded percentage. The summary SHALL print percentages with one decimal, and a threshold failure SHALL print the actual percentage with two decimals.

#### Scenario: Decimal threshold
- **WHEN** `./gradlew coverageReport -Pcoverage.minLine=72.5` runs
- **THEN** the threshold is accepted and compared with the line coverage

#### Scenario: Rounded value just below the threshold
- **WHEN** line coverage is 79.96% and `./gradlew coverageReport -Pcoverage.minLine=80` runs
- **THEN** the summary shows `80.0%`, and the command fails with a message showing 79.96% and 80%

### Requirement: Non-logic classes are excluded from coverage
The reports SHALL exclude the application main class and its top-level `main` function, classes annotated with `@Configuration`, and classes annotated with `@ConfigurationProperties`. The exclusions SHALL be defined by annotation or name pattern, so new classes of those kinds are excluded without changing the coverage setup.

#### Scenario: Configuration classes are excluded
- **WHEN** the report is generated
- **THEN** it contains no class annotated with `@SpringBootApplication`, `@Configuration` or `@ConfigurationProperties`, and no class holding the application's top-level `main`

#### Scenario: A new configuration class
- **WHEN** a new `@Configuration` class is added to any opted-in module
- **THEN** the next report excludes it with no change to the coverage setup

### Requirement: Diff coverage shows coverage of changed lines
`./gradlew diffCoverage` SHALL generate the XML report for the same module selection as `coverageReport` and then report coverage of only the lines changed since the merge base of `HEAD` and a base ref, plus uncommitted and untracked changes. The base ref SHALL be `origin/master` unless `-Pcoverage.compareBranch=<ref>` is given. Committed, staged, unstaged and untracked changes SHALL count. Changed lines in modules outside the selection SHALL NOT be reported.

#### Scenario: Branch compared with origin/master
- **WHEN** `./gradlew diffCoverage` runs on a branch with committed changes to `:board` code
- **THEN** the terminal shows coverage of the changed `:board` lines relative to `origin/master`

#### Scenario: Other base ref
- **WHEN** `./gradlew diffCoverage -Pcoverage.compareBranch=HEAD~3` runs
- **THEN** changed lines are computed relative to `HEAD~3`

#### Scenario: Uncommitted changes count
- **WHEN** a `:user` source file has unstaged changes and `./gradlew diffCoverage` runs
- **THEN** the changed lines in that file appear in the diff coverage result

#### Scenario: Untracked files count
- **WHEN** a new `:board` source file has never been `git add`-ed and `./gradlew diffCoverage` runs
- **THEN** that file's lines appear in the diff coverage result

#### Scenario: Selection limits the diff
- **WHEN** both `:user` and `:board` code changed and `./gradlew diffCoverage -Pcoverage.modules=user` runs
- **THEN** only the changed `:user` lines are reported

#### Scenario: Unknown base ref
- **WHEN** `./gradlew diffCoverage -Pcoverage.compareBranch=does-not-exist` runs
- **THEN** the command fails with a message naming the ref

### Requirement: Diff coverage uses the local base ref without fetching
`diffCoverage` SHALL NOT run `git fetch`. It SHALL use the base ref as it exists locally, so a stale `origin/master` needs a `git fetch` first.

#### Scenario: Stale origin/master
- **WHEN** the local `origin/master` is behind the remote and `./gradlew diffCoverage` runs
- **THEN** no fetch happens, and changed lines are computed against the merge base with the local `origin/master`

### Requirement: Diff coverage writes a terminal and an HTML report and never fails on coverage
`diffCoverage` SHALL print its result to the terminal and write an HTML report to `build/reports/diff-coverage/index.html` in the root project. It SHALL NOT fail because of the coverage of the changed lines, and the threshold properties SHALL NOT apply to it.

#### Scenario: HTML report
- **WHEN** `./gradlew diffCoverage` completes
- **THEN** `build/reports/diff-coverage/index.html` exists and the terminal shows the diff coverage summary and the path of that file

#### Scenario: Low coverage of changed lines
- **WHEN** none of the changed lines are covered
- **THEN** `diffCoverage` succeeds

#### Scenario: Thresholds are ignored
- **WHEN** `./gradlew diffCoverage -Pcoverage.minLine=100` runs and the changed lines are below 100% coverage
- **THEN** the command succeeds

### Requirement: Diff coverage needs only uv
`diffCoverage` SHALL run the diff coverage tool at a version pinned in the repository, with `uv` as the only prerequisite beyond the JDK and Git. When `uv` is not on the path, the command SHALL fail with a message saying that `uv` is required.

#### Scenario: Pinned version
- **WHEN** two developers run `./gradlew diffCoverage` on different machines
- **THEN** both use the same diff coverage tool version, without installing it themselves

#### Scenario: uv missing
- **WHEN** `uv` is not installed and `./gradlew diffCoverage` runs
- **THEN** the command fails with a message saying `uv` is required

### Requirement: Coverage runs only on demand
Coverage SHALL run only when `coverageReport`, `diffCoverage` or another coverage task is requested. `./gradlew build` and `./gradlew check` SHALL NOT generate coverage reports or check thresholds, and CI SHALL NOT run coverage.

#### Scenario: Build without coverage
- **WHEN** `./gradlew build` runs
- **THEN** no coverage task runs and no coverage report is written

### Requirement: Tests are instrumented only for coverage runs
Test tasks SHALL be instrumented for coverage only when the requested task names include `coverageReport`, `diffCoverage` or a task whose name starts with `kover`. Otherwise every test task SHALL run without coverage instrumentation, so `./gradlew build`, `./gradlew test` and module test runs are not slowed by coverage.

#### Scenario: Plain test run
- **WHEN** `./gradlew test` or `./gradlew :user:test` runs
- **THEN** the test JVMs run without the coverage agent

#### Scenario: Coverage run
- **WHEN** `./gradlew coverageReport` runs
- **THEN** the test JVMs of every opted-in module run with the coverage agent

### Requirement: Coverage tasks refuse to run uninstrumented
When instrumentation is off for a build, `coverageReport`, `diffCoverage` and every Kover report task SHALL fail before any test runs and before any report is written. The failure message SHALL tell the developer to run with the full task name.

#### Scenario: Abbreviated task name
- **WHEN** `./gradlew cR` runs
- **THEN** the command fails before any test runs with a message saying to run with the full task name, and no file under `build/reports/kover` or `build/reports/diff-coverage` is written or changed

#### Scenario: Full task name
- **WHEN** `./gradlew coverageReport` runs
- **THEN** the guard does not fail and the reports are written

#### Scenario: CI unchanged
- **WHEN** CI runs for a push or pull request
- **THEN** it runs no coverage task
