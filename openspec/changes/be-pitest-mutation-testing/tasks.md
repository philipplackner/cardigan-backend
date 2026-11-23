# Tasks

## 1. Versions and catalog

- [x] 1.1 Add `info.solidsoft.pitest` 1.19.0, PIT 1.30.2 and `pitest-junit5-plugin` 1.2.2 to `gradle/libs.versions.toml` with plugin aliases for `cardigan.pitest` and `cardigan.pitest.report`, and the PIT Gradle plugin as `compileOnly` in `build-logic/convention`. If a newer release of any of them exists, keep the approved version and report it to the user. Apply the plugin by hand to `:common` in a scratch run with the configuration cache on, and verify that PIT reads the JDK 25 class files, finds the JUnit tests on the JUnit version from the Spring Boot 4.1 BOM, and reports no configuration-cache problems. Revert the scratch setup. If any check fails, stop and report it to the user.

## 2. Module plugin

- [x] 2.1 Add `cardigan.pitest`, applied only when a requested task's last segment is `mutationReport` or starts with `pitest`. Configure target classes, source sets, `DEFAULTS` mutators, default timeouts, HTML and XML output, line-coverage export, non-timestamped reports, `failWhenNoMutations = false`, no up-to-date checks or build caching for `pitest`, and the shared build service with `maxParallelUsages = 1` as in design.md. Apply it in `:common`, `:user`, `:board`. Verify that `./gradlew :board:pitest` writes `board/build/reports/pitest/index.html` and `mutations.xml`, that a second unchanged `./gradlew :board:pitest` runs PIT again (not `UP-TO-DATE` or `FROM-CACHE`), that `./gradlew build --dry-run` lists no PIT task, and that `./gradlew test --rerun` takes as long as before, within noise.
- [x] 2.2 Add `@Tag("integration")` to every test class that starts a Spring context or a container and to `E2eTest`, and set `excludedGroups`. Verify that `./gradlew :common:pitest :user:pitest :board:pitest` succeeds with Docker stopped, and that `./gradlew test` runs the same number of tests as before (compare the test result XML counts).
- [x] 2.3 Add the class exclusions: the annotation scan, or the name-pattern fallback if the plugin doesn't accept provider-based `excludedClasses` with the configuration cache. Record the choice in design.md and report the fallback to the user. Verify that no report contains a mutant in a `@Configuration`, `@ConfigurationProperties` or `@SpringBootApplication` class, for example `BoardProperties`, and that adding a scratch `@Configuration` class named `Scratch` to `:board` excludes it (revert afterwards).
- [x] 2.4 Add the Kotlin filters (`avoidCallsTo kotlin.jvm.internal`, excluded data-class methods). Verify that a `:board` domain data class has no mutant in `copy`, `componentN`, `equals`, `hashCode` or `toString`, and that the report has no mutant on an `Intrinsics` null-check call.

## 3. Root task and properties

- [ ] 3.1 Add `cardigan.pitest.report` with the `mutationReport` task, the aggregator, the `mutation.modules` selection and the abbreviation guard. Verify that:
  - `./gradlew mutationReport` writes `build/reports/pitest/index.html` with mutants from all three modules and prints its path;
  - the mutation score and test strength computed from `mutations.xml` (killed = `KILLED`, `TIMED_OUT`, `MEMORY_ERROR`, `RUN_ERROR`, `NON_VIABLE`) match the combined HTML report, and test strength is 0 when no mutant is covered (unit test of the counting); OPEN: the code counts only the first three statuses until the user settles T1, see design.md;
  - `-Pmutation.modules=board` runs only `:board`'s `pitest`, `=user,:board` runs `:user` and `:board`, `=user,:user` runs `:user` once, and no property runs all three;
  - `-Pmutation.modules=board` right after a full run writes a combined report with only `:board` classes;
  - with parallel execution on, the three modules' PIT runs don't overlap (`--info` timestamps);
  - a module without mutants doesn't fail the run (scratch empty module or a selection with only excluded classes; revert afterwards);
  - errors for a bad `mutation.modules` name `mutation.modules`, not `coverage.modules`;
  - `=cards`, `=`, `=user,` and `=user,,board` fail before any test runs, listing `board, common, user`;
  - `./gradlew mR` fails with the "full task name" message before any test runs;
  - a second run with configuration-cache reuse works.
- [x] 3.2 Add `mutation.threads`. Verify that:
  - the default run uses one thread per available processor and `-Pmutation.threads=2` uses two (check PIT's logged config with `--info`);
  - `-Pmutation.threads=0` and `=abc` fail before any test runs.
  - Dropped: `mutation.incremental`. Open-source PIT 1.25.8 and later has no history plugin, so the user removed incremental analysis (D9).
- [x] 3.3 Add `mutation.minScore`. Verify that:
  - `-Pmutation.minScore=100` fails after the reports are written, printing the mutation score and test strength with two decimals and the required 100%;
  - `-Pmutation.minScore=0` and a decimal such as `=0.5` succeed;
  - `=abc` and `=120` fail before any test runs;
  - without the property, the run succeeds whatever the score.

## 4. Documentation

- [x] 4.1 Add `mutationReport` to the `CLAUDE.md` Testing command table and a short paragraph after the coverage notes: the three `mutation.*` properties and their defaults, the report paths, that only unit tests run and Docker isn't needed, that new Spring or Testcontainers test classes need `@Tag("integration")`, that the full task name is required, and that it never runs in `build` or CI. Add `cardigan.pitest` to "Where does X go?". Verify that every documented command runs as written.

## 5. Integration check

- [x] 5.1 Run `./gradlew build` and confirm it passes with no PIT or mutation task in its output and `.github/workflows/ci.yml` unchanged. Run `openspec validate be-pitest-mutation-testing --strict` and confirm it passes.

## Workflow follow-up

- Commit each group per the project's Git workflow once `./gradlew build` passes, only after the user's OK to implement.
- Archive the change only when the user says so.
