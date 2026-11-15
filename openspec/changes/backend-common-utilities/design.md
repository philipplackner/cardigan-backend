# Design

## Context

See `proposal.md` for the motivation and `decisions.md` for every settled choice (D1–D23).

Current state:

- Modules `:app`, `:common`, `:user` and `:board` exist with no production code besides `CardiganApplication`. `:common` has only test fixtures (`PostgresTestConfig`).
- Only `GET /actuator/health` is served. There is no security, validation or springdoc dependency.
- Tests use the `test` profile with Hibernate `create-drop` on a Testcontainers Postgres 17.
- Spring Boot 4.1.1, Jackson 3, Kotlin 2.3.21, virtual threads.

## Goals / Non-Goals

**Goals:**

- `be-auth`, `be-boards` and `be-labels` can start in parallel and only add controllers, services, entities, domain exceptions and their own `PublicRoutes` bean. None of them touch the filter chain, the error handler or the OpenAPI setup.
- Every shared-spec behavior listed in `proposal.md` is covered by a test.

**Non-Goals:**

- Refresh tokens, cookies, password hashing, rate limiting (`be-auth`).
- Per-endpoint body limits above 256 KB. The mechanism allows them, but the 5 MB import limit is configured by `be-import-export`.
- Multipart requests. Multipart handling is turned off (see "Request size limit"). `be-import-export` turns it on.
- The Modulith event registry. No events exist yet.

## Decisions

### Package placement

| Piece | Module / package |
|---|---|
| `ErrorCode` enum (code, status, title, detail), `FieldErrorCode` enum, base exceptions (`ResourceNotFoundException`, `UniquenessConflictException(field)`, `UnauthenticatedException`) | `:common` `domain/exception` |
| Text rules: `normalizeText`, `normalizePassword`, `codePointLength`, `checkText(...)` | `:common` `domain/text` (pure Kotlin) |
| `AccessTokenIssuer` interface + `IssuedAccessToken(token, expiresAt)` | `:common` `domain/security` |
| `JwtProperties`, `jwtSecretKey` bean, `JwtDecoder` bean, `JwtAccessTokenIssuer`, claim constants, token validators, `BearerTokenResolver` | `:common` `infra/security` |
| `CommonExceptionHandler`, `problemDetail()`, `ProblemResponseWriter`, `ProblemErrorController`, `RequestRejectedHandler` bean, `requestUserId` | `:common` `api` |
| `@Required`, `@ValidText`, `@ValidUuid`, `@ValidEnum`, their validators, `RequestValidator` | `:common` `api/validation` |
| Jackson customization, `RequestSizeLimitFilter`, `PayloadTooLargeException`, `PublicRoutes` | `:common` `api/web` |
| OpenAPI customizers | `:common` `api/openapi` |
| `SecurityConfig`, `ApplicationConfig` (`Clock`) | `:app` |
| `User`, `UserRepository`, `UserService`, `UserEntity`, `UserController`, `CurrentUserDto` | `:user` (layers per `CLAUDE.md`) |
| `MutableClock`, `DatabaseCleaner`, `application-test.yaml` | `:common` `testFixtures` |
| `UserFixtures` | `:user` `testFixtures` (new `java-test-fixtures` plugin), used by `:app` E2E tests |

`:common` gets `api` dependencies on webmvc, security, oauth2-resource-server, validation and springdoc, so feature modules see the types they need for controllers and constraints.

This placement deviates from the current `CLAUDE.md` layout on purpose: no per-feature exception handlers, public routes in `PublicRoutes` instead of `SecurityConfig`, OpenAPI customizers in `:common`, and no `ForbiddenException`. Task 9.1 updates `CLAUDE.md` to match.

### Error model

