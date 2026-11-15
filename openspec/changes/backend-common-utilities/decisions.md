# Decisions: backend-common-utilities

Confirmed with Philipp Lackner on 2026-11-15, through the `cardiganapp-be` planning session, which relayed the questions and answers.

## Summary

This change builds the backend foundation every feature chunk needs (breakdown chunk `be-common`). It adds the security filter chain with JWT access-token verification, the access-token issuer, the current-user principal, the users table in `:user`, and the global error handling for the shared error model. It also adds request validation with `text-length` normalization, the cross-cutting wire conventions, and a committed OpenAPI JSON snapshot that clients copy. `GET /api/v1/users/me` proves the pipeline. Register, login, refresh, logout, rate limiting and the email and password rules stay in `be-auth`.

## Decisions

### D1 — Email storage and uniqueness
- **Question**: How should the users table enforce case-insensitive email uniqueness?
- **Decision**: Emails are stored lowercased, with a plain unique constraint on `email`. There's no lookup column and no functional index. Every response that carries the email, including `/users/me` and login, returns the lowercased form. Lowercasing on register belongs to `be-auth`, but this change writes the lowercase invariant and its tests into the spec, and mixed-case duplicates must fail. This contradicts shared `domain-user` ("Email identifies exactly one user": stored as entered, not case-folded). The user confirmed the choice after the conflict was raised, and the shared spec is changed to match in a separate change in the shared repo.
- **Alternatives**: Store the email as entered, plus a lowercased `email_lookup` column with a unique constraint; store it as entered with a functional unique index on `lower(email)`.
- **Why**: The user doesn't want a separate column, and one stored form is simpler.
- **Depends on**: —

### D2 — Email and password rules
- **Question**: Where do the email (RFC 5322, ASCII) and password composition validators live?
- **Decision**: In `be-auth`, next to register, their only user.
- **Alternatives**: In `be-common`, with the users table.
- **Why**: Keeps this change smaller.
- **Depends on**: —

### D3 — User existence per request
- **Question**: Should each authenticated request check that the user still exists?
- **Decision**: No. The token's `sub` is trusted, with no database lookup.
- **Alternatives**: Look up the user on every request.
- **Why**: No account deletion exists in v1, and it saves a query per request.
- **Depends on**: —

### D4 — Signing secret
- **Question**: How is the HMAC signing secret configured?
- **Decision**: One env var `JWT_SECRET`, base64-encoded, at least 256 bits after decoding. Startup fails when it's missing, not valid base64, or too short. One key, no key IDs.
- **Alternatives**: Several keys with `kid` for smooth rotation.
- **Why**: Simple. Rotating the secret invalidates all access tokens, which is acceptable at a 15-minute lifetime.
- **Depends on**: —

### D5 — Access-token issuer
- **Question**: Who builds the access-token issuer?
- **Decision**: This change, in `:common`, next to verification. Tests mint real tokens for fixture users. `be-auth` only adds the flows.
- **Alternatives**: `be-auth` as in the breakdown, with a test-only encoder here.
- **Why**: Issuing and verifying share one claim format, and tests work before register exists.
- **Depends on**: D4

### D6 — Expiry precision
- **Question**: How do the JWT `exp` (whole seconds) and `accessTokenExpiresAt` (milliseconds) stay equal?
- **Decision**: The issuer truncates the issue time to whole seconds, so `accessTokenExpiresAt` always ends in `.000` and equals `exp`.
- **Alternatives**: A custom claim with the exact millisecond expiry.
- **Why**: Exact with a tiny change, and keeps the standard claims.
- **Depends on**: D5

### D7 — Timestamp precision
- **Question**: When are timestamps cut to milliseconds?
- **Decision**: When they are created, through a `Clock` that ticks in milliseconds. Stored and sent values are equal.
- **Alternatives**: Store microseconds and truncate only in JSON.
- **Why**: Sorting in the database matches what clients see (`api-conventions`, "Board list order").
- **Depends on**: —

### D8 — Problem `type` and `title`
- **Question**: What goes in the problem-details `type` field?
- **Decision**: A URN per code, `urn:cardigan:problem:<code in kebab-case>` (e.g. `urn:cardigan:problem:not-found`), a fixed English `title` per code, and no `instance` field.
- **Alternatives**: `about:blank` with the status phrase as title; an https URL per code.
- **Why**: Distinct per code without hosting a page.
- **Depends on**: —

### D9 — `WWW-Authenticate` on 401
- **Question**: Should 401 `UNAUTHENTICATED` responses carry a `WWW-Authenticate` header?
- **Decision**: Yes, plain `Bearer` with no error parameters.
- **Alternatives**: No header; Spring's default with `error="invalid_token"` and a description.
- **Why**: Standard per RFC 6750 and reveals nothing.
- **Depends on**: —

