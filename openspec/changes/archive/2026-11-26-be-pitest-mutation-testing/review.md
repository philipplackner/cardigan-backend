# Artifact review (light): be-pitest-mutation-testing

**Verdict:** Ready
**Reviewed:** 2026-11-23, schema `careful-planning`, repo kind `code`

| Pass | Blocker | Major | Minor |
|---|---|---|---|
| Testability | 0 | 1 | 2 |
| Security | 0 | 0 | 0 |
| Hidden assumptions | 0 | 2 | 0 |
| Design contract | 0 | 2 | 1 |

## Findings

### D1: The aggregator picks up every module that has PIT applied, not just the selected ones
**Severity:** major · **Status:** applied
**Location:** design.md > Root plugin `cardigan.pitest.report` and `mutationReport`
**Current text:** "`mutationReport` depends on the `pitest` task of each selected module and on the root `pitestReportAggregate`, which merges those modules' XML into `build/reports/pitest/index.html`. Only selected modules are wired in, so unselected modules run nothing."
**Failure scenario:** `PitestAggregatorPlugin` 1.19.0 collects `mutations.xml` and line coverage from `project.allprojects.findAll { it.plugins.hasPlugin(PitestPlugin) }`. The `cardigan.pitest` switch applies PIT to all three modules whenever `mutationReport` is requested. So after a full run, `-Pmutation.modules=board` produces a combined report that also contains stale `:user` and `:common` mutants. That breaks the "One module" scenario, and the combined report then disagrees with the score the root task computes.
**Fix:** In design.md, replace the paragraph with: "`mutationReport` depends on the `pitest` task of each selected module and on the root `pitestReportAggregate`. The module plugin applies `info.solidsoft.pitest` only to modules in the parsed `mutation.modules` selection (all opted-in modules when it is absent), so the aggregator, which collects from every project that has the PIT plugin, sees only selected modules. Unselected modules get no PIT task and run nothing." Add to task 3.1: "`-Pmutation.modules=board` after a full run: the combined report contains only `:board` classes."

### D2: PIT fails by default when there are no mutants, so the zero-mutant scenario can't happen
**Severity:** major · **Status:** applied
**Location:** specs/mutation-testing/spec.md > An optional minimum score fails the task > Scenario: No mutants in the selection; design.md > `cardigan.pitest` wraps `info.solidsoft.pitest`
**Current text:** "**WHEN** the selection produces no mutants and `-Pmutation.minScore=50` is given **THEN** the minimum counts as met"
**Failure scenario:** PIT's `failWhenNoMutations` defaults to true. A module with no mutable classes, for example a newly opted-in empty module, fails its `pitest` task before `mutationReport` checks anything. The scenario then fails, and so does a plain `mutationReport` without `minScore`.
**Fix:** Add to the design.md configuration list: "- `failWhenNoMutations = false`, so a module without mutants yields an empty report instead of failing; the root task treats zero mutants as described in the spec." Add a scenario to "One task writes HTML mutation reports": "#### Scenario: A module without mutants / - **WHEN** an opted-in module has no mutable main classes and `./gradlew mutationReport` runs / - **THEN** the command succeeds and the combined report shows that module with no mutants".

### T1: PIT statuses other than killed, timed-out, memory-error, survived and no-coverage aren't defined
**Severity:** major · **Status:** applied
**Location:** specs/mutation-testing/spec.md > Requirement: Score and test strength are defined as in PIT's report
**Current text:** "The mutation score SHALL be killed mutants divided by all mutants of the selected modules together. Test strength SHALL be killed mutants divided by mutants with test coverage. Timed-out and memory-error mutants SHALL count as killed."
**Failure scenario:** PIT's `DetectionStatus.isDetected()` is also true for `RUN_ERROR` and `NON_VIABLE`, and `NOT_STARTED`/`STARTED` can appear after a crashed minion. The design counts only `KILLED`, `TIMED_OUT`, `MEMORY_ERROR`. A run with non-viable mutants (common with Kotlin bytecode) then prints a lower score than the HTML report, even though the requirement says "as in PIT's report". Mobile could map the extra statuses differently, which breaks the parity D13 asks for.
**Fix:** Ask the user, then make it explicit. Proposed text, matching PIT: "Killed mutants SHALL be those with status `KILLED`, `TIMED_OUT`, `MEMORY_ERROR`, `RUN_ERROR` or `NON_VIABLE`, as PIT counts them as detected. Mutants with test coverage SHALL be all mutants except `NO_COVERAGE`." Add a scenario with one `RUN_ERROR` and one `NON_VIABLE` mutant. Carry the same list into design.md and task 3.1, and tell mobile. A wrong implementation this catches: counting only the three statuses named in D12.

