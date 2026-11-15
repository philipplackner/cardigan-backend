# Decisions: be-labels

Confirmed with Philipp Lackner on 2026-11-15, through the `cardigan-cd` planning session, which relayed the questions and answers.

## Summary

This change adds user-defined labels to the `:board` module (breakdown chunk `be-labels`): create, list, rename, recolor and delete, with names unique per user after Unicode case folding and colors from the fixed palette. Labels belong to one user and are invisible to everyone else. The API writes palette colors as upper-case constants, which differs from the current shared specs, so a follow-up change in the shared repo switches them to upper case. Attaching labels to cards, and removing a deleted label from cards, stays in `be-cards`.

## Decisions

### D1 — Color format on the wire
- **Question**: How is a label's color written on the wire (the chunk's open question, names vs. ids)?
- **Decision**: Upper-case constants: `AMBER`, `BLUE`, `BROWN`, `CYAN`, `EMERALD`, `FUCHSIA`, `GREEN`, `INDIGO`, `LIME`, `OLIVE`, `ORANGE`, `PINK`, `PURPLE`, `RED`, `ROSE`, `SKY`, `SLATE`, `TEAL`, `VIOLET`, `YELLOW`. The value is case-sensitive. Anything else, including `Red` or `red`, is rejected with `INVALID_VALUE` on `color`. This deviates from shared `domain-label` ("Label color is a palette name") and `board-export-format`, which use `Red`; D2 resolves it.
- **Alternatives**: The palette names exactly as in the shared spec (`Red`); numeric ids.
- **Why**: —
- **Depends on**: —

### D2 — Shared specs follow the upper-case spelling
- **Question**: How is the mismatch between `"RED"` in the API and `Red` in the shared specs resolved?
- **Decision**: Upper case everywhere. A follow-up change in the shared specs repo switches `domain-label` and `board-export-format` to `AMBER` ... `YELLOW`, so the API, the export file and the specs use one spelling and import/export needs no translation. `be-labels` depends on that change and does not edit the shared repo itself.
- **Alternatives**: Change only the API side in the shared specs and keep `Red` in the export file, with translation in `be-import-export`; no shared-spec change, with the deviation recorded only in this repo.
- **Why**: Nothing has shipped, so the export format can still change without a new `formatVersion`.
- **Depends on**: D1

### D3 — Endpoints
- **Question**: Which label endpoints does this chunk ship, and under which operationIds and tag?
- **Decision**: Four protected endpoints with the tag `Labels`, and no get-by-id:
  - `POST /api/v1/labels` — `createLabel`, 201 with the label
  - `GET /api/v1/labels` — `listLabels`, 200
  - `PATCH /api/v1/labels/{labelId}` — `updateLabel`, 200 with the label
  - `DELETE /api/v1/labels/{labelId}` — `deleteLabel`, 204
- **Alternatives**: Five endpoints, adding `GET /api/v1/labels/{labelId}` (`getLabel`).
- **Why**: The list is always complete and create/update return the full label. Get-by-id can be added later without breaking anything.
- **Depends on**: —

### D4 — List response shape
- **Question**: What is the response body of the label list?
- **Decision**: A wrapper object `{ "labels": [...] }` as the named DTO `LabelListDto`. An empty list is `{ "labels": [] }`. `be-boards` applies the same rule.
- **Alternatives**: A bare array of labels.
- **Why**: Within v1 the API may only add fields, and a bare array can never get one.
- **Depends on**: —

### D5 — PATCH with unchanged values
- **Question**: What happens to `updatedAt` when a PATCH sends values equal to the stored ones?
- **Decision**: `updatedAt` moves only when the stored name or color really differs after normalization. The response is 200 with the full label either way. A change in letter case only (`bug` to `Bug`) is a real change. `be-boards` applies the same rule.
- **Alternatives**: Bump `updatedAt` on every PATCH that has at least one field.
- **Why**: Matches the empty-PATCH rule and the shared wording "when the label's name or color changes".
- **Depends on**: —

### D6 — Label limit
- **Question**: Is there a maximum number of labels per user?
- **Decision**: No limit.
- **Alternatives**: A cap, which would need a shared-spec change first.
- **Why**: The shared specs define none.
- **Depends on**: —

### D7 — Case folding
- **Question**: How does the backend compute full Unicode default case folding for name uniqueness and list order?
- **Decision**: A new dependency `com.ibm.icu:icu4j` in `:board`, using `UCharacter.foldCase`. The folded name is stored in a column `name_key` next to the name as entered, with a unique constraint on (owner, `name_key`). The list is sorted by `name_key` in code point order. `be-cards` and `be-import-export` reuse the same function.
- **Alternatives**: Bundling Unicode's `CaseFolding.txt` and folding with our own function; `uppercase(ROOT).lowercase(ROOT)` from the JDK, which isn't exactly the Unicode algorithm.
- **Why**: ICU is the reference implementation and stays current with Unicode.
- **Depends on**: —

