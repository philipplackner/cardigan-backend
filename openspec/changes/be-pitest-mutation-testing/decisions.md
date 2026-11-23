# Decisions: be-pitest-mutation-testing

Confirmed with Philipp Lackner on 2026-11-23. The orchestrator session `cardigan-f2` asked the user and relayed the answers.

## Summary

The backend gets on-demand mutation testing with PIT. Each module opts in with one convention plugin. One root task, `mutationReport`, runs PIT with unit tests only and writes an HTML report per module plus one combined HTML report. Optional properties select modules, set the thread count, turn on incremental analysis and set a minimum score. Nothing runs in `build`, `check`, CI or hooks. The property names and their meaning match mobile.

## Decisions

### D1 — Scope
- **Question**: What does this change cover?
- **Decision**: One manually run Gradle task that produces an HTML mutation report, as a starting point for measuring mutation scores. Not part of the development workflow: no `build`/`check`/CI integration, no agent or git hooks. The only failure on score is an explicitly passed minimum (D12).
- **Alternatives**: —
- **Why**: Given by the user through `cardigan-f2`.
- **Depends on**: —

### D2 — Which modules
- **Question**: Which modules are mutated?
- **Decision**: `:common`, `:user`, `:board`. `:app` does not opt in.
- **Alternatives**: All four; `:user` and `:board` only.
- **Why**: `:app` main code is only the application class and `@Configuration` classes, which D11 excludes.
- **Depends on**: D11

### D3 — Which tests run against mutants
- **Question**: Which tests does PIT run?
- **Decision**: Unit tests only. No Spring context, no Testcontainers, no E2E. Mutation testing does not need Docker.
- **Alternatives**: Unit + module integration tests; everything incl. E2E.
- **Why**: PIT reruns covering tests per mutant in forked JVMs; Spring and containers would turn minutes into hours. Controllers, JPA adapters and Spring wiring show as "no coverage" as a result.
- **Depends on**: —

### D4 — How integration tests are kept out
- **Question**: How does PIT tell unit tests from Spring/Testcontainers tests?
- **Decision**: A JUnit tag, `integration`, on every test class that starts a Spring context or a container, and once on the `E2eTest` base class. PIT excludes the tag. Normal test runs still run every test.
- **Alternatives**: `excludedTestClasses` globs with a naming convention and renames; a hand-kept list.
- **Why**: Explicit, about 26 classes get one annotation. A new Spring test without the tag slows PIT down but still works.
- **Depends on**: D3

### D5 — Gradle plugin
- **Question**: How is PIT run from Gradle?
- **Decision**: The `info.solidsoft.pitest` Gradle plugin, wrapped in a `cardigan.pitest` convention plugin, like `cardigan.kover`.
- **Alternatives**: Our own `JavaExec` task around PIT's command line.
- **Depends on**: —

### D6 — Versions
- **Question**: Which versions?
- **Decision**: `info.solidsoft.pitest` 1.19.0, PIT 1.30.2, `pitest-junit5-plugin` 1.2.2, pinned in `libs.versions.toml`. The user approved all three dependencies.
- **Alternatives**: The Gradle plugin's default PIT (1.22.1).
- **Why**: JDK 25 class files need a recent ASM.
- **Depends on**: D5

### D7 — Kotlin support
- **Question**: How are Kotlin compiler artifacts handled?
- **Decision**: Open-source PIT only, with filters: no mutants on calls into `kotlin.jvm.internal` (null-check intrinsics), and the generated data-class methods `componentN`, `copy`, `copy$default`, `equals`, `hashCode`, `toString` are excluded.
- **Alternatives**: Arcmutate's commercial Kotlin plugin; the archived `pitest-kotlin` plugin.
- **Why**: Free and maintained. Some Kotlin noise (elvis, safe casts, `when` branches) stays in the report.
- **Depends on**: D6

### D8 — Mutator set
- **Question**: Which mutators?
- **Decision**: `DEFAULTS`.
- **Alternatives**: `STRONGER`; `ALL`.
- **Depends on**: —

### D9 — Incremental analysis
- **Question**: Is PIT's history (incremental analysis) on?
- **Decision**: Opt-in. Off by default, so every run is a full analysis. `-Pmutation.incremental` turns it on, with the history file under `build/`. Same on mobile and web.
- **Detail**: `-Pmutation.incremental` without a value or `=true` turns it on, `=false` turns it off, compared case-insensitively; any other value fails before any test runs, naming the property and the value. When on, it reads and writes `<module>/build/pitest/history.bin`; otherwise no history is read. Same as mobile (revised by the user through `cardigan-f2` on 2026-11-23, replacing "on whenever present").
- **Alternatives**: Always off; always on.
- **Why**: Decided by the user to keep clean baselines by default and allow fast reruns on request.
- **Depends on**: —

