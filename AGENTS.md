# Cardigan Backend

Cardigan is a Spring Boot backend that serves both mobile and web clients over a REST API. The API contract is an OpenAPI schema that must work for type-safe mobile code generation.

This file is the project's system prompt. The `spring-*` skills in `.claude/skills/` hold the detailed rules and code patterns. This file gives the big picture, the decisions specific to this project, and how to work here. **When this file and a skill disagree, this file wins.** Mention the conflict when you hit it.

---

## Tech Stack

| Area | Choice |
|---|---|
| Runtime | Spring Boot 4, Spring MVC (blocking) on virtual threads. No WebFlux, no coroutines in request handling. |
| Language / build | Kotlin 2.3, JDK 25, Gradle 9, version catalog (`gradle/libs.versions.toml`), convention plugins in `build-logic/` |
| JSON | Jackson 3 (`tools.jackson.*`). Never mix in Jackson 2 artifacts. |
| Persistence | JPA/Hibernate + Spring Data, Flyway migrations, Postgres hosted on Supabase |
| Auth | Spring Security with self-issued JWT access tokens (HS256) + opaque rotating refresh tokens |
| Events | Spring `ApplicationEventPublisher` + Spring Modulith event publication registry (JPA) |
| Rate limiting | bucket4j, in-memory buckets in a Caffeine cache, keyed by email, for login and register only |
| API contract | OpenAPI 3.0.1, generated code-first with springdoc-openapi, committed as a JSON snapshot |
| Tests | JUnit 5, AssertK, hand-written fakes, Testcontainers (Postgres), WireMock, Awaitility |

**Current state:** the modules `:app`, `:common`, `:user` and `:board` exist. Security (JWT access-token verification and issuing), the shared error model, request validation, the wire conventions and springdoc are wired in. `:user` has the `users`, `sessions` and `refresh_tokens` tables, `GET /api/v1/users/me`, register, login, refresh and logout under `/api/v1/auth`, the per-email rate limit on login and register, and the daily cleanup of ended sessions. `:board` has no code. There are no migration files yet. The Modulith event registry is described below but not wired in. It arrives with the first event.

---

## Architecture Template

### Principles

- **Modular monolith.** One deployable app, split into Gradle modules by business capability, never by technical layer.
- **Layers are packages inside each module:** `api`, `service`, `domain`, `infra`.
- **Business modules never depend on each other.** They share only `:common` and react to each other through events.
- **Each module owns its data:** its own Postgres schema (`<feature>_service`) and its own Flyway migrations. No module touches another module's tables.
- **`:app` only assembles.** It holds the application class, cross-cutting config, the security filter chain, `application*.yaml`, and the E2E tests. No business logic.

### Gradle modules

```
cardigan/
├── app/                      :app       – @SpringBootApplication, SecurityConfig, config, E2E tests
├── common/                   :common    – shared kernel (IDs, cross-module events, error contract, JWT decoding)
├── user/                     :user      – users and everything auth-related (schema user_service)
├── board/                    :board     – boards, columns, cards, labels, import/export (schema board_service)
├── build-logic/              convention plugins (cardigan.kotlin.jvm, cardigan.spring.module, ...)
├── gradle/libs.versions.toml every version, library, and plugin
└── settings.gradle.kts
```

`<feature>` below stands for `user` or `board`. Don't create a business module that hasn't been agreed on. Labels and import/export stay inside `:board`, because they change the same data in one transaction.

### Business module (`com.plcoding.cardigan.<feature>`)

