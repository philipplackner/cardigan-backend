# Tasks

## 1. Acceptance tests

- [x] 1.1 [acceptance-red] Write A01-A12 from tests.md as engine tests in `tooling/src/test/kotlin/com/plcoding/cardigan/tooling/detekt/` (acceptance level from design.md decision 8: detekt-core in-process, shared `config/detekt/detekt.yml`, rule set loaded only through `META-INF/services`).

## 2. Build setup

- [x] 2.1 [plain] Add to `gradle/libs.versions.toml`: versions `detekt = "2.0.0-alpha.6"` and `konsist = "1.0.0-beta.1"`; libraries `detekt-gradlePlugin`, `detekt-api`, `detekt-test`, `detekt-core`, `konsist`; plugins `detekt` (`dev.detekt`) and `convention-detekt` (`cardigan.detekt`). Add `compileOnly(libs.detekt.gradlePlugin)` to `build-logic/convention/build.gradle.kts`. Verify with `./gradlew -p build-logic compileKotlin`.
- [x] 2.2 [plain] Create the `:tooling` module (design decision 7): include it in `settings.gradle.kts`; `tooling/build.gradle.kts` applies `cardigan.kotlin.jvm`, with `compileOnly(libs.detekt.api)` and test dependencies detekt-test, detekt-core, Konsist, JUnit 5, AssertK. No project dependencies, no `cardigan.kover`, no `cardigan.pitest`. Verify with `./gradlew :tooling:compileKotlin` and `./gradlew projects` listing exactly `:app`, `:common`, `:user`, `:board`, `:tooling`.
- [x] 2.3 [plain] Wire the `:tooling` test tasks (design decision 7): `test` excludes the `com.plcoding.cardigan.tooling.konsist` package and runs only when `:tooling:test`/`tooling:test` is requested; a `Test` task named `konsist` runs only that package and only when `konsist`, `:tooling:konsist` or `staticAnalysis` is requested; neither is wired into `check`. The requested-name check runs at configuration time. `konsist` declares the `src` folders of `:common`, `:user` and `:board` as inputs, and `test` declares `config/detekt/detekt.yml`. Verify that `./gradlew build` shows both as SKIPPED or not run.
- [x] 2.4 [plain] Add `.claude/tdd/project.md` entries for `:tooling`: `tooling` as an area with its test command `./gradlew :tooling:test`, and the `:tooling` acceptance level from design.md decision 8. Make sure `tdd.conf` treats `tooling/src/test` as test code. Verify by reading both files.

## 3. NoMockingLibraries rule

- [x] 3.1 [scaffold] Add `CardiganRuleSetProvider` and `NoMockingLibraries(config: Config)` in `tooling/src/main/kotlin/com/plcoding/cardigan/tooling/detekt/` with the signatures from design decision 8 and `TODO()` bodies where logic goes. Register the provider in `src/main/resources/META-INF/services/dev.detekt.api.RuleSetProvider`. Verify with `./gradlew :tooling:testClasses`.
- [x] 3.2 [red] Write C01-C13 from tests.md as `NoMockingLibrariesTest` (and the provider test it names) in `tooling/src/test/kotlin/com/plcoding/cardigan/tooling/detekt/`.
- [x] 3.3 [green] Implement `NoMockingLibraries` and `CardiganRuleSetProvider` as design decision 8 describes: case-sensitive matching, the exact message, one finding per import, off unless configured. Verify with the tests of 3.2 passing (`./gradlew :tooling:test`).

## 4. detekt wiring and config

- [ ] 4.1 [plain] Create `config/detekt/detekt.yml` (design decision 3): only overrides; the rules that duplicate ktlint off under a `# duplicates ktlint` comment; `cardigan.NoMockingLibraries` with `active: true` and `includes` for `src/test` and `src/testFixtures`; config validation that accepts the `cardigan` section where the rule set isn't loaded. Verify by reading the file against detekt's default config.
- [ ] 4.2 [acceptance-green] Make the acceptance tests of 1.1 pass. Verify with `./gradlew :tooling:test` passing.
- [ ] 4.3 [plain] Add `DetektConventionPlugin` (`cardigan.detekt`) in build-logic (design decision 2):
  - shared config, `buildUponDefaultConfig`, no baseline;
  - HTML and SARIF reports only, fail on any finding;
  - `detektPlugins(project(":tooling"))`;
  - the plain `detekt` task depends on every `detekt<SourceSet>` task and analyzes nothing itself;
  - all detekt tasks removed from `check`;
  - `ignoreFailures = true` when `staticAnalysis` is requested.

  Register it in `gradlePlugin`. Verify with `./gradlew -p build-logic test` and `./gradlew :user:detekt --dry-run` listing `detektMain`, `detektTest` and `detektTestFixtures`.
