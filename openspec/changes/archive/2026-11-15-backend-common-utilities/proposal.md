# Proposal

## Why

The wave-3 backend chunks (`be-auth`, `be-boards`, `be-labels`) should run in parallel. Each one needs authenticated requests, the shared error model, validation, the wire conventions and an OpenAPI spec that clients can generate from. If each chunk built its own version, they would differ and conflict. This change builds them once (breakdown chunk `be-common`). See `decisions.md` for every settled choice.

## What Changes

- Security filter chain: every route under `/api/v1` requires a bearer access token, except auth routes listed explicitly. Paths outside `/api/v1` keep their current behavior. A 401 carries `WWW-Authenticate: Bearer`.
- JWT access-token verification and issuing in `:common`: HS256 with `JWT_SECRET`, zero leeway, and issue times truncated to whole seconds. The service fails to start without a valid secret.
- Current-user principal: controllers read the user ID from the token's `sub`, with no database lookup.
- Users table in `:user` (schema `user_service`): ID, email, password hash and timestamps. Emails are stored lowercased, with a unique constraint.
- `GET /api/v1/users/me` returns the current user's `id`, `email` and `createdAt`. A valid token for a missing user gets 401.
- The `Accept` header is ignored: responses are always JSON or problem+json, never 406.
- The snapshot uses OpenAPI 3.0.1.
- Global error handling: every error is `application/problem+json` per the shared error model, with a URN `type` and a fixed `title` per code. This includes errors raised before a controller is reached (401, 404 route, 413, 415, 400 malformed).
- Request validation: nullable request DTO fields with a `REQUIRED` check, so all broken fields are reported. A text validation annotation enforces `text-length` normalization, characters and limits, and mappers normalize explicitly.
- Wire conventions: a 256 KB body limit, millisecond UTC timestamps, lowercase UUIDs, ignored unknown fields, 404 for malformed path UUIDs, and no CORS headers.
- Timestamps are truncated to milliseconds when they are created. Tests can move the backend time.
- springdoc generates the spec, which is committed as `app/src/test/resources/openapi/openapi.json`. A test fails on drift, and a Gradle task updates the file. No runtime `/v3/api-docs` or Swagger UI.
- Test helpers: user fixtures and authenticated requests as a given user.
- New dependencies: Spring Security, the OAuth2 resource server (JWT code only), Bean Validation, springdoc-openapi 3.x.
- `AGENTS.md` (`CLAUDE.md`) is aligned with the shared specs (rate limiting, error code names, email rule, request DTO nullability, spec snapshot).
- The `service-health` scenario for `GET /api/v1/actuator/health` changes, because unknown `/api/v1` paths now return 401 without a token.

## Shared requirements implemented or depended on

