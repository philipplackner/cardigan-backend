# Artifact review: backend-common-utilities

**Verdict:** Ready
**Reviewed:** 2026-11-15, schema `careful-planning`, repo kind `code`

Counts are per reviewer, before merging duplicates. After merging: 1 blocker, 23 major, 10 minor (34 findings).

| Reviewer | Blocker | Major | Minor |
|---|---|---|---|
| Testability | 0 | 10 | 6 |
| Security | 1 | 4 | 1 |
| Hidden assumptions | 0 | 6 | 7 |
| Design contract | 0 | 5 | 2 |

## Conflicts between reviewers

### C1: Are optional request properties nullable in the document? (T7 vs A9)
- **T7** proposes the scenario "`note` is not under `required` and is `nullable: true`" (recommended there, with "optional, not nullable" named as the alternative).
- **A9** proposes "Optional request properties SHALL NOT be marked nullable either, because `null` is rejected with `REQUIRED` (`api-conventions`, "Null and empty updates")", and a customizer that marks every request property non-null.
- They can't both apply: the snapshot either marks `note` nullable or not. A9's reasoning ties it to a shared rule; T7 leaves it as a user choice.
- **User decision:** A9. Optional request properties are neither required nor nullable.

### C2: Multipart handling (S2 vs A12)
- **S2** turns multipart off (`spring.servlet.multipart.enabled: false`) until `be-import-export` needs it, and that chunk must add a per-path limit enforced before parsing.
- **A12** keeps multipart on, states that the counting wrapper does not cover it, has `be-import-export` set `max-file-size` / `max-request-size`, and maps `MaxUploadSizeExceededException` to `PAYLOAD_TOO_LARGE`.
- They disagree on whether multipart is enabled in this change. Both agree the wrapper doesn't cover it.
- **User decision:** S2. Multipart is turned off until `be-import-export` needs it.

### C3: Status for requests rejected by Spring Security's firewall (S6 vs A4, merged with T5)
- **S6** maps firewall rejections to 400 `MALFORMED_REQUEST` (404 as the alternative).
- **A4** maps them to 404 `ROUTE_NOT_FOUND` (400 as the alternative).
- Both add an `ErrorController` replacing Boot's `/error`. T5 adds the "Failure in a filter" scenario, which fits either choice.
- **User decision:** A4. Firewall rejections get 404 `ROUTE_NOT_FOUND`.

## Findings

### S1 / A5: FormContentFilter reads unauthenticated form bodies without limit before security and the size filter
**Status:** applied
- Severity: blocker (S1; A5 rated it major). Unauthenticated memory-exhaustion DoS on every path. The shared 256 KB limit is bypassed, and every planned test still passes.
- Location: design.md > Request size limit
- Current text: "`RequestSizeLimitFilter` runs right after the security filter chain, so a 401 wins over a 413 (`api-errors`, \"Error precedence\")."
- Problem: Spring Boot auto-registers `OrderedFormContentFilter` (order -9900) unless `spring.mvc.formcontent.filter.enabled=false`. It runs before Spring Security (-100). For PUT, PATCH and DELETE with `application/x-www-form-urlencoded`, it reads the whole body into a String with no limit. The design doesn't mention it.
- Failure scenario: many concurrent anonymous chunked `PUT /api/v1/users/me` requests with a form content type and bodies of hundreds of MB. Each one is fully buffered on the heap before the 401 is sent. Virtual threads don't limit concurrency, so the JVM runs out of memory.
- Proposed fix: design.md > Request size limit, after the first bullet: "Spring Boot's `FormContentFilter` is turned off with `spring.mvc.formcontent.filter.enabled: false`. It runs before the security chain and reads PUT, PATCH and DELETE form bodies into memory with no limit. The API accepts only JSON. No filter before `RequestSizeLimitFilter` may read the request body." Add the property to design.md > Configuration and to task 1.2. Add to task 5.2: "an unauthenticated chunked 300 KB `PUT` with `Content-Type: application/x-www-form-urlencoded` gets 401, an authenticated one gets 413, and a context test asserts no `FormContentFilter` bean exists".
- Upstream: no

### S2 / A12: Multipart bodies bypass the counting stream — see conflict C2
**Status:** applied
- Severity: major (S2; A12 rated it minor). The 256 KB limit and "stop reading once the limit is passed" don't hold for multipart. Up to 10 MB per request is parsed to temp disk, including on unauthenticated paths outside `/api/v1`.
- Location: design.md > Request size limit; design.md > Goals / Non-Goals
- Current text: "Otherwise it wraps the input stream in a counting stream that throws `PayloadTooLargeException` once byte 262,145 is read." / "Per-endpoint body limits above 256 KB. The mechanism allows them, but the 5 MB import limit is configured by `be-import-export`."
- Problem: Boot's `MultipartAutoConfiguration` is on by default. `DispatcherServlet.checkMultipart` calls `getParts()`, and Tomcat reads from its own buffer instead of the wrapped `getInputStream()`. `MaxUploadSizeExceededException` falls into "anything else" (500).
- Failure scenario: a chunked 5 MB `multipart/form-data` POST to `/anything` is parsed and stored before the 404 or 415. A 2 MB import later returns 500.
- Proposed fix: S2 says to turn multipart off. A12 says to keep it, document the gap and add a `MaxUploadSizeExceededException` → `PAYLOAD_TOO_LARGE` row. See C2.
- Upstream: no

