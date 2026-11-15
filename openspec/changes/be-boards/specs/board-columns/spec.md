# Spec Delta

## Purpose

Defines the backend's column endpoints and their request and response bodies, where a new column goes, how a column is moved, and how column positions and timestamps behave under renames, deletes and concurrent requests, on top of the shared specs `domain-column` and `domain-ordering`.

## ADDED Requirements

### Requirement: Column representation
A column in a response SHALL have exactly the fields `id`, `name`, `position`, `createdAt` and `updatedAt`. It SHALL NOT carry its board's identifier. No field SHALL be nullable or absent.

#### Scenario: Column body
- **WHEN** a column named `Review` at position 3 was created at `2026-11-15T09:15:30.123Z` and is returned
- **THEN** the body has exactly `id` (a lowercase UUID), `name` `Review`, `position` `3`, `createdAt` `2026-11-15T09:15:30.123Z` and `updatedAt` `2026-11-15T09:15:30.123Z`

### Requirement: Columns are read through their board
The backend SHALL NOT offer an endpoint that lists a board's columns on their own. Columns SHALL be read through `GET /api/v1/boards/{boardId}`.

#### Scenario: No column list endpoint
- **WHEN** an authenticated user sends a GET to `/api/v1/boards/{boardId}/columns` for their own board
- **THEN** the response is 404 `ROUTE_NOT_FOUND`

### Requirement: Create a column
`POST /api/v1/boards/{boardId}/columns` SHALL create a column in a board the authenticated user owns from the required field `name`, and SHALL return 201 with the column. The new column SHALL always get the position after the board's last column. The request SHALL have no `position` field. `createdAt` and `updatedAt` SHALL both be the time of creation.

#### Scenario: Column appended
- **WHEN** a user sends `{ "name": " Review " }` for their board with three columns
- **THEN** the response is 201 with a new `id`, `name` `Review` and `position` `3`

#### Scenario: First column of an empty board
- **WHEN** a user creates a column in a board without columns
- **THEN** the column has the position 0

#### Scenario: Position in the body is ignored
- **WHEN** a user sends `{ "name": "Review", "position": 0 }` for their board with three columns
- **THEN** the response is 201 with `position` `3`

#### Scenario: Missing name
- **WHEN** a user sends `{}`
- **THEN** the response is 400 `VALIDATION_FAILED` with `name` `REQUIRED`, and no column is stored

#### Scenario: Name too long
- **WHEN** a user sends a name of normalized length 31
- **THEN** the response is 400 `VALIDATION_FAILED` with `name` `TOO_LONG` with `max` 30

#### Scenario: Many columns
- **WHEN** a user creates a column in a board that already has 200 columns
- **THEN** the response is 201 with `position` `200`

### Requirement: Rename a column
`PATCH /api/v1/boards/{boardId}/columns/{columnId}` SHALL change the `name` of a column and SHALL return 200 with that column. `name` SHALL be optional, and sent as `null` it SHALL produce the field error `REQUIRED`. The request SHALL have no other field, so a `position` in the body SHALL be ignored.

#### Scenario: Rename
- **WHEN** a user sends `{ "name": "QA" }` for their column `Review`
- **THEN** the response is 200 with `name` `QA`, the unchanged `position` and `createdAt`, and `updatedAt` set to the time of the request

#### Scenario: Null name
- **WHEN** a user sends `{ "name": null }`
- **THEN** the response is 400 `VALIDATION_FAILED` with `name` `REQUIRED`, and the column is unchanged

#### Scenario: Position in the body
- **WHEN** a user sends `{ "name": "QA", "position": 0 }` for their column at position 2
- **THEN** the response is 200 with `name` `QA` and `position` `2`

### Requirement: Move a column
`POST /api/v1/boards/{boardId}/columns/{columnId}/move` SHALL move a column to the required `position` and SHALL return 200 with an object whose only field `columns` holds all columns of the board in ascending position order. A missing or `null` `position` SHALL produce the field error `REQUIRED`.

#### Scenario: Move to the front
- **WHEN** a board has the columns A, B, C and a user sends `{ "position": 0 }` for C
- **THEN** the response is 200 with `{ "columns": [...] }` holding C, A, B with the positions 0, 1, 2

#### Scenario: Move to the end
- **WHEN** a board has the columns A, B, C and a user sends `{ "position": 2 }` for A
- **THEN** the response holds B, C, A with the positions 0, 1, 2

#### Scenario: Missing position
- **WHEN** a user sends `{}` to the move endpoint
- **THEN** the response is 400 `VALIDATION_FAILED` with `position` `REQUIRED`, and no position changes

#### Scenario: Position of another type
- **WHEN** a user sends `{ "position": "a" }`
- **THEN** the response is 400 `MALFORMED_REQUEST`

### Requirement: The move range follows the current column count
The valid range of `position` SHALL be 0 to n-1 for the n columns the board has at the moment the move is applied, and an `OUT_OF_RANGE` field error SHALL carry that `min` and `max`. A position that is in range for the current columns SHALL be applied as sent, even when the client's view of the board was stale.

#### Scenario: Position equal to the column count
- **WHEN** a board has 3 columns and a column is moved to position 3
- **THEN** the response is 400 `VALIDATION_FAILED` with the field error `{ "field": "position", "code": "OUT_OF_RANGE", "min": 0, "max": 2 }`, and no position or timestamp changes

#### Scenario: Negative position
- **WHEN** a column is moved to position -1
- **THEN** the field error for `position` is `OUT_OF_RANGE` with `min` 0

#### Scenario: Huge position
- **WHEN** a column in a board with 3 columns is moved to position 99999999999999999999
- **THEN** the field error for `position` is `OUT_OF_RANGE` with `min` 0 and `max` 2

