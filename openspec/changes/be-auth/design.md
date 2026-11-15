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
| `@ValidEmail`, `@ValidPassword` and their validators | `api/validation` |
| `RefreshTokenCookie` (builds and reads the cookie) | `api/controller` (internal helper) |
| `AuthService`, `SessionCleanupService` | `service` |
| `Session`, `RefreshToken`, `RefreshTokenTransport`, `IssuedSession` | `domain/model` |
| `InvalidCredentialsException`, `InvalidRefreshTokenException`, `EmailAlreadyUsedException`, `AuthRateLimitedException` | `domain/exception` |
| `SessionRepository`, `RefreshTokenRepository`, `PasswordHasher`, `RefreshTokenGenerator`, `AuthRateLimiter` | `domain/repository` |
| `SessionEntity`, `RefreshTokenEntity` | `infra/database/entity` |
| `SpringDataSessionRepository`, `JpaSessionRepository`, `SpringDataRefreshTokenRepository`, `JpaRefreshTokenRepository` | `infra/database/repository` |
| `BcryptPasswordHasher`, `SecureRandomRefreshTokenGenerator` | `infra/security` |
| `Bucket4jAuthRateLimiter` | `infra/ratelimit` |
| `AuthProperties`, `UserPublicRoutesConfig` | `infra/config` |
| `InvalidCredentialsException`, `InvalidRefreshTokenException`, `RateLimitedException` base classes | `:common` `domain/exception` |

`infra/security` and `infra/ratelimit` are new sub-packages next to `database`, `http` and `config`.

### Error mapping in `:common`

Three base exceptions are added next to the existing ones, and `CommonExceptionHandler` maps them:

| Base exception | Response |
|---|---|
| `InvalidCredentialsException` | 401 `INVALID_CREDENTIALS`, no `WWW-Authenticate` |
| `InvalidRefreshTokenException` | 401 `INVALID_REFRESH_TOKEN`, no `WWW-Authenticate` |
| `RateLimitedException(retryAfterSeconds)` | 429 `RATE_LIMITED`, `Retry-After: <seconds>` |

They are not subclasses of `UnauthenticatedException`, because that one means `UNAUTHENTICATED` and sends the bearer challenge. `CLAUDE.md` lists three base exceptions today; the list grows to six. The `:user` exceptions subclass these, so there is still no feature exception handler.

If `be-boards` or `be-labels` land first and already added base exceptions to `:common` (for example `FieldOutOfRangeException`), these three are added beside them.

### Request DTOs and validation

All request properties are nullable `String`s with no default, as the project rules require.

| DTO | Property | Constraints |
|---|---|---|
| `RegisterRequest` | `email` | `@Required`, `@ValidText(max = 254)`, `@ValidEmail` |
| | `password` | `@Required`, `@ValidPassword` |
| | `refreshTokenTransport` | `@Required`, `@ValidEnum(RefreshTokenTransportDto::class)` |
| `LoginRequest` | `email`, `password` | `@Required` |
| | `refreshTokenTransport` | `@Required`, `@ValidEnum` |
| `RefreshRequest`, `LogoutRequest` | `refreshTokenTransport` | `@Required`, `@ValidEnum` |
| | `refreshToken` | none (optional) |

- `@ValidEmail` checks the normalized value against the RFC 5322 `addr-spec` (dot-atom or quoted-string local part, dot-atom or domain-literal domain, ASCII only, no comments or folding whitespace) with a hand-written regex and reports `INVALID_FORMAT`. Hibernate's `@Email` is not used, because its rules differ from the shared spec.
- `@ValidPassword` works on `normalizePassword(value)`: `checkText(min = 12, max = 128, multiline = false)` for `REQUIRED`, `INVALID_CHARACTER`, `TOO_SHORT`, `TOO_LONG` with their limits, then one `Ll`, one `Lu` and one `Nd` character or `PASSWORD_TOO_WEAK`. It reports through the same dynamic payload as `@ValidText`, so `min` and `max` reach the field error.
- `@Required` on the login `password` must treat `""` as missing but must not trim: a password of only spaces is a value. If the existing `@Required` validator trims, login gets a password-specific check instead. This is verified in the first validation task.
- Refresh and logout use `@Valid`. Register and login call `RequestValidator.validate(request)` themselves, after the rate limit check (see the next section).

### Order of checks in the controller and service

For register and login:

