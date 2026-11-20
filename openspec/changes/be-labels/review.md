# Artifact review: be-labels

**Verdict:** Ready
**Reviewed:** 2026-11-15, schema `careful-planning`, repo kind `code`

No blocker was found, none was rejected, and all four reviewers completed. All 15 findings were applied on 2026-11-15. The new wording has not been reviewed.

Counts as the reviewers returned them:

| Reviewer | Blocker | Major | Minor |
|---|---|---|---|
| Testability | 0 | 3 | 3 |
| Security | 0 | 1 | 2 |
| Hidden assumptions | 0 | 1 | 4 |
| Design contract | 0 | 2 | 5 |

After the merge: 15 findings (0 blockers, 6 majors, 9 minors). Four pairs were merged as duplicates, and two findings were dropped because the text they quote no longer exists (see "Coverage").

## Conflicts between reviewers

### T1 / S2: how the concurrency test in `tasks.md` 4.2 forces the race

Both reviewers replace the same sentence in task 4.2 ("two threads create `Bug` and `bug` for one owner at the same time, ...") with different text.

- **T1** proposes: "two threads released by a `CyclicBarrier` create `Bug` and `bug` for one owner, repeated for 20 rounds with a new owner each". The threads start together, but the overlap is still a matter of timing. T1 covers the constraint path with a separate, deterministic repository-level scenario in the spec ("Clash found only by the database").
- **S2** proposes: "The test makes the race certain: a test wrapper around `LabelRepository` holds both threads after `existsByOwnerAndNameKey` returned false (for example with a `CyclicBarrier`) until both have passed the check, so the loser's exception comes from the database constraint and not from the pre-check. Add the same test for two renames of different labels to one folded name."

Why they can't both apply as written: they are two replacements for one sentence, and they disagree on where the barrier sits (before the service call, or between the pre-check and the insert). T1's two spec scenarios work with either wording.

Decision: S2. Task 4.2 gates both threads after the pre-check. T1's two spec scenarios were applied as well.

## Findings

### T1 + S2: The concurrency scenario can pass with a wrong implementation
**Status:** applied
Merged duplicate of T1 (major) and S2 (minor). Severity: the higher one.

- Severity: major - the only scenario that covers the 409 on a race depends on timing, so a missing constraint translation (500 instead of 409) passes most runs.
- Location: `specs/labels/spec.md` > Requirement "Name uniqueness holds under concurrent requests" > Scenario "Two simultaneous creates"; `tasks.md` > 4.2
- Current text: "**WHEN** a user sends two create requests for `Bug` and `bug` at the same time" / "two threads create `Bug` and `bug` for one owner at the same time, exactly one succeeds, the other gets `LabelNameAlreadyUsedException`, and one row exists."
- Problem: "At the same time" is an input a test can't control. If the two requests run one after the other, the service's pre-check answers 409 and the test passes, even when the constraint violation isn't translated or `save` doesn't flush. The deterministic check exists only in `tasks.md` 3.2, inside a `@DataJpaTest` transaction, not inside the service's own transaction. The requirement also says "several simultaneous requests", but nothing says how the test forces overlap.
- Failure scenario: `JpaLabelRepository.save` uses `save` instead of `saveAndFlush`, or the translation misses inside the service transaction. The violation surfaces as a 500. The two-thread test passes on the developer's machine because the threads serialize. In production two real simultaneous creates give 201 and 500.
- Proposed fix (T1), in `specs/labels/spec.md`, replace the scenario "Two simultaneous creates" with these two:
  ```
  #### Scenario: Two simultaneous creates
  - **WHEN** two create requests of one user for `Bug` and `bug` are released together from a start barrier, repeated for 20 rounds with a new user each round
  - **THEN** in every round one response is 201, the other is 409 `CONFLICT` with `ALREADY_USED` for `name`, and the user has exactly one of the two labels

  #### Scenario: Clash found only by the database
  - **WHEN** the labels `Bug` and `bug` of one user are saved through the label repository, without the service's name check
  - **THEN** the second save fails with the name-already-used error and not with a generic database error, and the same two saves for two different users succeed
  ```
  The round count of 20 is the reviewer's pick; any fixed number works.
- Proposed fix for `tasks.md` 4.2: see "Conflicts between reviewers". Applied: S2's wording.
- Upstream: no
- Test sketch (T1):
  - Level: integration (`@DataJpaTest` for the second scenario, `@SpringBootTest` for the first)
  - Arrange and act: save `Bug` then `bug` for one owner through `JpaLabelRepository`.
  - Passes when: the second save throws `LabelNameAlreadyUsedException`.
  - Fails when: the adapter uses `save` without flush or doesn't translate.

### T2: The update request's `color` has no negative scenario
**Status:** applied

- Severity: major - `UpdateLabelRequest` is its own DTO with per-property "was sent" tracking, and both gaps give a wrong response that no scenario catches.
- Location: `specs/labels/spec.md` > Requirement "Update a label" (and "Colors are validated as palette names", whose scenarios all say "a label is created")
- Current text: "A field sent as `null` SHALL produce the field error `REQUIRED`." with only the scenario "Null name"
- Problem: "A field" covers `color`, but only `name` is tested. `@ValidEnum` accepts `null`, so `{ "color": null }` looks like an absent field unless the null check also tracks `color`. An unknown color is only tested on create.
- Failure scenario: (a) the "sent properties" tracking is wired for `name` only, so `{ "color": null }` returns 200 as a no-op. (b) `@ValidEnum` is missing on `UpdateLabelRequest.color`, so `{ "color": "Red" }` reaches `LabelColorDto.valueOf` and ends as a 500. All current scenarios pass in both cases.
- Proposed fix: add to `specs/labels/spec.md` under "Update a label":
  ```
  #### Scenario: Null color
  - **WHEN** a user sends `{ "color": null }` for their label colored `RED`
  - **THEN** the response is 400 `VALIDATION_FAILED` with exactly the field error `color` `REQUIRED`, and the label is still `RED`

  #### Scenario: Unknown color on update
  - **WHEN** a user sends `{ "color": "Red" }` for their label colored `RED`
  - **THEN** the response is 400 `VALIDATION_FAILED` with exactly the field error `color` `INVALID_VALUE`, and the label is unchanged
  ```
  In `tasks.md` 5.3, change "update (partial, null, whitespace-only, validation storing nothing)" to "update (partial, null name, null color, unknown color, whitespace-only, validation storing nothing)".
