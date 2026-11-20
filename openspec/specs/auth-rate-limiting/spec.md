# auth-rate-limiting Specification

## Purpose

Defines the rate limit values for login and register and how the backend keeps the counters. Which endpoints are limited, the per-email key and the 429 response are defined in the shared spec `api-auth-tokens`; this spec only adds what that spec leaves to the backend.

## Requirements

### Requirement: Limit values
Login SHALL allow 5 requests per minute per email, refilled evenly over the minute. Register SHALL allow the same. Both values SHALL be configuration values, separate for login and register. They are the backend setting that the shared spec `api-auth-tokens` ("Auth endpoints are rate limited") refers to. A request is processed when its answer is anything other than 429.

#### Scenario: Sixth login
- **WHEN** a client sends 6 login requests for one email within one second
- **THEN** the first 5 are processed and the sixth answers 429 `RATE_LIMITED`

#### Scenario: Sixth register
- **WHEN** a client sends 6 register requests for one email within one second
- **THEN** the first 5 are processed and the sixth answers 429 `RATE_LIMITED`

#### Scenario: Refill
- **WHEN** 5 login requests for one email were sent at the same instant
- **THEN** a sixth request 11.999 seconds later answers 429, a request 12 seconds after the first five is processed, and another one at that same instant answers 429

#### Scenario: Retry-After
- **WHEN** 5 login requests for one email were sent at the same instant
- **THEN** a sixth request at that instant answers 429 with `Retry-After: 12`, one 5 seconds later with `Retry-After: 7`, and one 11.5 seconds later with `Retry-After: 1`

#### Scenario: Waiting for Retry-After is enough
- **WHEN** a client gets 429 with `Retry-After: n` and sends the request again n seconds later
- **THEN** the request is processed

### Requirement: The limit is checked before validation and credentials
On login and register, a request over the limit SHALL answer 429 `RATE_LIMITED` without field validation, the credentials or the uniqueness check being evaluated, and SHALL NOT create a user or a session. A request whose body cannot be read SHALL NOT use up any limit.

#### Scenario: Correct password over the limit
- **WHEN** a client over the login limit for an email sends the right password
- **THEN** the response is 429 `RATE_LIMITED` and no session is created

#### Scenario: Invalid body over the limit
- **WHEN** a client over the register limit for an email sends a weak password
- **THEN** the response is 429 `RATE_LIMITED`, not 400

#### Scenario: Used email over the limit
- **WHEN** a client over the register limit for a registered email sends a valid register request for it
- **THEN** the response is 429 `RATE_LIMITED`, not 409

#### Scenario: Unparseable body
- **WHEN** a client sends 6 login requests whose body is not valid JSON, and then a valid login
- **THEN** the first 6 responses are 400 `MALFORMED_REQUEST` and the valid login is processed

### Requirement: Limits are kept per instance in bounded memory
Limit counters SHALL be kept in the memory of each backend instance, SHALL reset when it restarts, and SHALL be bounded: the number of tracked emails per limited endpoint has a configured maximum, and an email that was not used for longer than the limit window stops being tracked. The limiter SHALL report how many emails it tracks per endpoint, for tests.

#### Scenario: Restart
- **WHEN** an email is over its login limit and a new limiter instance is created with the same configuration
- **THEN** a login request for that email is processed

#### Scenario: Many different emails
- **WHEN** the maximum is configured as 2 and login requests arrive for 3 different emails
- **THEN** the limiter tracks at most 2 login emails once its pending cleanup has run

#### Scenario: Idle email
- **WHEN** an email was last used for login 61 seconds ago
- **THEN** the limiter tracks no login entry for it, and its next 5 login requests are processed

### Requirement: Password hashing has a bounded wait
At most a configured number of password hash computations SHALL run at once. A login or register request that waits longer than a configured time for its turn, 5 seconds by default, SHALL answer 429 `RATE_LIMITED` with `Retry-After` set to that time rounded up to whole seconds, and SHALL NOT create a user or a session. This 429 does not depend on the email, and the retry may be answered 429 again while the backend is still busy.

#### Scenario: Hashing stays busy
- **WHEN** every hashing slot is busy for longer than the configured wait and a client sends a login with a valid body
- **THEN** the response is 429 `RATE_LIMITED` with `Retry-After: 5`, and no password was verified for that request

#### Scenario: Slot becomes free in time
- **WHEN** every hashing slot is busy and one becomes free before the configured wait is over
- **THEN** the waiting request is processed