1. Spring reads the body (413, 415, `MALFORMED_REQUEST` happen here).
2. The controller method takes the raw request without `@Valid`, computes the rate limit key from `email`, and calls `AuthRateLimiter`. Over the limit throws `AuthRateLimitedException`.
3. The controller calls `RequestValidator.validate(request)`.
4. The mapper builds the domain input (`normalizeText(email).lowercase(Locale.ROOT)`, `normalizePassword(password)`), and the service runs.

Step 2 must come before step 3 so that 429 wins over 400, which is why these two methods don't use `@Valid`. Refresh and logout use `@Valid`.

This is the order of the shared spec `api-errors` ("Error precedence"): parsing, then the rate limit, then field validation (D21).

### Rate limiter

- Domain interface: `AuthRateLimiter.check(action: AuthAction, email: String)`, with `AuthAction` `LOGIN` or `REGISTER`. It throws `AuthRateLimitedException(retryAfterSeconds)` when no token is left.
- The controller passes `normalizeText(email).lowercase(Locale.ROOT)` and skips the call when the result is empty.
- `Bucket4jAuthRateLimiter` keeps one Caffeine cache per action. The cache key is the SHA-256 of the email, so a long email can't bloat the cache. Each value is a bucket4j `Bucket` with capacity 5 and a greedy refill of 5 per minute, both from config.
- Caffeine uses `maximumSize` from config (default 100,000) and `expireAfterAccess` equal to the refill period. A bucket that was idle for a full period is full again, so dropping it changes nothing.
- bucket4j gets a `TimeMeter` that reads the `Clock` bean, and Caffeine gets a `Ticker` from the same clock. Tests move `MutableClock` instead of sleeping.
- `Retry-After` is the bucket's wait time rounded up to whole seconds, at least 1.
- The implementation has a package-internal `clear()` that test cleanup calls, so E2E tests that reuse an email don't hit the limit.

Alternative considered: bucket4j's own Caffeine proxy manager. It adds a second artifact and async plumbing for no gain with in-memory buckets.

### Password hashing

- Domain interface `PasswordHasher` with `hash(password): String` and `matches(password, hash): Boolean`. Callers pass the NFC-normalized password.
- `BcryptPasswordHasher` computes `Base64(SHA-256(password as UTF-8))` (44 ASCII characters) and passes it to Spring Security's `BCryptPasswordEncoder(cost)`. The cost comes from `cardigan.user.auth.bcrypt-cost`, default 12. The test profile sets 4.
- For an unknown email, login calls `matches` against a dummy hash that the hasher creates once at startup with the same cost, then throws the same exception. This gives the equal work `api-errors` asks for.
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

- The entities hold plain UUID columns for `user_id` and `session_id`, not `@ManyToOne` associations. The foreign keys are declared so that `ddl-auto` creates them with `on delete cascade`.
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

- **Register**: one transaction. `existsByEmail` → `EmailAlreadyUsedException` (a `UniquenessConflictException` on `email`). Otherwise save the user, create the session and the first token. The unique constraint on `users.email` covers the race: `JpaUserRepository.save` flushes and translates the constraint violation into the same exception.
- **Login**: `UserRepository.findByEmail` is added. The password check runs outside a database transaction, so a connection isn't held during the BCrypt work. Creating the session and token is its own short transaction.
- **Logout**: one transaction. Look up the token by hash. If it exists, its session has the request's transport and isn't revoked, set `revoked_at`. A rotated token takes the same path, which gives D17's "rotated token revokes the session". Everything else is a no-op. The controller always answers 204.

### Cookie handling

- `RefreshTokenCookie` builds a `ResponseCookie`: name `cardigan_refresh_token`, `httpOnly`, `secure`, `sameSite("Strict")`, `path("/api/v1/auth")`, `maxAge` of the refresh-token TTL, no domain. The clearing cookie is the same with an empty value and `maxAge(0)`.
- The controller reads the cookie with `@CookieValue(required = false)`, annotated `@Parameter(hidden = true)`, so generated clients get no cookie parameter (D20). The descriptions of `refresh` and `logout` name the cookie.
- With `COOKIE`, the body token is ignored. With `BODY`, the cookie is ignored.
- `Secure` is always set. Browsers accept `Secure` cookies from `http://localhost`, so local web development works through the dev proxy.

### Response DTOs and OpenAPI

