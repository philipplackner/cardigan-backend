# Proposal

## Why

Boards and columns are the frame every card hangs off, and both clients have a board list and a board view waiting for real data. `be-cards`, `mob-boards-connect` and `web-boards-connect` are blocked until boards and columns exist on the backend. This change builds them (breakdown chunk `be-boards`). See `decisions.md` for every settled choice.

## What Changes

- Five protected endpoints with the tag `Boards` (D17):
  - `GET /api/v1/boards` (`listBoards`) returns all of the user's boards as `{ "boards": [...] }`, without columns (D4).
  - `POST /api/v1/boards` (`createBoard`) creates a board from `name` and `key` plus the three columns `To do`, `In progress`, `Done` in one transaction, and returns 201 with the full board (D5, D6, D14).
  - `GET /api/v1/boards/{boardId}` (`getBoard`) returns the full board with its columns embedded in position order (D1).
  - `PATCH /api/v1/boards/{boardId}` (`updateBoard`) renames the board and returns 200 with the full board (D14).
  - `DELETE /api/v1/boards/{boardId}` (`deleteBoard`) deletes the board with its columns and returns 204 (D19).
- Four protected endpoints with the tag `Columns`, nested under the board (D2, D17):
  - `POST /api/v1/boards/{boardId}/columns` (`createColumn`) adds a column at the end and returns 201 with it (D7).
  - `PATCH /api/v1/boards/{boardId}/columns/{columnId}` (`updateColumn`) renames the column and returns 200 with it.
  - `POST /api/v1/boards/{boardId}/columns/{columnId}/move` (`moveColumn`) moves the column to `position` and returns 200 with `{ "columns": [...] }`, all columns of the board (D3, D4).
  - `DELETE /api/v1/boards/{boardId}/columns/{columnId}` (`deleteColumn`) deletes the column and returns 204.
- No separate endpoint lists a board's columns (D15).
- A board has `id`, `name`, `key`, `createdAt`, `updatedAt`, and `columns` on the full board. A column has `id`, `name`, `position`, `createdAt`, `updatedAt` (D16).
- A rename to the current name or a move to the current position changes nothing, including every `updatedAt` (D8).
- Every wrong character in a board key, control characters included, is `INVALID_FORMAT`, checked after the length (D11).
- Column create, move and delete lock the board row, so positions stay dense under concurrent requests (D13). The move range is checked under that lock (D20).
- No caps on boards per user or columns per board (D9). No `cardCount` yet (D10).
- New tables `boards` and `columns` in the schema `board_service`. A board's owner is a plain UUID without a foreign key to the users table (D12). Columns are removed with their board by the database (D19). No Flyway migration yet; dev and tests build the schema from the entities.
- `:common` gets a base exception for an out-of-range field (D20) and the shared `@NotNullWhenPresent` constraint for PATCH requests (D21), unless `be-labels` added the latter first.
- `openapi.json` gets the nine operations and the board and column schemas.
- `:board` gets production code, so the `module-boundaries` scenario that expects no source files there changes. `be-labels` makes the same change.

Not in this change: cards, labels, import/export, and the baseline migration.

## Shared requirements implemented or depended on

