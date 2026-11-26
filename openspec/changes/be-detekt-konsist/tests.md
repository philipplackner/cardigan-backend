# Test plan: be-detekt-konsist

<!-- Written by the tdd-test-designer agent from the change's planning
     artifacts only. Every test states where its expected result comes from.
     A test that asserts something no artifact states is a bug in this plan. -->

Scope: the `Workflow` line in decisions.md limits test-first to the sample
custom detekt rule `cardigan:NoMockingLibraries` (D2, D15, design decisions 3
and 8). Every other scenario is part of the `[plain]` build, task, config and
docs work. Those rows are marked "not test-first (Workflow line)" and have no
`A` or `C` tests.

All tests below live in `tooling/src/test/kotlin/com/plcoding/cardigan/tooling/detekt/`
(never in the `...tooling.konsist` package, which `:tooling:test` excludes,
design decision 7). They run with `./gradlew :tooling:test`. JUnit 5 and
AssertK, no mocks.

## Coverage

| Spec > Requirement > Scenario | Tests |
|---|---|
| `module-boundaries` > The backend has a fixed set of modules > Listing the modules | not test-first (Workflow line) |
| `module-boundaries` > The backend has a fixed set of modules > One deployable | not test-first (Workflow line) |
| `module-boundaries` > Business modules are independent > Business module dependencies | not test-first (Workflow line) |
| `module-boundaries` > Business modules are independent > Common dependencies | not test-first (Workflow line) |
| `module-boundaries` > Business modules are independent > App dependencies | not test-first (Workflow line) |
| `module-boundaries` > Business modules are independent > Tooling dependencies | not test-first (Workflow line) |
| `static-analysis` > Modules opt in to detekt with one plugin > Opted-in modules | not test-first (Workflow line) |
| `static-analysis` > Modules opt in to detekt with one plugin > A new module opts in | not test-first (Workflow line) |
| `static-analysis` > detekt analyzes main and test sources with type resolution > Finding in test code | not test-first (Workflow line) |
| `static-analysis` > detekt analyzes main and test sources with type resolution > Type-resolution rule | not test-first (Workflow line) |
| `static-analysis` > detekt uses its default rules with one shared config > Formatting is left to ktlint | not test-first (Workflow line) |
| `static-analysis` > detekt uses its default rules with one shared config > No baseline | not test-first (Workflow line) |
| `static-analysis` > detekt fails on any finding > One finding | not test-first (Workflow line) |
| `static-analysis` > detekt fails on any finding > Clean code | not test-first (Workflow line) |
| `static-analysis` > detekt can run for one module > One module | not test-first (Workflow line) |
| `static-analysis` > detekt writes HTML and SARIF reports > Reports after a run | not test-first (Workflow line) |
| `static-analysis` > detekt writes HTML and SARIF reports > Reports after a failed run | not test-first (Workflow line) |
| `static-analysis` > Mocking libraries are flagged in test code > Mockito import in a test | A01 |
| `static-analysis` > Mocking libraries are flagged in test code > Star and aliased MockK imports | A02 |
| `static-analysis` > Mocking libraries are flagged in test code > Different letter case | A08 |
| `static-analysis` > Mocking libraries are flagged in test code > Similar package names | A03 |
| `static-analysis` > Mocking libraries are flagged in test code > Main source set | A04, A05 |
| `static-analysis` > Mocking libraries are flagged in test code > Qualified reference without import | A06 |
| `static-analysis` > Mocking libraries are flagged in test code > Suppressed | A07 |
| `static-analysis` > Mocking libraries are flagged in test code (requirement: every import from the packages and their subpackages, rule set `cardigan`) | C01, C02, C03, C04, C05, C06, C07, C08, C10, C11, C12, C13 |
| `static-analysis` > Mocking-library findings have a fixed message > Plain import | A09 |
| `static-analysis` > Mocking-library findings have a fixed message > Star import | A10 |
| `static-analysis` > Mocking-library findings have a fixed message (requirement: path without the alias) | C09 |
| `static-analysis` > The mocking rule is off unless configured > Not configured | A11 |
| `static-analysis` > The mocking rule is off unless configured > Shared config | A12 |
| `static-analysis` > Domain packages are checked for framework imports > Clean domain packages | not test-first (Workflow line) |
| `static-analysis` > Domain packages are checked for framework imports > Spring import in a domain package | not test-first (Workflow line) |
| `static-analysis` > One command runs all static analysis > detekt fails | not test-first (Workflow line) |
| `static-analysis` > One command runs all static analysis > Both clean | not test-first (Workflow line) |
| `static-analysis` > Custom rule tests run on demand > Rule tests | not test-first (Workflow line) |
| `static-analysis` > Static analysis runs only on demand > Build without static analysis | not test-first (Workflow line) |
| `static-analysis` > Static analysis runs only on demand > CI unchanged | not test-first (Workflow line) |
| `static-analysis` > The tooling module stays out of coverage and mutation reports > Reports without tooling | not test-first (Workflow line) |

