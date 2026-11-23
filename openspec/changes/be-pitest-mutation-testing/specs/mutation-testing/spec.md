# Spec Delta

## Purpose

Lets developers measure how well the backend tests detect faults, by running mutation testing from the terminal on demand for chosen modules. It defines tooling only and sets no mutation score policy.

## ADDED Requirements

### Requirement: Modules opt in to mutation testing with one plugin
A Gradle module SHALL take part in mutation testing when it applies the `cardigan.pitest` plugin and needs no other configuration. `:common`, `:user` and `:board` SHALL apply it. Only opted-in modules SHALL be mutated.

#### Scenario: Opted-in modules
- **WHEN** `./gradlew mutationReport` runs
- **THEN** the report contains mutants from `:common`, `:user` and `:board` and none from `:app`

#### Scenario: A new module opts in
- **WHEN** a new module applies only `cardigan.pitest` in its build file
- **THEN** the next `./gradlew mutationReport` mutates that module's main classes

### Requirement: One task writes HTML mutation reports
`./gradlew mutationReport` SHALL run mutation testing on the selected modules and write an HTML and an XML report per module under `<module>/build/reports/pitest/`, and a combined HTML report at `build/reports/pitest/index.html` in the root project. Report paths SHALL be the same on every run. Only main source sets SHALL be mutated.

#### Scenario: Report for all modules
- **WHEN** `./gradlew mutationReport` runs and all selected tests pass
- **THEN** the per-module reports and the combined report exist at those paths and show the mutation score and test strength

#### Scenario: Test code is not mutated
- **WHEN** the report is generated
- **THEN** it contains no mutant in a class from a `src/test` or `src/testFixtures` source set

### Requirement: Every run executes PIT for each selected module
Every `mutationReport` SHALL run PIT for each selected module. Gradle SHALL NOT skip it as up to date or restore it from the build cache. An opted-in module without mutants SHALL NOT fail the command.

#### Scenario: Repeated run without changes
- **WHEN** `./gradlew mutationReport` runs twice with no change in between
- **THEN** the second run executes PIT for every selected module, and no module's PIT task is reported as `UP-TO-DATE` or `FROM-CACHE`

#### Scenario: A module without mutants
- **WHEN** an opted-in module has no mutable main classes and `./gradlew mutationReport` runs
- **THEN** the command succeeds and the combined report shows that module with no mutants

### Requirement: A run can be limited to chosen modules
`mutationReport` SHALL accept `-Pmutation.modules=<names>`, a comma-separated list of module names, with or without a leading `:`, trimmed and deduplicated. Only those modules SHALL then be mutated and reported. Without the property, all opted-in modules SHALL be. An unknown or not opted-in name, an empty value or an empty entry in the list SHALL fail the command before any test runs, naming the bad value and the valid module names.

#### Scenario: One module
- **WHEN** `./gradlew mutationReport -Pmutation.modules=board` runs
- **THEN** only `:board` is mutated and the combined report contains only `:board` mutants, also when reports of other modules remain from an earlier run

#### Scenario: A combination of modules
- **WHEN** `./gradlew mutationReport -Pmutation.modules=user,:board` runs
- **THEN** only `:user` and `:board` are mutated

#### Scenario: Unknown module
- **WHEN** `./gradlew mutationReport -Pmutation.modules=cards` runs
- **THEN** the command fails before any test runs, with a message naming `cards` and listing `board`, `common`, `user`

#### Scenario: Duplicate names
- **WHEN** `./gradlew mutationReport -Pmutation.modules=user,:user` runs
- **THEN** only `:user` is mutated, once

#### Scenario: No selection
- **WHEN** `./gradlew mutationReport` runs without `mutation.modules`
- **THEN** all opted-in modules are mutated

#### Scenario: Empty selection
- **WHEN** `./gradlew mutationReport -Pmutation.modules=` runs
- **THEN** the command fails before any test runs, with a message listing the valid module names

#### Scenario: Empty entry
- **WHEN** `./gradlew mutationReport -Pmutation.modules=user,` or `-Pmutation.modules=user,,board` runs
- **THEN** the command fails before any test runs, with a message listing the valid module names

### Requirement: The thread count can be overridden
Mutation testing SHALL use as many worker threads as the machine has available processors. `-Pmutation.threads=<n>` SHALL override this with a positive whole number. Any other value SHALL fail the command before any test runs, naming the property and the allowed values. Module mutation runs SHALL NOT overlap: at most one module's PIT run SHALL execute at a time, even with parallel Gradle execution, so the thread count is the total for the machine.

#### Scenario: Modules run one after another
- **WHEN** `./gradlew mutationReport` runs for `:common`, `:user` and `:board` with parallel execution on
- **THEN** no two modules' PIT runs overlap in time

#### Scenario: Default thread count
- **WHEN** `./gradlew mutationReport` runs without `mutation.threads`
- **THEN** PIT runs with one thread per available processor

#### Scenario: Override
- **WHEN** `./gradlew mutationReport -Pmutation.threads=2` runs
- **THEN** PIT runs with two threads

#### Scenario: Invalid value
- **WHEN** `./gradlew mutationReport -Pmutation.threads=0` or `-Pmutation.threads=abc` runs
- **THEN** the command fails before any test runs, naming `mutation.threads`

