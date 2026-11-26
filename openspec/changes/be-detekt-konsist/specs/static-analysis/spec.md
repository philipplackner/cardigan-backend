# Spec Delta

## Purpose

Lets developers check the backend code with detekt and its architecture rules with Konsist from the terminal, on demand. It defines the tooling, its commands and one sample rule of each kind; it sets no rule policy beyond detekt's defaults.

## ADDED Requirements

### Requirement: Modules opt in to detekt with one plugin
A Gradle module SHALL be analyzed by detekt when it applies the `cardigan.detekt` plugin and needs no other configuration. `:app`, `:common`, `:user`, `:board` and `:tooling` SHALL apply it. build-logic SHALL also be analyzed, with the same detekt version and config file.

#### Scenario: Opted-in modules
- **WHEN** `./gradlew detekt` runs
- **THEN** detekt analyzes `:app`, `:common`, `:user`, `:board`, `:tooling` and build-logic

#### Scenario: A new module opts in
- **WHEN** a new module applies only `cardigan.detekt` in its build file
- **THEN** the next `./gradlew detekt` analyzes that module

### Requirement: detekt analyzes main and test sources with type resolution
detekt SHALL analyze the main and test source sets of every analyzed module, with type resolution. Rules that need type resolution SHALL report findings.

#### Scenario: Finding in test code
- **WHEN** a test source file breaks a default detekt rule and `./gradlew detekt` runs
- **THEN** the finding is reported

#### Scenario: Type-resolution rule
- **WHEN** main code contains a safe call on a non-null receiver (`val s = "x"; s?.length`), which only the type-resolution rule `UnnecessarySafeCall` detects, and `./gradlew detekt` runs
- **THEN** an `UnnecessarySafeCall` finding is reported

### Requirement: detekt uses its default rules with one shared config
All analyzed code SHALL use detekt's default rule set and one config file at `config/detekt/detekt.yml`. The config SHALL hold only overrides of the defaults. Rules that duplicate ktlint's formatting checks SHALL be off, and no ktlint wrapper SHALL run inside detekt. There SHALL be no baseline file.

#### Scenario: Formatting is left to ktlint
- **WHEN** a file has a line longer than detekt's default limit and `./gradlew detekt` runs
- **THEN** detekt reports no finding for the line length

#### Scenario: No baseline
- **WHEN** existing code has findings and `./gradlew detekt` runs
- **THEN** every finding is reported; none is hidden by a baseline

### Requirement: detekt fails on any finding
A requested detekt run SHALL fail when it reports at least one finding and SHALL succeed when it reports none.

#### Scenario: One finding
- **WHEN** an analyzed module has one finding and `./gradlew detekt` runs
- **THEN** the command fails and names the finding

#### Scenario: Clean code
- **WHEN** no analyzed code has a finding and `./gradlew detekt` runs
- **THEN** the command succeeds

### Requirement: detekt can run for one module
`./gradlew :<module>:detekt` SHALL analyze only that module. There SHALL be no property that selects modules.

#### Scenario: One module
- **WHEN** `./gradlew :user:detekt` runs
- **THEN** only `:user` is analyzed and only its findings decide the result

### Requirement: detekt writes HTML and SARIF reports
Every analyzed module SHALL get an HTML and a SARIF report under its own `build/reports/detekt/`. A run of `./gradlew detekt` SHALL also write one merged SARIF report under the root `build/reports/detekt/`. When the run fails, every module's report and the merged report SHALL still be written for the modules that were analyzed.

#### Scenario: Reports after a run
- **WHEN** `./gradlew detekt` finishes
- **THEN** each analyzed module has an HTML and a SARIF report, and the root has a merged SARIF report with the findings of all modules

#### Scenario: Reports after a failed run
- **WHEN** `./gradlew detekt` fails on a finding in `:user`
- **THEN** `:user` has an HTML and a SARIF report and the root merged SARIF report contains that finding

### Requirement: Mocking libraries are flagged in test code
The custom rule `NoMockingLibraries` in the rule set `cardigan` SHALL report every import from `org.mockito` or `io.mockk` and their subpackages, including star and aliased imports, in test source sets. Matching is case-sensitive. It SHALL NOT check main source sets or fully qualified references without an import.

#### Scenario: Mockito import in a test
- **WHEN** a test file contains `import org.mockito.Mockito` and detekt runs
- **THEN** `cardigan:NoMockingLibraries` reports that import

#### Scenario: Star and aliased MockK imports
- **WHEN** a test file contains `import io.mockk.*` and `import io.mockk.mockk as m`
- **THEN** each import is reported once

