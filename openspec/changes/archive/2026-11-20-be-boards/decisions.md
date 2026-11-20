# Decisions: be-boards

Confirmed with Philipp Lackner on 2026-11-15, through the `cardigan-cd` planning session, which relayed the questions and answers.

## Summary

This change builds boards and their columns in `:board` (breakdown chunk `be-boards`): create, list, get, rename and delete for boards, and create, rename, move and delete for columns, all scoped to the signed-in user. A board is read in one call with its columns embedded, and a new board starts with three default columns. Column positions stay dense under concurrent changes by locking the board row. The domain rules themselves (keys, text limits, dense positions, move semantics, timestamps, cascading deletes, 404 for foreign data) come from the shared specs and are not redecided here.

## Decisions

### D1 — Board read shape
- **Question**: What does `GET /api/v1/boards/{boardId}` return?
- **Decision**: The full board in one call: the board's fields plus its columns, embedded in position order. `be-cards` later adds each column's cards inside the column, so one request loads the whole board as one snapshot.
- **Alternatives**: Board with columns but cards from a separate endpoint; board, columns and cards each from their own endpoint.
- **Why**: Clients have no cache and load the open board from the backend, and search and filter run client-side on the open board.
- **Depends on**: —

### D2 — Column paths
- **Question**: Where do column endpoints live?
- **Decision**: Nested under the board: `/api/v1/boards/{boardId}/columns` and `/api/v1/boards/{boardId}/columns/{columnId}`. The board is resolved first, and a column under a board it doesn't belong to is 404.
- **Alternatives**: Create nested, rename/move/delete flat under `/api/v1/columns/{columnId}`.
- **Why**: Fits shared `api-errors` ("Other users' data is not found, never forbidden"), which already describes a column referenced under the wrong board.
- **Depends on**: —

### D3 — Column move endpoint
- **Question**: How is a column moved?
- **Decision**: An own endpoint, `POST /api/v1/boards/{boardId}/columns/{columnId}/move` with `{ "position": n }`. It returns all columns of the board. `PATCH` on a column is rename-only and returns the one column.
- **Alternatives**: `PATCH` on the column accepting `name` and/or `position`; `PUT …/columns/{columnId}/position`.
- **Why**: A move and a rename have different results, so they stay separate operations.
- **Depends on**: D2

### D4 — List wrapper
- **Question**: Do list-style responses use a wrapper object or a bare JSON array?
- **Decision**: A wrapper object: `GET /api/v1/boards` returns `{ "boards": [...] }` and a column move returns `{ "columns": [...] }`. The schemas are `BoardListDto` and `ColumnListDto`, matching `LabelListDto` in `be-labels`.
- **Alternatives**: Bare arrays.
- **Why**: Fields can be added next to the list later without a breaking change, and the user chose the same for the label list.
- **Depends on**: —

### D5 — Default columns
- **Question**: Who creates the default columns the design asks for?
- **Decision**: The backend. `POST /api/v1/boards` creates the board and three columns in one transaction.
- **Alternatives**: The create request carries an optional list of column names; the board starts empty and clients create the columns with separate calls.
- **Why**: One place, the same result on every client, and never a half-created board.
- **Depends on**: —

### D6 — Default column names
- **Question**: Which exact names do the default columns get?
- **Decision**: Always `To do`, `In progress`, `Done` at positions 0, 1, 2, in English, for every user.
- **Alternatives**: Names picked from the `Accept-Language` header.
- **Why**: No language handling on the backend; the names are stored text the user can rename.
- **Depends on**: D5

### D7 — Position of a new column
- **Question**: Where does a new column go?
- **Decision**: Always at the end (position n). The create request has only `name`.
- **Alternatives**: An optional `position` in the create request.
- **Why**: No client screen has a position picker for new columns.
- **Depends on**: —

### D8 — Changes that change nothing
- **Question**: What happens on a rename to the current name (after normalization) or a move to the current position?
- **Decision**: Nothing changes. The response is 200 with the current data, and neither the item's nor the board's `updatedAt` moves.
- **Alternatives**: Count it as a change and set both updated times.
- **Why**: Same as the empty `PATCH {}` in shared `api-conventions`, and the same rule the user chose in `be-labels`. The board doesn't jump to the top of the list.
- **Depends on**: —

### D9 — Limits
- **Question**: Is there a cap on boards per user or columns per board?
- **Decision**: No caps.
- **Alternatives**: Cap columns at 200 (the import limit); cap both.
- **Why**: The shared specs define none, and a cap would need a new error rule there first.
- **Depends on**: —

### D10 — Card count
- **Question**: When does the board list get the card count the design's board tile shows?
- **Decision**: Not in this change. `be-cards` adds `cardCount` as an additive field once cards exist.
- **Alternatives**: Ship `cardCount` now, always 0; never send a count from the backend.
- **Why**: No field that is always 0. Clients connect to boards in wave 5, after `be-cards`.
- **Depends on**: —

