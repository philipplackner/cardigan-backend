# Artifact review: be-auth

**Verdict:** Ready
**Reviewed:** 2026-11-15, schema `careful-planning`, repo kind `code`

No blocker was found and all four reviewers completed. After the review, the user accepted every recommended fix except S4 (minor, rejected: left implicit). All 10 majors are applied. The new wording has not been reviewed itself; a re-run in a fresh session is recommended.

Counts as the reviewers returned them:

| Reviewer | Blocker | Major | Minor |
|---|---|---|---|
| Testability | 0 | 6 | 10 |
| Security | 0 | 3 | 2 |
| Hidden assumptions | 0 | 4 | 5 |
| Design contract | 0 | 5 | 9 |

After the merge: 31 findings in this change (10 major, 21 minor), 2 upstream findings, 1 finding dropped.

Note on timing: the artifacts changed during the review (commit `9facfce`, "align be-auth with the updated shared auth specs"). The design-contract reviewer re-read everything at HEAD `c64f96f`. The other three read the earlier version. Every citation below was checked against the current files. Where a finding's target moved to the shared spec or was already fixed by that commit, this is stated in the finding.

## Conflicts between reviewers

No incompatible fixes. Two smaller disagreements, both noted in the findings:

- **T3 / A3 / D1 / D2:** same fix, different name for the login email constraint: `@NotBlankText` (A3) or `@NotEmptyText` (D1). **Decision:** `@NotBlankText`.
- **S4:** the security reviewer reports cache eviction as a bypass to document. The assumptions reviewer listed the same thing as safe to leave implicit, on the premise that every filler request costs BCrypt work. S4 says filler requests without a password count toward the limit and skip hashing. **Decision:** leave it implicit; S4 is rejected.

## Findings

