# Proposal

## Why

Line and branch coverage show which code the tests run, not whether the tests would notice a bug in it. We want a starting point for measuring how well the backend tests catch faults: a mutation score, generated on demand from the terminal.

## What Changes

- A new convention plugin, `cardigan.pitest`, in `build-logic`, which applies the `info.solidsoft.pitest` Gradle plugin with pinned PIT and JUnit 5 plugin versions. `:common`, `:user` and `:board` apply it.
- A root task, `./gradlew mutationReport`, runs PIT on the opted-in modules and writes HTML and XML reports per module and one combined HTML report at `build/reports/pitest/index.html`.
- Optional properties, with the same names and meaning as on mobile:
  - `-Pmutation.modules=user,board` limits the run to those modules.
  - `-Pmutation.threads=<n>` overrides the thread count (default: available processors).
  - `-Pmutation.minScore=<percent>` fails the task when the selection's mutation score is below it. Never set by default.
- PIT runs only unit tests. Spring context and Testcontainers tests get a JUnit tag, `integration`, and are left out. Mutation testing needs no Docker.
- `DEFAULTS` mutators and PIT's default timeouts.
- Kotlin junk mutants are reduced with open-source PIT filters (null-check intrinsics, generated data-class methods). The application class, `@Configuration`, `@ConfigurationProperties` and `@SpringBootApplication` classes are not mutated.
- Mutation testing runs only when requested. `build`, `check`, CI and hooks don't change.
- New build dependencies, approved by the user: `info.solidsoft.pitest` 1.19.0, PIT 1.30.2, `pitest-junit5-plugin` 1.2.2.

Not breaking: no API, schema or runtime behavior changes.

## Shared specs

This change implements and depends on no `cardigan-shared-specs` requirement. No mutation score policy is specified (D1).

## Capabilities

### New Capabilities
- `mutation-testing`: on-demand PIT mutation reports for chosen backend modules, with optional thread count and minimum score. No incremental analysis (D9).

### Modified Capabilities

None. `delivery-pipeline` and `test-coverage` stay as they are.

## Impact

- `build-logic/convention`: the module plugin `cardigan.pitest`, a root plugin for `mutationReport`, their registration, and the PIT Gradle plugin as a `compileOnly` dependency.
- `gradle/libs.versions.toml`: PIT Gradle plugin, PIT core and JUnit 5 plugin versions, plugin aliases.
- Root `build.gradle.kts` and `common`, `user`, `board` `build.gradle.kts`: apply the plugins.
- Test sources: `@Tag("integration")` on Spring and Testcontainers test classes and on `E2eTest`. No test logic changes.
- `CLAUDE.md`: the command in the Testing table and the tag rule for new integration tests.
- No production code, API, OpenAPI snapshot or database change.