- `ErrorCode` holds the closed catalog from `api-errors`: code, HTTP status, fixed title and fixed detail. `type` is derived as `urn:cardigan:problem:` + kebab-case code (D8). `problemDetail(code, fieldErrors = emptyList())` builds a `ProblemDetail`, sets `type`, `title` and `detail` from `ErrorCode`, adds `code` and `fieldErrors` as properties, and leaves `instance` unset. Exception messages, framework default details and request values are never copied into the body. Spring fills `instance` only when it's null and the response goes through `ResponseEntityExceptionHandler`, so the handler explicitly sets it to null before returning. A test asserts the exact field set.
- `CommonExceptionHandler` extends `ResponseEntityExceptionHandler` and overrides `handleExceptionInternal`, so every Spring MVC exception goes through one mapping:

  | Exception | Code |
  |---|---|
  | `NoResourceFoundException`, `HttpRequestMethodNotSupportedException` | `ROUTE_NOT_FOUND` |
  | `HttpMediaTypeNotSupportedException` | `UNSUPPORTED_MEDIA_TYPE` |
  | `HttpMessageNotReadableException` (cause chain contains `PayloadTooLargeException`), bare `PayloadTooLargeException` | `PAYLOAD_TOO_LARGE` |
  | `HttpMessageNotReadableException` (other) | `MALFORMED_REQUEST` |
  | `MethodArgumentTypeMismatchException` on a `UUID` path variable | `NOT_FOUND` |
  | `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `RequestValidationException` | `VALIDATION_FAILED` |
  | `ResourceNotFoundException` | `NOT_FOUND` |
  | `UniquenessConflictException` | `CONFLICT` with `ALREADY_USED` on its field |
  | `UnauthenticatedException` | `UNAUTHENTICATED`, with `WWW-Authenticate: Bearer` |
  | anything else | `INTERNAL_ERROR`, logged at `ERROR` with stack trace |

- The `Accept` header is ignored (D23). Content negotiation is set to ignore the header and default to JSON, so `HttpMediaTypeNotAcceptableException` never comes up. Error bodies are always written as `application/problem+json`.
- `ProblemResponseWriter` serializes the same body for code that runs outside MVC: the security entry point, the size-limit filter, the firewall rejection handler and the error controller. It writes `application/problem+json` with the app's `JsonMapper`, and always includes `fieldErrors`, also when it's empty.
- A `RequestRejectedHandler` bean answers Spring Security firewall rejections (paths with `;`, `//`, encoded slashes and similar) with 404 `ROUTE_NOT_FOUND` through `ProblemResponseWriter`.
- `ProblemErrorController` replaces Boot's `BasicErrorController` and writes problem+json for every error dispatch: 404 → `ROUTE_NOT_FOUND`, 413 → `PAYLOAD_TOO_LARGE`, 400 → `MALFORMED_REQUEST`, everything else → `INTERNAL_ERROR`, logged with the stack trace. This covers exceptions thrown in filters. Requests that Tomcat rejects before Spring (malformed request line or headers) are out of scope.
- Spring MVC's implicit HEAD and OPTIONS handling is turned off for `/api/v1`, so those methods raise `HttpRequestMethodNotSupportedException` unless an endpoint declares them.
- No 403 handler and no `ForbiddenException`. This deviates from the `spring-api-layer` skill on purpose (`api-errors`, "Other users' data is not found, never forbidden").
- Feature modules add domain exceptions as subclasses of the three base exceptions. They don't add handlers unless a new top-level code is added to the shared catalog.

### Validation and field errors

- Request DTO properties are nullable with no default (D11). `@field:Required` marks the ones the endpoint needs. Its validator fails on `null` with code `REQUIRED`, and for strings `@ValidText` also reports `REQUIRED` when the normalized text is empty and `min ≥ 1`.
- `@field:ValidText(min, max, multiline = false)` normalizes the value with `normalizeText` and checks, in order: `INVALID_CHARACTER`, `TOO_SHORT`, `TOO_LONG`. The validator puts the code and `min`/`max` into the Hibernate Validator dynamic payload.
- Values of the right JSON type that don't convert are field errors, not parse errors. Request DTOs declare UUID fields as `String` with `@ValidUuid` (`INVALID_FORMAT`), enum fields as `String` with `@ValidEnum(XDto::class)` (`INVALID_VALUE`), and positions and other bounded integers as `BigInteger` with a range check (`OUT_OF_RANGE`). Mappers convert them after validation. The OpenAPI customizer still emits `format: uuid`, the enum values, and `integer` for these fields.
- Validation is an explicit step, so the `api-errors` precedence holds. `RequestValidator.validate(request)` in `:common` throws `RequestValidationException`, which maps to `VALIDATION_FAILED` like `@Valid` failures. Endpoints with path or body references call it after the service has resolved the references, so a missing reference (404) wins over field errors. `@Valid` is used only on endpoints with no references. `@RequestBody` is declared before any `@PathVariable`, so body parsing (400 `MALFORMED_REQUEST`) comes before path conversion (404).
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
- Spring Boot's `FormContentFilter` is turned off with `spring.mvc.formcontent.filter.enabled: false`. It runs before the security chain and reads PUT, PATCH and DELETE form bodies into memory with no limit, so it would bypass both authentication and this filter. The API accepts only JSON. No filter ordered before `RequestSizeLimitFilter` may read the request body.
- Multipart handling is turned off with `spring.servlet.multipart.enabled: false` until an endpoint needs it. Tomcat parses multipart parts from its own buffer, so the counting stream would never see them. `be-import-export` must turn it on only together with a per-path limit enforced before parsing.
- If `Content-Length` exceeds the limit, it answers 413 at once without reading the body. Otherwise it wraps the input stream in a counting stream that throws `PayloadTooLargeException` once byte 262,145 is read. `PayloadTooLargeException` extends `java.io.IOException`, so Jackson wraps it (in Jackson 3 inside `JacksonIOException`) and Spring surfaces it inside `HttpMessageNotReadableException`. The handler searches the whole cause chain for it and maps it to 413. Nothing is stored, because the controller never runs.
- The limit is `cardigan.web.max-request-body-size: 256KB`. Per-path overrides are a map in the same properties, empty for now.

