# Design

## Context

See proposal.md for the motivation and `decisions.md` for the settled choices.

- Register: `RegisterRequest.email` carries `@Required`, `@ValidText(min = 1, max = 254)` and `@ValidEmail`. `ValidEmailValidator` skips anything `checkText` already rejects and then runs an RFC 5322 `addr-spec` regex (`isAddrSpec`) with possessive quantifiers.
- Login: `LoginRequest.email` carries `@Required` and `@NotBlankText`, which only reports `REQUIRED`. `AuthService.findUser` treats an email that fails `checkText` as unknown and skips the lookup, which is how a forbidden character or an over-long email answers 401 today.
- `checkText` already returns the shared order `REQUIRED` → `INVALID_CHARACTER` → `TOO_SHORT` → `TOO_LONG`, and `ValidTextValidator` adds `max` to `TOO_LONG`.
- On JDK 25 the verbatim reference regex overflows the stack on the shared 250 KB near-match (`decisions.md` Facts).

## Goals / Non-Goals

**Goals:**
- Register accepts exactly the emails the shared reference pattern accepts, within 254 code points.
- Login reports `REQUIRED`, `INVALID_CHARACTER` and `TOO_LONG` for the email and nothing else about its shape.

**Non-Goals:**
- No change to the rate limiter, the password rules, the OpenAPI document, the database or the stored emails.

## Decisions

### Format check: verbatim pattern behind a length guard
`Validators.kt` in `user/api/validation` replaces `isAddrSpec` with `hasEmailFormat(text)`. It holds the shared pattern as one string constant, copied character for character, and returns `false` for text over `EMAIL_MAX_LENGTH` code points before running it. One short comment says why the guard exists (the JVM regex recurses per domain label). `ValidEmailValidator` keeps its structure and calls `hasEmailFormat`.

The check stays in the `api` layer, next to the validator that uses it. No domain or service code needs the format, because login must never apply it.

Alternatives: a possessive rewrite of the pattern (rejected in D3, second pattern to keep in sync); moving the check to `domain/model/Email.kt` (no second caller).

### Login uses `@ValidText`
`LoginRequest.email` swaps `@NotBlankText` for `@ValidText(min = 1, max = EMAIL_MAX_LENGTH)`, the same constraint register uses. That gives `REQUIRED`, `INVALID_CHARACTER` and `TOO_LONG` with `max` 254 in the shared order, and no format check. `NotBlankText` and its validator have no other user and are deleted. The springdoc output for the field stays `type: string`, as it already is for register; the snapshot test confirms it.

### Remove the service guard
`AuthService.findUser` drops its `checkText` guard and always looks the email up. The requirement it served is removed, and `Credentials` are only built from validated requests. A login with a validated but format-breaking email gets the normal lookup and, when unknown, the same hashing work as today.

Alternative: keep the guard as defense in depth. Rejected because it encodes a rule the spec no longer has and can't be reached through the API.

### Tests
- `RegisterRequestValidationTest`: every accepted and rejected email from the shared `domain-user` scenarios, the field error order cases (`jürgen@münchen.de` is `INVALID_FORMAT`; 255 characters without `@` is `TOO_LONG`), and the 250 KB near-match against `hasEmailFormat` under 100 ms. The RFC 5322 cases (quoted strings, domain literals) move from accepted to rejected, and the `isAddrSpec` backtracking test is replaced.
- `LoginRequestValidationTest`: U+0000 is `INVALID_CHARACTER`, 255 characters is `TOO_LONG` with `max` 254, `ann@localhost` and `not an email` have no field error.
- `AuthServiceRegisterLoginTest`: the unstorable-email case is removed. A stored email that breaks the format logs in with the right password.
- `AuthRegisterLoginE2eTest`: register with `mail@pl-coding` is 400 `INVALID_FORMAT`. Login with U+0000 is 400 `INVALID_CHARACTER`, and login with `ann@localhost` is 401.

## Risks / Trade-offs

- [The guard means `hasEmailFormat` rejects a pattern-valid email over 254 code points] → No observable effect: such an email is `TOO_LONG` before the format is checked, as `domain-user` "Register enforces the email format" requires.
- [A client that relied on 401 for an odd login email now gets 400] → Accepted as not breaking (D4). Clients implement the same shared rule.
- [Dev accounts with emails such as `test@localhost` can no longer be registered again] → They can still log in, because login skips the format. Wiping dev data is not needed.
- [The JVM regex could still recurse deeply on valid input] → Input is at most 254 code points when the regex runs, far below the depth that overflowed.

## Migration Plan

No data or schema migration. Deploy as usual; rollback is a revert of the commit.
