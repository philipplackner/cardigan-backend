# Test-first workflow: project facts

The tdd-* agents and the `openspec-apply-tdd` skill read this file for
everything that depends on this codebase. Written by `install-openspec-tdd`;
keep it current when the build or the test setup changes. Where test code
lives is configured in `tdd.conf` next to this file, which the hook and the
scripts use.

## Schemas

- Test-first schema: `careful-planning-tdd`
- Plain schema (changes with nothing a test could observe): `careful-planning`
- The `Workflow` line of a change (test-first or plain, and which parts) is in: `decisions.md` of the change (the line starting `**Workflow**:`).

## Project rules

Read these for code style, architecture, commit rules and test conventions:

- `AGENTS.md` (`CLAUDE.md` is a symlink to it): architecture, layer rules, API contract, testing table, commands, git workflow. It wins over the skills.
- `.claude/skills/spring-unit-testing/SKILL.md`: unit tests (adversarial method, fakes, AssertK, `MutableClock`).
- `.claude/skills/spring-integration-testing/SKILL.md`: persistence, module integration, HTTP client and E2E tests.
- The other `.claude/skills/spring-*` skills for the layer a task touches (see the Skill Map in `AGENTS.md`).

## Test code

- Where tests live: `<module>/src/test/kotlin/...` in the package of the class under test, module-only fakes in `<module>/src/test/.../fake`, shared fakes and fixtures in `common/src/testFixtures` (`MutableClock`, `PostgresTestConfig`, `DatabaseCleaner`, `application-test.yaml`) and `user/src/testFixtures`. E2E tests in `app/src/test/kotlin/com/plcoding/cardigan/`. Build-logic unit tests in `build-logic/convention/src/test`. `app/src/test/resources/openapi/openapi.json` is the committed API snapshot: the implementer regenerates it with `./gradlew :app:updateOpenApiSnapshot` when the API changes and never edits it by hand (the hook blocks that); it is not a test-writer file.
- Frameworks and conventions: JUnit 5, AssertK, hand-written fakes (no Mockito, no MockK), Testcontainers Postgres 17 (never H2, never the Supabase database), WireMock for HTTP clients, Awaitility for async. Every test class that starts a Spring context or a container carries `@Tag("integration")`, directly or through its base class, so mutation testing leaves it out. Unit tests follow the adversarial method: list break vectors first, then one test per distinct angle. Tests use the `test` profile (Hibernate `create-drop`, Flyway off) until the baseline migration exists. Docker must be running for anything with a database.
- Test levels, cheapest first: unit (pure logic, fakes, `MutableClock`, no Spring, no I/O) → persistence (`@DataJpaTest` + Testcontainers) → module integration (`@SpringBootTest` with the module's `<Feature>TestApplication`; `:board` tests import `BoardTestConfig` and set `cardigan.board.lock-timeout`) → HTTP client (WireMock) → E2E (`:app`, extends `E2eTest`).

## Commands

| Purpose | Command |
|---|---|
| Full check. Must pass before every commit | `./gradlew build` (compiles everything and runs all tests, including the OpenAPI snapshot test; needs Docker). If build-logic changed, also `./gradlew -p build-logic test`. |
| Compile or type-check the tests without running them | `./gradlew testClasses` (all modules, test fixtures included). For build-logic: `./gradlew -p build-logic testClasses`. |
| Run all tests | `./gradlew test` (needs Docker). Build-logic: `./gradlew -p build-logic test`. |
| Run the tests of one area (`<area>` as defined below) | `./gradlew :<area>:test`, narrowed with `--tests '<fully.qualified.ClassName>'` where useful. Build-logic: `./gradlew -p build-logic test`. |

Run tests so they never write new expected-value files (snapshot or golden
files) on their own: the normal test tasks never write expected values. `OpenApiSnapshotTest` only compares; only `./gradlew :app:updateOpenApiSnapshot` rewrites `openapi.json`. Run it on purpose when the API changes, never to make a test pass, and never pass `-Dopenapi.update=true` to a test run.

## Areas

An area is one Gradle module: `app`, `common`, `user`, `board` or `tooling`. A changed file belongs to the module named by its first path segment (`board/src/...` → `board`). `build-logic/` is a separate included build with its own test command (above). `:app` holds only assembly and E2E tests; business code lives in `user` and `board`, shared code in `common`. `tooling` holds the custom detekt rules and, in `src/test`, their tests and the Konsist checks (package `com.plcoding.cardigan.tooling.konsist`). Its test command is `./gradlew :tooling:test` (full name required; `./gradlew test` and `./gradlew build` skip it), and it never runs the Konsist checks; those run with `./gradlew konsist`. It takes no part in coverage or mutation testing.

## Scaffold

- A scaffold body is: `TODO()` in Kotlin, for every function body and property getter that holds logic. Constructors, data class fields, enums, interfaces and Spring annotations are written for real so the context and the tests compile.
- Calling it fails with: `kotlin.NotImplementedError: An operation is not implemented.` Through HTTP this shows up as a 500 `INTERNAL_ERROR` problem response.

## Acceptance tests

- Level: the HTTP API, end to end: the whole app over HTTP in `:app`.
- How they are written: Kotlin test classes in `app/src/test/kotlin/com/plcoding/cardigan/`, one `<Feature>E2eTest` per feature, extending the `E2eTest` base class (`@SpringBootTest(RANDOM_PORT)`, `RestTestClient`, Testcontainers Postgres, `@Tag("integration")`). Sign in with `E2eTest.signIn` (register then login over HTTP). Assert problem responses with the helpers in `ProblemAssertions.kt`. Requests and responses are written as JSON against the documented `/api/v1/...` routes, not with the change's DTO classes. Run with `./gradlew :app:test --tests 'com.plcoding.cardigan.<Name>E2eTest'`.
- They use only the public interface of that level, never types or
  functions the change adds.
- `:tooling` (custom detekt rules) has its own acceptance level: detekt's engine (`detekt-core`, test-only) run in-process from tests in `tooling/src/test/kotlin/com/plcoding/cardigan/tooling/detekt/`. The run uses the real `config/detekt/detekt.yml`, loads the `cardigan` rule set only through detekt's plugin mechanism (the `META-INF/services/dev.detekt.api.RuleSetProvider` registration on the classpath), and analyzes files at `src/main/...`, `src/test/...` and `src/testFixtures/...` paths. Component tests use detekt-test's `lint(code)` against the rule directly. Run with `./gradlew :tooling:test`, narrowed with `--tests` where useful.

## Mutation testing

- Tool: PIT, through the `info.solidsoft.pitest` Gradle plugin and the `cardigan.pitest` convention plugin. Only unit tests run against mutants (`@Tag("integration")` classes are left out), so Docker isn't needed.
- Command for one area: `./gradlew mutationReport -Pmutation.modules=<area>` (areas `common`, `user`, `board`; `app` and build-logic don't take part). Use the full task name; abbreviations fail.
- Report: `<area>/build/reports/pitest/mutations.xml` (PIT XML, one `<mutation>` element per mutant with `status`, `sourceFile`, `mutatedClass`, `mutatedMethod`, `lineNumber`, `mutator`). HTML in `<area>/build/reports/pitest/index.html`, combined HTML in `build/reports/pitest/index.html`. The folder is wiped before every run.
- Survived: status `SURVIVED` or `NO_COVERAGE`. `KILLED`, `TIMED_OUT`, `MEMORY_ERROR`, `RUN_ERROR` and `NON_VIABLE` count as killed.
- Wiring and configuration classes, `@Repository` and `@Entity` classes, Kotlin null-check intrinsics and generated data-class methods are never mutated.
- Modules run one after another, never from Gradle's cache; `-Pmutation.threads=<n>` sets the thread count.

## Commits

From the Git Workflow section of `AGENTS.md`:

- Commit as soon as a logical chunk of work is done and `./gradlew build` passes. Never commit when the code doesn't compile, a test fails, or the build hasn't run. Red tests are never committed on their own.
- Conventional Commits with a scope where it fits: `feat(<module>): ...`, `fix(<module>): ...`, `test(<module>): ...`, `refactor: ...`, `build: ...`, `docs: ...`, `chore(config): ...`. Imperative mood, lowercase, no trailing period. One logical change per commit.
- Commit locally only, never push. Never amend or rewrite commits, never use `--no-verify`, never commit `.env` or secrets.
- An API change comes with the updated `openapi.json` snapshot in the same commit.
