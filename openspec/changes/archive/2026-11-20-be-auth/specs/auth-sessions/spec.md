# Spec Delta

## Purpose

Defines the backend's register, login, refresh and logout endpoints: their paths, request and response shapes, how sessions and refresh tokens are stored and ended, and the refresh cookie. Token behavior shared with clients lives in the shared spec `api-auth-tokens`; this spec only adds what that spec leaves to the backend.

## ADDED Requirements

### Requirement: Auth endpoints and status codes
The backend SHALL serve `POST /api/v1/auth/register` (201), `POST /api/v1/auth/login` (200), `POST /api/v1/auth/refresh` (200) and `POST /api/v1/auth/logout` (204, no body). In the OpenAPI document they SHALL have the tag `Auth` and the operation IDs `register`, `login`, `refresh` and `logout`.

#### Scenario: Register succeeds
- **WHEN** a client registers with a new email, a valid password and `refreshTokenTransport` `BODY`
- **THEN** the response is 201

#### Scenario: Logout succeeds
- **WHEN** a client logs out with its refresh token
- **THEN** the response is 204 with an empty body

#### Scenario: Operations in the document
- **WHEN** the OpenAPI snapshot is inspected
- **THEN** it has the four paths with the tag `Auth` and the operation IDs `register`, `login`, `refresh` and `logout`

### Requirement: Register and login return tokens and the user
A successful register or login SHALL return the schema `AuthSessionDto` with `accessToken`, `accessTokenExpiresAt`, `user` (the same `id`, `email` and `createdAt` as `GET /api/v1/users/me`) and, only with the `BODY` transport, `refreshToken`. With `COOKIE`, the `refreshToken` property SHALL be absent, not `null`.

#### Scenario: Register with the body transport
- **WHEN** a client registers with `  Ann@Example.com `, a valid password and `BODY`
- **THEN** the body has `accessToken`, `accessTokenExpiresAt`, `refreshToken` and `user` with `email` `ann@example.com`

#### Scenario: Login with the cookie transport
- **WHEN** a client logs in with `COOKIE`
- **THEN** the body has `accessToken`, `accessTokenExpiresAt` and `user`, and no `refreshToken` property

#### Scenario: Returned access token works
- **WHEN** the `accessToken` from a register response is sent to `GET /api/v1/users/me`
- **THEN** the response is 200 with the same `id` as `user.id`

### Requirement: Refresh returns tokens only
A successful refresh SHALL return the schema `AuthTokensDto` with `accessToken`, `accessTokenExpiresAt` and, only with the `BODY` transport, `refreshToken`. It SHALL NOT contain the user.

#### Scenario: Refresh with the body transport
- **WHEN** a client refreshes with a valid refresh token and `BODY`
- **THEN** the response is 200 and the body has exactly `accessToken`, `accessTokenExpiresAt` and `refreshToken`

#### Scenario: Refresh with the cookie transport
- **WHEN** a client refreshes with a valid refresh cookie and `COOKIE`
- **THEN** the body has exactly `accessToken` and `accessTokenExpiresAt`

### Requirement: Register validates email and password
Register SHALL require `email`, `password` and `refreshTokenTransport`, and SHALL check the email and password against every rule of the shared spec `domain-user` ("Email is stored lowercased and identifies one user", "Email follows the RFC 5322 address format", "Passwords follow one composition rule"). Broken rules SHALL produce 400 `VALIDATION_FAILED` with one field error per broken field, and no user SHALL be stored.

#### Scenario: Empty body
- **WHEN** a client registers with `{}`
- **THEN** the response is 400 `VALIDATION_FAILED` with `REQUIRED` for `email`, `password` and `refreshTokenTransport`

#### Scenario: Bad email and weak password
- **WHEN** a client registers with the email `ann@` and the password `alllowercase12`
- **THEN** the field errors are `email` `INVALID_FORMAT` and `password` `PASSWORD_TOO_WEAK`, and no user is stored

