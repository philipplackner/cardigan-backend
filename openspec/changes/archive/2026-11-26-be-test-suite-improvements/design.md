# Design

## Context

See proposal.md for the motivation and decisions.md (Facts) for the baseline numbers per module and class.

The gap has two different causes, and each needs its own fix:

- **No coverage (302 mutants).** These classes are reached only by tests tagged `@Tag("integration")` (`@SpringBootTest`, `@DataJpaTest`, E2E), and PIT leaves those tests out. Even classes with a dedicated test fall in this group: `CardiganOpenApiCustomizerTest` boots Spring, and `CommonExceptionHandlerTest` never calls the handler's methods.
- **Survived (35 mutants).** Unit tests run the code but don't assert precisely enough. Typical cases are boundary checks in validators (`ValidPasswordValidator`, `ValidEmailValidator`, `ValidBoardKeyValidator`, `BoardKey`), mapper fields nobody asserts, and single conditions in services (`SessionService.registerUser`, `SessionCleanupService`, `AuthService`).

PIT decides what to leave out through `NON_LOGIC_CLASS_ANNOTATIONS` in `build-logic/convention/src/main/kotlin/NonLogicClassScanTask.kt`. It scans class-file annotations, so the exclusion works by annotation and needs no package patterns.

## Goals / Non-Goals

**Goals:**
- Reach the one-time targets in decisions.md D3/D4 with unit tests that check real behavior, not just run code to raise the numbers.
- Keep every new test a pure unit test: in memory, no Spring Boot context, no container, no `@Tag("integration")`.

**Non-Goals:**
- Writing the targets into build defaults, CLAUDE.md, main specs or memory (D2).
- Replacing existing integration or E2E tests. They stay as they are.
- Tests whose only purpose is coverage (D9).

## Decisions

### Exclude `@Repository` and `@Entity` by annotation (D5)
Add `org.springframework.stereotype.Repository` and `jakarta.persistence.Entity` to `NON_LOGIC_CLASS_ANNOTATIONS`. All six JPA adapters carry `@Repository` and all six entities carry `@Entity`, and no other main class uses either. So the exclusion matches D5 exactly. This holds today. From now on, `@Repository` means a JPA adapter in this project: a class with logic worth mutating must not carry it. New adapters and entities are excluded with no setup change, the same way `@Configuration` classes are. Spring Data interfaces produce no mutants, and entity mapper files are top-level functions with neither annotation, so they stay mutated.
*Alternative:* package patterns such as `*.infra.database.repository.Jpa*`. Rejected because they depend on names and wouldn't match how the existing exclusions work.

### Web layer: call the classes directly (D6)
- **Controllers** (`AuthController`, `UserController`, `BoardController`, `ColumnController`, `LabelController`): build them with the real service on the module's existing fakes (`user/src/test/.../fake`, `board/src/test/.../fake`). Build them with a `RequestValidator` over a plain Bean Validation `Validator` from `Validation.buildDefaultValidatorFactory()`, and use `MockHttpServletResponse` where needed. Assertions cover the returned DTO field by field (D7), the cookie written by `RefreshTokenCookie`, the order "rate limit before validation" in register and login, and the refresh and logout transport branches. Request DTO mappers already have tests, so controller tests don't repeat validation rules.
- **`CommonExceptionHandler`, `ProblemErrorController`, `ProblemResponseWriter`, `ProblemDetailBodyAdvice`:** call the handler methods with each exception type, plus the response writer with `MockHttpServletResponse`. Assert status, `code`, `type`, `title`, `detail`, `fieldErrors` and headers such as `Retry-After`. The existing `CommonExceptionHandlerTest` grows into this.
- **`RequestSizeLimitFilter`:** run it with `MockHttpServletRequest`/`MockHttpServletResponse`/`MockFilterChain`. Cover the limit edges (exactly at the limit, one byte over), declared `Content-Length` versus a streamed body, every read method of the limited stream, and the response when the limit trips.
- **`CardiganOpenApiCustomizer`:** feed it an in-memory `OpenAPI` object, a `RequestMappingHandlerMapping` with handler methods registered through `registerMapping`, and a `PublicRouteRegistry`. This uses the same kind of test controllers and DTOs as the existing Spring test, moved into a shared test file so both tests use them. The existing `@SpringBootTest` stays as the end-to-end check of the springdoc wiring.
- **Small helpers** (`PublicRouteRegistry`, `ApiBearerTokenResolver`, `ExplicitMethodsHandlerMapping`, `CanonicalUuidBinderAdvice`/`CanonicalUuidEditor`, `requestUserId`, `RequestValidator`, `ValidRangeValidator`, `UniquenessConflictException`): direct unit tests.

