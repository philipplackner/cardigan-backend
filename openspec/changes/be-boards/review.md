# Artifact review: be-boards

**Verdict:** Ready (no blocker; all 20 findings applied, 5 of them with edits)
**Reviewed:** 2026-11-15, schema `careful-planning`, repo kind `code`

| Reviewer | Blocker | Major | Minor |
|---|---|---|---|
| Testability | 0 | 3 | 6 |
| Security | 0 | 2 | 0 |
| Hidden assumptions | 0 | 0 | 3 |
| Design contract | 0 | 2 | 4 |

No two reviewers proposed incompatible fixes. Four groups of findings touch the same text and should be applied together:

- **S1 and T5** both change the requirement "Positions stay dense under concurrent requests". S1 rewords the sentence "Requests on different boards SHALL NOT wait for each other"; T5 adds scenarios for it. T5's scenarios fit either S1 option.
- **D1, T6 and D3** all concern the API document. D1 adds the 400 `VALIDATION_FAILED` documentation, T6 adds scenarios for the error statuses, and D3 says those scenarios belong in `OpenApiSnapshotTest`. T6's test sketch names `ApiDocsE2eTest`; if D3 is applied, T6's tests go into `OpenApiSnapshotTest` as well.
- **A2 and T7** both concern one clock read per write. A2 fixes the design, T7 adds the scenario assertion.
- **T3, T5 and A2** each add a case to task 4.3. The additions don't overlap.

T9, D3 and D6 name the same defect in `be-labels`. That change is outside this review and is not edited from here.

## Findings