- [ ] 4.4 [plain] Apply `alias(libs.plugins.convention.detekt)` in `:app`, `:common`, `:user`, `:board` and `:tooling`. If `:tooling` loading its own rules creates a cycle, use the fallback in design.md Risks. Verify with `./gradlew build --dry-run` showing no detekt task, and `./gradlew :tooling:detekt` running.
- [ ] 4.5 [plain] Apply `dev.detekt` directly in `build-logic/convention/build.gradle.kts` (design decision 4): same config, HTML and SARIF reports, type-resolution wiring, no `detektPlugins`, off `check`. Verify with `./gradlew -p build-logic test` not running detekt, and `./gradlew -p build-logic detekt` running it.
- [ ] 4.6 [plain] In the root `build.gradle.kts`, register the root `detekt` task (runs build-logic's `:convention:detekt`) and `detektReportMerge` writing `build/reports/detekt/merge.sarif`. Every module's `detekt<SourceSet>` task and the root `detekt` task are finalized by the merge; build-logic's SARIF is added by file path (design decision 5). Verify that `./gradlew detekt` runs every module and build-logic and writes per-module HTML and SARIF plus the merged SARIF, also when it fails on findings. Verify that `./gradlew :user:detekt` runs only `:user`.
- [ ] 4.7 [plain] Manually check the spec scenarios for detekt, using a temporary sample file and deleting it afterwards:
  - a finding in test code;
  - a type-resolution rule (`UnnecessarySafeCall` on a safe call on a non-null receiver; first confirm it is active by default in alpha.6, otherwise use another default-active rule that needs type resolution and update the spec scenario);
  - a long line not reported;
  - failure on one finding, success on clean code;
  - a temporary `import io.mockk.mockk` in `user/src/test/...` is reported by `./gradlew :user:detekt` as `cardigan:NoMockingLibraries`, and the same import in `user/src/main/...` is not.

  Record the results in the commit message or the task notes.

## 5. Konsist and staticAnalysis

- [ ] 5.1 [plain] Test-writer task (D20, the path guard reserves `src/test`): write `DomainLayerTest` in `tooling/src/test/kotlin/com/plcoding/cardigan/tooling/konsist/` from design decision 9 and the two "Domain packages are checked for framework imports" scenarios. Verify that `./gradlew konsist` passes on the current code and fails, naming the file, with a temporary Spring import in a `board` domain file (then remove it). Run `./gradlew konsist` once clean, then add the temporary import and run it again without `--rerun`.
- [ ] 5.2 [plain] Register the root `staticAnalysis` task (design decision 6): depends on `detekt` and `:tooling:konsist`, reads the merged SARIF and the Konsist JUnit XML, fails at the end with one message when either has findings, and is configuration-cache safe. Verify that with a temporary detekt finding and clean Konsist checks, `./gradlew staticAnalysis` runs Konsist and fails, and that it succeeds when both are clean, or report that detekt has existing findings (D6).

## 6. Docs

- [ ] 6.1 [plain] Add a "Static analysis" section to `AGENTS.md` (`CLAUDE.md` is a symlink to it), next to the coverage and mutation sections:
  - the commands `detekt`, `:<module>:detekt`, `konsist`, `staticAnalysis` and `:tooling:test`;
  - that nothing runs in build or CI, and that full task names are needed;
  - the report paths;
  - that new modules opt in with `alias(libs.plugins.convention.detekt)`;
  - `:tooling` in the module tree and the "Where does X go?" table.

  Verify by reading the section.

## 7. Integration

- [ ] 7.1 [plain] Run `openspec validate be-detekt-konsist --strict`, `./gradlew build`, `./gradlew -p build-logic test`, `./gradlew :tooling:test` and `./gradlew konsist`. Verify all pass, and that `./gradlew build` ran no detekt task, Konsist check or `:tooling` test.

## Workflow follow-up

- Whichever of be-ktlint and be-detekt-konsist lands second applies `cardigan.ktlint` to `:tooling`.
- Archive only when the user says so.
