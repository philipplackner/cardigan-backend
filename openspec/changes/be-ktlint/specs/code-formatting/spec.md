# Spec Delta

## Purpose

Lets developers check and fix the formatting of the backend's Kotlin code from the terminal, on demand, against one set of rule settings shared with the mobile repo. It defines tooling only and enforces nothing.

## ADDED Requirements

### Requirement: Modules opt in to formatting checks with one plugin
A Gradle module SHALL take part in `ktlintCheck` and `ktlintFormat` when it applies the `cardigan.ktlint` plugin and needs no other ktlint configuration. The root project, `:app`, `:common`, `:user` and `:board` SHALL apply it. build-logic SHALL take part without the plugin.

#### Scenario: All backend modules opt in
- **WHEN** `./gradlew ktlintCheck` runs
- **THEN** it checks the Kotlin files of the root project, `:app`, `:common`, `:user`, `:board` and build-logic

#### Scenario: A new module opts in
- **WHEN** a new module applies its Kotlin convention plugin plus `cardigan.ktlint`, and has no other ktlint configuration
- **THEN** `./gradlew ktlintCheck` and `./gradlew ktlintFormat` include that module's Kotlin files

### Requirement: ktlintCheck reports violations without changing files
`./gradlew ktlintCheck` SHALL check every covered file against the rule settings and SHALL NOT modify any file. Each violation SHALL be printed in the console with its file, line, column, rule ID and message. The command SHALL fail when at least one violation is found and succeed when none is.

#### Scenario: Clean code
- **WHEN** `./gradlew ktlintCheck` runs on code without violations
- **THEN** the command succeeds and no file changes

#### Scenario: A violation
- **WHEN** a covered file contains a line of 121 characters that ktlint cannot wrap and `./gradlew ktlintCheck` runs
- **THEN** the command fails, the console names the file, line and the `max-line-length` rule, and the file is unchanged

### Requirement: ktlintFormat fixes what ktlint can fix
`./gradlew ktlintFormat` SHALL rewrite every covered file to fix the violations ktlint can fix on its own. Violations it cannot fix SHALL be printed in the console as `ktlintCheck` prints them.

#### Scenario: A fixable violation
- **WHEN** a covered file has wrong indentation and `./gradlew ktlintFormat` runs
- **THEN** the file is rewritten with correct indentation, and a following `./gradlew ktlintCheck` reports no violation for it

#### Scenario: Already formatted code
- **WHEN** `./gradlew ktlintFormat` runs on code without violations
- **THEN** no file content changes

### Requirement: The commands cover all Kotlin sources and build scripts
Both commands SHALL cover the main, test and testFixtures Kotlin sources of every opted-in module, every `*.gradle.kts` file of the root project and the opted-in modules including `settings.gradle.kts`, and the Kotlin sources and build scripts of build-logic. Generated code and build output SHALL NOT be checked or formatted.

#### Scenario: Test and fixture sources
- **WHEN** a violation is in a file under `src/test` or `src/testFixtures`
- **THEN** `./gradlew ktlintCheck` reports it

#### Scenario: Build scripts
- **WHEN** a violation is in the root `build.gradle.kts`, the root `settings.gradle.kts` or a module's `build.gradle.kts`
- **THEN** `./gradlew ktlintCheck` reports it

#### Scenario: build-logic
- **WHEN** a violation is in a convention plugin source under `build-logic`
- **THEN** `./gradlew ktlintCheck` from the repo root reports it, and `./gradlew ktlintFormat` from the repo root fixes it if ktlint can

#### Scenario: Generated sources
- **WHEN** build-logic's `kotlin-dsl` accessors have been generated under `build-logic/convention/build/` and `./gradlew ktlintCheck` runs
- **THEN** no violation is reported for a file under any `build/` directory, and `./gradlew ktlintFormat` changes no file there

### Requirement: Rule settings come from a shared editorconfig
The rule settings SHALL be read from a root `.editorconfig` that applies only to `*.kt` and `*.kts` files. It SHALL use the `ktlint_official` code style with a maximum line length of 120, and SHALL set the IntelliJ Kotlin formatter options so that the IDE's Reformat Code produces `ktlint_official` output. Only ktlint's built-in rules SHALL run.

#### Scenario: Line length
- **WHEN** a covered file has a line of 121 characters that ktlint cannot wrap
- **THEN** `./gradlew ktlintCheck` reports a `max-line-length` violation, and a line of 120 characters is not reported

#### Scenario: Non-Kotlin files
- **WHEN** the `.editorconfig` is read for a YAML, SQL, JSON or Markdown file
- **THEN** no setting from it applies to that file

### Requirement: The editorconfig matches the mobile repo
The backend's root `.editorconfig` SHALL match the mobile repo's `.editorconfig` line for line, except that the backend's file leaves out `ktlint_function_naming_ignore_when_annotated_with = Composable`.

#### Scenario: Compare with mobile
- **WHEN** the backend's and the mobile repo's `.editorconfig` files are compared
- **THEN** the only difference is the mobile-only `ktlint_function_naming_ignore_when_annotated_with = Composable` line

### Requirement: ktlintCheck writes SARIF and Checkstyle reports
`./gradlew ktlintCheck` SHALL write a SARIF and a Checkstyle report for each checked source set and script set, under the owning project's `build/reports/ktlint/`, whether or not violations are found.

#### Scenario: Reports after a run
- **WHEN** `./gradlew ktlintCheck` runs
- **THEN** each opted-in project and build-logic has `.sarif` and Checkstyle `.xml` files under its `build/reports/ktlint/`, listing the violations found, or none

### Requirement: Formatting is never enforced or applied automatically
ktlint SHALL run only when `ktlintCheck` or `ktlintFormat` is called. `./gradlew build`, `./gradlew check`, CI and git hooks SHALL NOT run ktlint, and SHALL NOT fail because of a formatting violation. No task SHALL format files as a side effect.

#### Scenario: Build with a violation
- **WHEN** a covered file has a formatting violation and `./gradlew build` runs
- **THEN** the build does not run ktlint and does not fail because of the violation, and the file is unchanged

#### Scenario: Commit
- **WHEN** a developer commits a file with a formatting violation
- **THEN** no hook runs ktlint and the commit is not blocked by it

### Requirement: The codebase starts free of violations
After this change, `./gradlew ktlintCheck` SHALL pass on the whole codebase. A violation that cannot be fixed without changing behavior or a client-visible name (API paths, JSON property names, enum constants, database names) SHALL be suppressed on that declaration with `@Suppress("ktlint:standard:<rule>")`, and the rule SHALL stay on everywhere else. The reformat SHALL NOT change the API contract or the database schema.

#### Scenario: Clean start
- **WHEN** `./gradlew ktlintCheck` runs right after this change
- **THEN** it succeeds

#### Scenario: Contract unchanged
- **WHEN** `./gradlew build` runs after the reformat
- **THEN** all tests pass, including the OpenAPI snapshot test, with `openapi.json` unchanged
