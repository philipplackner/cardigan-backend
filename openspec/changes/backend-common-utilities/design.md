# Design

## Context

See `proposal.md` for the motivation and `decisions.md` for every settled choice (D1–D20).

Current state:

- Modules `:app`, `:common`, `:user` and `:board` exist with no production code besides `CardiganApplication`. `:common` has only test fixtures (`PostgresTestConfig`).
- Only `GET /actuator/health` is served. There is no security, validation or springdoc dependency.
- Tests use the `test` profile with Hibernate `create-drop` on a Testcontainers Postgres 17.
- Spring Boot 4.1.1, Jackson 3, Kotlin 2.3.21, virtual threads.

## Goals / Non-Goals

**Goals:**

- `be-auth`, `be-boards` and `be-labels` can start in parallel and only add controllers, services, entities and domain exceptions. None of them touch the filter chain, the error handler or the OpenAPI setup.
- Every shared-spec behavior listed in `proposal.md` is covered by a test.

**Non-Goals:**

- Refresh tokens, cookies, password hashing, rate limiting (`be-auth`).
- Per-endpoint body limits above 256 KB. The mechanism allows them, but the 5 MB import limit is configured by `be-import-export`.
- The Modulith event registry. No events exist yet.

## Decisions

### Package placement

| Piece | Module / package |
|---|---|
| `ErrorCode` enum (code, status, title), `FieldErrorCode` enum, base exceptions (`ResourceNotFoundException`, `UniquenessConflictException(field)`, `UnauthenticatedException`) | `:common` `domain/exception` |
| Text rules: `normalizeText`, `normalizePassword`, `codePointLength`, `checkText(...)` | `:common` `domain/text` (pure Kotlin) |
| `AccessTokenIssuer` interface + `IssuedAccessToken(token, expiresAt)` | `:common` `domain/security` |
| `JwtProperties`, `JwtDecoder` bean, `JwtAccessTokenIssuer`, claim constants, token validators | `:common` `infra/security` |
| `CommonExceptionHandler`, `problemDetail()`, `ProblemResponseWriter`, `requestUserId` | `:common` `api` |
| `@Required`, `@ValidText` and their validators | `:common` `api/validation` |
| Jackson customization, `RequestSizeLimitFilter`, OpenAPI customizers, `PublicRoutes` | `:common` `api/web`, `api/openapi` |
| `SecurityConfig`, `ApplicationConfig` (`Clock`) | `:app` |
| `User`, `UserRepository`, `UserService`, `UserEntity`, `UserController`, `CurrentUserDto` | `:user` (layers per `CLAUDE.md`) |
| `MutableClock`, `DatabaseCleaner` | `:common` `testFixtures` |
| `UserFixtures` | `:user` `testFixtures` (new `java-test-fixtures` plugin), used by `:app` E2E tests |

`:common` gets `api` dependencies on webmvc, security, oauth2-resource-server, validation and springdoc, so feature modules see the types they need for controllers and constraints.

### Error model

- `ErrorCode` holds the closed catalog from `api-errors`: code, HTTP status and fixed title. `type` is derived as `urn:cardigan:problem:` + kebab-case code (D8). `problemDetail(code, detail, fieldErrors = emptyList())` builds a `ProblemDetail`, sets `type` and `title`, adds `code` and `fieldErrors` as properties, and leaves `instance` unset. Spring fills `instance` only when it's null and the response goes through `ResponseEntityExceptionHandler`, so the handler explicitly sets it to null before returning. A test asserts the exact field set.
- `CommonExceptionHandler` extends `ResponseEntityExceptionHandler` and overrides `handleExceptionInternal`, so every Spring MVC exception goes through one mapping:

  | Exception | Code |
  |---|---|
  | `NoResourceFoundException`, `HttpRequestMethodNotSupportedException` | `ROUTE_NOT_FOUND` |
  | `HttpMediaTypeNotSupportedException` | `UNSUPPORTED_MEDIA_TYPE` |
  | `HttpMessageNotReadableException` (cause is `PayloadTooLargeException`) | `PAYLOAD_TOO_LARGE` |
  | `HttpMessageNotReadableException` (other) | `MALFORMED_REQUEST` |
  | `MethodArgumentTypeMismatchException` on a `UUID` path variable | `NOT_FOUND` |
  | `MethodArgumentNotValidException`, `HandlerMethodValidationException` | `VALIDATION_FAILED` |
  | `ResourceNotFoundException` | `NOT_FOUND` |
  | `UniquenessConflictException` | `CONFLICT` with `ALREADY_USED` on its field |
  | anything else | `INTERNAL_ERROR`, logged at `ERROR` with stack trace |

