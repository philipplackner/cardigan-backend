# Artifact review (light): be-ktlint

**Verdict:** Ready
**Reviewed:** 2026-11-26, schema `careful-planning`, repo kind `code`

| Pass | Blocker | Major | Minor |
|---|---|---|---|
| Testability | 0 | 0 | 3 |
| Security | 0 | 0 | 0 |
| Hidden assumptions | 0 | 1 | 1 |
| Design contract | 0 | 1 | 0 |

## Findings

### D1: Root project may get no ktlint tasks without a Kotlin plugin
**Severity:** major · **Status:** applied with edits
**Location:** design.md > Root project and build scripts; specs/code-formatting/spec.md > Modules opt in to formatting checks with one plugin
**Current text:** "The root project applies `cardigan.ktlint`. Without a Kotlin source set it gets only the script tasks, which cover the root `build.gradle.kts` and `settings.gradle.kts`."
**Failure scenario:** ktlint-gradle registers its tasks, the `kotlinScript` pair included, from `plugins.withId("kotlin"…)` callbacks. The root project only declares the Kotlin plugin with `apply false`, so it may get no `ktlintCheck` task at all. Then the root scripts are never checked, and step 5 of the convention plugin has no root task to hang the build-logic dependency on, so build-logic is silently left out of `./gradlew ktlintCheck`. Task 2.3's dry-run would catch it, but the design gives the implementer no fallback, so they would improvise one mid-task.
**Fix:** Add to design.md > Root project and build scripts: "This relies on ktlint-gradle creating the `ktlintKotlinScript*` tasks and the `ktlintCheck`/`ktlintFormat` aggregates in a project with no Kotlin plugin applied. Task 2.3 verifies this first. If the plugin creates no tasks there, the root project does not apply `cardigan.ktlint`. Instead, `:app` adds the root `*.gradle.kts` files through `kotlinScriptAdditionalPaths`, and the convention plugin registers the root `ktlintCheck` and `ktlintFormat` as plain lifecycle tasks that depend on build-logic's tasks. Ask the user before choosing any other workaround."

### A1: Removing ktlintCheck from check depends on plugin order
**Severity:** major · **Status:** applied
**Location:** design.md > `cardigan.ktlint` convention plugin, step 4
**Current text:** "The plugin wires it in when it is applied, so the convention plugin filters `ktlintCheck` out of `check`'s `dependsOn` after applying it."
**Failure scenario:** ktlint-gradle adds the `check` dependency only once the Kotlin plugin is applied. A module that lists `cardigan.ktlint` before its Kotlin or `cardigan.spring.module` plugin gets the filter first and the dependency afterwards. Then `./gradlew build` runs ktlint and fails on violations, which breaks D4. Today's modules may pass task 2.4 because of their plugin order, and the first new module with a different order breaks it.
**Fix:** Replace the sentence with: "ktlint-gradle adds that dependency only once a Kotlin plugin is applied, so the convention plugin removes it inside `pluginManager.withPlugin(\"org.jetbrains.kotlin.jvm\")`, registered after applying ktlint-gradle. That way the removal runs after ktlint-gradle's wiring, whatever order the build file lists the plugins in." Add to task 2.4: "Also verify with a temporary module, or a temporary reorder in `:board`, that lists `cardigan.ktlint` before its Kotlin plugin."

### A2: Reaching build-logic's tasks when it is included only under pluginManagement
**Severity:** minor · **Status:** applied
**Location:** design.md > `cardigan.ktlint` convention plugin, step 5
**Current text:** "makes the root `ktlintCheck` and `ktlintFormat` depend on `:convention:ktlintCheck` and `:convention:ktlintFormat` of the included build `build-logic` (D10)."
**Failure scenario:** `settings.gradle.kts` includes build-logic only inside `pluginManagement { includeBuild(...) }`. If `gradle.includedBuild("build-logic")` can't see a build that is included only for plugins, configuration fails or the implementer adds a second `includeBuild` without a stated reason.
**Fix:** Append: "It looks the build up with `gradle.includedBuild(\"build-logic\")`. If a build included only under `pluginManagement` can't be found that way, also add a top-level `includeBuild(\"build-logic\")` to `settings.gradle.kts`."