#### Scenario: Password length limits
- **WHEN** a client registers with a password of 11 characters, and another with 129
- **THEN** the field errors are `password` `TOO_SHORT` with `min` 12, and `password` `TOO_LONG` with `max` 128

#### Scenario: Unknown transport
- **WHEN** a client registers with `refreshTokenTransport` `HEADER`
- **THEN** the field error is `refreshTokenTransport` `INVALID_VALUE`

### Requirement: Register rejects a used email
Register with an email that is already stored, compared after normalization and lowercasing, SHALL produce 409 `CONFLICT` with the field error `email` `ALREADY_USED`. This SHALL also hold when two registrations for the same email are processed at the same time: exactly one SHALL succeed. Field validation SHALL come before this check.

#### Scenario: Same email in another case
- **WHEN** `ann@example.com` is registered and a client registers `ANN@Example.com`
- **THEN** the response is 409 `CONFLICT` with `email` `ALREADY_USED`

#### Scenario: Parallel registration
- **WHEN** two register requests for the same new email are processed at the same time
- **THEN** one answers 201 and the other 409 `CONFLICT`

#### Scenario: Email stored after the existence check
- **WHEN** a registration stores a user whose email another registration stored after the first one's existence check
- **THEN** the result is the same 409 `CONFLICT` with `email` `ALREADY_USED`, and one user exists

#### Scenario: Used email and weak password
- **WHEN** a client registers a used email with a weak password
- **THEN** the response is 400 `VALIDATION_FAILED`, not 409

### Requirement: Login validates required fields only
Login SHALL answer 400 `VALIDATION_FAILED` only when `email`, `password` or `refreshTokenTransport` is missing, `null`, empty after normalization (the email is trimmed, the password is not), or the transport is unknown. It SHALL NOT apply the email format, the length limits or the password composition rule: any other email or password that does not match a user SHALL produce 401 `INVALID_CREDENTIALS`.
#### Scenario: Missing password
- **WHEN** a client logs in without `password`
- **THEN** the response is 400 `VALIDATION_FAILED` with `password` `REQUIRED`

#### Scenario: Malformed email
- **WHEN** a client logs in with the email `not an email` and any password
- **THEN** the response is 401 `INVALID_CREDENTIALS`

#### Scenario: Short password
- **WHEN** a client logs in with a known email and the password `abc`
- **THEN** the response is 401 `INVALID_CREDENTIALS`, not a field error

#### Scenario: Blank email
- **WHEN** a client logs in with the email `"   "` and any password
- **THEN** the response is 400 `VALIDATION_FAILED` with `email` `REQUIRED`

#### Scenario: Empty password
- **WHEN** a client logs in with a known email and the password `""`
- **THEN** the response is 400 `VALIDATION_FAILED` with `password` `REQUIRED`

#### Scenario: Password of only spaces
- **WHEN** a client logs in with a known email and the password `"   "`
- **THEN** the response is 401 `INVALID_CREDENTIALS`

#### Scenario: Unknown transport on login
- **WHEN** a client logs in with valid credentials and `refreshTokenTransport` `HEADER`
- **THEN** the response is 400 `VALIDATION_FAILED` with `refreshTokenTransport` `INVALID_VALUE` and no session is created

### Requirement: A login email that cannot be stored counts as unknown
A login email that could never be stored (a character `text-length` forbids, or more than 254 characters) SHALL be treated as an unknown email: 401 `INVALID_CREDENTIALS` with the same password verification work, without a database lookup.

#### Scenario: Control character in the email
- **WHEN** a client logs in with an email containing U+0000 and any password
- **THEN** the response is 401 `INVALID_CREDENTIALS`, identical to the response for an unknown email

### Requirement: Login matches the lowercased email and the exact password
Login SHALL normalize and lowercase the email before matching it, and SHALL compare the password after NFC normalization without trimming it. A wrong password and an unknown email SHALL produce identical 401 `INVALID_CREDENTIALS` responses, as the shared spec `api-errors` ("Login failures look the same") requires.