- `AuthSessionDto(accessToken, accessTokenExpiresAt, refreshToken: String?, user: CurrentUserDto)` and `AuthTokensDto(accessToken, accessTokenExpiresAt, refreshToken: String?)`.
- `refreshToken` has `@JsonInclude(NON_NULL)`, so it is absent for `COOKIE`. The customizer already leaves a nullable Kotlin property out of `required`. The snapshot must not show `nullable: true` for it; if springdoc adds it, the customizer clears it for response schemas the same way it does for request schemas.
- `@ApiErrors` per operation: register 400 `VALIDATION_FAILED`, 409 `CONFLICT`, 429 `RATE_LIMITED`; login 400 `VALIDATION_FAILED`, 401 `INVALID_CREDENTIALS`, 429 `RATE_LIMITED`; refresh 400 `VALIDATION_FAILED`, 401 `INVALID_REFRESH_TOKEN`; logout 400 `VALIDATION_FAILED`. If the customizer already documents `VALIDATION_FAILED` for validated bodies, those entries are dropped.
- `UserPublicRoutesConfig` registers a `PublicRoutes` bean with the four `POST` routes. The customizer then omits the bearer requirement and 401 `UNAUTHENTICATED` for them.

### Cleanup job

- `SessionCleanupService.deleteEndedSessions()` is `@Scheduled(cron = "\${cardigan.user.auth.session-cleanup-cron}", zone = "UTC")` with the default `0 0 3 * * *`, and `@Transactional`.
- It runs one delete: sessions where `revoked_at is not null`, or where no token exists with `rotated_at is null and expires_at > :now`. Tokens go with them through the cascade.
- `@EnableScheduling` is added to `ApplicationConfig` in `:app`. Tests call the method directly.

### Configuration

`AuthProperties` binds `cardigan.user.auth`:

```yaml
cardigan:
  user:
    auth:
      refresh-token-ttl: 30d
      bcrypt-cost: 12
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

The values live in `:app` `application.yaml`. `application-test.yaml` overrides `bcrypt-cost: 4`.

### Dependencies

`bucket4j` (`com.bucket4j:bucket4j_jdk17-core`) and Caffeine (`com.github.ben-manes.caffeine:caffeine`) go into `gradle/libs.versions.toml` and `:user`. The latest stable versions are looked up at implementation time. Caffeine's version comes from the Spring Boot BOM if it manages one.

### Tests

- Unit (fakes, `MutableClock`): `AuthService` for every refresh branch, logout branches, register and login; `@ValidEmail` and `@ValidPassword` with adversarial inputs; the rate limiter with the clock; the hasher (72-byte boundary, NFC forms, salt).
- Persistence (`@DataJpaTest`): the atomic claim statement, cascade deletes, the unique hash, the cleanup delete.
- Module integration: parallel refresh and parallel register with real threads against Postgres.
- E2E: every scenario of the four spec files, including cookie attributes, the OpenAPI snapshot, and the log check.
- `E2eTest.signIn` registers and logs in over HTTP. `UserFixtures.createUser` stays for tests that need a user without going through the API.

## Risks / Trade-offs

- [Email-only limits don't stop one client trying many emails, and anyone can use up a victim's bucket and block that email's login for up to a minute] → Accepted by the user (D10). The numbers are config values.
- [Limits are per instance and reset on restart] → Accepted in `CLAUDE.md`. Running more than one instance multiplies the effective limit.
- [SHA-256 pre-hashing means a leaked BCrypt hash could be attacked with hashes from other breaches ("password shucking")] → Low risk at this scale, accepted with D5.
- [The cleanup job runs on every instance] → The delete is idempotent, so concurrent runs are harmless. No ShedLock.
- [A lost refresh response logs the user out, because the old token then counts as reuse] → Required by the shared spec ("There is no grace period").
- [`sessions` and `refresh_tokens` exist only through `ddl-auto`] → They must be in the baseline migration before the first production deploy (deferred, see `decisions.md`).
- [BCrypt cost 12 takes a few hundred milliseconds of CPU per login] → Virtual threads keep request threads free, and the email limit caps the work per account. There is no global cap on logins across many emails.

## Migration Plan

No data migration. On `dev`, `ddl-auto: update` creates the two tables on the next start. Rollback is reverting the commits; the unused tables can stay or be dropped by hand.

## Open Questions

- The exact bucket4j and Caffeine versions, looked up when the dependencies are added.