- The `Accept` header is ignored (D23). Content negotiation is set to ignore the header and default to JSON, so `HttpMediaTypeNotAcceptableException` never comes up. Error bodies are always written as `application/problem+json`.
- `ProblemResponseWriter` serializes the same body for code that runs outside MVC: the security entry point and the size-limit filter. It writes `application/problem+json` with the app's `JsonMapper`.
- No 403 handler and no `ForbiddenException`. This deviates from the `spring-api-layer` skill on purpose (`api-errors`, "Other users' data is not found, never forbidden").
- Feature modules add domain exceptions as subclasses of the two base exceptions. They don't add handlers unless a new top-level code is added to the shared catalog.

### Validation and field errors

- Request DTO properties are nullable with no default (D11). `@field:Required` marks the ones the endpoint needs. Its validator fails on `null` with code `REQUIRED`, and for strings `@ValidText` also reports `REQUIRED` when the normalized text is empty and `min ≥ 1`.
- `@field:ValidText(min, max, multiline = false)` normalizes the value with `normalizeText` and checks, in order: `INVALID_CHARACTER`, `TOO_SHORT`, `TOO_LONG`. The validator puts the code and `min`/`max` into the Hibernate Validator dynamic payload.
- The handler groups violations by property path and keeps the one with the lowest rank in the `api-errors` order ("One field error per field"). Paths come out as `name`, `nested.name` and `labelIds[2]`, which matches the required JSON path form.
- Mappers call `normalizeText` again when building the domain value (D12). The validator never changes the DTO. A unit test per mapper is the guard against a forgotten normalization. `normalizePassword` (NFC only) is for `be-auth`.
- Text rules use `\p{IsWhite_Space}` for trimming. Control characters are U+0000–U+001F. Tab, LF and CR are allowed only when `multiline = true`. An unpaired surrogate is found by scanning chars, and passwords reject every control character.
- Jackson is configured so that a wrong JSON type never turns into a valid value: scalar coercion is off (`"name": 5` → `MALFORMED_REQUEST`, not `"5"`), and empty strings are not coerced to null. Unknown properties are ignored. That is Boot's default, and a test keeps it that way.

### Wire formats

- `Instant` is serialized with a fixed formatter `uuuu-MM-dd'T'HH:mm:ss.SSS'Z'` in UTC, so whole seconds still get `.000`. Deserialization accepts ISO 8601 instants.
- UUIDs are serialized with `UUID.toString()`, which is lowercase. Path and body parsing use `UUID.fromString`, which accepts any case.
- No CORS configuration exists: no `CorsConfigurationSource` bean, no `.cors()` in the filter chain, no `@CrossOrigin`. A test sends a cross-origin `OPTIONS` preflight and asserts that no `Access-Control-Allow-*` header comes back.

### Request size limit

- `RequestSizeLimitFilter` runs right after the security filter chain, so a 401 wins over a 413 (`api-errors`, "Error precedence").
- If `Content-Length` exceeds the limit, it answers 413 at once without reading the body. Otherwise it wraps the input stream in a counting stream that throws `PayloadTooLargeException` once byte 262,145 is read. Jackson surfaces that inside `HttpMessageNotReadableException`, and the handler maps it to 413. Nothing is stored, because the controller never runs.
- The limit is `cardigan.web.max-request-body-size: 256KB`. Per-path overrides are a map in the same properties, empty for now.

### Authentication

