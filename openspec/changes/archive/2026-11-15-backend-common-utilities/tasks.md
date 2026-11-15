# Tasks

## 1. Dependencies and configuration

- [x] 1.1 Add `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-validation` and `springdoc-openapi-starter-webmvc-api` (latest 3.x for Boot 4.1, looked up at implementation) to `gradle/libs.versions.toml`, and wire them into `:common` (`api`) and the test configurations. Verify with `./gradlew build`.
- [x] 1.2 Add `cardigan.security.jwt.*`, `cardigan.web.max-request-body-size`, `springdoc.api-docs.enabled: false`, `spring.mvc.formcontent.filter.enabled: false` and `spring.servlet.multipart.enabled: false` to `application.yaml`. Move `application-test.yaml` from `app/src/test/resources` to `:common` `testFixtures` resources and add a fixed base64 test secret to it. Verify that the existing `HealthSmokeTest` still passes.
- [x] 1.3 Ask the user to add `JWT_SECRET` (base64, ≥ 32 bytes) to the secret manager behind `.env`. Verify with `SPRING_PROFILES_ACTIVE=dev ./gradlew :app:bootRun` starting.

## 2. Time

- [x] 2.1 Add `ApplicationConfig` in `:app` with the millisecond-ticking UTC `Clock`, and `MutableClock` in `:common` test fixtures. Verify with a unit test that `MutableClock` truncates to milliseconds and moves forward on demand, and a unit test on `ApplicationConfig().clock()` that reads 1000 instants and asserts `nano % 1_000_000 == 0` and the UTC zone.

## 3. Error model

- [x] 3.1 Add `ErrorCode` (with fixed title and detail), `FieldErrorCode`, the base exceptions and `problemDetail()` in `:common`. Verify with unit tests: the URN for every code, the fixed title and detail, no `instance`, `fieldErrors` always present.
- [x] 3.2 Add `CommonExceptionHandler`, `ProblemResponseWriter`, `ProblemErrorController` and the `RequestRejectedHandler` bean with the mapping table from `design.md`, turn off implicit HEAD and OPTIONS for `/api/v1`, and add the test-only controller (including `whoami`) and the test-only failing filter for E2E tests. Verify with E2E tests for `ROUTE_NOT_FOUND` (unknown path, wrong method, HEAD and OPTIONS, a path with `;`), `UNSUPPORTED_MEDIA_TYPE`, `MALFORMED_REQUEST` (invalid JSON, wrong type, coerced scalar, the same `detail` for both), malformed path UUID → `NOT_FOUND`, `INTERNAL_ERROR` with a fixed detail and a logged stack trace from an endpoint and from a filter, no echoed path or media type in `detail`, the same `title` for the three 401 causes, and the exact field set of each body including those written by `ProblemResponseWriter` (`error-responses`).

## 4. Text rules and validation

- [x] 4.1 Add `normalizeText`, `normalizePassword`, `codePointLength` and the text check in `:common` `domain/text`. Verify with adversarial unit tests covering every `text-length` scenario plus NBSP, U+001C–U+001F, lone high and low surrogates, CR-only, and limits ±1.
- [x] 4.2 Add `@Required`, `@ValidText`, `@ValidUuid`, `@ValidEnum` and `RequestValidator` with the dynamic payload, plus per-field ranking in the handler. Verify with E2E tests: `{}` reports all required fields, an explicit `null` reports `REQUIRED`, missing plus too-long fields, one error per field by precedence, `min`/`max` present only for limit codes (`TOO_LONG` has only `max`), nested and array paths, a non-UUID array item → `INVALID_FORMAT`, an unknown enum value → `INVALID_VALUE`, a huge integer → `OUT_OF_RANGE`, a malformed path ID with an unparseable body → `MALFORMED_REQUEST`, and a missing reference with an invalid field → `NOT_FOUND` (`api-errors`, `error-responses`).

## 5. Wire conventions

- [x] 5.1 Configure Jackson: the `Instant` format with `.SSS'Z'`, scalar coercion off, unknown properties ignored. Verify with E2E tests for a whole-second timestamp, an uppercase UUID in the path accepted, a lowercase UUID in responses, and an unknown field ignored.
- [x] 5.2 Add `RequestSizeLimitFilter` after the security chain, with `PayloadTooLargeException` extending `IOException`. Verify with E2E tests at exactly 262,144 bytes (accepted), 262,145 bytes (413), `Content-Length` 307200 to any `/api/v1` path (413 with the exact field set), 300 KB chunked JSON to an endpoint that reads it (413), 401 winning over 413, tests pinning the chunked-plus-wrong-media-type and chunked-unread-body cases from `design.md`, an unauthenticated chunked 300 KB `PUT` with `Content-Type: application/x-www-form-urlencoded` (401), the same `PUT` authenticated with `Content-Length` (413), and authenticated and chunked (415, body never read, as accepted under Risks in `design.md`), a context test that no `FormContentFilter` and no `MultipartResolver` bean exists, and a chunked 300 KB `multipart/form-data` POST that is not parsed and never returns 2xx.
- [x] 5.3 Verify with an E2E test that a cross-origin request and an `OPTIONS` preflight get no `Access-Control-Allow-*` header.
- [x] 5.4 Configure content negotiation to ignore `Accept` (D23). Verify with E2E tests that `Accept: text/plain` on `/users/me` returns JSON and `Accept: text/html` on a 401 returns `application/problem+json`, never 406.

## 6. Authentication