#### Scenario: Range after a delete on another device
- **WHEN** a board had 5 columns, another device deleted two, and a client moves a column to position 4
- **THEN** the field error for `position` is `OUT_OF_RANGE` with `max` 2

#### Scenario: Only column
- **WHEN** a board has one column and it is moved to position 1
- **THEN** the field error for `position` is `OUT_OF_RANGE` with `min` 0 and `max` 0

### Requirement: Delete a column
`DELETE /api/v1/boards/{boardId}/columns/{columnId}` SHALL permanently delete a column and SHALL return 204 with no body. The columns after it SHALL move one position down and keep their `updatedAt`.

#### Scenario: Middle column deleted
- **WHEN** a board has the columns A, B, C and a user deletes B
- **THEN** the response is 204 with an empty body, and the board holds A and C at the positions 0 and 1 with unchanged `updatedAt`

#### Scenario: Last column deleted
- **WHEN** a user deletes the only column of a board
- **THEN** the response is 204 and the board has an empty `columns` array

#### Scenario: Delete twice
- **WHEN** a user deletes a column and sends the same delete again
- **THEN** the second response is 404 `NOT_FOUND`

### Requirement: Changes that change nothing move no updated time
A column rename whose name equals the stored one after normalization, an empty PATCH, and a move to the column's current position SHALL return 200 with the current data and SHALL change neither the column's nor the board's `updatedAt`.

#### Scenario: Same name again
- **WHEN** a column is named `Review` and, one minute after the board's last change, a user sends `{ "name": " Review " }`
- **THEN** the response is 200, and the column's and the board's `updatedAt` are unchanged

#### Scenario: Empty patch
- **WHEN** a user sends `{}` for their column
- **THEN** the response is 200 with the unchanged column, and the board's `updatedAt` is unchanged

#### Scenario: Move to the current position
- **WHEN** a column is at position 1 and a user moves it to position 1
- **THEN** the response is 200 with all columns unchanged, and no column's and not the board's `updatedAt` changes

### Requirement: Column changes update the board
A column create, an effective rename, an effective move and a delete SHALL set the board's `updatedAt` to the time of the request. A move SHALL set only the moved column's `updatedAt`.

#### Scenario: Create updates the board
- **WHEN** a user creates a column one minute after the board's last change
- **THEN** the board's `updatedAt` equals the new column's `createdAt`

#### Scenario: Move updates the moved column and the board
- **WHEN** a board has the columns A, B, C and C is moved to position 0 one minute later
- **THEN** C's and the board's `updatedAt` are the time of the move, and the `updatedAt` of A and B is unchanged

#### Scenario: Delete updates the board
- **WHEN** a user deletes a column one minute after the board's last change
- **THEN** the board's `updatedAt` is the time of the delete

### Requirement: Positions stay dense under concurrent requests
Column creates, moves and deletes on the same board SHALL be applied one after the other, each on the result of the one before. After any number of simultaneous requests, the board's column positions SHALL be exactly 0 to n-1, each used once. Requests on different boards SHALL NOT wait for each other.

#### Scenario: Simultaneous creates
- **WHEN** ten create requests for one board with three columns arrive at the same time
- **THEN** all ten return 201, and the board has 13 columns with the positions 0 to 12, each used once

#### Scenario: Simultaneous move and delete
- **WHEN** a move and a delete of two different columns of one board arrive at the same time
- **THEN** both succeed or the move returns `OUT_OF_RANGE`, and the remaining positions are dense

#### Scenario: Negative position written past the application
- **WHEN** a column row with the position -1 is inserted directly into the database
- **THEN** the database rejects it

### Requirement: Columns are reachable only through their own board
Every column endpoint SHALL first resolve `boardId` for the authenticated user and then `columnId` within that board. A board or column that belongs to another user, does not exist, or has a malformed ID, and a column addressed under a board it does not belong to, SHALL produce the same 404 `NOT_FOUND`, reported before field validation.

#### Scenario: Create in another user's board
- **WHEN** user B sends a column create for the ID of user A's board
- **THEN** the response is 404 `NOT_FOUND` and user A's board is unchanged

#### Scenario: Column under the wrong board
- **WHEN** a user owns the boards X and Y and sends a rename for a column of X under the path of Y
- **THEN** the response is 404 `NOT_FOUND` and the column is unchanged

#### Scenario: Move another user's column
- **WHEN** user B sends a move for user A's board and column IDs
- **THEN** the response is 404 `NOT_FOUND` and no position changes

#### Scenario: Malformed column ID
- **WHEN** a user sends a DELETE to `/api/v1/boards/{boardId}/columns/abc` for their own board
- **THEN** the response is 404 `NOT_FOUND`

#### Scenario: Missing column and invalid body
- **WHEN** a user sends a move with `{ "position": -1 }` for a column ID that names no column
- **THEN** the response is 404 `NOT_FOUND`

#### Scenario: Column of a deleted board
- **WHEN** a user deletes a board and then renames one of its former columns
- **THEN** the response is 404 `NOT_FOUND`

### Requirement: Column operations in the API document
The OpenAPI document SHALL list the column operations under the tag `Columns` with the operationIds `createColumn`, `updateColumn`, `moveColumn` and `deleteColumn`, each documenting 404 `NOT_FOUND`. `position` in the move request SHALL be documented as a required integer.

#### Scenario: Operations and tag
- **WHEN** the snapshot is inspected
- **THEN** the four column operations exist with those operationIds, each with exactly the tag `Columns`

#### Scenario: Request fields
- **WHEN** the column request schemas are inspected
- **THEN** the create request requires `name`, the update request has only `name` and requires nothing, the move request requires `position` of type `integer`, and no property is nullable
