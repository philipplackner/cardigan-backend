# Tasks

One-time targets for this change (decisions.md D3/D4), not recorded anywhere else: line coverage ≥ 80% and mutation score ≥ 80% in each of `:common`, `:user` and `:board`; mutation score ≥ 90% for `:user` plus `:common` `infra/security`. Every new test is a pure unit test without `@Tag("integration")`. Commit after each group once `./gradlew build` passes.

## 1. Exclude JPA adapters and entities from mutation testing

- [x] 1.1 Add `org.springframework.stereotype.Repository` and `jakarta.persistence.Entity` to `NON_LOGIC_CLASS_ANNOTATIONS` in `build-logic/convention/src/main/kotlin/NonLogicClassScanTask.kt`. Verify that `./gradlew -p build-logic test` passes and that after `./gradlew mutationReport` no `mutatedClass` in any `mutations.xml` is a `Jpa*Repository` or `*Entity` class, while the `*EntityMappersKt` classes still have mutants
- [x] 1.2 Update the mutation-testing bullet in `CLAUDE.md` ("... are not mutated") to name `@Repository` and `@Entity` classes. Verify the line matches the delta in `specs/mutation-testing/spec.md`, then commit as `build: exclude jpa adapters and entities from mutation testing`

## 2. `:common` error handling

- [x] 2.1 Extend `CommonExceptionHandlerTest` to call every handler method directly: each base exception, `RequestValidationException`, unreadable body, unsupported media type, size limit, method not supported, unknown exception, and the `hasCause` helper. Assert status, `code`, `type`, `title`, `detail`, `fieldErrors` and `Retry-After`. Verify that `CommonExceptionHandler` and `CommonExceptionHandlerKt` have no `NO_COVERAGE` or `SURVIVED` mutants in `./gradlew mutationReport -Pmutation.modules=common`
- [x] 2.2 Add unit tests for `ProblemErrorController`, `ProblemResponseWriter`, `ProblemDetailBodyAdvice` and `ProblemFieldError` with mock servlet objects. Verify that these classes have no `NO_COVERAGE` or `SURVIVED` mutants in the `:common` report
- [x] 2.3 Add unit tests for `UniquenessConflictException` and `FieldOutOfRangeException` that kill the remaining survivor and no-coverage mutant. Verify in the `:common` report

## 3. `:common` web helpers and security

- [x] 3.1 Add `RequestSizeLimitFilterTest` with `MockHttpServletRequest`, `MockHttpServletResponse` and `MockFilterChain`. Cover the limit exactly, one byte over, a declared `Content-Length` over the limit, a streamed body without `Content-Length`, and every read method of the limited stream. Verify that `RequestSizeLimitFilter` and its inner classes have no `NO_COVERAGE` or `SURVIVED` mutants
- [x] 3.2 Add unit tests for `PublicRouteRegistry`, `PublicRoutes`/`PublicRoute`, `ExplicitMethodsHandlerMapping`, `CanonicalUuidBinderAdvice`/`CanonicalUuidEditor` and `requestUserId`. Verify there are no `NO_COVERAGE` mutants in these classes
- [x] 3.3 Add `ApiBearerTokenResolverTest`: a public route ignores the bearer token, a path outside `/api/v1` ignores it, and a protected route resolves it. Verify that `ApiBearerTokenResolver` has no surviving or no-coverage mutants
- [x] 3.4 Add unit tests for `RequestValidator`, `ValidRangeValidator`, and the survivors in `RequiredValidator`, `ValidTextValidator`, `ValidUuidValidator`, `ValidEnumValidator`, `NotNullWhenPresentValidator`, `FieldViolationKt` and `TextKt`, with boundary values that tell each mutant from the original. Verify in the `:common` report; mutants that can't be killed follow task 3.5
- [x] 3.5 For each `:common` survivor that no input can kill, simplify the production code without changing behavior in a separate `refactor(common)` commit (in security code only with a test that shows the check is redundant, see design.md D8), or record the mutant with a reason in the final report. Verify that `./gradlew build` passes and `OpenApiSnapshotTest` is unchanged

## 4. `:common` OpenAPI customizer