### Requirement: Score and test strength are defined as in PIT's report
The mutation score SHALL be killed mutants divided by all mutants of the selected modules together. Test strength SHALL be killed mutants divided by mutants with test coverage. Killed mutants SHALL be those with status `KILLED`, `TIMED_OUT`, `MEMORY_ERROR`, `RUN_ERROR` or `NON_VIABLE`, as PIT counts them as detected. Mutants with test coverage SHALL be all mutants except `NO_COVERAGE`. When no mutant has test coverage, test strength SHALL be 0%.

#### Scenario: Score of a selection
- **WHEN** the selection has 100 mutants, of which 50 are killed, 5 time out, 1 hits a memory error, 24 survive and 20 have no coverage
- **THEN** the mutation score is 56% and test strength is 70%

#### Scenario: Run errors and non-viable mutants
- **WHEN** the selection has 10 mutants, of which 4 are killed, 1 has a run error, 1 is non-viable, 2 survive and 2 have no coverage
- **THEN** the mutation score is 60% and test strength is 75%

#### Scenario: No covered mutants
- **WHEN** every mutant in the selection has no coverage
- **THEN** the mutation score and test strength are both 0%

### Requirement: An optional minimum score fails the task
`mutationReport` SHALL accept `-Pmutation.minScore=<percent>`, a number from 0 to 100, never set by default. It SHALL check the mutation score, not test strength, compared unrounded. When it is given and the score is below it, the command SHALL fail after writing the reports and print the mutation score and test strength with two decimals and the required score. Without the property, the command SHALL never fail on the score.

#### Scenario: Minimum not met
- **WHEN** the selection's mutation score is 61.37% and `./gradlew mutationReport -Pmutation.minScore=70` runs
- **THEN** the reports are written and the command fails, printing the mutation score 61.37%, the test strength and the required 70%

#### Scenario: Minimum met
- **WHEN** the selection's mutation score is 72.5% and `./gradlew mutationReport -Pmutation.minScore=72.5` runs
- **THEN** the command succeeds

#### Scenario: No minimum given
- **WHEN** most mutants survive and `./gradlew mutationReport` runs without `mutation.minScore`
- **THEN** the command succeeds and writes the reports

#### Scenario: Invalid minimum
- **WHEN** `./gradlew mutationReport -Pmutation.minScore=abc` or `-Pmutation.minScore=120` runs
- **THEN** the command fails before any test runs, naming `mutation.minScore` and the allowed range

#### Scenario: No mutants in the selection
- **WHEN** the selection produces no mutants and `-Pmutation.minScore=50` is given
- **THEN** the minimum counts as met

### Requirement: Only unit tests run against mutants
Mutation testing SHALL run only tests that start no Spring context and no container. Those tests SHALL carry a marker that mutation testing excludes, and SHALL still run in `./gradlew test` and `build` as before. Mutation testing SHALL NOT need Docker.

#### Scenario: Integration tests are left out
- **WHEN** `./gradlew mutationReport` runs
- **THEN** no Spring Boot test, JPA test or E2E test runs, and no container starts

#### Scenario: Normal test runs are unchanged
- **WHEN** `./gradlew test` runs
- **THEN** the same tests run as before this change

#### Scenario: A failing unit test
- **WHEN** a selected unit test fails on the unmutated code
- **THEN** `mutationReport` fails

### Requirement: Non-logic classes are not mutated
Mutation testing SHALL NOT mutate the application main class and its top-level `main`, or classes annotated with `@SpringBootApplication`, `@Configuration` or `@ConfigurationProperties`. New classes of those kinds SHALL be excluded without changing the mutation setup.

#### Scenario: Configuration classes are excluded
- **WHEN** the report is generated
- **THEN** it contains no mutant in a class annotated with `@SpringBootApplication`, `@Configuration` or `@ConfigurationProperties`

#### Scenario: A new configuration class
- **WHEN** a new `@Configuration` class is added to an opted-in module
- **THEN** the next report has no mutant in it, with no change to the mutation setup

### Requirement: Kotlin compiler-generated code is not mutated
Mutation testing SHALL NOT create mutants in Kotlin's generated null checks or in the generated `componentN`, `copy`, `equals`, `hashCode` and `toString` methods of data classes.

#### Scenario: Data class methods
- **WHEN** a module contains a data class and the report is generated
- **THEN** the report has no mutant in that class's generated methods

#### Scenario: Null-check intrinsics
- **WHEN** a module has a public function with a non-null parameter and the report is generated
- **THEN** the report has no mutant on a call into `kotlin.jvm.internal.Intrinsics`

### Requirement: Mutation testing runs only on demand
Mutation testing SHALL run only when `mutationReport` or a PIT task is requested. `./gradlew build`, `./gradlew check` and `./gradlew test` SHALL NOT run any mutation task, and CI SHALL NOT run mutation testing.

#### Scenario: Build without mutation testing
- **WHEN** `./gradlew build` runs
- **THEN** no PIT or mutation task runs and no mutation report is written

#### Scenario: CI unchanged
- **WHEN** CI runs for a push or pull request
- **THEN** it runs no mutation task