- Upstream: no
- Test sketch:
  - Level: integration (E2E in `:app`)
  - Arrange and act: create `Bug`/`RED`, PATCH it with `{ "color": null }`, then list.
  - Passes when: 400 with `color` `REQUIRED` and the list still shows `RED`.
  - Fails when: the null check ignores `color`; the test sees 200.

### T3: Timestamp scenarios don't say the clock moved, so they can't fail
**Status:** applied

- Severity: major - a test written from these scenarios as worded passes for an implementation that rewrites `createdAt` or `updatedAt` on every PATCH.
- Location: `specs/labels/spec.md` > "Update a label" > "Rename and recolor"; "Unchanged values do not move the updated time" > "Empty patch", "Same name with surrounding spaces", "One of two fields differs", "Case-only rename"
- Current text: "**WHEN** a user sends `{ "name": "Defect", "color": "ROSE" }`" / "**WHEN** a user sends `{}` for their label" / "a user later sends"
- Problem: `E2eTest` resets `MutableClock` to a fixed instant and it only moves when the test moves it. "Rename and recolor" and "Empty patch" set no time gap, and "later" has no amount. With the clock unmoved, "unchanged" and "changes to the time of the request" give the same value. "Rename and recolor" also doesn't name the label or its starting values. Only "Same color again" states the gap ("one minute after").
- Failure scenario: `LabelService.update` always sets `updatedAt = clock.instant()`. The "Empty patch" test doesn't advance the clock, compares equal instants, and passes.
- Proposed fix: in `specs/labels/spec.md`, replace the WHEN/THEN lines:
  - Rename and recolor: "**WHEN** a user sends `{ "name": "Defect", "color": "ROSE" }` for their label `Bug` colored `RED`, one minute after creating it" / "**THEN** the response is 200 with `name` `Defect` and `color` `ROSE`, `createdAt` is still the creation time, and `updatedAt` is one minute later"
  - Same name with surrounding spaces: "**WHEN** a label is named `Bug` and, one minute after its last change, a user sends `{ "name": " Bug " }`"
  - Empty patch: "**WHEN** a user sends `{}` for their label one minute after its last change"
  - One of two fields differs: "**WHEN** a label is `Bug` and `RED` and, one minute after its last change, a user sends `{ "name": "Bug", "color": "BLUE" }`"
  - Case-only rename: "**WHEN** a label is named `bug` and, one minute after its last change, a user sends `{ "name": "Bug" }`"
- Upstream: no
- Test sketch:
  - Level: integration (E2E in `:app`) and unit (`LabelService` with `MutableClock`)
  - Arrange and act: create a label, `clock.advanceBy(1 minute)`, send `{}`.
  - Passes when: `updatedAt` equals the creation instant.
  - Fails when: the service saves on every PATCH; the test sees `updatedAt` one minute later.

### S1: Unlimited labels plus an unpaginated, in-memory sorted list is an unrecorded denial-of-service risk
**Status:** applied

Note: this finding does not challenge D6 ("No limit"). Its fix records the cost of D6 as an accepted risk. The assumptions reviewer listed the in-memory list as safe to leave implicit ("A user can only grow their own list"); the two reviewers assess the same fact differently.

- Severity: major - any registered user can take the single instance down with ordinary requests, and no artifact records this as an accepted risk. It is not a blocker only because D6 is a settled decision and the fix here is to record it, not to change behavior.
- Location: `design.md` > Risks / Trade-offs (also > "List order is computed in the service")
- Current text: "The list is sorted in Kotlin by `nameKey` with a code-point comparator (`codePointOrder`)." The Risks section has no entry for D6.
- Problem: D6 ("No limit", reason: "The shared specs define none") was decided without its availability cost on record. Labels have no rate limit, be-auth has no IP limit on registration, the body limit only bounds one request, and the list loads, folds, sorts and serializes every label per call. `nameKey` is a computed property, so the comparator also calls ICU folding O(n log n) times per request. `CLAUDE.md` asks for known trade-offs to be raised, not left silent.
- Failure scenario: a user scripts creates until the account holds about a million labels, then sends a few parallel `GET /api/v1/labels`. Each builds a response over 100 MB from a million entities. The instance runs out of heap and all users lose service. The 601-label E2E test stays green.
- Proposed fix: add to `design.md` "Risks / Trade-offs":
  "[No label limit (D6), no rate limit on label endpoints, and a complete in-memory sorted list] → One account can create labels without bound and then make `GET /api/v1/labels` load, sort and return all of them, which can exhaust the heap of the single instance. Accepted for v1 by D6. A cap needs a shared-spec change and the user's decision. To keep the cost per request down, the list sort folds each name once (it sorts by the stored key, not by calling `foldCase` inside the comparator)."
  And change the sentence in "List order is computed in the service" to: "The list is sorted in Kotlin by `nameKey` with a code-point comparator (`codePointOrder`); the key of each label is computed once per request, not per comparison."
  Alternative, which only the user can choose: reopen D6 and set a cap (needs the shared-spec change D6 mentions).
- Upstream: no

### A1: A PATCH or DELETE racing a DELETE of the same label has no defined result
**Status:** applied