- `JwtProperties` (`cardigan.security.jwt.secret: ${JWT_SECRET}`, `access-token-ttl: 15m`) decodes the secret in its init block and throws when it's missing, not base64 or shorter than 32 bytes. The message never contains the value.
- `JwtDecoder`: `NimbusJwtDecoder.withSecretKey(key).macAlgorithm(HS256)`, which rejects every other `alg`, including `none`. Spring's default validators are replaced with ours. `ExpiryValidator(clock)` rejects when `!now.isBefore(exp)` with zero skew, `TokenTypeValidator` requires `type == "access"`, and `SubjectValidator` requires a UUID `sub`. Spring already turns off Nimbus's own claim checks.
- `JwtAccessTokenIssuer` (`NimbusJwtEncoder` with the same key) truncates `clock.instant()` to seconds and sets `iat`, `exp = iat + ttl`, `sub` and `type`. It returns the token and `exp` as `expiresAt` (D5, D6).
- `SecurityConfig` in `:app`: stateless sessions, CSRF, form login, HTTP basic, logout and request cache turned off. `/api/v1/**` is `authenticated()`, except `PublicRoutes` entries (method + exact path, empty in this change). Everything else is `permitAll()`, so `/actuator/env` stays a 404 (`service-health`). `oauth2ResourceServer { jwt {} }` uses a custom `AuthenticationEntryPoint`. It writes 401 `UNAUTHENTICATED` through `ProblemResponseWriter` and sets `WWW-Authenticate: Bearer` (D9), for a missing token, a wrong scheme or an invalid token. Query-parameter tokens stay off.
- `requestUserId` reads the `JwtAuthenticationToken` from the security context and returns `UUID.fromString(token.name)`. No database lookup (D3).

### Time

- `ApplicationConfig` provides `Clock.tick(Clock.systemUTC(), Duration.ofMillis(1))` (D7). Every timestamp comes from this clock. Entities don't use `@CreationTimestamp` or `@UpdateTimestamp`, because those read the system time.
- `MutableClock` in `:common` test fixtures replaces the bean as `@Primary` in the test configurations. It also ticks in milliseconds.
- Columns are `timestamptz`. Postgres keeps microseconds, but values are already truncated, so reads equal writes.

### Users

- `user_service.users`: `id uuid primary key`, `email varchar(254) not null unique`, `password_hash varchar not null`, `created_at timestamptz not null`, `updated_at timestamptz not null`.
- Lowercase invariant (D1), enforced twice. The `User` domain model's `init` requires `email == email.lowercase(Locale.ROOT)`. Emails are ASCII, so this equals case folding. The entity also carries a Hibernate `@Check(constraints = "email = lower(email)")`, which `ddl-auto` creates on dev and in tests, and which the baseline migration must repeat. `be-auth` lowercases on register and login. A duplicate surfaces as a unique-constraint violation, which `be-auth` turns into `UniquenessConflictException("email")`.
- `UserRepository` (domain) offers `findById`, `save` and `existsByEmail` for now. `be-auth` adds what register and login need.
- `UserController`: `GET /api/v1/users/me`, `operationId` `getCurrentUser`, tag `Users`, returns `CurrentUserDto(id, email, createdAt)`. When the row is missing, `UserService` throws `CurrentUserNotFoundException`, a subclass of a new `:common` base `UnauthenticatedException`. The handler maps it to 401 `UNAUTHENTICATED` with `WWW-Authenticate: Bearer`, the same as the entry point (D21).

### OpenAPI snapshot

