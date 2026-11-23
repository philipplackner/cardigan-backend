# Design

## Context

See proposal.md for the motivation and decisions.md for the settled choices. Facts that shape the approach:

- Convention plugins live in `build-logic/convention` as `Plugin<Project>` classes, registered in `libs.versions.toml` without a version (D20 of `be-kover-coverage`).
- The build runs with the configuration cache, build cache and parallel execution on. Anything new must be configuration-cache compatible.
- `cardigan.kover` already applies its tool only when a coverage task is requested, and `coverage.modules` is parsed in `gradle.projectsEvaluated`. Mutation testing reuses both patterns.
- No test class is tagged today. Spring and Testcontainers tests are recognizable only by their annotations or the `E2eTest` base class.
- `:app` has a second `Test` task, `updateOpenApiSnapshot`, which must never run as a side effect. `:app` does not opt in (D2).

## Goals / Non-Goals

**Goals:**
- One command for a mutation report over all opted-in modules or a chosen subset.
- No effect on `build`, `check`, `test` or CI.
- Opting in a future module takes one plugin line.
- Property names and meaning identical to mobile (D13).

**Non-Goals:**
- Killing mutants in code that only integration tests reach (D3).
- Arcmutate or other commercial extensions (D7).

## Decisions

### `cardigan.pitest` wraps `info.solidsoft.pitest`

`cardigan.pitest` applies `info.solidsoft.pitest` to a module only when the requested task names include one whose last segment is `mutationReport` or starts with `pitest`, the same literal-name switch as `isCoverageInstrumented()`, and the module is in the selection: `mutation.modules` is absent, or one of its trimmed entries without a leading `:` equals the module name. The root hook still validates the full value. Otherwise it does nothing, so a normal build has no PIT task and an unselected module has none either. This matters because the aggregator collects reports from every project that has the PIT plugin, so applying it to unselected modules would put their old `mutations.xml` into the combined report. It configures:

- `pitestVersion = 1.30.2` and `junit5PluginVersion = 1.2.2` from the catalog (D6).
- `targetClasses = com.plcoding.cardigan.<module>.*`.
- `testSourceSets = [test]` and `mainSourceSets = [main]`, so `testFixtures` is never a target.
- `excludedGroups = [integration]` (D4).
- `mutators = [DEFAULTS]` (D8), PIT's default `timeoutFactor` and `timeoutConstInMillis` (D10).
- `threads` from `mutation.threads`, else `Runtime.getRuntime().availableProcessors()` (D10).
- `outputFormats = [HTML, XML]`, `exportLineCoverage = true` (needed by the aggregator), `timestampedReports = false` (D14).
- No `historyInputLocation` or `historyOutputLocation`: there is no incremental analysis (D9).
- `failWhenNoMutations = false`, so a module without mutants yields an empty report instead of failing; the root task treats zero mutants as the spec describes.
- `outputs.upToDateWhen { false }` and `outputs.cacheIf { false }` on the `pitest` task. `PitestTask` is `@CacheableTask` and the build cache is on, so without this an unchanged second run would analyse nothing (D9).
- A shared build service registered with `maxParallelUsages = 1`, used by every `pitest` task, so module runs don't overlap under `org.gradle.parallel`. Otherwise each module starts one minion JVM per processor at the same time, and out-of-memory minions show up as `MEMORY_ERROR`, which counts as killed.
- No `mutationThreshold` or `coverageThreshold`. PIT's own threshold is per module; `mutation.minScore` is about the selection as a whole and is checked in the root task.
- Kotlin and class exclusions, below.

The PIT Gradle plugin goes into `build-logic` as a `compileOnly` dependency, like Kover. Since `:app` doesn't apply `cardigan.pitest`, `updateOpenApiSnapshot` can't be pulled in.

Alternative: our own `JavaExec` task around PIT's command-line entry point. More control, but we would rebuild classpath handling and aggregation.