| Spec | Requirement | How |
|---|---|---|
| `domain-board` | Boards have a backend-assigned identifier | Implemented |
| `domain-board` | Board name | Implemented |
| `domain-board` | Board key format | Implemented |
| `domain-board` | Board key is unique among a user's existing boards | Implemented |
| `domain-board` | Board key never changes | Implemented. The rename request has no `key` field |
| `domain-board` | Board timestamps | Implemented for board and column changes. The card part is `be-cards` |
| `domain-board` | Deleting a board removes its content | Implemented for columns. The card part is `be-cards` |
| `domain-column` | Columns have a backend-assigned identifier | Implemented |
| `domain-column` | A column belongs to exactly one board | Implemented |
| `domain-column` | Column name | Implemented |
| `domain-column` | Column timestamps | Implemented |
| `domain-column` | Deleting a column removes its cards | The column is deleted and positions stay dense. The card part is `be-cards` |
| `domain-ordering` | Columns have dense positions in their board | Implemented |
| `domain-ordering` | Order is defined only by positions | Implemented for columns |
| `domain-ordering` | Concurrent changes resolve as last write wins | Implemented for boards and columns |
| `domain-user` | Boards and labels belong to exactly one user | Implemented for boards |
| `domain-user` | Other users' data is treated as not found | Implemented for boards and columns |
| `text-length` | Text is normalized before validation | Used for `name` and `key` |
| `text-length` | Length is counted in Unicode code points | Used for `name` and `key` |
| `text-length` | Invalid characters are rejected | Used for `name` (single line). For `key` see D11 |
| `text-length` | Backend rejects text outside its length limits | Used for `name` and `key` |
| `text-length` | Backend stores and returns normalized text | Implemented for `name` and `key` |
| `api-conventions` | Updates are partial | Implemented for boards and columns |
| `api-conventions` | Null and empty updates | Implemented for boards and columns |
| `api-conventions` | Unknown fields are ignored | Depended on: a `key` in a board PATCH is ignored |
| `api-conventions` | Success status codes and bodies | Implemented |
| `api-conventions` | Deleting a missing resource is not found | Implemented |
| `api-conventions` | Malformed path identifiers are not found | Used for `boardId` and `columnId` |
| `api-conventions` | Move target is the final position | Implemented for columns |
| `api-conventions` | Move responses contain all affected items | Implemented for columns |
| `api-conventions` | Lists are complete and unpaginated | Implemented for boards |
| `api-conventions` | Board list order | Implemented |
| `api-conventions` | Documented fields are always present | Implemented |
| `api-conventions` | Null values | No board or column field is nullable |
| `api-errors` | Field error codes | Text codes for `name`, key codes for `key`, `OUT_OF_RANGE` for `position` |
| `api-errors` | One field error per field | Implemented for `key` (length before format, D11) |
| `api-errors` | Limit errors carry the limit | `min`/`max` on `key`, `name` and `position` errors |
| `api-errors` | Validation errors report all broken fields | Used |
| `api-errors` | Uniqueness clashes are conflicts | Implemented for board keys |
| `api-errors` | Other users' data is not found, never forbidden | Implemented, including a column under the wrong board |
| `api-errors` | Error precedence | Parsing, then 404, then field validation, then 409 |
| `api-auth-tokens` | Protected requests use a bearer access token | All nine endpoints are protected |

## Capabilities

### New Capabilities

- `boards`: the backend's board endpoints and their bodies, the default columns of a new board, the key error codes, when a rename moves `updatedAt`, and board isolation between users.
- `board-columns`: the backend's column endpoints and their bodies, where a new column goes, the move endpoint and its range check, how positions stay dense under concurrent requests, and when a column change moves `updatedAt`.

### Modified Capabilities

- `module-boundaries`: "Module layer packages exist from the start" — `:board` now holds production code, so the "no source files" scenario becomes a check for placeholder classes in empty packages. `be-labels` carries the identical delta.

## Impact

- **Code**: `:board` (board and column domain models, services, controllers, DTOs, mappers, the key constraint, entities, repositories, module test app). `:common` (a base exception for an out-of-range field with its handler mapping, `@NotNullWhenPresent` if it doesn't exist yet, and the field-violation helper opened up so a module can write its own constraint). `:app` (E2E tests, cleanup of `board_service` in the E2E base class).
- **API**: nine new operations and the schemas `BoardDto`, `BoardSummaryDto`, `BoardListDto`, `ColumnDto`, `ColumnListDto`, `CreateBoardRequest`, `UpdateBoardRequest`, `CreateColumnRequest`, `UpdateColumnRequest` and `MoveColumnRequest` in `openapi.json`. Additive only.
- **Dependencies**: none new.
- **Database**: tables `board_service.boards` and `board_service.columns`. Dev creates them through `ddl-auto: update`. They become part of the baseline migration before the first production deploy.
- **Other chunks**: `be-cards` adds `cards` inside each column, `cardCount` on boards, the card cascade, and reuses the board lock, the dense-position helper and the out-of-range exception. `be-import-export` reuses the key rule. `mob-boards-connect` and `web-boards-connect` generate from the updated `openapi.json`. `be-labels` touches the same module and shared files; whichever lands second merges.
- **Project rules**: `AGENTS.md` (current state, and the list of `:common` base exceptions).
