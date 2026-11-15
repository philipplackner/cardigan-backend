# Tasks

Every group ends with `./gradlew build` passing and a commit, as `CLAUDE.md` requires.

## 1. Dependencies and configuration

- [ ] 1.1 Look up the latest stable bucket4j and Caffeine versions, add them to `gradle/libs.versions.toml` and to `:user`. Verify with `./gradlew :user:dependencies` showing both and `./gradlew build` passing.
- [ ] 1.2 Add `AuthProperties` (`cardigan.user.auth.*` as in `design.md`) with validation of its values, the values in `:app` `application.yaml`, and `bcrypt-cost: 4` in `application-test.yaml`. Verify with a binding test for the defaults and a test that a cost below 4 or a non-positive capacity fails startup.

## 2. Error mapping in `:common`

- [ ] 2.1 Add the base exceptions `InvalidCredentialsException`, `InvalidRefreshTokenException` and `RateLimitedException(retryAfterSeconds)` and map them in `CommonExceptionHandler`. Verify with E2E tests through the test-only API: 401 `INVALID_CREDENTIALS` and 401 `INVALID_REFRESH_TOKEN` without `WWW-Authenticate`, 429 `RATE_LIMITED` with `Retry-After`, each with the exact problem field set.

## 3. Password hashing

- [ ] 3.1 Add `PasswordHasher` in `domain` and `BcryptPasswordHasher` (SHA-256, base64, BCrypt with the configured cost, dummy hash for unknown emails). Verify with unit tests for every `user-accounts` delta scenario: the hash is BCrypt with the configured cost, two hashes of one password differ, 128-character passwords that differ in the last character don't match, a 400-byte password hashes and matches, and a wrong password doesn't match the dummy hash.

## 4. Validation

- [ ] 4.1 Add `@ValidEmail` and `@ValidPassword` in `:user` `api/validation`. Check how `@Required` treats a whitespace-only string and handle the login password as `design.md` describes. Verify with adversarial unit tests: the `domain-user` email scenarios (valid plus-address, `ann@`, non-ASCII, comment, 254 and 255 characters, quoted local part, leading and trailing dots), password lengths 11, 12, 128 and 129 in code points, each missing character class, non-ASCII letters and digits, a control character, an astral-plane character counted once, and a password of only spaces.
- [ ] 4.2 Add the request DTOs, `RefreshTokenTransportDto` and the mappers with `normalizeText` plus lowercasing for the email and `normalizePassword` for the password. Verify with mapper unit tests for `  Ann@Example.com ` and for a decomposed password.

## 5. Sessions and refresh tokens

- [ ] 5.1 Add `Session`, `RefreshToken`, `RefreshTokenTransport`, the token hash function, `RefreshTokenGenerator` and its `SecureRandom` implementation. Verify with unit tests: the token has 43 base64url characters, 1,000 generated tokens are distinct, and the hash is the lowercase hex SHA-256.
- [ ] 5.2 Add `SessionEntity`, `RefreshTokenEntity`, the Spring Data repositories, the adapters, the mappers, and `UserRepository.findByEmail`. Verify with `@DataJpaTest` tests: the round trip at millisecond precision, the unique `token_hash`, the transport check constraint, deleting a user removes its sessions and tokens, the claim statement updates one row the first time and zero the second, and a duplicate email on `save` becomes `EmailAlreadyUsedException`.

## 6. Rate limiter

- [ ] 6.1 Add `AuthRateLimiter` in `domain`, `AuthRateLimitedException`, and `Bucket4jAuthRateLimiter` with the clock-driven time source and the bounded Caffeine caches. Verify with unit tests on `MutableClock` for the `auth-rate-limiting` scenarios: the sixth request fails, one more after 12 seconds, separate login and register counts, separate emails, `Retry-After` between 1 and 12 and never 0, an idle bucket is evicted after the period, and the cache never holds more than the configured maximum.

## 7. Auth service

- [ ] 7.1 Add `AuthService.register` and `login`. Verify with unit tests on fakes: register stores a lowercased email and a hash and creates a session with one token expiring in 30 days; a used email throws; login with a wrong password and with an unknown email throw the same exception and both call the hasher once; login in another case succeeds; a password with a leading space fails; 20 logins leave 20 active sessions.
- [ ] 7.2 Add `AuthService.refresh` returning a result type. Verify with unit tests on fakes and `MutableClock` for each branch in `design.md`: success rotates and issues a 30-day token; unknown, missing, expired (at the exact expiry and 1 ms before), revoked and wrong-transport tokens fail; the wrong-transport and expired cases change nothing; a rotated token revokes the session and the current token then fails; a second session of the same user is unaffected; a session survives 29-day refresh intervals.
- [ ] 7.3 Add `AuthService.logout`. Verify with unit tests: the current token revokes the session, a rotated token revokes it, and unknown, missing, expired, already revoked and wrong-transport tokens change nothing and don't throw.
- [ ] 7.4 Verify concurrency with module integration tests against Postgres: two parallel refreshes with one token give at most one success and a revoked session afterwards, and two parallel registrations of one email give one user and one `EmailAlreadyUsedException`. Also verify that the reuse revocation is committed when the request fails.

