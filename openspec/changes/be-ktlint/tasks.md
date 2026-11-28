# Tasks

## 1. Versions and rule settings

- [x] 1.1 Look up the versions mobile-ktlint pinned (or, if it isn't implemented yet, the latest stable ktlint-gradle and ktlint releases). Add the `ktlint-gradle` and `ktlint` versions, the `ktlint-gradlePlugin` library and the `ktlint` plugin alias to `gradle/libs.versions.toml`. Verify that both versions equal mobile's, or note them for mobile-ktlint to copy.
- [x] 1.2 Add the root `.editorconfig` from design.md (`root = true`, one `[*.{kt,kts}]` section). Compare it with the mobile repo's file if it exists. Verify that the only difference is mobile's Composable key.

## 2. Gradle setup

- [x] 2.1 Add `KtlintConventionPlugin` in `build-logic/convention` (apply the plugin, catalog engine version, console output, SARIF + Checkstyle reporters, exclude `build/`, remove `ktlintCheck` from `check`, wire the root tasks to build-logic's tasks). Register it as `cardigan.ktlint`, add `compileOnly(libs.ktlint.gradlePlugin)` to build-logic and `convention-ktlint` to the catalog. Verify with `./gradlew -p build-logic build`.
- [x] 2.2 Apply ktlint-gradle directly in `build-logic/convention/build.gradle.kts` with the same settings, plus `kotlinScriptAdditionalPaths` for `build-logic/settings.gradle.kts`. Verify with `./gradlew -p build-logic ktlintCheck --dry-run` that it lists the main and script check tasks.
- [x] 2.3 Add `alias(libs.plugins.ktlint) apply false` and `alias(libs.plugins.convention.ktlint)` to the root `build.gradle.kts`, and `alias(libs.plugins.convention.ktlint)` to `:app`, `:common`, `:user` and `:board`. First verify that the root project gets ktlint-gradle's script tasks without a Kotlin plugin; if not, use the fallback in design.md > Root project and build scripts. If `gradle.includedBuild("build-logic")` can't find build-logic, add the top-level `includeBuild` from design.md step 5. Verify with `./gradlew ktlintCheck --dry-run` that it lists the main, test and (for `:common` and `:user`) testFixtures check tasks of every module, the script check tasks of the root and every module, and `:build-logic:convention:ktlintCheck`. Verify the same for `ktlintFormat`.
- [x] 2.4 Verify the on-demand rule. `./gradlew check --dry-run` and `./gradlew build --dry-run` list no `ktlint` task. Also verify this with a temporary reorder in `:board` that lists `cardigan.ktlint` before its Kotlin convention plugin, then revert the reorder. No git hook is installed (`ls .git/hooks` shows no ktlint hook).
- [x] 2.5 Verify the commands behave as the spec says. Run `./gradlew ktlintCheck --continue`: it fails on the current code, prints file:line:column, rule and message, leaves `git status` clean, and writes `.sarif` and Checkstyle `.xml` files under `build/reports/ktlint/` of each project and of `build-logic/convention`. Run it a second time and check the output says the configuration cache was reused.
- [x] 2.6 Update `AGENTS.md`: Tech Stack row, `./gradlew ktlintCheck [--continue]` and `./gradlew ktlintFormat` in the Commands table, the "A new module in ktlint" row in "Where does X go?", and a note that nothing runs ktlint automatically. Verify that the commands work exactly as written.
- [x] 2.7 Run `./gradlew build` and `./gradlew -p build-logic test`. Both must pass. Then commit the setup on its own (`build: add ktlint with on-demand check and format tasks`).

## 3. One-time reformat

- [x] 3.1 Run `./gradlew ktlintFormat`. Verify that `git diff` only contains formatting changes.
- [x] 3.2 Fix the remaining `./gradlew ktlintCheck --continue` violations by hand without changing behavior. Where a fix would change an API path, JSON name, enum constant or database name, add `@Suppress("ktlint:standard:<rule>")` on the narrowest declaration. If a rule clashes with a project convention (for example backticked test names or the `PatchRequest` property pattern), stop and ask the user whether to disable it in `.editorconfig`. Verify that `./gradlew ktlintCheck` passes.
- [x] 3.3 Verify that `./gradlew ktlintFormat` now changes no file (`git status` clean after a second run on the formatted tree).
- [x] 3.4 Run `./gradlew build` and `./gradlew -p build-logic test`. Both must pass, and `git diff` must show `app/src/test/resources/openapi/openapi.json` unchanged. Then commit the reformat on its own (`style: reformat code with ktlint`).

## 4. Integration

- [x] 4.1 Run `openspec validate be-ktlint --strict`, `./gradlew ktlintCheck` and `./gradlew build`. Verify that all three pass.

## Workflow follow-up

- If be-detekt-konsist has landed first, `:tooling` applies `cardigan.ktlint` and is formatted as part of group 3. If this change lands first, be-detekt-konsist does it.
- Archive the change only when the user says so.