### Authentication

- `JwtProperties` (`cardigan.security.jwt.secret: ${JWT_SECRET}`, `access-token-ttl: 15m`) binds the secret as a plain `String` and does no checks during binding, so no `BindException` and no failure analysis can carry the value. A separate `@Bean` factory `jwtSecretKey` checks it after binding: it trims leading and trailing whitespace, decodes with `Base64.getDecoder()` (standard alphabet, RFC 4648 section 4, optional padding, no inner whitespace, no URL-safe alphabet), and throws an `IllegalStateException` whose message names the problem (missing, not base64, too short) and never the value. An unresolved `${JWT_SECRET}` placeholder is treated as missing.
- `JwtDecoder`: `NimbusJwtDecoder.withSecretKey(key).macAlgorithm(HS256)`, which rejects every other `alg`, including `none` and HS384/HS512. Spring's default validators are replaced with ours. `ExpiryValidator(clock)` rejects when `exp` is missing or `!now.isBefore(exp)`, with zero skew. `TokenTypeValidator` requires `type == "access"`, and `SubjectValidator` requires a UUID `sub`. A missing claim fails its validator. Spring already turns off Nimbus's own claim checks.
- `JwtAccessTokenIssuer` (`NimbusJwtEncoder` with the same key) truncates `clock.instant()` to seconds and sets `iat`, `exp = iat + ttl`, `sub` and `type`. It returns the token and `exp` as `expiresAt` (D5, D6).
- `PublicRoutes` is a bean type holding `(HttpMethod, path)` entries. Each module contributes its own `PublicRoutes` bean (`be-auth` adds one in `:user`). `SecurityConfig`, the `BearerTokenResolver` and the OpenAPI customizer all inject `List<PublicRoutes>` and merge the entries, so the document can't disagree with the filter chain. This change registers none.
- `SecurityConfig` in `:app`: stateless sessions, CSRF, form login, HTTP basic, logout and request cache turned off. `/api/v1/**` is `authenticated()`, except `PublicRoutes` entries (method + exact path). Everything else is `permitAll()`, so `/actuator/env` stays a 404 (`service-health`). `oauth2ResourceServer { jwt {} }` uses a custom `AuthenticationEntryPoint`. It writes 401 `UNAUTHENTICATED` through `ProblemResponseWriter` and sets `WWW-Authenticate: Bearer` (D9), for a missing token, a wrong scheme or an invalid token. Query-parameter tokens stay off.
- A custom `BearerTokenResolver` returns null for `PublicRoutes` entries and for paths outside `/api/v1`, so the bearer filter skips them and a stale `Authorization` header never causes a 401 there.
- Because everything outside `/api/v1` is `permitAll()`, authentication depends on every controller using the right prefix. A test in `:app` reads every pattern from `RequestMappingHandlerMapping` and fails when one doesn't start with `/api/v1/`, except handlers registered by the test configuration. Actuator isn't a `RequestMappingHandlerMapping` handler.
- `requestUserId` reads the `JwtAuthenticationToken` from the security context and returns `UUID.fromString(token.name)`. No database lookup (D3).