*Alternative:* `MockMvc` standalone setups. Rejected because it builds Spring MVC infrastructure, needs the integration tag by project rules, and so PIT would leave it out again.

### DTO getters through mapper and controller tests (D7)
Mapper tests build a domain object with a distinct value in every field and assert every DTO property. Empty strings, empty lists and zeros are never used as expected values, because PIT replaces return values with exactly those.

### Surviving mutants: assert harder, or simplify (D8)
For each survivor, read the mutant's description in `mutations.xml` and write the one test that tells the original from the mutant, usually a boundary value or a field nobody checked. If no input can tell them apart (an equivalent mutant), first try to simplify the production code in a separate `refactor(<module>)` commit that keeps behavior and `openapi.json` unchanged. If that doesn't work either, list the mutant in the final report.

In security code (`:common` `infra/security`, `api/security`, `RequestSizeLimitFilter`, `:user` `infra/security`, `infra/ratelimit`, `AuthService`, `SessionService` and the password and email validators), a check is removed only with a test that shows it is redundant, and the commit message names the mutant. Without such a test, keep the code and record the mutant in the final report.

### Measuring the targets
All targets are measured on the raw `mutationReport` numbers. Recorded equivalent mutants count as not killed. If a target is still missed after every non-equivalent mutant is killed, stop, and report the gap and the equivalent mutants to the user instead of adding tests.

### Measuring the auth target
`mutationReport` reports per module, but the D1 scope is `:user` plus `:common` `infra/security`. That score is computed from `user/build/reports/pitest/mutations.xml` and the `com.plcoding.cardigan.common.infra.security` entries of `common/build/reports/pitest/mutations.xml`, counting killed statuses the same way `MutationReportTask` does. A one-off script in the session scratchpad does this. It is not added to the repo (D2).

### Order of work
Build logic first, because it changes the denominators the targets are measured against. Then `:common`, which has the largest gap and whose web tests set the pattern. Then `:user`, which has the higher bar. Then `:board`, which is almost at target after the exclusion. One commit per logical chunk, after `./gradlew build` passes, as CLAUDE.md requires.

## Risks / Trade-offs

- [Excluding adapters hides real logic, for example the exception translation in `JpaUserRepository.save` and `JpaLabelRepository`] → Persistence and E2E tests already cover these paths. Kover still reports them, and the next coverage run shows any loss.
- [`RequestMappingHandlerMapping.registerMapping` may need an application context to initialize] → If so, keep the customizer covered only by its existing Spring test, record its remaining mutants in the final report, and tell the user early, because `:common` then reaches 80% only if most other classes are fully killed. A `StaticApplicationContext` is not an option: it starts a Spring context, which needs the integration tag, so PIT would leave the test out.
- [Tests written just to kill mutants become brittle] → Each test asserts one behavior a client or caller relies on and is named after that behavior. Tests that only pin incidental implementation details are not written.
- [Line numbers in `mutations.xml` shift during the work] → Re-run `mutationReport -Pmutation.modules=<module>` after each chunk and work from the fresh report.
- [PIT timing variance flips a mutant between `TIMED_OUT` and `SURVIVED`] → Both count the same way as in PIT's report. Only `SURVIVED` and `NO_COVERAGE` need work.

## Migration Plan

Tests and build logic only. Nothing is deployed and there's nothing to roll back beyond reverting commits.
