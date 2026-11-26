# Decisions: be-detekt-konsist

Confirmed with Philipp Lackner on 2026-11-26 (through the orchestrator session cardigan-ae, together with mobile-detekt-konsist).

**Workflow**: Test-first (careful-planning-tdd) — only the sample rule has behavior a test can observe. Test-first for the sample custom detekt rule `NoMockingLibraries`; not for the build setup, Gradle tasks, config and docs, which are `[plain]` tasks.

## Summary

Adds detekt and Konsist to the backend as on-demand tools: `./gradlew detekt`, `./gradlew konsist` and `./gradlew staticAnalysis`. Nothing runs in `build`, CI or hooks. A new `:tooling` module holds one sample custom detekt rule (`cardigan:NoMockingLibraries`) and one sample Konsist layer check. No further rules are added and existing findings are not fixed. Decisions, command structure and build setup are identical to the mobile change mobile-detekt-konsist.

## Decisions

### D1 — Cross-repo parity
- **Question**: Must decisions, commands and build setup match mobile?
- **Decision**: Yes. This was a user requirement from the start. Every decision below except D16 was decided once for backend and mobile.
- **Alternatives**: —
- **Why**: User requirement.
- **Depends on**: —

### D2 — Workflow
- **Question**: Test-first or plain?
- **Decision**: Test-first only for the sample detekt rule; everything else is `[plain]`.
- **Alternatives**: Test-first for all, Plain.
- **Why**: Only the rule has observable behavior.
- **Depends on**: —

### D3 — detekt version
- **Question**: Which detekt line?
- **Decision**: 2.0.0-alpha.6, plugin id `dev.detekt`, with type resolution.
- **Alternatives**: 1.23.8 stable; without type resolution.
- **Why**: Matches Kotlin 2.3 / JDK 25; the rule API won't change again at 2.0 final.
- **Depends on**: —

### D4 — Konsist version
- **Question**: Which Konsist version?
- **Decision**: 1.0.0-beta.1.
- **Alternatives**: 0.18.1 stable.
- **Why**: Avoids a 1.0 migration later.
- **Depends on**: —

### D5 — Relation to ktlint
- **Question**: How does detekt relate to the be-ktlint change?
- **Decision**: ktlint owns formatting. No detekt-formatting plugin; detekt rules that duplicate ktlint are turned off. The two changes are independent and can land in any order. Whichever lands second applies `cardigan.ktlint` to `:tooling`. `staticAnalysis` does not run ktlint.
- **Alternatives**: detekt-formatting too; detekt depends on be-ktlint.
- **Why**: No duplicate findings, one place to configure formatting.
- **Depends on**: —

### D6 — Rule set and existing findings
- **Question**: Which rules are active and what happens to existing findings?
- **Decision**: detekt's default rules. `config/detekt/detekt.yml` holds only the needed overrides (ktlint overlaps off, the custom rule). Existing findings are not fixed and there is no baseline file.
- **Alternatives**: Defaults + baseline; defaults and fix all; warn only.
- **Why**: The user only wants the tooling; it is on demand, so it can't break a build or CI.
- **Depends on**: D7

### D7 — When the tools run
- **Question**: Do detekt and Konsist run in `build` and CI?
- **Decision**: On demand only. Not in `build`, `check`, CI, commit gates or hooks. An explicit detekt run fails on any finding.
- **Alternatives**: In build and failing; in build but never failing.
- **Why**: Decided by the user for every tool in every repo.
- **Depends on**: —

### D8 — Commands
- **Question**: Which Gradle commands exist?
- **Decision**: `./gradlew detekt` runs detekt in every opted-in module and in build-logic. `./gradlew :<module>:detekt` runs one module; there is no modules property. `./gradlew konsist` runs only the Konsist checks. `./gradlew staticAnalysis` runs both. `./gradlew :tooling:test` runs the rule tests.
- **Alternatives**: Plugin defaults only; report-style task with a modules property.
- **Why**: Same commands in both repos.
- **Depends on**: D7

### D9 — staticAnalysis failure mode
- **Question**: When detekt fails during `staticAnalysis`, does Konsist still run?
- **Decision**: Both always run; the command fails at the end if either found something.
- **Alternatives**: Stop at the first failure.
- **Why**: One run shows every finding.
- **Depends on**: D8

### D10 — What detekt analyzes
- **Question**: Which sources does detekt analyze?
- **Decision**: Main and test source sets of every opted-in module, plus `:tooling` and build-logic, with type resolution.
- **Alternatives**: Main only.
- **Why**: Whole codebase checked; the sample rule only matters in tests.
- **Depends on**: D3

### D11 — How modules get detekt
- **Question**: Opt-in or automatic?
- **Decision**: Opt-in convention plugin `cardigan.detekt`, like `cardigan.kover` and `cardigan.pitest`. Applied by `:app`, `:common`, `:user`, `:board` and `:tooling`. build-logic applies `dev.detekt` directly in its own build file with the same version and the same `config/detekt/detekt.yml`, default rules only and without the custom rule. The root `detekt` task also runs build-logic's detekt.
- **Alternatives**: Applied automatically through `cardigan.kotlin.jvm`.
- **Why**: Same pattern as kover/pitest; decided for ktlint and detekt in both repos.
- **Depends on**: D10

### D12 — Config location
- **Question**: Where does the detekt config live?
- **Decision**: `config/detekt/detekt.yml` at the repo root.
- **Alternatives**: Inside `:tooling`; per module.
- **Why**: One shared file for all modules and build-logic.
- **Depends on**: D6

### D13 — Reports
- **Question**: Which report formats?
- **Decision**: HTML and SARIF per module. The SARIF reports are also merged into `build/reports/detekt/`, the same as mobile.
- **Alternatives**: Other detekt formats.
- **Why**: Decided for both repos; the merge keeps backend identical to mobile (D1).
- **Depends on**: D1

