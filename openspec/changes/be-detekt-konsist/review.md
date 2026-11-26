# Artifact review (light): be-detekt-konsist

**Verdict:** Ready
**Reviewed:** 2026-11-26, schema `careful-planning-tdd`, repo kind `code`

| Pass | Blocker | Major | Minor |
|---|---|---|---|
| Testability | 0 | 1 | 0 |
| Security | 0 | 0 | 0 |
| Hidden assumptions | 0 | 2 | 1 |
| Design contract | 0 | 1 | 2 |

## Findings

### A1: `includes` globs may not match the paths detekt sees in a real Gradle run
**Severity:** major · **Status:** applied
**Location:** design.md > 3. Config file `config/detekt/detekt.yml`; tasks.md > 4.7
**Current text:** "`includes: ['**/src/test/**', '**/src/testFixtures/**']` `includes` is detekt's standard per-rule path filter."
**Failure scenario:** Java globs need a literal `/` before `src`, so `**/src/test/**` does not match a path that starts with `src/test/...`. If detekt 2.0 matches filters against paths relative to the module's base path, the rule never fires in `./gradlew :user:detekt`. The acceptance tests A04/A05 still pass, because their samples sit under `<tmp>/sample/src/...` with a leading segment. No task checks the rule in a real Gradle run.
**Fix:** In design.md decision 3, add: "The implementer confirms whether detekt 2.0.0-alpha.6 matches `includes` against absolute paths or paths relative to the base path, and picks patterns that match both `src/test/...` and `<module>/src/test/...`." In tasks.md 4.7, add a bullet: "a temporary `import io.mockk.mockk` in `user/src/test/...` is reported by `./gradlew :user:detekt` as `cardigan:NoMockingLibraries`, and the same import in `user/src/main/...` is not."

### A2: `konsist` and `:tooling:test` can be UP-TO-DATE while what they check has changed
**Severity:** major · **Status:** applied
**Location:** design.md > 7. `:tooling` module (Test tasks); design.md > 9. Sample Konsist check
**Current text:** "A second `Test` task named `konsist` runs only that package, on the same test source set"
**Failure scenario:** A `Test` task's inputs are its test classes and classpath, not the sources of `:common`, `:user` and `:board`. After a clean `./gradlew konsist`, adding a Spring import to a domain file leaves the task UP-TO-DATE, and the command passes. The same applies to the acceptance tests in `:tooling:test`, which read `../config/detekt/detekt.yml` without declaring it: removing the `cardigan` entry would not rerun A12.
**Fix:** Add to design.md decision 7: "The `konsist` task declares the `src` folders of `:common`, `:user` and `:board` as inputs (`inputs.files(...).withPathSensitivity(RELATIVE)`), and `:tooling:test` declares `config/detekt/detekt.yml` as an input, so a change to what they check reruns them." In tasks.md 5.1, add to the verify step: "run `./gradlew konsist` once clean, then add the temporary import and run it again without `--rerun`."

### A3: `onlyIf` lambdas must not read `gradle` at execution time
**Severity:** minor · **Status:** applied
**Location:** design.md > 7. `:tooling` module (Test tasks)
**Current text:** "`test` has `onlyIf { \":tooling:test\" or \"tooling:test\" is among the requested task names }`."
**Failure scenario:** A lambda that calls `gradle.startParameter` at execution time breaks the configuration cache, which is on in this build.
**Fix:** Append: "The requested-name check runs at configuration time, like `isMutationRequested()`; the `onlyIf` lambda captures only the resulting `Boolean`."

### T1: The type-resolution scenario doesn't name a rule, so it can pass without type resolution
**Severity:** major · **Status:** applied
**Location:** specs/static-analysis/spec.md > detekt analyzes main and test sources with type resolution > Type-resolution rule
**Current text:** "**WHEN** main code breaks a default rule that needs type resolution and `./gradlew detekt` runs"
**Failure scenario:** Several default rules that are documented as type-resolution rules are inactive by default or also run without a binding context. A check with such a rule passes even if `detekt` is wired to the plain task without type resolution. That is the exact mistake decision 2 guards against.
**Fix:** "**WHEN** main code contains a safe call on a non-null receiver (`val s = \"x\"; s?.length`), which only the type-resolution rule `UnnecessarySafeCall` detects, and `./gradlew detekt` runs **THEN** an `UnnecessarySafeCall` finding is reported." Before using it, the implementer confirms that the rule is active by default in alpha.6, or picks another rule that is active by default and needs type resolution. Wrong implementation caught: a `detekt` task that runs without type resolution reports nothing here.

