# Tasks

## 1. Dependencies and configuration

- [ ] 1.1 Add `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-validation`, `springdoc-openapi-starter-webmvc-api` (latest 3.x for Boot 4.1, looked up at implementation) and `spring-security-test` to `gradle/libs.versions.toml`, and wire them into `:common` (`api`) and the test configurations. Verify with `./gradlew build`.
- [ ] 1.2 Add `cardigan.security.jwt.*`, `cardigan.web.max-request-body-size` and `springdoc.api-docs.enabled: false` to `application.yaml`, and a fixed base64 test secret to `application-test.yaml`. Verify that the existing `HealthSmokeTest` still passes.
- [ ] 1.3 Ask the user to add `JWT_SECRET` (base64, ≥ 32 bytes) to the secret manager behind `.env`. Verify with `SPRING_PROFILES_ACTIVE=dev ./gradlew :app:bootRun` starting.

## 2. Time

- [ ] 2.1 Add `ApplicationConfig` in `:app` with the millisecond-ticking UTC `Clock`, and `MutableClock` in `:common` test fixtures. Verify with a unit test that `MutableClock` truncates to milliseconds and moves forward on demand.

## 3. Error model

- [ ] 3.1 Add `ErrorCode`, `FieldErrorCode`, the base exceptions and `problemDetail()` in `:common`. Verify with unit tests: the URN for every code, the fixed title, no `instance`, `fieldErrors` always present.
- [ ] 3.2 Add `CommonExceptionHandler` and `ProblemResponseWriter` with the mapping table from `design.md`, plus a test-only controller for E2E tests. Verify with E2E tests for `ROUTE_NOT_FOUND` (unknown path, wrong method), `UNSUPPORTED_MEDIA_TYPE`, `MALFORMED_REQUEST` (invalid JSON, wrong type, coerced scalar), malformed path UUID → `NOT_FOUND`, `INTERNAL_ERROR` with a fixed detail and a logged stack trace, and the exact field set of each body (`error-responses`).

## 4. Text rules and validation

- [ ] 4.1 Add `normalizeText`, `normalizePassword`, `codePointLength` and the text check in `:common` `domain/text`. Verify with adversarial unit tests covering every `text-length` scenario plus NBSP, U+001C–U+001F, lone high and low surrogates, CR-only, and limits ±1.
- [ ] 4.2 Add `@Required` and `@ValidText` with the dynamic payload, plus per-field ranking in the handler. Verify with E2E tests: `{}` reports all required fields, missing plus too-long fields, one error per field by precedence, `min`/`max` present only for limit codes, and nested and array paths (`api-errors`, `error-responses`).

## 5. Wire conventions

- [ ] 5.1 Configure Jackson: the `Instant` format with `.SSS'Z'`, scalar coercion off, unknown properties ignored. Verify with E2E tests for a whole-second timestamp, an uppercase UUID in the path accepted, a lowercase UUID in responses, and an unknown field ignored.
- [ ] 5.2 Add `RequestSizeLimitFilter` after the security chain. Verify with E2E tests at exactly 262,144 bytes (accepted), 262,145 bytes (413), 300 KB chunked (413), 401 winning over 413, and a test pinning the chunked-plus-wrong-media-type case from `design.md`.
- [ ] 5.3 Verify with an E2E test that a cross-origin request and an `OPTIONS` preflight get no `Access-Control-Allow-*` header.
- [ ] 5.4 Configure content negotiation to ignore `Accept` (D23). Verify with E2E tests that `Accept: text/plain` on `/users/me` returns JSON and `Accept: text/html` on a 401 returns `application/problem+json`, never 406.

## 6. Authentication

- [ ] 6.1 Add `JwtProperties` with secret checks. Verify with `ApplicationContextRunner` tests: missing, non-base64 and 31-byte secrets fail with no secret in the message, and a 32-byte secret starts.
- [ ] 6.2 Add the `JwtDecoder` with the HS256-only setup and the expiry, type and subject validators, and `JwtAccessTokenIssuer` with second truncation. Verify with unit tests: `.789` issue time, exact `exp` rejected, 1 ms before accepted, `alg none`, other secret, `type refresh`, non-UUID `sub`.
- [ ] 6.3 Add `SecurityConfig`, `PublicRoutes`, the entry point with `WWW-Authenticate: Bearer`, and `requestUserId`. Verify with E2E tests for every `request-authentication` scenario: no token, `Basic` scheme, tampered, expired after moving `MutableClock` 16 minutes, unknown `/api/v1` path with and without a token, `/actuator/env` → 404, `/actuator/health` → 200, no `Set-Cookie`.
- [ ] 6.4 Update the `service-health` E2E or smoke test for `/api/v1/actuator/health` (401 without a token, 404 `ROUTE_NOT_FOUND` with one).

## 7. Users

- [ ] 7.1 Add `User`, `UserRepository`, `UserEntity`, the Spring Data adapter and the mappers in `:user`, with the lowercase invariant in the domain model and the entity `@Check` (D1). Verify with unit tests that the domain model rejects `Ann@Example.com`, and `@DataJpaTest` tests for the round trip, the exact duplicate rejected, the mixed-case input lowercased and then rejected as a duplicate, a direct uppercase insert rejected by the check constraint, and `created_at` stored at millisecond precision.
- [ ] 7.2 Add `UserService` and `UserController` with `GET /api/v1/users/me` returning `id`, `email`, `createdAt`, and `CurrentUserNotFoundException` mapped to 401 with `WWW-Authenticate: Bearer` (D21). Verify with E2E tests for the happy path (exact fields, lowercase email and UUID), the missing user (401), and no token.
- [ ] 7.3 Add `UserFixtures` in `:user` test fixtures and the authenticated-client helper in the `:app` `E2eTest` base class. Verify that the tests in 6.3 and 7.2 use them.

## 8. OpenAPI snapshot

- [ ] 8.1 Add the springdoc customizers (`@Required` handling, `Problem` and `FieldError` schemas, default 401/500 responses, `@ApiErrors`, `bearerAuth` from `PublicRoutes`) with `springdoc.api-docs.version: openapi_3_0` (D22). Verify with a test that the snapshot declares `3.0.1` and has no list-valued `type`, and a test that asserts the `getCurrentUser` operation, its tag, formats, error responses and security requirement (`api-contract-snapshot`).
- [ ] 8.2 Add `OpenApiSnapshotTest`, the `:app:updateOpenApiSnapshot` task and the committed `app/src/test/resources/openapi/openapi.json`. Verify that the build fails after a deliberate DTO change, that the update task fixes it, that two runs produce identical files, and that `/v3/api-docs` and `/swagger-ui/index.html` return 404 in the running app.

## 9. Project rules

- [ ] 9.1 Update `AGENTS.md`: rate limiting per IP plus per email for login with code `RATE_LIMITED`, emails stored lowercased (D1, pending the shared `domain-user` change), `/users/me` 401 for a missing user, OpenAPI 3.0.1, `Accept` ignored, the nullable request DTO rule with `@Required`, the JSON snapshot with no runtime docs and its update task, `JWT_SECRET`, URN problem types and no 403, and the current state (security, springdoc and the users table now wired in). Verify by reading the diff against `decisions.md`.

## 10. Integration

- [ ] 10.1 Run `./gradlew build` from a clean checkout with Docker running, and confirm that every scenario in this change's specs maps to at least one test.

## Workflow follow-up

- Commit each group separately once `./gradlew build` passes, per `CLAUDE.md`.
- Archive the change after implementation and review, and only after the shared `domain-user` spec stores emails lowercased (D1).
