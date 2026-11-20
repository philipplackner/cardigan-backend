# Spec Delta

## Purpose

Defines the backend's label endpoints and their request and response bodies, and the backend-specific behavior around name uniqueness, updates and ownership, on top of the shared spec `domain-label`.

## ADDED Requirements

### Requirement: Label representation
A label in a response SHALL have exactly the fields `id`, `name`, `color`, `createdAt` and `updatedAt`. No field SHALL be nullable or absent.

#### Scenario: Label body
- **WHEN** a label named `Bug` with the color `RED` was created at `2026-11-15T09:15:30.123Z` and is returned
- **THEN** the body has exactly `id` (a lowercase UUID), `name` `Bug`, `color` `RED`, `createdAt` `2026-11-15T09:15:30.123Z` and `updatedAt` `2026-11-15T09:15:30.123Z`

### Requirement: Colors are validated as palette names
A label color on the wire SHALL be a string holding one of the palette names of shared `domain-label` ("Label color is a palette name"), unchanged and compared case-sensitively. Any other string SHALL be rejected with the field error `INVALID_VALUE` for `color`. A value that is not a string SHALL make the request malformed.

#### Scenario: Palette name accepted
- **WHEN** a label is created with the color `TEAL`
- **THEN** the response is 201 and `color` is `TEAL`

#### Scenario: Mixed-case color rejected
- **WHEN** a label is created with the color `Red`
- **THEN** the response is 400 `VALIDATION_FAILED` with the field error `{ "field": "color", "code": "INVALID_VALUE" }`

#### Scenario: Lower-case color rejected
- **WHEN** a label is created with the color `red`
- **THEN** the response is 400 `VALIDATION_FAILED` with a field error for `color` with code `INVALID_VALUE`

#### Scenario: Color of another type
- **WHEN** a label is created with `"color": 14`
- **THEN** the response is 400 `MALFORMED_REQUEST`

### Requirement: Create a label
`POST /api/v1/labels` SHALL create a label owned by the authenticated user from the required fields `name` and `color`, and SHALL return 201 with the label. `createdAt` and `updatedAt` SHALL both be the time of creation.

#### Scenario: Label created
- **WHEN** a user sends `{ "name": "  Bug ", "color": "RED" }`
- **THEN** the response is 201 with a new `id`, `name` `Bug`, `color` `RED`, and equal `createdAt` and `updatedAt`

#### Scenario: Empty body object
- **WHEN** a user sends `{}`
- **THEN** the response is 400 `VALIDATION_FAILED` with exactly the field errors `name` `REQUIRED` and `color` `REQUIRED`, and no label is stored

#### Scenario: Name too long and unknown color
- **WHEN** a user sends a name of normalized length 31 and the color `MAGENTA`
- **THEN** the response is 400 `VALIDATION_FAILED` with `name` `TOO_LONG` with `max` 30 and `color` `INVALID_VALUE`

#### Scenario: Line break in the name
- **WHEN** a user sends the name `"a\nb"`
- **THEN** the response is 400 `VALIDATION_FAILED` with `name` `INVALID_CHARACTER`

#### Scenario: No token
- **WHEN** a client calls `POST /api/v1/labels` without a token
- **THEN** the response is 401 `UNAUTHENTICATED`

### Requirement: List labels
`GET /api/v1/labels` SHALL return 200 with an object whose only field `labels` is an array of all labels the authenticated user owns, in the label list order of shared `api-conventions`. With no labels, `labels` SHALL be an empty array.

#### Scenario: Sorted list
- **WHEN** a user has the labels `frontend`, `Bug` and `api`
- **THEN** the response is 200 with `{ "labels": [...] }` holding `api`, `Bug`, `frontend` in that order

#### Scenario: Non-ASCII and supplementary characters
- **WHEN** a user has the labels `Zebra`, `Äpfel`, `apple`, `😀` and `ｚ` (U+FF5A)
- **THEN** the list order is `apple`, `Zebra`, `Äpfel`, `ｚ`, `😀`

#### Scenario: No labels
- **WHEN** a user without labels calls the endpoint
- **THEN** the response is 200 with `{ "labels": [] }`

### Requirement: Update a label
`PATCH /api/v1/labels/{labelId}` SHALL change the `name`, the `color` or both of a label the authenticated user owns, and SHALL return 200 with the full label. Both fields SHALL be optional. A field sent as `null` SHALL produce the field error `REQUIRED`.

#### Scenario: Recolor only
- **WHEN** a user sends `{ "color": "BLUE" }` for their label `Bug` colored `RED`
- **THEN** the response is 200 with `name` `Bug` and `color` `BLUE`

#### Scenario: Rename and recolor
- **WHEN** a user sends `{ "name": "Defect", "color": "ROSE" }` for their label `Bug` colored `RED`, one minute after creating it
- **THEN** the response is 200 with `name` `Defect` and `color` `ROSE`, `createdAt` is still the creation time, and `updatedAt` is one minute later