### Time

- `ApplicationConfig` provides `Clock.tick(Clock.systemUTC(), Duration.ofMillis(1))` (D7). Every timestamp comes from this clock. Entities don't use `@CreationTimestamp` or `@UpdateTimestamp`, because those read the system time. A unit test reads the production bean 1000 times and asserts a zero sub-millisecond part and the UTC zone.
- `MutableClock` in `:common` test fixtures replaces the bean as `@Primary` in the test configurations. It also ticks in milliseconds. It lives only in test fixtures, never on the main runtime classpath.
- Columns are `timestamptz`. Postgres keeps microseconds, but values are already truncated, so reads equal writes.

### Users

- `user_service.users`: `id uuid primary key`, `email varchar(254) not null unique`, `password_hash varchar not null`, `created_at timestamptz not null`, `updated_at timestamptz not null`.
- Lowercase invariant (D1), enforced twice. The `User` domain model's `init` requires `email == email.lowercase(Locale.ROOT)`. Emails are ASCII, so this equals case folding. The entity also carries a Hibernate `@Check(constraints = "email = lower(email)")`, which `ddl-auto` creates on dev and in tests, and which the baseline migration must repeat. `be-auth` lowercases on register and login. A duplicate surfaces as a unique-constraint violation, which `be-auth` turns into `UniquenessConflictException("email")`.
- `UserRepository` (domain) offers `findById`, `save` and `existsByEmail` for now. `be-auth` adds what register and login need.
- `UserController`: `GET /api/v1/users/me`, `operationId` `getCurrentUser`, tag `Users`, returns `CurrentUserDto(id, email, createdAt)`. When the row is missing, `UserService` throws `CurrentUserNotFoundException`, a subclass of a new `:common` base `UnauthenticatedException`. The handler maps it to 401 `UNAUTHENTICATED` with `WWW-Authenticate: Bearer`, the same as the entry point (D21).

### OpenAPI snapshot

- Dependency `springdoc-openapi-starter-webmvc-api` (no UI). `springdoc.api-docs.enabled: false` in `application.yaml`, so no profile serves `/v3/api-docs` (D15). The snapshot test enables it through a test property on its own context.
- `OpenApiSnapshotTest` in `:app` starts the app (Testcontainers, `test` profile), fetches `/v3/api-docs` and pretty-prints it with sorted keys and a trailing newline. It compares the result with `app/src/test/resources/openapi/openapi.json`. When the system property `openapi.update=true` is set, it writes the file instead of failing. The Gradle task `:app:updateOpenApiSnapshot` runs only this test with that property, and the failure message names the task.
- The `OpenAPI` bean sets exactly one server, `{ "url": "/" }`, so no value in the document depends on the host or port.
- Customizers in `:common`:
  - All request DTO properties are marked non-null, and only `@Required` ones are added to the schema's `required` list. Response DTO properties are non-null Kotlin types and come out required.
  - `Problem` and `FieldError` schemas are registered once. `code` is a plain string, not an enum, because a new code is an additive change and generated enums would fail on unknown values. Every operation gets 500 `INTERNAL_ERROR`. Operations behind authentication also get 401 `UNAUTHENTICATED` and the `bearerAuth` security requirement. Operations with a request body also get 400 `MALFORMED_REQUEST`, 413 `PAYLOAD_TOO_LARGE` and 415 `UNSUPPORTED_MEDIA_TYPE`. An `@ApiErrors(status, codes)` annotation adds operation-specific statuses, with the codes listed in the response description.
  - The security-requirement customizer reads the merged `PublicRoutes` entries (see "Authentication").
- OpenAPI 3.0.1 (D22), set with `springdoc.api-docs.version: openapi_3_0`.
- operationIds and tags are set explicitly with `@Operation(operationId = ...)` and `@Tag` (D17). The snapshot test also asserts that every `operationId` is camelCase and unique, every operation has exactly one PascalCase plural tag, and no path starts with `/api/v1/test`.
- Customizer tests (required/optional request fields, `@ApiErrors`) generate a document from a separate test context with a test-only DTO, because this change has no production request DTO.

### Tests