- [x] 4.1 Move the test controllers and DTOs from `CardiganOpenApiCustomizerTest` into a shared test file, so the Spring test and a unit test can both use them. Verify that the existing `CardiganOpenApiCustomizerTest` still passes unchanged
- [x] 4.2 Add `CardiganOpenApiCustomizerUnitTest`, which builds a `RequestMappingHandlerMapping` through `registerMapping`, a `PublicRouteRegistry` and an in-memory `OpenAPI`. Assert required and nullable properties, `format: uuid`, enum values, the documented error responses per operation, the 401 only on protected operations and the 400/413/415 only with a body. Verify that `CardiganOpenApiCustomizer` has no `NO_COVERAGE` mutants and that the test class carries no integration tag
- [x] 4.3 Run `./gradlew mutationReport -Pmutation.modules=common`. Verify that the `:common` mutation score is ≥ 80%. Note the `infra/security` counts for task 5.6. If the score is short, add tests for the largest remaining class before continuing. If it is still short once every non-equivalent mutant is killed, stop and report the gap and the equivalent mutants to the user (design.md, Measuring the targets)

## 5. `:user`

- [x] 5.1 Add `AuthControllerTest` with the real `AuthService` on the existing fakes, a `RequestValidator` over a plain Bean Validation validator, and `MockHttpServletResponse`. Cover register and login with `COOKIE` and `BODY` transport, the rate limit checked before validation, refresh from cookie versus body (including the missing-token error), and logout. Assert every `AuthSessionDto`/`AuthTokensDto` field and the cookie attributes. Verify that `AuthController`, `RefreshTokenCookie`, `AuthSessionDto`, `AuthTokensDto`, `RefreshRequest` and `LogoutRequest` have no no-coverage mutants
- [x] 5.2 Add `UserControllerTest` and extend `UserService` tests to cover the unknown-user branch of `getCurrentUser`, asserting every `CurrentUserDto` field. Verify that `UserController`, `UserService` and `CurrentUserDto` have no surviving or no-coverage mutants
- [x] 5.3 Kill the survivors in `ValidPasswordValidator`, `ValidEmailValidator` and `NotEmptyPasswordValidator` with boundary inputs (length limits, normalization edges) and the no-coverage line in `ValidPasswordValidator`. Verify in `./gradlew mutationReport -Pmutation.modules=user`
- [x] 5.4 Kill the survivors in `AuthService`, `SessionService.registerUser`, `SessionCleanupService`, `BcryptPasswordHasher` and `Bucket4jAuthRateLimiter`, plus the no-coverage paths in the last two and in `AuthDtoMappersKt`. Verify in the `:user` report
- [x] 5.5 For each `:user` survivor that no input can kill, simplify the production code without changing behavior in a separate `refactor(user)` commit (in security code only with a test that shows the check is redundant, see design.md D8), or record it with a reason. Verify that `./gradlew build` passes
- [x] 5.6 Compute the auth-scope score from `user/build/reports/pitest/mutations.xml` plus the `com.plcoding.cardigan.common.infra.security` entries of the `:common` report, with a scratchpad script that is not committed. Verify that the auth-scope score is ≥ 90%

## 6. `:board`

- [x] 6.1 Add unit tests for `BoardController`, `ColumnController` and `LabelController` with the real services on the existing fakes and a plain `RequestValidator`. Assert every field of `BoardDto`, `BoardSummaryDto`, `BoardListDto`, `ColumnDto`, `LabelDto` and `LabelListDto`. Verify that these classes have no no-coverage mutants in `./gradlew mutationReport -Pmutation.modules=board`
- [x] 6.2 Kill the survivors in `BoardService` (and its two no-coverage mutants), `BoardKeyKt`, `ValidBoardKeyValidator`, `BoardDtoMappersKt`, `ColumnDtoMappersKt` and `DensePositionsKt` with boundary and field-by-field assertions. Verify in the `:board` report
- [x] 6.3 For each `:board` survivor that no input can kill, simplify the production code without changing behavior in a separate `refactor(board)` commit, or record it with a reason. Verify that `./gradlew build` passes and the `:board` mutation score is ≥ 80%

## 7. Final check

- [x] 7.1 Run `./gradlew build`, `./gradlew coverageReport` and `./gradlew mutationReport` (Docker running). Verify that every module has line coverage ≥ 80%, every module has a mutation score ≥ 80%, the auth scope (task 5.6 method) is ≥ 90%, and `openapi.json` is unchanged in `git diff master`
- [x] 7.2 Report the before/after numbers per module and every mutant that was accepted as equivalent, with its reason, to the user in the session. Verify that no target number was added to `CLAUDE.md`, a main spec, build defaults or memory (`git diff master -- CLAUDE.md openspec/specs build-logic gradle`)

## Workflow follow-up

- Archive the change only when the user asks for it.