### D10 — Cross-cutting conventions in scope
- **Question**: Which cross-cutting `api-conventions` rules does this change implement?
- **Decision**: All of these: the 256 KB body limit returning 413 (counted on the bytes received, including chunked bodies), unknown routes and wrong methods returning 404 `ROUTE_NOT_FOUND` (401 without a token), malformed path UUIDs returning 404 `NOT_FOUND`, timestamps as `yyyy-MM-ddTHH:mm:ss.sssZ`, lowercase UUIDs, ignored unknown request fields, and no CORS headers.
- **Alternatives**: Leave some of them to the feature chunks.
- **Why**: They apply to every endpoint, and the parallel wave-3 chunks shouldn't each solve them.
- **Depends on**: —

### D11 — Missing and null request fields
- **Question**: How do missing or null fields become `VALIDATION_FAILED` / `REQUIRED` for every broken field at once?
- **Decision**: Request DTO properties are nullable in Kotlin and get a `REQUIRED` check. An OpenAPI customizer marks them as required and non-null in the generated schema. This deviates from the `CLAUDE.md` wording "Kotlin nullability is the contract" for request DTOs, and `CLAUDE.md` is updated to say so (D20).
- **Alternatives**: Non-null properties, mapping Jackson's missing-field error to `REQUIRED`. That reports only the first missing field and breaks `api-errors` ("Validation errors report all broken fields").
- **Why**: It's the only option that meets the shared spec.
- **Depends on**: —

### D12 — Text normalization
- **Question**: Where is text normalized?
- **Decision**: A validation annotation checks the normalized value, and the API mapper normalizes explicitly before the value reaches the service.
- **Alternatives**: A global Jackson string deserializer with an opt-out for passwords; a `NormalizedText` wrapper type.
- **Why**: Explicit, and easy to keep away from passwords and tokens.
- **Depends on**: D11

### D13 — OpenAPI hand-off
- **Question**: How do the mobile and web repos get the spec?
- **Decision**: A committed snapshot under `app/src/test/resources/openapi/`. A test fails when the generated spec differs from it, and a Gradle task rewrites it. Clients copy the file at a pinned commit.
- **Alternatives**: A CI artifact; a copy in the shared specs repo.
- **Why**: Contract changes show up in reviewable diffs, with no extra infrastructure.
- **Depends on**: —

### D14 — Snapshot format
- **Question**: Which file format does the snapshot use?
- **Decision**: JSON, as `openapi.json`.
- **Alternatives**: YAML.
- **Why**: The mobile repo already expects `api/openapi.json`.
- **Depends on**: D13

### D15 — Runtime API docs
- **Question**: Are `/v3/api-docs` and Swagger UI served at runtime?
- **Decision**: Never, in no profile. The snapshot is generated another way, for example from the test context.
- **Alternatives**: Dev profile only; all profiles.
- **Why**: The snapshot file is enough, and nothing extra is exposed.
- **Depends on**: D13

### D16 — Breaking-change check
- **Question**: Should CI check for breaking API changes automatically?
- **Decision**: Not yet. Snapshot diffs are reviewed by hand.
- **Alternatives**: Add an OpenAPI diff tool to CI now.
- **Why**: Nothing is released before `infra-vps`.
- **Depends on**: D13

### D17 — operationIds and tags
- **Question**: How are operationIds and tags named?
- **Decision**: Explicit camelCase verb plus resource (`getCurrentUser`, `listBoards`) on every operation, and one plural tag per resource (`Users`, `Boards`).
- **Alternatives**: springdoc defaults from method names.
- **Why**: Stable, readable names in generated client code.
- **Depends on**: —

### D18 — Sample protected endpoint
- **Question**: Which protected endpoint proves the pipeline?
- **Decision**: A real `GET /api/v1/users/me` returning `id`, `email` and `createdAt`.
- **Alternatives**: The same with `updatedAt`; a test-only endpoint.
- **Why**: Clients can use it to check a session.
- **Depends on**: D1

