# Spec Delta

## Purpose

Defines the rate limit values for login and register and how the backend keeps the counters. Which endpoints are limited, the per-email key and the 429 response are defined in the shared spec `api-auth-tokens`; this spec only adds what that spec leaves to the backend.

## ADDED Requirements

### Requirement: Limit values
Login SHALL allow 5 requests per minute per email, refilled evenly over the minute. Register SHALL allow the same. Both values SHALL be configuration values, separate for login and register. They are the backend setting that the shared spec `api-auth-tokens` ("Auth endpoints are rate limited") refers to.

#### Scenario: Sixth login
- **WHEN** a client sends 6 login requests for one email within one second
- **THEN** the first 5 are processed and the sixth answers 429 `RATE_LIMITED`

#### Scenario: Sixth register
- **WHEN** a client sends 6 register requests for one email within one second
- **THEN** the first 5 are processed and the sixth answers 429 `RATE_LIMITED`

#### Scenario: Refill
- **WHEN** 12 seconds have passed since the limit for an email was used up
- **THEN** one more login request for that email is processed

#### Scenario: Retry-After range
- **WHEN** a login request is over the limit
- **THEN** the `Retry-After` header is between 1 and 12

### Requirement: The limit is checked before validation and credentials
On login and register, a request over the limit SHALL answer 429 `RATE_LIMITED` without field validation, the credentials or the uniqueness check being evaluated, and SHALL NOT create a user or a session. A request whose body cannot be read SHALL NOT use up any limit.

#### Scenario: Correct password over the limit
- **WHEN** a client over the login limit for an email sends the right password
- **THEN** the response is 429 `RATE_LIMITED` and no session is created

#### Scenario: Invalid body over the limit
- **WHEN** a client over the register limit for an email sends a weak password
- **THEN** the response is 429 `RATE_LIMITED`, not 400

#### Scenario: Unparseable body
- **WHEN** a client sends 6 login requests whose body is not valid JSON, and then a valid login
- **THEN** the first 6 responses are 400 `MALFORMED_REQUEST` and the valid login is processed

### Requirement: Limits are kept per instance in bounded memory
Limit counters SHALL be kept in the memory of each backend instance, SHALL reset when it restarts, and SHALL be bounded: the number of tracked emails has a configured maximum, and an email that was not used for longer than the limit window stops being tracked.

#### Scenario: Restart
- **WHEN** an email is over its login limit and the backend restarts
- **THEN** a login request for that email is processed

#### Scenario: Many different emails
- **WHEN** login requests arrive for more different emails than the configured maximum
- **THEN** the backend keeps counters for at most that many emails