#### Scenario: Different letter case
- **WHEN** a test file contains `import Org.Mockito.Mockito`
- **THEN** no finding is reported

#### Scenario: Similar package names
- **WHEN** a test file imports from a package that only starts with the same letters, such as `org.mockitox.Foo` or `io.mockkx.Bar`
- **THEN** no finding is reported

#### Scenario: Main source set
- **WHEN** a main source file imports `io.mockk.mockk`
- **THEN** `cardigan:NoMockingLibraries` reports nothing

#### Scenario: Qualified reference without import
- **WHEN** a test file calls `io.mockk.mockk<Foo>()` without importing it
- **THEN** `cardigan:NoMockingLibraries` reports nothing

#### Scenario: Suppressed
- **WHEN** a test file annotates the import's file with `@file:Suppress("cardigan:NoMockingLibraries")`
- **THEN** no finding is reported

### Requirement: Mocking-library findings have a fixed message
Each `NoMockingLibraries` finding SHALL have exactly the message `Import of '<path>' is not allowed. Use fakes instead of mocking libraries.`, where `<path>` is the imported path as written in the import, without an alias.

#### Scenario: Plain import
- **WHEN** a test file contains `import org.mockito.Mockito`
- **THEN** the finding's message is `Import of 'org.mockito.Mockito' is not allowed. Use fakes instead of mocking libraries.`

#### Scenario: Star import
- **WHEN** a test file contains `import io.mockk.*`
- **THEN** the finding's message is `Import of 'io.mockk.*' is not allowed. Use fakes instead of mocking libraries.`

### Requirement: The mocking rule is off unless configured
`NoMockingLibraries` SHALL run only when a detekt config activates it. The shared config SHALL activate it.

#### Scenario: Not configured
- **WHEN** detekt runs on a test file that imports `io.mockk.mockk` with a config that has no `cardigan` section
- **THEN** `cardigan:NoMockingLibraries` reports nothing

#### Scenario: Shared config
- **WHEN** detekt runs with `config/detekt/detekt.yml` on a test file that imports `io.mockk.mockk`
- **THEN** the import is reported

### Requirement: Domain packages are checked for framework imports
`./gradlew konsist` SHALL run the Konsist checks and only those. One check SHALL fail when a file in a `domain` package in the main or testFixtures sources of `:common`, `:user` or `:board` imports from `org.springframework` or `jakarta.persistence`, and name each such file.

#### Scenario: Clean domain packages
- **WHEN** no domain file imports Spring or `jakarta.persistence` and `./gradlew konsist` runs
- **THEN** the command succeeds

#### Scenario: Spring import in a domain package
- **WHEN** a file under `com.plcoding.cardigan.board.domain` imports `org.springframework.stereotype.Component` and `./gradlew konsist` runs
- **THEN** the command fails and names that file

### Requirement: One command runs all static analysis
`./gradlew staticAnalysis` SHALL run detekt for every analyzed module and the Konsist checks. Both SHALL run even when the other fails. The command SHALL fail when either found something and succeed only when both are clean.

#### Scenario: detekt fails
- **WHEN** detekt has a finding, the Konsist checks are clean, and `./gradlew staticAnalysis` runs
- **THEN** the Konsist checks still run and the command fails

#### Scenario: Both clean
- **WHEN** neither tool finds anything
- **THEN** `./gradlew staticAnalysis` succeeds

### Requirement: Custom rule tests run on demand
`./gradlew :tooling:test` SHALL run the unit tests of the custom detekt rules and SHALL NOT run the Konsist checks.

#### Scenario: Rule tests
- **WHEN** `./gradlew :tooling:test` runs
- **THEN** the `NoMockingLibraries` tests run and no Konsist check runs

### Requirement: Static analysis runs only on demand
detekt, the Konsist checks and the `:tooling` tests SHALL run only when one of their tasks is requested. `./gradlew build`, `./gradlew check` and `./gradlew test` SHALL NOT run them, and CI SHALL NOT run them.

#### Scenario: Build without static analysis
- **WHEN** `./gradlew build` runs while the code has detekt findings and a domain file imports Spring
- **THEN** no detekt task, Konsist check or `:tooling` test runs, and the build result does not depend on them

#### Scenario: CI unchanged
- **WHEN** CI runs for a push or pull request
- **THEN** it runs no detekt task, Konsist check or `:tooling` test

### Requirement: The tooling module stays out of coverage and mutation reports
`:tooling` SHALL NOT take part in `coverageReport` or `mutationReport`.

#### Scenario: Reports without tooling
- **WHEN** `./gradlew mutationReport` or `./gradlew coverageReport` runs
- **THEN** the report has no entry for `:tooling` classes