### T1: "except for mobile-only keys" can't fail
**Severity:** minor · **Status:** applied
**Location:** specs/code-formatting/spec.md > Rule settings come from a shared editorconfig
**Current text:** "The file SHALL match the mobile repo's, except for mobile-only keys."
**Failure scenario:** Any extra key in the mobile file can be called "mobile-only", so a diverging file still passes. D7 names exactly one allowed difference.
**Fix:** "The file SHALL match the mobile repo's `.editorconfig` line for line, except that the backend's file leaves out `ktlint_function_naming_ignore_when_annotated_with = Composable`."
Wrong implementation caught: a backend file that drops `ij_kotlin_imports_layout` while mobile keeps it.

### T2: Generated code exclusion has no scenario
**Severity:** minor · **Status:** applied
**Location:** specs/code-formatting/spec.md > The commands cover all Kotlin sources and build scripts
**Current text:** "Generated code and build output SHALL NOT be checked or formatted."
**Failure scenario:** Without the `build/` exclude, ktlint checks the `kotlin-dsl` accessors that are generated into build-logic's main source set, and either fails on code nobody can edit or makes `ktlintFormat` rewrite it. No scenario forces a test for this.
**Fix:** Add a scenario: "#### Scenario: Generated sources — **WHEN** build-logic's `kotlin-dsl` accessors have been generated under `build-logic/convention/build/` and `./gradlew ktlintCheck` runs — **THEN** no violation is reported for a file under any `build/` directory, and `./gradlew ktlintFormat` changes no file there"
Wrong implementation caught: a convention plugin without the `filter { exclude { … build/ … } }` step.

### T3: "applies only cardigan.ktlint" is ambiguous
**Severity:** minor · **Status:** applied
**Location:** specs/code-formatting/spec.md > Modules opt in… > Scenario: A new module opts in
**Current text:** "**WHEN** a new module applies only `cardigan.ktlint` in its build file"
**Failure scenario:** Taken literally, the module has no Kotlin plugin and so no Kotlin sources. A tester either builds a module that proves nothing or reads "only" differently from the implementer.
**Fix:** "**WHEN** a new module applies its Kotlin convention plugin plus `cardigan.ktlint`, and has no other ktlint configuration"
Wrong implementation caught: a plugin that needs extra per-module `ktlint { }` setup before the module's files are covered.

## Trust boundaries
- Third-party Gradle plugin and ktlint engine (supply chain): versions are pinned in the catalog, the same as every other build dependency. No new gap.
- `.editorconfig` lookup: a file higher up on a developer's machine could change the rules. `root = true` stops that.
- Report files: SARIF and Checkstyle stay local under `build/`. CI upload is out of scope. No exposure.
- No network, auth, user-input or runtime boundary is touched. No security findings.

## Coverage
Checked all 8 requirements and 13 scenarios in `specs/code-formatting/spec.md`, plus decisions.md, proposal.md, design.md and tasks.md, against `settings.gradle.kts`, root and module `build.gradle.kts` files, `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts`, `gradle.properties` and the overlapping parts of be-detekt-konsist (no conflict: both defer `:tooling` to whichever lands second, and `staticAnalysis` leaves out ktlint on both sides). The ktlint-gradle source wasn't available locally, so D1 and A1 rest on how the plugin is known to wire its tasks. Not read: the mobile repo's mobile-ktlint change, AGENTS.md in full, and the delivery-pipeline main spec beyond the proposal's statement.

## Applied changes
- design.md: step 4 now removes the `check` dependency inside `pluginManager.withPlugin("org.jetbrains.kotlin.jvm")` (A1). Step 5 names the `gradle.includedBuild` lookup and the top-level `includeBuild` fallback (A2). "Root project and build scripts" gets the no-Kotlin-plugin fallback (D1).
- D1 was applied with edits. In the fallback as first proposed, the root project would not apply `cardigan.ktlint`, which contradicts decisions.md D9. In the applied version, the root still applies the plugin, and the convention plugin registers plain root lifecycle tasks there.
- specs/code-formatting/spec.md: the "A new module opts in" scenario is reworded (T3), a "Generated sources" scenario is added (T2), and the mobile match moved into a new requirement, "The editorconfig matches the mobile repo", with a "Compare with mobile" scenario (T1). The requirement was split because the reworded text passed strict validation's 500-character limit.
- tasks.md: task 2.3 verifies the root script tasks and the included-build lookup first. Task 2.4 adds a temporary plugin-reorder check in `:board`. No checkbox changed.
- `openspec validate be-ktlint --strict` passes. No non-standard artifacts in the change folder. No accepted risks.