- Severity: major - the likely implementations give a 500 or bring a deleted label back; the window is small, but two implementers would build different things.
- Location: `design.md` > "Request flow" and "Update semantics (D5)"; `specs/labels/spec.md` > Requirement: Delete a label
- Current text: "The controller calls `LabelService.get(ownerId, labelId)` first so a missing label is 404, then `RequestValidator.validate(request)`, then `LabelService.update(ownerId, labelId, changes)`." and "Otherwise it sets `updatedAt = clock.instant()` and saves."
- Assumption: The label still exists between the existence check, the load in `update`, and the save. Nobody deletes it in between.
- Problem: `get` and `update` are separate service calls, and the label ID is assigned by the application. `UserEntity` shows the pattern the label entity will copy: a plain `@Id UUID` with no `@Version`, saved through Spring Data `save`. With an assigned ID, `save` is a `merge`. If the row is gone and the entity is still managed, the UPDATE hits 0 rows and Hibernate throws a stale-state exception, which `CommonExceptionHandler` turns into 500. If the entity is not managed, `merge` finds nothing and INSERTs, so the deleted label comes back with its old `createdAt`. The same applies to two DELETEs at once if delete is written as find-then-delete.
- Failure scenario: The user deletes `Bug` on the phone while the web client sends a rename for it. The DELETE commits between the PATCH's load and its save. The PATCH answers 500, or answers 200 and `Bug` is back in the list. Once `be-cards` exists, the resurrected label is also gone from all cards.
- Proposed fix: Add to `specs/labels/spec.md`, under "Delete a label":
  "A PATCH or DELETE for a label that another request deleted in the meantime SHALL return 404 `NOT_FOUND` and SHALL NOT recreate the label.
  #### Scenario: Update after a concurrent delete
  - **WHEN** a label is deleted after an update request for it was accepted but before that update is stored
  - **THEN** the update returns 404 `NOT_FOUND` and the label list does not contain the label"
  Add to `design.md` > "Request flow": "`LabelService.update` loads the label with a row lock (`findByIdAndOwnerId` with `PESSIMISTIC_WRITE`) inside its own transaction and throws `LabelNotFoundException` when it finds nothing, so a concurrent delete either waits or is seen. Delete uses `deleteByIdAndOwnerId` and treats an affected-row count of 0 as `LabelNotFoundException`; it never loads first."
  Alternative: no lock, and `JpaLabelRepository.save` translates the stale-state exception into `LabelNotFoundException`. That fixes the 500 but not A2.
- Upstream: no
- Not checked by the reviewer: actual Hibernate/Spring Data behavior at runtime. The finding rests on the documented `merge` behavior for assigned IDs.

### D2 + A5: The "sent properties" mechanism behind `@NotNullWhenPresent` is not designed
**Status:** applied
Merged duplicate of D2 (major) and A5 (minor). Severity: the higher one. The two fixes are compatible; A5 adds the spec and task wording.

- Severity: major - it is the only new `:common` abstraction, `be-boards` defers to this description ("adds it as `be-labels`' design describes it"), and the description doesn't say how presence is recorded, so the implementer has to invent shared API-contract code.
- Location: `design.md` > "Telling `null` from absent in the update request"; `tasks.md` 5.2; `specs/labels/spec.md` > scenario "Request fields"
- Current text: "`UpdateLabelRequest` therefore records which properties were present in the JSON, and a class-level constraint `@NotNullWhenPresent` reports `REQUIRED` at the property's path for one that was sent as `null`." / "the `:common` customizer needs no change for that, because it only marks `@Required` fields."
- Problem: Three gaps against the code:
  1. Every request DTO today is a data class with nullable constructor properties (`TestRequest` in `app/src/test/.../testapi/TestApi.kt`; rule in `CLAUDE.md` "every property is nullable in Kotlin with no default"). Jackson gives no presence hook for constructor properties, so the mechanism needs either `var` body properties with recording setters (a deviation from that rule) or a custom deserializer in `:common`. The design picks neither.
  2. `CardiganOpenApiCustomizer.customizeRequestSchema` reads `requestClass.declaredFields` only. springdoc documents public properties of the class and its supertypes, so a base-type member that Jackson or springdoc can see becomes a property of `UpdateLabelRequest` in `openapi.json`, or an `allOf` with a base schema. The "needs no change" claim only holds if the base type exposes no property.
  3. The existing `reject(FieldViolation)` helper in `FieldViolation.kt` relies on the default violation at the annotated field. A class-level constraint must disable the default violation and add one violation per property node, or `fieldErrorsOf` reports an empty field path.
- Failure scenario: the first implementer adds a base class with a public `sentProperties` set. The snapshot gains a `sentProperties` request property that mobile clients generate, and task 6.1's checks ("requires nothing", "no request property is nullable") still pass. Or the validator uses `reject(...)` as the other validators do and the error comes back with `"field": ""`.
- Proposed fix (D2, recommended option): replace the mechanism description in that design section with:
  "`:common` `api/validation` gets an abstract class `PatchRequest`. It keeps a private set of the property names that were sent as JSON `null` and offers `protected fun <T> sent(name: String, value: T?): T?`, which records the name when `value` is null, and `fun nullProperties(): Set<String>`. It has no public property, so neither Jackson nor springdoc sees anything of it and `openapi.json` is unaffected. A PATCH request DTO is a plain class (not a data class) that extends it and declares each field in the body as `var name: String? = null` with `set(value) { field = sent("name", value) }`, with the usual constraints on the field. Jackson calls a setter only for a property that is present, also for an explicit `null`. This is the one deliberate exception to the rule that request properties are constructor properties with no default. `@NotNullWhenPresent` is a class-level constraint on `PatchRequest`. Its validator disables the default violation and adds one violation per name in `nullProperties()` with `addPropertyNode(name)` and the dynamic payload `FieldViolation(REQUIRED)`. Field constraints skip null, so a sent value is still checked by its own constraints. `CardiganOpenApiCustomizer` needs no change: it reads the declared fields of the DTO, and body properties have backing fields."
  Add to `tasks.md` 5.2 verification: "and a `CardiganOpenApiCustomizerTest` case that a `PatchRequest` subclass documents exactly its own properties, none required and none nullable."
  Alternative (D2): keep constructor properties and add a custom Jackson deserializer in `:common` that records the present field names. It keeps the DTO rule intact but adds Jackson machinery to `:common`.