#### Scenario: Email in another case
- **WHEN** a user registered as `Ann@Example.com` logs in with ` ANN@example.COM ` and the right password
- **THEN** the response is 200

#### Scenario: Password with surrounding space
- **WHEN** a user registered with the password `Correct horse 7` logs in with ` Correct horse 7`
- **THEN** the response is 401 `INVALID_CREDENTIALS`

#### Scenario: Unknown email and wrong password
- **WHEN** one login uses an unknown email and another a wrong password
- **THEN** both responses are 401 `INVALID_CREDENTIALS` with the same body and the same headers, not counting `Date`, and neither has `WWW-Authenticate`

#### Scenario: Equal work for an unknown email
- **WHEN** a login uses an unknown email
- **THEN** the password hasher verifies the submitted password once, as for a known email

### Requirement: Sessions and refresh tokens are stored hashed
Each register and login SHALL create one session with its user and its transport, and one refresh token in it. A refresh token SHALL be a random value of at least 256 bits. The backend SHALL store only its SHA-256 hash, with its expiry 30 days after issue, and SHALL never store or log the token itself.

#### Scenario: Stored token
- **WHEN** a client logs in and receives a refresh token
- **THEN** the database holds no value equal to that token, and holds its SHA-256 hash with an expiry 30 days after the login

#### Scenario: Two logins
- **WHEN** a user logs in twice
- **THEN** two sessions exist, each with its own refresh token

### Requirement: No limit on sessions per user
The backend SHALL NOT limit how many active sessions a user has. A new login SHALL NOT end or change any other session.

#### Scenario: Many sessions
- **WHEN** a user logs in 20 times, one minute apart, and then refreshes with the token from the first login
- **THEN** the refresh succeeds

### Requirement: Refresh and logout take the transport and an optional token
Refresh and logout requests SHALL require `refreshTokenTransport` and MAY carry `refreshToken`. A missing or unknown transport SHALL produce 400 `VALIDATION_FAILED`. A missing `refreshToken` SHALL NOT be a field error: for refresh it is 401 `INVALID_REFRESH_TOKEN` as the shared spec `api-auth-tokens` ("Invalid refresh tokens") requires, and for logout it is 204.

#### Scenario: Refresh without transport
- **WHEN** a client refreshes with `{}`
- **THEN** the response is 400 `VALIDATION_FAILED` with `refreshTokenTransport` `REQUIRED`

#### Scenario: Refresh without a token
- **WHEN** a client refreshes with `BODY` and no `refreshToken`
- **THEN** the response is 401 `INVALID_REFRESH_TOKEN`

#### Scenario: Cookie refresh without a cookie
- **WHEN** a client refreshes with `COOKIE` and no refresh cookie, with a valid `refreshToken` in the body
- **THEN** the response is 401 `INVALID_REFRESH_TOKEN` and that token's session stays active

#### Scenario: Logout without transport
- **WHEN** a client logs out with `{}`
- **THEN** the response is 400 `VALIDATION_FAILED` with `refreshTokenTransport` `REQUIRED`

#### Scenario: Body logout without a token
- **WHEN** a client logs out with `BODY` and no `refreshToken`
- **THEN** the response is 204

### Requirement: A wrong transport does not end the session
A refresh whose transport differs from its session's transport SHALL fail with 401 `INVALID_REFRESH_TOKEN` without rotating the token or revoking the session.

#### Scenario: Body token of a cookie session
- **WHEN** the refresh token of a `COOKIE` session is sent in the body with `BODY`
- **THEN** the response is 401 `INVALID_REFRESH_TOKEN`, and a later refresh with the cookie and `COOKIE` succeeds

### Requirement: Session revocation survives the failed request
When a refresh fails because a rotated token was reused, the revocation of the session SHALL be stored even though the request itself answers 401.

#### Scenario: Reuse is persisted
- **WHEN** token R1 was rotated to R2 and R1 is used again
- **THEN** the response is 401 `INVALID_REFRESH_TOKEN`, and a refresh with R2 in a new request also answers 401 `INVALID_REFRESH_TOKEN`