## Acceptance tests

<!-- `A` IDs. Black-box tests through the public interface only, at the
     acceptance level .claude/tdd/project.md names. Written before any
     implementation exists, so they may use nothing but that interface and
     the existing test helpers. -->

The acceptance level for `:tooling` is set in design.md > Decision 8 ("Test
levels for `:tooling`"): detekt's engine (`detekt-core`, D19) run in-process
from a `:tooling` unit test. Unless a test says otherwise, the run uses the
repo's shared config `config/detekt/detekt.yml` (read from
`../config/detekt/detekt.yml`, the test working directory being `tooling/`)
on top of detekt's defaults, and loads the rule set only through detekt's
plugin mechanism (the `META-INF/services` registration on the test
classpath), never by referencing `NoMockingLibraries` or
`CardiganRuleSetProvider` directly. Input files are written to a JUnit
`@TempDir` under a module-like path such as
`<tmp>/sample/src/test/kotlin/Sample.kt` or
`<tmp>/sample/src/main/kotlin/Sample.kt`. Only findings of rule set
`cardigan`, rule `NoMockingLibraries` are counted, so default-rule findings
on the samples do not matter.

### A01: a Mockito import in a test file is reported by cardigan:NoMockingLibraries
- Source: specs/static-analysis/spec.md > Mocking libraries are flagged in test code > Mockito import in a test
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: `<tmp>/sample/src/test/kotlin/SampleTest.kt` with `package sample`, `import org.mockito.Mockito` and a class that does not use it.
- Act: run detekt on `<tmp>`.
- Expect: exactly one finding of rule set `cardigan`, rule `NoMockingLibraries`, in `SampleTest.kt` on the import's line.
- Must fail when: `META-INF/services/dev.detekt.api.RuleSetProvider` is missing or names the wrong class, or the rule set id is not `cardigan`; the engine never loads the rule and the test sees zero findings.

### A02: a star import and an aliased import from MockK are each reported once
- Source: specs/static-analysis/spec.md > Mocking libraries are flagged in test code > Star and aliased MockK imports
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: `<tmp>/sample/src/test/kotlin/SampleTest.kt` with `import io.mockk.*` on one line and `import io.mockk.mockk as m` on the next.
- Act: run detekt on `<tmp>`.
- Expect: exactly two `cardigan:NoMockingLibraries` findings, one on each import's line.
- Must fail when: the rule matches only `org.mockito`, skips star imports (checks only a non-null imported name), or checks the alias name `m` instead of the imported path; the test sees fewer than two findings or a missing line.