```
<feature>/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── kotlin/com/plcoding/cardigan/<feature>/
    │   │   ├── api/
    │   │   │   ├── controller/      <Resource>Controller
    │   │   │   ├── dto/             <Action><Resource>Request, <Resource>Dto, <Name>Dto enums
    │   │   │   ├── mapper/          <Area>DtoMappers.kt (domain ↔ DTO extension functions)
    │   │   │   ├── validation/      custom Bean Validation constraints used only by this module
    │   │   │   └── event/           listeners for other modules' events (<Publisher>EventListener)
    │   │   ├── service/             <Concept>Service (business rules, @Transactional)
    │   │   ├── domain/
    │   │   │   ├── model/           immutable data classes, no Spring/JPA
    │   │   │   ├── exception/       <Thing><Problem>Exception, subclasses of the :common base exceptions
    │   │   │   ├── event/           module-internal events
    │   │   │   └── repository/      <Model>Repository interfaces + other outbound interfaces
    │   │   └── infra/
    │   │       ├── database/
    │   │       │   ├── entity/      <Model>Entity
    │   │       │   ├── repository/  SpringData<Model>Repository, Jpa<Model>Repository (adapter)
    │   │       │   └── mapper/      <Area>EntityMappers.kt
    │   │       ├── http/            third-party HTTP clients (@HttpExchange + adapter)
    │   │       ├── security/        password hashing, refresh token generation (`:user` only)
    │   │       ├── ratelimit/       the bucket4j rate limiter (`:user` only)
    │   │       └── config/          @Configuration, @ConfigurationProperties, the module's PublicRoutes bean
    │   └── resources/db/migration/<feature>/
    │       └── V<yyyy_MM_dd_HHmm>__<description>.sql
    └── test/kotlin/com/plcoding/cardigan/<feature>/
        ├── <Feature>TestApplication.kt   module test app (+ <Feature>TestConfig)
        ├── fake/                         fakes used only by this module
        └── ...                           tests in the package of the class under test
```

### `:common` (`com.plcoding.cardigan.common`)

```
common/src/
├── main/kotlin/com/plcoding/cardigan/common/
│   ├── api/              CommonExceptionHandler, problemDetail(), ProblemResponseWriter, ProblemErrorController, requestUserId
│   │   ├── validation/   @Required, @ValidText, @ValidUuid, @ValidEnum, @ValidRange, RequestValidator
│   │   ├── web/          Jackson config, RequestSizeLimitFilter, PublicRoutes, content negotiation
│   │   ├── openapi/      springdoc customizers, @ApiErrors
│   │   └── security/     authentication entry point, firewall rejection handler
│   ├── domain/
│   │   ├── exception/    ErrorCode, FieldErrorCode, base exceptions (ResourceNotFoundException,
│   │   │                 UniquenessConflictException, UnauthenticatedException, InvalidCredentialsException,
│   │   │                 InvalidRefreshTokenException, RateLimitedException). No ForbiddenException.
│   │   ├── text/         normalizeText, normalizePassword, codePointLength, checkText
│   │   ├── security/     AccessTokenIssuer
│   │   └── event/        cross-module events, one sealed <Publisher>Event per publishing module
│   └── infra/
│       └── security/     JwtProperties, JwtDecoder/JwtEncoder config, token validators, JwtAccessTokenIssuer, BearerTokenResolver
└── testFixtures/         MutableClock, PostgresTestConfig, DatabaseCleaner, application-test.yaml
```

### `:app` (`com.plcoding.cardigan`)

```
app/src/
├── main/
│   ├── kotlin/com/plcoding/cardigan/
│   │   ├── CardiganApplication.kt     @SpringBootApplication + @ConfigurationPropertiesScan
│   │   ├── config/                    ApplicationConfig (Clock, @EnableScheduling; later @EnableAsync)
│   │   └── api/security/              SecurityConfig (filter chain; public routes come from PublicRoutes beans)
│   └── resources/
│       ├── application.yaml           shared defaults, imports .env
│       ├── application-dev.yaml
│       └── application-prod.yaml
└── test/
    ├── kotlin/com/plcoding/cardigan/  E2eTest base class, E2eTestConfig, <Feature>E2eTest, OpenApiSnapshotTest, test-only API
    └── resources/openapi/             openapi.json, the committed OpenAPI snapshot
```

### Layer rules

| Layer | Contains | May depend on |
|---|---|---|
| `domain` | Models, domain exceptions, events, repository interfaces | Kotlin, `java.*`, `common.domain` |
| `service` | Business logic, transactions, event publishing | `domain` |
| `infra` | JPA entities and adapters, HTTP clients, SDK wrappers | `domain` |
| `api` | Controllers, DTOs, exception handlers, event listeners | `service`, `domain` |

- `domain` imports no Spring and no `jakarta.persistence`.
- Services depend on domain interfaces, never on `infra` classes.
- JPA entities never leave `infra`. HTTP types never leave `api`.
- Controllers never inject repositories and never hold business rules.
- Every controller mapping starts with `/api/v1/`. Everything outside `/api/v1` is open to unauthenticated requests, and a test in `:app` fails for any mapping outside it.

### Where does X go?