### D8 — Location of the PATCH null check
- **Question**: Where does the constraint live that rejects an optional PATCH field sent as `null` with `REQUIRED`, while accepting an absent field? `:common` has no such helper yet.
- **Decision**: In `:common` `api/validation`, next to `@Required` and `@ValidText`. `be-boards` reuses the same constraint. Only one change adds it: whichever is implemented first. Decided after the first confirmation, when the design raised the point.
- **Alternatives**: In `:board` `api/validation`, since every PATCH endpoint of v1 is in `:board`.
- **Why**: —
- **Depends on**: —

## Settled by existing rules

Recorded here, not asked again.

- A label has `id`, `name`, `color`, `createdAt` and `updatedAt`, all always present — source: shared `domain-label`; shared `api-conventions`, "Success status codes and bodies", "Documented fields are always present".
- The name is required, single-line, 1 to 30 code points after normalization, and stored as entered after normalization — source: shared `domain-label`, "Label name"; shared `text-length`.
- Names are unique per user after case folding. A clash is 409 `CONFLICT` with `ALREADY_USED` on `name` — source: shared `domain-label`, "Label names are unique per user ignoring case"; shared `api-errors`, "Uniqueness clashes are conflicts".
- One PATCH changes name, color or both. Absent fields keep their values, `null` is `REQUIRED`, and `{}` changes nothing — source: shared `api-conventions`, "Updates are partial", "Null and empty updates".
- A label id that is missing, malformed or owned by another user is 404 `NOT_FOUND`, also on delete. 404 wins over field errors, and field errors win over the 409 — source: shared `api-errors`, "Other users' data is not found, never forbidden", "Error precedence"; shared `api-conventions`, "Malformed path identifiers are not found", "Deleting a missing resource is not found".
- The list is complete, unpaginated and sorted by the code points of the case-folded name — source: shared `api-conventions`, "Lists are complete and unpaginated", "Label list order".
- Labels live in `:board`, in the schema `board_service`. The owner is stored as the user's id with no foreign key into `user_service` — source: `CLAUDE.md`, "Principles" (each module owns its data).
- The owner is the access token's `sub`, with no user lookup — source: `CLAUDE.md`, "Authentication"; `be-common` D3.
- No Flyway migration in this change. Dev and tests build the schema from the entity until the baseline migration exists — source: `CLAUDE.md`, "Database".
- All test types are expected, with Testcontainers Postgres and fakes — source: `CLAUDE.md`, "Testing"; chunk `10-be-labels.md`, "Done when".

## Facts

- The JDK has no case-folding function. `lowercase()` treats `ß` and `SS` as different, and `uppercase().lowercase()` differs from Unicode default case folding for some characters, such as capital `ẞ` — source: JDK `String` docs; Unicode `CaseFolding.txt`.
- Postgres gets `casefold()` only in version 18. Supabase runs Postgres 17, so folding has to happen in the application — source: PostgreSQL 18 release notes; `CLAUDE.md`, "Testing".
- Names are unique per user after folding, so the list order never has ties — source: shared `domain-label`.
- A rename to a case variant of the label's own name clashes with no other label, so it is allowed — source: shared `domain-label`, "Label names are unique per user ignoring case" ("No two labels").
- `@ValidEnum` accepts the constant names of the DTO enum, and the OpenAPI customizer lists the same names, so upper-case constants need no extra mapping — source: `common/.../api/validation/Validators.kt`, `common/.../api/openapi/CardiganOpenApiCustomizer.kt`.
- `:board` has no code yet and applies the `spring.module` and `spring.jpa` convention plugins. ICU4J is not in the version catalog — source: `board/build.gradle.kts`, `gradle/libs.versions.toml`.
- `UniquenessConflictException`, `ResourceNotFoundException`, `RequestValidator`, `@Required`, `@ValidText`, `@ValidEnum`, `requestUserId` and `E2eTest.signIn` already exist — source: `common/src/main`, `CLAUDE.md`.
- No account deletion exists in v1, so labels are never orphaned by a deleted user — source: breakdown `OVERVIEW.md`, scope; `be-common` D3.
- The shared specs that still spell colors as `Red`: `domain-label` ("Label color is a palette name" and its scenarios) and `board-export-format` (file label colors such as `Teal`, `Blue`, `Red`, `Magenta`) — source: `cardigan-shared-specs/openspec/specs`.

## Out of scope

- Attaching labels to cards, and removing a deleted label from cards with their `updatedAt` change (`be-cards`). Delete in this change removes only the label.
- Import and export of labels (`be-import-export`).
- A get-by-id endpoint (D3) and a label limit (D6).
- Editing the shared specs repo. The upper-case follow-up change (D2) is arranged by the coordinating session.
- Events, rate limiting and public routes. Labels need none.
- A Flyway migration (arrives with the baseline migration).

## Deferred

Nothing.
