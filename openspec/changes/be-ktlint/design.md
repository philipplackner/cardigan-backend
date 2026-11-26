# Design

## Context

See proposal.md for the motivation and decisions.md for every settled choice (D1–D15). The build uses convention plugins in the included build `build-logic`. Modules opt in to tooling by applying a plugin (`cardigan.kover`, `cardigan.pitest`), and the root `build.gradle.kts` puts third-party plugins on the classpath with `apply false`. build-logic reads the version catalog through its own `settings.gradle.kts`. Gradle runs with the configuration cache and parallel execution on. CI runs only `./gradlew build`.

## Goals / Non-Goals

**Goals:**
- `./gradlew ktlintCheck` and `./gradlew ktlintFormat` from the repo root cover every file the spec lists, build-logic included.
- Adding a module to ktlint takes one plugin line.
- The setup is the same as in the mobile repo: same plugin, versions, task names, report types and `.editorconfig`.

**Non-Goals:**
- No task runs ktlint implicitly, and no root aggregate under another name.
- No custom rule sets or ktlint rule providers.

## Decisions

### Versions in the catalog
Add `ktlint-gradle` (plugin `org.jlleitschuh.gradle.ktlint`) and `ktlint` (the engine) to `[versions]`, a `ktlint-gradlePlugin` library for build-logic, and a `ktlint` plugin alias. Both versions are the latest stable at implementation time and must equal the ones mobile-ktlint pins. Whichever change is implemented second copies the first one's versions. Setting the engine version explicitly keeps both repos on the same rules even if their plugin versions drift.

### `cardigan.ktlint` convention plugin
New `KtlintConventionPlugin` in `build-logic/convention`, registered as `cardigan.ktlint` and exposed as `convention-ktlint` in the catalog. build-logic gets `compileOnly(libs.ktlint.gradlePlugin)`, and the root build adds `alias(libs.plugins.ktlint) apply false` to put it on the classpath, the same way as kover and pitest. The plugin:

1. Applies `org.jlleitschuh.gradle.ktlint`.
2. Sets `version` from the catalog's `ktlint` version, `outputToConsole = true`, `ignoreFailures = false`, and the reporters `SARIF` and `CHECKSTYLE`. The plugin writes them to `build/reports/ktlint/<task>/`.
3. Excludes anything under the project's `build/` directory with `filter { exclude { ... } }`, so generated sources are never checked.
4. Removes the `ktlintCheck` dependency that ktlint-gradle adds to `check`, so `build` and `check` never run ktlint (D4). ktlint-gradle adds that dependency only once a Kotlin plugin is applied, so the convention plugin removes it inside `pluginManager.withPlugin("org.jetbrains.kotlin.jvm")`, registered after applying ktlint-gradle. That way the removal runs after ktlint-gradle's wiring, whatever order the build file lists the plugins in.
5. When applied to the root project: makes the root `ktlintCheck` and `ktlintFormat` depend on `:convention:ktlintCheck` and `:convention:ktlintFormat` of the included build `build-logic` (D10). It looks the build up with `gradle.includedBuild("build-logic")`. If a build included only under `pluginManagement` can't be found that way, also add a top-level `includeBuild("build-logic")` to `settings.gradle.kts`.

ktlint-gradle creates a check and a format task per Kotlin source set (`main`, `test`, `testFixtures`) and a `ktlintKotlinScript*` pair for the `*.kts` files in the project directory. `ktlintCheck` and `ktlintFormat` aggregate them. Running `./gradlew ktlintCheck` from the root runs the task in every project that has it.

Alternative considered: a separate root plugin (like `cardigan.kover.report`) for step 5. Rejected, because the root only needs two `dependsOn` lines and no aggregate task of its own. Kover and PIT need theirs because they merge reports.

### Root project and build scripts
The root project applies `cardigan.ktlint`. Without a Kotlin source set it gets only the script tasks, which cover the root `build.gradle.kts` and `settings.gradle.kts`. Each module's script tasks cover its own `build.gradle.kts`.