### S3: Bad-secret startup failure can print JWT_SECRET; the planned test can't see it
**Status:** applied
- Severity: major. A likely misconfiguration (trailing newline) leaks the signing secret, or a value one character from it, to logs.
- Location: design.md > Authentication; tasks.md > 6.1
- Current text: "`JwtProperties` (...) decodes the secret in its init block and throws when it's missing, not base64 or shorter than 32 bytes. The message never contains the value." / "Verify with `ApplicationContextRunner` tests: missing, non-base64 and 31-byte secrets fail with no secret in the message"
- Problem: throwing during `@ConfigurationProperties` binding produces a `BindException`. On a real startup, `BindFailureAnalyzer` logs `Property`, `Value: "<raw value>"` and `Origin`. `ApplicationContextRunner` doesn't run failure analyzers.
- Failure scenario: `JWT_SECRET` stored with a trailing newline fails base64 decoding, and the console shows the full secret.
- Proposed fix: design.md > Authentication: "`JwtProperties` binds the secret as a plain `String` and does no checks during binding. A separate `@Bean` factory (`jwtSecretKey`) decodes and checks it after binding and throws an `IllegalStateException` whose message names the problem and never the value." Task 6.1: verify through `SpringApplication` with `OutputCaptureExtension` (missing, non-base64, base64 plus trailing newline, 31 bytes). The captured output must not contain the value. A 32-byte secret starts.
- Upstream: no

### S4: `detail` is only fixed for MALFORMED_REQUEST and INTERNAL_ERROR
**Status:** applied
- Severity: major. Spring's default details and exception messages echo the path, method, content type or IDs. This breaks shared `api-errors` "Error text is for developers only".
- Location: specs/error-responses/spec.md > Malformed bodies hide their content; design.md > Error model
- Current text: "...SHALL produce 400 `MALFORMED_REQUEST` with a fixed `detail` that names neither the field, the value nor any class." / "`problemDetail(code, detail, fieldErrors = emptyList())` builds a `ProblemDetail`"
- Problem: `problemDetail` takes a free `detail`. The natural implementation copies `ex.body.detail` or a domain exception message.
- Failure scenario: `be-boards` throws `BoardNotFoundException("Board $id not found")`, and the handler forwards the message to the client.
- Proposed fix: rename the requirement to "Error details are fixed per code": "The `detail` of every error body SHALL be a fixed English text chosen per top-level code. It SHALL NOT be taken from framework or domain exception messages and SHALL NOT contain any part of the request." Add the scenarios "Unknown path is not echoed" (`GET /api/v1/zz-marker-zz`) and "Media type is not echoed" (`Content-Type: text/zz-marker`). design.md: `problemDetail(code, fieldErrors = emptyList())`, with `detail` taken from `ErrorCode`, plus "Exception messages are never copied into the body."
- Upstream: no

### S5: Routes outside `/api/v1` fail open
**Status:** applied
- Severity: major. A controller mapped outside `/api/v1` runs without authentication. The `spring-api-layer` skill shows `/api/...`, which makes this mistake likely.
- Location: design.md > Authentication (`SecurityConfig` bullet); tasks.md > 6.3
- Current text: "Everything else is `permitAll()`, so `/actuator/env` stays a 404 (`service-health`)."
- Problem: authentication depends on every controller using the right prefix, and nothing checks the prefixes.
- Failure scenario: `be-labels` maps `@RequestMapping("/api/labels")`, and its endpoints run without a token.
- Proposed fix: design.md > Authentication: "A test in `:app` reads every pattern from `RequestMappingHandlerMapping` and fails when one doesn't start with `/api/v1/`, except handlers registered by the test configuration." Add the same check to task 6.3.
- Upstream: no

### D1: Change breaks the main spec `module-boundaries` ("No placeholder classes") with no delta
**Status:** applied
- Severity: major. After archive, the main spec says `:common` and `:user` have no source files, which is false.
- Location: openspec/specs/module-boundaries/spec.md > Module layer packages exist from the start > No placeholder classes. No delta exists in this change.
- Current text: "**WHEN** the `:user`, `:board` and `:common` main source folders are inspected / **THEN** they contain no source files"
- Problem: this change adds production code to `:common` and `:user`. The proposal's "Modified Capabilities" lists only `service-health`.
- Failure scenario: verifying `module-boundaries` after archive fails, and a later change could treat the code as placeholders.
- Proposed fix: add `specs/module-boundaries/spec.md` with a MODIFIED "Module layer packages exist from the start". Keep "A package with no real code SHALL hold no placeholder classes", and narrow "No placeholder classes" to `:board`. Add `module-boundaries` to proposal.md "Modified Capabilities".
- Upstream: no