### Requirement: The refresh cookie
With the `COOKIE` transport, the backend SHALL set the refresh token in a cookie named `cardigan_refresh_token` with `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`, `Max-Age=2592000` and no `Domain` attribute, on every environment. With `BODY`, no response SHALL set this cookie.

#### Scenario: Cookie on web login
- **WHEN** a client logs in with `COOKIE`
- **THEN** the `Set-Cookie` header is for `cardigan_refresh_token` with `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth` and `Max-Age=2592000`, and has no `Domain`

#### Scenario: Cookie on web register
- **WHEN** a client registers with `COOKIE`
- **THEN** the `Set-Cookie` header is for `cardigan_refresh_token` with the same attributes, and a refresh with that cookie and `COOKIE` succeeds

#### Scenario: Cookie over plain HTTP
- **WHEN** the same login reaches the backend over plain HTTP
- **THEN** the cookie still has `Secure`

#### Scenario: No cookie for the body transport
- **WHEN** a client registers, logs in or refreshes with `BODY`
- **THEN** the response has no `Set-Cookie` header

### Requirement: A failed cookie refresh clears the cookie
As the shared spec `api-auth-tokens` ("A failed cookie refresh clears the cookie") requires, a refresh with the `COOKIE` transport that answers 401 `INVALID_REFRESH_TOKEN` SHALL send a `Set-Cookie` header for `cardigan_refresh_token` with the same attributes and `Max-Age=0`. This SHALL hold whatever the reason for the failure, also when no cookie was sent. A failed refresh with `BODY`, and a refresh that fails with any other status, SHALL NOT send a `Set-Cookie` header.

#### Scenario: Expired cookie token
- **WHEN** a client refreshes with `COOKIE` and a refresh cookie whose token has expired
- **THEN** the response is 401 `INVALID_REFRESH_TOKEN` with a `Set-Cookie` header for `cardigan_refresh_token` with `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth` and `Max-Age=0`

#### Scenario: Reused cookie token
- **WHEN** a client refreshes with `COOKIE` and a refresh cookie whose token was already rotated
- **THEN** the response is 401 `INVALID_REFRESH_TOKEN` with the clearing `Set-Cookie` header

#### Scenario: No cookie sent
- **WHEN** a client refreshes with `COOKIE` and no refresh cookie
- **THEN** the response is 401 `INVALID_REFRESH_TOKEN` with the clearing `Set-Cookie` header

#### Scenario: Failed body refresh
- **WHEN** a client refreshes with `BODY`, an unknown `refreshToken` and a valid refresh cookie
- **THEN** the response is 401 `INVALID_REFRESH_TOKEN` with no `Set-Cookie` header

#### Scenario: Missing transport
- **WHEN** a client refreshes with `{}` and a refresh cookie
- **THEN** the response is 400 `VALIDATION_FAILED` with no `Set-Cookie` header

### Requirement: The refresh cookie is not a documented parameter
The OpenAPI document SHALL NOT list the refresh cookie as a parameter of any operation. The descriptions of the refresh and logout operations SHALL name the cookie.

#### Scenario: Refresh operation in the document
- **WHEN** the OpenAPI snapshot is inspected
- **THEN** the `refresh` and `logout` operations have no parameters, and their descriptions contain `cardigan_refresh_token`

### Requirement: Logout is public and always succeeds
Logout SHALL NOT need an access token. A logout with a valid `refreshTokenTransport` SHALL answer 204 whether or not a session was revoked: also when the refresh token is missing, unknown, expired without having been rotated, already revoked, or sent with a transport other than its session's. In those cases no session SHALL change.

#### Scenario: Logout with an expired access token
- **WHEN** a client logs out with a valid refresh token and an expired access token in the `Authorization` header
- **THEN** the response is 204 and the refresh token no longer works

#### Scenario: Logout twice
- **WHEN** a client logs out twice with the same refresh token
- **THEN** both responses are 204

