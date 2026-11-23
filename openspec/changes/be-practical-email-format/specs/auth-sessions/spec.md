# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Login validates required fields only`
- TO: `### Requirement: Login validates required fields and email limits`

## MODIFIED Requirements

### Requirement: Register validates email and password
Register SHALL require `email`, `password` and `refreshTokenTransport`, and SHALL check the email and password against every rule of the shared spec `domain-user` ("Email is stored lowercased and identifies one user", "Email local part is a dot-atom", "Email domain has at least two labels", "Email format has one reference pattern", "Register enforces the email format", "Passwords follow one composition rule"). Broken rules SHALL produce 400 `VALIDATION_FAILED` with one field error per broken field, and no user SHALL be stored.

#### Scenario: Empty body
- **WHEN** a client registers with `{}`
- **THEN** the response is 400 `VALIDATION_FAILED` with `REQUIRED` for `email`, `password` and `refreshTokenTransport`

#### Scenario: Bad email and weak password
- **WHEN** a client registers with the email `ann@` and the password `alllowercase12`
- **THEN** the field errors are `email` `INVALID_FORMAT` and `password` `PASSWORD_TOO_WEAK`, and no user is stored

#### Scenario: Single-label domain
- **WHEN** a client registers with the email `mail@pl-coding` and a valid password
- **THEN** the response is 400 `VALIDATION_FAILED` with `email` `INVALID_FORMAT`, and no user is stored

#### Scenario: Address RFC 5322 allowed
- **WHEN** a client registers with the email `"ann lee"@example.com` or `ann@[192.168.0.1]` and a valid password
- **THEN** the field error is `email` `INVALID_FORMAT`

#### Scenario: Password length limits
- **WHEN** a client registers with a password of 11 characters, and another with 129
- **THEN** the field errors are `password` `TOO_SHORT` with `min` 12, and `password` `TOO_LONG` with `max` 128

#### Scenario: Unknown transport
- **WHEN** a client registers with `refreshTokenTransport` `HEADER`
- **THEN** the field error is `refreshTokenTransport` `INVALID_VALUE`

### Requirement: Login validates required fields and email limits
Login SHALL answer 400 `VALIDATION_FAILED` only when `email`, `password` or `refreshTokenTransport` is missing, `null`, empty after normalization (the email is trimmed, the password is not), the transport is unknown, or the email breaks the shared spec `domain-user` ("Login does not check the email format") with `INVALID_CHARACTER` or `TOO_LONG`. It SHALL NOT apply the email format or the password rules: any other email or password that does not match a user SHALL produce 401 `INVALID_CREDENTIALS`.

#### Scenario: Missing password
- **WHEN** a client logs in without `password`
- **THEN** the response is 400 `VALIDATION_FAILED` with `password` `REQUIRED`

#### Scenario: Malformed email
- **WHEN** a client logs in with the email `not an email` and any password
- **THEN** the response is 401 `INVALID_CREDENTIALS`

#### Scenario: Single-label domain on login
- **WHEN** a client logs in with the email `ann@localhost`, no user has that email, and any password
- **THEN** the response is 401 `INVALID_CREDENTIALS`, not a validation error

#### Scenario: Control character in the email
- **WHEN** a client logs in with an email containing U+0000 and any password
- **THEN** the response is 400 `VALIDATION_FAILED` with `email` `INVALID_CHARACTER`

#### Scenario: Email over the limit
- **WHEN** a client logs in with an email of 255 characters after normalization and any password
- **THEN** the response is 400 `VALIDATION_FAILED` with `email` `TOO_LONG` and `max` 254

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

## REMOVED Requirements

### Requirement: A login email that cannot be stored counts as unknown
**Reason**: The shared spec `domain-user` ("Login does not check the email format") now has login reject a forbidden character or an email over 254 characters with a field error.
**Migration**: Covered by "Login validates required fields and email limits": such an email now answers 400 `VALIDATION_FAILED` with `INVALID_CHARACTER` or `TOO_LONG` instead of 401 `INVALID_CREDENTIALS`.