### D19 — New dependencies
- **Question**: Which dependencies may this change add?
- **Decision**: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` (only Spring's JWT code; no identity provider), `spring-boot-starter-validation`, and springdoc-openapi 3.x (the Boot 4 line).
- **Alternatives**: Hand-written JWT verification on Nimbus instead of the resource-server starter.
- **Why**: Standard Spring building blocks.
- **Depends on**: —

### D20 — Project rules
- **Question**: Should this change align the backend `CLAUDE.md` with the shared specs?
- **Decision**: Yes. Rate limits per client IP plus per email for login, code `RATE_LIMITED`, email uniqueness per D1, the request DTO nullability deviation (D11), the JSON snapshot without runtime docs (D13–D15), and the current state. Changing `AGENTS.md` updates `CLAUDE.md`, which is a symlink to it.
- **Alternatives**: Leave it to `be-auth`.
- **Why**: `be-auth` and the other wave-3 chunks start from correct rules.
- **Depends on**: D1, D11, D13, D14, D15

### D21 — Current user without a user row
- **Question**: What does `GET /api/v1/users/me` return for a valid access token whose user has no row? In v1 this only happens after a dev database wipe.
- **Decision**: 401 `UNAUTHENTICATED`.
- **Alternatives**: 404 `NOT_FOUND`.
- **Why**: The client refreshes, the refresh fails because the sessions are gone too, and the user lands on login. Clients need no special case.
- **Depends on**: D3, D18

### D22 — OpenAPI version
- **Question**: Which OpenAPI version does the snapshot use?
- **Decision**: OpenAPI 3.0.1.
- **Alternatives**: OpenAPI 3.1, springdoc 3's default.
- **Why**: Best support in client code generators.
- **Depends on**: D13

### D23 — Unsupported `Accept` headers
- **Question**: What happens when a client sends an `Accept` header the API can't serve? The shared catalog has no 406 code.
- **Decision**: The `Accept` header is ignored. Responses are always JSON, and errors are always `application/problem+json`. No 406.
- **Alternatives**: 406 with a new code such as `NOT_ACCEPTABLE` added to `api-errors`.
- **Why**: No new error code and no surprises for clients.
- **Depends on**: —

## Settled by existing rules

Recorded here, not asked again.

- HS256 with the backend's own secret. Access tokens carry only `sub`, `type`, `iat` and `exp` — source: `CLAUDE.md`, "Authentication: self-issued tokens".
- RFC 9457 problem details with `code` and `fieldErrors`, and the closed code catalog — source: shared `api-errors`.
- Migrations live in one folder per module with versions `V<yyyy_MM_dd_HHmm>`, and none exist until the baseline. This answers the breakdown's open question on migration numbering for parallel chunks — source: `CLAUDE.md`, "Database".
- A `Clock` bean, with `MutableClock` in `:common` test fixtures, lets tests move the backend time — source: shared `api-conventions`, "Backend time can be set in tests"; `CLAUDE.md`.

## Facts

- Emails are ASCII-only after normalization, so Unicode default case folding equals ASCII lowercasing for them. No ICU dependency is needed — source: shared `domain-user`, "Email follows the RFC 5322 address format".
- Spring's `JwtTimestampValidator` allows 60 seconds of clock skew by default and accepts a token exactly at `exp`. The spec needs zero leeway and rejection at `exp`, so a custom validator is needed — source: shared `api-auth-tokens`, "Token lifetimes"; Spring Security defaults.
- A path under `/api/v1/` that no endpoint serves returns 401 without a token. The existing `service-health` scenario expects 404 for `GET /api/v1/actuator/health` without credentials, so that scenario must change — source: shared `api-conventions`, "All endpoints use a versioned base path"; `openspec/specs/service-health/spec.md`.
- Paths outside `/api/v1` other than `/actuator/health`, such as `/actuator/env`, must keep returning 404 without credentials — source: `service-health`, "Only health is exposed operationally".
- Java's `\p{IsWhite_Space}` matches the Unicode White_Space property, while `Character.isWhitespace` doesn't (it misses U+00A0 and includes U+001C–U+001F) — source: JDK `Pattern` and `Character` docs.
- The build has no security, validation or springdoc dependency, and `:common` has only test fixtures with `PostgresTestConfig` — source: `gradle/libs.versions.toml`, `common/build.gradle.kts`.
- The `spring-api-layer` skill uses `about:blank` and a `ForbiddenException`. Both conflict with D8 and with `api-errors` ("Other users' data is not found, never forbidden") — source: `.claude/skills/spring-api-layer/SKILL.md`.
- The breakdown chunk `05-be-common.md` still lists signing and migration numbering as open. The shared repo isn't changed here; the coordinating session handles it — source: `docs/breakdown/chunks/05-be-common.md` in `cardigan-shared-specs`.

## Out of scope

- Register, login, refresh, logout, refresh tokens, cookies, password hashing (`be-auth`).
- Email and password composition validators (D2).
- Rate limiting (`be-auth`).
- Board, column, card and label endpoints.
- Runtime API docs and Swagger UI (D15), and an automated breaking-change check (D16).
- The baseline Flyway migration (before the first production deploy).
- Changes to the shared specs repo, including the `domain-user` change for D1, which the coordinating session arranges.