### A03: packages that only share the leading letters are not reported
- Source: specs/static-analysis/spec.md > Mocking libraries are flagged in test code > Similar package names
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: `<tmp>/sample/src/test/kotlin/SampleTest.kt` with `import org.mockitox.Foo` and `import io.mockkx.Bar`.
- Act: run detekt on `<tmp>`.
- Expect: no `cardigan:NoMockingLibraries` finding.
- Must fail when: the rule uses `startsWith("org.mockito")` / `startsWith("io.mockk")` without the dot boundary; the test sees two findings.

### A04: a MockK import in a main source file is not reported
- Source: specs/static-analysis/spec.md > Mocking libraries are flagged in test code > Main source set; design.md > Decision 3 (`includes`)
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: `<tmp>/sample/src/main/kotlin/Sample.kt` with `import io.mockk.mockk`, and `<tmp>/sample/src/test/kotlin/SampleTest.kt` with the same import (control).
- Act: run detekt on `<tmp>`.
- Expect: exactly one `cardigan:NoMockingLibraries` finding, in `SampleTest.kt`; none in `Sample.kt`.
- Must fail when: the config has no `includes` for the rule (or an include such as `**/src/**`), so the main file is reported too; the test sees two findings.

### A05: a MockK import in a testFixtures source file is reported
- Source: design.md > Decision 3 (`includes: ['**/src/test/**', '**/src/testFixtures/**']`, "testFixtures counts as test code")
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: `<tmp>/sample/src/testFixtures/kotlin/Fixture.kt` with `import io.mockk.mockk`.
- Act: run detekt on `<tmp>`.
- Expect: exactly one `cardigan:NoMockingLibraries` finding, in `Fixture.kt`.
- Must fail when: `includes` lists only `**/src/test/**`; the test sees no finding.

### A06: a fully qualified MockK call without an import is not reported
- Source: specs/static-analysis/spec.md > Mocking libraries are flagged in test code > Qualified reference without import
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: `<tmp>/sample/src/test/kotlin/SampleTest.kt` with no imports and a function body `val foo = io.mockk.mockk<Foo>()`.
- Act: run detekt on `<tmp>`.
- Expect: no `cardigan:NoMockingLibraries` finding.
- Must fail when: the rule also visits dot-qualified expressions or user types; the test sees a finding on the call line.

### A07: @file:Suppress with the rule id suppresses the finding
- Source: specs/static-analysis/spec.md > Mocking libraries are flagged in test code > Suppressed
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: `<tmp>/sample/src/test/kotlin/SampleTest.kt` starting with `@file:Suppress("cardigan:NoMockingLibraries")`, then `package sample` and `import org.mockito.Mockito`.
- Act: run detekt on `<tmp>`.
- Expect: no `cardigan:NoMockingLibraries` finding.
- Must fail when: the rule reports through a path that bypasses detekt's suppression (for example a rule id or rule set id that does not match `cardigan:NoMockingLibraries`, such as a rule named `NoMockingLibrary`); the test sees one finding.

### A08: an import that differs only in letter case is not reported
- Source: specs/static-analysis/spec.md > Mocking libraries are flagged in test code > Different letter case; decisions.md > D21
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: `<tmp>/sample/src/test/kotlin/SampleTest.kt` with `import Org.Mockito.Mockito`.
- Act: run detekt on `<tmp>`.
- Expect: no `cardigan:NoMockingLibraries` finding.
- Must fail when: the rule lowercases the imported path (or uses `startsWith(..., ignoreCase = true)`) before matching; the test sees one finding.

### A09: a plain import's finding has the exact fixed message
- Source: specs/static-analysis/spec.md > Mocking-library findings have a fixed message > Plain import; decisions.md > D23
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: `<tmp>/sample/src/test/kotlin/SampleTest.kt` with `import org.mockito.Mockito`.
- Act: run detekt on `<tmp>`.
- Expect: the single `cardigan:NoMockingLibraries` finding's message equals exactly `Import of 'org.mockito.Mockito' is not allowed. Use fakes instead of mocking libraries.`
- Must fail when: the message differs in any character (no quotes around the path, a missing period, other wording, only the last segment `Mockito`); the equality check fails.

