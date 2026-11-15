# Spec Delta

## Purpose

Defines which auth endpoints the backend rate limits, by which key and with which values. It records where the backend deviates from the shared spec `api-auth-tokens`, which asks for per-IP limits and a limit on refresh.

## ADDED Requirements

### Requirement: Login and register are limited per email
Login and register SHALL each be rate limited per submitted email, compared after `text-length` normalization and lowercasing. The backend SHALL NOT use the client IP address for any limit. This deviates from the shared spec `api-auth-tokens` ("What rate limits count") until that spec is changed to match.

#### Scenario: Same email in different forms
- **WHEN** a client sends login requests for `ann@example.com`, ` Ann@Example.com ` and `ANN@EXAMPLE.COM`
- **THEN** all of them count toward the same limit

#### Scenario: Different emails
- **WHEN** a client is over the login limit for `ann@example.com` and logs in as `bob@example.com`
- **THEN** the request for `bob@example.com` is not answered with 429

#### Scenario: Different client, same email
- **WHEN** one client used up the login limit for `ann@example.com` and another client logs in as `ann@example.com` with the right password
- **THEN** the response is 429 `RATE_LIMITED`

### Requirement: Limit values
Login SHALL allow 5 requests per minute per email, refilled evenly over the minute. Register SHALL allow the same, counted separately from login. Both values SHALL be configuration values, separate for login and register.

#### Scenario: Sixth login
- **WHEN** a client sends 6 login requests for one email within one second
- **THEN** the first 5 are processed and the sixth answers 429 `RATE_LIMITED`

#### Scenario: Refill
- **WHEN** 12 seconds have passed since the limit for an email was used up
- **THEN** one more login request for that email is processed

#### Scenario: Separate counts
- **WHEN** a client used up the login limit for an email and sends a register request for the same email
- **THEN** the register request is not answered with 429

### Requirement: Every request counts
Every login or register request that carries an email SHALL use up one unit of that email's limit, whether it succeeds or fails, including requests that fail validation.

#### Scenario: Successful logins count
- **WHEN** a client logs in successfully 5 times within one second and sends a sixth request with the right password
- **THEN** the sixth response is 429 `RATE_LIMITED`

#### Scenario: Invalid register requests count
- **WHEN** a client sends 5 register requests for one email with a weak password and then one with a valid password
- **THEN** the sixth response is 429 `RATE_LIMITED`

### Requirement: Requests without an email are not limited
A login or register request whose `email` is missing, `null` or empty after normalization SHALL NOT be rate limited and SHALL be answered by field validation.

#### Scenario: No email
- **WHEN** a client sends 20 login requests without `email` within one second
- **THEN** every response is 400 `VALIDATION_FAILED`

### Requirement: Over the limit
A request over the limit SHALL answer 429 `RATE_LIMITED` with a `Retry-After` header holding the whole seconds, at least 1, until the next request for that email is allowed. The limit SHALL be checked after the body is read and before field validation, credentials and the uniqueness check. Size, media type and parsing errors SHALL come before it, because the email is in the body.

#### Scenario: Retry-After
- **WHEN** a login request is over the limit
- **THEN** the response is 429 `RATE_LIMITED` with `Retry-After` between 1 and 12

#### Scenario: Correct password over the limit
- **WHEN** a client over the login limit for an email sends the right password
- **THEN** the response is 429 `RATE_LIMITED` and no session is created

#### Scenario: Invalid body over the limit
- **WHEN** a client over the register limit for an email sends a weak password
- **THEN** the response is 429 `RATE_LIMITED`, not 400

#### Scenario: Unparseable body
- **WHEN** a client sends a login request whose body is not valid JSON
- **THEN** the response is 400 `MALFORMED_REQUEST` and no limit is used up

### Requirement: Refresh and logout are not rate limited
Refresh and logout SHALL never answer 429. For refresh, this deviates from the shared spec `api-auth-tokens` ("Auth endpoints are rate limited") until that spec is changed to match.

#### Scenario: Many refresh attempts
- **WHEN** a client sends 100 refresh requests with unknown tokens within one second
- **THEN** every response is 401 `INVALID_REFRESH_TOKEN`

### Requirement: Limits are kept per instance in bounded memory
Limit counters SHALL be kept in the memory of each backend instance, SHALL reset when it restarts, and SHALL be bounded: the number of tracked emails has a configured maximum, and an email that was not used for longer than the limit window stops being tracked.

#### Scenario: Restart
- **WHEN** an email is over its login limit and the backend restarts
- **THEN** a login request for that email is processed

#### Scenario: Many different emails
- **WHEN** login requests arrive for more different emails than the configured maximum
- **THEN** the backend keeps counters for at most that many emails
