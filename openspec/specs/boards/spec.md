# boards Specification

## Purpose

Defines the backend's board endpoints and their request and response bodies, the default columns of a new board, and the backend-specific behavior around board keys, renames, deletion and ownership, on top of the shared spec `domain-board`.

## Requirements

### Requirement: Board representations
A full board in a response SHALL have exactly the fields `id`, `name`, `key`, `createdAt`, `updatedAt` and `columns`, where `columns` holds all of the board's columns in ascending position order, in the column representation of `board-columns`. A board in the board list SHALL have exactly `id`, `name`, `key`, `createdAt` and `updatedAt`. No field SHALL be nullable or absent.

#### Scenario: Full board body
- **WHEN** a board named `Roadmap` with the key `CRD` was created at `2026-11-15T09:15:30.123Z` and is returned by `GET /api/v1/boards/{boardId}`
- **THEN** the body has exactly `id` (a lowercase UUID), `name` `Roadmap`, `key` `CRD`, `createdAt` and `updatedAt` `2026-11-15T09:15:30.123Z`, and `columns`

#### Scenario: Board without columns
- **WHEN** a board whose columns were all deleted is returned
- **THEN** `columns` is an empty array

#### Scenario: List item has no columns
- **WHEN** a board is returned in the board list
- **THEN** its entry has exactly `id`, `name`, `key`, `createdAt` and `updatedAt`

### Requirement: Create a board
`POST /api/v1/boards` SHALL create a board owned by the authenticated user from the required fields `name` and `key`, and SHALL return 201 with the full board. `createdAt` and `updatedAt` SHALL both be the time of creation.

#### Scenario: Board created
- **WHEN** a user sends `{ "name": "  Roadmap ", "key": "crd" }`
- **THEN** the response is 201 with a new `id`, `name` `Roadmap`, `key` `CRD`, and equal `createdAt` and `updatedAt`

#### Scenario: Empty body object
- **WHEN** a user sends `{}`
- **THEN** the response is 400 `VALIDATION_FAILED` with exactly the field errors `key` `REQUIRED` and `name` `REQUIRED`, and no board is stored

#### Scenario: Line break in the name
- **WHEN** a user sends the name `"a\nb"` with a valid key
- **THEN** the response is 400 `VALIDATION_FAILED` with `name` `INVALID_CHARACTER`

#### Scenario: No token
- **WHEN** a client calls `POST /api/v1/boards` without a token
- **THEN** the response is 401 `UNAUTHENTICATED`

#### Scenario: Many boards
- **WHEN** a user who already has 300 boards creates another one with an unused key
- **THEN** the response is 201

### Requirement: A new board starts with three columns
Creating a board through `POST /api/v1/boards` SHALL also create the columns `To do`, `In progress` and `Done` at positions 0, 1 and 2, with the board's creation time as their `createdAt` and `updatedAt`. The names SHALL be the same for every user and request language. The board and its three columns SHALL be stored together or not at all.

#### Scenario: Default columns in the response
- **WHEN** a user creates a board
- **THEN** the 201 body's `columns` holds exactly `To do`, `In progress`, `Done` with the positions 0, 1, 2, each with its own `id`, and each with `createdAt` and `updatedAt` equal to the board's `createdAt`

#### Scenario: Request language is ignored
- **WHEN** a user creates a board with the header `Accept-Language: de`
- **THEN** the columns are still named `To do`, `In progress` and `Done`

#### Scenario: Rejected create stores no columns
- **WHEN** a board create is rejected with 409 `CONFLICT` for a used key
- **THEN** no board and no column is stored

#### Scenario: Column write fails
- **WHEN** storing the third default column fails during a board create
- **THEN** the create fails, and neither the board nor any of its columns is stored

### Requirement: Key validation codes
The `key` of a create request SHALL be trimmed as `text-length` defines and its letters `a` to `z` converted to uppercase, then checked in this order: empty is `REQUIRED`, fewer than 2 code points is `TOO_SHORT` with `min` 2, more than 5 is `TOO_LONG` with `max` 5, and any character outside `A` to `Z` is `INVALID_FORMAT`. A control character or unpaired surrogate in a key SHALL be `INVALID_FORMAT` too, never `INVALID_CHARACTER`.

#### Scenario: Surrounding whitespace
- **WHEN** a board is created with the key `" crd "`
- **THEN** the response is 201 with `key` `CRD`

#### Scenario: Whitespace-only key
- **WHEN** a board is created with the key `"   "`
- **THEN** the field error for `key` is `REQUIRED`

#### Scenario: Tab inside the key
- **WHEN** a board is created with the key `"C\tD"`
- **THEN** the field error for `key` is `INVALID_FORMAT`

#### Scenario: Unpaired surrogate in the key
- **WHEN** a board is created with the key `"C\ud800D"`
- **THEN** the field error for `key` is `INVALID_FORMAT`

