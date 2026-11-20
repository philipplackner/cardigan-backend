# Design

## Context

`:board` has no code yet. `:common` provides `requestUserId`, `@Required`, `@ValidText`, `RequestValidator`, `ResourceNotFoundException`, `UniquenessConflictException`, `@ApiErrors`, `normalizeText`, `codePointLength`, the millisecond `Clock`, and the handling of malformed path UUIDs (a `@PathVariable` of type `UUID` that can't be converted becomes 404). That conversion uses `UUID.fromString`, which also accepts short forms such as `1-2-3-4-5`; this change closes that (see "Changes to `:common`"). `:user` is the reference for the layering.

Three things this change needs are missing in `:common`:

- A service can't raise a field error. `RequestValidationException` lives in `common.api`, which services may not depend on, and `@ValidRange` only takes fixed limits.
- `ConstraintValidatorContext.reject(FieldViolation)` is `internal`, so a constraint written in `:board` can't report a field error code.
- Nothing tells a JSON `null` from an absent field in a PATCH request.

`be-labels` is planned in parallel in the same module (`openspec/changes/be-labels`). Its design places `@NotNullWhenPresent` in `:common` `api/validation` as well (D21, `be-labels` D8), so whichever change is implemented first adds it.

Constraints: no Flyway migration yet (dev uses `ddl-auto: update`, tests `create-drop`), no dependency between `:board` and `:user`, fakes instead of mocks, and every decision in `decisions.md`.

## Goals / Non-Goals

**Goals:**
- Board and column CRUD in `:board` following the existing layer rules.
- Dense column positions that hold under concurrent requests, with one simple rule: every write to a board or its columns runs under that board's row lock.
- Pieces `be-cards` can reuse as they are: the board lock, the dense-position helper, the out-of-range exception and the cascade pattern.

**Non-Goals:**
- Cards, `cardCount`, labels, import/export.
- A Flyway migration, events, rate limiting.
- A generic ordering framework. The helper covers what columns need and what cards will need in the same shape, nothing more.

## Decisions

### Package layout in `:board`

```
board/
├── api/controller/    BoardController, ColumnController
├── api/dto/           BoardDto, BoardSummaryDto, BoardListDto, ColumnDto, ColumnListDto,
│                      CreateBoardRequest, UpdateBoardRequest,
│                      CreateColumnRequest, UpdateColumnRequest, MoveColumnRequest
├── api/mapper/        BoardDtoMappers.kt, ColumnDtoMappers.kt
├── api/validation/    @ValidBoardKey
├── service/           BoardService, ColumnService
├── domain/model/      Board, BoardKey, Column, BoardWithColumns, DensePositions.kt
├── domain/exception/  BoardNotFoundException, ColumnNotFoundException, BoardKeyAlreadyUsedException
├── domain/repository/ BoardRepository, ColumnRepository
└── infra/database/    entity/BoardEntity, ColumnEntity
                       repository/SpringDataBoardRepository + JpaBoardRepository,
                                  SpringDataColumnRepository + JpaColumnRepository
                       mapper/BoardEntityMappers.kt
```

The `.gitkeep` files in packages that now hold code are removed. `BoardDto` is the full board with `columns`; `BoardSummaryDto` is the list item (D16).

### Changes to `:common`

1. **`FieldOutOfRangeException(field, min, max)`** in `common.domain.exception`, next to the other base exceptions. `CommonExceptionHandler` maps it to 400 `VALIDATION_FAILED` with one field error `OUT_OF_RANGE` carrying `min` and `max` (D20). No new error code. `ColumnService` throws it directly; no subclass is needed because the field name says what failed.
2. **`@NotNullWhenPresent`** with its "sent properties" base type in `common.api.validation` (D21). If `be-labels` was implemented first it already exists and is reused unchanged. If not, this change adds it as `be-labels`' design describes it: the request records which properties were present in the JSON, and the class-level constraint reports `REQUIRED` at the path of a property sent as `null`. The base type is the abstract class `PatchRequest` in `common.api.validation`, exactly as `be-labels`' design defines it under its PATCH section: a private set of the property names sent as JSON `null`, `protected fun <T> sent(name: String, value: T?): T?`, `fun nullProperties(): Set<String>`, and no public property, so nothing from it appears in the JSON or in the OpenAPI schema. `UpdateBoardRequest` and `UpdateColumnRequest` are plain classes that extend it and declare `var name: String? = null` with `set(value) { field = sent("name", value) }`; `@NotNullWhenPresent` sits on `PatchRequest`. A `CardiganOpenApiCustomizerTest` case asserts that a `PatchRequest` subclass documents exactly its own properties, none required and none nullable.
3. **`reject(FieldViolation)` becomes public.** `@ValidBoardKey` lives in `:board` `api/validation` (the project layout puts module-specific constraints there) and needs it to report its code with `min`/`max`.
4. **Canonical path UUIDs.** A `Converter<String, UUID>` registered in `WebConfig` that uses `parseCanonicalUuid` and throws `IllegalArgumentException` for anything else, so short forms end as 404 like `abc`. Uppercase IDs stay valid.

*Alternative for 3:* put `@ValidBoardKey` in `:common`. Rejected: the key rule belongs to boards, and `:common` would learn a board concept.

### Board key: one rule in the domain, used by the constraint and the mapper

`BoardKey.kt` in `domain/model` holds two pure functions:

- `normalizeBoardKey(raw)`: `normalizeText`, then ASCII `a`–`z` to uppercase. Other letters are left alone, so `ä` stays `ä` and fails the format check instead of becoming `Ä`.
- `checkBoardKey(normalized)`: returns the first broken rule in the order of D11: empty → `REQUIRED`; fewer than 2 code points → `TOO_SHORT`; more than 5 → `TOO_LONG`; any character outside `A`–`Z` → `INVALID_FORMAT`. It never returns `INVALID_CHARACTER`.

`@ValidBoardKey` calls both and reports the code (with `min` 2 or `max` 5). The request field is `@Required @ValidBoardKey val key: String?`. The mapper calls `normalizeBoardKey` again to build the domain value, like `normalizeText` for names. `be-import-export` reuses the two functions for the file's key.

`@ValidText` is not used for the key, because its precedence would report a control character as `INVALID_CHARACTER`.

### Data model

Table `board_service.boards`:

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | primary key, assigned in the service |
| `owner_id` | `uuid` | not null. The user's ID, no foreign key (D12) |
| `name` | `varchar(100)` | not null, normalized |
| `key` | `varchar(5)` | not null, uppercase |
| `created_at` | `timestamptz` | not null |
| `updated_at` | `timestamptz` | not null |

Unique constraint `boards_owner_key_unique` on (`owner_id`, `key`). Its index starts with `owner_id`, so it is also the index D12 asks for and serves the list query.

Table `board_service.columns`:

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | primary key, assigned in the service |
| `board_id` | `uuid` | not null, foreign key to `boards.id` with `ON DELETE CASCADE` (D19) |
| `name` | `varchar(30)` | not null, normalized |
| `position` | `integer` | not null, check `position >= 0` (D18) |
| `created_at` | `timestamptz` | not null |
| `updated_at` | `timestamptz` | not null |

Index `columns_board_position_idx` on (`board_id`, `position`), not unique (D18).

`ColumnEntity` maps `board_id` as a lazy `@ManyToOne` to `BoardEntity` with `@OnDelete(action = OnDeleteAction.CASCADE)`, so Hibernate generates the cascading foreign key for dev and tests. The adapter sets it with `getReferenceById` and never navigates it. `BoardEntity` has no collection of columns: columns are loaded by query, which keeps board list queries free of joins and N+1.

`columns` is a non-reserved word in Postgres and works unquoted as a table name.

Postgres counts `varchar(n)` in characters, which matches code points.

### Every write runs under the board's row lock

`BoardRepository.lockByIdAndOwner(boardId, ownerId)` loads the board with `SELECT … FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)` on the Spring Data query) or returns null. It does both jobs at once: it checks ownership, and it serializes writers.