| Spec | Requirement | How |
|---|---|---|
| `api-auth-tokens` | Protected requests use a bearer access token | Implemented |
| `api-auth-tokens` | Invalid access tokens | Implemented |
| `api-auth-tokens` | Access tokens are opaque to clients | Issuer exposes `accessTokenExpiresAt`; flows in `be-auth` |
| `api-auth-tokens` | Token lifetimes | Access-token part implemented; refresh in `be-auth` |
| `api-errors` | Errors use problem details | Implemented |
| `api-errors` | Error text is for developers only | Implemented |
| `api-errors` | Top-level error codes | Catalog and status mapping implemented; feature codes raised by feature chunks |
| `api-errors` | Field errors identify the field and rule | Implemented |
| `api-errors` | Field error codes | Implemented (`PASSWORD_TOO_WEAK` raised by `be-auth`) |
| `api-errors` | One field error per field | Implemented |
| `api-errors` | Limit errors carry the limit | Implemented |
| `api-errors` | Validation errors report all broken fields | Implemented |
| `api-errors` | Error precedence | Implemented for 401, 413, 415, 400, 404 route and references before field validation; rate limit in `be-auth` |
| `api-errors` | Uniqueness clashes are conflicts | Mapping implemented; raised by feature chunks |
| `api-errors` | Other users' data is not found, never forbidden | Mapping implemented; no 403 handler |
| `api-conventions` | Generated OpenAPI is authoritative for wire shape | Implemented (snapshot) |
| `api-conventions` | All endpoints use a versioned base path | Implemented |
| `api-conventions` | JSON with camelCase field names | Implemented |
| `api-conventions` | Timestamps are UTC strings with milliseconds | Implemented |
| `api-conventions` | UUIDs are lowercase canonical strings | Implemented |
| `api-conventions` | Malformed path identifiers are not found | Implemented |
| `api-conventions` | Documented fields are always present | Implemented for `/users/me` |
| `api-conventions` | Null values | Implemented for `/users/me` |
| `api-conventions` | Unknown fields are ignored | Implemented |
| `api-conventions` | Null and empty updates | `REQUIRED` for null implemented; PATCH endpoints in feature chunks |
| `api-conventions` | Request body size limit | Implemented |
| `api-conventions` | Same-origin only | Implemented |
| `api-conventions` | Backend time can be set in tests | Implemented |
| `text-length` | Text is normalized before validation | Implemented (annotation + mapper) |
| `text-length` | Passwords are normalized without trimming | Helper implemented; used by `be-auth` |
| `text-length` | Length is counted in Unicode code points | Implemented |
| `text-length` | Invalid characters are rejected | Implemented |
| `text-length` | Backend rejects text outside its length limits | Implemented |
| `text-length` | Backend stores and returns normalized text | Mapper rule for all chunks |
| `domain-user` | Users have a backend-assigned identifier | Implemented |
| `domain-user` | Email identifies exactly one user | Unique constraint on the lowercased email. **Deviates**: emails are stored lowercased, not as entered (D1). Depends on a pending shared-spec change. |

**Dependency on a shared-spec change:** D1 stores emails lowercased, which contradicts `domain-user` ("Email identifies exactly one user", scenario "Email is kept as entered"). The shared spec is changed to match in a separate change in the shared repo, arranged by the coordinating session. This change can be implemented before that, but must not be archived until `domain-user` has been updated. Otherwise the backend and the shared spec disagree.

## Capabilities

### New Capabilities

- `request-authentication`: how the backend verifies and issues access tokens, which routes are public, how the signing secret is configured, and how the current user is identified.
- `error-responses`: backend-specific shape of error bodies (URN `type`, fixed titles, no `instance`) and that every error, including ones raised before a controller, uses it.
- `api-contract-snapshot`: the committed OpenAPI JSON snapshot, its drift check and update task, naming of operations and tags, schema rules for request fields and errors, and no runtime API docs.
- `user-accounts`: the stored user record, its lowercase email invariant, and the current-user endpoint.
- `backend-time`: millisecond precision of backend timestamps and the test-settable clock.

### Modified Capabilities

- `service-health`: "Health is not part of the client API" — the `/api/v1/actuator/health` scenario returns 401 without a token and 404 with one.
- `module-boundaries`: "Module layer packages exist from the start" — `:common` and `:user` now hold production code, so the "no source files" check applies only to `:board`.

## Impact

- **Code**: `:common` (security, JWT, error handling, validation, wire conventions, OpenAPI customizers, test fixtures), `:user` (user entity, repository, `/users/me`), `:app` (`SecurityConfig`, `ApplicationConfig`, snapshot test, E2E tests, config).
- **API**: first endpoint `GET /api/v1/users/me`, the error schema, and `openapi.json` for clients.
- **Dependencies**: four new libraries in `gradle/libs.versions.toml` (D19).
- **Configuration**: `JWT_SECRET` env var, required in every environment, including CI tests (a fixed test value) and the Docker image at runtime.
- **Project rules**: `AGENTS.md` (`CLAUDE.md`).
- **Other chunks**: `be-auth` uses the issuer, the users table and the error model. `mob-api-client` and `web-api-client` copy `openapi.json`.