### D11 — Key error for control characters
- **Question**: Which field error does a board key with a control character get, given that `text-length` says `INVALID_CHARACTER` and `api-errors` says a wrong key character is `INVALID_FORMAT`?
- **Decision**: `INVALID_FORMAT` for every wrong character in a key, control characters included. Length is checked first, so a key that is a single wrong character is `TOO_SHORT`.
- **Alternatives**: `INVALID_CHARACTER` from the general text rule, checked before length.
- **Why**: Follows the key-specific sentence in `api-errors` and the clients' local check order. Both clients show the same message either way.
- **Depends on**: —

### D12 — Owner reference
- **Question**: How does a board reference its owner in the database?
- **Decision**: `boards.owner_id` is a plain indexed UUID column with no foreign key to `user_service.users`.
- **Alternatives**: A cross-schema foreign key.
- **Why**: Modules stay independent. The token's user ID is trusted without a lookup, so a token for a user without a row (only after a dev database wipe) can create boards.
- **Depends on**: —

### D13 — Concurrent column changes
- **Question**: How do concurrent column changes on one board keep positions dense?
- **Decision**: Every column create, move and delete first locks its board row (`SELECT … FOR UPDATE`) in the same transaction.
- **Alternatives**: An optimistic version column with retries; `SERIALIZABLE` transactions with retries.
- **Why**: Changes on one board run one after the other with no retry logic, and the board row is written anyway for `updatedAt`.
- **Depends on**: —

### D14 — Result of create and rename board
- **Question**: What do create board (201) and rename board (200) return?
- **Decision**: Both return the full board with its columns, the same shape as `GET /api/v1/boards/{boardId}`.
- **Alternatives**: Full board on create and only the board's own fields on rename; only the board's own fields for both.
- **Why**: One board schema besides the list item, and the client sees the default columns right after create.
- **Depends on**: D1, D5

### D15 — Column list endpoint
- **Question**: Is there a separate endpoint that lists a board's columns?
- **Decision**: No. Columns are read through `GET /api/v1/boards/{boardId}` only.
- **Alternatives**: `GET /api/v1/boards/{boardId}/columns`.
- **Why**: No second way to load the same data. The breakdown's "list within a board" is covered by the embedded columns.
- **Depends on**: D1

### D16 — Fields
- **Question**: Which fields do the responses and requests have?
- **Decision**: Board list item: `id`, `name`, `key`, `createdAt`, `updatedAt`. Full board: the same plus `columns`. Column: `id`, `name`, `position`, `createdAt`, `updatedAt`, with no `boardId`. Requests: create board `{ name, key }`, rename board `{ name }`, create column `{ name }`, rename column `{ name }`, move column `{ position }`.
- **Alternatives**: A column that also carries `boardId`.
- **Why**: A column always appears inside its board or under the board's URL.
- **Depends on**: D1, D2, D4

### D17 — operationIds and tags
- **Question**: Which operationIds and tags do the endpoints get?
- **Decision**: Tag `Boards`: `listBoards`, `createBoard`, `getBoard`, `updateBoard`, `deleteBoard`. Tag `Columns`: `createColumn`, `updateColumn`, `moveColumn`, `deleteColumn`.
- **Alternatives**: `renameBoard` / `renameColumn` for the PATCH operations; all nine operations under the one tag `Boards`.
- **Why**: `update*` still fits if PATCH gets more fields later.
- **Depends on**: D2, D3

### D18 — Database guard for positions
- **Question**: Should the database guard column positions on top of the board lock?
- **Decision**: Only a check constraint `position >= 0`. No unique constraint on (`board_id`, `position`).
- **Alternatives**: A unique constraint, with every shift done as a two-step update.
- **Why**: The dev and test schema comes from the entities, which can't declare a deferrable constraint. Density is kept by the lock and the service and proven by tests.
- **Depends on**: D13

### D19 — Cascading delete
- **Question**: How are a board's columns deleted with the board?
- **Decision**: Through the database: the foreign key `columns.board_id → boards.id` is `ON DELETE CASCADE`. `be-cards` follows the same pattern for cards.
- **Alternatives**: Delete columns, then the board, in the service.
- **Why**: One statement removes everything, also for boards with many cards later.
- **Depends on**: —

### D20 — Move range check
- **Question**: Where is the move position range checked?
- **Decision**: In the service, inside the transaction that holds the board lock. `:common` gets a new base exception for an out-of-range field (field, min, max), mapped to 400 `VALIDATION_FAILED` with the field error `OUT_OF_RANGE`. It adds no new error code. `be-cards` reuses it for card moves.
- **Alternatives**: The controller asks for the column count and builds the field error itself, outside the lock.
- **Why**: The valid range depends on the current column count, and a check outside the lock can be overtaken by a concurrent delete.
- **Depends on**: D13

