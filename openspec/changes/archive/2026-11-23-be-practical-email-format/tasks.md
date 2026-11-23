# Tasks

## 1. Register email format

- [x] 1.1 Replace `isAddrSpec` with `hasEmailFormat` in `user/api/validation/Validators.kt`: the shared `domain-user` reference pattern verbatim, behind a guard that returns `false` over `EMAIL_MAX_LENGTH` code points, and use it in `ValidEmailValidator`. Verify `./gradlew :user:compileKotlin` succeeds
- [x] 1.2 Update `RegisterRequestValidationTest` with every accepted and rejected email from the shared scenarios, the field error order cases (`jürgen@münchen.de` → `INVALID_FORMAT`, 255 characters without `@` → `TOO_LONG` with `max` 254) and the 250 KB near-match under 100 ms; remove the RFC 5322 cases. Verify `./gradlew :user:test --tests '*RegisterRequestValidationTest'` passes
- [x] 1.3 Add an E2E case to `AuthRegisterLoginE2eTest`: register with `mail@pl-coding` answers 400 `VALIDATION_FAILED` with `email` `INVALID_FORMAT` and stores no user. Verify `./gradlew :app:test --tests '*AuthRegisterLoginE2eTest'` passes

## 2. Login email checks

- [x] 2.1 Swap `@NotBlankText` for `@ValidText(min = 1, max = EMAIL_MAX_LENGTH)` on `LoginRequest.email`, delete `NotBlankText` and its validator, and update the `LoginRequest` KDoc. Verify `LoginRequestValidationTest` covers U+0000 → `INVALID_CHARACTER`, 255 characters → `TOO_LONG` with `max` 254, and no field error for `ann@localhost` and `not an email`, and passes
- [x] 2.2 Remove the `checkText` guard from `AuthService.findUser` and its KDoc sentence. In `AuthServiceRegisterLoginTest`, drop the unstorable-email case and add one where a stored email that breaks the format logs in with the right password. Verify `./gradlew :user:test` passes
- [x] 2.3 Update `AuthRegisterLoginE2eTest`: login with U+0000 in the email answers 400 `INVALID_CHARACTER`, a 255-character email answers 400 `TOO_LONG` with `max` 254, and `ann@localhost` answers 401 `INVALID_CREDENTIALS`. Verify `./gradlew :app:test --tests '*AuthRegisterLoginE2eTest'` passes

## 3. Integration check

- [x] 3.1 Run `./gradlew build` and verify every test passes, including `OpenApiSnapshotTest` and `AuthOpenApiSnapshotTest` with no snapshot change

## Workflow follow-up

- Archive the change only when the user says so.