### T3 / A3 / D1 / D2: `@Required` only rejects `null`, so empty login fields answer 401 and an empty register email gets `INVALID_FORMAT`
**Status:** applied with edits (constraint named `@NotBlankText`, the user's choice)
- Severity: major - the endpoint answers 401 (or the wrong field code) where the spec and the shared specs require 400 `REQUIRED`, the design's constraints don't meet the spec, and no scenario or task tests it. (Merged from T3 major, A3 major, D1 major, D2 major.)
- Location: `design.md` > Request DTOs and validation; `specs/auth-sessions/spec.md` > Requirement: Login validates required fields only; `tasks.md` 4.1
- Current text: "| `LoginRequest` | `email`, `password` | `@Required` |", "| `RegisterRequest` | `email` | `@Required`, `@ValidText(max = 254)`, `@ValidEmail` |", "`@Required` on the login `password` must treat `\"\"` as missing but must not trim: a password of only spaces is a value. If the existing `@Required` validator trims, login gets a password-specific check instead." and spec: "Login SHALL answer 400 `VALIDATION_FAILED` only when `email`, `password` or `refreshTokenTransport` is missing, `null`, empty after normalization, or the transport is unknown."
- Problem: `RequiredValidator` in `common/.../api/validation/Validators.kt` is `value != null`. It neither trims nor rejects `""`. `@ValidText.min` defaults to 0, and `checkText` returns `REQUIRED` only when `min >= 1`. So with the constraints in the design table, login with `email: "   "` or `password: ""` passes validation and answers 401, and register with `email: "  "` answers `INVALID_FORMAT` from `@ValidEmail`. The design only plans for the opposite case (the validator trims). The spec scenarios cover only a missing `password`. It is also unclear what "empty after normalization" means for a password, which is not trimmed.
- Failure scenario: A client sends `{"email":"   ","password":"","refreshTokenTransport":"BODY"}` to login. The rate limit is skipped (empty key), validation passes, the service runs the dummy hash and answers 401 `INVALID_CREDENTIALS` instead of 400 with two `REQUIRED` errors. No test notices.
- Proposed fix:
  - `design.md`, constraint table: `RegisterRequest.email`: `@Required`, `@ValidText(min = 1, max = 254)`, `@ValidEmail`. `LoginRequest.email`: `@Required`, `@NotBlankText` (A3) or `@NotEmptyText` (D1). `LoginRequest.password`: `@Required`, `@NotEmptyPassword`.
  - `design.md`, replace the `@Required` bullet with: "`RequiredValidator` in `:common` only checks for null. Login therefore gets two constraints in `:user` `api/validation`: `@NotBlankText` reports `REQUIRED` when `normalizeText(value)` is empty, and `@NotEmptyPassword` reports `REQUIRED` when the value is `\"\"` without trimming, so a password of only spaces is a value. Neither applies any other rule. `@Required` in `:common` is not changed."
  - `specs/auth-sessions/spec.md`, in the requirement text replace "empty after normalization" with "empty after normalization (the email is trimmed, the password is not)" and add:
    ```
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
    ```
  - `tasks.md` 4.1: replace "Check how `@Required` treats a whitespace-only string and handle the login password as `design.md` describes" with "Add the two login constraints as `design.md` describes", and add to the test list: "login with `email` `\"  \"` and with `password` `\"\"` gives `REQUIRED`; a whitespace-only register email gives `REQUIRED`".
  - Not carried over: T3 also proposed a "Blank email" scenario for "Requests without an email are not limited". That requirement is no longer in this change; it lives in the shared spec.
- Upstream: no
- Test sketch (T3):
  - Level: integration (E2E)
  - Arrange and act: register a user, then send the four login bodies above.
  - Passes when: statuses and field errors match the scenarios.
  - Fails when: `LoginRequest` uses only `@Required`; the blank email and empty password answer 401.

### T2 / A5 / D5: Logout with a token that is both rotated and expired has two valid answers, and the design's logout has no expiry check
**Status:** applied
- Severity: major - two requirements give opposite results for the same input, and the unit test in task 7.3 fails against the flow in `design.md`. (Merged from T2 major, A5 minor, D5 major.)
- Location: `specs/auth-sessions/spec.md` > "Logout is public and always succeeds" and "Logout with a rotated token ends its session"; `design.md` > Register, login and logout flows (Logout)
- Current text: "also when the refresh token is missing, unknown, expired, already revoked, or sent with a transport other than its session's. In those cases no session SHALL change.", "When logout receives a refresh token that was already rotated and whose session is still active, it SHALL revoke that session" and design: "Look up the token by hash. If it exists, its session has the request's transport and isn't revoked, set `revoked_at`. A rotated token takes the same path"
- Problem: A rotated token of a long-lived session passes its own 30 days while the session stays active. Logout with it is "expired" (no change) and "rotated, session active" (revoke). The design's logout has no expiry check at all, so it also revokes for an unrotated expired token, against the spec and task 7.3 ("expired ... change nothing"). "No session SHALL change" for an expired current token can't be seen over HTTP, because refresh fails either way; only `revoked_at` shows it.
- Failure scenario: R1 is rotated to R2 on day 29. On day 31 a client logs out with R1. One test expects R2 to fail afterwards, another expects it to still work. Separately, the test "logout with an expired token changes nothing" fails because the designed flow sets `revoked_at`.
- Proposed fix (all three reviewers agree):
  - `specs/auth-sessions/spec.md`: in "Logout is public and always succeeds" replace "expired" with "expired without having been rotated". In "Logout with a rotated token ends its session" add "This SHALL also hold after the rotated token's own 30 days have passed." and add:
    ```
    #### Scenario: Old token past its own expiry
    - **WHEN** token R1 was rotated to R2 on day 29 and a client logs out with R1 on day 31
    - **THEN** the response is 204, and a refresh with R2 answers 401 `INVALID_REFRESH_TOKEN`

    #### Scenario: Expired current token
    - **WHEN** a client logs out with a session's current refresh token one second after its expiry
    - **THEN** the response is 204 and the stored session has no revocation time
    ```
  - `design.md`, replace the Logout bullet with: "**Logout**: one transaction. Look up the token by hash. Nothing changes when there is no token, the session has another transport, or the session is already revoked. Otherwise: a rotated token revokes the session, whatever its expiry (D17, same order as refresh steps 4 and 5); a current token revokes the session only when `expires_at > now`. The controller always answers 204."
  - Alternative: let an expired current token revoke the session too, and remove "expired" from the no-change list. This would contradict D17 in `decisions.md`.
- Upstream: no
- Test sketch (T2):
  - Level: unit (`AuthService` on fakes with `MutableClock`), plus E2E for the first scenario
  - Arrange and act: log in, advance 29 days, refresh, advance 2 days, log out with R1; in a second test log out with an expired current token.
  - Passes when: R2 fails afterwards in the first; `revokedAt` is null in the second.
  - Fails when: logout checks expiry before rotation and leaves the session active, or has no expiry check and sets `revokedAt` in the second.

### S3 / A2: A login email Postgres cannot store answers 500 instead of 401
**Status:** applied with edits (the rule became its own requirement, "A login email that cannot be stored counts as unknown", because strict validation rejects requirement text over 500 characters)
- Severity: major - contradicts "any other email ... SHALL produce 401" on a public endpoint, skips the equal-work path, and logs a stack trace per request; unit tests with fakes and the listed E2E scenarios stay green. (Merged from S3 major, A2 major.)
- Location: `specs/auth-sessions/spec.md` > Requirement: Login validates required fields only; `design.md` > Register, login and logout flows
- Current text: "It SHALL NOT apply the email format, the length limits or the password composition rule: any other email or password that does not match a user SHALL produce 401 `INVALID_CREDENTIALS`."
- Problem: Login passes the unvalidated email to `UserRepository.findByEmail`. Postgres rejects a text parameter containing U+0000 (valid JSON as `"\u0000"`), so the query throws, `CommonExceptionHandler` logs an error with a stack trace and answers 500. No password hashing happens. Register is safe because `checkText` rejects control characters; login has no such check by D15. The password and the refresh token are safe because they are hashed before use.
- Failure scenario: `POST /api/v1/auth/login` with `{"email":"a\u0000@x.com","password":"x","refreshTokenTransport":"BODY"}` answers 500 `INTERNAL_ERROR`. Repeating it with varying emails fills the error log and trips 5xx alerting, at no BCrypt cost to the sender. The fake `UserRepository` returns null, and the E2E scenario "Malformed email" uses `not an email`.
- Proposed fix:
  - `specs/auth-sessions/spec.md`, add to the requirement: "An email that could never be stored (a character `text-length` forbids, or more than 254 characters) SHALL be treated as an unknown email: 401 `INVALID_CREDENTIALS` with the same password verification work, without a database lookup." and:
    ```
    #### Scenario: Control character in the email
    - **WHEN** a client logs in with an email containing U+0000 and any password
    - **THEN** the response is 401 `INVALID_CREDENTIALS`, identical to the response for an unknown email
    ```
  - `design.md` > Login, add: "When `checkText(email, min = 1, max = 254, multiline = false)` is not null, the service skips `findByEmail` and takes the unknown-email path (dummy hash, then `InvalidCredentialsException`)."
  - `tasks.md`: add the case to 7.1 and 8.1.
- Upstream: no

### A4 / D3: Foreign keys with cascade can't come from plain UUID columns
**Status:** applied
- Severity: major - the design asks for two things Hibernate can't do together, and the cleanup job and the user-delete cascade depend on it; caught by the 5.2 persistence test, but two implementers would solve it differently. (Merged from A4 major, D3 major.)
- Location: `design.md` > Refresh tokens and sessions
- Current text: "The entities hold plain UUID columns for `user_id` and `session_id`, not `@ManyToOne` associations. The foreign keys are declared so that `ddl-auto` creates them with `on delete cascade`."
- Problem: Hibernate generates a foreign key only from an association mapping. `@ForeignKey` only works inside `@JoinColumn`, and `@OnDelete` needs an association. With a plain `@Column`, `ddl-auto` creates no foreign key. The project skill `spring-persistence-jpa` reserves plain ID columns for references to another module's data, and both references here are inside `:user`. `be-boards` (`design.md` line 100) uses a lazy `@ManyToOne` with `@OnDelete(action = OnDeleteAction.CASCADE)` for the same need.
- Failure scenario: The entities are built as written. Dev and test tables have no FK. The cleanup delete removes sessions and leaves every refresh token row behind, a leftover token later resolves to a missing session in `refresh` (500), and the 5.2 test "deleting a user removes its sessions and tokens" fails.
- Proposed fix (`design.md`, replace the bullet): "`SessionEntity.user` and `RefreshTokenEntity.session` are lazy `@ManyToOne(fetch = LAZY, optional = false)` associations with `@JoinColumn(nullable = false)` and `@OnDelete(action = OnDeleteAction.CASCADE)`, so Hibernate generates the cascading foreign keys for dev and tests (same pattern as `be-boards`). The adapters set them with `getReferenceById` and never navigate them. The domain models keep plain `userId` and `sessionId`." Alternative: keep plain UUID columns with `@Column(columnDefinition = "uuid not null references user_service.users(id) on delete cascade")`, which ties the entity to Postgres DDL.
- Upstream: no

### T6 / A8: The bounded-memory requirement is not observable, its maximum is ambiguous, and Caffeine evicts asynchronously
**Status:** applied
- Severity: major - no test can check the requirement as written, and the spec and design disagree on what the maximum counts. (Merged from T6 major, A8 minor.)
- Location: `specs/auth-rate-limiting/spec.md` > Limits are kept per instance in bounded memory; `design.md` > Rate limiter; `tasks.md` 6.1
- Current text: "the number of tracked emails has a configured maximum, and an email that was not used for longer than the limit window stops being tracked.", "**THEN** the backend keeps counters for at most that many emails" and task 6.1: "and the cache never holds more than the configured maximum"
- Problem: (1) "Keeps counters for at most" is internal state with no way to read it. (2) The design has one cache per action, each with `max-tracked-emails`, so the real maximum is 2 x the value. (3) Idle eviction has no scenario. (4) Caffeine evicts on its executor, so the size can be above the maximum for a short time after writes and a size check right after is flaky. (5) "Restart" needs a full context restart in E2E, and task 6.1 has no test for it.
- Failure scenario: The implementer forgets `maximumSize` on one cache; no test can show it, and the register cache grows without bound. Or the unit test inserts `max + 100` emails, asserts `estimatedSize() <= max` right away, and fails now and then on a loaded CI runner.
- Proposed fix:
  - `specs/auth-rate-limiting/spec.md`: replace the second half of the requirement with "the number of tracked emails per limited endpoint has a configured maximum, and an email that was not used for longer than the limit window stops being tracked. The limiter SHALL report how many emails it tracks per endpoint, for tests." Replace the scenarios with:
    ```
    #### Scenario: Restart
    - **WHEN** an email is over its login limit and a new limiter instance is created with the same configuration
    - **THEN** a login request for that email is processed

    #### Scenario: Many different emails
    - **WHEN** the maximum is configured as 2 and login requests arrive for 3 different emails
    - **THEN** the limiter tracks at most 2 login emails once its pending cleanup has run

    #### Scenario: Idle email
    - **WHEN** an email was last used for login 61 seconds ago
    - **THEN** the limiter tracks no login entry for it, and its next 5 login requests are processed
    ```
  - `design.md` > Rate limiter, add: "`max-tracked-emails` applies per action. The caches are built with `executor(Runnable::run)`, so eviction happens on the calling thread and the bound holds after every call." (T6 allows the same-thread executor in tests only, or a count accessor that calls `cleanUp()` first.)
  - `tasks.md` 6.1: change the phrase to "and after inserting more emails than the maximum, the cache holds at most the maximum".
  - Alternative: one shared maximum across both actions, which needs one cache with a composite key.
- Upstream: no
- Test sketch (T6):
  - Level: unit
  - Arrange and act: build `Bucket4jAuthRateLimiter` with maximum 2 and `MutableClock`, check 3 emails, read the tracked count; advance 61 s and read it again.
  - Passes when: the count is at most 2, then 0.
  - Fails when: `maximumSize` or `expireAfterAccess` is missing; the count is 3, or stays above 0 after 61 s.

### S1: A few concurrent logins can stall the whole app, and the design's mitigation is wrong
**Status:** applied
- Severity: major - an unauthenticated client can make every endpoint unresponsive with single-digit requests per second; limited to availability.
- Location: `design.md` > Risks / Trade-offs
- Current text: "[BCrypt cost 12 takes a few hundred milliseconds of CPU per login] → Virtual threads keep request threads free, and the email limit caps the work per account. There is no global cap on logins across many emails."
- Problem: BCrypt is CPU-bound. A virtual thread doing CPU work holds its carrier thread until it finishes, and there are only as many carriers as cores. Virtual threads do not keep anything free here. The per-email limit does not help because the sender uses a new email per request, and unknown emails still run the dummy hash. Register has the same cost.
- Failure scenario: On a 4-core VPS, a script sends 20 login requests per second, each with a different random email. Each takes about 250 ms of CPU, so all 4 carriers are busy all the time. Board requests, refreshes and `/actuator/health` queue behind them and time out. Tests never see it because the test profile uses cost 4.
- Proposed fix: replace the risk line in `design.md` with: "[BCrypt cost 12 takes a few hundred milliseconds of CPU per login and holds a carrier thread for that time] → `BcryptPasswordHasher` runs `hash` and `matches` under a fair `Semaphore` with `cardigan.user.auth.max-concurrent-hashes` permits (default: half the available processors, at least 1). Requests beyond that wait, so hashing never takes all carrier threads and other endpoints stay responsive. There is still no cap on total logins across many emails; under a flood, logins get slow but the rest of the API keeps working." Add `max-concurrent-hashes` to the Configuration block, and to `tasks.md` 3.1: "Verify with a unit test that no more than the configured number of hashes run at once." This adds a config value, so it needs the user's yes. Alternative: change nothing in behavior and correct the risk text to say that concurrent logins can occupy all carrier threads and this is accepted.
- Upstream: no

### S2: The email regex runs on unbounded input and can backtrack
**Status:** applied
- Severity: major - a public endpoint runs a hand-written backtracking regex on up to 256KB of unauthenticated input; likely missed in implementation because the length rule looks like it guards it.
- Location: `design.md` > Request DTOs and validation
- Current text: "`@ValidEmail` checks the normalized value against the RFC 5322 `addr-spec` (dot-atom or quoted-string local part, dot-atom or domain-literal domain, ASCII only, no comments or folding whitespace) with a hand-written regex and reports `INVALID_FORMAT`."
- Problem: `@ValidText(max = 254)` and `@ValidEmail` sit on the same property, and Bean Validation runs both. So the regex runs on emails far longer than 254 characters. An `addr-spec` regex with dot-atom and quoted-string alternatives easily has nested quantifiers, which backtrack exponentially on a near-match. The rate limit does not help: the sender changes the email each time. It also leaves open which code a too-long email gets, while the spec says "one field error per broken field".
- Failure scenario: A client posts register requests whose `email` is 200KB of a pattern that almost matches the local part. Each request pins a carrier thread in `Pattern.matcher` for seconds or longer. A few of them stall the app, as in S1. The unit tests in task 4.1 only list short inputs and 254/255 characters.
- Proposed fix: append to that bullet in `design.md`: "The validator returns valid without running the regex when the normalized value is empty, longer than 254 code points or has an invalid character, so `@ValidText` alone reports those cases and the field gets one error. The regex must not backtrack: no nested quantifiers, possessive quantifiers or atomic groups throughout." Add to `tasks.md` 4.1's list: "a 255-character email reports only `TOO_LONG`, and a 250KB near-matching email is validated within 100 ms".
- Upstream: no

### T1: "Many sessions" scenario cannot pass with the login rate limit
**Status:** applied
- Severity: major - the design says every spec scenario becomes an E2E test, and this one fails as written, so the test or the limiter gets bent to fit.
- Location: `specs/auth-sessions/spec.md` > No limit on sessions per user > Scenario: Many sessions
- Current text: "**WHEN** a user logs in 20 times and then refreshes with the token from the first login"
- Problem: Login allows 5 requests per minute per email. The WHEN does not say that time passes, so the 6th login answers 429 and 20 sessions never exist.
- Failure scenario: The E2E test runs on the fixed `MutableClock`. Login 6 gets 429. The implementer either clears the limiter mid-test or drops the test to the service level, where the HTTP path is no longer covered.
- Proposed fix: Replace the WHEN with "**WHEN** a user logs in 20 times, one minute apart, and then refreshes with the token from the first login".
- Upstream: no
- Test sketch:
  - Level: integration (E2E)
  - Arrange and act: register, then loop 20 times: advance the clock one minute and log in; refresh with the first login's token.
  - Passes when: all 20 logins are 200 and the refresh is 200.
  - Fails when: an implementation caps sessions at 10 and revokes the oldest; the refresh answers 401.

### T5: Refill and Retry-After scenarios accept wrong refill behavior
**Status:** applied
- Severity: major - clients use only `Retry-After` to retry, and a constant value or a wrong refill passes.
- Location: `specs/auth-rate-limiting/spec.md` > Limit values > Scenario: Refill; Scenario: Retry-After range (citation corrected: the reviewer read it as "Over the limit > Retry-After" in the earlier version)
- Current text: "**WHEN** 12 seconds have passed since the limit for an email was used up **THEN** one more login request for that email is processed" and "**WHEN** a login request is over the limit **THEN** the `Retry-After` header is between 1 and 12"
- Problem: "Refill" has no lower bound (still 429 just before 12 s) and no upper bound (only one request is allowed). It also does not say the 5 requests came at one instant, which changes when the next unit arrives. "Between 1 and 12" passes for a hard-coded `1` or `12`. The clock is controllable, so exact values are possible.
- Failure scenario: The bucket refills all 5 units at once after 12 s, or `Retry-After` is always 1. Both pass. Clients retry every second and stay locked out.
- Proposed fix: Replace both scenarios with:
  ```
  #### Scenario: Refill
  - **WHEN** 5 login requests for one email were sent at the same instant
  - **THEN** a sixth request 11.999 seconds later answers 429, a request 12 seconds after the first five is processed, and another one at that same instant answers 429

  #### Scenario: Retry-After
  - **WHEN** 5 login requests for one email were sent at the same instant
  - **THEN** a sixth request at that instant answers 429 with `Retry-After: 12`, one 5 seconds later with `Retry-After: 7`, and one 11.5 seconds later with `Retry-After: 1`

  #### Scenario: Waiting for Retry-After is enough
  - **WHEN** a client gets 429 with `Retry-After: n` and sends the request again n seconds later
  - **THEN** the request is processed
  ```
  Not checked by the reviewer: whether bucket4j's greedy refill gives exactly these values.
- Upstream: no
- Test sketch:
  - Level: unit (limiter with `MutableClock`), one E2E case for the header
  - Arrange and act: consume 5 units at T, then call at T, T+5 s, T+11.5 s, T+11.999 s and T+12 s.
  - Passes when: the wait values are 12, 7, 1, the T+12 s call passes and the next fails.
  - Fails when: the refill is interval-based (T+12 s is 429), or `Retry-After` is truncated instead of rounded up (11.5 s gives 0).

### D4: Login's "own short transaction" has no mechanism, and self-invocation would silently drop it
**Status:** applied with edits (the transactional part of register is also a method on `SessionService`, so neither flow relies on self-invocation)
- Severity: major - without a real transaction a login can leave a session with no token, which the cleanup job treats as ended.
- Location: `design.md` > Register, login and logout flows
- Current text: "The password check runs outside a database transaction, so a connection isn't held during the BCrypt work. Creating the session and token is its own short transaction."
- Problem: Every existing service uses `@Transactional` on public methods (`UserService`). If `AuthService.login` is not transactional and calls a `@Transactional` method of the same bean, the proxy is bypassed (`spring-di-configuration`, "Proxy Pitfalls"). Each repository `save` then commits alone. The design names no bean or `TransactionTemplate`. Register also hashes inside its transaction, after `existsByEmail` took the connection, which is the thing login avoids.
- Failure scenario: Session insert commits, token insert fails. A session without any token stays. Or the implementer puts `@Transactional` on `login` and holds a connection during BCrypt (dev pool is 5).
- Proposed fix (`design.md`, replace the Login bullet): "**Login**: `UserRepository.findByEmail` is added. `AuthService.login` is not transactional: it loads the user, checks the password, then calls `SessionService.start(userId, transport)`, a separate bean in `service` whose `@Transactional` method inserts the session and its first token together. `AuthService.register` hashes the password before it calls the transactional part (user insert plus `SessionService.start`), so no connection is held during BCrypt." Add `SessionService` to the package table under `service`. Alternative: inject `TransactionTemplate` into `AuthService`.
- Upstream: no

### T8 / S5: The no-secrets-in-logs rule is only checked on the happy path, and nothing says how the types avoid printing secrets
**Status:** applied (one combined scenario, T8's form)
- Severity: minor - no listed code path logs a secret today, but one stray log line or exception message leaks passwords and the test would not notice. (Merged from T8 minor, S5 minor.)
- Location: `specs/auth-sessions/spec.md` > Auth flows have no side effects > Scenario: Log output of a full flow; `design.md` > Request DTOs and validation; `tasks.md` 8.4
- Current text: "- **WHEN** a client registers, logs in, refreshes and logs out\n- **THEN** the log output contains none of the submitted or issued secrets"
- Problem: Password hashes and token hashes are neither submitted nor issued, so the scenario skips them. Only the success path runs; failed logins, reuse and rate limiting are where values tend to get logged (`log.error("Unhandled exception", ex)` in `CommonExceptionHandler`). The log level is not stated. The request DTOs, `AuthSessionDto`, `AuthTokensDto`, `IssuedSession` and `RefreshToken` would be data classes whose generated `toString()` prints `password`, `refreshToken`, `accessToken` and `tokenHash`. `User` already overrides `toString()` for this reason; the design does not ask the same of the new types.
- Failure scenario: A later change adds `log.warn("Login failed for {}", request)`, or a warning on token reuse logs the presented token. Secrets land in the log aggregator. Task 8.4 still passes.
- Proposed fix:
  - `specs/auth-sessions/spec.md`, replace the scenario with:
    ```
    #### Scenario: Log output of a full flow
    - **WHEN** a client registers, logs in, refreshes and logs out, and also sends a register with a weak password, a login with a wrong password, a refresh with a rotated token, a login over the limit and a request that ends in 500, with the `com.plcoding.cardigan` loggers at `DEBUG`
    - **THEN** the log output contains none of: the submitted passwords, the stored password hash, the issued refresh tokens, their stored hashes, the issued access tokens
    ```
    (T8 puts everything in one scenario; S5 keeps the success scenario and adds a second "Log output of failed requests" scenario. Either works.)
  - `design.md` > Request DTOs and validation, add: "Every type that holds a password, token or hash (`RegisterRequest`, `LoginRequest`, `RefreshRequest`, `LogoutRequest`, `AuthSessionDto`, `AuthTokensDto`, `IssuedSession`, `RefreshToken`) overrides `toString()` to leave those values out, as `User` does."
  - `tasks.md` 8.4: add the failure cases and a unit test that the `toString()` of each type does not contain its secret.
- Upstream: no
- Test sketch (T8):
  - Level: integration (E2E with `OutputCaptureExtension`)
  - Arrange and act: run the flow, read the password hash and token hashes from the database, search the captured output.
  - Passes when: no value occurs in the output.
  - Fails when: the reuse branch logs `token=<value>`; the search finds it.

### A7 / D6: The limiter's test-only `clear()` can't be "package-internal", and it is required, not optional
**Status:** applied
- Severity: minor - caught at compile time or on the first E2E run, but the design's wording can't be implemented as written. (Merged from A7 minor, D6 minor.)
- Location: `design.md` > Rate limiter
- Current text: "The implementation has a package-internal `clear()` that test cleanup calls, so E2E tests that reuse an email don't hit the limit."
- Problem: Kotlin has no package-private visibility, and `internal` is per Gradle module. `E2eTest` lives in `:app`; the limiter lives in `:user`. Also, `E2eTest.setUpE2e` runs `clock.set(START)` before every test, so the clock moves backward between tests. bucket4j does not refill while its time source is behind the last refill time, and the Spring context (and the limiter bean) is shared across test classes. A bucket drained in one test stays drained in every later test with the same email.
- Failure scenario: `AuthRateLimitE2eTest` drains `ann@example.com`. `UserE2eTest` runs next in the same context, `signIn()` registers and logs in as `ann@example.com` (the default), and gets 429. Which tests fail depends on execution order.
- Proposed fix (`design.md`, replace the bullet): "`Bucket4jAuthRateLimiter` has a public `clear()` that empties both caches. It is not on the `AuthRateLimiter` domain interface. `E2eTest.setUpE2e` and the `:user` module test base call it before every test, next to `databaseCleaner.clean`. This is required, not a convenience: the test clock is reset backward before each test, and a bucket never refills while the clock is behind its last refill." Add to `tasks.md` 10.2: "`setUpE2e` clears the rate limiter."
- Upstream: no

### S4: Filling the bucket cache resets a victim's used-up limit
**Status:** rejected (the user chose to leave it implicit)
- Severity: minor - the gain is capped by how fast the sender can send requests, and D10 already accepts weak limits; still an undocumented bypass of "5 per minute per email".
- Location: `specs/auth-rate-limiting/spec.md` > Requirement: Limits are kept per instance in bounded memory; `design.md` > Rate limiter
- Current text: "the number of tracked emails has a configured maximum, and an email that was not used for longer than the limit window stops being tracked."
- Problem: When the maximum is reached, Caffeine drops entries by size, including buckets that are currently empty. A dropped bucket comes back full. The requests that fill the cache are cheap: a login with an email and no password counts toward the limit and fails validation before any hashing. Design says "dropping it changes nothing", which is only true for expiry, not for size eviction.
- Failure scenario: Someone uses 5 guesses for `ann@example.com`, then sends a burst of logins without a password for 100,000+ throwaway emails, then gets 5 more guesses without waiting. Tests cover the bound and the limit separately, never together.
- Proposed fix: recommended, since failing closed on a full cache would let anyone block all logins: document it. Add to the requirement: "When the maximum is reached, tracked emails are dropped and their limits start again. This is an accepted trade-off of the in-memory limiter." and add to `design.md` Risks: "[A client that sends requests for more emails than `max-tracked-emails` can evict a used-up bucket and get a fresh limit for that email] → Accepted; it takes over 100,000 requests per 5 extra attempts." Alternative: answer 429 for emails that are not tracked while the cache is full, which closes the bypass but lets a flood block all logins.
- Note: the assumptions reviewer listed this as safe to leave implicit, assuming each filler request costs BCrypt work. See "Conflicts between reviewers".
- Upstream: no

### T7: "Stored hash" asserts cost 12, but tests run with cost 4
**Status:** applied
- Severity: minor - caught on the first test run, but the scenario and the test profile contradict each other.
- Location: `specs/user-accounts/spec.md` > Passwords are hashed with a slow one-way hash over the whole password > Scenario: Stored hash
- Current text: "**THEN** the stored password hash is a BCrypt hash with cost 12 and does not contain the password"
- Problem: The requirement says "a configured cost, 12 by default". The test profile sets 4, so an E2E test of this scenario sees `$2a$04$`. The default of 12 has no scenario.
- Failure scenario: The E2E test asserts `$2a$12$` and fails, or the implementer hard-codes 12 to make it pass and the config value stops working.
- Proposed fix: Replace the scenario with:
  ```
  #### Scenario: Stored hash
  - **WHEN** the cost is configured as 5 and a user registers with the password `Correct horse 7`
  - **THEN** the stored password hash is a BCrypt hash with cost 5 and does not contain the password

  #### Scenario: Default cost
  - **WHEN** no cost is configured
  - **THEN** the cost is 12
  ```
- Upstream: no
- Test sketch:
  - Level: unit (hasher) and a properties binding test
  - Arrange and act: hash with a hasher built with cost 5; bind `AuthProperties` from the main `application.yaml`.
  - Passes when: the hash starts with `$2a$05$` (or `$2b$05$`); the bound cost is 12.
  - Fails when: the cost is hard-coded; the prefix is `$2a$12$`.

### T9: "Same headers" for the two login failures is not literally testable
**Status:** applied
- Severity: minor - the test is flaky or gets weakened without a rule.
- Location: `specs/auth-sessions/spec.md` > Login matches the lowercased email and the exact password > Scenario: Unknown email and wrong password
- Current text: "**THEN** both responses have the same status, headers and body"
- Problem: Tomcat sends a `Date` header, which differs between two requests that cross a second boundary. The equal hashing work the requirement points to has no observable check either.
- Failure scenario: The test compares all headers and fails about once in many runs. Someone removes the header comparison, and a `WWW-Authenticate` difference goes unnoticed.
- Proposed fix: Replace the THEN with "**THEN** both responses are 401 `INVALID_CREDENTIALS` with the same body and the same headers, not counting `Date`, and neither has `WWW-Authenticate`". Add:
  ```
  #### Scenario: Equal work for an unknown email
  - **WHEN** a login uses an unknown email
  - **THEN** the password hasher verifies the submitted password once, as for a known email
  ```
- Upstream: no
- Test sketch:
  - Level: E2E for the response, unit (`AuthService` with a counting fake hasher) for the work
  - Arrange and act: send both logins; in the unit test call `login` with an unknown email.
  - Passes when: bodies and headers minus `Date` are equal; the fake's `matches` count is 1.
  - Fails when: the unknown-email branch returns early; the count is 0.

### T10: "Before the uniqueness check" has no scenario
**Status:** applied
- Severity: minor - a small gap with an easy test.
- Location: `specs/auth-rate-limiting/spec.md` > The limit is checked before validation and credentials (citation corrected: the reviewer read it as "Over the limit" in the earlier version)
- Current text: "a request over the limit SHALL answer 429 `RATE_LIMITED` without field validation, the credentials or the uniqueness check being evaluated"
- Problem: The requirement has scenarios for the credentials and for field validation, but none for the uniqueness check (429 against 409).
- Failure scenario: Register checks `existsByEmail` before the limiter. A client over the limit can still probe which emails exist through 409, and no test fails.
- Proposed fix: Add:
  ```
  #### Scenario: Used email over the limit
  - **WHEN** a client over the register limit for a registered email sends a valid register request for it
  - **THEN** the response is 429 `RATE_LIMITED`, not 409
  ```
- Narrowed during the merge: the reviewer's other two points no longer apply to this change. "Unparseable body ... no limit is used up" was rewritten in commit `9facfce` and is now observable. "Refresh and logout SHALL never answer 429" moved to the shared spec.
- Upstream: no
- Test sketch:
  - Level: integration (E2E)
  - Arrange and act: register `ann@example.com`, send 5 more register requests for it, then a sixth.
  - Passes when: the sixth is 429.
  - Fails when: the uniqueness check runs first; the sixth is 409.

### T11: Missing scenarios for logout validation, refresh's status and the register cookie
**Status:** applied
- Severity: minor - same code paths as tested cases, but each promise is untested.
- Location: `specs/auth-sessions/spec.md` > Refresh and logout take the transport and an optional token; Auth endpoints and status codes; The refresh cookie
- Current text: "Refresh and logout requests SHALL require `refreshTokenTransport`", "`POST /api/v1/auth/refresh` (200)" and "With the `COOKIE` transport, the backend SHALL set the refresh token in a cookie"
- Problem: All three scenarios of the first requirement are about refresh. No scenario states that refresh answers 200. The cookie is only checked on login, not on register.
- Failure scenario: Logout skips validation and answers 204 for `{}`, or register with `COOKIE` sets no cookie and the web user is signed out after 15 minutes.
- Proposed fix: Add:
  ```
  #### Scenario: Logout without transport
  - **WHEN** a client logs out with `{}`
  - **THEN** the response is 400 `VALIDATION_FAILED` with `refreshTokenTransport` `REQUIRED`

  #### Scenario: Body logout without a token
  - **WHEN** a client logs out with `BODY` and no `refreshToken`
  - **THEN** the response is 204
  ```
  In "Refresh with the body transport" change the THEN to "the response is 200 and the body has exactly ...". Add to "The refresh cookie":
  ```
  #### Scenario: Cookie on web register
  - **WHEN** a client registers with `COOKIE`
  - **THEN** the `Set-Cookie` header is for `cardigan_refresh_token` with the same attributes, and a refresh with that cookie and `COOKIE` succeeds
  ```
- Upstream: no
- Test sketch:
  - Level: integration (E2E)
  - Arrange and act: register with `COOKIE`, read `Set-Cookie`, refresh with the cookie value.
  - Passes when: the attributes match and the refresh is 200.
  - Fails when: the cookie is only built on login; no `Set-Cookie` header is present.

### T12: "Parallel registration" depends on timing the test cannot control
**Status:** applied
- Severity: minor - task 5.2 has the deterministic check, but the spec scenario alone passes for a broken implementation.
- Location: `specs/auth-sessions/spec.md` > Register rejects a used email > Scenario: Parallel registration
- Current text: "**WHEN** two register requests for the same new email are processed at the same time"
- Problem: Two threads rarely overlap between the existence check and the insert. Without the constraint translation the test still passes most runs, and fails with 500 only when the race happens.
- Failure scenario: `JpaUserRepository.save` does not translate the unique violation. The threaded test is green in CI and production answers 500 on a double tap.
- Proposed fix: Keep the scenario and add:
  ```
  #### Scenario: Email stored after the existence check
  - **WHEN** a registration stores a user whose email another registration stored after the first one's existence check
  - **THEN** the result is the same 409 `CONFLICT` with `email` `ALREADY_USED`, and one user exists
  ```
- Upstream: no
- Test sketch:
  - Level: integration (`@DataJpaTest`)
  - Arrange and act: save a user, then call `save` for a second user with the same email without calling `existsByEmail`.
  - Passes when: `EmailAlreadyUsedException` is thrown.
  - Fails when: the adapter does not flush or translate; `DataIntegrityViolationException` surfaces.

### T13: "No events, emails or other systems" has no scenario
**Status:** applied
- Severity: minor - a negative promise that no listed test checks.
- Location: `specs/auth-sessions/spec.md` > Auth flows have no side effects
- Current text: "Register, login, refresh and logout SHALL NOT publish events, send emails or call other systems."
- Problem: The only scenario is about logs. Emails and outside calls cannot happen without new infrastructure, so only the event part needs a test.
- Failure scenario: Someone adds a `UserRegistered` event "for later". The Modulith registry is not wired, so it is lost on a crash, and nothing flags that D14 was broken.
- Proposed fix: Add:
  ```
  #### Scenario: No events
  - **WHEN** a client registers, logs in, refreshes and logs out
  - **THEN** no application event with a payload type from `com.plcoding.cardigan` is published
  ```
- Upstream: no
- Test sketch:
  - Level: module integration
  - Arrange and act: register a test `@EventListener` for `Any` that records payloads from the project package, then run the four service calls.
  - Passes when: the recorded list is empty.
  - Fails when: `AuthService.register` publishes an event; the list has one entry.

### T14: The cleanup schedule and its expiry boundary are untested
**Status:** applied
- Severity: minor - "03:00 UTC" and the exact boundary are stated but no scenario checks them.
- Location: `specs/auth-sessions/spec.md` > Ended sessions are deleted daily
- Current text: "A job SHALL run daily at 03:00 UTC" and "**WHEN** a session's current refresh token expired one second ago and the job runs"
- Problem: Every scenario starts with "the job runs". Nothing checks the time or the zone; task 9.1 only checks that the cron resolves. The side just before expiry is missing, so a job that deletes sessions too early passes.
- Failure scenario: The `zone` attribute is left out and the server runs in another zone, or the delete comparison is reversed and removes a session with 1 ms left.
- Proposed fix: Add:
  ```
  #### Scenario: Schedule
  - **WHEN** the default configuration is used and the time is `2026-11-15T09:15:30Z`
  - **THEN** the job's next run is at `2026-11-16T03:00:00Z`

  #### Scenario: Session about to expire
  - **WHEN** a session's current refresh token expires in 1 millisecond and the job runs
  - **THEN** the session remains and a refresh with that token succeeds
  ```
  Change "Expired session" to "expired at this instant or earlier".
- Upstream: no
- Test sketch:
  - Level: integration (context test for the schedule, persistence test for the boundary)
  - Arrange and act: read the cron task from `ScheduledAnnotationBeanPostProcessor.getScheduledTasks()` and ask its trigger for the next run after the given instant; run the delete at `expiresAt - 1 ms` and at `expiresAt`.
  - Passes when: the next run is 03:00 UTC the next day; the session exists at -1 ms and is gone at 0.
  - Fails when: the zone is missing and the JVM default is not UTC.

### T16: "Processed" is not defined
**Status:** applied
- Severity: minor - testers have to guess the assertion.
- Location: `specs/auth-rate-limiting/spec.md` > Limit values > Scenario: Sixth login (also "Sixth register", "Refill", "Restart", "Unparseable body")
- Current text: "**THEN** the first 5 are processed and the sixth answers 429 `RATE_LIMITED`"
- Problem: "Processed" has no observable meaning. With a wrong password it means 401, with a right one 200.
- Failure scenario: One test asserts 200 for the first five and uses a wrong password, so it fails for no real reason.
- Proposed fix: Add to the "Limit values" requirement: "A request is processed when its answer is anything other than 429."
- Upstream: no
- Test sketch:
  - Level: integration (E2E)
  - Arrange and act: send 6 logins with a wrong password at one instant.
  - Passes when: five are 401 `INVALID_CREDENTIALS` and the sixth is 429.
  - Fails when: the capacity is read as 4; the fifth is 429.

### A6: The cookie path assumes the browser sees the same path as the backend
**Status:** applied with edits (`design.md` only; `decisions.md` is never edited by this review)
- Severity: minor - nothing in this repo breaks, but web refresh silently fails if a proxy mounts the API under a prefix.
- Location: `design.md` > Cookie handling
- Current text: "`RefreshTokenCookie` builds a `ResponseCookie`: name `cardigan_refresh_token`, `httpOnly`, `secure`, `sameSite(\"Strict\")`, `path(\"/api/v1/auth\")`"
- Assumption: the production reverse proxy and the web dev proxy forward `/api/v1/auth/*` without adding or stripping a path prefix, and on the same site as the web app.
- Problem: `decisions.md` records "same domain behind one reverse proxy", but not that the path is unchanged. The `Path` attribute is matched against the URL the browser requests.
- Failure scenario: `41-infra-vps` or the Vite dev proxy exposes the backend as `/backend/api/v1/...`. The browser stores the cookie with `Path=/api/v1/auth` and never sends it to `/backend/api/v1/auth/refresh`. Every web refresh answers 401 and users are logged out every 15 minutes.
- Proposed fix: Add to `design.md` > Cookie handling: "The cookie path is the backend's own path. Any proxy in front of the backend, including the web dev proxy, must expose the auth routes under exactly `/api/v1/auth` on the web app's site. A path prefix or a different site breaks the `COOKIE` transport." The reviewer also proposed the same sentence for `decisions.md` > Facts; this review never edits `decisions.md`, so that part is the user's to add.
- Upstream: no

### A9: Token storage assumes clients refresh about once per access-token lifetime
**Status:** applied
- Severity: minor - D8 and D12 are settled, so this is only a missing entry in the known trade-offs.
- Location: `design.md` > Risks / Trade-offs
- Current text: "[`sessions` and `refresh_tokens` exist only through `ddl-auto`] → They must be in the baseline migration before the first production deploy (deferred, see `decisions.md`)."
- Assumption: the number of rotated tokens per session stays small.
- Problem: Rotated tokens of an active session are kept for the session's whole life (D8, shared spec), refresh has no rate limit (D12), and each successful refresh hands the client the next token. One client can add rows without bound. The Risks section lists the other accepted trade-offs but not this one.
- Failure scenario: A buggy or hostile client loops refresh with `BODY` at 100 requests per second. That is about 8.6 million `refresh_tokens` rows per day from one session, which fills a small Supabase database. The cleanup job never removes them because the session is active.
- Proposed fix: Add to `design.md` > Risks: "[An active session keeps every rotated token, and refresh is not rate limited, so one client that refreshes in a loop grows `refresh_tokens` without bound] → Accepted with D8 and D12. Normal clients add about 100 rows per session per day. If table size becomes a problem, the fix is a per-session refresh limit or a cap on kept tokens, which needs a new decision."
- Upstream: no

### D7: `reject(FieldViolation)` is internal to `:common`, and the shared touch points with be-boards and be-labels are not listed
**Status:** applied
- Severity: minor - compile error in `:user`, or a duplicate change when two chunks land.
- Location: `design.md` > Request DTOs and validation; Error mapping in `:common`
- Current text: "It reports through the same dynamic payload as `@ValidText`, so `min` and `max` reach the field error." and "If `be-boards` or `be-labels` land first and already added base exceptions to `:common` ... these three are added beside them."
- Problem: `ConstraintValidatorContext.reject` in `common/.../api/validation/FieldViolation.kt` is `internal`, so `@ValidEmail` and `@ValidPassword` in `:user` can't call it. `be-boards` task 1.3 makes it public for the same reason. be-auth has no such task. Both other changes also touch `BaseExceptions.kt`, `CommonExceptionHandler`, `TestApiController`, `E2eTest`, `openapi.json`, `gradle/libs.versions.toml` and `AGENTS.md` (`CLAUDE.md` is a symlink to it).
- Failure scenario: be-auth is implemented first and `@ValidPassword` doesn't compile, or the implementer copies the payload code into `:user`.
- Proposed fix (`design.md`, add under "Request DTOs and validation"): "`reject(FieldViolation)` in `:common` is `internal` today. It becomes public, unless `be-boards` (its task 1.3) already did that." Add to `tasks.md` 4.1: "Make `reject(FieldViolation)` public if it still is internal." Add to Risks: "[`be-boards` and `be-labels` run in parallel] → Shared touch points: `BaseExceptions.kt`, `CommonExceptionHandler`, `reject`, `TestApiController`, `E2eTest`, `libs.versions.toml`, `AGENTS.md` and the snapshot. Whichever change lands second merges them."
- Upstream: no

### D8: The controller calls the rate limiter directly and computes its key
**Status:** applied
- Severity: minor - breaks a stated layer rule and duplicates the email normalization in two places.
- Location: `design.md` > Order of checks in the controller and service; Rate limiter; Package placement
- Current text: "The controller method takes the raw request without `@Valid`, computes the rate limit key from `email`, and calls `AuthRateLimiter`." and "| `SessionRepository`, `RefreshTokenRepository`, `PasswordHasher`, `RefreshTokenGenerator`, `AuthRateLimiter` | `domain/repository` |"
- Problem: `CLAUDE.md`: "Controllers never inject repositories and never hold business rules", and a rule that needs state goes in `service`. Here the controller injects an outbound interface from `domain/repository` and owns the key rule (`normalizeText(email).lowercase`), which the mapper repeats for the lookup. `AuthAction` is also missing from the package table.
- Failure scenario: The key normalization and the mapper's normalization drift apart, and the limit key no longer matches the account that is checked.
- Proposed fix (`design.md`, step 2): "The controller method takes the raw request without `@Valid` and calls `AuthService.checkRateLimit(AuthAction.LOGIN, request.email)` (or `REGISTER`). The service normalizes and lowercases the email with the same domain function the mappers use, skips the limiter when the result is empty, and otherwise calls `AuthRateLimiter`." Add `AuthAction` to the `domain/model` row. Alternative: keep the call in the controller and record it in `CLAUDE.md` as an accepted exception.
- Upstream: no

### D9: Two `:user` exceptions have the same simple name as their `:common` base class
**Status:** applied
- Severity: minor - name clash and two classes that add nothing.
- Location: `design.md` > Package placement
- Current text: "| `InvalidCredentialsException`, `InvalidRefreshTokenException`, `EmailAlreadyUsedException`, `AuthRateLimitedException` | `domain/exception` |" and "| `InvalidCredentialsException`, `InvalidRefreshTokenException`, `RateLimitedException` base classes | `:common` `domain/exception` |"
- Problem: A `:user` class `InvalidCredentialsException` extending `common...InvalidCredentialsException` needs a fully qualified supertype and is easy to import wrongly. The base classes are `open` and non-abstract (`BaseExceptions.kt`), and `be-boards` throws its new base exception directly ("no subclass is needed").
- Failure scenario: A file imports the `:common` class where the `:user` one was meant, or the reverse. Both compile and behave the same, so the subclass is dead weight.
- Proposed fix (`design.md`): remove `InvalidCredentialsException` and `InvalidRefreshTokenException` from the `:user` row and add: "`AuthService` and `AuthController` throw the `:common` `InvalidCredentialsException` and `InvalidRefreshTokenException` directly; they carry no data, so `:user` adds no subclass. `EmailAlreadyUsedException` and `AuthRateLimitedException` stay." Alternative: keep subclasses under different names (`LoginFailedException`, `RefreshRejectedException`).
- Upstream: no

### D10: `PasswordHasher` has no way to do the dummy check the login flow needs
**Status:** applied
- Severity: minor - the interface and the flow don't match; one of them changes during implementation.
- Location: `design.md` > Password hashing
- Current text: "Domain interface `PasswordHasher` with `hash(password): String` and `matches(password, hash): Boolean`." and "login calls `matches` against a dummy hash that the hasher creates once at startup"
- Problem: The service can't reach a hash that lives inside the implementation through these two methods.
- Failure scenario: The implementer exposes the dummy hash as a string in the service, or adds a method the fake in task 7.1 doesn't have.
- Proposed fix (`design.md`): "Domain interface `PasswordHasher` with `hash(password): String` and `matches(password, hash: String?): Boolean`. With a `null` hash, the implementation checks against a dummy hash it created once at startup with the same cost and returns false. Login passes `user?.passwordHash`."
- Upstream: no

### D11: Changing `JpaUserRepository.save` breaks two existing tests and doesn't say how the email constraint is recognized
**Status:** applied
- Severity: minor - caught by the build, but "pass unchanged" expectations don't hold for `:user`.
- Location: `design.md` > Register, login and logout flows (Register); `tasks.md` 5.2
- Current text: "`JpaUserRepository.save` flushes and translates the constraint violation into the same exception."
- Problem: `JpaUserRepositoryTest` ("exact duplicate email is rejected", "mixed-case input lowercased before storing is rejected as a duplicate") expects `save` to return and `entityManager.flush()` to throw Hibernate's `ConstraintViolationException`. `UserEntity` declares the unique constraint as `@Column(unique = true)` with a generated name, and the table also has the check `users_email_lowercase`, so "a constraint violation" is not enough to mean "email used".
- Failure scenario: The two tests fail after 5.2. Or the adapter maps every integrity violation, including the lowercase check, to 409 `ALREADY_USED`.
- Proposed fix (`design.md`): "`JpaUserRepository.save` uses `saveAndFlush` and turns a violation of the email unique constraint into `EmailAlreadyUsedException`. The constraint gets the explicit name `users_email_unique` (`@Table(uniqueConstraints = ...)`), and only that name is translated. The two duplicate tests in `JpaUserRepositoryTest` change to expect `EmailAlreadyUsedException` from `save`." Add the last sentence to `tasks.md` 5.2.
- Upstream: no

### D12: `AuthProperties` sits in `infra/config` but the service and the cookie helper need its values
**Status:** applied
- Severity: minor - forces a `service` → `infra` or `api` → `infra` import that the layer rules forbid.
- Location: `design.md` > Package placement; Cookie handling
- Current text: "| `AuthProperties`, `UserPublicRoutesConfig` | `infra/config` |" and "`maxAge` of the refresh-token TTL"
- Problem: `AuthService` needs `refresh-token-ttl`, and `RefreshTokenCookie` (`api`) needs it for `Max-Age`. `CLAUDE.md`: "Services depend on domain interfaces, never on `infra` classes"; `api` may depend on `service` and `domain` only. The design doesn't say how the value gets there. The skill `spring-di-configuration` allows properties classes in `service`.
- Failure scenario: `AuthService` imports `user.infra.config.AuthProperties`, against the layer table and the review checklist.
- Proposed fix (`design.md`): "The properties are split by consumer: `AuthProperties` (`cardigan.user.auth`: `refresh-token-ttl`, `session-cleanup-cron`) lives in `service`; `PasswordHashingProperties` (`bcrypt-cost`) and `AuthRateLimitProperties` (`rate-limit.*`) live in `infra/config`. The YAML keys stay as shown. `IssuedSession` and the refresh result carry `refreshTokenExpiresAt`, and `RefreshTokenCookie` computes `Max-Age` from it and the clock, so `api` reads no config." Alternative: keep one class in `infra/config` and record the exception in `CLAUDE.md`.
- Upstream: no

### D13: `:user` module integration tests can't start as the test app stands
**Status:** applied
- Severity: minor - tasks 7.4 and 9.1 need setup the design doesn't mention.
- Location: `design.md` > Tests; Configuration; `tasks.md` 1.2, 7.4
- Current text: "Module integration: parallel refresh and parallel register with real threads against Postgres." and "The values live in `:app` `application.yaml`."
- Problem: `UserTestApplication` is a bare `@SpringBootApplication` in `com.plcoding.cardigan.user`. It doesn't scan `:common` (no `AccessTokenIssuer`), has no `Clock` bean (that is in `:app`), and has no `@ConfigurationPropertiesScan`. `:app`'s `application.yaml` is not on `:user`'s test classpath, so `cardigan.user.auth.*` has no values there unless the properties class has defaults. The design says both "default 12" and "values live in `application.yaml`".
- Failure scenario: The 7.4 context fails with missing beans or unbound properties, and the implementer picks defaults in one place or the other.
- Proposed fix (`design.md`, "Tests"): "`UserTestApplication` gets `@ConfigurationPropertiesScan` and imports a `UserTestConfig` with `PostgresTestConfig`, a `MutableClock` bean and a fake `AccessTokenIssuer`. The properties classes carry the defaults from the YAML block (TTL `30d`, cost 12, cron, limits), so `:user` tests need no extra YAML; `:app` `application.yaml` repeats them as the visible setting." Add this to `tasks.md` 1.2.
- Upstream: no

### D14: "Clear `nullable` for response schemas" is too broad, and two conditionals are already answered by the code
**Status:** applied
- Severity: minor - a general change to the customizer would break the `CLAUDE.md` contract for other response DTOs.
- Location: `design.md` > Response DTOs and OpenAPI; `proposal.md` > Impact
- Current text: "if springdoc adds it, the customizer clears it for response schemas the same way it does for request schemas.", "If the customizer already documents `VALIDATION_FAILED` for validated bodies, those entries are dropped." and "`:common`: a `RateLimitedException` base exception mapped to 429 with `Retry-After`, if not already there."
- Problem: `CLAUDE.md` says a nullable response property is `nullable`. Clearing it for all response schemas changes that rule for `be-boards` and later chunks. `CardiganOpenApiCustomizer.customizeOperation` adds only `MALFORMED_REQUEST`, 413 and 415 for bodies, never `VALIDATION_FAILED`, so the `@ApiErrors` entries are needed. `BaseExceptions.kt` has no `RateLimitedException`, and the proposal's Impact omits the other two base exceptions that `design.md` adds.
- Failure scenario: The implementer sets `nullable = null` on every response property, and a later nullable response field loses `nullable: true` in the snapshot.
- Proposed fix (`design.md`): "The snapshot must not show `nullable: true` for `refreshToken`. `customizeResponseSchema` clears `nullable` only for properties whose field carries `@JsonInclude(NON_NULL)`; other nullable response properties stay `nullable`." and "The customizer does not document `VALIDATION_FAILED`, so every operation lists it in `@ApiErrors(400, VALIDATION_FAILED)`." (`proposal.md` Impact): "`:common`: the base exceptions `InvalidCredentialsException`, `InvalidRefreshTokenException` and `RateLimitedException`, mapped in `CommonExceptionHandler` (401 without `WWW-Authenticate`, 429 with `Retry-After`), and a public `reject(FieldViolation)`."
- Upstream: no

## Security analysis

### Trust boundaries

**Login request body (unauthenticated network input)**
- Attacker position: anyone on the internet, no account.
- Attack path: (a) send logins for many distinct emails so every request runs BCrypt cost 12; (b) send an email with a character Postgres can't store, such as U+0000; (c) send very long emails or passwords.
- What they gain: (a) CPU starvation of the whole app; (b) a 500 and an error log entry per request instead of 401; (c) nothing, the 256KB body limit and SHA-256 pre-hash bound the work.
- What stops it: (a) nothing, see S1. (b) nothing, see S3 / A2. (c) `cardigan.web.max-request-body-size` and the pre-hash in design "Password hashing".

**Register request body**
- Attacker position: anyone, no account.
- Attack path: send an over-long, crafted `email` so the hand-written RFC 5322 regex runs on it; or enumerate emails through 409.
- What they gain: a request thread stuck in regex backtracking (S2). Enumeration through 409 `ALREADY_USED` is inherent in shared `api-errors` and the email-only limit is settled in D10, so it is not reported.
- What stops it: for the regex, nothing. `@ValidText(max = 254)` reports `TOO_LONG` but Bean Validation still runs `@ValidEmail` on the same value.

**Rate limiter state (attacker-controlled keys)**
- Attacker position: anyone, guessing one victim's password.
- Attack path: use the victim's 5 attempts, then send cheap login requests (email set, no password, so no BCrypt) for more distinct emails than `max-tracked-emails` to push the victim's bucket out of the Caffeine cache. The bucket is recreated full.
- What they gain: more than 5 guesses per minute for one email. Also the accepted lockout of a victim (D10, not reported).
- What stops it: nothing in the spec; the bound is only the request throughput. See S4.

**Refresh token presented to refresh (body or cookie)**
- Attacker position: holds a stolen current or rotated refresh token, or guesses.
- Attack path: replay a rotated token; race two refreshes; present a cookie-session token with `BODY`; guess tokens.
- What they gain: nothing beyond what the shared spec accepts.
- What stops it: 256-bit tokens stored as SHA-256; the single-statement claim in design "Refresh flow" step 6; "Session revocation survives the failed request"; "A wrong transport does not end the session". A refresh racing a logout can still hand out one access token for a revoked session, but its new refresh token fails at step 3, and shared "Token lifetimes" already accepts access tokens outliving logout.

**Refresh cookie in the browser (cross-site and script access)**
- Attacker position: a malicious site the victim visits, or script injected into the web app.
- Attack path: cross-site POST to refresh, logout or login to rotate, clear or replace the victim's session; read the cookie from script.
- What they gain: nothing.
- What stops it: `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`; JSON-only bodies with 415 for form content types and no CORS, so a cross-origin JSON request fails preflight. Cookie tossing from a sibling subdomain is possible without a `__Secure-`/`__Host-` prefix, but D16 considered and rejected the prefix, so it is not reported. This protection depends on CORS staying off; a later CORS change must revisit forced logout and login CSRF.

**Logout (public, token only)**
- Attacker position: anyone; or holder of an old token.
- Attack path: probe tokens through logout; force a logout with a rotated token.
- What they gain: nothing new. Forced logout needs a stolen token, the same as reuse on refresh.
- What stops it: "Logout is public and always succeeds" (always 204, no oracle) and "Logout with a rotated token ends its session".

**Public route list and the `Authorization` header**
- Attacker position: anyone.
- Attack path: reach a protected route through a path variant of an auth route, or other methods on auth paths.
- What they gain: nothing.
- What stops it: exact method-and-path matching in `PublicRouteRegistry` and the modified "Public routes are listed explicitly" (scenario "Other method on an auth path"). Mismatches fail closed (401).

**Database and logs (stored secrets)**
- Attacker position: someone with read access to the database or log output.
- Attack path: read refresh tokens or passwords from rows or log lines.
- What they gain: with the database, only hashes. With logs, secrets if any failure path prints a request DTO or token.
- What stops it: "Sessions and refresh tokens are stored hashed", the `user-accounts` hashing requirement. For logs, the requirement exists but is only checked on the happy path, see T8 / S5.

**Scheduled cleanup versus reuse detection**
- Attacker position: holder of a rotated token who waits for cleanup.
- Attack path: hope cleanup removes the rotated-token record of an active session so the reuse is not detected.
- What they gain: nothing.
- What stops it: "Ended sessions are deleted daily" keeps all rotated tokens of sessions that are not ended; deleted sessions answer 401 as unknown tokens.

### Test versus production

**Login failures look the same (equal work for unknown email)**
- What a test would do: unit test with a fake hasher counting one call per login; E2E comparing status, headers and body at BCrypt cost 4.
- What differs in production: cost 12, and real Postgres between the request and the hash.
- How it fails: an email Postgres rejects (U+0000) fails in `findByEmail` before any hashing and answers 500, see S3 / A2. Otherwise no gap: the dummy hash is created with the configured cost. Caveat with no finding: if `bcrypt-cost` is changed later, old user hashes and the dummy hash have different costs until those users are rehashed.
- What would close the gap: S3 / A2.

**Limit values ("Sixth login" answers 429)**
- What a test would do: 6 requests for one email on `MutableClock`, limiter cleared between tests, a small `max-tracked-emails` to test the bound.
- What differs in production: anyone can add entries to the same cache at will, and several instances multiply the limit (accepted in `CLAUDE.md`).
- How it fails: eviction by size drops the victim's used-up bucket; the next request gets a fresh bucket. Tests never mix eviction with a used-up bucket. See S4.
- What would close the gap: S4.

**Rate limit is checked before credentials**
- What a test would do: over-limit request with the right password, assert 429 and no session.
- What differs in production: nothing relevant. The limiter key and the lookup key are the same function, and NFC maps look-alikes such as the Kelvin sign before lowercasing.
- How it fails: no realistic gap found.
- What would close the gap: nothing needed.

**Parallel refresh, at most one success, and the revocation is committed**
- What a test would do: task 7.4, real threads against Testcontainers Postgres.
- What differs in production: nothing that changes the result; the claim is one `update ... where rotated_at is null`, safe under READ COMMITTED.
- How it fails: no realistic gap found. It would break only if someone replaced the claim with read-then-write; the `@DataJpaTest` in task 5.2 pins the statement.
- What would close the gap: nothing needed.

**Passwords are hashed over the whole password with BCrypt**
- What a test would do: unit tests at cost 4 for the 72-byte boundary, NFC forms and salt.
- What differs in production: cost 12, about 250 ms of CPU per call, on virtual threads.
- How it fails: correctness holds. Availability does not: a handful of concurrent logins occupy every carrier thread, and no test runs at production cost, see S1.
- What would close the gap: S1.

**The refresh cookie attributes, on every environment**
- What a test would do: E2E over plain HTTP asserting the `Set-Cookie` attributes.
- What differs in production: TLS ends at the reverse proxy, so the backend also sees plain HTTP there.
- How it fails: no gap. The spec sets `Secure` unconditionally and has the scenario "Cookie over plain HTTP".
- What would close the gap: nothing needed.

**Logs contain no passwords, hashes or tokens**
- What a test would do: task 8.4, one successful register, login, refresh, logout with `OutputCaptureExtension`.
- What differs in production: most log lines come from failures, and request DTOs are Kotlin data classes whose `toString()` prints every property.
- How it fails: a failure path or a later log line prints `LoginRequest(email=..., password=...)` or an `IssuedSession` with the raw token; the happy-path test stays green. See T8 / S5.
- What would close the gap: T8 / S5.

## Assumptions safe to leave implicit

| Assumption | Where it is implied | Why it is safe |
|---|---|---|
| The system clock does not step backward | `design.md` > Rate limiter (bucket4j `TimeMeter` and Caffeine `Ticker` read the `Clock` bean) | `backend-time` requires one clock. A backward step only delays refill for already drained emails, and the state is gone on restart. |
| The app is running at 03:00 UTC, and the cron trigger uses system time, not the `Clock` bean | auth-sessions > "Ended sessions are deleted daily" | Ended sessions are rejected by the refresh and logout checks whether or not their rows are deleted. A missed run is made up the next day. Tests call the method directly. |
| Browsers accept a `Secure` cookie from `http://localhost` | `design.md` > Cookie handling | Stated in the design. It fails only in local development on browsers that refuse it, and the attributes are fixed by the shared spec. |
| No stored password or email holds an unpaired surrogate | Login skips `checkText`; UTF-8 encoding turns a lone surrogate into `?` | Register rejects them through `checkText`, so a match would need the rest of the correct password. |
| Evicting a drained bucket at the cache maximum does not weaken the limit | `design.md` > Rate limiter (`maximumSize`) | Forcing an eviction takes about 100,000 login requests, each with BCrypt work. Waiting 12 seconds for a refill is cheaper. **Disputed by S4**, which says the filler requests need no BCrypt work. |
| Register can hold a database connection during the BCrypt hash | `design.md` > Register ("one transaction") | It only queues other requests for a few hundred milliseconds. (D4 proposes hashing before the transaction anyway.) |
| Existing `users` rows have BCrypt hashes | Login `matches` against stored hashes | Only dev and test rows exist. `BCryptPasswordEncoder.matches` returns false for a non-BCrypt value, which gives 401. |
| Two refresh tokens never collide | auth-sessions > "Sessions and refresh tokens are stored hashed" | 256 random bits, and the unique index on `token_hash` fails loudly if it ever happened. |
| A refresh token value is safe in a cookie and a JSON string | `design.md` > Refresh tokens (base64url, 43 characters) | The base64url alphabet has no character that cookies or JSON need escaped. |
| The request body is small enough to hash and normalize the email cheaply | `design.md` > Rate limiter | `cardigan.web.max-request-body-size` is 256KB and is enforced before the controller. |

## New abstractions

- `AuthRateLimiter` (domain interface) + `Bucket4jAuthRateLimiter`: fine as is. `CLAUDE.md` prescribes exactly this split. Who calls it: see D8.
- `PasswordHasher` (domain interface) + `BcryptPasswordHasher`: keep. Keeps Spring Security out of `domain` and lets unit tests use a fast fake. Signature gap: D10.
- `RefreshTokenGenerator` (domain interface) + `SecureRandomRefreshTokenGenerator`: keep and document. One implementation; the only reason is predictable tokens in fakes. `design.md` should say so: "`RefreshTokenGenerator` is an interface so unit tests can supply known tokens and assert on their hashes."
- `SessionRepository`, `RefreshTokenRepository` (+ Spring Data interfaces and JPA adapters): fine as is, same pattern as `UserRepository`.
- `Session`, `RefreshToken`, `RefreshTokenTransport`, `IssuedSession` (domain models): fine as is. The refresh result type and `AuthAction` are used but not in the package table (D8).
- `sessions`, `refresh_tokens` tables: fine as is (D7 in `decisions.md`). FK mapping: A4 / D3.
- `InvalidCredentialsException`, `InvalidRefreshTokenException`, `RateLimitedException` in `:common`: keep, documented. Needed because `UnauthenticatedException` always sends the bearer challenge.
- `:user` `InvalidCredentialsException`, `InvalidRefreshTokenException`: challenge (D9). `EmailAlreadyUsedException`, `AuthRateLimitedException`: fine as is.
- `@ValidEmail`, `@ValidPassword` in `:user` `api/validation`: fine as is; the layout has a place for them. Needs `reject` public (D7). Login needs two more small constraints (T3 / A3 / D1 / D2).
- `RefreshTokenCookie` (api helper): fine as is. Config access: D12.
- `AuthService`, `SessionCleanupService`: fine as is. `@Scheduled` on the owning service matches `CLAUDE.md`. Transaction split for login: D4.
- `AuthProperties`: placement issue, D12.
- `infra/security`, `infra/ratelimit` sub-packages: fine as is, documented, and task 10.3 adds them to `CLAUDE.md`.
- `UserPublicRoutesConfig` (`PublicRoutes` bean): fine as is; uses the existing `PublicRoutes`/`PublicRouteRegistry`.
- Public API (four operations, seven schemas): fine as is, fixed in `decisions.md` D1, D2, D18.

## Upstream: shared specs

Both findings were written against scenarios that were in this change's `auth-rate-limiting` delta when the reviewer read it. Commit `9facfce` removed them here; the same wording exists in the shared spec, so they are reported upstream. They need a change in `cardigan-shared-specs` and are not edited here.

### T4: "Same email in different forms" cannot fail
- Store and spec: `cardigan-shared-specs`, `api-auth-tokens` > Rate limits count per email > Scenario: Same email in different forms
- Severity: major - a limiter that keys on the raw email passes the scenario as written.
- Current text: "**THEN** all of them count toward the same limit"
- Problem: Three requests stay under the limit of 5, and "count toward the same limit" is internal state. No response differs between a right and a wrong implementation.
- Failure scenario: The limiter keys on the untrimmed, case-sensitive email. All three requests are processed, the test is green, and each spelling gets its own 5 attempts.
- Proposed fix: Replace the scenario with:
  ```
  #### Scenario: Same email in different forms
  - **WHEN** a client sends 2 login requests for `ann@example.com`, 2 for ` Ann@Example.com ` and 1 for `ANN@EXAMPLE.COM` within one second, and then one more for `ann@example.com`
  - **THEN** the sixth response is 429 `RATE_LIMITED`
  ```
  Until the shared spec changes, task 6.1 / 8.3 can test the six-request form anyway.
- Test sketch: unit (limiter) and E2E; send the six logins at one clock instant; passes when responses 1 to 5 are not 429 and the sixth is; fails when the key is the raw string.

### T15: "Another client" is not defined, so the no-IP rule has no failing test
- Store and spec: `cardigan-shared-specs`, `api-auth-tokens` > Rate limits count per email > Scenario: Another client, same email (the reviewer cited it as "Different client, same email" in this change)
- Severity: minor - all test requests come from localhost, so a limiter keyed on IP plus email passes.
- Current text: "**WHEN** one client used up the login limit for `ann@example.com` and another client logs in as `ann@example.com` with the right password"
- Problem: The test cannot change its socket address. Without a definition of "another client", the scenario equals "Correct password over the limit".
- Failure scenario: Someone later adds `X-Forwarded-For` to the key. Every test still passes.
- Proposed fix: Replace the WHEN with "**WHEN** one client used up the login limit for `ann@example.com` and a request with a different `X-Forwarded-For` and `Forwarded` header logs in as `ann@example.com` with the right password".
- Test sketch: E2E; send 5 logins with `X-Forwarded-For: 203.0.113.1`, then one with `203.0.113.2`; passes when the sixth is 429.

## Coverage

- Artifacts reviewed: `proposal.md`, `design.md`, `tasks.md`, `specs/auth-rate-limiting/spec.md`, `specs/auth-sessions/spec.md`, `specs/request-authentication/spec.md`, `specs/user-accounts/spec.md`, with `decisions.md` (21 decisions) as settled context.
- Missing artifacts: none. No earlier `review.md` existed, so there are no run marks.
- Artifacts changed during the review: commit `9facfce` aligned the change with the updated shared specs. Testability, security and hidden assumptions read the earlier version; design contract re-read at HEAD `c64f96f`. The `auth-rate-limiting` delta shrank from 7 requirements to 3 in that commit. The 3 current requirements are covered by findings T5, T6, T10 and T16, but the rewritten "Unparseable body" scenario was not reviewed in its new wording.
- Testability: 29 requirements and 80 scenarios checked in the version read (auth-rate-limiting 7/16, auth-sessions 20/50, request-authentication 1/9, user-accounts 1/5); 14 requirements and 17 scenarios flagged. Not checked: the shared `domain-user` and `text-length` scenarios that register validation relies on, and whether bucket4j's greedy refill gives exactly the values in T5.
- Security: 9 trust boundaries and 7 invariants examined. Not reported because settled: email-only limits and victim lockout (D10), no refresh/logout limit (D12), cookie name without prefix (D16), password shucking (D5), account enumeration through 409 (shared `api-errors`). Could not check: bucket4j and Caffeine behavior for versions not yet chosen, and the reverse proxy config (other repo).
- Hidden assumptions: 9 must-specify findings, 10 safe assumptions. Not applicable: platform (OS, architecture), time zones and daylight saving (all UTC), battery and disk. Not checked: shared spec `api-conventions`. bucket4j behavior with a backward clock and the Postgres NUL rejection were reasoned, not run.
- Design contract: code mode. Checked all artifacts at HEAD, `openspec/config.yaml`, `CLAUDE.md`, 4 main specs, shared specs `api-auth-tokens` and `domain-user` in full and `api-errors`, `text-length`, `api-conventions` in part, the active changes `be-boards` and `be-labels`, about 35 source and build files, and three project skills. 14 findings, 15 abstraction entries. Not checked: bucket4j and Caffeine API details against real versions.
- Reviewers that did not complete: none
- Findings dropped because their evidence did not exist: 1. A1 (major, "the artifacts assume the shared spec still asks for per-IP limits") quoted deviation notes that commit `9facfce` removed. Every part of its proposed fix is already in the current files. Also dropped as parts of kept findings: T3's scenario for "Requests without an email are not limited" and two of T10's three points (see those findings).
- Findings dropped because they challenged decisions.md: 0
- Code facts cited by findings and confirmed by the orchestrator: `RequiredValidator` is `value != null`; `reject(FieldViolation)` is `internal`; `E2eTest` sets the clock to `START` before each test; the two `JpaUserRepositoryTest` duplicate tests expect `ConstraintViolationException` on flush; `UserEntity.email` uses `@Column(unique = true)`; the base exceptions are `open`; `UserTestApplication` is a bare `@SpringBootApplication`; `be-boards` task 1.3 and `design.md` line 100.

## Applied changes

- Files changed: `design.md`, `proposal.md`, `tasks.md`, `specs/auth-sessions/spec.md`, `specs/auth-rate-limiting/spec.md`, `specs/user-accounts/spec.md`. `openspec validate be-auth --strict` passes. No task checkbox was changed.
- Applied: 30 of 31 findings. Rejected: S4 (minor).
- Not applied: the upstream findings T4 and T15 (they need a change in `cardigan-shared-specs`), and the "keep and document" note for `RefreshTokenGenerator` under "New abstractions" (not a finding).
- Stale non-standard artifacts the user chose not to update: none
- Contradictions with decisions.md: none. Two notes for the user, since this review never edits that file: A6's proxy-path constraint is only in `design.md`, not under Facts; and S1 added the config value `cardigan.user.auth.max-concurrent-hashes`, which no decision records.
- Accepted risks: none (no blocker was rejected)