### A10: a star import's finding names the path with `.*`
- Source: specs/static-analysis/spec.md > Mocking-library findings have a fixed message > Star import; decisions.md > D23
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: `<tmp>/sample/src/test/kotlin/SampleTest.kt` with `import io.mockk.*`.
- Act: run detekt on `<tmp>`.
- Expect: the single `cardigan:NoMockingLibraries` finding's message equals exactly `Import of 'io.mockk.*' is not allowed. Use fakes instead of mocking libraries.`
- Must fail when: the message uses the import's FQ name without the star (`'io.mockk'`); the equality check fails.

### A11: without a `cardigan` section in the config the rule reports nothing
- Source: specs/static-analysis/spec.md > The mocking rule is off unless configured > Not configured; decisions.md > D22
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: a config file in `<tmp>` that has no `cardigan` section (for example only `style:` overrides, or empty), used instead of the shared config, with detekt's defaults on and the rule set still on the classpath. `<tmp>/sample/src/test/kotlin/SampleTest.kt` with `import io.mockk.mockk`.
- Act: run detekt on `<tmp>/sample` with that config.
- Expect: no `cardigan:NoMockingLibraries` finding.
- Must fail when: the rule set ships a default config with `active: true` for the rule (or the rule overrides its default activation); the test sees one finding.

### A12: the shared config activates the rule
- Source: specs/static-analysis/spec.md > The mocking rule is off unless configured > Shared config; design.md > Decision 3
- Level: acceptance, detekt engine in `:tooling` (design.md > Decision 8)
- Arrange: the same `SampleTest.kt` as A11, with the shared `config/detekt/detekt.yml`.
- Act: run detekt on `<tmp>/sample`.
- Expect: exactly one `cardigan:NoMockingLibraries` finding, on the import's line.
- Must fail when: `config/detekt/detekt.yml` has no `cardigan.NoMockingLibraries` entry, or has it with `active: false`; given the rule is off by default, the test sees zero findings.

## Component tests

<!-- `C` IDs. Tests against the public surface the design names (types,
     functions, components, repositories, constraints), at the cheapest test
     level from project.md that can observe the behavior. Derive cases from
     the contract, not from an imagined implementation. -->