| I need to add... | Put it in |
|---|---|
| An endpoint | `<feature>/api/controller` under `/api/v1/`, with an explicit `operationId`, one plural `@Tag` and `@ApiErrors` for its extra error statuses. If it's public, add it to the module's own `PublicRoutes` bean, not to `SecurityConfig` |
| A request or response body | `<feature>/api/dto`, mapped in `api/mapper`. Request DTOs follow the rules in "API contract" below |
| A format/length/range check | Bean Validation on the request DTO with the `:common` constraints (`@Required`, `@ValidText`, `@ValidUuid`, `@ValidEnum`, `@ValidRange`) |
| A rule that needs data or state | `<feature>/service` |
| A failure clients must handle | Exception in `<feature>/domain/exception` that subclasses `ResourceNotFoundException`, `UniquenessConflictException`, `UnauthenticatedException` or `RateLimitedException` from `:common`. `InvalidCredentialsException` and `InvalidRefreshTokenException` carry no data and are thrown as they are. `CommonExceptionHandler` maps it; no feature handler. A new top-level code needs a shared `api-errors` change first |
| An OpenAPI rule | `:common` `api/openapi` customizers, never per-feature springdoc config |
| A table, column, or index | Entity in `infra/database/entity`. Dev and tests build their schema from it. Once the baseline migration exists, also a Flyway migration in `<feature>/src/main/resources/db/migration/<feature>` |
| A query | `SpringData<Model>Repository`, exposed through the domain interface and `Jpa<Model>Repository` |
| A call to a third-party API | Domain interface in `domain`, `@HttpExchange` client + adapter in `infra/http/<provider>` |
| Something another module must react to | Event subtype in `common/domain/event/<publisher>`, listener in the consumer's `api/event` |
| A config value | `@ConfigurationProperties` under `cardigan.<feature>.<concern>`, value in `:app` `application.yaml` |
| A scheduled job | `@Scheduled` method on the service that owns the data, cron from config |
| Auth rate limiting | `:user`: `AuthRateLimiter` in `domain`, `Bucket4jAuthRateLimiter` in `infra/ratelimit` |
| A fake for tests | Module `src/test/.../fake`, or `:common` `testFixtures` if several modules need it |
| An E2E test | `:app` `src/test`, extending the `E2eTest` base class |

---

## Project Decisions

### Database: Supabase as plain Postgres

- Supabase is only the hosted Postgres. No Supabase Auth, no Supabase client SDKs, no PostgREST or direct database access from clients, no RLS-based authorization. All access goes through this backend.
- Schema handling depends on the profile. This overrides the Flyway and `validate` rules in the `spring-*` skills.

  | Profile | `ddl-auto` | Flyway |
  |---|---|---|
  | `dev` | `update`: creates missing schemas, tables and columns and keeps data. It never drops anything, so wipe a drifted dev schema by hand. | Off |
  | `prod` | `validate` | On |
  | none | `validate`. It never falls back to dev settings. | On |
  | `test` | `create-drop` | Off |

- **There are no migration files yet. Writing the baseline migration from the entities is required before the first production deploy.** Once entities exist, `prod` won't start without it. Tests move to real migrations with `validate` at the same point.
- One schema per module (`<feature>_service`). Migrations use timestamp versions and are never edited after they are committed.
- No profile is active by default. Set `SPRING_PROFILES_ACTIVE` explicitly: `dev` locally, `prod` in the Docker image.
- The connection comes from `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`, with no defaults. `DB_URL` is a JDBC URL with no credentials in it: `jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require`. Dev uses the Supabase session pooler, whose user is `postgres.<project-ref>`. Pointing `DB_*` at any other Postgres also works. The role must be allowed to create schemas.
- Connection details and secrets come only from environment variables or the git-ignored `.env` file, which `application.yaml` imports. `.env` is a named pipe served by a secret manager. Never commit them.

### Running locally

- `SPRING_PROFILES_ACTIVE=dev ./gradlew :app:bootRun`. `bootRun` runs from the repo root so it finds `.env`. An IDE run configuration must also set its working directory to the repo root and set the profile itself. A `SPRING_PROFILES_ACTIVE` key in `.env` doesn't work.
- Only start from the repo root when you want `.env`. Tests run from `app/`, so they never see it.

### Users