- Proposed fix (A5, in addition): in `specs/labels/spec.md` > scenario "Request fields", change the THEN to: "the create request lists `name` and `color` as required, the update request lists no required field and has exactly the properties `name` and `color`, no request property is nullable, and no schema exists for a request base type". Add "`UpdateLabelRequest` has exactly `name` and `color`" to the checklist in `tasks.md` 6.1.
- Upstream: no

### T4: PATCH scenarios in "Labels are isolated per user" don't state a body
**Status:** applied

- Severity: minor - the wrong reading fails right away and is easy to fix, but two valid tests of the same scenario disagree.
- Location: `specs/labels/spec.md` > "Labels are isolated per user" > "Update another user's label", "Malformed label ID", "Uppercase label ID"
- Current text: "**WHEN** a user sends a PATCH to `/api/v1/labels/abc`" / "**THEN** the response is 404 `NOT_FOUND`"
- Problem: shared `api-errors` "Error precedence" puts media type (415) and parsing (400) before references (404). A PATCH with no body or no content type gives 415 or 400 `MALFORMED_REQUEST`, not 404. "Uppercase label ID" promises 200 without saying what was sent. The requirement also covers delete with a malformed ID, and no scenario does.
- Failure scenario: a test sends the PATCH to `/abc` with no body, gets 400, and the developer "fixes" the handler order to make it 404, which breaks error precedence.
- Proposed fix: in `specs/labels/spec.md`:
  - Update another user's label: "**WHEN** user B sends a PATCH with `{ "color": "BLUE" }` for the ID of user A's label"
  - Malformed label ID: "**WHEN** a user sends a PATCH with `{ "color": "BLUE" }`, or a DELETE, to `/api/v1/labels/abc`" / "**THEN** each response is 404 `NOT_FOUND`"
  - Uppercase label ID: "**WHEN** a user sends a PATCH with `{ "color": "BLUE" }` for their own label with the ID written in uppercase" / "**THEN** the response is 200 with `color` `BLUE`, and `id` is lowercase"
- Upstream: no
- Test sketch:
  - Level: integration (E2E in `:app`)
  - Arrange and act: signed-in user sends PATCH `/api/v1/labels/abc` with `{ "color": "BLUE" }`, and DELETE `/api/v1/labels/abc`.
  - Passes when: both are 404 `NOT_FOUND`.
  - Fails when: `labelId` is declared as `String` and parsed by hand with a 400.

### T5 + D3: The documented 409 and 404 have no scenario, and task 6.1 names a test class that can't see the document
**Status:** applied
Merged duplicate of T5 (minor) and D3 (minor). T5's fix includes D3's.

- Severity: minor - a missing `@ApiErrors` only loses a typed error in generated clients and can be added later without breaking them; the wrong test class is caught at implementation.
- Location: `specs/labels/spec.md` > Requirement "Label operations in the API document"; `tasks.md` 6.1
- Current text: "`createLabel` and `updateLabel` SHALL document 409 `CONFLICT`, and `updateLabel` and `deleteLabel` SHALL document 404 `NOT_FOUND`." / "Verify with `OpenApiSnapshotTest` passing and an `ApiDocsE2eTest` case for the `labels` scenarios "Operations and tag", "Color enum" and "Request fields"."
- Problem: none of the three scenarios checks the error statuses. `OpenApiSnapshotTest` only compares against the snapshot, which is regenerated in the same change, so it can't catch a missing status. Also, `ApiDocsE2eTest` extends `E2eTest`, where springdoc is off; its only test asserts that `/v3/api-docs` is 404. The document assertions live in `OpenApiSnapshotTest`, which enables springdoc.
- Failure scenario: `@ApiErrors(404)` is forgotten on `deleteLabel`. The snapshot is regenerated without it, every test passes, and the mobile client has no typed 404 for delete. Or the implementer enables API docs in the E2E context to make the `ApiDocsE2eTest` case work, which breaks `no API docs are served at runtime`.
- Proposed fix: add to `specs/labels/spec.md` under "Label operations in the API document":
  ```
  #### Scenario: Error statuses
  - **WHEN** the responses of the four operations in the snapshot are inspected
  - **THEN** `createLabel` documents exactly 201, 400, 401, 409, 413, 415 and 500, `listLabels` 200, 401 and 500, `updateLabel` 200, 400, 401, 404, 409, 413, 415 and 500, and `deleteLabel` 204, 401, 404 and 500, with `CONFLICT` in the 409 description and `NOT_FOUND` in the 404 description
  ```
  In `tasks.md` 6.1, replace "an `ApiDocsE2eTest` case for the `labels` scenarios "Operations and tag", "Color enum" and "Request fields"" with "`OpenApiSnapshotTest` cases for the `labels` scenarios "Operations and tag", "Color enum", "Request fields" and "Error statuses"". The status lists follow the `CLAUDE.md` rule; check them against `CardiganOpenApiCustomizer` when pasting. (D3 notes that `be-boards` task 6.1 has the same mistake.)
- Upstream: no
- Test sketch:
  - Level: contract (`OpenApiSnapshotTest`)
  - Arrange and act: read `paths./api/v1/labels/{labelId}.delete.responses` from the generated document.
  - Passes when: the keys are exactly 204, 401, 404, 500.
  - Fails when: `@ApiErrors` is missing on `deleteLabel`.

### T6: "Real code" in the modified module-boundaries scenario can't be checked
**Status:** applied