Level for all `C` tests: unit, in `:tooling`, no Spring, no I/O beyond what
detekt-test does in memory. The rule is driven with detekt-test's `lint` on a
code string (design decision 8: "the rule works with detekt-test's plain
`lint(code)`"), with `NoMockingLibraries` built from detekt-test's test config with
`active` set to true, because the rule is off unless configured (D22). Mockito and MockK are not on the classpath, so snippets must be
parsed, not compiled: if detekt-test's `lint` compiles the snippet by default,
switch that off. The snippets need a `package` line only where stated.

### C01: an import of the bare package name is reported
- Source: design.md > Decision 8 ("imported FQ name equals `org.mockito` or `io.mockk`")
- Level: unit
- Unit under test: `NoMockingLibraries.visitImportDirective` through `lint`
- Technique: boundary (exact match of the prefix itself)
- Arrange: two cases (parameterized): `import org.mockito` and `import io.mockk`.
- Act: lint the snippet.
- Expect: exactly one finding.
- Must fail when: the match is only `startsWith("org.mockito.")` / `startsWith("io.mockk.")`; the test sees no finding.

### C02: imports from deep subpackages are reported
- Source: specs/static-analysis/spec.md > Mocking libraries are flagged in test code (requirement: "and their subpackages"); design.md > Decision 8
- Level: unit
- Unit under test: `NoMockingLibraries` through `lint`
- Technique: equivalence class (subpackage two or more levels down)
- Arrange: cases: `import org.mockito.kotlin.mock`, `import org.mockito.junit.jupiter.MockitoExtension`, `import io.mockk.junit5.MockKExtension`, `import org.mockito.kotlin.*`.
- Act: lint each snippet.
- Expect: exactly one finding each.
- Must fail when: the rule compares the import's parent package with `org.mockito` / `io.mockk` (direct children only); the test sees no finding for the deeper imports.

### C03: a mocking package name in the middle or at the end of a path is not reported
- Source: design.md > Decision 8 ("equals ... or starts with `org.mockito.` or `io.mockk.`")
- Level: unit
- Unit under test: `NoMockingLibraries` through `lint`
- Technique: break vector: containment instead of prefix
- Arrange: cases: `import com.example.org.mockito.Mockito`, `import com.io.mockk.mockk`, `import fake.io.mockk`.
- Act: lint each snippet.
- Expect: no finding.
- Must fail when: the rule uses `contains("org.mockito")` / `contains("io.mockk")` or matches by suffix; the test sees a finding.

### C04: the alias name is ignored in both directions
- Source: design.md > Decision 8 ("aliased imports on the imported path, not the alias")
- Level: unit
- Unit under test: `NoMockingLibraries` through `lint`
- Technique: break vector: alias vs imported path
- Arrange: one snippet with `import com.example.FakeRepository as mockk` and `import com.example.Clock as Mockito`.
- Act: lint the snippet.
- Expect: no finding.
- Must fail when: the rule checks the alias or the import directive's whole text (`... as mockk`); the test sees findings.

### C05: the import's text form does not matter, only its FQ name
- Source: design.md > Decision 8 ("its imported FQ name")
- Level: unit
- Unit under test: `NoMockingLibraries` through `lint`
- Technique: break vector: raw text vs parsed name (parameterized)
- Arrange: cases: ``import `org`.mockito.Mockito``, ``import io.`mockk`.mockk``, `import org.mockito.Mockito;` (trailing semicolon), `import io.mockk.mockk // test helper` (trailing comment).
- Act: lint each snippet.
- Expect: exactly one finding each.
- Must fail when: the rule runs a regex or `startsWith` over the directive's source text; the backtick cases are missed (or the trailing-text cases fail an anchored regex).

### C06: mocking names in comments, strings and the package declaration are not reported
- Source: specs/static-analysis/spec.md > Mocking libraries are flagged in test code ("every import"; "SHALL NOT check ... fully qualified references without an import"); design.md > Decision 8 (`visitImportDirective`)
- Level: unit
- Unit under test: `NoMockingLibraries` through `lint`
- Technique: break vector: text scanning
- Arrange: one snippet with `package org.mockito.sample`, a line comment `// import io.mockk.mockk`, a KDoc mentioning `import org.mockito.Mockito`, and `val s = "import io.mockk.every"`. No import directives.
- Act: lint the snippet.
- Expect: no finding.
- Must fail when: the rule scans the file text line by line, or also visits the package directive; the test sees one or more findings.

### C07: every offending import in a file is reported, at its own line, and clean imports are not
- Source: design.md > Decision 8 ("one finding per offending import directive, located at that directive")
- Level: unit
- Unit under test: `NoMockingLibraries` through `lint`
- Technique: equivalence class (many, mixed)
- Arrange: snippet with `package sample` on line 1, a blank line 2, then `import kotlin.test.Test` (line 3), `import org.mockito.Mockito` (line 4), `import java.util.UUID` (line 5), `import io.mockk.every` (line 6).
- Act: lint the snippet.
- Expect: exactly two findings, at lines 4 and 6.
- Must fail when: the rule stops after the first finding per file, reports at the file or import-list start instead of the directive, or reports every import once one is offending; the test sees the wrong count or the wrong lines.

### C08: the same offending import twice gives two findings
- Source: design.md > Decision 8 ("one finding per offending import directive")
- Level: unit
- Unit under test: `NoMockingLibraries` through `lint`
- Technique: break vector: duplicates
- Arrange: snippet with `import io.mockk.mockk` on two lines.
- Act: lint the snippet.
- Expect: exactly two findings, one per line.
- Must fail when: the rule de-duplicates by imported path (a set of seen names); the test sees one finding.

### C09: an aliased import's message names the imported path, not the alias
- Source: specs/static-analysis/spec.md > Mocking-library findings have a fixed message ("the imported path as written in the import, without an alias"); design.md > Decision 8
- Level: unit
- Unit under test: `NoMockingLibraries` through `lint`
- Technique: break vector: alias vs imported path
- Arrange: snippet with `import io.mockk.mockk as m`.
- Act: lint the snippet.
- Expect: the single finding's message equals exactly `Import of 'io.mockk.mockk' is not allowed. Use fakes instead of mocking libraries.`
- Must fail when: the message uses the directive's text including the alias (`'io.mockk.mockk as m'`) or the alias alone (`'m'`); the equality check fails.

### C10: a file with only clean imports has no finding
- Source: specs/static-analysis/spec.md > Mocking libraries are flagged in test code ("every import from `org.mockito` or `io.mockk`", nothing else)
- Level: unit
- Unit under test: `NoMockingLibraries` through `lint`
- Technique: equivalence class (clean)
- Arrange: snippet with `import kotlin.test.Test`, `import assertk.assertThat`, `import com.plcoding.cardigan.common.fake.MutableClock`, `import org.junit.jupiter.api.Test`.
- Act: lint the snippet.
- Expect: no finding.
- Must fail when: the rule reports every import, or matches on `org.` / `io.` only; the test sees findings.

### C11: the provider exposes rule set `cardigan` with exactly NoMockingLibraries
- Source: design.md > Decision 8 (`CardiganRuleSetProvider`: `ruleSetId = RuleSetId("cardigan")`, `instance()` "contains exactly ::NoMockingLibraries")
- Level: unit
- Unit under test: `CardiganRuleSetProvider.ruleSetId`, `CardiganRuleSetProvider.instance()`
- Technique: equivalence class (one rule)
- Arrange: a new `CardiganRuleSetProvider()`.
- Act: read `ruleSetId`; call `instance()` and create its rules with an empty config.
- Expect: the rule set id is `cardigan`; the rule set holds exactly one rule, a `NoMockingLibraries`.
- Must fail when: the id is `tooling` (the rejected alternative in D15) or the set is empty or holds an extra rule; the test sees the wrong id or count.

### C12: the rule's name is NoMockingLibraries
- Source: design.md > Decision 8 ("rule name = \"NoMockingLibraries\" (from the class name)"); specs/static-analysis/spec.md > Mocking libraries are flagged in test code
- Level: unit
- Unit under test: `NoMockingLibraries`
- Technique: equivalence class
- Arrange: a `NoMockingLibraries` built from detekt-test's test config.
- Act: read the rule's name (the `ruleName` detekt derives).
- Expect: `NoMockingLibraries`.
- Must fail when: the class or an override names the rule differently (for example `NoMocking`), which also breaks the config key and `@Suppress` id; the test sees the other name.

### C13: the provider is registered for ServiceLoader discovery
- Source: design.md > Decision 8 ("Registered in `src/main/resources/META-INF/services/dev.detekt.api.RuleSetProvider`")
- Level: unit
- Unit under test: the service registration of `CardiganRuleSetProvider`
- Technique: state (registered vs not)
- Arrange: the test classpath with the `:tooling` main output.
- Act: `ServiceLoader.load(RuleSetProvider::class.java)` and collect the providers.
- Expect: exactly one provider has rule set id `cardigan`, and it is a `CardiganRuleSetProvider`.
- Must fail when: the services file is missing, misnamed (for example the detekt 1.x name `io.gitlab.arturbosch.detekt.api.RuleSetProvider`) or names a wrong class; the test finds no `cardigan` provider.
