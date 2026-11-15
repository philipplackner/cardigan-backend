# Proposal

## Why

Cards carry user-defined labels, and both clients have a label management screen. `be-cards`, `mob-labels-connect` and `web-labels-connect` are blocked until labels exist on the backend. This change builds them (breakdown chunk `be-labels`). See `decisions.md` for every settled choice.

## What Changes

- Four protected endpoints with the tag `Labels` (D3):
  - `POST /api/v1/labels` (`createLabel`) creates a label and returns 201 with it.
  - `GET /api/v1/labels` (`listLabels`) returns all of the user's labels as `{ "labels": [...] }` (D4).
  - `PATCH /api/v1/labels/{labelId}` (`updateLabel`) renames, recolors or both, and returns 200 with the label.
  - `DELETE /api/v1/labels/{labelId}` (`deleteLabel`) deletes the label and returns 204.
- A label has `id`, `name`, `color`, `createdAt` and `updatedAt`.
- Colors are upper-case constants on the wire, `AMBER` ... `YELLOW` (D1). This differs from the shared specs as they stand today (D2).
- Names are unique per user after full Unicode case folding. The folded name is stored next to the name as entered, and the database enforces the uniqueness, so two racing requests can't both win (D7).
- A PATCH whose values equal the stored ones leaves `updatedAt` alone (D5).
- No limit on the number of labels per user (D6).
- A new `labels` table in the schema `board_service`. No Flyway migration yet; dev and tests build the schema from the entity.
- New dependency: `com.ibm.icu:icu4j` in `:board` (D7).
- `openapi.json` gets the four operations and the label schemas.
- First production code in `:board`, so the `module-boundaries` scenario that expects no source files there changes.

Not in this change: attaching labels to cards, and removing a deleted label from cards (`be-cards`). Deleting a label here removes only the label.

## Shared requirements implemented or depended on

| Spec | Requirement | How |
|---|---|---|
| `domain-label` | Labels have a backend-assigned identifier | Implemented |
| `domain-label` | Labels are shared across a user's boards | Labels are stored per user, not per board. Attaching is `be-cards` |
| `domain-label` | Label name | Implemented |
| `domain-label` | Label names are unique per user ignoring case | Implemented |
| `domain-label` | Label color is a palette name | Implemented. **Deviates**: values are upper case (D1). Depends on a pending shared-spec change (D2) |
| `domain-label` | Label timestamps | Implemented |
| `domain-label` | Deleting a label removes it from cards | The label is deleted here. The card part is `be-cards` |
| `domain-user` | Boards and labels belong to exactly one user | Implemented for labels |
| `domain-user` | Other users' data is treated as not found | Implemented for labels |
| `text-length` | Text is normalized before validation | Used for `name` |
| `text-length` | Length is counted in Unicode code points | Used for `name` |
| `text-length` | Invalid characters are rejected | Used for `name` (single line) |
| `text-length` | Backend rejects text outside its length limits | Used for `name` |
| `text-length` | Backend stores and returns normalized text | Implemented for `name` |
| `api-conventions` | Updates are partial | Implemented for labels |
| `api-conventions` | Null and empty updates | Implemented for labels |
| `api-conventions` | Success status codes and bodies | Implemented for labels |
| `api-conventions` | Deleting a missing resource is not found | Implemented for labels |
| `api-conventions` | Malformed path identifiers are not found | Used for `labelId` |
| `api-conventions` | Lists are complete and unpaginated | Implemented for labels |
| `api-conventions` | Label list order | Implemented |
| `api-conventions` | Documented fields are always present | Implemented for labels |
| `api-conventions` | Null values | No label field is nullable |
| `api-errors` | Field error codes | `INVALID_VALUE` for an unknown color, text codes for `name` |
| `api-errors` | Validation errors report all broken fields | Used |
| `api-errors` | Uniqueness clashes are conflicts | Implemented for label names |
| `api-errors` | Other users' data is not found, never forbidden | Implemented for labels |
| `api-errors` | Error precedence | 404 before field validation before 409 |
| `api-auth-tokens` | Protected requests use a bearer access token | All four endpoints are protected |

**Dependency on a shared-spec change:** D1 writes colors as `RED`, which contradicts `domain-label` ("Label color is a palette name") and `board-export-format`, both of which use `Red`. A follow-up change in the shared repo switches both to `AMBER` ... `YELLOW` (D2), arranged by the coordinating session. This change can be implemented before that, but must not be archived until the shared specs have been updated. Otherwise the backend and the shared specs disagree.

## Capabilities

### New Capabilities

- `labels`: the backend's label endpoints and their bodies, the color spelling on the wire, how name uniqueness holds under concurrent requests, when a PATCH moves `updatedAt`, and label isolation between users.

### Modified Capabilities

- `module-boundaries`: "Module layer packages exist from the start" — `:board` now holds production code, so the "no source files" scenario becomes a check for placeholder classes in empty packages.

## Impact

- **Code**: `:board` (label domain model, service, controller, DTOs, mappers, entity, repository, case-folding helper, module test app). `:app` (E2E tests, database cleanup of `board_service` in the E2E base class).
- **API**: four new operations and the schemas `LabelDto`, `LabelListDto`, `LabelColorDto`, `CreateLabelRequest` and `UpdateLabelRequest` in `openapi.json`. Additive only.
- **Dependencies**: `com.ibm.icu:icu4j` in `gradle/libs.versions.toml`, used by `:board`. About 14 MB more in the app jar.
- **Database**: table `board_service.labels`. Dev creates it through `ddl-auto: update`. It becomes part of the baseline migration before the first production deploy.
- **Other chunks**: `be-cards` attaches labels and extends label deletion. `be-import-export` reuses the case-folding helper and the color constants. `mob-labels-connect` and `web-labels-connect` generate from the updated `openapi.json`.
- **Shared specs**: the upper-case follow-up change in `cardigan-shared-specs` (D2), outside this repo.