#### Scenario: Null name
- **WHEN** a user sends `{ "name": null }`
- **THEN** the response is 400 `VALIDATION_FAILED` with `name` `REQUIRED`, and the label is unchanged

#### Scenario: Null color
- **WHEN** a user sends `{ "color": null }` for their label colored `RED`
- **THEN** the response is 400 `VALIDATION_FAILED` with exactly the field error `color` `REQUIRED`, and the label is still `RED`

#### Scenario: Unknown color on update
- **WHEN** a user sends `{ "color": "Red" }` for their label colored `RED`
- **THEN** the response is 400 `VALIDATION_FAILED` with exactly the field error `color` `INVALID_VALUE`, and the label is unchanged

#### Scenario: Whitespace-only name
- **WHEN** a user sends `{ "name": "   " }`
- **THEN** the response is 400 `VALIDATION_FAILED` with `name` `REQUIRED`

#### Scenario: Valid color with invalid name
- **WHEN** a user sends a valid `color` and a `name` of normalized length 31
- **THEN** the response is 400 `VALIDATION_FAILED`, and neither the name nor the color changes

### Requirement: Unchanged values do not move the updated time
A label's `updatedAt` SHALL change to the time of the request only when the stored name or the stored color differs after the update. A PATCH whose values equal the stored ones after normalization, and an empty PATCH, SHALL return 200 with the unchanged label. A name that differs only in letter case SHALL count as a change.

#### Scenario: Same color again
- **WHEN** a label is `RED` and, one minute after its last change, a user sends `{ "color": "RED" }`
- **THEN** the response is 200 and `updatedAt` is unchanged

#### Scenario: Same name with surrounding spaces
- **WHEN** a label is named `Bug` and, one minute after its last change, a user sends `{ "name": " Bug " }`
- **THEN** the response is 200 and `updatedAt` is unchanged

#### Scenario: Empty patch
- **WHEN** a user sends `{}` for their label one minute after its last change
- **THEN** the response is 200 with the unchanged label, including `updatedAt`

#### Scenario: One of two fields differs
- **WHEN** a label is `Bug` and `RED` and, one minute after its last change, a user sends `{ "name": "Bug", "color": "BLUE" }`
- **THEN** `updatedAt` changes to the time of the request

#### Scenario: Case-only rename
- **WHEN** a label is named `bug` and, one minute after its last change, a user sends `{ "name": "Bug" }`
- **THEN** the response is 200 with `name` `Bug`, and `updatedAt` changes to the time of the request

### Requirement: Name clashes are conflicts
Creating or renaming a label to a name that, after case folding, equals the name of another label of the same user SHALL return 409 `CONFLICT` with the field error `ALREADY_USED` for `name`, and SHALL store nothing. A label SHALL NOT clash with itself. Field validation SHALL be reported before a name clash.

#### Scenario: Create with a used name
- **WHEN** a user has the label `Bug` and creates a label named `bug`
- **THEN** the response is 409 `CONFLICT` with the field error `{ "field": "name", "code": "ALREADY_USED" }`, and the user still has one label

#### Scenario: Folding beyond lowercase
- **WHEN** a user has the label `Straße` and creates a label named `STRASSE`
- **THEN** the response is 409 `CONFLICT` with `ALREADY_USED` for `name`

#### Scenario: Rename to another label's name
- **WHEN** a user has the labels `Bug` and `Docs` and renames `Docs` to `BUG`
- **THEN** the response is 409 `CONFLICT` with `ALREADY_USED` for `name`, and `Docs` is unchanged

#### Scenario: Rename with a used name and a new color
- **WHEN** a user has the labels `Bug` and `Docs` and sends `{ "name": "bug", "color": "BLUE" }` for `Docs`
- **THEN** the response is 409 `CONFLICT`, and neither the name nor the color of `Docs` changes

#### Scenario: Used name and unknown color
- **WHEN** a user has the label `Bug` and creates a label with the name `bug` and the color `MAGENTA`
- **THEN** the response is 400 `VALIDATION_FAILED` with `color` `INVALID_VALUE` and no entry for `name`

#### Scenario: Same name for another user
- **WHEN** user A has the label `Bug` and user B creates a label named `Bug`
- **THEN** user B's response is 201

#### Scenario: Name free again after delete
- **WHEN** a user deletes their label `Bug` and then creates a label named `bug`
- **THEN** the response is 201

### Requirement: Name uniqueness holds under concurrent requests
The uniqueness of label names per user SHALL hold when requests run at the same time. Of several simultaneous requests that would give one user two labels with the same case-folded name, exactly one SHALL succeed, and each other SHALL return 409 `CONFLICT` with `ALREADY_USED` for `name`.