### S1: One user can hold the whole connection pool by queuing writes on their own board
**Status:** applied with edits (user chose the lock timeout; 5 seconds, set with `SET LOCAL lock_timeout` instead of the JPA hint, moved into its own requirement "Board lock waits are bounded" with a timeout scenario)
- Severity: major - any registered account can stall the API for all users, and the spec promises the opposite ("Requests on different boards SHALL NOT wait for each other") without a test for it.
- Location: `design.md` > Risks / Trade-offs; `specs/board-columns/spec.md` > Requirement "Positions stay dense under concurrent requests"
- Current text: "[A request holds the board lock for its whole transaction, so slow writes on one board queue up] → Each write is a handful of statements. Only writes to the same board wait; reads never do."
- Problem: Two parts of this are wrong. A move or delete is one bulk `UPDATE` over up to n rows, and n has no cap (D9), so a write is not always small. And a request waiting for the board lock already holds a pool connection, so once one board has as many queued writers as the pool has connections (5 on dev, Hikari's default of 10 on prod), requests on other boards and all reads wait for a connection too. Nothing bounds the wait: no lock timeout, no statement timeout, no rate limit on these endpoints.
- Failure scenario: A user adds tens of thousands of columns to one board with a script, then keeps 20 moves from the last position to position 0 in flight. Each move rewrites every row while the other 19 hold connections and wait. Other users' requests queue for a connection, hit Hikari's 30 second timeout and return 500.
- Proposed fix: This needs the user's choice. D9 (no caps) stays as it is in both options.
  - Recommended: bound the lock wait. In `design.md`, add to "Every write runs under the board's row lock": "`lockByIdAndOwner` sets a lock timeout (`jakarta.persistence.lock.timeout`, value from `cardigan.board.lock-timeout`). A request that doesn't get the lock in that time fails and ends as 500 `INTERNAL_ERROR`; no new error code." The timeout value is the user's to pick. Replace the risk row with: "[A request waiting for the board lock holds a pool connection, and a move or delete shifts up to n rows with no cap on n (D9). Enough queued writes on one large board can use up the pool and delay every user] → The lock wait is bounded by `cardigan.board.lock-timeout`, so queued requests give their connection back. A single account can still slow the API with steady load; a per-user limit or a column cap would need its own decision." In `specs/board-columns/spec.md`, replace "Requests on different boards SHALL NOT wait for each other." with "Requests on different boards SHALL NOT wait for each other's board lock, and a request SHALL NOT wait for a board lock longer than the configured lock timeout."
  - Alternative: accept the risk. Use the same risk row without the timeout sentence, marked "Accepted", and change the spec sentence to "Requests on different boards SHALL NOT wait for each other's board lock."
- Upstream: no

### S2: No scenario covers a foreign column ID under the caller's own board, and column delete has no isolation scenario
**Status:** applied
- Severity: major - isolation is the main security promise, each of rename, move and delete is a separate code path, and the one destructive path (delete) is not pinned by any scenario.
- Location: `specs/board-columns/spec.md` > Requirement "Columns are reachable only through their own board"
- Current text: "#### Scenario: Move another user's column / - **WHEN** user B sends a move for user A's board and column IDs / - **THEN** the response is 404 `NOT_FOUND` and no position changes"
- Problem: Every foreign-user scenario puts A's board ID in the path, so the board lookup already returns 404 and the column lookup is never reached. The only wrong-board scenario is a rename between two boards of the same user. The case where the board resolves (B's own board) and the column belongs to A is untested for rename, move and delete. Task 5.3 builds the E2E tests from the scenarios, and task 4.2 names "a column under another board" once, not per operation.
- Failure scenario: `ColumnService.delete` locks B's board, then calls `deleteById(columnId)` and shifts positions `WHERE board_id = <B's board>`. B sends `DELETE /api/v1/boards/{B's board}/columns/{A's column}`. A's column is deleted, A's board gets a gap, B's board gets shifted, and all tests are green.
- Proposed fix: Add these scenarios to the requirement:
  ```
  #### Scenario: Delete another user's column under an own board
  - **WHEN** user B sends a DELETE for user A's column ID under the path of user B's own board
  - **THEN** the response is 404 `NOT_FOUND`, user A's board still has the column, and no position changes in either board

  #### Scenario: Move another user's column under an own board
  - **WHEN** user B sends a move with `{ "position": 0 }` for user A's column ID under the path of user B's own board
  - **THEN** the response is 404 `NOT_FOUND` and no position or `updatedAt` changes in either board

  #### Scenario: Rename another user's column under an own board
  - **WHEN** user B sends `{ "name": "X" }` for user A's column ID under the path of user B's own board
  - **THEN** the response is 404 `NOT_FOUND` and user A's column is unchanged

  #### Scenario: Delete a column under the wrong own board
  - **WHEN** a user owns the boards X and Y and sends a DELETE for a column of X under the path of Y
  - **THEN** the response is 404 `NOT_FOUND`, and X and Y keep all their columns and positions
  ```
  In `tasks.md` 4.2, replace "a column under another board, in another owner's board, and an unknown ID" with "for each of rename, move and delete: a column under another board of the same owner, another owner's column under the caller's own board, a column in another owner's board, and an unknown ID".
- Upstream: no

### D1: No operation documents 400 `VALIDATION_FAILED`
**Status:** applied
- Severity: major - the committed contract would be wrong for five operations, and no scenario or test in the change would catch it.
- Location: `design.md` > OpenAPI (also `specs/boards/spec.md` and `specs/board-columns/spec.md` > "... operations in the API document", and `tasks.md` 6.1)
- Current text: "`@ApiErrors(409, CONFLICT)` on `createBoard`; `@ApiErrors(404, NOT_FOUND)` on every operation with a path ID."
- Problem: `CardiganOpenApiCustomizer` adds only `MALFORMED_REQUEST`, `PAYLOAD_TOO_LARGE` and `UNSUPPORTED_MEDIA_TYPE` for operations with a body. `VALIDATION_FAILED` only appears through `@ApiErrors(400, ErrorCode.VALIDATION_FAILED)`. The main spec `api-contract-snapshot` says each status lists the codes the operation can return. `be-auth` design line 172 does list `400 VALIDATION_FAILED` per operation; this design doesn't.
- Failure scenario: the snapshot shows `400: "Error codes: MALFORMED_REQUEST"` for `createBoard`, `updateBoard`, `createColumn`, `updateColumn` and `moveColumn`. Clients generate from it and never see that these operations return field errors (including `OUT_OF_RANGE` on move). `OpenApiSnapshotTest` passes because the snapshot is regenerated from the same code.
- Proposed fix: in `design.md` > OpenAPI, replace the quoted sentence with: "`@ApiErrors(400, VALIDATION_FAILED)` on the five operations with a request body (`createBoard`, `updateBoard`, `createColumn`, `updateColumn`, `moveColumn`), because the customizer adds only `MALFORMED_REQUEST` for bodies; `@ApiErrors(409, CONFLICT)` on `createBoard`; `@ApiErrors(404, NOT_FOUND)` on every operation with a path ID." In `specs/boards/spec.md`, append to "Board operations in the API document": "`createBoard` and `updateBoard` SHALL document 400 with the codes `VALIDATION_FAILED` and `MALFORMED_REQUEST`." In `specs/board-columns/spec.md`, append to "Column operations in the API document": "`createColumn`, `updateColumn` and `moveColumn` SHALL document 400 with the codes `VALIDATION_FAILED` and `MALFORMED_REQUEST`." In `tasks.md` 6.1, add to the review list: "the 400 response of the five body operations lists `VALIDATION_FAILED, MALFORMED_REQUEST`". Alternative: make the customizer add `VALIDATION_FAILED` for every operation with a body, which is a `:common` change that also affects `be-auth` and `be-labels`.
- Upstream: no

### D2: The module `@SpringBootTest` has no described test context, and `BoardTestApplication` alone can't start one
**Status:** applied
- Severity: major - the concurrency tests in task 4.3 can't start as described; it would be found during implementation, but `be-boards` and `be-labels` could each solve it differently in the same module.
- Location: `design.md` > Tests; `tasks.md` 1.1 and 4.3
- Current text: "Module integration (`:board`, `@SpringBootTest`): concurrent creates of the same key, concurrent column creates, and a concurrent move and delete, each ending with dense positions."
- Problem: this is the first module-level `@SpringBootTest` in the repo. `BoardTestApplication`, modeled on `UserTestApplication`, scans only `com.plcoding.cardigan.board`. The `Clock` bean lives in `:app` (`ApplicationConfig`), and `RequestValidator` is a `@Component` in `common.api.validation`. Neither is in the context, but the services need the `Clock` and the scanned controllers need `RequestValidator`. `AGENTS.md` names a `<Feature>TestConfig`, but no artifact mentions `BoardTestConfig`. Task 1.1 also says the `:common` test fixtures are added "as in `user/build.gradle.kts`"; that file doesn't add them, `SpringModuleConventionPlugin` does for every module.
- Failure scenario: the 4.3 tests fail at context start with a missing `Clock` or `RequestValidator` bean. The implementer picks an ad-hoc fix, and `be-labels` task 4.2 picks a different one.
- Proposed fix: add to `design.md` > Tests, after the "Module integration" bullet: "Module integration tests use `@SpringBootTest(webEnvironment = NONE)` with `@ActiveProfiles("test")` and import `PostgresTestConfig` and `BoardTestConfig`. `BoardTestConfig` (`board/src/test`, next to `BoardTestApplication`) is a `@TestConfiguration` that provides a `MutableClock` as the `Clock`, a `DatabaseCleaner`, and imports `RequestValidator`, because `BoardTestApplication` scans only the `:board` package. Each test cleans `board_service` in `@BeforeEach`. `be-labels` reuses it." Change `tasks.md` 1.1 to: "Add `spring-boot-starter-data-jpa-test` as a test dependency in `board/build.gradle.kts` (as in `user/build.gradle.kts`; the `:common` test fixtures already come from the convention plugin), plus `BoardTestApplication` and `BoardTestConfig` in `board/src/test`, unless `be-labels` already added them. Verify with an empty `@DataJpaTest` context and an empty `@SpringBootTest` context starting against Testcontainers Postgres." Alternative: `BoardTestApplication` sets `scanBasePackages` to include `com.plcoding.cardigan.common.api.validation`.
- Upstream: no

### T1: "Simultaneous move and delete" has no fixed setup and accepts almost any outcome
**Status:** applied
- Severity: major - the only scenario for the move/delete race does not say which columns or which target, so a test built from it can pass without the lock.
- Location: `specs/board-columns/spec.md` > Requirement "Positions stay dense under concurrent requests" > Scenario "Simultaneous move and delete"
- Current text: "**WHEN** a move and a delete of two different columns of one board arrive at the same time / **THEN** both succeed or the move returns `OUT_OF_RANGE`, and the remaining positions are dense"
- Problem: Missing precondition and an open result. The column count and move target are not given, so `OUT_OF_RANGE` may be impossible in one test and possible in another. "Dense" alone does not check that the result equals one of the two serial orders.
- Failure scenario: A test moves C to position 0 and deletes B in a 3-column board. `OUT_OF_RANGE` can never happen there. An implementation that applies the move on a stale read can still end with dense positions in the wrong order, and the test passes.
- Proposed fix (replace the scenario):
  ```
  #### Scenario: Simultaneous move and delete
  - **WHEN** a board has the columns A, B, C, and a move of A to position 2 and a delete of B arrive at the same time
  - **THEN** the delete returns 204, and the result is one of exactly two: the move returns 200 and the board holds C, A at the positions 0, 1; or the move returns 400 `OUT_OF_RANGE` with `max` 1 and the board holds A, C at the positions 0, 1
  ```
- Upstream: no
- Test sketch:
  - Level: integration (`@SpringBootTest` in `:board`, task 4.3)
  - Arrange and act: create A, B, C, start both calls from two threads behind one latch, repeat the run several times.
  - Passes when: each run ends in one of the two listed states.
  - Fails when: the service skips the board lock. The move shifts B and C down and sets A to 2, the delete (which loaded B at position 1) then shifts A to 1, and the board holds C and A both at position 1.

### T2: Timestamp scenarios don't say that time passed, so they pass on a frozen test clock
**Status:** applied
- Severity: major - ten scenarios assert "changed" or "unchanged" `updatedAt` while the test clock stands still, so they pass for an implementation that never sets it, or always sets it.
- Location:
  - `specs/boards/spec.md` > "Rename a board" > "Rename", "Only a key in the body"
  - `specs/boards/spec.md` > "An unchanged board name does not move the updated time" > "Empty patch", "Case-only rename"
  - `specs/boards/spec.md` > "List boards" > "Column change moves a board to the front"
  - `specs/board-columns/spec.md` > "Rename a column" > "Rename"
  - `specs/board-columns/spec.md` > "The move range follows the current column count" > "Position equal to the column count"
  - `specs/board-columns/spec.md` > "Delete a column" > "Middle column deleted"
  - `specs/board-columns/spec.md` > "Changes that change nothing move no updated time" > "Empty patch", "Move to the current position"
- Current text (examples): "**WHEN** a user sends `{ "name": " Platform " }` for their board `Roadmap` / **THEN** ... `updatedAt` set to the time of the request"; "**WHEN** a user has the boards A and B, B was updated last, and the user then renames a column of A"; "**WHEN** a column is at position 1 and a user moves it to position 1"
- Problem: Missing precondition. `E2eTest` uses a `MutableClock` that only moves when a test advances it. Other scenarios in the same specs say "one minute after the board's last change"; these don't. Task 5.2 mentions moving the clock, but the scenarios are the contract.
- Failure scenario: `BoardE2eTest` creates a board and renames it at the same clock instant. `createdAt` equals `updatedAt` before and after, so the test passes even if rename never writes `updatedAt`. For the list scenario, A and B get the same `updatedAt` and the order falls to the random ID.
- Proposed fix (replace the WHEN lines):
  - boards "Rename": `- **WHEN** a user sends { "name": " Platform " } for their board Roadmap one minute after it was created`
  - boards "Only a key in the body": `- **WHEN** a user sends { "key": "NEW" } for their board with the key CRD one minute after its last change`
  - boards "Empty patch": `- **WHEN** a user sends {} for their board one minute after its last change`
  - boards "Case-only rename": `- **WHEN** a board is named roadmap and, one minute after its last change, a user sends { "name": "Roadmap" }`
  - boards "Column change moves a board to the front": `- **WHEN** a user creates board A, creates board B one minute later, and one minute after that renames a column of A to a new name` (THEN unchanged)
  - columns "Rename": `- **WHEN** a user sends { "name": "QA" } for their column Review one minute after it was created`
  - columns "Position equal to the column count": `- **WHEN** a board has 3 columns and, one minute after its last change, a column is moved to position 3`
  - columns "Middle column deleted": `- **WHEN** a board has the columns A, B, C and a user deletes B one minute after the board's last change`
  - columns "Empty patch": `- **WHEN** a user sends {} for their column one minute after the board's last change`
  - columns "Move to the current position": `- **WHEN** a column is at position 1 and, one minute after the board's last change, a user moves it to position 1`
  - Add under boards "List boards":
    ```
    #### Scenario: A column change that changes nothing keeps the order
    - **WHEN** a user creates board A, creates board B one minute later, and one minute after that renames a column of A to its current name
    - **THEN** the list returns B before A
    ```