### D14 — Tooling module
- **Question**: What is the separate module for tooling and custom rules?
- **Decision**: One module `:tooling`: plain Kotlin/JVM, not part of the deployable, no project dependencies. Holds the custom detekt rules and, in `src/test`, the rule tests and the Konsist tests. Its tests are excluded from `./gradlew build` and CI. `./gradlew :tooling:test` runs the rule tests; `./gradlew konsist` runs only the Konsist checks.
- **Alternatives**: `:tooling:detekt-rules`; `:detekt-rules`; Konsist in its own module or in `:app`; rule tests in build.
- **Why**: One place for all tooling; nothing in build gates (D7).
- **Depends on**: D7, D8

### D15 — Sample custom detekt rule
- **Question**: What does the sample rule check?
- **Decision**: Rule set `cardigan`, rule `NoMockingLibraries`. Flags every import from `org.mockito.*` or `io.mockk.*`, including star and aliased imports. Imports only, no fully qualified references. Active for test source sets only.
- **Alternatives**: No println; also flag qualified references; rule set `tooling`; active on all sources.
- **Why**: Enforces the existing "fakes, not mocks" rule in both repos.
- **Depends on**: D10

### D16 — Sample Konsist check
- **Question**: Which sample Konsist check?
- **Decision**: One layer check: no `domain` package in `:common`, `:user` or `:board` imports `org.springframework.*` or `jakarta.persistence.*`. Mobile uses its own layer equivalent.
- **Alternatives**: A naming check; no sample check.
- **Why**: Enforces an existing CLAUDE.md layer rule.
- **Depends on**: D14

### D17 — Coverage and mutation
- **Question**: Does `:tooling` take part in `coverageReport` and `mutationReport`?
- **Decision**: No.
- **Alternatives**: Yes.
- **Why**: The reports stay about product code.
- **Depends on**: D14

### D18 — Docs
- **Question**: Where are the new commands documented?
- **Decision**: In AGENTS.md (CLAUDE.md is a symlink to it), next to the coverage and mutation commands.
- **Alternatives**: —
- **Why**: Existing pattern for on-demand tools.
- **Depends on**: D8

### D19 — Engine test dependency
- **Question**: May `:tooling` get detekt's engine as a test-only dependency?
- **Decision**: Yes: `dev.detekt:detekt-core` 2.0.0-alpha.6 as `testImplementation`. This is the dependency approval. The rule's acceptance tests run real detekt in-process with `config/detekt/detekt.yml`.
- **Alternatives**: No engine; manual checks.
- **Why**: Lets the tests cover the test-sources-only scope and suppression automatically.
- **Depends on**: D15

### D20 — Who writes the Konsist check
- **Question**: Who writes `DomainLayerTest` in `tooling/src/test`?
- **Decision**: The test-writer agent writes it as a `[plain]` task, from the design and the two Konsist scenarios. The test path guard stays unchanged.
- **Alternatives**: The implementer, with a guard exception.
- **Why**: Keeps the path guard as it is.
- **Depends on**: D2, D16

### D21 — Letter case
- **Question**: Does `NoMockingLibraries` match package names case-sensitively?
- **Decision**: Yes. `import Org.Mockito.Mockito` gets no finding.
- **Alternatives**: Case-insensitive.
- **Why**: Kotlin packages are case-sensitive.
- **Depends on**: D15

### D22 — Default activation
- **Question**: Is the rule active when a config doesn't mention it?
- **Decision**: No. It is off unless configured; the shared config's `active: true` turns it on.
- **Alternatives**: On by default.
- **Why**: detekt's usual behavior for custom rule sets.
- **Depends on**: D15

### D23 — Finding message
- **Question**: What does a finding say?
- **Decision**: Exactly `Import of '<path>' is not allowed. Use fakes instead of mocking libraries.` This is shared with mobile, and tests assert the full text. `<path>` is the imported path as written; for a star import that includes `.*`, e.g. `io.mockk.*`.
- **Alternatives**: —
- **Why**: Same text in both repos.
- **Depends on**: D1, D15

## Facts

- Backend uses Kotlin 2.3.21, JDK 25, Gradle 9, configuration cache on — source: `gradle/libs.versions.toml`, `gradle.properties`
- Convention plugins live in `build-logic/convention`; build-logic is an included build — source: `settings.gradle.kts`, `build-logic/`
- detekt 1.23.8 is the last stable release (id `io.gitlab.arturbosch.detekt`); 2.0.0-alpha.6 is the newest (id `dev.detekt`) — source: Maven Central metadata
- Konsist releases: 0.18.1 stable, 1.0.0-beta.1 newest (Nov 2026) — source: Maven Central metadata
- The detekt plugin hooks its tasks into `check` by default — source: detekt Gradle docs
- CI runs only `./gradlew build` — source: `.github/workflows/ci.yml`
- The `module-boundaries` spec requires exactly `:app`, `:common`, `:user`, `:board`, so `:tooling` modifies it — source: `openspec/specs/module-boundaries/spec.md`
- CLAUDE.md is a symlink to AGENTS.md, which lists the on-demand coverage and mutation commands — source: `ls -l CLAUDE.md`, `.claude/tdd/project.md`

## Out of scope

- Further detekt or Konsist rules
- Fixing existing detekt findings, and a baseline file
- Running anything in build, CI, commit gates or hooks
- Running ktlint from `staticAnalysis`
- detekt-formatting
- Product behavior; no shared-spec requirement is touched

## Deferred

- Applying `cardigan.ktlint` to `:tooling` — settle before: implementation of whichever of be-ktlint and be-detekt-konsist lands second
