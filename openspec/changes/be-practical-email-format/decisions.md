# Decisions: be-practical-email-format

Confirmed with Philipp Lackner on 2026-11-23.

## Summary

The backend implements the shared change `practical-email-format` (`domain-user`). Register replaces the RFC 5322 email check with the shared reference regex. Login now validates the email for `REQUIRED`, `INVALID_CHARACTER` and `TOO_LONG`, as the shared spec requires, but never checks the format. The OpenAPI document does not change and the change is not breaking.

## Decisions

### D1 — Scope
- **Question**: What does this backend change implement?
- **Decision**: The backend part of the shared change `practical-email-format`: every `domain-user` email requirement it added, in place of "Email follows the RFC 5322 address format".
- **Alternatives**: —
- **Why**: The shared spec changed, and backend specs reference it instead of copying it.
- **Depends on**: —

### D2 — Login follows the shared email checks
- **Question**: The shared spec says login validates the email for `REQUIRED`, `INVALID_CHARACTER` and `TOO_LONG`. The backend's `auth-sessions` spec answers 401 `INVALID_CREDENTIALS` for a login email with a forbidden character or over 254 characters. Which one wins?
- **Decision**: Follow the shared spec. Login answers 400 `VALIDATION_FAILED` for `REQUIRED`, `INVALID_CHARACTER` and `TOO_LONG` (with `max` 254) on the email, and never checks the format. `auth-sessions` "Login validates required fields only" is modified and "A login email that cannot be stored counts as unknown" is removed. The password on login is unchanged.
- **Alternatives**: Keep the backend's 401 behavior and ask for a shared-spec follow-up.
- **Depends on**: D1

### D3 — Reference regex verbatim
- **Question**: How does the backend implement the format check?
- **Decision**: Use the shared reference pattern exactly as written in `domain-user` ("Email format has one reference pattern"). The format check returns invalid for input over 254 code points without running the regex. A test checks that the 250 KB near-match from the shared scenario is reported invalid within 100 ms on the JVM.
- **Alternatives**: An equivalent regex with possessive quantifiers, like the current RFC 5322 one; possessive regex plus the length guard.
- **Why**: One source of truth across platforms. The length guard was added after the verbatim pattern overflowed the stack on the 250 KB input (see Facts). Every email over 254 code points is `TOO_LONG` anyway, so the guard changes no observable result.
- **Depends on**: D1

### D4 — Not breaking
- **Question**: Is this change breaking?
- **Decision**: No. The OpenAPI snapshot does not change and no stored data has to migrate.
- **Alternatives**: Mark as breaking.
- **Why**: There is no real user data, as in the shared decision.
- **Depends on**: D2

### D5 — Branch
- **Question**: Where is the change committed?
- **Decision**: On the current branch `11-labels-boards-auth`, locally.
- **Alternatives**: New branch `13-email-validation-updates`; leave uncommitted.
- **Depends on**: —

## Facts

- Register checks the email with `@ValidText(min = 1, max = 254)` and `@ValidEmail`, which uses an RFC 5322 `addr-spec` regex with possessive quantifiers — source: `user/.../api/dto/AuthRequests.kt`, `user/.../api/validation/Validators.kt`.
- Login checks the email only with `@NotBlankText`; `AuthService.findUser` treats an email that fails `checkText` as unknown without a lookup — source: `user/.../api/dto/AuthRequests.kt`, `user/.../service/AuthService.kt`.
- The `User` model enforces only that the email is lowercase, so a stored email that breaks the new format still loads — source: `user/.../domain/model/User.kt`.
- The rate limit runs before field validation and counts any non-empty normalized email, so it is not affected — source: `AuthService.checkRateLimit`, `auth-rate-limiting`.
- `RegisterRequest.email` and `LoginRequest.email` are plain `type: string` in the OpenAPI snapshot — source: `app/src/test/resources/openapi/openapi.json`.
- On JDK 25, `java.util.regex` with the verbatim reference pattern throws `StackOverflowError` on `a@` + `a.` × 125,000 + `1`, because it recurses once per repeated domain label. A possessive variant finishes in about 9 ms. Valid samples (`a@b.co`, `ann@a.b.example.co.uk`, `ann@xn--mnchen-3ya.de`) match and invalid ones (`mail@pl-coding`, `ann@example.xn--p1ai`) don't — source: local `java` run on 2026-11-23.
- The shared change keeps the `api-errors` field error order `REQUIRED` → `INVALID_CHARACTER` → `TOO_LONG` → `INVALID_FORMAT` — source: `cardigan-shared-specs` archive `2026-11-23-practical-email-format/decisions.md` D2.

## Out of scope

- Client changes (mobile and web).
- Email verification and password reset.
- Lowercasing and uniqueness of emails.
- Login password validation.

## Deferred

- None.