D13 asks for the lock on column create, move and delete. The design uses the same locked load for column rename, board rename and board delete too. They all write the board row anyway (`updatedAt`, or the delete itself), so locking adds no statement, and it removes two races: a rename that overwrites a newer `updatedAt`, and a column insert that races a board delete. Reads (`GET`) take no lock.

The lock wait is bounded. Before the locked select, the adapter runs `SET LOCAL lock_timeout` with the value of `cardigan.board.lock-timeout` (a `Duration` in `@ConfigurationProperties`, set to `5s` in `:app` `application.yaml`). `SET LOCAL` lasts for the transaction only, so it works through the session pooler. The JPA hint `jakarta.persistence.lock.timeout` is not used, because Postgres has no `FOR UPDATE WAIT n` and the hint is only reliable for `NOWAIT`. A request that doesn't get the lock in that time fails with Postgres error `55P03`, rolls back, gives its connection back and ends as 500 `INTERNAL_ERROR`; no new error code. A normal write holds the lock for a few milliseconds, so 5 seconds is only reached when a board is under abnormal load, and it stays well below Hikari's 30 second connection timeout.

Each service method is one `@Transactional` method, and the lock is its first statement, so the range check and the shifts see the board as the previous writer left it (default `READ COMMITTED`: after the lock is granted, later statements see the committed state).

