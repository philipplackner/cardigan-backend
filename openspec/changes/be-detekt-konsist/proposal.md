# Proposal

## Why

The backend has no static analysis and no automated check of its architecture rules (layering, "fakes, not mocks"). This change adds the tooling for both, detekt and Konsist, as on-demand commands, with the same setup and commands as the mobile repo, so rules can be added later without more build work.

## What Changes

- Add detekt 2.0.0-alpha.6 with type resolution and Konsist 1.0.0-beta.1 to the version catalog.
- New opt-in convention plugin `cardigan.detekt`, applied by `:app`, `:common`, `:user`, `:board` and `:tooling`. build-logic gets detekt in its own build file with the same version and config, default rules only.
- One shared config `config/detekt/detekt.yml`: detekt's default rules, the rules that duplicate ktlint turned off, and the custom rule. No baseline, and existing findings are not fixed.
- New commands, all on demand: `./gradlew detekt`, `./gradlew :<module>:detekt`, `./gradlew konsist`, `./gradlew staticAnalysis`, `./gradlew :tooling:test`. detekt is taken out of `check`. Nothing new runs in `./gradlew build`, CI or hooks.
- Reports: HTML and SARIF per module, plus one merged SARIF report under `build/reports/detekt/`.
- New module `:tooling` (plain Kotlin/JVM, not deployed, no project dependencies, not in coverage or mutation reports) with:
  - the sample custom detekt rule `cardigan:NoMockingLibraries`, which flags imports from Mockito and MockK in test sources,
  - the sample Konsist check: no `domain` package imports Spring or `jakarta.persistence`.
- Document the commands in AGENTS.md (CLAUDE.md is a symlink to it).
- No product behavior changes. Nothing is breaking.

## Capabilities

### New Capabilities
- `static-analysis`: the on-demand detekt and Konsist commands, what they analyze, when they fail, their reports, and the sample detekt rule and Konsist check.

### Modified Capabilities
- `module-boundaries`: the fixed module list gains the non-deployed `:tooling` module, which depends on no other project module.

### Shared-spec requirements

None. This change is backend build tooling; it implements and depends on no requirement in cardigan-shared-specs.

## Impact

- Build: `gradle/libs.versions.toml`, `settings.gradle.kts`, root `build.gradle.kts`, `build-logic` (new convention plugin, own detekt setup), new `config/detekt/detekt.yml`, new `tooling/` module.
- Module build files of `:app`, `:common`, `:user`, `:board` apply `cardigan.detekt`.
- New dependencies: detekt Gradle plugin, detekt-api, detekt-test and detekt-core (for the rule and its tests), Konsist. All test or build-time only; nothing reaches the runtime artifact.
- Docs: AGENTS.md (CLAUDE.md is a symlink to it).
- CI: unchanged; `./gradlew build` runs nothing new.
- Related change: be-ktlint is independent and can land in either order. Whichever lands second applies `cardigan.ktlint` to `:tooling`.
- Cross-repo: mobile-detekt-konsist makes the same decisions and uses the same commands in the mobile repo.
