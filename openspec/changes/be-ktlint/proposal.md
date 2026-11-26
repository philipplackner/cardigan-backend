# Proposal

## Why

The backend has no linter or formatter and no written formatting rules, so style depends on each editor's settings. ktlint gives backend and mobile one shared set of formatting rules and two commands to check and fix code. Enforcement comes later, once the user decides to add it.

## What Changes

- Add the ktlint-gradle plugin and pin the plugin and ktlint versions in `gradle/libs.versions.toml`, the same versions as mobile.
- Add an opt-in convention plugin `cardigan.ktlint`, applied by `:app`, `:common`, `:user`, `:board` and the root project. build-logic applies ktlint-gradle directly.
- `./gradlew ktlintCheck` reports violations in all Kotlin sources (main, test, testFixtures), all `*.gradle.kts` files and build-logic, with SARIF and Checkstyle report files. `./gradlew ktlintFormat` fixes what ktlint can fix in the same files.
- Add a root `.editorconfig` with a Kotlin-only section: `ktlint_official` style, 120 columns, and IntelliJ formatter keys that match.
- Reformat the existing code once to zero violations, in its own commit.
- ktlint stays on demand: not part of `build`, `check`, CI or any git hook.
- Document the commands and the opt-in step in `AGENTS.md`.

No API, schema or runtime behavior changes. Not breaking.

## Capabilities

### New Capabilities
- `code-formatting`: the on-demand Kotlin lint and format commands, the files they cover, the shared rule settings, the report files, and how a module opts in.

### Modified Capabilities
None. `delivery-pipeline` is unchanged, because CI and `./gradlew build` don't run ktlint.

## Shared specs

This change implements and depends on no requirement of `cardigan-shared-specs`. The rule settings match the mobile change mobile-ktlint by agreement, not through a shared spec.

## Impact

- Build: `gradle/libs.versions.toml`, `build-logic` (new convention plugin and ktlint for build-logic itself), root and module `build.gradle.kts` files.
- New file: `.editorconfig`.
- Code: a formatting-only pass over all Kotlin and `.kts` files. Behavior, API contract (`openapi.json`) and database schema stay the same. `@Suppress("ktlint:standard:<rule>")` where a fix would change a client-visible name.
- Docs: `AGENTS.md`.
- Related: be-detekt-konsist adds a `:tooling` module. Whichever change lands second makes `:tooling` apply `cardigan.ktlint`.
