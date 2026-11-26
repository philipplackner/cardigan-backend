# Design

## Context

- See proposal.md for why; decisions.md D1–D23 settle every choice below.
- The build uses Kotlin 2.3.21, JDK 25, Gradle 9.8.1 with configuration cache and parallel execution on. Convention plugins live in the included build `build-logic` (`:convention`), which reads the root version catalog.
- On-demand tools already exist. `cardigan.pitest` and `cardigan.kover` detect the literal requested task names (`gradle.isMutationRequested()`, `CoverageInstrumentation.kt`) to decide what to wire. This design reuses that idea for the `:tooling` tests and for `staticAnalysis`.
- detekt 2.0.0-alpha.6 facts (checked in the published jars):
  - The plugin id `dev.detekt` registers a plain `detekt` task without type resolution, plus `detekt<SourceSet>` tasks with type resolution, e.g. `detektMain`, `detektTest`, `detektTestFixtures`.
  - By default the plugin hooks its tasks into `check`.
  - The rule API is `dev.detekt.api.Rule`, `RuleSet`, `RuleSetProvider` and `Config`. The plugin ships `dev.detekt.gradle.report.ReportMergeTask`.
  - `detekt-api` is built against Kotlin 2.4.10, one minor version ahead of this build.

## Goals / Non-Goals

**Goals:**
- Name the public surface of the sample rule precisely enough for test-first.
- Wire the commands so the names and behavior match mobile-detekt-konsist exactly.
- Keep `./gradlew build` and CI byte-for-byte unaffected in what they run.

**Non-Goals:**
- No additional rule configuration beyond the ktlint overlaps and the custom rule.
- No changes to `cardigan.kover`, `cardigan.pitest` or their report tasks beyond keeping `:tooling` out (it never applies them).

## Decisions

### 1. Version catalog entries
Add the versions `detekt = "2.0.0-alpha.6"` and `konsist = "1.0.0-beta.1"`. Add these entries:
- libraries `detekt-gradlePlugin` (`dev.detekt:detekt-gradle-plugin`), `detekt-api`, `detekt-test`, `detekt-core` (test-only, D19), `konsist` (`com.lemonappdev:konsist`)
- plugin `detekt` (`dev.detekt`)
- plugin alias `convention-detekt` (`cardigan.detekt`)

build-logic gets `compileOnly(libs.detekt.gradlePlugin)`, like kover and pitest.

### 2. `cardigan.detekt` convention plugin (`DetektConventionPlugin`)
It applies `dev.detekt` and configures it:
- `config.setFrom(rootDir/config/detekt/detekt.yml)` and `buildUponDefaultConfig = true` (D6). `parallel = true`. No baseline.
- Reports: HTML and SARIF on, all other formats off (D13).
- `failOnSeverity` set to the lowest severity, so any finding fails the run (D7). `ignoreFailures` stays false, except under `staticAnalysis` (decision 6).
- `detektPlugins(project(":tooling"))`, so every opted-in module runs the `cardigan` rule set. `:tooling` itself also adds it; Gradle resolves the self-reference to the module's own jar. If that proves cyclic, `:tooling` gets the rule jar through a separate configuration (implementation detail, no spec impact).
- **The `detekt` task name means "with type resolution" (D3, D10).** The plain `detekt` task gets an empty source and `dependsOn` every `detekt<SourceSet>` task of the module (main, test, testFixtures where present). `./gradlew :user:detekt` therefore runs `detektMain`, `detektTest` and `detektTestFixtures`.
  - Alternative: a new task name such as `detektAll`. Rejected, because the agreed command is `detekt`.
- **Off `check` (D7).** The plugin removes every detekt task from `check`'s dependencies.
  - Alternative: apply detekt only when requested, like pitest. Rejected, because the IDE and `:module:detekt` should always see the tasks, and removing the `check` edge is enough.

### 3. Config file `config/detekt/detekt.yml`
Only overrides:
- The `style` and `formatting` rules that duplicate ktlint are turned off: `MaxLineLength`, `NewLineAtEndOfFile`, `TrailingWhitespace`, `NoTabs`, `UnnecessaryParentheses` if it overlaps, and any other rule the implementer finds duplicating `ktlint_official`. Each one is listed under `# duplicates ktlint`.
- The custom rule:
  ```yaml
  cardigan:
    NoMockingLibraries:
      active: true
      includes: ['**/src/test/**', '**/src/testFixtures/**']
  ```
  `includes` is detekt's standard per-rule path filter. It is how the rule applies to test source sets only (D15). `testFixtures` counts as test code, because it ships only to tests.
  The implementer confirms whether detekt 2.0.0-alpha.6 matches `includes` against absolute paths or paths relative to the base path, and picks patterns that match both `src/test/...` and `<module>/src/test/...`.
  - Alternative: different config per `detekt<SourceSet>` task. Rejected, because it means two config files.
