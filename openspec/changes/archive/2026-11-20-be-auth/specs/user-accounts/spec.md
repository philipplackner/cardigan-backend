# Spec Delta

## ADDED Requirements

### Requirement: Passwords are hashed with a slow one-way hash over the whole password
The backend SHALL store a password only as a BCrypt hash with a configured cost, 12 by default, with a random salt per user. Every character of the NFC-normalized password SHALL count, also beyond BCrypt's 72-byte input limit. Surrounding whitespace SHALL be kept.

#### Scenario: Stored hash
- **WHEN** the cost is configured as 5 and a user registers with the password `Correct horse 7`
- **THEN** the stored password hash is a BCrypt hash with cost 5 and does not contain the password

#### Scenario: Default cost
- **WHEN** no cost is configured
- **THEN** the cost is 12

#### Scenario: Same password, different users
- **WHEN** two users register with the same password
- **THEN** their stored hashes differ

#### Scenario: Long passwords that differ at the end
- **WHEN** a user registers with a valid password of 128 characters and logs in with a password that differs only in the last character
- **THEN** the login fails with 401 `INVALID_CREDENTIALS`

#### Scenario: Multi-byte password over 72 bytes
- **WHEN** a user registers with a valid password of 100 characters that are each 4 bytes in UTF-8, plus the required classes
- **THEN** registration succeeds and a login with the same password succeeds

#### Scenario: Composed and decomposed form
- **WHEN** a user registers with a password containing `é` as one code point and logs in with `e` followed by U+0301
- **THEN** the login succeeds
