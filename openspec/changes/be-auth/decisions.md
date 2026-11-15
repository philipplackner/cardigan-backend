# Decisions: be-auth

Confirmed with Philipp Lackner on 2026-11-15. Questions were asked through the `cardigan-cd` session and are referenced by their IDs (AUTH-n).

## Summary

This change adds register, login, refresh and logout under `/api/v1/auth`, all public. Passwords are pre-hashed with SHA-256 and stored with BCrypt. Refresh tokens are opaque, stored hashed, grouped into sessions, rotated on every refresh, and revoked per session on reuse or logout. Login and register are rate limited per email only; there is no IP-based limiting, and refresh and logout have no limit. A daily job deletes ended sessions.

## Decisions

### D1 — Endpoint paths (AUTH-1)
- **Question**: Which paths do the four auth endpoints get?
- **Decision**: `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`. Tag `Auth`, operationIds `register`, `login`, `refresh`, `logout`.
- **Alternatives**: Resource style (`/api/v1/users`, `/api/v1/sessions/...`); `/api/v1/auth-sessions/*`.
- **Why**: One prefix for the refresh cookie path. Accepted deviation from the plural-resource rule for this group.
- **Depends on**: —

### D2 — Success response bodies (AUTH-2)
- **Question**: What do register, login and refresh return?
- **Decision**: Register and login return the tokens plus the user. Refresh returns tokens only. Two response schemas. `refreshToken` is present only with the `BODY` transport.
- **Alternatives**: Tokens only for all three; tokens plus `refreshTokenExpiresAt`.
- **Why**: Saves a request after sign-in.
- **Depends on**: —

### D3 — Status codes (AUTH-3)
- **Question**: Which success status codes do the endpoints use?
- **Decision**: Register 201, login 200, refresh 200, logout 204 with no body.
- **Alternatives**: 200 for register too.
- **Why**: —
- **Depends on**: —

### D4 — Logout is public (AUTH-4)
- **Question**: Does logout need a valid access token?
- **Decision**: No. Logout is a public route, identified only by the refresh token.
- **Alternatives**: Protected logout.
- **Why**: Works with an expired access token.
- **Depends on**: —

### D5 — Passwords longer than 72 bytes (AUTH-5)
- **Question**: How are passwords longer than BCrypt's 72 bytes hashed?
- **Decision**: Every password is NFC-normalized, hashed with SHA-256, base64 encoded, and the result is hashed with BCrypt.
- **Alternatives**: HMAC-SHA256 with a pepper secret; Argon2id; truncate to 72 bytes.
- **Why**: All 128 characters count, one code path, no new secret or dependency.
- **Depends on**: —

### D6 — BCrypt cost (AUTH-6)
- **Question**: Which BCrypt cost factor?
- **Decision**: 12, as a config value.
- **Alternatives**: 10; 13.
- **Why**: —
- **Depends on**: D5

### D7 — Data model (AUTH-7)
- **Question**: How are sessions and refresh tokens stored?
- **Decision**: Two tables in `user_service`. `sessions`: `id`, `user_id` (FK to `users`, cascade), `transport`, `created_at`, `revoked_at` (nullable). `refresh_tokens`: `id`, `session_id` (FK to `sessions`, cascade), `token_hash` (unique, SHA-256), `created_at`, `expires_at`, `rotated_at` (nullable).
- **Alternatives**: One `refresh_tokens` table with a family column.
- **Why**: Session-level facts live in one place.
- **Depends on**: —

### D8 — Cleanup of ended sessions (AUTH-8, AUTH-25, AUTH-26)
- **Question**: What happens to rows of ended sessions, how often does cleanup run, and how long are they kept?
- **Decision**: A scheduled job deletes ended sessions and their tokens daily at 03:00 UTC, cron from config, with no retention. A session is ended when it was logged out, revoked after reuse, or its newest refresh token has expired. Rotated tokens of active sessions are kept.
- **Alternatives**: Delete on end plus a scheduled job; no cleanup; hourly; keep ended sessions 30 days.
- **Why**: Least data kept. Running on several instances is harmless for deletes, so no ShedLock.
- **Depends on**: D7

### D9 — No Flyway migration (AUTH-9)
- **Question**: Does be-auth write Flyway migrations?
- **Decision**: No. The new tables come from JPA entities only, like `users` today.
- **Alternatives**: Write the `user_service` baseline now.
- **Why**: No conflicts with the parallel wave 3 chunks.
- **Depends on**: —

