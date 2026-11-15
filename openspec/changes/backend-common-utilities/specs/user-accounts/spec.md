# Spec Delta

## Purpose

Defines the backend's stored user record, the form in which a user's email is kept and compared, and the endpoint that returns the authenticated user, on top of the shared spec `domain-user`.

## ADDED Requirements

### Requirement: A user record holds identity, email, password hash and times
The backend SHALL store each user with a backend-assigned UUID, the email, a password hash, a creation time and an update time. It SHALL NOT store the plain password.

#### Scenario: Stored record
- **WHEN** a user record is saved
- **THEN** it has an ID, an email, a password hash, and creation and update times at millisecond precision

### Requirement: Emails are stored lowercased
The backend SHALL store every email in lowercase after `text-length` normalization, and SHALL return that stored form everywhere the email appears in a response. A user record with an email that has an uppercase letter SHALL NOT be stored. This deviates from shared `domain-user` ("Email identifies exactly one user") until that spec is changed to match.

#### Scenario: Uppercase email rejected by storage
- **WHEN** code tries to store a user with the email `Ann@Example.com`
- **THEN** the record is not stored

#### Scenario: Lowercased email returned
- **WHEN** a user stored with `ann@example.com` calls `GET /api/v1/users/me`
- **THEN** `email` is `ann@example.com`

### Requirement: Emails are unique
No two user records SHALL have the same email. Because emails are stored lowercased, emails that differ only in letter case SHALL count as the same.

#### Scenario: Exact duplicate
- **WHEN** a user with `ann@example.com` exists and another record with `ann@example.com` is stored
- **THEN** the second record is not stored and the first is unchanged

#### Scenario: Mixed-case duplicate
- **WHEN** a user with `ann@example.com` exists and another record is stored from the input `Ann@EXAMPLE.com`, lowercased before storing
- **THEN** the second record is not stored

### Requirement: The current user endpoint
`GET /api/v1/users/me` SHALL return 200 with the authenticated user's `id`, `email` and `createdAt`, and no other field.

#### Scenario: Signed-in user
- **WHEN** a user created at `2026-11-15T09:15:30.123Z` with the email `ann@example.com` calls `GET /api/v1/users/me` with a valid access token
- **THEN** the response is 200 with exactly `id` (the user's UUID in lowercase), `email` `ann@example.com` and `createdAt` `2026-11-15T09:15:30.123Z`

#### Scenario: No token
- **WHEN** a client calls `GET /api/v1/users/me` without a token
- **THEN** the response is 401 `UNAUTHENTICATED`

### Requirement: A missing current user is unauthenticated
When the access token is valid but no user record exists for its subject, `GET /api/v1/users/me` SHALL respond 401 `UNAUTHENTICATED`, the same as for an invalid token, including the `WWW-Authenticate: Bearer` header.

#### Scenario: User row gone
- **WHEN** a valid access token names a user ID with no user record and calls `GET /api/v1/users/me`
- **THEN** the response is 401 `UNAUTHENTICATED` with `WWW-Authenticate: Bearer`
