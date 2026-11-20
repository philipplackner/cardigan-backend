# Proposal

## Why

The backend can verify access tokens but can't issue them to real users: there is no register, login, refresh or logout. The mobile and web auth chunks (`mob-auth-connect`, `web-auth-connect`) generate their clients from these endpoints, so they are blocked until the endpoints are in the OpenAPI snapshot (breakdown chunk `be-auth`). See `decisions.md` for every settled choice.

## What Changes

- Four public endpoints under `/api/v1/auth`, tag `Auth`: `register` (201), `login` (200), `refresh` (200), `logout` (204).
- Register and login return the tokens and the user. Refresh returns tokens only. `refreshToken` is in the body only with the `BODY` transport.
- Register validates email and password per `domain-user`. Login validates required fields only; everything else is 401 `INVALID_CREDENTIALS`.
- Passwords are NFC-normalized, hashed with SHA-256, base64 encoded and then hashed with BCrypt at cost 12 (config).
- Sessions and refresh tokens are stored in two new tables in `user_service` (`sessions`, `refresh_tokens`), from JPA entities only. Refresh tokens are 32 random bytes, stored as SHA-256 hashes, rotated on every refresh. Reuse of a rotated token revokes its session.
- The `COOKIE` transport uses the cookie `cardigan_refresh_token` with `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth` and a 30-day `Max-Age`.
- Logout takes the same request shape as refresh, always answers 204 for a valid request, and also revokes the session when given a rotated older token.
- Rate limiting per email only: login and register each allow 5 requests per minute per submitted email, in separate buckets. Every request counts. Over the limit: 429 `RATE_LIMITED` with `Retry-After`. Refresh and logout are not rate limited, and no client IP is read, as the shared spec `api-auth-tokens` defines.
- A scheduled job deletes ended sessions and their tokens daily at 03:00 UTC.
- No events, no emails, no Flyway migration, no session cap.
- New dependencies: bucket4j and Caffeine.
- `E2eTest.signIn` switches to register → login.
- `CLAUDE.md` is updated: the rate limiting section and tech stack row (email only), the auth routes exception to the plural-resource rule, and the current-state paragraph.
- The OpenAPI snapshot gains the four operations and the schemas `RegisterRequest`, `LoginRequest`, `RefreshRequest`, `LogoutRequest`, `AuthSessionDto`, `AuthTokensDto` and `RefreshTokenTransportDto`. Nothing existing changes, so the change is not breaking.

## Shared requirements implemented or depended on

| Spec | Requirement | How |
|---|---|---|
| `api-auth-tokens` | Protected requests use a bearer access token | The four auth routes become the public exceptions (logout added by this change) |
| `api-auth-tokens` | Access tokens are opaque to clients | Implemented (`accessTokenExpiresAt` in every token response) |
| `api-auth-tokens` | Token lifetimes | Refresh-token part implemented |
| `api-auth-tokens` | Refresh rotates the refresh token | Implemented |
| `api-auth-tokens` | Reusing a rotated refresh token revokes the session | Implemented |
| `api-auth-tokens` | Rotated refresh tokens stay recognizable | Implemented |
| `api-auth-tokens` | Invalid refresh tokens | Implemented |
| `api-auth-tokens` | Client chooses the refresh token transport | Implemented |
| `api-auth-tokens` | The backend reads the refresh token from the chosen transport | Implemented |
| `api-auth-tokens` | Missing or unknown transport | Implemented |
| `api-auth-tokens` | Web uses a protected refresh cookie | Backend part implemented |
| `api-auth-tokens` | Mobile uses the body transport | Backend part implemented |
| `api-auth-tokens` | Logout ends only the current session | Implemented |
| `api-auth-tokens` | Auth endpoints are rate limited | Implemented; the backend sets the values |
| `api-auth-tokens` | Rate limits count per email | Implemented |
| `api-auth-tokens` | Requests without an email are not rate limited | Implemented |
| `api-auth-tokens` | A failed cookie refresh clears the cookie | Implemented |
| `api-auth-tokens` | Clients end the session on a rejected refresh | Depended on (clients discard tokens on `INVALID_REFRESH_TOKEN`) |
| `domain-user` | Users have a backend-assigned identifier | Implemented at registration |
| `domain-user` | Email is stored lowercased and identifies one user | Implemented for register and login |
| `domain-user` | Email follows the RFC 5322 address format | Implemented |
| `domain-user` | Passwords follow one composition rule | Implemented |
| `text-length` | Text is normalized before validation | Depended on (email) |
| `text-length` | Passwords are normalized without trimming | Depended on |
| `api-errors` | Top-level error codes | `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `RATE_LIMITED` raised for the first time |
| `api-errors` | Field error codes | `PASSWORD_TOO_WEAK`, `INVALID_FORMAT` for email raised |
| `api-errors` | Login failures look the same | Implemented |
| `api-errors` | Uniqueness clashes are conflicts | Implemented for a used email |
| `api-errors` | Error precedence | Parsing, rate limit, validation, credentials and uniqueness order implemented for auth |
| `api-errors` | Rate limit is checked before credentials | Implemented for login and register |
| `api-conventions` | Documented fields are always present | `refreshToken` is the documented exception |

## Capabilities

### New Capabilities
- `auth-sessions`: the register, login, refresh and logout endpoints, their request and response shapes, session and refresh-token storage, the refresh cookie, and cleanup of ended sessions, where the backend adds to `api-auth-tokens`.
- `auth-rate-limiting`: the limit values for login and register and how the backend keeps the counters, on top of the shared spec `api-auth-tokens`.

### Modified Capabilities
- `user-accounts`: adds how the password hash is produced.
- `request-authentication`: "Public routes are listed explicitly" now names the four auth routes as public.

## Impact

- `:user`: new controller, DTOs, validators, services, domain models, repositories, entities, rate limiter, scheduled job, config properties, `PublicRoutes` bean.
- `:common`: the base exceptions `InvalidCredentialsException`, `InvalidRefreshTokenException` and `RateLimitedException`, mapped in `CommonExceptionHandler` (401 without `WWW-Authenticate`, 429 with `Retry-After`), and a public `reject(FieldViolation)`.
- `:app`: `@EnableScheduling`, new config values in `application.yaml`, a low BCrypt cost in the test profile, new E2E tests, `E2eTest.signIn`, the OpenAPI snapshot.
- Database: tables `user_service.sessions` and `user_service.refresh_tokens`. They must be part of the baseline migration before the first production deploy.
- Dependencies: bucket4j, Caffeine.
- Other repos: mobile and web can generate their auth clients once the snapshot is updated.