### D10 — Rate limiting is email-only (AUTH-10, AUTH-11, AUTH-17, AUTH-18)
- **Question**: Where does the client IP come from, what key does register use, and which requests consume tokens?
- **Decision**: No IP-based rate limiting. Login and register are limited per submitted email, trimmed and lowercased, in separate buckets. Every request consumes a token. No client IP is read and no forwarded-header handling is configured.
- **Alternatives**: `X-Forwarded-For` from trusted proxies; socket address only; custom proxy header; failed attempts only; no register limit; shared login and register bucket.
- **Why**: User: IP-based limiting "would be too complex for this course". The user saw the trade-off: email keys don't stop one client trying many emails, and anyone can drain a victim's bucket.
- **Depends on**: —

### D11 — Limit values (AUTH-19, AUTH-20)
- **Question**: How many login and register attempts per email are allowed?
- **Decision**: Login 5 per minute per email. Register the same numbers, with its own config keys and bucket.
- **Alternatives**: 10 per minute; 5 per 15 minutes; register 3 per hour.
- **Why**: —
- **Depends on**: D10

### D12 — Refresh and logout are not rate limited (AUTH-12, AUTH-16)
- **Question**: Is logout limited, and what key does a refresh limit use without an email?
- **Decision**: Neither is rate limited. Refresh and logout never answer 429.
- **Alternatives**: Refresh per user after token lookup; refresh per presented token; logout per IP.
- **Why**: A refresh request has no email, and guessing a 256-bit token isn't feasible.
- **Depends on**: D10

### D13 — No session cap (AUTH-13)
- **Question**: Is there a maximum number of active sessions per user?
- **Decision**: No.
- **Alternatives**: A cap that revokes the oldest session.
- **Why**: —
- **Depends on**: —

### D14 — No side effects (AUTH-14)
- **Question**: Do the auth flows have side effects beyond their own data?
- **Decision**: No events and no emails. The Modulith event registry stays unwired.
- **Alternatives**: Publish `UserRegistered` and wire in the registry.
- **Why**: Nothing consumes user events yet.
- **Depends on**: —

### D15 — Login validation (AUTH-15)
- **Question**: How strictly is the login request validated before credentials are checked?
- **Decision**: Required fields only: email, password and `refreshTokenTransport`. Any other email or password is 401 `INVALID_CREDENTIALS`. Register applies the full `domain-user` rules.
- **Alternatives**: Required plus upper bounds; full register rules.
- **Why**: A rule change must not lock out existing users, and login must not leak the rules.
- **Depends on**: —

### D16 — Refresh cookie (AUTH-21)
- **Question**: What is the refresh cookie called?
- **Decision**: `cardigan_refresh_token`, with `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`, `Max-Age` of 30 days and no `Domain` attribute.
- **Alternatives**: `__Secure-cardigan_refresh_token`; `refresh_token`.
- **Why**: —
- **Depends on**: D1

### D17 — Logout request and answers (AUTH-22, AUTH-23, AUTH-24)
- **Question**: Does logout carry `refreshTokenTransport`, what does it answer for an unusable token, and what happens with a rotated token?
- **Decision**: Logout has the same request shape as refresh, with `refreshTokenTransport` required. It always answers 204 when the request is valid, also for a missing, unknown, expired, already revoked or wrong-transport token. The cookie is cleared only for `COOKIE`. A rotated older token of an active session revokes that session.
- **Alternatives**: Auto-detect the transport; 401 `INVALID_REFRESH_TOKEN`; ignore rotated tokens.
- **Why**: Logout is idempotent and doesn't reveal whether a token exists.
- **Depends on**: D4

### D18 — Confirmed technical details (AUTH-27)
- **Question**: Is the summary, including the listed technical details, the shared understanding?
- **Decision**: Yes. The details:
  - Everything lives in `:user`. New dependencies: bucket4j and Caffeine.
  - OpenAPI schema names: `RegisterRequest`, `LoginRequest`, `RefreshRequest`, `LogoutRequest`, `AuthSessionDto` (tokens plus `user`, reusing `CurrentUserDto`), `AuthTokensDto`, enum `RefreshTokenTransportDto`.
  - A refresh token is 32 random bytes from `SecureRandom`, base64url encoded.
  - Rate limit buckets live in a Caffeine cache with expire-after-access and a maximum size from config, default 100,000 entries.
  - Tests use a low BCrypt cost through the test profile.
  - `E2eTest.signIn` switches to register → login.
  - The change is not breaking: it only adds endpoints and schemas.
- **Alternatives**: Revise something.
- **Why**: —
- **Depends on**: D1–D17