- Severity: minor - nothing breaks at runtime, but the old scenario was a mechanical check and the new one is a judgment call.
- Location: `specs/module-boundaries/spec.md` > "Module layer packages exist from the start" > Scenario "No placeholder classes"
- Current text: "**THEN** every source file holds real code, and a layer package without real code holds no source file"
- Problem: "real code" has no measurable meaning. An empty-body class can be real here (an exception subclass such as `LabelNotFoundException`), so "empty class" doesn't define a placeholder either. The main spec's scenario ("it contains no source files") was checkable. The repo's actual mechanism is `.gitkeep` files in the four `:board` layer packages, which `design.md` says are removed once a package holds code.
- Failure scenario: `be-boards` lands first and leaves a stub class in `board/service` to keep the package. Nobody can say from this scenario whether that breaks the spec.
- Proposed fix: in `specs/module-boundaries/spec.md`, replace the scenario with:
  ```
  #### Scenario: No placeholder classes
  - **WHEN** the layer packages of `:user`, `:board` and `:common` in the main source folders are inspected
  - **THEN** each one holds either at least one Kotlin source file at any depth and no `.gitkeep`, or a `.gitkeep` and no source file
  ```
  Alternative: keep the current wording and state that it is a review check with no automated test. Note: the `be-boards` delta for this requirement is identical, so it needs the same wording.
- Upstream: no
- Test sketch:
  - Level: unit (file-system check in `:app`, like the existing mapping checks)
  - Passes when: every layer folder has `.kt` files and no `.gitkeep`, or only a `.gitkeep`.
  - Fails when: a `.gitkeep` stays next to new code, or a layer package is missing.

### S3 + A4: Nothing carries the unique constraint and its name into the production migration
**Status:** applied
Merged duplicate of S3 (minor) and A4 (minor). The fixes say the same thing.

- Severity: minor - the gap opens only when the baseline migration is written later, and the persistence tests catch it if they then run against the migrated schema, which `CLAUDE.md` plans but this change does not require.
- Location: `tasks.md` > Workflow follow-up; `design.md` > "Uniqueness: check first, constraint as the backstop" and Risks, "[A table exists without a migration]"
- Current text: "translates a `DataIntegrityViolationException` whose constraint is `labels_owner_name_key_unique` into the same `LabelNameAlreadyUsedException`." / "[A table exists without a migration] → `prod` fails to start until the baseline migration is written, as `CLAUDE.md` already states."
- Assumption (A4): every environment's schema has this unique constraint under exactly this name.
- Problem: the constraint is the only guard against the race, and the translation matches it by the exact name. In production both come from a hand-written migration outside this change. Hibernate `validate` checks tables and columns, not unique constraints or their names, so a missing or renamed one passes startup.
- Failure scenario: the baseline migration is written with `UNIQUE (owner_id, name_key)` and no name (Postgres names it `labels_owner_id_name_key_key`), or without the constraint. `prod` starts. Either racing creates store two labels with the same folded name, or the race answers 500.
- Proposed fix: add to `tasks.md` "Workflow follow-up":
  "- The baseline migration must create the unique constraint `labels_owner_name_key_unique` on (`owner_id`, `name_key`) in `board_service.labels` with exactly that name. Hibernate `validate` does not check it. The persistence tests from 3.1 and 3.2 (native duplicate insert, constraint translation) must run against the migrated schema once it exists."
  And extend the `design.md` risk line with: "The migration must carry the unique constraint under the same name; `validate` does not check it (see tasks.md, Workflow follow-up)."
- Upstream: no

### A2: Two PATCHes on the same label at once can undo each other's field
**Status:** applied

- Severity: minor - needs two overlapping updates of one label by the same user; the result is a silently reverted field, with no error.
- Location: `design.md` > "Update semantics (D5)"
- Current text: "`LabelService.update` loads the label, applies the sent fields, and compares the result with the stored label on `name` and `color`"
- Assumption: Updates of one label never overlap, so the loaded label is still the stored one when it is saved.
- Problem: The service writes the whole label back. Shared `api-conventions` "Updates are partial" says absent fields keep their current values. With two overlapping requests, the second save writes the old value of a field it never sent.
- Failure scenario: The label is `Bug`/`RED`. The web client sends `{ "color": "BLUE" }` and the phone sends `{ "name": "Defect" }` at the same time. Both load `Bug`/`RED`. The stored result is `Defect`/`RED`, although the color request got 200 with `BLUE`.
- Proposed fix: The row lock from A1 covers this. Add to `design.md` > "Update semantics (D5)": "The label is loaded with a row lock, so two updates of one label run one after the other and an absent field always keeps the value of the latest committed update." If the user prefers no lock, state the opposite in the Risks section: "[Two overlapping PATCHes on one label] → last write wins for the whole label; accepted."
- Upstream: no

### D4: The proposal lists a `LabelColorDto` schema that the design doesn't produce
**Status:** applied

- Severity: minor - affects the names of generated client types, and the snapshot review in task 6.1 has no stated target.
- Location: `proposal.md` > Impact > API
- Current text: "four new operations and the schemas `LabelDto`, `LabelListDto`, `LabelColorDto`, `CreateLabelRequest` and `UpdateLabelRequest` in `openapi.json`"
- Problem: Request colors are `String` with `@ValidEnum`, and `CardiganOpenApiCustomizer.applyConstraintFormats` writes the values inline (`property.enum = ...`). It can't emit a `$ref`. springdoc also inlines a response enum unless `enumAsRef` is set. So by default no `LabelColorDto` component exists. If the implementer forces a `$ref` on the response to match the proposal, clients get one named enum for responses and two anonymous ones for requests.
- Failure scenario: task 6.1 expects "the label schemas" and the reviewer can't tell whether a missing or present `LabelColorDto` component is correct.
- Proposed fix (recommended): in `proposal.md` write "the schemas `LabelDto`, `LabelListDto`, `CreateLabelRequest` and `UpdateLabelRequest`. The color is an inline string enum in all three places it appears." and add to `design.md` > OpenAPI: "The color enum is inline in `LabelDto` and both requests. There is no `LabelColorDto` component." Alternative: one shared `LabelColorDto` component referenced everywhere, which needs a customizer change in `:common` and gives clients one enum type.
- Upstream: no
- Not checked by the reviewer: springdoc's actual enum output was not run.

