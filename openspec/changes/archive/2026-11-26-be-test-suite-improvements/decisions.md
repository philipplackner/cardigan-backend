# Decisions: be-test-suite-improvements

Confirmed with Philipp Lackner on 2026-11-26.

## Summary

Raise the backend's test quality where the coverage and mutation reports show weak spots, mainly code that only integration and E2E tests reach. The targets below are one-time goals for this change only. They are never written into main specs, CLAUDE.md, build config or memory, and nothing enforces them. The only permanent rule change is that JPA adapters and JPA entities are no longer mutated.

## Decisions

### D1 — Scope of the auth mutation target
- **Question**: There is no separate auth module. Which code does the higher auth mutation target cover?
- **Decision**: All of `:user` plus the JWT code in `:common` `infra/security`.
- **Alternatives**: All of `:user` only; only the auth classes inside `:user`.
- **Why**: Chosen by the user.
- **Depends on**: —

### D2 — Where the targets are written down
- **Question**: The user does not want the targets as permanent rules. How do the artifacts handle them?
- **Decision**: The numbers appear only in this change's artifacts, as one-time goals. No main spec, CLAUDE.md, build config (`coverage.minLine`, `mutation.minScore` defaults) or memory gets them.
- **Alternatives**: Leave the numbers out of all files.
- **Why**: The user wants to raise the floor once, not set a standing policy.
- **Depends on**: —

### D3 — Targets for this change
- **Question**: Which targets apply?
- **Decision**: Line coverage of at least 80% and a mutation score of at least 80% per module. Mutation score of at least 90% for the D1 auth scope. Branch coverage is reported but has no target.
- **Alternatives**: Line and branch coverage both at 80%.
- **Why**: Given by the user in the request. The line-only coverage metric was confirmed.
- **Depends on**: D1, D2

### D4 — Per-module targets
- **Question**: Do the targets apply per module or to the combined total?
- **Decision**: Per module: `:common`, `:user` and `:board` each reach them on their own.
- **Alternatives**: Combined total only.
- **Why**: A strong module must not hide a weak one.
- **Depends on**: D3

### D5 — JPA adapters and entities leave mutation testing
- **Question**: How are the `Jpa*Repository` adapter mutants handled? They are tested only by `@DataJpaTest` and E2E tests, which PIT leaves out.
- **Decision**: Exclude the JPA adapters and the JPA entities from PIT. This is a permanent change to the `mutation-testing` spec. Kover coverage is not affected. Entity mappers stay mutated.
- **Alternatives**: Unit tests with hand-written fakes of the Spring Data interfaces; letting `@DataJpaTest` tests run under PIT; excluding all of `infra/database` including entity mappers; excluding only the adapters.
- **Why**: Chosen by the user.
- **Depends on**: —

### D6 — Web layer gets plain unit tests
- **Question**: How are controllers, `CommonExceptionHandler`, `ProblemErrorController`, `RequestSizeLimitFilter` and `CardiganOpenApiCustomizer` tested for mutation purposes?
- **Decision**: Plain unit tests without Spring. Controllers are called directly with real services backed by fakes. The handler gets exceptions directly. The filter runs on mock servlet objects. The customizer runs on an in-memory OpenAPI object.
- **Alternatives**: Exclude the web layer from PIT.
- **Why**: Recommended option, accepted.
- **Depends on**: —

### D7 — DTO getter mutants
- **Question**: How are the DTO property getter mutants handled? Only Jackson reads them, in E2E tests.
- **Decision**: Mapper and controller unit tests check every field of the DTO they return, which kills these mutants.
- **Alternatives**: Exclude `api/dto` from PIT.
- **Why**: Recommended option, accepted. It also checks the mapping.
- **Depends on**: D6

### D8 — Production code changes
- **Question**: What may the implementation do when a surviving mutant shows redundant or unreachable production code?
- **Decision**: Simplify or remove it in a separate refactor commit, as long as behavior and the OpenAPI snapshot stay the same. Equivalent mutants that remain are listed in the final report.
- **Alternatives**: Tests only.
- **Why**: Recommended option, accepted.
- **Depends on**: —

### D9 — No separate coverage work
- **Question**: Line coverage is already at least 80% everywhere. Do the coverage gaps get tasks of their own?
- **Decision**: No. Coverage improves as a side effect of the mutation work. A final `coverageReport` run confirms the result.
- **Alternatives**: Close every remaining missed line and branch.
- **Why**: Recommended option, accepted.
- **Depends on**: D3

## Facts

- Baseline coverage (`./gradlew coverageReport`, 2026-11-26): total line 97.7% (1347/1379), branch 85.8% (514/599). Per module, line / branch: `:common` 93.8% / 77.5%, `:user` 99.6% / 95.8%, `:board` 99.6% / 94.3%. `:app` has no reportable classes. — source: `build/reports/kover/report.xml`
- Baseline mutation (`./gradlew mutationReport`, 2026-11-26): `:common` 34.7% (108/311), `:user` 59.9% (100/167), `:board` 63.0% (114/181), total 48.9%. Test strength is 90.2%, so 302 of the 337 mutants that aren't killed have no unit-test coverage at all. — source: `<module>/build/reports/pitest/mutations.xml`
- Without the JPA adapters and entities (D5), the baseline is `:common` 34.7% (108/311), `:user` 69.9% (100/143), `:board` 79.2% (114/144). The D1 auth scope is 68.7% (101/147). — source: computed from the same mutations.xml files
- The largest no-coverage classes in `:common` are `CardiganOpenApiCustomizer` (99 mutants), `CommonExceptionHandler` (28), `RequestSizeLimitFilter` (22), `ProblemResponseWriter` (7), `PublicRouteRegistry` (5) and `ProblemErrorController` (5). — source: `common/build/reports/pitest/mutations.xml`
- `CardiganOpenApiCustomizerTest` is a `@SpringBootTest`, so PIT leaves it out. `CommonExceptionHandlerTest` is a unit test, but it doesn't reach the handler's methods. — source: `common/src/test/.../api`
- In `:user`, `AuthController` (14) is all no-coverage. Survivors are in `ValidPasswordValidator` (4), `AuthService` (3), `ValidEmailValidator` (2), `SessionService` (1), `SessionCleanupService` (1), `BcryptPasswordHasher` (1) and `Bucket4jAuthRateLimiter` (1). — source: `user/build/reports/pitest/mutations.xml`
- In `:board`, the controllers (8) and DTOs (about 10) are no-coverage. Survivors are in `BoardService`, `BoardKey`, `ValidBoardKeyValidator`, the DTO mappers and `DensePositions`. — source: `board/build/reports/pitest/mutations.xml`
- In the JWT code, `ApiBearerTokenResolver` (3) is no-coverage. The other `infra/security` classes produce no mutants or are already killed. — source: `common/build/reports/pitest/mutations.xml`
- All six JPA adapters are annotated `@Repository`, and all six entities `@Entity`. No other main class uses either annotation. PIT excludes non-logic classes by scanning class annotations (`NON_LOGIC_CLASS_ANNOTATIONS` in `NonLogicClassScanTask`). — source: `build-logic/convention/src/main/kotlin/NonLogicClassScanTask.kt`, grep over `*/src/main`

## Out of scope

- Any change to API behavior, the OpenAPI snapshot, data or security behavior.
- Making targets permanent: no default `coverage.minLine`, `coverage.minBranch` or `mutation.minScore`, and no CLAUDE.md, main spec or memory entry for them.
- Running integration or persistence tests under PIT.
- Branch coverage targets.

## Deferred

- None.