- Dependency `springdoc-openapi-starter-webmvc-api` (no UI). `springdoc.api-docs.enabled: false` in `application.yaml`, so no profile serves `/v3/api-docs` (D15). The snapshot test enables it through a test property on its own context.
- `OpenApiSnapshotTest` in `:app` starts the app (Testcontainers, `test` profile), fetches `/v3/api-docs` and pretty-prints it with sorted keys and a trailing newline. It compares the result with `app/src/test/resources/openapi/openapi.json`. When the system property `openapi.update=true` is set, it writes the file instead of failing. The Gradle task `:app:updateOpenApiSnapshot` runs only this test with that property, and the failure message names the task.
- Customizers in `:common`:
  - Properties with `@Required` are added to the schema's `required` list and marked non-null. Response DTO properties are non-null Kotlin types and come out required.
  - `Problem` and `FieldError` schemas are registered once. `code` is a plain string, not an enum, because a new code is an additive change and generated enums would fail on unknown values. Every operation gets 500 `INTERNAL_ERROR`. Operations behind authentication also get 401 `UNAUTHENTICATED` and the `bearerAuth` security requirement. An `@ApiErrors(status, codes)` annotation adds operation-specific statuses, with the codes listed in the response description.
  - `PublicRoutes` is the single source for both `SecurityConfig` and the security-requirement customizer, so the document can't disagree with the filter chain.
- OpenAPI 3.0.1 (D22), set with `springdoc.api-docs.version: openapi_3_0`.
- operationIds and tags are set explicitly with `@Operation(operationId = ...)` and `@Tag` (D17). The snapshot test also asserts that every operation has a camelCase `operationId` and exactly one tag.

### Tests

| Area | Type | Notes |
|---|---|---|
| Text rules, error ranking, token validators, issuer truncation | Unit | Adversarial cases: emoji, ZWJ sequences, NBSP, lone surrogates, CR-only, boundaries ±1 |
| Error mapping, validation, wire formats, size limit, CORS, JWT | E2E in `:app` | Uses a test-only controller under `/api/v1/test/...`, registered only in the E2E test configuration and absent from the snapshot |
| Users table, `/users/me` | Persistence + E2E | `UserFixtures` + issuer for authenticated calls |
| Startup with a bad secret | `ApplicationContextRunner` | Missing, non-base64, 31 and 32 bytes |

`CLAUDE.md` says the E2E auth helper goes register → login. Until `be-auth` exists, the helper creates the user with `UserFixtures` and mints a token with the real issuer. `be-auth` switches it to register → login.

### Configuration

- `application.yaml`: `cardigan.security.jwt.secret: ${JWT_SECRET}`, `cardigan.security.jwt.access-token-ttl: 15m`, `cardigan.web.max-request-body-size: 256KB`, `springdoc.api-docs.enabled: false`.
- `application-test.yaml`: a fixed base64 test secret, so CI needs no secret.
- Local dev: `JWT_SECRET` must be added to the secret manager behind `.env`. That's a manual step for the user (task 1.3).
- Docker: no Dockerfile change. `JWT_SECRET` is set at runtime like `DB_*`, which `infra-vps` provides.

## Risks / Trade-offs

- [A chunked oversized body with a wrong content type returns 415, not 413, because its size is only known while reading, after the media type check.] → This breaks `api-errors` precedence (413 before 415) only for chunked bodies without `Content-Length`. With `Content-Length`, 413 wins. Reported as a follow-up; a test pins the current behavior.
- [Nullable request DTO fields make the Kotlin types weaker than the contract.] → `@Required` plus the schema customizer. Mappers run only after validation and use `requireNotNull` with the field name, so a missed `@Required` fails loudly as 500 in tests. `CLAUDE.md` records the deviation (D20).
- [A forgotten mapper normalization would store un-normalized text.] → Mapper unit tests per field. Code review checklist entry in `CLAUDE.md`.
- [Rotating `JWT_SECRET` logs every client out within 15 minutes.] → Accepted (D4).
- [The snapshot test needs Docker.] → Already true for every database test.
- [The spring-api-layer skill uses `about:blank` and `ForbiddenException`.] → `CLAUDE.md` wins. Noted in the `CLAUDE.md` update.

## Migration Plan

No migrations. `users` comes from the entity through `ddl-auto` on dev and in tests. The baseline migration before the first production deploy must include it. `JWT_SECRET` must be set in every environment before this change is deployed or run locally.

- [Lowercased emails contradict `domain-user` until the shared spec changes.] → The proposal makes archiving wait for that shared change.