### D21 — Null check for PATCH fields
- **Question**: Where does the constraint live that rejects `"name": null` in a PATCH with `REQUIRED` while accepting an absent field?
- **Decision**: In `:common`, as `@NotNullWhenPresent` in `com.plcoding.cardigan.common.api.validation`, reporting `REQUIRED` at the property path. It is shared with `be-labels`: whichever change is implemented first adds it, the other reuses it. No second copy.
- **Alternatives**: In `:board` `api/validation`.
- **Why**: Settled by the user in the `be-labels` session for both changes.
- **Depends on**: —

## Settled by existing rules

Recorded here, not asked again.

- Board name 1–100 and column name 1–30, single-line, normalized and counted per `text-length` — source: shared `domain-board` "Board name", `domain-column` "Column name".
- Key format, uppercase conversion, uniqueness per user, reuse after delete, and immutability — source: shared `domain-board`.
- A used key is 409 `CONFLICT` with `{ "field": "key", "code": "ALREADY_USED" }`, and field validation wins over uniqueness — source: shared `api-errors` "Uniqueness clashes are conflicts", "Error precedence".
- The move target is `position`, the final index, valid from 0 to n-1, otherwise `OUT_OF_RANGE` with `min` and `max`. A successful column move returns all columns — source: shared `api-conventions` "Move target is the final position", "Move responses contain all affected items".
- Updates are PATCH, an empty PATCH changes nothing, and `null` for a field is `REQUIRED` — source: shared `api-conventions` "Updates are partial", "Null and empty updates".
- `key` isn't a field of the rename request, so a `key` sent in a PATCH is an unknown field and is ignored — source: shared `api-conventions` "Unknown fields are ignored", `domain-board` "Board key never changes".
- Create is 201 with the full resource, update and move are 200, delete is 204, deleting something missing is 404 — source: shared `api-conventions`.
- Board list order (updated time, then created time, both newest first, then ID) and no pagination — source: shared `api-conventions` "Board list order", "Lists are complete and unpaginated".
- Missing or foreign boards and columns, and malformed path IDs, are 404 `NOT_FOUND`; references are checked before field validation — source: shared `api-errors`, `api-conventions`.
- Board and column timestamps, including that a board's updated time follows column changes and that shifted columns keep their updated time — source: shared `domain-board` "Board timestamps", `domain-column` "Column timestamps".
- No migration file yet; dev and tests build the schema from the entities — source: `CLAUDE.md`, "Database".

## Facts

- `:board` has no source files, only `.gitkeep` files in its layer packages and an empty migration folder — source: `board/src/main`.
- `:common` has `@Required`, `@ValidText`, `@ValidUuid`, `@ValidEnum`, `@ValidRange` and `RequestValidator`, but no key constraint, no way to raise a field error from a service, and no way to tell a JSON `null` from an absent field — source: `common/src/main/kotlin/.../api/validation`, `domain/exception/BaseExceptions.kt`.
- `@ValidRange` takes fixed limits, so it can't express a range that depends on the board's column count — source: `Constraints.kt`.
- Field error precedence in `:common` is `REQUIRED`, `INVALID_CHARACTER`, `TOO_SHORT`, `TOO_LONG`, `INVALID_FORMAT`, so a key checked with `@ValidText` would report a control character as `INVALID_CHARACTER`. D11 needs a key-specific check — source: `FieldErrorCode.kt`, `Validators.kt`.
- Surrounding whitespace in a key is trimmed before the key rules apply, because the key is a text field under `text-length` — source: shared `text-length` "Text is normalized before validation".
- `E2eTest` cleans only `user_service` before each test — source: `app/src/test/kotlin/com/plcoding/cardigan/E2eTest.kt`.
- `be-labels` is planned in parallel in the same module. It also changes the `module-boundaries` requirement "Module layer packages exist from the start", and touches `board/build.gradle.kts`, `BoardTestApplication`, the `.gitkeep` files, `E2eTest` and the OpenAPI snapshot — source: `openspec/changes/be-labels` (commit 8923aa5).
- The design decisions list "card count" on the board tile and the three default columns; the shared specs mention neither — source: `docs/design/DECISIONS.md` in `cardigan-shared-specs`.

## Out of scope

- Cards, card moves, card numbering and `cardCount` (`be-cards`).
- Labels (`be-labels`) and import/export (`be-import-export`).
- A separate column list endpoint (D15) and caps on boards or columns (D9).
- Localized default column names (D6).
- The baseline Flyway migration (before the first production deploy).
- Events: nothing in this change publishes or consumes one, so the Modulith registry isn't wired in.
- Changes to the shared specs repo.

## Deferred

Nothing is deferred.