### D19 — A failed cookie refresh clears the cookie (AUTH-28)
- **Question**: Does a failed refresh with the `COOKIE` transport clear the stale refresh cookie?
- **Decision**: Yes. A refresh with `COOKIE` that answers 401 `INVALID_REFRESH_TOKEN` sends an expired `cardigan_refresh_token` cookie with the same `Path` and attributes. Settled on 2026-11-15 after the first confirmation, when the first draft of the artifacts left the cookie untouched.
- **Alternatives**: Send no `Set-Cookie` header on a failed refresh.
- **Why**: The shared spec `api-auth-tokens` was silent on this case when it was decided. It now has the requirement "A failed cookie refresh clears the cookie".
- **Depends on**: D16

### D20 — The refresh cookie is not an OpenAPI parameter (AUTH-29)
- **Question**: Is the refresh cookie listed as a parameter of refresh and logout in the OpenAPI document?
- **Decision**: No. The operation descriptions name the cookie, and generated clients get no cookie argument. Settled on 2026-11-15 after the first confirmation.
- **Alternatives**: Document it as an optional cookie parameter.
- **Why**: The cookie is `HttpOnly`; the browser sends it, not client code.
- **Depends on**: D16

### D21 — Parsing errors come before the rate limit (review of the artifacts)
- **Question**: Is it acceptable that 413, 415 and `MALFORMED_REQUEST` come before 429 on login and register?
- **Decision**: Yes, accepted as is. 429 still wins over field validation, credentials and 409.
- **Alternatives**: —
- **Why**: The limit key is the email in the body, so the body must be read first.
- **Depends on**: D10

## Facts

- Lifetimes (15 minutes, 30 days), rotation, session revocation on reuse, single-session logout, the `COOKIE`/`BODY` transports and the cookie attributes are fixed — source: shared spec `api-auth-tokens`.
- Register starts a session and carries `refreshTokenTransport` — source: `api-auth-tokens`, "Rotated refresh tokens stay recognizable" and "Client chooses the refresh token transport".
- A refresh with a missing refresh token is 401 `INVALID_REFRESH_TOKEN`, not a field error — source: `api-auth-tokens`, "Invalid refresh tokens".
- `refreshToken` is the one auth response field allowed to be absent — source: shared spec `api-conventions`, "Documented fields are always present".
- Email and password rules, lowercased emails — source: shared spec `domain-user`.
- Error codes `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `RATE_LIMITED`, `CONFLICT` with `ALREADY_USED`, identical login failures with equal hashing work, error precedence — source: shared spec `api-errors`; `common/.../domain/exception/ErrorCode.kt` already has all codes.
- `BCrypt.hashpw` in Spring Security 7.1.1 throws `IllegalArgumentException` for a password over 72 bytes — source: `spring-security-crypto-7.1.1` sources, `BCrypt.java` line 615.
- `AccessTokenIssuer`, `PublicRoutes`, `normalizeText`, `normalizePassword`, `checkText`, the `User` model and `UserRepository` exist; `UserRepository` has no lookup by email yet — source: `common/src/main`, `user/src/main`.
- bucket4j and Caffeine are not in `gradle/libs.versions.toml` yet; `CLAUDE.md` names both in the tech stack — source: `gradle/libs.versions.toml`, `CLAUDE.md`.
- No rate limiting code and no forwarded-header configuration exists — source: grep over `app/src/main`, `common/src/main`.
- Web and API are planned on the same domain behind one reverse proxy — source: shared repo `docs/breakdown/chunks/41-infra-vps.md`.
- There are no migration files; dev uses `ddl-auto: update`, tests `create-drop` — source: `CLAUDE.md`.

- The shared specs were changed after this review to match D10, D12, D19 and D21 (shared change `auth-email-rate-limits`): `api-auth-tokens` "Auth endpoints are rate limited", "Rate limits count per email", "Requests without an email are not rate limited", "A failed cookie refresh clears the cookie", and `api-errors` "Error precedence" with the rate limit after parsing. The facts above about per-IP limits describe the shared spec as it was during the review — source: `cardigan-shared-specs`, commit 64c3c09.

## Out of scope

- IP-based rate limiting and forwarded-header handling
- Rate limits on refresh and logout
- CORS
- Email verification, forgot/reset/change password, social login
- Logout from all devices, session listing, a session cap
- Flyway migrations
- Events and the Modulith event registry

## Deferred

- `CLAUDE.md` "Rate limiting" section and tech stack row still describe per-IP limits — settle before: implementation is finished (a task of this change).
- Baseline Flyway migration including `sessions` and `refresh_tokens` — settle before: the first production deploy.