- build-logic uses the same file. The `cardigan` section has no effect there because the rule set isn't loaded. Config validation must therefore allow an unknown rule set: `config.validation: true` with `excludes: 'cardigan.*'`, or the equivalent alpha.6 key.

### 4. build-logic
`build-logic/convention/build.gradle.kts` applies `alias(libs.plugins.detekt)` directly. It uses the same config file (`../../config/detekt/detekt.yml`), the same reports and the same type-resolution wiring, but no `detektPlugins` (D11). It is also removed from `check`, so `./gradlew -p build-logic test` (documented for the score counting) is unaffected.

The root `build.gradle.kts` registers a root task `detekt`. It depends on `gradle.includedBuild("build-logic").task(":convention:detekt")` and finalizes into the SARIF merge. Because Gradle's task-name selector runs `detekt` in every project, `./gradlew detekt` runs the root task plus every module's task. `./gradlew :user:detekt` runs only `:user`.

### 5. Merged SARIF report
The root registers `detektReportMerge` (`ReportMergeTask`), with output `build/reports/detekt/merge.sarif`. `detektReportMerge` finalizes every module's `detekt<SourceSet>` task and the root `detekt` task, and each module's SARIF file is added to its inputs. build-logic is an included build and can't reference a root task, so its SARIF file is added to the merge inputs by file path, and the root `detekt` task depends on the included build's task. Because a finalizer runs even after a failure, the merged report also exists when detekt fails. It holds the findings of the modules that were analyzed; without `--continue`, Gradle doesn't start the remaining modules after the first failure.

### 6. `staticAnalysis` (root) — run both, fail at the end (D9)
- When the requested task names contain `staticAnalysis`, the convention plugin sets `ignoreFailures = true` on every detekt task, and `:tooling` sets it on the `konsist` test task. This uses the same literal-name detection as `isMutationRequested()`.
- `staticAnalysis` depends on `detekt` (root + all modules) and `:tooling:konsist`. After they run, it reads the merged SARIF (result count) and the Konsist JUnit XML results (failures + errors). It fails with one message listing the detekt finding count with the merged report path, and the failing Konsist checks. When both are zero it succeeds.
- Alternative: set `startParameter.isContinueOnFailure` programmatically. Rejected, because it is unreliable with the configuration cache and also hides unrelated failures.
- Configuration-cache safe: the task gets the report files as `@InputFile`/`@InputFiles` providers and doesn't touch `project` at execution time.

### 7. `:tooling` module
`settings.gradle.kts` includes `:tooling`. `tooling/build.gradle.kts` applies:
- `cardigan.kotlin.jvm`
- `cardigan.detekt`

Dependencies:
- `compileOnly(libs.detekt.api)`
- `testImplementation(libs.detekt.test)`
- `testImplementation(libs.detekt.core)` (D19), for the rule's acceptance tests
- `testImplementation(libs.konsist)`
- JUnit 5 and AssertK

There are no project dependencies (module-boundaries delta). It never applies `cardigan.kover` or `cardigan.pitest` (D17), and `coverage.modules` and `mutation.modules` reject `tooling` as an unknown name, as they do today.

Packages:
- `com.plcoding.cardigan.tooling.detekt`: the rule set
- `com.plcoding.cardigan.tooling.konsist` (test only): the Konsist checks