- Upstream: no
- Test sketch:
  - Level: E2E (`:app`, `clock.advanceBy(Duration.ofMinutes(1))`)
  - Arrange and act: create the board at `START`, advance the clock one minute, send the request.
  - Passes when: `updatedAt` is `START` plus one minute for an effective change and `START` for a no-op.
  - Fails when: rename never sets `updatedAt`, or the service touches the board on every PATCH.

### T3: "Stored together or not at all" has no scenario that can fail
**Status:** applied
- Severity: major - the atomicity promise of D5 ("never a half-created board") is untested; the one scenario under it is rejected before anything is written.
- Location: `specs/boards/spec.md` > Requirement "A new board starts with three columns" > Scenario "Rejected create stores no columns"
- Current text: "**WHEN** a board create is rejected with 409 `CONFLICT` for a used key / **THEN** no board and no column is stored"
- Problem: Test that cannot fail. The design checks `existsByOwnerIdAndKey` first, and the race path fails on the board insert itself. In both cases no column insert was ever attempted, so a create without a transaction passes too.
- Failure scenario: `BoardService.create` loses `@Transactional`. A column insert fails, the board row stays with zero or one column, and every test is green.
- Proposed fix (add to the same requirement; keep the existing scenario):
  ```
  #### Scenario: Column write fails
  - **WHEN** storing the third default column fails during a board create
  - **THEN** the create fails, and neither the board nor any of its columns is stored
  ```
  And add to task 4.3 in `tasks.md`: "a board create whose third column save throws (a `ColumnRepository` test double that wraps the real adapter), ending with no board row and no column row".