*Alternative:* lock only where D13 names it and write `updatedAt` with a targeted `UPDATE` elsewhere. Rejected: two code paths for "touch the board", and the delete race stays.

### Position changes

`DensePositions.kt` in `domain/model` holds the pure rules, written for "items in a container" so `be-cards` can use them for cards:

- `requireInRange(field, position: BigInteger, count)`: throws `FieldOutOfRangeException(field, 0, count - 1)` unless `0 <= position <= count - 1`. The position stays a `BigInteger` until this check, so a value beyond `Int` is rejected with the right `min`/`max` instead of overflowing.
- `shiftForMove(from, to)`: the closed range of positions that shifts and the direction (−1 when moving right, +1 when moving left).

`ColumnService` under the lock:

- **Create**: `position = countByBoardId`, insert, touch the board.
- **Move**: load the column in the board (404), count the columns, `requireInRange`. If `to == from`, return the current columns with no write (D8). Otherwise one bulk `UPDATE columns SET position = position ± 1 WHERE board_id = ? AND position BETWEEN ? AND ?`, then set the moved column's `position` and `updatedAt`, touch the board, and return all columns.
- **Delete**: delete the column, one bulk `UPDATE … SET position = position - 1 WHERE board_id = ? AND position > ?`, touch the board.

The bulk updates are JPQL `@Modifying(flushAutomatically = true, clearAutomatically = true)` queries. They don't set `updated_at`, which is exactly what `domain-column` wants for shifted columns. The moved column is written after the bulk update, from a freshly loaded entity, so the cleared persistence context can't lose its change.

Each write method reads `clock.instant()` once, right after the board lock is granted (for board create: at the start), and uses that one value for every `createdAt` and `updatedAt` it sets. "Touch the board" means setting the locked board's `updatedAt` to that value and saving it.

### Board operations

- **Create** (`BoardService.create(ownerId, name, key)`): check `existsByOwnerIdAndKey`, throw `BoardKeyAlreadyUsedException` (a `UniquenessConflictException` for the field `key`); save the board and three columns named `To do`, `In progress`, `Done` at 0, 1, 2 with the board's creation time (D5, D6). The names are constants in `BoardService`. Two racing creates can both pass the check, so `JpaBoardRepository.save` uses `saveAndFlush` and translates a violation of `boards_owner_key_unique` into the same exception. Any other integrity violation is rethrown and ends as a 500. This is the same pattern as label names in `be-labels`.
- **List**: `findAllByOwnerIdOrderByUpdatedAtDescCreatedAtDescIdAsc`. Postgres compares `uuid` values bytewise, which is the same order as their lowercase strings. Java's `UUID.compareTo` is not (it compares signed longs), so the fake repository in unit tests sorts by `id.toString()`.
- **Get**: load the board by ID and owner, then its columns ordered by position, returned as `BoardWithColumns`. The method is `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`, so both statements read one snapshot and a board deleted in between is never returned with an empty column list.
- **Rename**: lock, compare the normalized name with the stored one, return without writing when equal (D8), otherwise set `name` and `updatedAt`. Returns `BoardWithColumns` (D14).
- **Delete**: lock (404 when missing), delete the board row. The database removes the columns (D19).

### Request flow in the controllers

- Endpoints without references (`createBoard`) use `@Valid @RequestBody`.
- Endpoints with path references declare `@RequestBody` before the `@PathVariable`s, resolve the references first through the service (`BoardService.get` / `ColumnService.get`, which throw the 404 exceptions), then call `RequestValidator.validate(request)`, then the mutating service method. The mutating method resolves again under the lock, so a delete between the two steps still ends in 404.
- `moveColumn` passes the `BigInteger` position to `ColumnService.move`. `REQUIRED` comes from `@Required` on the DTO; the range comes from the service (D20). `@ValidRange` isn't used.
- Every repository query that starts from a request takes the owner ID (`findByIdAndOwnerId`, `lockByIdAndOwner`), and column queries take the board ID (`findByIdAndBoardId`), so foreign data and a column under the wrong board look exactly like missing data.
- `UpdateBoardRequest` and `UpdateColumnRequest` have the one optional property `name` with `@ValidText(min = 1, max = …)` and use `@NotNullWhenPresent` (D21). The services take `name: String?`, where null means "not sent".