This relies on ktlint-gradle creating the `ktlintKotlinScript*` tasks and the `ktlintCheck`/`ktlintFormat` aggregates in a project with no Kotlin plugin applied. Task 2.3 verifies this first. If the plugin creates no tasks there, the root project still applies `cardigan.ktlint` (D9), and on the root project the convention plugin registers `ktlintCheck` and `ktlintFormat` as plain lifecycle tasks that depend on build-logic's tasks (step 5). `:app` then adds the root `*.gradle.kts` files through `kotlinScriptAdditionalPaths`. Ask the user before choosing any other workaround.

### build-logic
`build-logic/convention/build.gradle.kts` applies `alias(libs.plugins.ktlint)` directly, because an included build can't apply its own convention plugin. It repeats the settings of steps 2–4 (about ten lines; build-logic can't share code with itself). `kotlinScriptAdditionalPaths` adds `../settings.gradle.kts`, because build-logic's root project has no build file and would otherwise leave its settings script unchecked. The `kotlin-dsl` plugin's generated accessors live under `build/` and are excluded.

### `.editorconfig`
New file at the repo root:

```
root = true

[*.{kt,kts}]
ktlint_code_style = ktlint_official
max_line_length = 120
indent_size = 4
indent_style = space
insert_final_newline = true
ij_kotlin_code_style_defaults = KOTLIN_OFFICIAL
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
ij_kotlin_imports_layout = *,java.**,javax.**,kotlin.**,^
ij_kotlin_name_count_to_use_star_import = 2147483647
ij_kotlin_name_count_to_use_star_import_for_members = 2147483647
ij_kotlin_packages_to_use_import_on_demand = unset
```

`root = true` stops lookup at the repo root, so an `.editorconfig` higher up on a developer's machine can't change the rules. The `ij_kotlin_*` keys are the IntelliJ settings that ktlint documents for `ktlint_official`. The final key set must match mobile-ktlint's file exactly, except mobile's `ktlint_function_naming_ignore_when_annotated_with = Composable`. If the two sessions arrive at different keys, the second implementation aligns with the first.

### One-time reformat
After the setup commit: run `./gradlew ktlintFormat`, then fix the remaining `ktlintCheck` violations by hand until it passes. Expected manual work: long lines that ktlint can't wrap (about 124 today, a few of them string literals), and naming-rule findings. Fixes must keep behavior, the API contract and the database schema. Where that isn't possible (API paths, JSON names, enum constants, database names), add `@Suppress("ktlint:standard:<rule>")` on the narrowest declaration (D14). If a rule clashes with a project convention, for example a rule that fights the backticked test names or the `PatchRequest` property pattern, stop and ask the user whether to disable it in `.editorconfig`. Don't decide that during implementation. The reformat is its own commit, and `./gradlew build` must pass before it, with `openapi.json` unchanged.

### Documentation
`AGENTS.md`: a Tech Stack row for formatting (ktlint, `ktlint_official`, on demand), the two commands in the Commands table, a "Where does X go?" row "A new module in ktlint → apply `alias(libs.plugins.convention.ktlint)`", and a short note that nothing runs ktlint automatically.

## Risks / Trade-offs

- [ktlint-gradle changes how it wires into `check`, so the removal stops matching and `build` starts running ktlint] → The integration task verifies with `./gradlew check --dry-run` that no ktlint task is listed.
- [Without `--continue`, `./gradlew ktlintCheck` stops at the first failing project and hides violations in the others] → Document `./gradlew ktlintCheck --continue` for a full listing in `AGENTS.md`.
- [The reformat touches almost every file and conflicts with open branches] → Keep it a separate, formatting-only commit, so other branches can rebase by re-running `ktlintFormat`.
- [IntelliJ's Reformat Code and ktlint still disagree on a few constructs] → Accepted. `ktlintFormat` is the reference. The `ij_kotlin_*` keys only reduce the differences.
- [Nothing enforces formatting, so violations come back] → Accepted per D4. Enforcement is a later decision.
- [Configuration cache incompatibility of the plugin or the included-build dependency] → The setup task runs both commands twice with the configuration cache on and checks that the second run reuses it.

## Migration Plan

Developer tooling only, nothing to deploy. Rollback: revert the setup commit and the reformat commit.

Dependency note: be-detekt-konsist adds a `:tooling` module. Whichever of the two changes lands second makes `:tooling` apply `cardigan.ktlint` and runs `ktlintFormat` on it. The two changes don't otherwise depend on each other.