- Upstream: no
- Test sketch:
  - Level: integration (`@SpringBootTest` in `:board`, a hand-written failing wrapper around the real `ColumnRepository`, no mocks)
  - Arrange and act: make the wrapper throw on the third save, call `BoardService.create`.
  - Passes when: the call throws and `boards` and `columns` are both empty.
  - Fails when: `create` is not one transaction. The test sees one board row and two column rows.

### T4: "Board with columns" cannot detect a missing sort
**Status:** applied
- Severity: minor - the ordering promise for `GET` passes without an `ORDER BY`, but the persistence test in task 3.1 partly covers it.
- Location: `specs/boards/spec.md` > Requirement "Get a board" > Scenario "Board with columns"
- Current text: "**WHEN** a user requests their board whose columns `Done`, `To do`, `In progress` have the positions 2, 0, 1 / **THEN** the response is 200 and `columns` holds `To do`, `In progress`, `Done` in that order"
- Problem: This state is exactly what board create produces, in insertion order. A query without a sort returns insertion order.
- Failure scenario: The column query in `get` has no order clause. A new board still returns `To do`, `In progress`, `Done`, and the test passes. After the first move, clients see columns in the wrong order.
- Proposed fix (replace the scenario):
  ```
  #### Scenario: Board with columns
  - **WHEN** a user creates a board, moves its column `Done` to position 0, and then requests the board
  - **THEN** the response is 200 and `columns` holds `Done`, `To do`, `In progress` in that order with the positions 0, 1, 2
  ```
- Upstream: no
- Test sketch:
  - Level: E2E
  - Arrange and act: `POST /boards`, move `Done` to position 0, `GET /boards/{id}`.
  - Passes when: `columns` is `Done`, `To do`, `In progress`.
  - Fails when: the query has no sort, or sorts by `createdAt`.

### T5: The locking promises have no deterministic scenario
**Status:** applied
- Severity: minor - "one after the other" is only tested by races that may not hit, and "different boards don't wait" has no scenario at all.
- Location: `specs/board-columns/spec.md` > Requirement "Positions stay dense under concurrent requests"
- Current text: "Column creates, moves and deletes on the same board SHALL be applied one after the other, each on the result of the one before. ... Requests on different boards SHALL NOT wait for each other."
- Problem: Requirement text with no scenario (second sentence), and uncontrolled timing (first sentence).
- Failure scenario: The lock is replaced by one global mutex or a table lock. All three existing scenarios pass, and every user's column writes queue behind each other.
- Proposed fix (add to the requirement, and add both cases to task 4.3):
  ```
  #### Scenario: Write waits for an open write on the same board
  - **WHEN** a transaction holds the row lock of board X and a column create for X arrives
  - **THEN** the create does not complete while the lock is held, and after the lock is released it returns the position after the board's last column

  #### Scenario: Write on another board does not wait
  - **WHEN** a transaction holds the row lock of board X and a column create for board Y arrives
  - **THEN** the create for Y completes while the lock on X is still held
  ```
- Upstream: no
- Test sketch:
  - Level: integration (`@SpringBootTest` in `:board`, `TransactionTemplate` plus a latch, Awaitility for the waits)
  - Arrange and act: thread 1 calls `lockByIdAndOwner(X)` in an open transaction and waits on a latch; thread 2 calls `ColumnService.create` for X or Y.
  - Passes when: the create on X is still pending after a short wait and finishes after the latch opens; the create on Y finishes while the latch is closed.
  - Fails when: create skips the lock, or the implementation locks the table or uses a global mutex.

