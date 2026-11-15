# Spec Delta

## Purpose

Defines how precise the backend's timestamps are and how tests control the backend's notion of the current time, so that stored, sorted and returned times always agree.

## ADDED Requirements

### Requirement: Timestamps have millisecond precision from creation
Every timestamp the backend creates, stores or returns SHALL have at most millisecond precision at the moment it is created. A stored timestamp SHALL equal the value returned for it on the wire.

#### Scenario: Created at a sub-millisecond time
- **WHEN** a user record is created at `2026-11-15T09:15:30.123456Z`
- **THEN** its stored creation time is `2026-11-15T09:15:30.123Z` and the API returns `2026-11-15T09:15:30.123Z`

### Requirement: One clock for all backend time
Token issue and expiry checks, stored timestamps and every other use of the current time SHALL read it from one source that tests can replace.

#### Scenario: Time moved forward in a test
- **WHEN** a test moves the backend time forward by 16 minutes after issuing an access token
- **THEN** a request with that token fails with 401 `UNAUTHENTICATED`

### Requirement: Production time is the system time
Outside tests, the backend's current time SHALL be the system clock in UTC, and no endpoint or setting SHALL change it.

#### Scenario: Running service
- **WHEN** the service runs with any profile
- **THEN** no HTTP endpoint or configuration property can change the time it uses