## 8. Endpoints

- [ ] 8.1 Add `AuthController` with register and login (rate limit, then validation, then service), `AuthSessionDto`, `@ApiErrors`, the `PublicRoutes` bean and `RefreshTokenCookie`. Verify with E2E tests for the `auth-sessions` scenarios on register and login (status codes, body fields per transport, absent `refreshToken`, the returned access token on `/users/me`, validation errors, 409, identical login failures, cookie attributes also over plain HTTP, no cookie for `BODY`) and the new `request-authentication` scenarios (no token, broken token, `GET` on an auth path).
- [ ] 8.2 Add refresh and logout with `AuthTokensDto`. Verify with E2E tests for the remaining `auth-sessions` scenarios: refresh per transport, missing transport, missing token, cookie refresh without a cookie, wrong transport, the clearing cookie on a failed `COOKIE` refresh (expired, reused, no cookie) and none on a failed `BODY` refresh or a 400, reuse persisted across requests, logout with an expired access token, twice, with an unknown token, with the wrong transport, with a rotated token, the clearing cookie with and without a cookie, and no `Set-Cookie` for `BODY`. Include the shared `api-auth-tokens` scenarios "Idle for 31 days", "Access token after logout" and "Other device unaffected".
- [ ] 8.3 Verify rate limiting over HTTP with E2E tests for the `auth-rate-limiting` scenarios: the sixth login and register, a correct password over the limit creates no session, 429 before 400 on register, requests without an email, an unparseable body uses no limit, 100 refreshes never answer 429, and the limiter is cleared between tests.
- [ ] 8.4 Verify with an E2E test using `OutputCaptureExtension` that a full register, login, refresh, logout flow logs no password, password hash, refresh token, token hash or access token.

## 9. Cleanup job

- [ ] 9.1 Add `SessionCleanupService` with the scheduled delete and `@EnableScheduling` in `ApplicationConfig`. Verify with a persistence or module integration test for the "Ended sessions are deleted daily" scenarios (logged out, expired by one second, active with 41 tokens kept, a deleted token answers 401) and a context test that the cron expression resolves from config.

## 10. Contract, test helper and docs

- [ ] 10.1 Run `./gradlew :app:updateOpenApiSnapshot` and review the diff: four operations with the tag `Auth` and the agreed operation IDs, the seven schemas, no bearer requirement and no `UNAUTHENTICATED` on them, `refreshToken` neither required nor nullable, no parameters on `refresh` and `logout` and the cookie name in their descriptions, and no change to existing paths or schemas. Verify with `OpenApiSnapshotTest` passing.
- [ ] 10.2 Switch `E2eTest.signIn` to register → login over HTTP. Verify that all existing E2E tests pass unchanged.
- [ ] 10.3 Update `CLAUDE.md`: rate limiting is per email for login and register only, with no IP and no refresh or logout limit (section and tech stack row); the auth routes are the agreed exception to the plural-resource rule; the base exception list; the `infra/security` and `infra/ratelimit` packages; the current-state paragraph and the E2E row in the test table. Verify by reading the file against `decisions.md`.
- [ ] 10.4 Run `./gradlew build` and start the app once with `SPRING_PROFILES_ACTIVE=dev ./gradlew :app:bootRun`. Verify that the dev database has `user_service.sessions` and `user_service.refresh_tokens` with the columns, foreign keys with cascade, and unique index from `design.md`, and that a register, refresh and logout round trip with `curl` works.

## Workflow follow-up

- Propose the shared-spec change in `cardigan-shared-specs` for `api-auth-tokens` ("Auth endpoints are rate limited", "What rate limits count"): email-only limits on login and register, no refresh limit. Include that a failed `COOKIE` refresh clears the cookie, and that parsing errors come before the rate limit on login and register. Then remove the deviation notes from `auth-rate-limiting`.
- Include `sessions` and `refresh_tokens` in the baseline Flyway migration before the first production deploy.
- Archive the change after review.