- Emails are stored lowercased, enforced by the `User` domain model and a database check constraint, with a plain unique constraint on `email`. Every response returns the stored form. This deviates from the shared `domain-user` spec until that spec is changed to match (pending).
- `GET /api/v1/users/me` answers 401 `UNAUTHENTICATED` when the token is valid but the user has no record.

### Authentication: self-issued tokens

- **The app issues and validates its own tokens.** There is no external identity provider or authorization server (Auth0, Keycloak, Cognito, Supabase Auth, ...). This keeps complexity low for this project. It doesn't mean an external provider is generally wrong. Switching would be a deliberate decision for the user.
- Access tokens are JWTs signed by our own `JwtEncoder` and checked by our own `JwtDecoder` with the same HMAC secret from `JWT_SECRET` (base64, at least 32 bytes after decoding, required in every environment; startup fails without it). Expiry has no clock skew and is checked against the `Clock` bean. The `spring-boot-starter-security-oauth2-resource-server` dependency is just Spring's JWT validation code, despite its name. Never configure `issuer-uri` or `jwk-set-uri`, and never add OAuth2 login or an authorization server.
- Access tokens are short-lived and carry only `sub`, `type`, `iat`, `exp`. Refresh tokens are opaque random strings, stored as SHA-256 hashes, and rotated on every refresh. Passwords are hashed with BCrypt.
- **Auth scope is only: register, login, refresh, logout.** No email verification, no forgot/reset password, no change password. Ignore those flows in `spring-security` and `spring-integration-testing` (for example, the E2E auth helper goes register → login with no verify step). Don't build them unless asked.
- Every public route under `/api/v1` is listed explicitly, by method and exact path, in the owning module's `PublicRoutes` bean. Everything else under `/api/v1` requires authentication. The bearer token is ignored on public routes and outside `/api/v1`.
- The request user is the token's `sub` (`requestUserId`), with no database lookup.

### Rate limiting: per email, login and register only

- Login and register are rate limited per submitted email, normalized and lowercased, in separate buckets: 5 requests per minute each, refilled evenly. The values are config (`cardigan.user.auth.rate-limit.*`).
- Every request that carries an email counts, whether it succeeds or fails. A request without an email isn't counted; field validation answers it.
- No limit uses the client IP. No client IP is read and no forwarded-header handling is configured. Refresh and logout are not rate limited and never answer 429.
- bucket4j buckets live in memory in a Caffeine cache with a maximum size and expire-after-access. Both read the `Clock` bean.
- Exceeding a limit throws `AuthRateLimitedException`, which maps to `429` with code `RATE_LIMITED` and a `Retry-After` header. The limit is checked after the body was parsed and before field validation, credentials and the uniqueness check.
- Password hashing runs at most `max-concurrent-hashes` at a time. A login or register that waits longer than `hash-wait-timeout` (5s) for its turn throws `PasswordHashingBusyException` and answers the same `429` `RATE_LIMITED`, with `Retry-After` set to that timeout.
- Accepted trade-offs: limits are per instance and reset on restart. Email keys don't stop one client trying many emails, and anyone can use up another email's limit for up to a minute. This is on purpose, to keep things simple. Don't add IP-based or global limits without asking.

### Events: in-process only

- Cross-module communication uses Spring's `ApplicationEventPublisher`, as described in `spring-service-layer`. **No message broker** (RabbitMQ, Kafka, SQS, ...). The reason is complexity, not that a broker is wrong. Introducing one is the user's decision.
- The Spring Modulith event publication registry (JPA) persists events, so an `@Async @TransactionalEventListener` event survives a crash and is replayed on restart. Its table is part of the schema like every other table, so on `prod` it comes from a Flyway migration.
- Because events can be replayed, every listener must be idempotent.

### API contract: OpenAPI, type-safe

The spec is generated code-first by springdoc from controllers and DTOs. Mobile clients generate type-safe code from it, so the spec must be exact:

- All routes live under `/api/v1/<plural-resource>`. This replaces the `/api/...` base path in `spring-api-layer`. The agreed exception is the auth group: `POST /api/v1/auth/register`, `/login`, `/refresh` and `/logout`, with the tag `Auth`, so the refresh cookie has one path.
- The document is OpenAPI 3.0.1 (`springdoc.api-docs.version: openapi_3_0`), for the best code generator support.
- Response DTOs: Kotlin nullability is the contract. A non-null property is `required`, a nullable one is `nullable`.
- Request DTOs deliberately differ: every property is nullable in Kotlin with no default, so missing and `null` fields become `REQUIRED` field errors and all broken fields are reported at once. `@Required` marks the fields the endpoint needs. In the document, only `@Required` properties are `required`, and no request property is `nullable`: optional fields are omitted, never sent as `null`.
- Request DTOs declare UUIDs as `String` with `@ValidUuid`, enums as `String` with `@ValidEnum(XDto::class)`, and bounded integers such as positions as `BigInteger` with a range check. Mappers convert them after validation. The document still shows `format: uuid`, the enum values and `integer`.
- Text fields use `@ValidText`. Mappers call `normalizeText` again when building the domain value; the validator never changes the DTO.
- Endpoints that reference resources in the path or body call `RequestValidator.validate(request)` after the service resolved them, so 404 wins over field errors. `@Valid` is only for endpoints without references. Declare `@RequestBody` before any `@PathVariable`.
- Every request and response body is a concrete, named DTO. No `Any`, `Map<String, Any>`, `JsonNode`, or untyped lists.
- API enums are separate `<Name>Dto` enums with stable constant names.
- IDs are `string` with `format: uuid`, lowercase in responses. Instants are `string` with `format: date-time`, always UTC with milliseconds (`.000Z` for whole seconds).
- Sealed or polymorphic types get an explicit discriminator property and a named schema per subtype.
- Errors are `application/problem+json` with `type` `urn:cardigan:problem:<code in kebab-case>`, a fixed `title` and `detail` per code, `code`, and `fieldErrors`. No `instance`, and never 403 (other users' data is 404). This replaces `about:blank` and `ForbiddenException` in `spring-api-layer`.
- Every operation documents 500, 401 when protected, 400/413/415 when it has a body, and its `@ApiErrors` statuses, each with the `Problem` schema and its codes in the description. `CardiganOpenApiCustomizer` adds these.
- The `Accept` header is ignored. Responses are always JSON or problem+json, never 406.
- `operationId`s (camelCase verb + resource) and tags (one plural PascalCase tag, or `Auth`) are stable. Renaming them breaks generated clients.
- The generated spec is committed as `app/src/test/resources/openapi/openapi.json`, which client repos copy. `OpenApiSnapshotTest` fails when the generated spec differs from it; `./gradlew :app:updateOpenApiSnapshot` rewrites it. Update the snapshot on purpose, never just to make the test pass. No profile serves `/v3/api-docs` or Swagger UI at runtime.
- No breaking change to a shipped endpoint (removing or renaming fields, making optional fields required, changing types or error codes). It needs a new version or the user's explicit approval.

### Supabase MCP

The Supabase MCP server is configured in `.mcp.json` and connected to the **dev branch** (`project_ref=hcplzkrgqjckrztvoext`). A separate production branch is used for deployment.

You may freely use the MCP on the dev branch to:

- query data while developing or debugging,
- seed sample data,
- inspect tables, migrations, logs, and advisors,
- change dev configuration.

Rules:

- Dev schema changes come from the entities through `ddl-auto: update`. Use the MCP to inspect the result, not to change the schema by hand. The one exception is wiping a drifted dev schema.
- The dev branch has no Flyway history table. Production schema changes go only through Flyway migration files in the repo.
- Never use the Supabase database for automated tests. Tests use Testcontainers.
- The production branch is off-limits. Never point `.mcp.json` or any tool call at it. Production only changes through deployments (Flyway on startup).
- Never run branch operations (`create_branch`, `merge_branch`, `rebase_branch`, `reset_branch`, `delete_branch`) unless the user explicitly asks.

---

## Testing

All test types are expected. See `spring-unit-testing` and `spring-integration-testing` for the details.

| Type | Where | What's real |
|---|---|---|
| Unit | module `src/test` | Pure logic, in memory. Fakes for domain interfaces, `MutableClock`. No Spring, no I/O. |
| Persistence | module `src/test`, `@DataJpaTest` | Postgres, entities, adapters, plus Flyway once the baseline migration exists |
| Module integration | module `src/test`, `@SpringBootTest` with the module's test app | Services, transactions, events, Postgres |
| HTTP client | module `src/test` | Our client against WireMock |
| E2E | `:app` `src/test`, `@SpringBootTest(RANDOM_PORT)` + `RestTestClient` | The whole app over HTTP. `E2eTest.signIn` registers and logs in over HTTP. `setUpE2e` clears the rate limiter before every test. `UserFixtures` stays for tests that need a user without going through the API |

- Every test that needs a database uses a **Testcontainers Postgres** with the same major version as Supabase (17). Never the Supabase instance, never H2.
- Until the baseline migration exists, tests use the `test` profile: Hibernate `create-drop` with Flyway off. After that they use real migrations with `validate`, as `spring-integration-testing` describes.
- Fakes, not mocks. No Mockito, no MockK.
- The test profile config lives in `:common` test fixtures (`application-test.yaml`), including a fixed test `JWT_SECRET`.
- Write unit tests the adversarial way: list the break vectors first, then one test per distinct angle.
- Docker must be running for Testcontainers.

Commands:

| Scope | Command |
|---|---|
| One module | `./gradlew :<feature>:test` |
| E2E | `./gradlew :app:test` |
| Everything (compile + all tests) | `./gradlew build` |

---

## Git Workflow

- **Commit automatically** as soon as a logical chunk of work is done **and** `./gradlew build` passes. Don't batch several chunks into one commit, and don't wait until the end of the session.
- **Never commit** when the code doesn't compile, a test fails, or the build hasn't been run. Fix it first or tell the user what is broken.
- Use Conventional Commits with a scope where it fits, as in the existing history: `feat(<feature>): ...`, `fix(<feature>): ...`, `refactor: ...`, `test(<feature>): ...`, `build: ...`, `docs: ...`, `chore(config): ...`. Imperative mood, lowercase, no trailing period.
- One logical change per commit. Unrelated changes go into separate commits.
- Commit locally only. **Never push.** The user pushes.
- Never amend or rewrite existing commits, never skip hooks (`--no-verify`), and never commit `.env` or other secrets.

---

## Review Checklist

- Mappers call `normalizeText` for every text field before it reaches the service.
- Every controller mapping starts with `/api/v1/`, and public routes are in the module's `PublicRoutes` bean.
- Client-facing failures subclass the `:common` base exceptions; no feature exception handler, no 403.
- An API change comes with an updated `openapi.json` snapshot.

---

## Decision Rules

- **Never assume a decision the user hasn't made.** If something isn't specified, ask. Don't silently pick a default.
- **Never make product decisions.** That includes features, endpoints, request and response fields, error codes, validation limits, token lifetimes, rate-limit values, names that clients see, data retention, and anything that changes user-facing behavior.
- Ask before adding a dependency, an external service, a new business module, or new infrastructure (cache, broker, scheduler lock, ...).
- **If you think a different approach would be better than one the user decided on, propose it.** Explain the trade-off briefly, then keep following the existing decision until the user agrees to change it.
- Raise known trade-offs when they become relevant instead of solving them silently. Examples: in-memory rate limits on more than one instance, scheduled jobs needing ShedLock on more than one instance, a breaking API change.
- If a skill's example conflicts with a decision in this file, follow this file and mention the conflict.

---

## Skill Map

| Skill | Use it when |
|---|---|
| `spring-module-structure` | Setting up or migrating the project layout, adding a business module, deciding where a class or package belongs, touching `build-logic`, `settings.gradle.kts`, or `libs.versions.toml`, adding a dependency |
| `spring-api-layer` | Adding or changing an endpoint, controller, request/response DTO, validation, API mapper, pagination, exception handler, or error code |
| `spring-service-layer` | Writing business logic, domain models, domain exceptions, transactions, events and listeners, scheduled jobs, or logging |
| `spring-persistence-jpa` | Creating or changing entities, tables, columns, indexes, repositories, queries, or Flyway migrations; debugging N+1 or lazy loading |
| `spring-security` | Touching register/login/refresh/logout, tokens, the filter chain, public vs protected routes, the current user, password hashing, or anything returning 401/403 |
| `spring-di-configuration` | Creating beans, injecting dependencies, adding config properties, editing `application*.yaml` or profiles, handling secrets, reading the current time |
| `spring-rest-client` | Calling a third-party HTTP API: `@HttpExchange` clients, base URLs, timeouts, auth headers, error translation, retries |
| `spring-unit-testing` | Writing or reviewing unit tests for services, domain models, mappers, validators, or other pure logic |
| `spring-integration-testing` | Writing tests that touch the database, transactions, events, security, JSON, an external API, or a full HTTP flow (incl. E2E) |