- [x] 6.1 Add `JwtProperties` (plain `String`, no checks during binding) and the `jwtSecretKey` bean with the secret checks. Verify with tests that start the app through `SpringApplication` with `OutputCaptureExtension`: a missing secret, a non-base64 secret and a 31-byte secret each fail, and the captured output (including the failure analysis) doesn't contain the value. A 32-byte secret and a 32-byte secret plus a trailing newline start. Add a test that loads the main `application.yaml` (no test profile) without `JWT_SECRET` and asserts that startup fails.
- [x] 6.2 Add the `JwtDecoder` with the HS256-only setup and the expiry, type and subject validators, and `JwtAccessTokenIssuer` with second truncation. Verify with unit tests: `.789` issue time, exact `exp` rejected, 1 ms before accepted, `alg none`, HS512 with the same secret, other secret, `type refresh`, non-UUID `sub`, and tokens with no `exp`, no `type` or no `sub`.
- [x] 6.3 Add `SecurityConfig`, `PublicRoutes` (bean type, injected as a list), the `BearerTokenResolver`, the entry point with `WWW-Authenticate: Bearer`, and `requestUserId`. Verify with E2E tests for every `request-authentication` scenario: no token, `Basic` scheme, tampered, expired after moving `MutableClock` 16 minutes, unknown `/api/v1` path with and without a token, `/actuator/env` → 404, `/actuator/health` → 200 also with an expired bearer token, `whoami` with a token for a user with no row → 200 with that ID, no `Set-Cookie`, and a test that fails when any controller mapping is outside `/api/v1/`.
- [x] 6.4 Update the `service-health` E2E or smoke test for `/api/v1/actuator/health` (401 without a token, 404 `ROUTE_NOT_FOUND` with one).

## 7. Users

- [x] 7.1 Add `User`, `UserRepository`, `UserEntity`, the Spring Data adapter and the mappers in `:user`, with the lowercase invariant in the domain model and the entity `@Check` (D1). Verify with unit tests that the domain model rejects `Ann@Example.com`, and `@DataJpaTest` tests for the round trip, the exact duplicate rejected, the mixed-case input lowercased and then rejected as a duplicate, a direct uppercase insert (native SQL) rejected by the check constraint, and `created_at` stored at millisecond precision.
- [x] 7.2 Add `UserService` and `UserController` with `GET /api/v1/users/me` returning `id`, `email`, `createdAt`, and `CurrentUserNotFoundException` mapped to 401 with `WWW-Authenticate: Bearer` (D21). Verify with E2E tests for the happy path (exact fields, lowercase email and UUID), the missing user (401), and no token.
- [x] 7.3 Add `UserFixtures` in `:user` test fixtures, `DatabaseCleaner` in `:common` test fixtures, `testImplementation(testFixtures(projects.user))` in `:app`, and the authenticated-client helper in the `:app` `E2eTest` base class. Verify that the tests in 6.3 and 7.2 use them.

## 8. OpenAPI snapshot

- [x] 8.1 Add the springdoc customizers (request properties non-null with only `@Required` ones required, `Problem` and `FieldError` schemas, default 401/500 responses, 400/413/415 on operations with a request body, `@ApiErrors`, `bearerAuth` from the merged `PublicRoutes`), the single server `/`, and `springdoc.api-docs.version: openapi_3_0` (D22). Verify with a test that the snapshot declares `3.0.1` and has no list-valued `type`, a test that asserts the `getCurrentUser` operation, its tag, formats, exactly the statuses 200/401/500 and its security requirement (`api-contract-snapshot`), and a customizer test on a separate context with a test DTO that has a required `name` and an optional `note` (neither nullable, only `name` required).
- [x] 8.2 Add `OpenApiSnapshotTest`, the `:app:updateOpenApiSnapshot` task and the committed `app/src/test/resources/openapi/openapi.json`. Verify that the build fails after a deliberate DTO change, that the update task fixes it, that two runs produce identical files, that every `operationId` is camelCase and unique with exactly one PascalCase plural tag, that no path starts with `/api/v1/test`, and that `/v3/api-docs` and `/swagger-ui/index.html` return 404 in the running app.

## 9. Project rules

- [x] 9.1 Update `AGENTS.md`: rate limiting per IP plus per email for login with code `RATE_LIMITED`, emails stored lowercased (D1, pending the shared `domain-user` change), `/users/me` 401 for a missing user, OpenAPI 3.0.1, `Accept` ignored, the nullable request DTO rule with `@Required` (optional request fields are not nullable in the document), the JSON snapshot with no runtime docs and its update task, `JWT_SECRET`, URN problem types and no 403, and the current state (security, springdoc and the users table now wired in). Also update "Where does X go?" and the module layouts: client-facing failures subclass `ResourceNotFoundException`, `UniquenessConflictException` or `UnauthenticatedException` in `:common` and need no feature handler; public routes are added through a module's own `PublicRoutes` bean, not in `SecurityConfig`; OpenAPI customizers live in `:common` `api/openapi`; `:common` `domain/exception` holds no `ForbiddenException`; request DTOs use `String` + `@ValidUuid`/`@ValidEnum` and `BigInteger` for bounded integers; endpoints with references call `RequestValidator` after resolving them; every controller mapping starts with `/api/v1/`; and add the review checklist entry "mappers call `normalizeText` for every text field". Verify by reading the diff against `decisions.md` and `design.md`.

## 10. Integration

- [x] 10.1 Run `./gradlew build` from a clean checkout with Docker running, and confirm that every scenario in this change's specs maps to at least one test.

## Workflow follow-up

- Commit each group separately once `./gradlew build` passes, per `CLAUDE.md`.
- Archive the change after implementation and review, and only after the shared `domain-user` spec stores emails lowercased (D1).