#### Scenario: Unknown token
- **WHEN** a client logs out with a random string as the refresh token
- **THEN** the response is 204

#### Scenario: Wrong transport
- **WHEN** the refresh token of a `COOKIE` session is sent to logout in the body with `BODY`
- **THEN** the response is 204 and the session stays active

#### Scenario: Expired current token
- **WHEN** a client logs out with a session's current refresh token one second after its expiry
- **THEN** the response is 204 and the stored session has no revocation time

### Requirement: Logout with a rotated token ends its session
When logout receives a refresh token that was already rotated and whose session is still active, it SHALL revoke that session, including its current refresh token. This SHALL also hold after the rotated token's own 30 days have passed.

#### Scenario: Old token on logout
- **WHEN** token R1 was rotated to R2 and a client logs out with R1
- **THEN** the response is 204, and a refresh with R2 answers 401 `INVALID_REFRESH_TOKEN`

#### Scenario: Old token past its own expiry
- **WHEN** token R1 was rotated to R2 on day 29 and a client logs out with R1 on day 31
- **THEN** the response is 204, and a refresh with R2 answers 401 `INVALID_REFRESH_TOKEN`

### Requirement: Logout clears the cookie only for the cookie transport
A logout with `COOKIE` SHALL send a `Set-Cookie` header for `cardigan_refresh_token` with the same attributes and `Max-Age=0`, whether or not a session was revoked. A logout with `BODY` SHALL NOT send a `Set-Cookie` header.

#### Scenario: Cookie logout without a cookie
- **WHEN** a client logs out with `COOKIE` and no refresh cookie
- **THEN** the response is 204 with a `Set-Cookie` header for `cardigan_refresh_token` with `Path=/api/v1/auth` and `Max-Age=0`

#### Scenario: Body logout
- **WHEN** a client logs out with `BODY`
- **THEN** the response has no `Set-Cookie` header

### Requirement: Ended sessions are deleted daily
A job SHALL run daily at 03:00 UTC and delete every ended session with all its refresh tokens. A session is ended when it was revoked by logout or by token reuse, or when its current refresh token has expired. Sessions that are not ended SHALL keep all their rotated tokens. The schedule SHALL be a configuration value.

#### Scenario: Logged-out session
- **WHEN** a session was logged out and the job runs
- **THEN** the session and all its refresh tokens are gone

#### Scenario: Schedule
- **WHEN** the default configuration is used and the time is `2026-11-15T09:15:30Z`
- **THEN** the job's next run is at `2026-11-16T03:00:00Z`

#### Scenario: Expired session
- **WHEN** a session's current refresh token expired at this instant or earlier and the job runs
- **THEN** the session and all its refresh tokens are gone

#### Scenario: Session about to expire
- **WHEN** a session's current refresh token expires in 1 millisecond and the job runs
- **THEN** the session remains and a refresh with that token succeeds

#### Scenario: Active session with old rotated tokens
- **WHEN** a session has refreshed 40 times over 60 days and the job runs
- **THEN** the session and all 41 of its tokens remain, and using the first token still revokes the session

#### Scenario: Deleted token
- **WHEN** a client refreshes with a token whose session the job deleted
- **THEN** the response is 401 `INVALID_REFRESH_TOKEN`

### Requirement: Auth flows have no side effects
Register, login, refresh and logout SHALL NOT publish events, send emails or call other systems. Logs SHALL NOT contain passwords, password hashes, refresh tokens, their hashes or access tokens.

#### Scenario: Log output of a full flow
- **WHEN** a client registers, logs in, refreshes and logs out, and also sends a register with a weak password, a login with a wrong password, a refresh with a rotated token, a login over the limit and a request that ends in 500, with the `com.plcoding.cardigan` loggers at `DEBUG`
- **THEN** the log output contains none of: the submitted passwords, the stored password hash, the issued refresh tokens, their stored hashes, the issued access tokens

#### Scenario: No events
- **WHEN** a client registers, logs in, refreshes and logs out
- **THEN** no application event with a payload type from `com.plcoding.cardigan` is published