#### Scenario: Single wrong character
- **WHEN** a board is created with the key `"1"`
- **THEN** the field error for `key` is `TOO_SHORT` with `min` 2

#### Scenario: Too long with wrong characters
- **WHEN** a board is created with the key `ÄBCDEF`
- **THEN** the field error for `key` is `TOO_LONG` with `max` 5

#### Scenario: Space inside the key
- **WHEN** a board is created with the key `"C D"`
- **THEN** the field error for `key` is `INVALID_FORMAT`

#### Scenario: Key of another type
- **WHEN** a board is created with `"key": 12`
- **THEN** the response is 400 `MALFORMED_REQUEST`

### Requirement: Key uniqueness holds under concurrent requests
The uniqueness of board keys per user SHALL hold when requests run at the same time. Of several simultaneous create requests by one user with the same key, exactly one SHALL succeed, and each other SHALL return 409 `CONFLICT` with the field error `ALREADY_USED` for `key`.

#### Scenario: Two simultaneous creates
- **WHEN** a user sends two create requests with the keys `CRD` and `crd` at the same time
- **THEN** one response is 201, the other is 409 `CONFLICT` with `ALREADY_USED` for `key`, and the user has exactly one board with three columns

#### Scenario: Duplicate written past the application
- **WHEN** a second board row with the same owner and key is inserted directly into the database
- **THEN** the database rejects it

### Requirement: List boards
`GET /api/v1/boards` SHALL return 200 with an object whose only field `boards` is an array of all boards the authenticated user owns, as list items, in the board list order of shared `api-conventions`. With no boards, `boards` SHALL be an empty array.

#### Scenario: Wrapper object
- **WHEN** a user with two boards calls the endpoint
- **THEN** the response is 200 with `{ "boards": [...] }` holding both boards

#### Scenario: No boards
- **WHEN** a user without boards calls the endpoint
- **THEN** the response is 200 with `{ "boards": [] }`

#### Scenario: Column change moves a board to the front
- **WHEN** a user creates board A, creates board B one minute later, and one minute after that renames a column of A to a new name
- **THEN** the list returns A before B

#### Scenario: A column change that changes nothing keeps the order
- **WHEN** a user creates board A, creates board B one minute later, and one minute after that renames a column of A to its current name
- **THEN** the list returns B before A

#### Scenario: Identifier tie-break
- **WHEN** two boards have the same `updatedAt` and `createdAt` and the identifiers `0f…` and `f0…`
- **THEN** the board with the identifier `0f…` comes first

### Requirement: Get a board
`GET /api/v1/boards/{boardId}` SHALL return 200 with the full board when the authenticated user owns it.

#### Scenario: Board with columns
- **WHEN** a user creates a board, moves its column `Done` to position 0, and then requests the board
- **THEN** the response is 200 and `columns` holds `Done`, `To do`, `In progress` in that order with the positions 0, 1, 2

#### Scenario: Uppercase board ID
- **WHEN** a user requests their own board with the ID written in uppercase
- **THEN** the response is 200 and `id` is lowercase

### Requirement: Rename a board
`PATCH /api/v1/boards/{boardId}` SHALL change the `name` of a board the authenticated user owns and SHALL return 200 with the full board. `name` SHALL be optional, and sent as `null` it SHALL produce the field error `REQUIRED`. The request SHALL have no other field, so a `key` in the body SHALL be ignored.

#### Scenario: Rename
- **WHEN** a user sends `{ "name": " Platform " }` for their board `Roadmap` one minute after it was created
- **THEN** the response is 200 with `name` `Platform`, the unchanged `key`, `createdAt` and `columns`, and `updatedAt` set to the time of the request

#### Scenario: Null name
- **WHEN** a user sends `{ "name": null }`
- **THEN** the response is 400 `VALIDATION_FAILED` with `name` `REQUIRED`, and the board is unchanged

#### Scenario: Whitespace-only name
- **WHEN** a user sends `{ "name": "   " }`
- **THEN** the response is 400 `VALIDATION_FAILED` with `name` `REQUIRED`

#### Scenario: Key in the body
- **WHEN** a user sends `{ "name": "Platform", "key": "NEW" }` for their board with the key `CRD`
- **THEN** the response is 200 with `name` `Platform` and `key` `CRD`

#### Scenario: Only a key in the body
- **WHEN** a user sends `{ "key": "NEW" }` for their board with the key `CRD` one minute after its last change
- **THEN** the response is 200 with the unchanged board, including `updatedAt`

### Requirement: An unchanged board name does not move the updated time
A board's `updatedAt` SHALL change on a rename only when the stored name differs after the update. A PATCH whose name equals the stored one after normalization, and an empty PATCH, SHALL return 200 with the unchanged board.