### A1: Parallel module runs multiply PIT's threads, and memory errors count as killed
**Severity:** major · **Status:** applied
**Location:** specs/mutation-testing/spec.md > Requirement: The thread count can be overridden; design.md > `threads`
**Current text:** "Mutation testing SHALL use as many worker threads as the machine has available processors."
**Failure scenario:** `org.gradle.parallel=true` runs `:common:pitest`, `:user:pitest` and `:board:pitest` at the same time, each starting one minion JVM per processor with the JVM's default heap. On a 12-core laptop that is 36 JVMs. Out-of-memory minions are reported as `MEMORY_ERROR`, which the spec counts as killed, so the score goes up when the machine runs short of memory. One implementer might serialize the modules and another might not.
**Fix:** Add to the requirement: "Module mutation runs SHALL NOT overlap: at most one module's PIT run SHALL execute at a time, even with parallel Gradle execution, so the thread count is the total for the machine." Add to design.md: "Each `pitest` task uses a shared build service with `maxParallelUsages = 1`." Add to task 3.2: "Verify with `--info` that the module PIT runs do not overlap." Wrong implementation caught: three PIT runs in parallel, each with N threads.

### A2: Gradle may skip or restore a module's PIT run from cache, but the spec promises a full analysis on every run
**Severity:** major · **Status:** applied
**Location:** specs/mutation-testing/spec.md > Requirement: Incremental analysis is opt-in > Scenario: Default full run
**Current text:** "**WHEN** `./gradlew mutationReport` runs twice without `mutation.incremental` **THEN** both runs analyse every mutant, and no history file is read"
**Failure scenario:** `PitestTask` in 1.19.0 is `@CacheableTask`, and the build cache is on in `gradle.properties`. With nothing changed, the second run is `UP-TO-DATE` or `FROM-CACHE` and analyses nothing, so the scenario fails. The incremental scenario then can't show history reuse either. One implementer might keep Gradle's caching and another might turn it off.
**Fix:** Following D9 ("every run is a full analysis"), add to design.md: "Each module `pitest` task sets `outputs.upToDateWhen { false }` and `outputs.cacheIf { false }`, so every request runs PIT. Speed-ups come only from `mutation.incremental`." Add to the requirement: "Every `mutationReport` SHALL run PIT for each selected module; Gradle SHALL NOT skip it as up to date or restore it from the build cache." Wrong implementation caught: a second unchanged run reported `UP-TO-DATE`.

### T2: Test strength with zero covered mutants is undefined
**Severity:** minor · **Status:** applied
**Location:** specs/mutation-testing/spec.md > Requirement: An optional minimum score fails the task
**Current text:** "print the mutation score and test strength with two decimals and the required score"
**Failure scenario:** When every mutant in the selection is `NO_COVERAGE`, test strength is 0/0. One implementation prints `NaN`, another prints `0.00%`, and a third crashes.
**Fix:** Append to the requirement: "When no mutant has test coverage, test strength SHALL be printed as 0.00%." Wrong implementation caught: a division-by-zero exception or `NaN%` in the failure message.

### T3: The null-check exclusion has no scenario
**Severity:** minor · **Status:** applied
**Location:** specs/mutation-testing/spec.md > Requirement: Kotlin compiler-generated code is not mutated
**Current text:** only "#### Scenario: Data class methods"
**Failure scenario:** The requirement also forbids mutants in generated null checks, but no scenario covers it. Leaving out `avoidCallsTo kotlin.jvm.internal` would pass every spec scenario.
**Fix:** Add "#### Scenario: Null-check intrinsics / - **WHEN** a module has a public function with a non-null parameter and the report is generated / - **THEN** the report has no mutant on a call into `kotlin.jvm.internal.Intrinsics`".