### D2: Placement of cross-cutting pieces contradicts CLAUDE.md, and task 9.1 doesn't fix the rules
**Status:** applied
- Severity: major. Parallel chunks start from CLAUDE.md (D20) and would build conflicting things.
- Location: design.md > Package placement, Error model; tasks.md > 9.1
- Current text: "Feature modules add domain exceptions as subclasses of the two base exceptions. They don't add handlers unless a new top-level code is added to the shared catalog."
- Problem: CLAUDE.md says to map failures in `<Feature>ExceptionHandler`, put public routes in `:app` `SecurityConfig` and OpenAPI config in `:app` `config/`, and lists `ForbiddenException, UnauthorizedException`. The design changes all of these. Task 9.1 covers none of them, nor the "Code review checklist entry in `CLAUDE.md`" promised under Risks.
- Failure scenario: `be-boards` adds a `BoardExceptionHandler` with a 403 mapping. `be-auth` adds `permitAll` rules to `SecurityConfig`, so OpenAPI marks login as requiring `bearerAuth`.
- Proposed fix: extend task 9.1 to update "Where does X go?" and the module layouts. That covers: base-exception subclasses with no feature handler, public routes in `PublicRoutes`, OpenAPI customizers in `:common` `api/openapi`, no `ForbiddenException`, and the checklist entry "mappers call `normalizeText` for every text field". Add to design.md > Package placement: "This placement deviates from the current CLAUDE.md layout on purpose. Task 9.1 updates CLAUDE.md to match."
- Upstream: no

### D3: How `be-auth` adds a public route through `PublicRoutes` is not defined
**Status:** applied
- Severity: major. It is the one extension point `be-auth` needs from this change.
- Location: design.md > Authentication; OpenAPI snapshot
- Current text: "`PublicRoutes` is the single source for both `SecurityConfig` and the security-requirement customizer, so the document can't disagree with the filter chain."
- Problem: the design doesn't say whether it's a fixed `:common` list or a bean type that each module contributes. The Goals don't include editing a `:common` list.
- Failure scenario: `be-auth` registers its own `PublicRoutes` bean, and `SecurityConfig` then fails with two candidates or reads only one.
- Proposed fix: "`PublicRoutes` is a bean type holding `(HttpMethod, path)` entries. Each module contributes its own. `SecurityConfig` and the OpenAPI customizer inject `List<PublicRoutes>`. This change registers none." Alternative: a single `object PublicRoutes` that `be-auth` edits, with "editing `PublicRoutes`" added to the Goals.
- Upstream: no