### D5: ICU4J in `domain` contradicts the layer table in `CLAUDE.md`, and `domain/text` is not in the module template
**Status:** applied

- Severity: minor - the design is explicit, but `CLAUDE.md` says it wins on conflict, and task 7.1 doesn't update it.
- Location: `design.md` > "Case folding lives in `:board` `domain/text`"; `tasks.md` 7.1
- Current text: "The `domain` layer may use it: the layer rule forbids Spring and `jakarta.persistence`, not plain libraries."
- Problem: `AGENTS.md` line 130 says `domain` may depend on "Kotlin, `java.*`, `common.domain`". Only the bullet below it is phrased as a ban on Spring and JPA. The business module template lists `domain/model`, `exception`, `event`, `repository`, no `text`. `be-boards` puts its pure helpers (`BoardKey.kt`, `DensePositions.kt`) in `domain/model`, so the two parallel changes use different homes for the same kind of code.
- Failure scenario: a later agent follows the table, treats the ICU import in `domain` as a violation and moves folding behind an interface in `infra`, which breaks the computed `Label.nameKey`.
- Proposed fix: add to `tasks.md` 7.1: "Also update the layer table (`domain` may depend on: Kotlin, `java.*`, `common.domain`, and ICU4J for case folding in `:board`) and add `text/  pure text helpers (CaseFolding.kt)` under `domain/` in the business module layout." Alternative: put `CaseFolding.kt` in `domain/model` as `be-boards` does with its helpers, and only update the layer table.
- Upstream: no

### D6: Column type `timestamp` doesn't match what the entities produce
**Status:** applied

- Severity: minor - dev and tests build from the entity, so it only matters when the baseline migration is written from this table.
- Location: `design.md` > Data model
- Current text: "| `created_at` | `timestamp` | not null |" and "| `updated_at` | `timestamp` | not null |"
- Problem: `UserEntity` maps `Instant` with a plain `@Column`, and Hibernate creates `timestamp(6) with time zone` for it on Postgres. `LabelEntity` will get the same. `timestamp` in Postgres means without time zone.
- Failure scenario: the baseline migration is written from the design table with `timestamp`, and `prod` (`ddl-auto: validate`) fails to start or stores local-time values.
- Proposed fix: write `timestamptz` in both rows. (`be-boards` has the same rows.)
- Upstream: no
- Not checked by the reviewer: Hibernate DDL was not run; the finding rests on the default behavior and the existing `UserEntity`. The assumptions reviewer listed the same wording as safe, because the entity decides the type.

### D7: Tasks and design disagree on which scenarios run as E2E, and task 7.1 misses the `:common` addition
**Status:** applied

- Severity: minor - small gaps that lead to a skipped or impossible test and a stale `AGENTS.md`.
- Location: `tasks.md` > 5.3 and 7.1; `design.md` > Tests
- Current text: "Verify with E2E tests for every scenario of `specs/labels/spec.md` except the snapshot ones" / "E2E (`:app`, `LabelE2eTest`): every scenario of the `labels` spec over HTTP."
- Problem: "Duplicate written past the application" can't run over HTTP (task 3.1 covers it). "Two simultaneous creates" promises HTTP responses 201 and 409, but the list in 5.3 leaves it out and 4.2 only tests it at service level. Task 7.1 doesn't add `@NotNullWhenPresent` to the `:common` validation lists in `AGENTS.md`; `be-boards` 7.1 does, so if `be-labels` lands first the lists stay stale. Task 5.3 also lacks the "unless `be-boards` already did" that `be-boards` 5.2 has for the `E2eTest` cleanup.
- Failure scenario: the concurrent-create scenario's HTTP outcome (409, not 500) is never tested, although the translation only reaches HTTP through `CommonExceptionHandler`.
- Proposed fix:
  - 5.3: "... every scenario of `specs/labels/spec.md` except the snapshot ones and "Duplicate written past the application" (covered in 3.1): ..., name clashes, two simultaneous creates over HTTP (one 201, one 409), delete ..." and "make `E2eTest` clean `board_service` too (unless `be-boards` already did)".
  - `design.md` Tests: "E2E (`:app`, `LabelE2eTest`): every scenario of the `labels` spec that can run over HTTP, including the two simultaneous creates."
  - 7.1: add "If this change added `@NotNullWhenPresent`, add it to the places that list the `:common` constraints."
- Upstream: no
- Note: the "two simultaneous creates over HTTP" part depends on how the T1 / S2 conflict is decided.

## Security analysis

### Trust boundaries

**Access token to owner ID**
- Attacker position: no account, or a registered user B.
- Attack path: call a label endpoint with no token, a tampered token, or try to pass an owner in the body (`ownerId`, `id`).
- What they gain: nothing.
- What stops it: `SecurityConfig` requires authentication for everything under `/api/v1` that is not in a `PublicRoutes` bean, and this change adds no public route. The owner is always the token's `sub` (`requestUserId`; `SubjectValidator` requires a canonical UUID). The request DTOs have only `name` and `color`, and unknown fields are ignored. The spec has a 401 scenario only for POST, but the default-deny rule is structural and `AuthenticationE2eTest` already covers it. Auth is bearer-header only, so there is no CSRF surface.

**`labelId` in the path (update, delete)**
- Attacker position: registered user B who knows or guesses user A's label ID.
- Attack path: PATCH or DELETE A's ID; compare the response with an unknown ID, a malformed ID, and with valid vs. invalid bodies to find an existence oracle.
- What they gain: nothing.
- What stops it: "Labels are isolated per user" plus `design.md` "Every repository query takes the owner ID". The controller resolves the label before `RequestValidator.validate`, so a field error can't reveal that a foreign ID exists. A body parse error (400) comes before the lookup for every ID. Foreign and missing IDs run the same owner-scoped query, so there is no useful timing difference. The 409 check is scoped to the caller's own labels, so 409 is not a cross-user oracle.