| Area | Type | Notes |
|---|---|---|
| Text rules, error ranking, token validators, issuer truncation, production clock | Unit | Adversarial cases: emoji, ZWJ sequences, NBSP, lone surrogates, CR-only, boundaries ±1. Tokens with missing `exp`, `type` or `sub`, and HS512 |
| Error mapping, validation, wire formats, size limit, CORS, JWT | E2E in `:app` | Uses a test-only controller under `/api/v1/test/...`, including `GET /api/v1/test/whoami` that returns `requestUserId`, and a test-only filter that throws on `/api/v1/test/boom-filter`. The controller has no stereotype annotation and is declared as a `@Bean` in a `@TestConfiguration` that only the E2E base class imports, so the snapshot context never sees it |
| Users table, `/users/me` | Persistence + E2E | `UserFixtures` + issuer for authenticated calls |
| Startup with a bad secret | `SpringApplication` with `OutputCaptureExtension` | Missing, non-base64, base64 plus trailing newline, 31 and 32 bytes. The captured output, including the failure analysis, never contains the value. A separate test loads the main `application.yaml` without the test profile and without `JWT_SECRET` and asserts that startup fails |
| Controller mappings | E2E in `:app` | Every `RequestMappingHandlerMapping` pattern starts with `/api/v1/` |

`CLAUDE.md` says the E2E auth helper goes register → login. Until `be-auth` exists, the helper creates the user with `UserFixtures` and mints a token with the real issuer. `be-auth` switches it to register → login.

### Configuration

- `application.yaml`: `cardigan.security.jwt.secret: ${JWT_SECRET}`, `cardigan.security.jwt.access-token-ttl: 15m`, `cardigan.web.max-request-body-size: 256KB`, `springdoc.api-docs.enabled: false`, `spring.mvc.formcontent.filter.enabled: false`, `spring.servlet.multipart.enabled: false`.
- `application-test.yaml` moves from `app/src/test/resources` to `:common` `testFixtures` resources, so module tests and `:app` E2E tests share one copy. It holds the existing test-profile settings plus a fixed base64 test secret, so CI needs no secret. `:app` keeps no copy of its own.
- Local dev: `JWT_SECRET` must be added to the secret manager behind `.env`. That's a manual step for the user (task 1.3).
- Docker: no Dockerfile change. `JWT_SECRET` is set at runtime like `DB_*`, which `infra-vps` provides.

## Risks / Trade-offs

- [A chunked oversized body is only counted while something reads it.] → With a wrong content type it returns 415, not 413, because the media type check comes first. Sent to an unknown route or to an endpoint that reads no body, it gets 404 or the endpoint's normal response instead of 413. This breaks `api-errors` precedence (413 before 415 and 404) only for chunked bodies without `Content-Length`. With `Content-Length`, 413 always wins. Reported as a follow-up; tests pin the current behavior.
- [Firewall rejections happen before authentication.] → `GET /api/v1/users/me;x=1` without a token returns 404 `ROUTE_NOT_FOUND`, not 401. Accepted, because the path never reaches a route.
- [Nullable request DTO fields make the Kotlin types weaker than the contract.] → `@Required` plus the schema customizer. Mappers run only after validation and use `requireNotNull` with the field name, so a missed `@Required` fails loudly as 500 in tests. `CLAUDE.md` records the deviation (D20).
- [UUID, enum and position fields are `String` or `BigInteger` in request DTOs.] → The schema customizer keeps the real formats in the document, and mappers convert after validation.
- [A forgotten mapper normalization would store un-normalized text.] → Mapper unit tests per field. Code review checklist entry in `CLAUDE.md` (task 9.1).
- [Rotating `JWT_SECRET` logs every client out within 15 minutes.] → Accepted (D4).
- [The snapshot test needs Docker.] → Already true for every database test.
- [The spring-api-layer skill uses `about:blank`, `ForbiddenException` and the `/api/...` base path.] → `CLAUDE.md` wins. Noted in the `CLAUDE.md` update, and the mapping-prefix test catches a wrong base path.

## Migration Plan

No migrations. `users` comes from the entity through `ddl-auto` on dev and in tests. The baseline migration before the first production deploy must include it. `JWT_SECRET` must be set in every environment before this change is deployed or run locally.

- [Lowercased emails contradict `domain-user` until the shared spec changes.] → The proposal makes archiving wait for that shared change.