### D3: Reusing the coverage parser as-is would name `coverage.modules` in mutation errors
**Severity:** minor · **Status:** applied
**Location:** design.md > Property parsing at configuration time
**Current text:** "`mutation.modules`: the same rules and the same shared parser as `coverage.modules`"
**Failure scenario:** `parseCoverageModules` in `build-logic/convention/src/main/kotlin/CoverageProperties.kt` hardcodes `MODULES_PROPERTY` (`coverage.modules`) in its messages. A bad `-Pmutation.modules=cards` would then fail with "Invalid coverage.modules 'cards'".
**Fix:** "`mutation.modules`: the same rules as `coverage.modules`, using the existing parser changed to take the property name as a parameter, so each error names the property the user passed."

## Trust boundaries
- Build dependencies (Gradle plugin portal, Maven Central): a tampered artifact would run in the build. The versions are pinned and the user approved them, with no dependency verification, the same as every other dependency in the repo. Accepted project practice, not a finding.
- `mutations.xml` parsed by the root task: a crafted XML file could try XXE. The design turns off external DTD loading, so this is stopped.
- `history.bin` under `build/`: a crafted file could feed PIT bad history. Only someone who can already write the local working tree could do this, and the feature is off by default. Not a realistic gap.
- No network, auth, user input or runtime code is involved. Production behavior is unchanged.

## Coverage
Read: decisions.md, proposal.md, specs/mutation-testing/spec.md (10 requirements, 27 scenarios), design.md, tasks.md, `openspec/config.yaml`, `CLAUDE.md`, the requirement list of the `test-coverage` main spec, the Kover convention plugins and `CoverageProperties.kt`, `.github/workflows/ci.yml`, `gradle.properties`, and the `gradle-pitest-plugin` 1.19.0 sources (aggregator discovery, `PitestTask` caching and `failWhenNoMutations`). No other active changes. No shared-spec dependency, as the proposal states. A light review did not open each test class to check which ones need the `integration` tag, did not check PIT 1.30.2 against JDK 25 (deferred to task 1.1 by decisions.md), and did not read the full `test-coverage` spec text.

## Applied changes

All 8 findings were applied on 2026-11-23 at the user's request.

- `specs/mutation-testing/spec.md`: new requirement "Every run executes PIT for each selected module", with the scenarios "Repeated run without changes" and "A module without mutants" (A2, D2). It was split out of "One task writes HTML mutation reports" so that requirement stays under the length limit. "One module" now also covers reports left over from an earlier run (D1). The thread requirement says module runs don't overlap and gains the scenario "Modules run one after another" (A1). The score requirement lists the statuses explicitly, sets test strength to 0% with no covered mutants, and gains the scenarios "Run errors and non-viable mutants" and "No covered mutants" (T1, T2). There is a new scenario, "Null-check intrinsics" (T3).
- `design.md`: the module plugin applies PIT only to selected modules (D1). New configuration entries: `failWhenNoMutations = false` (D2), no up-to-date checks or build caching on `pitest` (A2), and a shared build service with `maxParallelUsages = 1` (A1). The root task counts `RUN_ERROR` and `NON_VIABLE` as killed, and test strength is 0 with no covered mutants (T1, T2). `parseCoverageModules` takes the property name as a parameter (D3).
- `tasks.md`: tasks 2.1 and 3.1 gained the matching configuration and checks. No checkbox changed.
- `openspec validate be-pitest-mutation-testing --strict` passes.

Things to note:
- T1 goes beyond the wording of decisions.md D12, which names only timed-out and memory-error mutants as killed, "as in PIT's report". The new list follows PIT and doesn't contradict D12, but mobile must use the same list for the numbers to match. decisions.md was not edited.
- `proposal.md` didn't depend on the changed wording and was left as it is. There are no non-standard artifacts.