**Request body**
- Attacker position: registered user.
- Attack path: oversized body; NUL or control characters in `name`; a name that folds to more than the column holds; `color` of another type or case; `{"name": null}` to wipe a value; duplicate keys.
- What they gain: nothing.
- What stops it: the 256 KB limit (`RequestSizeLimitFilter`), `@ValidText(min = 1, max = 30)` with `checkText` rejecting U+0000–U+001F and unpaired surrogates, `name_key varchar(90)` covering the 3x worst case of full folding, the closed `@ValidEnum`, Jackson scalar coercion turned off, and `@NotNullWhenPresent`. Label names are only ever shown to their owner, so bidi or zero-width characters give no way to spoof another user.

**Database constraint as the race guard**
- Attacker position: registered user sending parallel creates or renames.
- Attack path: two requests pass `existsByOwnerAndNameKey` at the same time and both insert.
- What they gain: nothing in dev and tests. The second insert blocks on the unique index and fails.
- What stops it: `labels_owner_name_key_unique` plus the translation to `LabelNameAlreadyUsedException`. Two gaps in how this is verified and carried to production: T1 + S2, S3 + A4.

**Unbounded label list**
- Attacker position: any registered user. be-auth D10 has no IP limit on registration, so getting an account is free.
- Attack path: script `POST /api/v1/labels` with distinct names until the account holds hundreds of thousands or millions of labels. Then call `GET /api/v1/labels` in parallel.
- What they gain: heap exhaustion on the single app instance, plus unbounded growth of the database.
- What stops it: nothing. See S1.

**ICU4J dependency**
- Attacker position: supply chain.
- Attack path: a wrong or typosquatted artifact is added when the version is "looked up at implementation".
- What stops it: task 1.1 names the exact coordinates `com.ibm.icu:icu4j`, the version is pinned in the version catalog, and the dependency was agreed in D7. No finding.

**Dev database in task 7.2**
- Attacker position: none. This is an operator-error boundary.
- Attack path: the check runs against the production branch.
- What stops it: task 7.2 says "Supabase MCP (dev branch)" and `CLAUDE.md` forbids the production branch. The step is read-only. No finding.

### Test versus production

**Labels are isolated per user**
- What a test would do: E2E with two users from `E2eTest.signIn`, real tokens, real filter chain, real Postgres. Unit tests use a fake `LabelRepository`.
- What differs in production: nothing relevant. Task 3.1 tests the owner-scoped queries against Postgres and task 5.3 tests foreign IDs over HTTP.
- How it fails: no realistic gap found.

**Same 404 for foreign, unknown and malformed IDs, before field validation**
- What a test would do: E2E PATCH with an invalid body for an unknown ID, and for another user's ID.
- What differs in production: nothing. The order is fixed by the controller flow in `design.md`.
- How it fails: no realistic gap.

**Name uniqueness holds under concurrent requests**
- What a test would do: task 4.2 starts two threads that call the service with `Bug` and `bug`.
- What differs in production: real overlap. In the test the first thread often commits before the second runs its pre-check, so the constraint path in a service transaction never runs.
- How it fails: the test is green whichever path produced the exception. If the translation doesn't work inside the service transaction, a real race returns 500, not 409. The data stays unique because of the constraint.
- What would close the gap: T1 + S2.

**Duplicate written past the application is rejected by the database**
- What a test would do: native insert against a schema Hibernate built from `LabelEntity` (`create-drop`).
- What differs in production: the schema comes from a hand-written baseline migration that is not part of this change. Hibernate `validate` doesn't check unique constraints or their names.
- How it fails: the migration leaves the constraint out or names it differently. `prod` starts, `validate` passes.
- What would close the gap: S3 + A4.

**No limit on the number of labels**
- What a test would do: create 601 labels and list them.
- What differs in production: an attacker creates a thousand times more and lists them in parallel.
- How it fails: the 601 test is green while the list endpoint can exhaust the heap.
- What would close the gap: S1.

**Endpoints are protected**
- What a test would do: "No token" for POST only.
- What differs in production: nothing. Default-deny in `SecurityConfig` covers the other three.
- How it fails: no realistic gap.

## Assumptions safe to leave implicit

| Assumption | Where it is implied | Why it is safe |
|---|---|---|
| `"color": 14` fails at parsing, not at validation | spec, scenario "Color of another type" | `JacksonConfig` disables scalar coercion, so a number never becomes the string `"14"`. |
| A whitespace-only PATCH name gives `REQUIRED`, not `TOO_SHORT` | spec, scenario "Whitespace-only name" | `checkText` returns `REQUIRED` for length 0 with `min >= 1`, and `@ValidText` runs on the sent value without `@Required`. |
| `@ValidText` and `@ValidEnum` accept an absent optional field | design, "Request flow" | Both validators return true for `null` (`Validators.kt`). |
| The E2E clock can be moved and starts at `2026-11-15T09:15:30.123Z` | spec, "Label body", "Same color again" | `E2eTestConfig` provides a `MutableClock`, and `E2eTest.START` is that instant. |
| The schema `board_service` gets created in dev and tests | proposal, "Database" | `application-dev.yaml` and `application-test.yaml` set `hbm2ddl.create_namespaces: true`; `application.yaml` already lists `board_service` for Flyway. |
| `timestamp` in the data model means the same column type as `users` | design, "Data model" | The entity maps `Instant` like `UserEntity`, so Hibernate picks the type. (D6 disagrees for the later migration.) |
| A folded name fits `varchar(90)` | design, "Data model" | Full case folding maps one code point to at most three, and the name has at most 30. A wrong value fails loudly on insert. |
| The folding of a stored name doesn't change after an ICU upgrade | design, "`Label` exposes `nameKey` as a computed property" | Unicode guarantees stable case folding for assigned characters. The worst case is one missed clash for a name with code points unassigned at write time. |
| NFC then folding is the comparison key, without normalizing again after folding | spec, "Name clashes are conflicts" | Shared `domain-label` defines exactly this, so every platform computes the same key. |
| Lenient UUID forms that `UUID.fromString` accepts (for example `1-2-3-4-5`) | spec, "Malformed label ID" | They parse to an ID that names no label, so the result is the same 404. |
| The token's `sub` is a UUID and may belong to no user record | design, "Request flow" | Stated in `CLAUDE.md` and `decisions.md`; tokens are only issued by this backend with a UUID subject. |
| Listing loads and sorts all of a user's labels in memory | design, "List order is computed in the service" | D6 and shared `api-conventions` decide this. A user can only grow their own list. (S1 disagrees.) |
| The wall clock doesn't jump backwards between create and update | spec, "Unchanged values do not move the updated time" | Nothing in labels orders or compares by timestamp. |
| Old clients keep working after deploy | proposal, "API" | The change only adds operations and schemas. |
| `be-boards` adds the same `:common` constraint under the same name | design, Risks | `be-boards` D21 names `@NotNullWhenPresent` in `common.api.validation`, and both task lists check for an existing one first. |
| Concurrent duplicate inserts end in a unique violation, not two rows | design, "Uniqueness" | Postgres makes the second insert wait for the first transaction and then fails it with the constraint. |

