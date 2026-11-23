# Proposal

## Why

The shared change `practical-email-format` replaced the RFC 5322 email rule in `domain-user` with a practical one. It also changed which email checks login runs. The backend still accepts addresses like `mail@pl-coding`, and its login answers 401 where the shared spec now wants 400. See `decisions.md` for every settled choice.

## What Changes

- Register checks the email against the shared reference regex instead of RFC 5322. Quoted local parts, domain literals, single-label domains, trailing dots and non-ASCII addresses become `INVALID_FORMAT`.
- Login validates the email for `REQUIRED`, `INVALID_CHARACTER` and `TOO_LONG` (`max` 254), in that order, and answers 400 `VALIDATION_FAILED` for them. Before, a forbidden character or an email over 254 characters answered 401 `INVALID_CREDENTIALS`.
- Login never checks the email format. An email that passes the checks above but breaks the format is looked up like any other.
- Login password checks, the rate limit and the OpenAPI document don't change.
- Not breaking (no real user data, no schema change).

## Shared requirements implemented or depended on

| Spec | Requirement | How |
|---|---|---|
| `domain-user` | Email local part is a dot-atom | Implemented for register |
| `domain-user` | Email domain has at least two labels | Implemented for register |
| `domain-user` | Email format has one reference pattern | Implemented, pattern used verbatim behind a 254 code point guard |
| `domain-user` | Register enforces the email format | Implemented |
| `domain-user` | Login does not check the email format | Implemented; replaces the backend's 401 for unstorable login emails |
| `domain-user` | Email is stored lowercased and identifies one user | Depended on, unchanged |
| `api-errors` | One field error per field | Depended on (field error order) |
| `api-errors` | Field error codes | `INVALID_CHARACTER` and `TOO_LONG` now raised for the login email |
| `text-length` | Text is normalized before validation | Depended on |

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `auth-sessions`: "Register validates email and password" references the new `domain-user` email requirements instead of the RFC 5322 one. "Login validates required fields only" adds the email checks for `INVALID_CHARACTER` and `TOO_LONG`. "A login email that cannot be stored counts as unknown" is removed.

## Impact

- `:user`: the email validator and its regex in `api/validation`, `LoginRequest` validation, `AuthService.findUser`, and the unit and integration tests for register and login.
- `:app`: the auth E2E tests for login validation.
- No change to the OpenAPI snapshot, the database or dependencies.
- Other repos: none. Mobile and web implement the shared change on their own.