### OpenAPI

`BoardController` carries `@Tag(name = "Boards")`, `ColumnController` `@Tag(name = "Columns")`, both with explicit `operationId`s (D17). `@ApiErrors(400, VALIDATION_FAILED)` on the five operations with a request body (`createBoard`, `updateBoard`, `createColumn`, `updateColumn`, `moveColumn`), because the customizer adds only `MALFORMED_REQUEST` for bodies; `@ApiErrors(409, CONFLICT)` on `createBoard`; `@ApiErrors(404, NOT_FOUND)` on every operation with a path ID. `MoveColumnRequest.position` is a `BigInteger`, which the document shows as `integer`. The snapshot is rewritten with `./gradlew :app:updateOpenApiSnapshot` once, on purpose.

### Tests

- Unit (`:board`): `normalizeBoardKey`/`checkBoardKey`, `DensePositions`, the `Board` and `Column` invariants, the mappers, and `BoardService` and `ColumnService` against fake repositories and `MutableClock`. The fake `BoardRepository` treats `lockByIdAndOwner` as a plain lookup.
- Unit (`:common`): the handler mapping of `FieldOutOfRangeException`, the canonical path UUID converter, and `@NotNullWhenPresent` with `PatchRequest` if this change adds them.
- Persistence (`:board`, `@DataJpaTest` with `BoardTestApplication` and `PostgresTestConfig`): round trips, owner- and board-scoped queries, list order including the ID tie-break, the unique key constraint and its translation, the position check constraint, the cascade through a native delete, and the two bulk shift queries.
- Module integration (`:board`, `@SpringBootTest`): concurrent creates of the same key, concurrent column creates, and a concurrent move and delete, each ending with dense positions; a write that waits for a held lock on the same board, one that doesn't wait for another board's lock, and one that fails at the lock timeout; and a board create whose third column save fails, leaving nothing stored.
- Module integration tests use `@SpringBootTest(webEnvironment = NONE)` with `@ActiveProfiles("test")` and import `PostgresTestConfig` and `BoardTestConfig`. `BoardTestConfig` (`board/src/test`, next to `BoardTestApplication`) is a `@TestConfiguration` that provides a `MutableClock` as the `Clock`, a `DatabaseCleaner`, and imports `RequestValidator`, because `BoardTestApplication` scans only the `:board` package. The tests set `cardigan.board.lock-timeout` themselves (a short value for the timeout test). Each test cleans `board_service` in `@BeforeEach`. `be-labels` reuses it.
- E2E (`:app`, `BoardE2eTest`, `ColumnE2eTest`): every scenario of the two specs over HTTP, except the API-document, direct-database and concurrency ones. The API-document scenarios are cases in `OpenApiSnapshotTest`. `E2eTest` also cleans `board_service`.

## Risks / Trade-offs

- [No unique constraint on (`board_id`, `position`), so a bug in the service could store a gap or a duplicate] → Accepted (D18). The lock plus one code path for shifts keep it small, and the concurrency tests assert density after every scenario.
- [A request waiting for the board lock holds a pool connection, and a move or delete shifts up to n rows with no cap on n (D9). Enough queued writes on one large board can use up the pool and delay every user] → The lock wait is bounded by `cardigan.board.lock-timeout` (5 seconds), so queued requests give their connection back. A single account can still slow the API with steady load; a per-user limit or a column cap would need its own decision.
- [Rename and delete also take the lock, which D13 doesn't list] → No observable difference except correct timestamps under races. Called out above so the choice is visible.
- [`be-labels` works in the same module at the same time] → Board and column code is in its own classes. The shared touch points are `board/build.gradle.kts`, `BoardTestApplication`, `BoardTestConfig`, the `.gitkeep` files, `E2eTest`, `@NotNullWhenPresent`, the `module-boundaries` delta and the snapshot; whichever change lands second merges them. The archive guidance in `openspec/config.yaml` covers the spec delta.
- [No foreign key from `owner_id` to users] → Accepted (D12). No account deletion exists in v1, so no orphaned boards.
- [Tables exist without a migration] → `prod` fails to start until the baseline migration is written, as `CLAUDE.md` already states. The cascade and the check constraint must be carried into that migration by hand. Nothing deploys before `infra-vps`.
- [Hibernate `ddl-auto: update` doesn't add constraints to a table that already exists] → The tables are new, so dev gets them on first start. If a constraint changes during implementation, the dev tables must be dropped by hand.
- [The full board is returned on rename, and will hold every card after `be-cards`] → Accepted (D14). Rename is rare, and it costs the same as opening the board.
