# Decisions: be-ktlint

Confirmed with Philipp Lackner on 2026-11-26, via the orchestrator session cardigan-ae, together with mobile-ktlint.

**Workflow**: plain (`careful-planning`). Build config and tooling only, nothing a new test would observe.

## Summary

The backend gets ktlint as an on-demand linter and formatter through the ktlint-gradle plugin, with `./gradlew ktlintCheck` and `./gradlew ktlintFormat` as the commands. Rule settings live in a new root `.editorconfig` (`ktlint_official`, 120 columns) that matches the mobile repo. Nothing runs ktlint automatically. The existing code is reformatted once to zero violations in this change.

## Decisions

### D1 — Workflow
- **Question**: Test-first or plain workflow?
- **Decision**: Plain (`careful-planning`).
- **Alternatives**: Test-first (`careful-planning-tdd`).
- **Why**: Build and tooling only; no app behavior a new test could observe.
- **Depends on**: —

### D2 — Gradle integration
- **Question**: Which Gradle integration runs ktlint?
- **Decision**: ktlint-gradle (`org.jlleitschuh.gradle.ktlint`). Plugin and ktlint engine versions are pinned in `gradle/libs.versions.toml`, the same versions as in the mobile repo.
- **Alternatives**: Kotlinter; Spotless with ktlint.
- **Why**: Widely used, KMP support for mobile, identical setup in both repos.
- **Depends on**: —

### D3 — Command names
- **Question**: Which command names check and format the code?
- **Decision**: The plugin's own task names, `./gradlew ktlintCheck` and `./gradlew ktlintFormat`. No custom aliases. The user: "Just add the capability for us to call these CLI commands."
- **Alternatives**: Own neutral root aggregates (e.g. `lintCheck` / `format`).
- **Why**: Same names in both repos and in the plugin docs.
- **Depends on**: D2

### D4 — On demand only
- **Question**: Should ktlint violations fail `./gradlew build` and CI?
- **Decision**: No. ktlint runs only when called. It is not wired into `check`/`build`, CI, the commit gates or git hooks, and nothing formats automatically. This applies to every static analysis tool in every repo.
- **Alternatives**: Part of `check`, so build and CI fail on violations.
- **Why**: The user will add enforcement later.
- **Depends on**: —

### D5 — Code style
- **Question**: Which ktlint code style?
- **Decision**: `ktlint_official`.
- **Alternatives**: `intellij_idea`; `android_studio`.
- **Depends on**: —

### D6 — Line length
- **Question**: Maximum line length?
- **Decision**: 120.
- **Alternatives**: 140; no limit.
- **Depends on**: D5

### D7 — Rule settings file
- **Question**: Where do the rule settings live, and what do they cover?
- **Decision**: A new root `.editorconfig` with only a `[*.{kt,kts}]` section: `ktlint_code_style = ktlint_official`, `max_line_length = 120`, plus the `ij_kotlin_*` keys so IntelliJ's Reformat Code produces `ktlint_official` output. No `[*]` section. Identical to the mobile repo except mobile's `ktlint_function_naming_ignore_when_annotated_with = Composable`. Rule settings only, no custom rules.
- **Alternatives**: Also a general `[*]` section (charset, line endings, final newline) for all files.
- **Why**: No rule settings existed in either repo; the user decided them now. Keep the file from affecting non-Kotlin files.
- **Depends on**: D5, D6

### D8 — File scope
- **Question**: Which files does ktlint check and format?
- **Decision**: Main sources, test and testFixtures sources, all `*.gradle.kts` files (root and modules, including `settings.gradle.kts`), and build-logic.
- **Alternatives**: Any subset of these.
- **Depends on**: —

### D9 — Module setup
- **Question**: How do modules get ktlint?
- **Decision**: A new opt-in convention plugin `cardigan.ktlint` in build-logic, like `cardigan.kover` and `cardigan.pitest`. `:app`, `:common`, `:user`, `:board` and the root project apply it in this change. New modules apply it when they are created.
- **Alternatives**: The root build applies it to every subproject.
- **Why**: Matches the existing opt-in pattern; same in both repos and for detekt.
- **Depends on**: D2

### D10 — build-logic
- **Question**: How is build-logic itself checked?
- **Decision**: build-logic applies ktlint-gradle directly in its own build file (it can't use its own convention plugin). The root `ktlintCheck` and `ktlintFormat` depend on build-logic's matching tasks, so the two commands cover build-logic. No alias task names.
- **Alternatives**: —
- **Depends on**: D8, D9

### D11 — Git hook
- **Question**: Should a pre-commit hook run ktlint?
- **Decision**: No hook.
- **Alternatives**: Check hook; format hook.
- **Depends on**: D4

### D12 — Reports
- **Question**: Which output besides the console?
- **Decision**: Console plus SARIF and Checkstyle report files under `build/reports/ktlint/`. Uploading them in CI is out of scope.
- **Alternatives**: Console only; SARIF only; Checkstyle only.
- **Depends on**: D2

### D13 — Existing code
- **Question**: How are existing violations handled?
- **Decision**: One-time reformat in this change to zero `ktlintCheck` violations: `ktlintFormat`, then hand fixes for what it can't fix. Its own commit, after the setup commit. No baseline file.
- **Alternatives**: Baseline file; auto-fix only and leave the rest.
- **Depends on**: D5, D6, D7, D8

### D14 — Violations that can't be fixed safely
- **Question**: What if a fix would change behavior or a client-visible name (API names, JSON fields, enum constants, DB columns)?
- **Decision**: `@Suppress("ktlint:standard:<rule>")` on that declaration; the rule stays on everywhere else. If a rule clashes with a project convention, the implementer stops and asks the user whether to disable it.
- **Alternatives**: Turn the rule off in `.editorconfig`; ask per case.
- **Depends on**: D13

### D15 — Documentation
- **Question**: Which docs change?
- **Decision**: `AGENTS.md` (`CLAUDE.md` is a symlink) lists `ktlintCheck` and `ktlintFormat` in the Commands table and gets a "Where does X go?" row for adding a new module to ktlint (apply `cardigan.ktlint`).
- **Alternatives**: —
- **Depends on**: D3, D9

## Facts

- No `.editorconfig` or ktlint config existed in the backend repo or its git history — source: repo search, `git log --all -- .editorconfig`
- The mobile repo has no `.editorconfig` either — source: `~/AndroidStudioProjects/CardiganApp`
- Both repos set only `kotlin.code.style=official` — source: `gradle.properties`
- 271 Kotlin/kts files; 136 lines over 120 chars, 4 over 140, longest 164; no wildcard imports — source: awk/grep over `app common user board build-logic`
- 1,100 backticked test names, none over 120 chars — source: grep over test sources
- Existing tooling is opt-in per module through convention plugins (`cardigan.kover`, `cardigan.pitest`) with root aggregate tasks — source: `build-logic/convention/build.gradle.kts`, module `build.gradle.kts` files
- CI runs only `./gradlew build` — source: `.github/workflows/ci.yml`
- build-logic is an included build with its own `settings.gradle.kts` that reads the version catalog — source: `build-logic/`

## Out of scope

- detekt and Konsist (separate change be-detekt-konsist)
- Custom ktlint rules or advanced rule work
- Enforcement in `check`/`build`, CI or commit gates; git hooks
- CI upload of SARIF reports
- A `staticAnalysis` umbrella task that includes ktlint
- Formatting or checking non-Kotlin files

## Deferred

- `:tooling` module from be-detekt-konsist applies `cardigan.ktlint`: whichever of the two changes lands second does it — settle before: implementation
