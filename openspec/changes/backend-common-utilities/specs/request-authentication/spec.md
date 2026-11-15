# Spec Delta

## Purpose

Defines how the backend issues and verifies access tokens, how its signing secret is configured, which routes need a token, and how a request's user is identified. Client-facing token rules live in the shared spec `api-auth-tokens`.

## ADDED Requirements

### Requirement: Signing secret comes from the environment
The service SHALL read the access-token signing secret from `JWT_SECRET` as base64, SHALL ship no default for it, and SHALL fail startup when it is missing, not valid base64, or shorter than 256 bits after decoding.

#### Scenario: Secret missing
- **WHEN** the service starts without `JWT_SECRET`
- **THEN** startup fails and the service does not begin serving requests

#### Scenario: Secret too short
- **WHEN** `JWT_SECRET` decodes to 31 bytes
- **THEN** startup fails and the log names the problem without printing the secret

#### Scenario: Valid secret
- **WHEN** `JWT_SECRET` decodes to 32 bytes
- **THEN** startup completes

### Requirement: Access tokens are signed JWTs with fixed claims
Access tokens SHALL be JWTs signed with HS256 and the configured secret, and SHALL carry exactly the claims `sub` (the user ID), `type` with the value `access`, `iat` and `exp`. `exp` SHALL be 15 minutes after `iat`.

#### Scenario: Issued token
- **WHEN** the backend issues an access token for a user
- **THEN** its header names HS256 and its claims are `sub` with the user's ID, `type` `access`, `iat` and `exp` 900 seconds later

### Requirement: Issue time is whole seconds
The backend SHALL truncate the issue time of an access token to whole seconds, so that `accessTokenExpiresAt` always has the fractional part `.000` and equals the token's `exp`, as required by the shared spec `api-auth-tokens` ("Token lifetimes").

#### Scenario: Issue in the middle of a second
- **WHEN** an access token is issued at `2026-11-15T09:15:30.789Z`
- **THEN** `iat` is `2026-11-15T09:15:30Z`, and `accessTokenExpiresAt` is `2026-11-15T09:30:30.000Z`

#### Scenario: Use at the reported expiry
- **WHEN** that token is used at `2026-11-15T09:30:30.000Z`
- **THEN** the response is 401 `UNAUTHENTICATED`

#### Scenario: Use 1 ms before the reported expiry
- **WHEN** that token is used at `2026-11-15T09:30:29.999Z`
- **THEN** the request is accepted

### Requirement: Verification accepts only valid access tokens
A protected request SHALL be authenticated only when its bearer token has an HS256 signature that verifies with the configured secret, `type` `access`, a `sub` that is a UUID, and a current time before `exp`, with no clock-skew allowance. Any other bearer value SHALL produce 401 `UNAUTHENTICATED`.

#### Scenario: alg none
- **WHEN** a token with header `alg` `none` and valid-looking claims is sent
- **THEN** the response is 401 `UNAUTHENTICATED`

#### Scenario: Other type claim
- **WHEN** a correctly signed token has `type` `refresh`
- **THEN** the response is 401 `UNAUTHENTICATED`

#### Scenario: Signed with another secret
- **WHEN** a token is signed with HS256 and a different 256-bit secret
- **THEN** the response is 401 `UNAUTHENTICATED`

#### Scenario: Subject is not a UUID
- **WHEN** a correctly signed access token has `sub` `admin`
- **THEN** the response is 401 `UNAUTHENTICATED`

#### Scenario: One second after expiry
- **WHEN** an access token is used 1 second after its `exp`
- **THEN** the response is 401 `UNAUTHENTICATED`

### Requirement: Unauthenticated responses announce the bearer scheme
Every 401 `UNAUTHENTICATED` response SHALL carry the header `WWW-Authenticate: Bearer` with no further parameters, and the problem-details body defined in `error-responses`.

#### Scenario: Missing token
- **WHEN** a client calls `GET /api/v1/users/me` without an `Authorization` header
- **THEN** the response is 401 `UNAUTHENTICATED` with the header `WWW-Authenticate: Bearer` exactly

#### Scenario: Invalid token
- **WHEN** a client calls it with a tampered token
- **THEN** the `WWW-Authenticate` header is `Bearer` with no `error` or `error_description`

### Requirement: Public routes are listed explicitly
Every path under `/api/v1` SHALL require an access token unless it is on an explicit list of public routes, each given by method and exact path. This change adds no public route under `/api/v1`. Paths outside `/api/v1` SHALL NOT require a token.

#### Scenario: Unknown API path without a token
- **WHEN** a client without a token calls `GET /api/v1/does-not-exist`
- **THEN** the response is 401 `UNAUTHENTICATED`

#### Scenario: Unknown API path with a token
- **WHEN** a client with a valid token calls `GET /api/v1/does-not-exist`
- **THEN** the response is 404 `ROUTE_NOT_FOUND`

#### Scenario: Operational path without a token
- **WHEN** a client without a token calls `GET /actuator/env`
- **THEN** the response is 404, not 401

### Requirement: The request user comes from the token subject
The user acting in an authenticated request SHALL be the UUID in the access token's `sub`. The backend SHALL NOT look the user up in the database to authenticate the request, and SHALL NOT take the acting user from the request path, query or body.

#### Scenario: Token of a user with no row
- **WHEN** a valid access token names a user ID that has no user record
- **THEN** authentication succeeds, and the endpoint decides the outcome

### Requirement: Sessions are stateless
Authenticated requests SHALL NOT create a server-side session or set a session cookie.

#### Scenario: No session cookie
- **WHEN** an authenticated request to `GET /api/v1/users/me` succeeds
- **THEN** the response has no `Set-Cookie` header