### D4 / T2 / A6: The oversized-body scenario depends on transfer encoding and on whether the endpoint reads the body
**Status:** applied
- Severity: major (D4, T2; A6 rated it minor)
- Location: specs/error-responses/spec.md > Errors raised outside controllers use the same body > Oversized body; design.md > Request size limit, Risks
- Current text: "**WHEN** an authenticated client sends a 300 KB body to any `/api/v1` path / **THEN** the response is 413 `PAYLOAD_TOO_LARGE`"
- Problem: a chunked body is counted only when something reads it. `GET /api/v1/users/me` or an unknown path never reads it, so the result is 200 or 404. Shared `api-errors` precedence puts 413 before 404. Risks lists only the chunked plus wrong media type case.
- Failure scenario: a test sends a chunked 300 KB body to an unknown path and gets 404.
- Proposed fix (T2's split, compatible with D4 and A6): "Oversized body with Content-Length — any `/api/v1` path → 413" and "Oversized chunked JSON body — an endpoint that reads a JSON body → 413". Extend the design Risks with the unread-body case and pin it with a test in task 5.2 (D4). Alternative (A6): the filter drains every body up to limit + 1 byte, which matches D10's "counted on the bytes received, including chunked bodies" more closely but costs I/O on every request.
- Upstream: no

### D5 / A7: `PayloadTooLargeException` mapping depends on an unstated exception type and the exact cause chain
**Status:** applied
- Severity: major (D5; A7 rated it minor)
- Location: design.md > Error model table; Request size limit
- Current text: "| `HttpMessageNotReadableException` (cause is `PayloadTooLargeException`) | `PAYLOAD_TOO_LARGE` |" / "Jackson surfaces that inside `HttpMessageNotReadableException`"
- Problem: only an `IOException` gets wrapped, and Jackson 3 wraps it in `JacksonIOException`, so it isn't the direct `cause`. A `RuntimeException` passes through and ends up as 500.
- Failure scenario: a chunked 300 KB test returns 500 `INTERNAL_ERROR`, or 400 `MALFORMED_REQUEST`.
- Proposed fix: "`PayloadTooLargeException` extends `IOException`. The handler maps `HttpMessageNotReadableException` to `PAYLOAD_TOO_LARGE` when any exception in its cause chain is a `PayloadTooLargeException`, and also maps a bare `PayloadTooLargeException`." In the table, change the row to "(cause chain contains `PayloadTooLargeException`)".
- Upstream: no

### T1: "Token of a user with no row" can't tell authentication success apart from the endpoint's 401
**Status:** applied
- Severity: major. The scenario exists to prove D3 (no DB lookup during authentication), but a wrong implementation passes it.
- Location: specs/request-authentication/spec.md > The request user comes from the token subject > Token of a user with no row
- Current text: "**THEN** authentication succeeds, and the endpoint decides the outcome"
- Problem: this isn't observable. `/users/me` returns the same 401 that a lookup-based filter would return.
- Failure scenario: a user lookup is added to the JWT converter, and every test still passes.
- Proposed fix: "**WHEN** a valid access token names a user ID that has no user record and calls an authenticated endpoint that only returns the request user ID / **THEN** the response is 200 and contains that user ID". Add `GET /api/v1/test/whoami` to the test-only controller (design.md > Tests).
- Upstream: no
- Test sketch: E2E. Mint a token for a random UUID and call whoami. Passes on 200 with the UUID. A lookup in the converter gives 401.

### T3: The malformed-body scenario can't pass as written ("does not contain `a`")
**Status:** applied
- Severity: major
- Location: specs/error-responses/spec.md > Malformed bodies hide their content > Number expected
- Current text: "**THEN** the response is 400 `MALFORMED_REQUEST` and `detail` does not contain `position`, `a` or a class name"
- Problem: almost any English sentence contains `a`, and "a class name" isn't defined. The "fixed `detail`" isn't tested.
- Failure scenario: the tester drops the `a` check, and a handler that echoes Jackson's message passes.
- Proposed fix: use the value `zq9x`, and say `detail` contains none of `position`, `zq9x`, `Exception`, `jackson`, `kotlin.` or `java.`. Add "Detail is fixed": invalid JSON and a wrong value type give the same `detail`. Compatible with S4.
- Upstream: no
- Test sketch: E2E. Send both bodies to the test endpoint. The details must be equal and contain none of the tokens.

### T4: Error bodies written outside the MVC handler are never checked for their exact field set
**Status:** applied
- Severity: major. Clients treat `fieldErrors` as required.
- Location: specs/error-responses/spec.md > Errors raised outside controllers use the same body > Missing token
- Current text: "**THEN** the response has content type `application/problem+json` and the code `UNAUTHENTICATED`"
- Problem: only the `ROUTE_NOT_FOUND` scenario checks the exact field set, and it goes through `CommonExceptionHandler`, not `ProblemResponseWriter`.
- Failure scenario: the writer leaves out empty `fieldErrors`, and the mobile client then fails to parse every 401.
- Proposed fix: the "Missing token" THEN gets "...and exactly the fields `type`, `title`, `status`, `detail`, `code` and an empty `fieldErrors`". Add the same clause to the `Content-Length` 413 scenario.
- Upstream: no
- Test sketch: E2E. The key set equals the six names, and `fieldErrors == []`.

### T5 / S6 / A4: Errors before Spring MVC (filter failures, firewall rejections, `/error`) bypass problem+json — see conflict C3
**Status:** applied
- Severity: major (T5; S6 and A4 rated it minor)
- Location: specs/error-responses/spec.md > Errors raised outside controllers use the same body; design.md > Error model (`ProblemResponseWriter` bullet)
- Current text: "Errors produced before a request reaches an endpoint SHALL use the same content type and body as errors from endpoints. This covers authentication, unknown routes and methods, body size, media type, unparseable bodies and unexpected failures." / "`ProblemResponseWriter` serializes the same body for code that runs outside MVC: the security entry point and the size-limit filter."
- Problem: an exception thrown in a filter goes to Boot's `BasicErrorController` (`timestamp`, `error`, `path`). `StrictHttpFirewall` rejects paths containing `;`, `//`, encoded slashes and similar outside the problem writer, and the response may echo the path.
- Failure scenario: `GET /api/v1/users/me;x=1` gets a non-problem body, and a filter bug returns Boot's JSON with no `code`.
- Proposed fix: add the scenario "Failure in a filter → 500 `INTERNAL_ERROR` problem+json with exactly the documented fields" (T5), and a "Rejected path" scenario (A4). In design.md, add a `RequestRejectedHandler` bean writing through `ProblemResponseWriter`, and an `ErrorController` replacing `/error` (404 → `ROUTE_NOT_FOUND`, 413 → `PAYLOAD_TOO_LARGE`, 400 → `MALFORMED_REQUEST`, else `INTERNAL_ERROR`). The firewall status is open: 400 (S6) or 404 (A4). Requests Tomcat rejects before Spring are out of scope. Add an E2E case to task 3.2.
- Upstream: no
- Test sketch: E2E with a test filter that throws. The response is a 500 problem+json, not Boot's body.

### T6: Millisecond precision of production time is never tested, because tests replace the clock with a truncating `MutableClock`
**Status:** applied
- Severity: major
- Location: specs/backend-time/spec.md > Timestamps have millisecond precision from creation; tasks.md > 2.1
- Current text: "**WHEN** a user record is created at `2026-11-15T09:15:30.123456Z`"
- Problem: `MutableClock` "also ticks in milliseconds", so the scenario tests the fixture, not `ApplicationConfig`.
- Failure scenario: `ApplicationConfig` returns `Clock.systemUTC()`, and every test still passes.
- Proposed fix: add the scenario "Production clock precision: every instant has a zero sub-millisecond part, its zone is UTC, and it is within 1 s of the system time". Add a unit test on `ApplicationConfig().clock()` (1000 reads, `nano % 1_000_000 == 0`) to task 2.1. Optionally make `MutableClock` store exactly what it is given.
- Upstream: no
- Test sketch: unit test on the production bean.

### T7: The required/optional request-field rules can't be checked on the snapshot — nullability part conflicts with A9 (C1)
**Status:** applied
- Severity: major. The `@Required` customizer is first used in parallel by wave-3 chunks.
- Location: specs/api-contract-snapshot/spec.md > Required request fields are documented as required > Required name
- Current text: "**WHEN** a request body requires `name` **THEN** its schema lists `name` under `required` and `name` is not nullable"
- Problem: this change has no production request DTO, and the test controller is excluded from the snapshot. Optional fields have no scenario, and nothing checks that test endpoints stay out of the snapshot.
- Failure scenario: the customizer marks every property as required, and nothing fails until `be-auth` ships.
- Proposed fix: add a "Required and optional request fields" scenario against a test DTO in a separate springdoc test context (`note` nullability per C1). Add "Test endpoints stay out of the snapshot: no path starts with `/api/v1/test`". Add "Customizer tests generate a document from a separate test context with a test-only DTO" to design.md > Tests.
- Upstream: no
- Test sketch: integration (springdoc on a test context).

### T8: "Every error status it can return" contradicts the `getCurrentUser` scenario
**Status:** applied
- Severity: major
- Location: specs/api-contract-snapshot/spec.md > Error responses are documented per operation > Current user errors
- Current text: "Every operation SHALL document each error status it can return" / "**THEN** `getCurrentUser` documents 401 ... and 500 ..."
- Problem: `GET /users/me` with a 300 KB `Content-Length` body returns 413, so "can return" isn't a checkable set.
- Failure scenario: chunks disagree on documenting 413, and the generated clients differ.
- Proposed fix: "Every operation SHALL document 500 `INTERNAL_ERROR`, 401 `UNAUTHENTICATED` when it requires a token, and every status named in its `@ApiErrors`. Statuses caused only by malformed transport (413, 415, 400 `MALFORMED_REQUEST`) SHALL be documented only on operations with a request body." The scenario becomes "documents exactly 401 and 500". Alternative: 413 on every operation (user's call).
- Upstream: no
- Test sketch: contract. The response keys of `/users/me` GET are exactly {200, 401, 500}.

### T9: Startup-secret scenarios don't test the shipped configuration, so a default secret goes unnoticed
**Status:** applied
- Severity: major
- Location: specs/request-authentication/spec.md > Signing secret comes from the environment; tasks.md > 6.1
- Current text: "SHALL ship no default for it" / "**WHEN** the service starts without `JWT_SECRET`"
- Problem: `ApplicationContextRunner` tests don't load `application.yaml`, so a `${JWT_SECRET:...}` default isn't caught. "Not valid base64" has no scenario.
- Failure scenario: someone adds a convenience default, and prod runs with a public secret.
- Proposed fix: add the scenarios "Secret not base64" and "No shipped default" (main `application*.yaml` with no env var → startup fails). Add a task 6.1 test that loads the main config without `JWT_SECRET`. Related: S3, A13.
- Upstream: no
- Test sketch: start with the main config and no env var. Passes when startup fails.

### T10: Verification scenarios miss tokens with missing claims and other HMAC algorithms
**Status:** applied
- Severity: major. This is the security boundary. (The assumptions reviewer lists "a token without `exp`" as safe, because only holders of the secret can mint one. T10's case is an issuer bug in `be-auth`.)
- Location: specs/request-authentication/spec.md > Verification accepts only valid access tokens
- Current text: "...an HS256 signature that verifies with the configured secret, `type` `access`, a `sub` that is a UUID, and a current time before `exp`..."
- Problem: no scenario covers a missing `exp`, `type` or `sub`, or HS512 with the same secret.
- Failure scenario: an issuer bug drops `exp`, the tokens never expire, and no test fails.
- Proposed fix: add the scenarios "Missing expiry", "Missing type" and "Other HMAC algorithm (HS512)", each → 401 `UNAUTHENTICATED`.
- Upstream: no
- Test sketch: decoder unit tests with Nimbus-built tokens, plus one E2E.

### A1 / T16: Public routes and non-API paths still reject a bad bearer token
**Status:** applied
- Severity: major (A1; T16 rated it minor)
- Location: specs/request-authentication/spec.md > Public routes are listed explicitly; design.md > Authentication
- Current text: "Paths outside `/api/v1` SHALL NOT require a token." / "Everything else is `permitAll()`"
- Problem: `BearerTokenAuthenticationFilter` validates any bearer header on every path, so an expired token gets 401 on `permitAll` routes too.
- Failure scenario: a client that sends its expired token with `POST /api/v1/auth/refresh` gets 401, and the session ends. A stale header on `/actuator/health` also returns 401.
- Proposed fix: "On public routes and on paths outside `/api/v1`, the backend SHALL ignore the `Authorization` header." Add the scenario "Health with an expired token → 200 `UP`". design.md: "A custom `BearerTokenResolver` returns null for `PublicRoutes` entries and for paths outside `/api/v1`."
- Upstream: no

### A2: Bean Validation and argument binding run before reference checks, which breaks error precedence
**Status:** applied
- Severity: major. Every PATCH or move endpoint in wave 3 is affected.
- Location: proposal.md > Shared requirements table, `api-errors` / Error precedence; design.md > Validation and field errors
- Current text: "| `api-errors` | Error precedence | Implemented for 401, 413, 415, 400, 404 route; rate limit in `be-auth` |"
- Problem: `@Valid @RequestBody` fails before the service resolves references. A path variable declared before the body is converted first.
- Failure scenario: shared scenario "Missing board and invalid name" returns 400 instead of 404. A malformed path UUID combined with an unparseable body returns 404 instead of 400.
- Proposed fix: add an explicit `RequestValidator.validate(request)` in `:common`, called after references are resolved. Use `@Valid` only on endpoints with no references, and declare `@RequestBody` before `@PathVariable`. Add the scenario "Malformed path ID and unparseable body → 400 `MALFORMED_REQUEST`". Alternative: ask for a shared-spec precedence change (upstream).
- Upstream: no

### A3: Every Jackson conversion failure is treated as "wrong JSON type"
**Status:** applied
- Severity: major
- Location: specs/error-responses/spec.md > Malformed bodies hide their content; design.md > Validation and field errors
- Current text: "A request body that cannot be parsed, or that has a value of the wrong JSON type, SHALL produce 400 `MALFORMED_REQUEST`"
- Problem: a non-UUID string, an unknown enum string and an out-of-range integer all fail inside Jackson and become `MALFORMED_REQUEST`.
- Failure scenario: shared `api-errors` "Array item" (`INVALID_FORMAT`), `api-auth-tokens` "Missing or unknown transport" (`INVALID_VALUE`) and `api-conventions` "Huge position" (`OUT_OF_RANGE`) all get `MALFORMED_REQUEST`.
- Proposed fix: add the requirement "Unconvertible values of the right JSON type are field errors". Request DTOs declare UUID and enum fields as `String` with `@ValidUuid` / `@ValidEnum`, and positions as `BigInteger`. Mappers convert them after validation, and the OpenAPI customizer keeps `format: uuid` and the enum values.
- Upstream: no

### A8: The snapshot includes a server URL with the random test port
**Status:** applied
- Severity: major. The drift check fails on every run.
- Location: design.md > OpenAPI snapshot; specs/api-contract-snapshot/spec.md > The snapshot is updated by a task
- Current text: "`OpenApiSnapshotTest` in `:app` starts the app (Testcontainers, `test` profile), fetches `/v3/api-docs`"
- Problem: springdoc adds `servers: [{"url": "http://localhost:<port>"}]` from the request.
- Failure scenario: two runs produce different files.
- Proposed fix: the `OpenAPI` bean sets exactly one server, `{ "url": "/" }`. Add "and no value depends on the host, port or time of generation" to "Repeated generation". Alternative: a fixed production base URL (user's choice).
- Upstream: no

### A9: The spec doesn't say whether optional request properties are nullable — see conflict C1
**Status:** applied
- Severity: major
- Location: specs/api-contract-snapshot/spec.md > Required request fields are documented as required
- Current text: "Optional request properties SHALL NOT be listed as `required`."
- Problem: all request DTO properties are nullable Kotlin types (D11), and `api-conventions` "Null and empty updates" rejects `null` with `REQUIRED`.
- Failure scenario: a generated client sends `"description": null` in a PATCH and gets 400 `REQUIRED`.
- Proposed fix: "Optional request properties SHALL NOT be marked nullable either". Add the scenario "Optional field: not under `required` and not nullable". In design.md, mark every request property non-null and add only `@Required` ones to `required`.
- Upstream: no

### D6: The test-only controller would be component-scanned into the snapshot context
**Status:** applied
- Severity: minor
- Location: design.md > Tests table, row "Error mapping, validation, ..."
- Current text: "Uses a test-only controller under `/api/v1/test/...`, registered only in the E2E test configuration and absent from the snapshot"
- Problem: a `@RestController` under the application package in `app/src/test` is scanned by every `@SpringBootTest`, including `OpenApiSnapshotTest`.
- Failure scenario: `openapi.json` ships with `/api/v1/test/...` operations.
- Proposed fix: no stereotype annotation. Declare it as a `@Bean` in a `@TestConfiguration` that only the E2E base class imports, and have the snapshot test assert that no path starts with `/api/v1/test` (see also T7).
- Upstream: no

### D7: Smaller inconsistencies between the artifacts
**Status:** applied
- Severity: minor
- Location: design.md > Context, Error model, Package placement; tasks.md > 1.1
- Current text: "every settled choice (D1–D20)"; "subclasses of the two base exceptions"; task 1.1 "and `spring-security-test`"; "| `MutableClock`, `DatabaseCleaner` | `:common` `testFixtures` |"
- Problem: (a) the decisions go up to D23. (b) There are three base exceptions, and the table has no `UnauthenticatedException` row. (c) D19 doesn't approve `spring-security-test`. (d) No task creates `DatabaseCleaner` or adds `testFixtures(projects.user)` to `:app`.
- Failure scenario: (b) `CurrentUserNotFoundException` gets mapped to 500. (c) a dependency is added without approval.
- Proposed fix: "(D1–D23)". "three base exceptions" plus the row "| `UnauthenticatedException` | `UNAUTHENTICATED`, with `WWW-Authenticate: Bearer` |". Remove `spring-security-test` from 1.1, or mark it "ask the user first". Task 7.3: add `DatabaseCleaner` and `testImplementation(testFixtures(projects.user))`.
- Upstream: no

### T11: "No endpoint or configuration property can change the time" can't be tested
**Status:** applied
- Severity: minor
- Location: specs/backend-time/spec.md > Production time is the system time > Running service
- Current text: "**THEN** no HTTP endpoint or configuration property can change the time it uses"
- Problem: a universal negative with no bound.
- Proposed fix: "the `Clock` bean is the system UTC clock ticking in milliseconds, and `MutableClock` is not on the main runtime classpath". Pair it with T6.
- Upstream: no

### T12: Operation naming rules are checked for one operation only, and uniqueness has no scenario
**Status:** applied
- Severity: minor
- Location: specs/api-contract-snapshot/spec.md > Operations have explicit stable names
- Current text: "**THEN** `GET /api/v1/users/me` has the `operationId` `getCurrentUser` and the single tag `Users`"
- Problem: springdoc silently renames duplicates to `name_1`.
- Proposed fix: add the scenario "Every operation": every `operationId` matches `^[a-z][a-zA-Z0-9]*$` and is unique, and there is exactly one tag matching `^[A-Z][a-zA-Z]*s$`.
- Upstream: no

### T13: User-storage scenarios mix layers, and one is a tautology
**Status:** applied with edits (the storage scenario was split into a domain-model and a direct-insert scenario. "Mixed-case duplicate" and the plain-password sentence were kept, because D1 requires mixed-case duplicates to fail in this change's spec)
- Severity: minor
- Location: specs/user-accounts/spec.md > Emails are unique > Mixed-case duplicate; Emails are stored lowercased > Uppercase email rejected by storage; A user record holds ...
- Current text: "another record is stored from the input `Ann@EXAMPLE.com`, lowercased before storing" / "code tries to store a user with the email `Ann@Example.com`" / "It SHALL NOT store the plain password."
- Problem: the domain `init` rejects before the `@Check` is reached, and the plain-password rule can't be tested here.
- Failure scenario: the `@Check` is dropped, and the tests still pass.
- Proposed fix: split it into "`User` construction fails" and "a direct insert into `user_service.users` is rejected by the database". Move "Mixed-case duplicate" and the plain-password sentence to `be-auth`, or drop them.
- Upstream: no

### T14: Fixed-title scenario doesn't cover the second 401 code path
**Status:** applied
- Severity: minor
- Location: specs/error-responses/spec.md > Title is fixed per code > Same code, different causes
- Current text: "**WHEN** one request has a missing token and another has an expired token"
- Proposed fix: add a third request, a valid token for a user with no record (`GET /api/v1/users/me`). All three titles must be equal.
- Upstream: no

### T15: The `null` field case and limit-field exclusivity have no scenarios
**Status:** applied
- Severity: minor
- Location: specs/error-responses/spec.md > Missing and null fields are validation errors; Error bodies carry exactly the documented fields
- Current text: "...that is missing or `null` SHALL produce..." / "`min` and `max` only where `api-errors` ... requires them"
- Proposed fix: add the scenarios "Explicit null" (`{"name": null}` → `REQUIRED`) and "Too long carries only max" (exactly `field`, `code`, `max`).
- Upstream: no

### A10: The `test` profile config exists only in `:app`
**Status:** applied
- Severity: minor
- Location: design.md > Configuration; tasks.md > 7.1
- Current text: "`application-test.yaml`: a fixed base64 test secret"
- Problem: the `:user` `@DataJpaTest` doesn't see `app/src/test/resources/application-test.yaml`, so `user_service` is never created.
- Proposed fix: move the `test` profile file to `:common` `testFixtures` resources, and mention the move in task 1.2.
- Upstream: no

### A11: Spring answers HEAD and OPTIONS on every mapped path automatically
**Status:** applied
- Severity: minor
- Location: specs/error-responses/spec.md > Wrong method; design.md > mapping table
- Current text: "`NoResourceFoundException`, `HttpRequestMethodNotSupportedException` | `ROUTE_NOT_FOUND`"
- Problem: `OPTIONS /api/v1/users/me` returns 200 with `Allow`.
- Proposed fix: "HEAD and OPTIONS count as wrong methods unless an endpoint declares them", and turn off implicit handling for `/api/v1`. Alternative: document them as allowed.
- Upstream: no

### A13: The base64 alphabet and whitespace handling for `JWT_SECRET` are not defined
**Status:** applied
- Severity: minor
- Location: specs/request-authentication/spec.md > Signing secret comes from the environment
- Current text: "SHALL read the access-token signing secret from `JWT_SECRET` as base64"
- Problem: the standard, URL and MIME decoders differ. A secret-manager pipe may add a trailing newline. Related to S3.
- Proposed fix: "Base64 means the standard alphabet of RFC 4648 section 4, with optional padding. Leading and trailing whitespace is removed before decoding. Inner whitespace and the URL-safe alphabet are invalid."
- Upstream: no

## Security analysis

### Trust boundaries
- **Bearer token verification:** an anonymous client tries `alg none`, algorithm confusion, a guessed key, a refresh token, `sub` `admin`, expiry or skew. Stopped by HS256-only verification, `type`, UUID `sub` and zero skew. Refresh tokens are opaque.
- **Route authorization:** a controller mapped outside `/api/v1` runs without a token. Nothing stops this → S5.
- **Request body intake:** unauthenticated form bodies with no limit, and multipart that skips the counter. Nothing stops these → S1, S2.
- **Error responses:** Spring default details and exception messages echo request data, and firewall rejections go to `/error`. Only partly stopped → S4, S6.
- **Configuration and secrets:** `BindFailureAnalyzer` prints a bad `JWT_SECRET`. Not stopped → S3.
- **Token subject to user data:** the acting user only ever comes from `sub`. Stopped.
- **Cross-origin browser:** no CORS, and bearer tokens travel in headers only. Stopped.
- **Email storage:** lowercase invariant, `@Check` and unique constraint. Stopped.
- **Test-only code reaching production:** test source sets only, docs disabled, no Swagger UI. Stopped.

### Test versus production
- **Secret never printed:** the tests check the exception message, but production logs the failure analysis → S3.
- **Expiry with zero leeway:** same validator and clock path in tests and production. Closed.
- **Body size limit:** the tests are JSON only, while attackers choose the content type → S1, S2.
- **Error text never echoes request data:** only two codes are pinned → S4.
- **Every `/api/v1` route needs a token:** the tests only probe paths they chose → S5.
- **Alg/signature/type/sub, same-origin, stateless, no runtime docs, fixed clock:** no gap.

## Assumptions safe to leave implicit
| Assumption | Where it is implied | Why it is safe |
|---|---|---|
| Single instance or NTP-synced clocks | zero leeway | `infra-vps` is a single instance. Small drift only shifts expiry, and a 401 triggers a refresh. |
| Emails are ASCII, so `lowercase(Locale.ROOT)` and Postgres `lower()` agree | `@Check` | Shared `domain-user` ASCII rule (D2 facts). |
| JVM and DB time zones don't matter | `timestamptz` | `Instant` with a fixed UTC formatter. |
| Postgres keeps microseconds | Time | Values are truncated to ms before writing. |
| NFC and White_Space results match the clients' Unicode version | text rules | Unicode stability policy. Shared `text-length` owns this. |
| springdoc reflection order is stable | snapshot | JDK pinned, and drift fails loudly. |
| `openapi.json` keeps LF line endings | byte compare | A CRLF checkout fails loudly. |
| Parallel chunks edit the snapshot | snapshot | The merge conflict is loud, and the update task resolves it. |
| An unresolved `${JWT_SECRET}` becomes a literal | JwtProperties | It isn't valid base64, so startup fails. |
| A token without `exp` or with odd claims | ExpiryValidator | Only holders of the secret can mint one (see T10 for the issuer-bug view). |
| SecurityContext ThreadLocal on virtual threads | `requestUserId` | One request per virtual thread. |
| Reverse proxy body limits and HTTPS | size limit | Owned by `infra-vps`. |
| A CORS preflight under `/api/v1` gets 401 | CORS test | It carries no `Access-Control-Allow-*` header. |
| `JWT_SECRET` is set before deploy | proposal Impact, Migration Plan | Stated explicitly. |

## New abstractions
- `ErrorCode` / `FieldErrorCode`: fine as is (closed `api-errors` catalog).
- Base exceptions: keep. `UnauthenticatedException` is missing from the mapping table (D7).
- `AccessTokenIssuer` + `IssuedAccessToken`: fine as is (layer rule, D5).
- `JwtProperties` and the custom validators: fine as is. Secret handling needs rework (S3).
- `ProblemResponseWriter`: fine as is. Its scope should grow (T5/S6/A4).
- `PublicRoutes`: keep, but document the extension mechanism (D3) and resolve the CLAUDE.md conflict (D2).
- `RequestSizeLimitFilter` + `PayloadTooLargeException`: keep. Fix the exception type (D5) and the spec scope (D4).
- `@Required`, `@ValidText`: fine as is (D11, D12).
- `@ApiErrors` + springdoc customizers: fine as is.
- Text helpers: fine as is.
- `MutableClock`: fine as is. `DatabaseCleaner` has no task (D7).
- `UserFixtures` in the `:user` testFixtures: fine as is.
- `CurrentUserNotFoundException`: fine as is (D21).
- `OpenApiSnapshotTest` + `:app:updateOpenApiSnapshot`: fine as is.
- Test-only controller: keep, but fix how it is registered (D6).

## Coverage
- Artifacts reviewed: proposal.md, design.md, tasks.md, specs/{api-contract-snapshot, backend-time, error-responses, request-authentication, service-health, user-accounts}/spec.md, decisions.md (settled decisions)
- Missing artifacts: none
- Testability: 36 requirements and 60 scenarios checked, 15 requirements flagged
- Security: 9 trust boundaries and 6 invariant groups examined. S1–S3 were checked against Spring sources. The multipart bypass and the exact `BindException` property are reasoned, not read in source.
- Hidden assumptions: process lifetime is not applicable (no events, jobs or multi-step writes)
- Design contract: code mode. Checked the main specs `service-health` and `module-boundaries`, CLAUDE.md, and the shared `api-errors` / `api-conventions`.
- Reviewers that did not complete: none
- Findings dropped because their evidence did not exist: 0
- Findings dropped because they challenged decisions.md: 0
- Merged duplicates: S1/A5, S2/A12, D4/T2/A6, D5/A7, T5/S6/A4, A1/T16. Severity raised to the higher rating: A5, A12, A6, A7, S6, A4, T16.
- Note: no reviewer ran code. The Spring, Jackson and springdoc behavior claims (S1–S3, D5, D6, A1, A8, A11) come from library knowledge or source reading.

## Applied changes
- Files changed: `proposal.md`, `design.md`, `tasks.md`, `specs/api-contract-snapshot/spec.md`, `specs/backend-time/spec.md`, `specs/error-responses/spec.md`, `specs/request-authentication/spec.md`, `specs/user-accounts/spec.md`, new `specs/module-boundaries/spec.md`
- Validation splits: to pass `openspec validate --strict`, two long requirements were split. "The signing secret is decoded strictly and never logged" was split out of "Signing secret comes from the environment", and "Transport errors are documented only where a body is read" out of "Error responses are documented per operation". A2's precedence rule became its own requirement in error-responses ("Parsing comes before references, and references before field validation").
- Stale non-standard artifacts the user chose not to update: none (there are none)
- Contradictions with decisions.md: none. Two related notes:
  - T13's proposal to drop "Mixed-case duplicate" would have contradicted D1, so the scenario was kept.
  - D10 says the body limit is "counted on the bytes received, including chunked bodies". The design now records that a chunked body nobody reads is not counted (Risks, pinned by a test). D10's wording is unchanged.
- Accepted risks: none
- The new wording itself has not been reviewed. Re-run this review in a fresh session.