### D1: The merged SARIF report can't hold "the findings of all modules" when the run fails, and build-logic can't be finalized by a root task
**Severity:** major · **Status:** applied
**Location:** specs/static-analysis/spec.md > detekt writes HTML and SARIF reports; design.md > 5. Merged SARIF report
**Current text:** "Reports SHALL be written also when the run fails." / "the root has a merged SARIF report with the findings of all modules" / "Every `detekt<SourceSet>` task in every module and in build-logic is `finalizedBy` it"
**Failure scenario:** Without `--continue`, the first failing `detekt<SourceSet>` task stops Gradle from starting the remaining modules. The merge then holds only the modules that ran, so the scenario can't be met on failing code, and today's code is expected to fail (D6). Also, an included build (build-logic) can't reference a root-project task, so its tasks can't be `finalizedBy` `detektReportMerge`. If build-logic's detekt fails first, the root `detekt` task never runs.
**Fix:** In the spec, change the requirement's last sentence to: "When the run fails, every module's report and the merged report SHALL still be written for the modules that were analyzed." Add the scenario: "**WHEN** `./gradlew detekt` fails on a finding in `:user` **THEN** `:user` has an HTML and a SARIF report and the root merged SARIF report contains that finding." In design.md decision 5, replace the build-logic part with: "build-logic's SARIF file is added to the merge inputs by file path. The root `detekt` task depends on the included build's task. `detektReportMerge` finalizes every module's `detekt<SourceSet>` task and the root `detekt` task." The alternative is for plain `detekt` to behave like `staticAnalysis` (ignore failures per task, fail at the end), but that would be a new decision shared with mobile (D1), so it is for the user to decide.

### D2: The spec and design differ on which Konsist sources are checked
**Severity:** minor · **Status:** applied
**Location:** specs/static-analysis/spec.md > Domain packages are checked for framework imports; design.md > 9
**Current text:** Spec: "a file in a `domain` package of `:common`, `:user` or `:board`". Design: "Scope: production and test-fixture sources".
**Failure scenario:** A test file in a `...domain` package that imports Spring fails one implementation and passes the other.
**Fix:** In the spec requirement, write: "a file in a `domain` package in the main or testFixtures sources of `:common`, `:user` or `:board`".

### D3: Small inconsistencies between the artifacts
**Severity:** minor · **Status:** applied
**Location:** design.md > Context; design.md > 7; proposal.md > What Changes, Impact
**Current text:** "decisions.md D1–D18 settle every choice below" / "(D9's `src/test`)" / "Document the commands in CLAUDE.md and AGENTS.md." / "detekt Gradle plugin, detekt-api and detekt-test (for the rule), Konsist"
**Failure scenario:** The decisions now go up to D23. D9 is the failure mode; `src/test` comes from D14. CLAUDE.md is a symlink, so editing both files means editing one file twice. The dependency list leaves out the approved `detekt-core` (D19).
**Fix:** "decisions.md D1–D23"; "(D14's `src/test`)"; "Document the commands in AGENTS.md (CLAUDE.md is a symlink to it)."; "detekt Gradle plugin, detekt-api, detekt-test and detekt-core (for the rule and its tests), Konsist".

## Trust boundaries

- Third-party build dependencies (detekt alpha, Konsist beta from Maven Central): a compromised artifact would run at build time. The versions are pinned in the catalog, as for every other dependency. Nothing reaches the runtime artifact, and the module-boundaries delta keeps `:tooling` out of `:app`. No new gap.
- detekt loading `:tooling`'s jar as a plugin: this is project code, under the same review as the rest. No gap.
- No network, auth, user input or runtime surface is touched. The security pass found nothing.

## Coverage

Read: decisions.md, proposal.md, both delta specs, design.md, tests.md and tasks.md; the main `module-boundaries` spec; `openspec/config.yaml`; AGENTS.md/CLAUDE.md (through the session context); `settings.gradle.kts`, the root `build.gradle.kts`, `build-logic/convention/build.gradle.kts`, the `isMutationRequested`/`isCoverageInstrumented` pattern, `.claude/tdd/tdd.conf` and the CI workflow's gradle command. All 14 requirements and 31 scenarios were checked. Not read: the be-ktlint artifacts (the overlap in the catalog, build-logic and AGENTS.md is acknowledged in both changes), the mobile change, the detekt and Konsist jars. Claims about detekt alpha.6 behavior in A1 and T1 are for the implementer to confirm.

## Applied changes

All seven findings were applied as recommended. For D1, the reworded requirement was applied, not the alternative of making plain `detekt` fail at the end.

- `specs/static-analysis/spec.md`: T1 (named type-resolution scenario), D1 (failure wording and the new "Reports after a failed run" scenario), D2 (main or testFixtures sources).
- `design.md`: A1 (path-matching check), A2 (task inputs), A3 (configuration-time check), D1 (merge wiring and build-logic by file path), D3 (D1–D23, D14).
- `tasks.md`: 2.3 (A2, A3), 4.6 (D1), 4.7 (A1, T1), 5.1 (A2). No checkbox changed.
- `proposal.md`: D3 (docs line, Impact docs, dependency list).
- `tests.md`: coverage row for the new scenario, marked not test-first.
- No non-standard artifacts in the change folder. No contradiction with decisions.md. No accepted risks.
- `openspec validate be-detekt-konsist --strict` passes.