#### Scenario: Two simultaneous creates
- **WHEN** two create requests of one user for `Bug` and `bug` are released together from a start barrier, repeated for 20 rounds with a new user each round
- **THEN** in every round one response is 201, the other is 409 `CONFLICT` with `ALREADY_USED` for `name`, and the user has exactly one of the two labels

#### Scenario: Clash found only by the database
- **WHEN** the labels `Bug` and `bug` of one user are saved through the label repository, without the service's name check
- **THEN** the second save fails with the name-already-used error and not with a generic database error, and the same two saves for two different users succeed

#### Scenario: Duplicate written past the application
- **WHEN** a second label row with the same owner and the same case-folded name is inserted directly into the database
- **THEN** the database rejects it

### Requirement: Delete a label
`DELETE /api/v1/labels/{labelId}` SHALL permanently delete a label the authenticated user owns and SHALL return 204 with no body. A PATCH or DELETE for a label that another request deleted in the meantime SHALL return 404 `NOT_FOUND` and SHALL NOT recreate the label.

#### Scenario: Label deleted
- **WHEN** a user deletes one of their two labels
- **THEN** the response is 204 with an empty body, and the label list holds only the other label

#### Scenario: Delete twice
- **WHEN** a user deletes a label and sends the same delete again
- **THEN** the second response is 404 `NOT_FOUND`

#### Scenario: Update after a concurrent delete
- **WHEN** a label is deleted after an update request for it was accepted but before that update is stored
- **THEN** the update returns 404 `NOT_FOUND` and the label list does not contain the label

### Requirement: Labels are isolated per user
A label SHALL be readable and changeable only by its owner, the user who created it. For update and delete, a `labelId` that names another user's label, names no label, or is not a UUID SHALL produce the same 404 `NOT_FOUND`, and that 404 SHALL be reported before field validation.

#### Scenario: List shows only own labels
- **WHEN** user A has the label `Bug` and user B lists labels
- **THEN** user B's list does not contain user A's label

#### Scenario: Update another user's label
- **WHEN** user B sends a PATCH with `{ "color": "BLUE" }` for the ID of user A's label
- **THEN** the response is 404 `NOT_FOUND`, identical to the response for an ID that never existed, and user A's label is unchanged

#### Scenario: Delete another user's label
- **WHEN** user B sends a DELETE for the ID of user A's label
- **THEN** the response is 404 `NOT_FOUND` and user A still has the label

#### Scenario: Malformed label ID
- **WHEN** a user sends a PATCH with `{ "color": "BLUE" }`, or a DELETE, to `/api/v1/labels/abc`
- **THEN** each response is 404 `NOT_FOUND`

#### Scenario: Missing label and invalid body
- **WHEN** a user sends a PATCH with a name of normalized length 31 for an ID that names no label
- **THEN** the response is 404 `NOT_FOUND`

#### Scenario: Uppercase label ID
- **WHEN** a user sends a PATCH with `{ "color": "BLUE" }` for their own label with the ID written in uppercase
- **THEN** the response is 200 with `color` `BLUE`, and `id` is lowercase

### Requirement: No limit on the number of labels
The backend SHALL NOT limit how many labels a user has.

#### Scenario: Many labels
- **WHEN** a user with 600 labels creates another one and lists labels
- **THEN** the create returns 201 and the list returns all 601 labels in one response

### Requirement: Label operations in the API document
The generated OpenAPI document SHALL contain the operations `createLabel`, `listLabels`, `updateLabel` and `deleteLabel` with the tag `Labels`. The color SHALL be documented as a string enum with the 20 upper-case values, in requests and responses. `createLabel` and `updateLabel` SHALL document 409 `CONFLICT`, and `updateLabel` and `deleteLabel` SHALL document 404 `NOT_FOUND`.

#### Scenario: Operations and tag
- **WHEN** the committed snapshot is inspected
- **THEN** the four operations exist under `/api/v1/labels` and `/api/v1/labels/{labelId}`, each with the single tag `Labels`

#### Scenario: Color enum
- **WHEN** the label schemas in the snapshot are inspected
- **THEN** `color` is a string enum with exactly the 20 upper-case values in the label, the create request and the update request

#### Scenario: Request fields
- **WHEN** the request schemas are inspected
- **THEN** the create request lists `name` and `color` as required, the update request lists no required field and has exactly the properties `name` and `color`, no request property is nullable, and no schema exists for a request base type

#### Scenario: Error statuses
- **WHEN** the responses of the four operations in the snapshot are inspected
- **THEN** `createLabel` documents exactly 201, 400, 401, 409, 413, 415 and 500, `listLabels` 200, 401 and 500, `updateLabel` 200, 400, 401, 404, 409, 413, 415 and 500, and `deleteLabel` 204, 401, 404 and 500, with `CONFLICT` in the 409 description and `NOT_FOUND` in the 404 description