### T6: Documented error statuses and required response fields have no scenario
**Status:** applied with edits (the "Error statuses" scenarios also carry D1's 400 codes; tests go into `OpenApiSnapshotTest` per D3)
- Severity: minor - `OpenApiSnapshotTest` compares against a file that this change rewrites, so only an explicit scenario can catch a missing `@ApiErrors` or a non-required response field.
- Location: `specs/boards/spec.md` > "Board operations in the API document"; `specs/board-columns/spec.md` > "Column operations in the API document"; also "Board representations" and "Column representation" ("No field SHALL be nullable or absent")
- Current text: "`createBoard` SHALL document 409 `CONFLICT`, and `getBoard`, `updateBoard` and `deleteBoard` SHALL document 404 `NOT_FOUND`." / "...each documenting 404 `NOT_FOUND`."
- Problem: The scenarios check operationIds, tags and request fields, but not the error statuses, and not that every response property is required.
- Failure scenario: `@ApiErrors(404)` is forgotten on `deleteColumn`. The snapshot is regenerated without it, both tests pass, and the generated mobile client has no typed 404 for that call.
- Proposed fix: add to `specs/boards/spec.md`:
  ```
  #### Scenario: Error statuses
  - **WHEN** the snapshot is inspected
  - **THEN** `createBoard` documents 409 with the code `CONFLICT`, `getBoard`, `updateBoard` and `deleteBoard` document 404 with the code `NOT_FOUND`, and `listBoards` and `createBoard` document no 404

  #### Scenario: Response fields are required
  - **WHEN** the board response schemas are inspected
  - **THEN** every property of the full board, the list item and the board list is required, and none is nullable
  ```
  Add to `specs/board-columns/spec.md`:
  ```
  #### Scenario: Error statuses
  - **WHEN** the snapshot is inspected
  - **THEN** each of the four column operations documents 404 with the code `NOT_FOUND`

  #### Scenario: Response fields are required
  - **WHEN** the column response schemas are inspected
  - **THEN** every property of the column and the column list is required, and none is nullable
  ```
- Upstream: no
- Test sketch:
  - Level: contract (the reviewer named `ApiDocsE2eTest`; see D3, the document is only reachable in `OpenApiSnapshotTest`)
  - Arrange and act: load the document and read `responses` of each operation and `required` of each schema.
  - Passes when: the listed statuses and required lists are present.
  - Fails when: `@ApiErrors` is missing on one operation, or a DTO property is declared nullable.

### T7: Default columns' timestamps are promised but not checked
**Status:** applied
- Severity: minor - small promise with a unit test in task 4.1, but no scenario.
- Location: `specs/boards/spec.md` > Requirement "A new board starts with three columns" > Scenario "Default columns in the response"
- Current text: "**THEN** the 201 body's `columns` holds exactly `To do`, `In progress`, `Done` with the positions 0, 1, 2, each with its own `id`"
- Problem: The requirement says the columns get "the board's creation time as their `createdAt` and `updatedAt`". No scenario checks it.
- Failure scenario: The columns' timestamps come from a second `clock.instant()` call or a database default. With the real clock they differ from the board's by a few milliseconds, which no test sees.
- Proposed fix (replace the THEN line): `- **THEN** the 201 body's columns holds exactly To do, In progress, Done with the positions 0, 1, 2, each with its own id, and each with createdAt and updatedAt equal to the board's createdAt`
- Upstream: no
- Test sketch:
  - Level: unit (`BoardService` with a clock fake that advances one millisecond per `instant()` call), plus the E2E assertion
  - Passes when: all four `createdAt` values and all four `updatedAt` values are equal.
  - Fails when: the service reads the clock once per row.

### T8: The unpaired-surrogate rule for keys has no scenario
**Status:** applied
- Severity: minor - covered by a unit test in task 2.1, but the spec names the case and never shows it.
- Location: `specs/boards/spec.md` > Requirement "Key validation codes"
- Current text: "A control character or unpaired surrogate in a key SHALL be `INVALID_FORMAT` too, never `INVALID_CHARACTER`."
- Problem: Only the control character has a scenario ("Tab inside the key"). The surrogate path goes through different code in `:common` (`checkText`), so it can break alone.
- Failure scenario: `@ValidBoardKey` rejects surrogates through the shared text check before the key check, and the response says `INVALID_CHARACTER`.
- Proposed fix (add to the requirement):
  ```
  #### Scenario: Unpaired surrogate in the key
  - **WHEN** a board is created with the key `"C\ud800D"`
  - **THEN** the field error for `key` is `INVALID_FORMAT`
  ```
- Upstream: no
- Test sketch:
  - Level: E2E (JSON body with the escaped surrogate), and unit on `checkBoardKey`
  - Passes when: 400 `VALIDATION_FAILED` with `key` `INVALID_FORMAT`.
  - Fails when: the constraint delegates to `@ValidText` first.

### T9: "Real code" in the module-boundaries scenario can't be decided by a test
**Status:** applied with edits (uses the wording `be-labels` now carries, so both deltas stay identical)
- Severity: minor - the wording is inherited from the main spec and the risk is low, but the new THEN is a judgment call.
- Location: `specs/module-boundaries/spec.md` > Requirement "Module layer packages exist from the start" > Scenario "No placeholder classes"
- Current text: "**THEN** every source file holds real code, and a layer package without real code holds no source file"
- Problem: Vague term. "Real code" has no definition. `be-labels` carries the same text, so both deltas need the same wording.
- Failure scenario: A later chunk adds `object Placeholder` to keep a package after removing `.gitkeep`. Nobody can point to a rule that it breaks.
- Proposed fix (replace the THEN line here; the same line in `openspec/changes/be-labels/specs/module-boundaries/spec.md` is outside this change): `- **THEN** every declaration in a source file is used by the build's production or test code, and a layer package directory with no such source file holds only a .gitkeep file`. Alternative: keep the current wording and accept it as a review-only check.
- Upstream: no
- Test sketch:
  - Level: unit (a file-system test in `:app`), plus review for the "is used" half
  - Passes when: each layer package directory has at least one `.kt` file or exactly one `.gitkeep`, never both and never neither.
  - Fails when: a package keeps its `.gitkeep` next to code, or an empty package holds a `.kt` file with an unused type.

### A1: `getBoard` assumes the board and its columns come from one snapshot
**Status:** applied
- Severity: minor - wrong response only in a narrow race, and the client recovers on its next call.
- Location: `design.md` > Board operations
- Current text: "**Get**: load the board by ID and owner, then its columns ordered by position, returned as `BoardWithColumns`."
- Assumption: The two statements see the same data. Reads take no lock and run under `READ COMMITTED`, where each statement gets its own snapshot.
- Problem: Nothing says the read is one snapshot. If a board delete commits between the two statements, the board row is found and the cascade has already removed its columns.
- Failure scenario: Device A opens a board while device B deletes it. A gets 200 with `columns: []`, which is a valid state, so the client shows an empty board instead of handling a 404.
- Proposed fix: replace the bullet with: "**Get**: load the board by ID and owner, then its columns ordered by position, returned as `BoardWithColumns`. The method is `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`, so both statements read one snapshot and a board deleted in between is never returned with an empty column list." Alternative: keep `READ COMMITTED` and add this to Risks as accepted.
- Upstream: no

### A2: The specs assume one clock read per write, taken under the lock
**Status:** applied
- Severity: minor - timestamps can differ by milliseconds in production, and the tests can't see it because `MutableClock` is fixed.
- Location: `design.md` > Position changes
- Current text: "\"Touch the board\" means setting `updatedAt = clock.instant()` on the locked board and saving it."
- Assumption: Every timestamp written by one request is the same instant, and that instant is not older than the previous writer's.
- Problem: The specs require equal values ("the board's `updatedAt` equals the new column's `createdAt`", "C's and the board's `updatedAt` are the time of the move"). The design reads the clock in "touch the board" and says nothing about the column's own timestamp or about when the read happens relative to the lock.
- Failure scenario: An implementer calls `clock.instant()` once for the column and again in "touch the board". In production the two values differ by a millisecond. Or the clock is read at method start, the request waits for the lock, and it then writes an `updatedAt` older than the one the previous writer committed.
- Proposed fix: replace the sentence with: "Each write method reads `clock.instant()` once, right after the board lock is granted (for board create: at the start), and uses that one value for every `createdAt` and `updatedAt` it sets. \"Touch the board\" means setting the locked board's `updatedAt` to that value and saving it." Add to task 4.3: "The concurrency tests also assert that the board's `updatedAt` equals the newest column timestamp written."
- Upstream: no

### A3: Path IDs assume Spring's UUID conversion rejects every non-canonical form
**Status:** applied with edits (the isolation requirement now says "canonical 8-4-4-4-12 form"; task 1.3 also gets an E2E case)
- Severity: minor - only reachable with a hand-crafted path, and only for the caller's own data.
- Location: `design.md` > Context
- Current text: "the handling of malformed path UUIDs (a `@PathVariable` of type `UUID` that can't be converted becomes 404)"
- Assumption: Everything that isn't a canonical 8-4-4-4-12 UUID fails conversion.
- Problem: Spring converts with `UUID.fromString`, which accepts short forms such as `1-2-3-4-5`. `:common` already has `parseCanonicalUuid`, which rejects them for body UUIDs and the token subject. This change is the first production use of path UUIDs, and the specs only test `abc`.
- Failure scenario: A board has the ID `0a2b8c1e-0a4d-4e2f-8b1a-0c9d7e6f5a43`. `DELETE /api/v1/boards/a2b8c1e-a4d-4e2f-8b1a-c9d7e6f5a43` (leading zeros dropped) deletes it instead of returning 404.
- Proposed fix: add a fourth item to "Changes to `:common`" in `design.md`: "4. **Canonical path UUIDs.** A `Converter<String, UUID>` registered in `WebConfig` that uses `parseCanonicalUuid` and throws `IllegalArgumentException` for anything else, so short forms end as 404 like `abc`." Add a scenario to "Boards are isolated per user": "#### Scenario: Short-form board ID / - **WHEN** a user sends a GET for their own board with the leading zeros of an ID group left out / - **THEN** the response is 404 `NOT_FOUND`". Add the converter and its test to task 1.3. Alternative: accept the lenient parsing and record it under Risks.
- Upstream: no

### D3: Task 6.1 puts the API-document assertions in a test that can't see the document
**Status:** applied
- Severity: minor - wrong reference, found on the first attempt, but it leaves the spec scenarios without a clear home.
- Location: `tasks.md` > 6.1
- Current text: "Verify with `OpenApiSnapshotTest` passing and `ApiDocsE2eTest` cases for the scenarios of "Board operations in the API document" and "Column operations in the API document"."
- Problem: `ApiDocsE2eTest` extends `E2eTest`, where the API docs are off; its only test asserts that `/v3/api-docs` returns 404. The document assertions live in `OpenApiSnapshotTest`, which enables the docs in its own context and already has a `current user operation` test. `be-labels` task 6.1 has the same mistake.
- Failure scenario: a case added to `ApiDocsE2eTest` gets 404 for the document, or the implementer enables API docs in the E2E context and breaks the existing "no API docs are served at runtime" test.
- Proposed fix: replace the sentence with: "Verify with `OpenApiSnapshotTest` passing, including new cases in `OpenApiSnapshotTest` (next to `current user operation`) for the scenarios of "Board operations in the API document" and "Column operations in the API document"."
- Upstream: no

### D4: design.md describes `be-labels` as it was before its last update
**Status:** applied
- Severity: minor - misleading context, and task 1.4 has a branch built on it.
- Location: `design.md` > Context, and > Risks / Trade-offs
- Current text: "Its design still places `@NotNullWhenPresent` in `:board`; the user has since moved it to `:common` for both changes (D21)." and "[`be-labels`' design still shows `@NotNullWhenPresent` in `:board`] → D21 overrides it for both changes. The tasks check for an existing constraint in either place before adding one."
- Problem: `be-labels` was updated in commit 6d72e88. Its design and task 5.2 now place the constraint in `:common` `api/validation`. Both statements here are false.
- Failure scenario: an implementer believes the two changes disagree, and searches `:board` or asks the user about a conflict that no longer exists.
- Proposed fix: in Context, replace the sentence with: "Its design places `@NotNullWhenPresent` in `:common` `api/validation` as well (D21, `be-labels` D8), so whichever change is implemented first adds it." Delete the quoted Risks bullet. Task 1.4 can keep its "exists only in `:board`: stop and ask" branch as a safety net.
- Upstream: no

### D5: The "sent properties" base type is named but not defined in either change
**Status:** applied with edits (uses the `PatchRequest` shape `be-labels`' design now defines, not `wasSent`)
- Severity: minor - the scenarios "update request has only `name`" and "no property is nullable" would catch a bad result, but the mechanism is left to whoever implements first.
- Location: `design.md` > Changes to `:common`, item 2
- Current text: "If not, this change adds it as `be-labels`' design describes it: the request records which properties were present in the JSON, and the class-level constraint reports `REQUIRED` at the path of a property sent as `null`."
- Problem: `be-labels`' design is just as short. Open points: the type's name, how presence is recorded with Jackson 3 and a Kotlin constructor-bound DTO, and that the recording member must stay out of the JSON schema. springdoc builds the schema from all bean properties, so an inherited public property would show up in `UpdateBoardRequest`.
- Failure scenario: the base type exposes its set of sent names as a public property. The snapshot gets an extra property on `UpdateBoardRequest` and `UpdateColumnRequest`.
- Proposed fix: append to item 2: "The base type is an abstract class `PatchRequest` in `common.api.validation`. It records the names of the JSON properties that were present through a mechanism of the implementer's choice, exposes only `fun wasSent(property: String): Boolean`, and has no public property, so nothing from it appears in the JSON or in the OpenAPI schema. `UpdateBoardRequest` and `UpdateColumnRequest` extend it and carry `@NotNullWhenPresent` on the class. A unit test in `:common` generates the document for a test DTO that extends it and asserts the schema has only the DTO's own properties." If the name `PatchRequest` isn't wanted, pick another, but write the same name into `be-labels`' design.
- Upstream: no

### D6: Timestamp column type differs from the existing convention
**Status:** applied
- Severity: minor - no effect on dev or tests, but the data model table is the source for the hand-written baseline migration.
- Location: `design.md` > Data model (both tables)
- Current text: "| `created_at` | `timestamp` | not null |" and "| `updated_at` | `timestamp` | not null |"
- Problem: the archived `backend-common-utilities` design says "Columns are `timestamptz`" and defines `users.created_at timestamptz`. `UserEntity` maps `Instant`, which Hibernate creates as `timestamp with time zone`. The board entities will follow the same pattern. `be-labels` has the same wording.
- Failure scenario: the baseline migration is written from this table with `timestamp`, and `prod` fails `ddl-auto: validate` on start, or stores times without a zone.
- Proposed fix: change the type to `timestamptz` in all four rows.
- Upstream: no

The design-contract reviewer also suggested one line that is not a finding: add a Risks bullet for `be-auth`, which touches `E2eTest`, the `:common` base exceptions, `CommonExceptionHandler`, `TestApiController`, `AGENTS.md` and the snapshot. Its edits are additive, and `be-auth` design line 66 already expects `FieldOutOfRangeException`.

## Security analysis

Stated security requirements: all nine endpoints need a bearer token; boards are isolated per user; columns are reachable only through their own board; foreign, missing and malformed IDs give the same 404, before field validation; the owner is the token subject; key uniqueness and dense positions hold under concurrent requests; the database guards the unique key, `position >= 0` and the cascade.

### Trust boundaries

**Unauthenticated network request to the nine endpoints.** Attacker: anyone, no token. Gains nothing. `CLAUDE.md` requires authentication for everything under `/api/v1` outside a `PublicRoutes` bean, and this change adds none. The spec pins it with "No token" on `createBoard`; deny-by-default covers the other eight.

**Token subject to board owner.** Attacker: holder of a valid token sending `ownerId` or `id` in the body. Gains nothing. The request DTO has only `name` and `key`, unknown fields are ignored, and `requestUserId` parses `sub` from the verified JWT.

**Path IDs to another user's data.** Attacker: authenticated user B who knows IDs of user A's board or column. Path 1 (A's board ID) is stopped by "Boards are isolated per user" with a scenario per operation, plus `findByIdAndOwnerId` / `lockByIdAndOwner`. The lock query filters by owner, so B can't take a row lock on A's board. Path 2 (B's own board in the path, A's column ID) is covered by requirement text and `findByIdAndBoardId`, but by no scenario. See S2.

**404 as an existence oracle.** Attacker: authenticated user probing IDs. Gains nothing. `CommonExceptionHandler.handleNotFound` returns the fixed `NOT_FOUND` problem and ignores the exception message. Body parsing errors come before the 404 for existing and missing boards alike, field validation after it. Foreign and missing boards run the same single query.

**Request body to validator, service and database.** Attacker: authenticated user sending hostile JSON. Gains nothing. `position` stays a `BigInteger` until `requireInRange`. `JacksonConfig` turns off float-as-int and scalar coercion. The 256KB `RequestSizeLimitFilter` bounds the body. `normalizeBoardKey` uppercases only ASCII `a`–`z`. Control characters are rejected for names and keys. All queries use bound parameters.

**Concurrent requests to shared state (board lock, connection pool).** Attacker: any authenticated user on their own board. Grows one board to a very large column count, then keeps more writes in flight than the pool has connections. Nothing stops it. See S1.

**Direct database writes past the application.** Not an external attacker: a bug, a manual fix or the hand-written baseline migration. Stopped by `boards_owner_key_unique`, the `position >= 0` check and `ON DELETE CASCADE`, each with a direct-database scenario and a `@DataJpaTest` in task 3.1. Duplicate or gapped positions are not guarded by the database; that is settled in D18.

### Test versus production

**Boards are isolated per user.** E2E with two users, real tokens, real Postgres. Nothing relevant differs in production. No gap.

**Columns are reachable only through their own board.** The tests cover wrong-board for rename (one user's two boards) and foreign user for create and move with A's board ID in the path. An attacker picks the combination the tests skip: own board in the path, foreign column ID, on delete or move. Closed by S2.

**Key uniqueness holds under concurrent requests.** Tests run on the entity-built schema; production runs on a hand-written baseline, and Hibernate `validate` doesn't check unique constraints. Already closed: `CLAUDE.md` moves tests to the real migrations once the baseline exists, and the design's risk list says the constraints must be carried over.

**Positions stay dense / different boards do not wait.** Tests use a few columns and a pool at least as large as the thread count. Production has a pool of 5 (dev) or 10 (prod default), unbounded virtual threads, a network hop and no column cap. Density holds, but "Requests on different boards SHALL NOT wait for each other" breaks once one board has more queued writers than the pool has connections. Closed by S1.

**The move range follows the current column count.** No realistic gap. The count runs after the lock in the same transaction, `open-in-view` is `false`, and task 4.3 runs the race on real Postgres.

**Deleting a board removes its columns.** A missing cascade in the baseline makes board delete fail with a foreign key error (500). It fails closed. Covered by the design's risk list, task 7.2 and the move of tests to real migrations.

## Assumptions safe to leave implicit

| Assumption | Where it is implied | Why it is safe |
|---|---|---|
| The locked load returns fresh board state, not a cached entity from the controller's pre-check | design.md, "Every write runs under the board's row lock" | `spring.jpa.open-in-view: false`, so the pre-check and the write use separate persistence contexts |
| `"key": 12`, `"position": "1"` and `"position": 1.5` are `MALFORMED_REQUEST`, and unknown fields are ignored | boards and board-columns specs | `JacksonConfig` turns off scalar coercion and float-as-int and ignores unknown properties |
| GET on `/boards/{boardId}/columns` is 404 `ROUTE_NOT_FOUND`, not 405 | board-columns "No column list endpoint" | `CommonExceptionHandler` maps `HttpRequestMethodNotSupportedException` to `ROUTE_NOT_FOUND`; shared `api-conventions` "Wrong method" states it |
| Transactions run at `READ COMMITTED` | design.md, lock section | Stated in the design, and it is the Postgres and Supabase default |
| All writers share one database, so the row lock also works with more than one app instance | D13 | The lock lives in Postgres, not in the JVM |
| A waiting writer has no lock timeout and holds a pooled connection | D13, design Risks | The assumptions reviewer judged waits short because writes do only a few statements. The security reviewer disagrees for large boards; see S1 |
| A crash in the middle of a write leaves no half-moved columns or half-created board | "stored together or not at all", bulk shifts | Each write is one transaction; Postgres rolls it back |
| The system clock doesn't jump backwards | "Column change moves a board to the front" | A backward step only misorders the board list for a moment |
| Stored timestamps have millisecond precision, so database order matches wire order | List order | Main spec `backend-time` requires a millisecond clock |
| The database is UTF-8, so `varchar(n)` counts code points | design.md, Data model | Supabase and the Testcontainers image are UTF-8; a mismatch fails loudly in the persistence tests |
| `position` and `key` work as column names and in JPQL | design.md, Data model | Both are non-reserved in Postgres; a JPQL parse problem fails at context start |
| The future baseline migration keeps the name `boards_owner_key_unique` | design.md, Board operations | Tests move to the real migrations at that point, so task 3.2's test fails if the name differs |
| A retried `POST` after a lost response creates a second column, or gets 409 on the board key | Create a column, Create a board | No data is corrupted; an idempotency rule would belong to shared `api-conventions` |
| The token's `sub` is a real user | "A board's owner is the token subject" | Stated and accepted in D12 |
| `tokenWithoutUser`, `TestApiController`, `ApiDocsE2eTest` and `MutableClock` exist for the E2E tasks | tasks.md 1.2, 5.2, 6.1 | All four exist in `app/src/test` |
| Task 7.2 can reach the dev database and `.env` | tasks.md 7.2 | It fails loudly at startup if not |
| No older boards data needs migrating | proposal, Database | The tables are new and nothing is deployed to production yet |

## New abstractions

| Abstraction | Verdict |
|---|---|
| `FieldOutOfRangeException(field, min, max)` in `common.domain.exception` + handler mapping | Fine as is. Nothing existing lets a service raise a field error; reason and reuse by `be-cards` are documented (D20). |
| `@NotNullWhenPresent` + "sent properties" base type in `common.api.validation` | Keep and document. Purpose is documented, the shape is not. See D5. |
| `reject(FieldViolation)` made public | Fine as is. Documented with the rejected alternative. |
| `@ValidBoardKey` in `:board` `api/validation` | Fine as is. `@ValidText` can't give the D11 precedence. |
| `normalizeBoardKey` / `checkBoardKey` in `domain/model/BoardKey.kt` | Fine as is. Same shape as `normalizeText`/`checkText`; reuse by `be-import-export` is stated. |
| `DensePositions.kt` (`requireInRange`, `shiftForMove`) | Fine as is. Two small pure functions, scope limit stated in Non-Goals. |
| `BoardRepository.lockByIdAndOwner` | Fine as is. Documented, including the extension beyond D13. First pessimistic lock in the codebase. |
| `BoardWithColumns` read model | Fine as is. Follows from D1/D14. |
| `Board`, `Column`, `BoardRepository`, `ColumnRepository`, the three domain exceptions | Fine as is. Follow the `:user` pattern. |
| `ColumnEntity` lazy `@ManyToOne` with `@OnDelete(CASCADE)` | Fine as is. First association in the codebase; needed for the cascading foreign key (D19). |
| `BoardService` / `ColumnService`, `BoardController` / `ColumnController` | Fine as is. One per tag and resource. |
| The five response DTOs and five requests | Fine as is. Settled by D4, D14, D16. |
| `BoardTestApplication` | Fine as is. The missing `BoardTestConfig` is D2. |

## Coverage
- Artifacts reviewed: `proposal.md`, `specs/boards/spec.md`, `specs/board-columns/spec.md`, `specs/module-boundaries/spec.md`, `design.md`, `tasks.md`, with `decisions.md` as the settled decisions
- Missing artifacts: none
- Testability: 26 requirements and 89 scenarios checked; 7 requirements and 13 scenarios flagged in 9 findings. Shared `domain-board`, `domain-column`, `api-errors`, `text-length` and `domain-user` were not fetched. Nothing was run.
- Security: 7 trust boundaries and 6 invariants examined, 2 findings. The shared specs were not fetched (the store call did not run in the reviewer's shell).
- Hidden assumptions: about 45 candidates checked: 3 findings, 17 safe rows. Not applicable: platform, locale beyond text normalization, battery and disk, feature flags. Shared `text-length`, `api-errors`, `domain-user` and `api-auth-tokens` were not read in full.
- Design contract: code mode. 14 abstractions, about 20 code files, 3 main specs, and the active changes `be-labels` and `be-auth` checked for overlap. Nothing was built or run, so Hibernate and springdoc behavior is judged from the code, not observed.
- Reviewers that did not complete: none
- Findings dropped because their evidence did not exist: 0
- Findings dropped because they challenged decisions.md: 0
- Severity changes by the orchestrator: none
- Earlier report: none, so no run marks

## Applied changes
- Files changed: `proposal.md`, `design.md`, `tasks.md`, `specs/boards/spec.md`, `specs/board-columns/spec.md`, `specs/module-boundaries/spec.md`
- Lock timeout (S1): `cardigan.board.lock-timeout: 5s`. A normal write holds the lock for milliseconds, and 5 seconds stays well below Hikari's 30 second connection timeout. The design sets it with `SET LOCAL lock_timeout` before the locked select, because Postgres has no `FOR UPDATE WAIT n` and the JPA hint the reviewer named is only reliable for `NOWAIT`. A timed-out request ends as 500 `INTERNAL_ERROR`.
- Alignment with `be-labels`: that change was edited in another session while this review ran. Its `module-boundaries` delta, `PatchRequest` definition and `timestamptz` columns were taken as given, and `be-boards` now matches them. `be-labels` was not edited from here.
- Not applied: the design-contract reviewer's extra Risks bullet about `be-auth` (it was a suggestion, not a finding).
- Stale non-standard artifacts the user chose not to update: none
- Contradictions with decisions.md: none. D13 names the lock without a timeout; the timeout adds to it and does not change it.
- Accepted risks: none
- `openspec validate be-boards --strict` passes.
- The new wording has not been reviewed. Seven majors were fixed, so a re-run in a fresh session is recommended.