#### Scenario: Same name with surrounding spaces
- **WHEN** a board is named `Roadmap` and, one minute after its last change, a user sends `{ "name": " Roadmap " }`
- **THEN** the response is 200 and `updatedAt` is unchanged

#### Scenario: Empty patch
- **WHEN** a user sends `{}` for their board one minute after its last change
- **THEN** the response is 200 with the unchanged board, including `updatedAt`

#### Scenario: Case-only rename
- **WHEN** a board is named `roadmap` and, one minute after its last change, a user sends `{ "name": "Roadmap" }`
- **THEN** the response is 200 with `name` `Roadmap`, and `updatedAt` changes to the time of the request

### Requirement: Delete a board
`DELETE /api/v1/boards/{boardId}` SHALL permanently delete a board the authenticated user owns together with all its columns, and SHALL return 204 with no body. The database itself SHALL remove a board's columns when the board row is removed.

#### Scenario: Board deleted
- **WHEN** a user deletes one of their two boards
- **THEN** the response is 204 with an empty body, the board list holds only the other board, and no column of the deleted board is stored

#### Scenario: Delete twice
- **WHEN** a user deletes a board and sends the same delete again
- **THEN** the second response is 404 `NOT_FOUND`

#### Scenario: Board row removed directly
- **WHEN** a board row is deleted directly in the database
- **THEN** its column rows are gone as well

### Requirement: Boards are isolated per user
A board SHALL be readable and changeable only by its owner, the user whose token created it. For get, rename and delete, a `boardId` that names another user's board, names no board, or is not a UUID in the canonical 8-4-4-4-12 form SHALL produce the same 404 `NOT_FOUND`, and that 404 SHALL be reported before field validation.

#### Scenario: List shows only own boards
- **WHEN** user A has a board and user B lists boards
- **THEN** user B's list does not contain user A's board

#### Scenario: Get another user's board
- **WHEN** user B requests the ID of user A's board
- **THEN** the response is 404 `NOT_FOUND`, identical to the response for an ID that never existed

#### Scenario: Rename another user's board
- **WHEN** user B sends a PATCH for the ID of user A's board
- **THEN** the response is 404 `NOT_FOUND` and user A's board is unchanged

#### Scenario: Delete another user's board
- **WHEN** user B sends a DELETE for the ID of user A's board
- **THEN** the response is 404 `NOT_FOUND` and user A still has the board and its columns

#### Scenario: Malformed board ID
- **WHEN** a user sends a GET to `/api/v1/boards/abc`
- **THEN** the response is 404 `NOT_FOUND`

#### Scenario: Short-form board ID
- **WHEN** a user sends a GET for their own board with the leading zeros of an ID group left out
- **THEN** the response is 404 `NOT_FOUND`

#### Scenario: Missing board and invalid body
- **WHEN** a user sends a PATCH with a name of normalized length 101 for an ID that names no board
- **THEN** the response is 404 `NOT_FOUND`

### Requirement: A board's owner is the token subject
A board SHALL store the user ID from the access token as its owner, without checking that a user record exists, and the board tables SHALL NOT reference the user table.

#### Scenario: Token without a user record
- **WHEN** a valid access token whose subject has no user record creates a board
- **THEN** the response is 201 and the board is listed for that token

### Requirement: Board operations in the API document
The OpenAPI document SHALL list the board operations under the tag `Boards` with the operationIds `listBoards`, `createBoard`, `getBoard`, `updateBoard` and `deleteBoard`. `createBoard` SHALL document 409 `CONFLICT`, and `getBoard`, `updateBoard` and `deleteBoard` SHALL document 404 `NOT_FOUND`. `createBoard` and `updateBoard` SHALL document 400 with the codes `VALIDATION_FAILED` and `MALFORMED_REQUEST`.

#### Scenario: Operations and tag
- **WHEN** the snapshot is inspected
- **THEN** the five board operations exist with those operationIds, each with exactly the tag `Boards`

#### Scenario: Error statuses
- **WHEN** the snapshot is inspected
- **THEN** `createBoard` documents 409 with the code `CONFLICT`, `getBoard`, `updateBoard` and `deleteBoard` document 404 with the code `NOT_FOUND`, `listBoards` and `createBoard` document no 404, and `createBoard` and `updateBoard` document 400 with the codes `VALIDATION_FAILED` and `MALFORMED_REQUEST`

#### Scenario: Response fields are required
- **WHEN** the board response schemas are inspected
- **THEN** every property of the full board, the list item and the board list is required, and none is nullable

#### Scenario: Request fields
- **WHEN** the board request schemas are inspected
- **THEN** the create request requires `name` and `key`, the update request has only `name` and requires nothing, and no property is nullable

#### Scenario: Full board and list item are separate schemas
- **WHEN** the response schemas are inspected
- **THEN** the full board schema requires `columns`, and the list item schema has no `columns` property