### D10 — Threads and timeouts
- **Question**: How many worker threads and which timeouts?
- **Decision**: Threads = available processors by default, overridable with `-Pmutation.threads=<n>`. PIT's default timeouts. Same as mobile.
- **Detail**: `mutation.threads` must be a positive whole number.
- **Alternatives**: Available processors without override; a fixed count.
- **Depends on**: —

### D11 — Excluded classes
- **Question**: Which main classes are not mutated?
- **Decision**: The same set as Kover: the application main class and `*ApplicationKt`, classes annotated with `@Configuration`, `@ConfigurationProperties` or `@SpringBootApplication`. Matched by annotation, with name patterns only as a fallback if the annotation scan doesn't work (design).
- **Alternatives**: Also exclude everything only integration tests reach; no exclusions.
- **Why**: Integration-only code stays visible. PIT's test strength ignores "no coverage" mutants.
- **Depends on**: D3

### D12 — Thresholds
- **Question**: Does anything fail on a low mutation score?
- **Decision**: An optional `-Pmutation.minScore=<percent>`. It is never set in the build or CI, and the task fails only when it is passed and the selection's mutation score is below it. Same on mobile; web gets an optional break value.
- **Detail**: Mutation score = killed / all mutants; test strength = killed / mutants with coverage; timed-out and memory-error mutants count as killed, as in PIT's report. `minScore` (0–100) checks the mutation score, not test strength. On failure the run prints both values after the reports are written. A value outside 0–100 or not a number fails before any test runs. Same as mobile.
- **Alternatives**: No thresholds.
- **Depends on**: D1, D13

### D13 — Task name and module selection
- **Question**: What is the task called, and can it be limited to modules?
- **Decision**: Root task `mutationReport` with optional `-Pmutation.modules=user,board`, like `coverageReport`. The names `mutationReport`, `mutation.modules`, `mutation.threads`, `mutation.incremental` and `mutation.minScore` and their meaning are identical on mobile.
- **Detail**: `mutation.modules` follows the `coverage.modules` rules: trimmed and deduplicated, leading `:` allowed, absent means all modules; an empty value, an empty entry (`user,`, `a,,b`) or an unknown module fails with the list of valid ones. Same as mobile (revised by the user through `cardigan-f2` on 2026-11-23, replacing "empty means all").
- **Alternatives**: `mutationReport` without selection; the plugin's stock tasks only.
- **Depends on**: D2

### D14 — Report location and formats
- **Question**: Where do reports go?
- **Decision**: Per-module HTML and XML under `<module>/build/reports/pitest/`, plus one combined HTML report at `build/reports/pitest/index.html` in the root. No timestamped report folders.
- **Alternatives**: Per-module reports only.
- **Depends on**: D13

### D15 — Spec placement and name
- **Question**: Where is this specified?
- **Decision**: A new capability, `mutation-testing`. Change name `be-pitest-mutation-testing`.
- **Alternatives**: A delta to `test-coverage`; no spec.
- **Depends on**: —

## Facts

- Modules: `:app`, `:common`, `:user`, `:board` — source: `settings.gradle.kts`.
- Test classes by kind (plain unit vs Spring context or Testcontainers): `:common` 13 vs 1, `:user` 16 vs 3, `:board` 15 vs 8, `:app` 8 vs 14 — source: `grep` for `@SpringBootTest`, `@DataJpaTest`, `@WebMvcTest`, `@JsonTest`, `E2eTest` over `*Test.kt`.
- No test class carries a JUnit `@Tag` today — source: `grep`.
- Gradle 9.8.1 with configuration cache, build cache and parallel execution on; JDK 25; Kotlin 2.3.21 — source: `gradle-wrapper.properties`, `gradle.properties`, `libs.versions.toml`.
- `cardigan.kover` applies Kover only when a coverage task is requested — source: `build-logic/convention/src/main/kotlin/KoverConventionPlugin.kt`.
- `:app` has a second `Test` task, `updateOpenApiSnapshot`, which rewrites the committed snapshot — source: `KoverConventionPlugin.kt`.
- `info.solidsoft.pitest` latest is 1.19.0 (Gradle 8.4+/9, config cache since 1.7.0, defaults to PIT 1.22.1); PIT latest is 1.30.2 (ASM 9.11); `pitest-junit5-plugin` latest is 1.2.2; open-source `pitest-kotlin` is archived since 2023 — source: GitHub release pages, checked 2026-11-23.

## Out of scope

- Any mutation score policy in a shared or platform spec.
- CI, `build`, `check`, git hooks, agent hooks.
- Integration, Testcontainers and E2E tests as mutation killers.
- Commercial PIT extensions.
- Mobile and web mutation testing.

## Deferred

- Whether PIT 1.30.2 reads JDK 25 class files and whether `pitest-junit5-plugin` 1.2.2 runs on the JUnit version from the Spring Boot 4.1 BOM, and whether `info.solidsoft.pitest` 1.19.0 works on Gradle 9.8.1 with the configuration cache — settle before: implementation (task 1.1). A failure goes back to the user.