### Root plugin `cardigan.pitest.report` and `mutationReport`

A root plugin, applied in the root `build.gradle.kts` like `cardigan.kover.report`, always registers `mutationReport`, so an abbreviation resolves and reaches the guard, and applies `info.solidsoft.pitest.aggregator` to the root only when mutation testing is requested. The aggregator collects the reports of every module with PIT applied, so the root plugin resets its `mutationFiles`, `lineCoverageFiles`, `sourceDirs` and `additionalClasspath` to the selected modules; otherwise a stale report of an unselected module would leak into the combined report. It finds opted-in modules with `subprojects { pluginManager.withPlugin("cardigan.pitest") { … } }`.

`mutationReport` depends on the `pitest` task of each selected module and on the root `pitestReportAggregate`, which merges those modules' XML into `build/reports/pitest/index.html`. The aggregator collects from every project that has the PIT plugin, so it sees only selected modules because the module plugin applies PIT only to them (above). Unselected modules get no PIT task and run nothing.

`mutationReport` is a task class in build-logic. Its inputs are the selected modules' `mutations.xml` files and the optional minimum score. It:

- reads every `mutations.xml` with external DTD loading off and counts mutants by `status`: killed = `KILLED`, `TIMED_OUT`, `MEMORY_ERROR`, `RUN_ERROR`, `NON_VIABLE` (PIT's `isDetected()`); covered = all except `NO_COVERAGE`;
- computes mutation score = killed / all and test strength = killed / covered, unrounded, over the selection as a whole; test strength is 0 when no mutant is covered;
- prints the path of the combined report;
- if `mutation.minScore` is set and the mutation score is below it, fails and prints the mutation score and test strength with two decimals and the required score. With zero mutants the minimum counts as met.

It declares no outputs and is never up to date, so the check always runs.

The definitions follow PIT's own detected statuses, so they match the combined HTML report; task 3.1 compares them. Mobile must use the same status list for the numbers to match across platforms.

### Property parsing at configuration time

All three `mutation.*` properties are read with `providers.gradleProperty` and validated in a `gradle.projectsEvaluated` hook of the root plugin, once the opted-in modules are known. A bad value throws a `GradleException` before any task, so no test runs:

- `mutation.modules`: the same rules as `coverage.modules`, using the existing `parseCoverageModules` changed to take the property name as a parameter, so each error names the property the user passed: trimmed, deduplicated, leading `:` allowed; absent means all opted-in modules; an empty value, an empty entry (`user,`, `a,,b`) or an unknown name fails with the list of valid names.
- `mutation.threads`: a positive whole number.
- `mutation.minScore`: a number from 0 to 100, same parsing as `coverage.minLine`.

Module plugins read the parsed thread count from a shared function, so module and root can't disagree.

### Integration tests are excluded by a JUnit tag

Every test class that starts a Spring context or a container gets `@Tag("integration")`. `E2eTest` gets it once; JUnit's `@Tag` is `@Inherited`, so subclasses are covered. The PIT JUnit 5 plugin honours `excludedGroups`. The normal `test` tasks don't filter tags, so they still run everything.

Task 2.2 checks the result: PIT must succeed with Docker stopped.

### Class exclusions by annotation

PIT filters only by class and method name. To match by annotation (D11):

- **Primary:** a small task in build-logic per module, run before `pitest`, reads the compiled main classes with ASM and writes the names of classes directly annotated with `@Configuration`, `@ConfigurationProperties` or `@SpringBootApplication` to a file. `excludedClasses` reads that file through a provider, plus the pattern `*ApplicationKt`. Nested classes of an excluded class (`Outer$*`) are excluded with it.
- **Fallback:** name patterns (`*Config`, `*Configuration`, `*Properties`, `*Application`, `*ApplicationKt`), plus a check in task 2.3 that every annotated class matches one.

Task 2.3 uses the primary if the plugin accepts a provider-based `excludedClasses` under the configuration cache, otherwise the fallback, and records the result here. The fallback breaks D11's "by annotation" for oddly named classes, so using it is reported to the user.

**Result (task 2.3):** the primary works with the configuration cache. ASM is not on the build-logic classpath and would be a new dependency, so `pitestNonLogicClassScan` reads the class name and the runtime-visible class annotations with a small class-file reader (JVMS chapter 4) instead. It writes `<module>/build/pitest/excluded-classes.txt`.

### Kotlin junk mutants

- `avoidCallsTo` gets `kotlin.jvm.internal`, so `Intrinsics.checkNotNull*` calls are not mutated.
- `excludedMethods` gets `component*`, `copy`, `copy$default`, `equals`, `hashCode`, `toString`. This also skips hand-written `equals`/`hashCode`/`toString`, which are rare in this codebase.

Other Kotlin noise (elvis, safe casts, `when` branches) stays in the report (D7).

### Findings during implementation

- **Incremental analysis is not available in open-source PIT 1.30.2.** With history locations set, PIT fails with "History has been enabled but no history plugin has been installed/activated". The built-in history store (`DefaultHistoryFactory`, `ObjectOutputStreamHistory`) exists in `pitest-entry` 1.22.1 and is gone in 1.25.8 and 1.30.x; history moved to Arcmutate. The user dropped incremental analysis from the backend (D9).
- **`RUN_ERROR` mutants.** PIT's HTML report counts `RUN_ERROR` as detected, the agreed definition does not. The first full run had one `RUN_ERROR` mutant in `:user`, so `mutationReport` computes 321/659 killed where the HTML shows 322/659. Kept as agreed and reported to the user.
- **PIT adds a synthetic field to mutated classes.** Two `BoardDtoMappersTest` tests compared `declaredFields` of DTOs with an exact set and failed under PIT's coverage instrumentation. They now ignore synthetic fields, which keeps their meaning (Kotlin declares no synthetic fields on these DTOs).
- **`pitest-junit5-plugin` 1.2.3 exists.** The approved 1.2.2 works with JUnit 6.0.3 from the Spring Boot BOM, so it stays.
- **Deprecation warning.** The PIT plugins call `Configuration.setVisible`, deprecated for removal in Gradle 11. The same warning already appears in a normal build without this change.

## Risks / Trade-offs

- [PIT 1.30.2 or the JUnit 5 plugin 1.2.2 may not support JDK 25 class files or the JUnit version from the Spring Boot 4.1 BOM.] → Task 1.1 checks both first and reports a failure to the user instead of working around it.
- [`info.solidsoft.pitest` 1.19.0 is documented for Gradle 9.0, not 9.8.1, and may report configuration-cache problems.] → Task 1.1 checks it. A problem goes back to the user.
- [A new Spring test without the `integration` tag makes PIT start Spring contexts and containers.] → Accepted by D4. `CLAUDE.md` states the tag rule.
- [With unit tests only, controllers and JPA adapters show as "no coverage" and pull the raw score down, also for `mutation.minScore`.] → Accepted by D3 and D11. The report shows test strength next to the score.
- [The plugin switch matches requested task names literally, so an abbreviation such as `./gradlew mR` resolves the root task but PIT isn't applied to modules.] → The root plugin always registers `mutationReport` and, like the coverage guard, fails in `gradle.taskGraph.whenReady` with "Run with the full task name, e.g. ./gradlew mutationReport." when the graph has `mutationReport` but the switch is off.
- [A full run is slow: every mutant runs its covering tests.] → `mutation.modules` and `mutation.threads` shorten runs.
- [The root plugin configures subprojects, which doesn't work with isolated projects.] → Same as Kover; revisit if isolated projects is turned on.

## Migration Plan

Additive build tooling plus test annotations. Rollback: remove the plugin lines, catalog entries and tags.
