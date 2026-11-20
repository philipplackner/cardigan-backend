# Design

## Context

See `proposal.md` for the motivation and `decisions.md` (D1–D18) for the settled choices. What exists today:

- `:common` has `AccessTokenIssuer` (issues the JWT and its whole-second expiry), `PublicRoutes`/`PublicRouteRegistry`, the error model with all codes this change needs (`INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `RATE_LIMITED`), `normalizeText`, `normalizePassword`, `checkText`, and the constraints `@Required`, `@ValidText`, `@ValidEnum`.
- `CommonExceptionHandler` maps `ResourceNotFoundException`, `UniquenessConflictException` and `UnauthenticatedException`. Nothing maps to `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN` or `RATE_LIMITED` yet, and `UnauthenticatedException` always adds `WWW-Authenticate: Bearer`.
- `:user` has `User`, `UserRepository` (`findById`, `existsByEmail`, `save`), the `users` table and `GET /api/v1/users/me`.
- `CardiganOpenApiCustomizer` marks a response property as required unless its Kotlin type is nullable, and adds 401 `UNAUTHENTICATED` only to non-public routes.
- CSRF is off, sessions are stateless, there is no CORS and no scheduling.
- Tests run on the `test` profile with Hibernate `create-drop`.

Constraints: no business module depends on another, services depend on domain interfaces only, fakes instead of mocks, and no new top-level error code.

## Goals / Non-Goals

**Goals:**
- All auth code in `:user`, in the existing layer packages.
- Rotation and reuse detection that are correct under concurrent requests, without application-level locks.
- Rate limits and token expiry that tests can drive through `MutableClock`.

**Non-Goals:**
- Anything listed under "Out of scope" in `decisions.md`.
- A generic rate limiting facility in `:common`. Only the exception and its mapping are shared.
- Changing the access-token format or verification.

## Decisions

### Package placement

Everything is in `com.plcoding.cardigan.user` unless noted.

| Class | Package |
|---|---|
| `AuthController` | `api/controller` |
| `RegisterRequest`, `LoginRequest`, `RefreshRequest`, `LogoutRequest`, `AuthSessionDto`, `AuthTokensDto`, `RefreshTokenTransportDto` | `api/dto` |
| `AuthDtoMappers.kt` | `api/mapper` |
| `@ValidEmail`, `@ValidPassword`, `@NotBlankText`, `@NotEmptyPassword` and their validators | `api/validation` |
| `RefreshTokenCookie` (builds and reads the cookie) | `api/controller` (internal helper) |
| `AuthService`, `SessionService`, `SessionCleanupService`, `AuthProperties` | `service` |
| `Session`, `RefreshToken`, `RefreshTokenTransport`, `IssuedSession`, `AuthAction` | `domain/model` |
| `EmailAlreadyUsedException`, `AuthRateLimitedException` | `domain/exception` |
| `SessionRepository`, `RefreshTokenRepository`, `PasswordHasher`, `RefreshTokenGenerator`, `AuthRateLimiter` | `domain/repository` |
| `SessionEntity`, `RefreshTokenEntity` | `infra/database/entity` |
| `SpringDataSessionRepository`, `JpaSessionRepository`, `SpringDataRefreshTokenRepository`, `JpaRefreshTokenRepository` | `infra/database/repository` |
| `BcryptPasswordHasher`, `SecureRandomRefreshTokenGenerator` | `infra/security` |
| `Bucket4jAuthRateLimiter` | `infra/ratelimit` |
| `PasswordHashingProperties`, `AuthRateLimitProperties`, `UserPublicRoutesConfig` | `infra/config` |
| `InvalidCredentialsException`, `InvalidRefreshTokenException`, `RateLimitedException` base classes | `:common` `domain/exception` |

`infra/security` and `infra/ratelimit` are new sub-packages next to `database`, `http` and `config`.

### Error mapping in `:common`

Three base exceptions are added next to the existing ones, and `CommonExceptionHandler` maps them:

| Base exception | Response |
|---|---|
| `InvalidCredentialsException` | 401 `INVALID_CREDENTIALS`, no `WWW-Authenticate` |
| `InvalidRefreshTokenException` | 401 `INVALID_REFRESH_TOKEN`, no `WWW-Authenticate` |
| `RateLimitedException(retryAfterSeconds)` | 429 `RATE_LIMITED`, `Retry-After: <seconds>` |

They are not subclasses of `UnauthenticatedException`, because that one means `UNAUTHENTICATED` and sends the bearer challenge. `CLAUDE.md` lists three base exceptions today; the list grows to six.

`AuthService` and `AuthController` throw the `:common` `InvalidCredentialsException` and `InvalidRefreshTokenException` directly; they carry no data, so `:user` adds no subclass. `EmailAlreadyUsedException` subclasses `UniquenessConflictException` and `AuthRateLimitedException` subclasses `RateLimitedException`, so there is still no feature exception handler.

If `be-boards` or `be-labels` land first and already added base exceptions to `:common` (for example `FieldOutOfRangeException`), these three are added beside them.

### Request DTOs and validation

All request properties are nullable `String`s with no default, as the project rules require.

| DTO | Property | Constraints |
|---|---|---|
| `RegisterRequest` | `email` | `@Required`, `@ValidText(min = 1, max = 254)`, `@ValidEmail` |
| | `password` | `@Required`, `@ValidPassword` |
| | `refreshTokenTransport` | `@Required`, `@ValidEnum(RefreshTokenTransportDto::class)` |
| `LoginRequest` | `email` | `@Required`, `@NotBlankText` |
| | `password` | `@Required`, `@NotEmptyPassword` |
| | `refreshTokenTransport` | `@Required`, `@ValidEnum` |
| `RefreshRequest`, `LogoutRequest` | `refreshTokenTransport` | `@Required`, `@ValidEnum` |
| | `refreshToken` | none (optional) |

- `@ValidEmail` checks the normalized value against the RFC 5322 `addr-spec` (dot-atom or quoted-string local part, dot-atom or domain-literal domain, ASCII only, no comments or folding whitespace) with a hand-written regex and reports `INVALID_FORMAT`. Hibernate's `@Email` is not used, because its rules differ from the shared spec. The validator returns valid without running the regex when the normalized value is empty, longer than 254 code points or has an invalid character, so `@ValidText` alone reports those cases and the field gets one error. The regex must not backtrack: no nested quantifiers, possessive quantifiers or atomic groups throughout.
- `@ValidPassword` works on `normalizePassword(value)`: `checkText(min = 12, max = 128, multiline = false)` for `REQUIRED`, `INVALID_CHARACTER`, `TOO_SHORT`, `TOO_LONG` with their limits, then one `Ll`, one `Lu` and one `Nd` character or `PASSWORD_TOO_WEAK`. It reports through the same dynamic payload as `@ValidText`, so `min` and `max` reach the field error.
- `reject(FieldViolation)` in `:common` is `internal` today. It becomes public, unless `be-boards` (its task 1.3) already did that.
- `RequiredValidator` in `:common` only checks for null. Login therefore gets two constraints in `:user` `api/validation`: `@NotBlankText` reports `REQUIRED` when `normalizeText(value)` is empty, and `@NotEmptyPassword` reports `REQUIRED` when the value is `""` without trimming, so a password of only spaces is a value. Neither applies any other rule. `@Required` in `:common` is not changed.
- Every type that holds a password, token or hash (`RegisterRequest`, `LoginRequest`, `RefreshRequest`, `LogoutRequest`, `AuthSessionDto`, `AuthTokensDto`, `IssuedSession`, `RefreshToken`) overrides `toString()` to leave those values out, as `User` does.
- Refresh and logout use `@Valid`. Register and login call `RequestValidator.validate(request)` themselves, after the rate limit check (see the next section).

### Order of checks in the controller and service

For register and login:

1. Spring reads the body (413, 415, `MALFORMED_REQUEST` happen here).
2. The controller method takes the raw request without `@Valid` and calls `AuthService.checkRateLimit(AuthAction.LOGIN, request.email)` (or `REGISTER`). The service normalizes and lowercases the email with the same domain function the mappers use, skips the limiter when the result is empty, and otherwise calls `AuthRateLimiter`. Over the limit throws `AuthRateLimitedException`.
3. The controller calls `RequestValidator.validate(request)`.
4. The mapper builds the domain input (`normalizeText(email).lowercase(Locale.ROOT)`, `normalizePassword(password)`), and the service runs.

Step 2 must come before step 3 so that 429 wins over 400, which is why these two methods don't use `@Valid`. Refresh and logout use `@Valid`.

This is the order of the shared spec `api-errors` ("Error precedence"): parsing, then the rate limit, then field validation (D21).

### Rate limiter

- Domain interface: `AuthRateLimiter.check(action: AuthAction, email: String)`, with `AuthAction` `LOGIN` or `REGISTER`. It throws `AuthRateLimitedException(retryAfterSeconds)` when no token is left.
- `AuthService.checkRateLimit` passes `normalizeText(email).lowercase(Locale.ROOT)` and skips the call when the result is empty. The controller never calls the limiter itself.
- `Bucket4jAuthRateLimiter` keeps one Caffeine cache per action, and `max-tracked-emails` applies per action. It reports how many emails it tracks per action, for tests. The cache key is the SHA-256 of the email, so a long email can't bloat the cache. Each value is a bucket4j `Bucket` with capacity 5 and a greedy refill of 5 per minute, both from config.
- Caffeine uses `maximumSize` from config (default 100,000) and `expireAfterAccess` equal to the refill period. A bucket that was idle for a full period is full again, so dropping it changes nothing. The caches are built with `executor(Runnable::run)`, so eviction happens on the calling thread and the bound holds after every call.
- bucket4j gets a `TimeMeter` that reads the `Clock` bean, and Caffeine gets a `Ticker` from the same clock. Tests move `MutableClock` instead of sleeping.
- `Retry-After` is the bucket's wait time rounded up to whole seconds, at least 1.
- `Bucket4jAuthRateLimiter` has a public `clear()` that empties both caches. It is not on the `AuthRateLimiter` domain interface. `E2eTest.setUpE2e` and the `:user` module test base call it before every test, next to `databaseCleaner.clean`. This is required, not a convenience: the test clock is reset backward before each test, and a bucket never refills while the clock is behind its last refill.

Alternative considered: bucket4j's own Caffeine proxy manager. It adds a second artifact and async plumbing for no gain with in-memory buckets.

### Password hashing

- Domain interface `PasswordHasher` with `hash(password): String` and `matches(password, hash: String?): Boolean`. Callers pass the NFC-normalized password. With a `null` hash, the implementation checks against a dummy hash it created once at startup with the same cost and returns false.
- `BcryptPasswordHasher` computes `Base64(SHA-256(password as UTF-8))` (44 ASCII characters) and passes it to Spring Security's `BCryptPasswordEncoder(cost)`. The cost comes from `cardigan.user.auth.bcrypt-cost`, default 12. The test profile sets 4.
- `BcryptPasswordHasher` runs `hash` and `matches` under a fair `Semaphore` with `cardigan.user.auth.max-concurrent-hashes` permits (default: half the available processors, at least 1). Requests beyond that wait, so hashing never takes all carrier threads.
- Login passes `user?.passwordHash` to `matches`, so an unknown email does the same work, then throws the same exception. This gives the equal work `api-errors` asks for.
- `BCryptPasswordEncoder` comes from `spring-security-crypto`, which `:user` already gets through `:common`.

### Refresh tokens and sessions

Domain models (immutable, no Spring or JPA):

- `Session(id, userId, transport, createdAt, revokedAt?)`
- `RefreshToken(id, sessionId, tokenHash, createdAt, expiresAt, rotatedAt?)`

Tables in `user_service`, built from the entities:

| Table | Columns | Constraints and indexes |
|---|---|---|
| `sessions` | `id uuid`, `user_id uuid`, `transport varchar(16)`, `created_at timestamptz`, `revoked_at timestamptz null` | PK `id`; FK `user_id` → `users(id)` on delete cascade; index on `user_id`; check `transport in ('COOKIE','BODY')` |
| `refresh_tokens` | `id uuid`, `session_id uuid`, `token_hash varchar(64)`, `created_at timestamptz`, `expires_at timestamptz`, `rotated_at timestamptz null` | PK `id`; FK `session_id` → `sessions(id)` on delete cascade; unique `token_hash`; index on `session_id` |

- `SessionEntity.user` and `RefreshTokenEntity.session` are lazy `@ManyToOne(fetch = LAZY, optional = false)` associations with `@JoinColumn(nullable = false)` and `@OnDelete(action = OnDeleteAction.CASCADE)`, so Hibernate generates the cascading foreign keys for dev and tests (same pattern as `be-boards`). The adapters set them with `getReferenceById` and never navigate them. The domain models keep plain `userId` and `sessionId`.
- `SecureRandomRefreshTokenGenerator` returns 32 bytes from `SecureRandom`, base64url without padding (43 characters). `token_hash` is the lowercase hex SHA-256 of that string. Hashing is a pure function in `domain`, since both the service and tests need it.
- The refresh-token lifetime is `cardigan.user.auth.refresh-token-ttl`, default `30d`. All times come from the `Clock` bean, so they have millisecond precision.

### Refresh flow

`AuthService.refresh(transport, token)` runs in one transaction and returns a result instead of throwing, so the revocation in the reuse case commits. The controller turns a failed result into `InvalidRefreshTokenException` after the transaction ended.

1. No token, or no row for its hash → fail.
2. Load the session. Its transport differs from the request's → fail, nothing changes.
3. Session revoked → fail.
4. Token already rotated → revoke the session (`revoked_at = now`) → fail.
5. Token expired (`expires_at <= now`) → fail.
6. Claim the token with one statement: `update refresh_tokens set rotated_at = :now where id = :id and rotated_at is null`. Zero rows updated means another request rotated it in between: treat as step 4.
7. Insert the new refresh token for the same session, issue the access token, return both.

Step 6 is the atomicity the shared spec asks for: of two parallel refreshes, exactly one updates the row. The loser revokes the session, which also invalidates the winner's new token, as the shared scenario "Parallel refresh with one token" requires.

Alternative considered: `select ... for update` on the token row. It works too, but holds a lock across the whole request and needs the same reuse branch anyway.

A failed refresh with `COOKIE` clears the cookie (D19). The controller adds the clearing cookie to the `HttpServletResponse` before it throws `InvalidRefreshTokenException`, so the header is on the 401 that `CommonExceptionHandler` writes. This covers every failure reason, including a missing cookie. With `BODY`, and on a 400, no cookie header is sent.

### Register, login and logout flows

- **Transactions**: `AuthService.register` and `AuthService.login` are not transactional, so no connection is held during the BCrypt work. Their database parts are public `@Transactional` methods on `SessionService`, a separate bean in `service`. They are never methods of `AuthService` itself, because a call inside one bean skips the transaction proxy.
- **Register**: `AuthService.register` hashes the password first, then calls the transactional part on `SessionService`: `existsByEmail` → `EmailAlreadyUsedException` (a `UniquenessConflictException` on `email`). Otherwise save the user, create the session and the first token. The unique constraint on `users.email` covers the race: `JpaUserRepository.save` uses `saveAndFlush` and turns a violation of the email unique constraint into `EmailAlreadyUsedException`. The constraint gets the explicit name `users_email_unique` (`@Table(uniqueConstraints = ...)`), and only that name is translated. The two duplicate tests in `JpaUserRepositoryTest` change to expect `EmailAlreadyUsedException` from `save`.
- **Login**: `UserRepository.findByEmail` is added. `AuthService.login` loads the user, checks the password, then calls `SessionService.start(userId, transport)`, whose `@Transactional` method inserts the session and its first token together. When `checkText(email, min = 1, max = 254, multiline = false)` is not null, the email could never be stored: the service skips `findByEmail` and takes the unknown-email path (dummy hash, then `InvalidCredentialsException`).
- **Logout**: one transaction. Look up the token by hash. Nothing changes when there is no token, the session has another transport, or the session is already revoked. Otherwise: a rotated token revokes the session, whatever its expiry (D17, same order as refresh steps 4 and 5); a current token revokes the session only when `expires_at > now`. The controller always answers 204.

### Cookie handling

- `RefreshTokenCookie` builds a `ResponseCookie`: name `cardigan_refresh_token`, `httpOnly`, `secure`, `sameSite("Strict")`, `path("/api/v1/auth")`, `maxAge` of the refresh-token TTL, no domain. The clearing cookie is the same with an empty value and `maxAge(0)`. `IssuedSession` and the refresh result carry `refreshTokenExpiresAt`, and `RefreshTokenCookie` computes `Max-Age` from it and the clock, so `api` reads no config.
- The cookie path is the backend's own path. Any proxy in front of the backend, including the web dev proxy, must expose the auth routes under exactly `/api/v1/auth` on the web app's site. A path prefix or a different site breaks the `COOKIE` transport.
- The controller reads the cookie with `@CookieValue(required = false)`, annotated `@Parameter(hidden = true)`, so generated clients get no cookie parameter (D20). The descriptions of `refresh` and `logout` name the cookie.
- With `COOKIE`, the body token is ignored. With `BODY`, the cookie is ignored.
- `Secure` is always set. Browsers accept `Secure` cookies from `http://localhost`, so local web development works through the dev proxy.

### Response DTOs and OpenAPI

- `AuthSessionDto(accessToken, accessTokenExpiresAt, refreshToken: String?, user: CurrentUserDto)` and `AuthTokensDto(accessToken, accessTokenExpiresAt, refreshToken: String?)`.
- `refreshToken` has `@JsonInclude(NON_NULL)`, so it is absent for `COOKIE`. The customizer already leaves a nullable Kotlin property out of `required`. The snapshot must not show `nullable: true` for it. `customizeResponseSchema` clears `nullable` only for properties whose field carries `@JsonInclude(NON_NULL)`; other nullable response properties stay `nullable`.
- `@ApiErrors` per operation: register 400 `VALIDATION_FAILED`, 409 `CONFLICT`, 429 `RATE_LIMITED`; login 400 `VALIDATION_FAILED`, 401 `INVALID_CREDENTIALS`, 429 `RATE_LIMITED`; refresh 400 `VALIDATION_FAILED`, 401 `INVALID_REFRESH_TOKEN`; logout 400 `VALIDATION_FAILED`. The customizer does not document `VALIDATION_FAILED`, so every operation lists it in `@ApiErrors`.
- `UserPublicRoutesConfig` registers a `PublicRoutes` bean with the four `POST` routes. The customizer then omits the bearer requirement and 401 `UNAUTHENTICATED` for them.

### Cleanup job

- `SessionCleanupService.deleteEndedSessions()` is `@Scheduled(cron = "\${cardigan.user.auth.session-cleanup-cron}", zone = "UTC")` with the default `0 0 3 * * *`, and `@Transactional`.
- It runs one delete: sessions where `revoked_at is not null`, or where no token exists with `rotated_at is null and expires_at > :now`. Tokens go with them through the cascade.
- `@EnableScheduling` is added to `ApplicationConfig` in `:app`. Tests call the method directly.

### Configuration

The properties under `cardigan.user.auth` are split by consumer, so no layer imports from one it may not depend on: `AuthProperties` (`refresh-token-ttl`, `session-cleanup-cron`) lives in `service`; `PasswordHashingProperties` (`bcrypt-cost`, `max-concurrent-hashes`) and `AuthRateLimitProperties` (`rate-limit.*`) live in `infra/config`. The YAML keys stay as shown:

```yaml
cardigan:
  user:
    auth:
      refresh-token-ttl: 30d
      bcrypt-cost: 12
      # max-concurrent-hashes: default is half the available processors, at least 1
      session-cleanup-cron: "0 0 3 * * *"
      rate-limit:
        max-tracked-emails: 100000
        login:
          capacity: 5
          period: 1m
        register:
          capacity: 5
          period: 1m
```

The properties classes carry these defaults, so `:user` tests need no extra YAML. `:app` `application.yaml` repeats them as the visible setting. `application-test.yaml` overrides `bcrypt-cost: 4`.

### Dependencies

`bucket4j` (`com.bucket4j:bucket4j_jdk17-core`) and Caffeine (`com.github.ben-manes.caffeine:caffeine`) go into `gradle/libs.versions.toml` and `:user`. The latest stable versions are looked up at implementation time. Caffeine's version comes from the Spring Boot BOM if it manages one.

### Tests

- Unit (fakes, `MutableClock`): `AuthService` for every refresh branch, logout branches, register and login; `@ValidEmail` and `@ValidPassword` with adversarial inputs; the rate limiter with the clock; the hasher (72-byte boundary, NFC forms, salt).
- Persistence (`@DataJpaTest`): the atomic claim statement, cascade deletes, the unique hash, the cleanup delete.
- Module integration: parallel refresh and parallel register with real threads against Postgres. `UserTestApplication` gets `@ConfigurationPropertiesScan` and imports a `UserTestConfig` with `PostgresTestConfig`, a `MutableClock` bean and a fake `AccessTokenIssuer`.
- E2E: every scenario of the four spec files, including cookie attributes, the OpenAPI snapshot, and the log check.
- `E2eTest.signIn` registers and logs in over HTTP. `UserFixtures.createUser` stays for tests that need a user without going through the API.

## Risks / Trade-offs

- [Email-only limits don't stop one client trying many emails, and anyone can use up a victim's bucket and block that email's login for up to a minute] → Accepted by the user (D10). The numbers are config values.
- [Limits are per instance and reset on restart] → Accepted in `CLAUDE.md`. Running more than one instance multiplies the effective limit.
- [SHA-256 pre-hashing means a leaked BCrypt hash could be attacked with hashes from other breaches ("password shucking")] → Low risk at this scale, accepted with D5.
- [The cleanup job runs on every instance] → The delete is idempotent, so concurrent runs are harmless. No ShedLock.
- [A lost refresh response logs the user out, because the old token then counts as reuse] → Required by the shared spec ("There is no grace period").
- [`sessions` and `refresh_tokens` exist only through `ddl-auto`] → They must be in the baseline migration before the first production deploy (deferred, see `decisions.md`).
- [BCrypt cost 12 takes a few hundred milliseconds of CPU per login and holds a carrier thread for that time] → `BcryptPasswordHasher` limits concurrent hashes with a semaphore (`max-concurrent-hashes`), so hashing never takes all carrier threads and other endpoints stay responsive. There is still no cap on total logins across many emails; under a flood, logins get slow but the rest of the API keeps working.
- [An active session keeps every rotated token, and refresh is not rate limited, so one client that refreshes in a loop grows `refresh_tokens` without bound] → Accepted with D8 and D12. Normal clients add about 100 rows per session per day. If table size becomes a problem, the fix is a per-session refresh limit or a cap on kept tokens, which needs a new decision.
- [`be-boards` and `be-labels` run in parallel] → Shared touch points: `BaseExceptions.kt`, `CommonExceptionHandler`, `reject`, `TestApiController`, `E2eTest`, `libs.versions.toml`, `AGENTS.md` and the snapshot. Whichever change lands second merges them.

## Migration Plan

No data migration. On `dev`, `ddl-auto: update` creates the two tables on the next start. Rollback is reverting the commits; the unused tables can stay or be dropped by hand.

## Open Questions

- The exact bucket4j and Caffeine versions, looked up when the dependencies are added.