Test tasks:
- `test` excludes the `com.plcoding.cardigan.tooling.konsist` package.
- A second `Test` task named `konsist` runs only that package, on the same test source set (D14's `src/test`). Because the task name selector finds it, `./gradlew konsist` works from the root without a root task.
- Both run only when requested explicitly (D7, D14). `test` has `onlyIf { ":tooling:test" or "tooling:test" is among the requested task names }`. `konsist` has `onlyIf { "konsist", ":tooling:konsist" or "staticAnalysis" is requested }`. Neither is wired into `check`.

  The requested-name check runs at configuration time, like `isMutationRequested()`; the `onlyIf` lambda captures only the resulting `Boolean`.

  So `./gradlew build`, `./gradlew check` and `./gradlew test` skip both. A skip through `onlyIf` shows as `SKIPPED`, not as a failure.
- The `konsist` task declares the `src` folders of `:common`, `:user` and `:board` as inputs (`inputs.files(...).withPathSensitivity(RELATIVE)`), and `:tooling:test` declares `config/detekt/detekt.yml` as an input, so a change to what they check reruns them.
  - Alternative: a separate source set `konsistTest`. Rejected, because D9 places the tests in `src/test`.

### 8. Public surface of the sample rule (test-first)
```kotlin
package com.plcoding.cardigan.tooling.detekt

class CardiganRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId // = RuleSetId("cardigan")
    override fun instance(): RuleSet // contains exactly ::NoMockingLibraries
}

class NoMockingLibraries(config: Config) : Rule(config, description = "...") {
    // rule name = "NoMockingLibraries" (from the class name)
    override fun visitImportDirective(importDirective: KtImportDirective)
}
```
- Registered in `src/main/resources/META-INF/services/dev.detekt.api.RuleSetProvider`.
- One finding per offending import directive, located at that directive. The message is exactly `Import of '<path>' is not allowed. Use fakes instead of mocking libraries.` (D23), where `<path>` is the imported path as written, including `.*` for star imports and without the alias.
- Matching is case-sensitive (D21). An import is offending when its imported FQ name equals `org.mockito` or `io.mockk`, or starts with `org.mockito.` or `io.mockk.`. Star imports (`import io.mockk.*`) are checked on the package path; aliased imports on the imported path, not the alias. Prefix-only matches such as `org.mockitox` don't count.
- No type resolution needed; the rule works with detekt-test's plain `lint(code)`.
- Off unless configured (D22). The rule set provides no default `active: true`, so only a config that activates it runs it.
- Suppression with `@Suppress("cardigan:NoMockingLibraries")` / `@file:Suppress(...)` is standard detekt behavior and needs no code.
- The "test source sets only" behavior comes from the `includes` config (decision 3), not from the rule.
- **Test levels for `:tooling`:**
  - Component level: detekt-test's `lint(code)` against `NoMockingLibraries` directly.
  - Acceptance level: detekt's engine (`detekt-core`, D19) run in-process from `:tooling` tests. The run uses the real `config/detekt/detekt.yml`, loads the rule set only through detekt's plugin mechanism (the `META-INF/services` registration on the classpath), and analyzes files at `src/main/...`, `src/test/...` and `src/testFixtures/...` paths.
  - `.claude/tdd/project.md` names only HTTP E2E in `:app` as its acceptance level, so the change adds this `:tooling` level there. Mobile does the same.

### 9. Sample Konsist check
Written by the test-writer agent as a `[plain]` task, because `src/test` is reserved for it (D20).
`com.plcoding.cardigan.tooling.konsist.DomainLayerTest`:
- Scope: production and test-fixture sources of the modules `common`, `user` and `board`.
- It takes the files whose package contains a `domain` segment and asserts that none has an import starting with `org.springframework.` or `jakarta.persistence.`.
- The Konsist assertion failure names the offending files.
- Konsist finds the project root on its own; the test needs no path config.

### 10. Docs
Add a "Static analysis" table and short notes to AGENTS.md (CLAUDE.md is a symlink to it), next to the coverage and mutation sections. It lists:
- the five commands
- that nothing runs in build or CI
- the report paths
- that new modules opt in with `alias(libs.plugins.convention.detekt)`

## Risks / Trade-offs

- **[detekt is an alpha]** Config keys or task names may change in the next alpha. → Pinned in the catalog; bumps are deliberate. The 2.0 rule API is the one the final release will keep.
- **[Kotlin version skew]** `detekt-api` and its compiler are Kotlin 2.4.10; `:tooling` compiles with Kotlin 2.3.21. Kotlin reads metadata one minor version ahead, so this should compile. → If it doesn't, `:tooling` compiles with `-Xskip-metadata-version-check`, or the project Kotlin is bumped in a separate change. Type resolution analyzes 2.3 sources with the 2.4 analysis API, which is backward compatible.
- **[detekt plugin on build-logic's classpath]** Same skew for `compileOnly(libs.detekt.gradlePlugin)` against Gradle 9.8.1's embedded Kotlin. → The same mitigations apply. If the types can't be used, the convention plugin configures detekt through `extensions.configure("detekt")` with Gradle's dynamic API.
- **[Existing findings]** `./gradlew detekt` likely fails on today's code (D6, no baseline). This is accepted: it is on demand and can't break `build` or CI.
- **[Tooling self-plugin]** `:tooling` loading its own rules could form a task cycle. → Fall back to a separate configuration, or leave the `cardigan` rule set off for `:tooling` (it has no mocking imports to check anyway).
- **[Konsist beta]** 1.0.0-beta.1 APIs may change before 1.0. → One small check; easy to adapt.
- **[`onlyIf` by requested name]** Running `:tooling:test` through an IDE gutter uses a filtered test task request, which matches `:tooling:test`, so it works. A raw `./gradlew tooling:tes` abbreviation would be skipped. → Documented: use full names, as for coverage and mutation.

## Migration Plan

No runtime or data change. Merge order with be-ktlint doesn't matter. Whichever lands second applies `cardigan.ktlint` to `:tooling`. Rollback: revert the commits; nothing outside the build depends on them.