## New abstractions

- `@NotNullWhenPresent` + "sent properties" base type (`:common` `api/validation`): keep and document. Location is settled (D8), purpose is explained, mechanism is missing. See D2 + A5.
- `foldCase` / `codePointOrder` (`:board` `domain/text/CaseFolding.kt`): keep. Purpose and placement are justified (D7). Layer rule and package need a doc update. See D5.
- ICU4J dependency: fine as is. Settled in D7; task 1.1 adds it.
- `Label` with computed `nameKey`: fine as is. Justified with a rejected alternative.
- `LabelColor` (domain) + `LabelColorDto` (API): fine as is. Separate DTO enums are a project rule.
- `LabelChanges`: fine as is. With two fields a small type is reasonable.
- `LabelRepository` + `JpaLabelRepository` + `SpringDataLabelRepository`, entity and mappers: fine as is. Same shape as `:user`.
- Constraint-violation translation in `JpaLabelRepository.save` (`saveAndFlush`): fine as is. Documented with alternatives. `be-boards` copies the pattern; two copies don't justify a shared helper yet.
- `LabelNotFoundException`, `LabelNameAlreadyUsedException`: fine as is. They subclass the existing `:common` base exceptions.
- `LabelService.get` used by the controller for 404-first: fine as is. Follows the `RequestValidator` rule.
- `LabelListDto` wrapper: fine as is (D4).
- `name_key` column + `labels_owner_name_key_unique`: fine as is (D7).
- `BoardTestApplication`: fine as is. Mirrors `UserTestApplication`; shared with `be-boards`.

## Coverage

- Artifacts reviewed: `proposal.md`, `specs/labels/spec.md`, `specs/module-boundaries/spec.md`, `design.md`, `tasks.md`, with `decisions.md` as settled decisions. No non-standard artifacts.
- Missing artifacts: none
- Testability: 13 requirements and 46 scenarios checked; 6 requirements and 12 scenarios flagged in 6 findings.
- Security: 7 trust boundaries and 6 invariants examined; 3 findings. Not checked: the `@NotNullWhenPresent` mechanism (not specified, see D2 + A5) and ICU4J's own advisories.
- Hidden assumptions: 5 must-specify findings, 16 safe assumptions. Areas with nothing to report: platform, network, locale beyond case folding, process lifetime, permissions.
- Design contract: code mode. 13 abstractions checked against `:common`, `:user`, `:app` tests, the build files and the two other active changes (`be-boards`, `be-auth`).
- Reviewers that did not complete: none
- Findings dropped because their evidence did not exist: 2 (A3 and D1). Both said the artifacts still claim the shared specs spell colors `Red`. The commits `01fe3d9` and `c64f96f` landed while the reviewers ran and removed that wording; the quoted text is no longer in any artifact.
- Findings dropped because they challenged decisions.md: 0
- Nothing was run by any reviewer; all findings come from reading.
- Outside this change, noted by the design reviewer: `be-boards/design.md` still says "`be-labels`' design still places `@NotNullWhenPresent` in `:board`", which is no longer true.

## Applied changes

- Files changed: `specs/labels/spec.md`, `specs/module-boundaries/spec.md`, `design.md`, `tasks.md`, `proposal.md`
- Carried into other standard artifacts beyond the reviewers' text:
  - A1: `tasks.md` 4.1 and 4.2 test the update after a delete, and 5.3 excludes that scenario from E2E because it can't be forced over HTTP.
  - T1 + S2: `design.md` > Tests describes the gated race, and `tasks.md` 5.3 excludes "Clash found only by the database" from E2E (covered in 3.2).
  - S1: `tasks.md` 4.1 says each list key is computed once.
  - D2 + A5: `tasks.md` 5.2 names `PatchRequest`, 6.1 checks that no request base type schema exists, and 7.1 has `AGENTS.md` note that PATCH request DTOs extend `PatchRequest` with body properties.
  - D4: `tasks.md` 6.1 checks that no `LabelColorDto` schema exists.
- `openspec validate be-labels --strict`: passes, before and after the edits.
- Stale non-standard artifacts the user chose not to update: none
- Contradictions with decisions.md: none. S1 records the cost of D6 as an accepted risk and leaves D6 unchanged.
- Accepted risks: none
- Not edited, because they are outside this change: `be-boards` has the same `module-boundaries` delta (T6), the same `timestamp` rows (D6) and the same `ApiDocsE2eTest` mistake in its task 6.1 (D3), and its `design.md` describes the null check mechanism in the old words (D2).
