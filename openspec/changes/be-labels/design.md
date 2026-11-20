# Design

## Context

`:board` has no code yet. `:common` already provides everything cross-cutting this change needs: `requestUserId`, `@Required`, `@ValidText`, `@ValidEnum`, `RequestValidator`, `ResourceNotFoundException`, `UniquenessConflictException`, `@ApiErrors`, `normalizeText`, the millisecond `Clock` and the handling of malformed path UUIDs (a `@PathVariable` of type `UUID` that can't be converted becomes 404). `:user` is the reference for the layering. `be-boards` runs in parallel in the same module, so this change keeps its files in label-specific classes and touches shared files as little as possible.

Constraints: no Flyway migration yet (dev uses `ddl-auto: update`, tests `create-drop`), no dependency between `:board` and `:user`, fakes instead of mocks, and every decision in `decisions.md`.

## Goals / Non-Goals

**Goals:**
- Label CRUD in `:board` following the existing layer rules. The only addition to `:common` is the PATCH null check (D8).
- Name uniqueness that is exact (full case folding) and safe under concurrent requests.
- A case-folding helper and a color type that `be-cards` and `be-import-export` can reuse.

**Non-Goals:**
- The card–label link table and the card side of label deletion (`be-cards`).
- A Flyway migration, events, rate limiting.
- Changes to `:common` error handling, or to its existing constraints.

## Decisions

### Package layout in `:board`

```
board/
├── api/controller/LabelController
├── api/dto/           LabelDto, LabelListDto, LabelColorDto, CreateLabelRequest, UpdateLabelRequest
├── api/mapper/        LabelDtoMappers.kt
├── service/           LabelService
├── domain/model/      Label, LabelColor, LabelChanges
├── domain/text/       CaseFolding.kt (foldCase, codePointOrder)
├── domain/exception/  LabelNotFoundException, LabelNameAlreadyUsedException
├── domain/repository/ LabelRepository
└── infra/database/    entity/LabelEntity, repository/SpringDataLabelRepository + JpaLabelRepository,
                       mapper/LabelEntityMappers.kt
```

The `.gitkeep` files in packages that now hold code are removed.

### Case folding lives in `:board` `domain/text`, backed by ICU4J (D7)

`foldCase(text)` calls `UCharacter.foldCase(text, true)` (default mappings, locale-independent, full folding). It sits in `:board` because labels, card labels and import are its only users and all live there; putting it in `:common` would add ICU to a module that doesn't need it. The `domain` layer may use it: the layer rule forbids Spring and `jakarta.persistence`, not plain libraries.

`Label` exposes `nameKey` as a computed property (`foldCase(name)`), so the key can never drift from the name.

*Alternative:* computing the key in the entity mapper only. Rejected, because the service needs the key for the clash check and the sort.

### Data model

Table `board_service.labels`:

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | primary key, assigned in the service |
| `owner_id` | `uuid` | not null. The user's ID, no foreign key (separate schema, separate module) |
| `name` | `varchar(30)` | not null, as entered after normalization |
| `name_key` | `varchar(90)` | not null, `foldCase(name)`. Folding can turn one code point into up to three |
| `color` | `varchar(7)` | not null, the constant name such as `FUCHSIA` |
| `created_at` | `timestamptz` | not null |
| `updated_at` | `timestamptz` | not null |

Unique constraint `labels_owner_name_key_unique` on (`owner_id`, `name_key`). It is the uniqueness guarantee, and its index also serves the list query by owner. Postgres counts `varchar(n)` in characters, which matches code points.

`LabelColor` is a domain enum with the 20 upper-case constants. `LabelColorDto` is a separate API enum with the same constant names; the mapper converts by name. The entity stores the color with `@Enumerated(EnumType.STRING)`.

### Uniqueness: check first, constraint as the backstop

`LabelService` checks `existsByOwnerAndNameKey` before saving (excluding the label itself on update) and throws `LabelNameAlreadyUsedException`, a `UniquenessConflictException` for the field `name`. That covers the normal case with a clean error path.

Two racing requests can both pass the check. `JpaLabelRepository.save` therefore uses `saveAndFlush` and translates a `DataIntegrityViolationException` whose constraint is `labels_owner_name_key_unique` into the same `LabelNameAlreadyUsedException`. Any other integrity violation is rethrown and ends as a 500.

*Alternative:* only the constraint, no pre-check. Rejected: every ordinary clash would go through an exception from the database and mark the transaction rollback-only, which gets in the way once `be-import-export` creates labels inside a larger transaction.

*Alternative:* a per-user lock. Rejected as more machinery than a constraint that is needed anyway.

### List order is computed in the service

The list is sorted in Kotlin by `nameKey` with a code-point comparator (`codePointOrder`); the key of each label is computed once per request, not per comparison. `String.compareTo` compares UTF-16 code units, which puts supplementary characters such as emoji before U+E000–U+FFFF, so it must not be used. `be-cards` reuses the comparator for card labels.

*Alternative:* `ORDER BY name_key COLLATE "C"` in a native query. It gives the same order on a UTF-8 database, but ties the rule to database settings and can't be unit tested.

### Request flow

- **Create**: `@Valid @RequestBody CreateLabelRequest` (no references, so `@Valid` is enough) → mapper normalizes the name and converts the color → `LabelService.create(ownerId, name, color)`.
- **Update**: `@RequestBody UpdateLabelRequest` before `@PathVariable labelId: UUID`. The controller calls `LabelService.get(ownerId, labelId)` first so a missing label is 404, then `RequestValidator.validate(request)`, then `LabelService.update(ownerId, labelId, changes)`. `LabelChanges` holds a nullable name and a nullable color, where null means "not sent". A JSON `null` never gets that far, see "Telling `null` from absent" below.
- **Delete**: `LabelService.delete(ownerId, labelId)`, 404 when the repository finds nothing for that owner.
- **List**: `LabelService.list(ownerId)` → `LabelListDto(labels)`.

Every repository query takes the owner ID (`findByIdAndOwnerId`, `deleteByIdAndOwnerId`, ...), so a foreign label is indistinguishable from a missing one.

`LabelService.update` loads the label with a row lock (`findByIdAndOwnerId` with `PESSIMISTIC_WRITE`) inside its own transaction and throws `LabelNotFoundException` when it finds nothing, so a concurrent delete either waits or is seen. Delete uses `deleteByIdAndOwnerId` and treats an affected-row count of 0 as `LabelNotFoundException`; it never loads first.

### Telling `null` from absent in the update request

Shared `api-conventions` wants `{ "name": null }` rejected with `REQUIRED` but an absent `name` accepted. A plain nullable Kotlin constructor property can't tell the two apart, and `:common` has no helper for it yet, because no PATCH endpoint exists. `UpdateLabelRequest` therefore records which properties were sent as `null`, and a class-level constraint `@NotNullWhenPresent` reports `REQUIRED` at the property's path for each of them.

`:common` `api/validation` gets an abstract class `PatchRequest`. It keeps a private set of the property names that were sent as JSON `null` and offers `protected fun <T> sent(name: String, value: T?): T?`, which records the name when `value` is null, and `fun nullProperties(): Set<String>`. It has no public property, so neither Jackson nor springdoc sees anything of it and `openapi.json` is unaffected. A PATCH request DTO is a plain class (not a data class) that extends it and declares each field in the body as `var name: String? = null` with `set(value) { field = sent("name", value) }`, with the usual constraints on the field. Jackson calls a setter only for a property that is present, also for an explicit `null`. This is the one deliberate exception to the rule that request properties are constructor properties with no default. `@NotNullWhenPresent` is a class-level constraint on `PatchRequest`. Its validator disables the default violation and adds one violation per name in `nullProperties()` with `addPropertyNode(name)` and the dynamic payload `FieldViolation(REQUIRED)`. Field constraints skip null, so a sent value is still checked by its own constraints.

The constraint and `PatchRequest` live in `:common` `api/validation`, next to `@Required` and `@ValidText` (D8). `be-boards` needs the same thing for its PATCH endpoints. Only one change adds it: whichever is implemented first. The other reuses it and drops its own task for it. In the OpenAPI document neither property is required and neither is nullable, as the project rules say; `CardiganOpenApiCustomizer` needs no change for that: it reads the declared fields of the DTO, body properties have backing fields, and it only marks `@Required` fields. The request schema holds only `name` and `color`, and no schema exists for `PatchRequest`.

*Alternative:* `Optional<String>?` properties, where Jackson maps an explicit `null` to an empty `Optional`. Rejected: the OpenAPI customizer and the `:common` constraints read plain `String` fields, so both would need changes in `:common`.

### Update semantics (D5)

`LabelService.update` loads the label, applies the sent fields, and compares the result with the stored label on `name` and `color` (exact string comparison of the normalized name, so a case-only rename is a change). If nothing differs it returns the stored label without saving. Otherwise it sets `updatedAt = clock.instant()` and saves. The clash check runs only when the `nameKey` changes. The label is loaded with a row lock, so two updates of one label run one after the other and an absent field always keeps the value of the latest committed update.

### OpenAPI

`LabelController` carries `@Tag(name = "Labels")`, explicit `operationId`s, `@ApiErrors(409, CONFLICT)` on create and update, and `@ApiErrors(404, NOT_FOUND)` on update and delete. The color request fields are `String` with `@ValidEnum(LabelColorDto::class)`, so the document shows the enum values. The color enum is inline in `LabelDto` and both requests. There is no `LabelColorDto` component. The snapshot is rewritten with `./gradlew :app:updateOpenApiSnapshot` once, on purpose.

### Tests

- Unit (`:board`): `foldCase` and `codePointOrder` (`ß`/`SS`, `ẞ`, Greek final sigma, Turkish dotted `İ`, emoji vs. U+FF5A), `Label` invariants, mappers, and `LabelService` against a fake `LabelRepository` and `MutableClock` (no-op PATCH, case-only rename, clash rules, ownership).
- Persistence (`:board`, `@DataJpaTest` with `BoardTestApplication` and `PostgresTestConfig`): round trip, owner-scoped queries, the unique constraint through a native insert, and the translation of the constraint violation.
- Module integration (`:board`, `@SpringBootTest`): two concurrent creates of `Bug` and `bug`, both held after the name check until each has passed it, end with one label and one `LabelNameAlreadyUsedException` from the constraint. The same for two renames to one folded name, and an update after a delete ends in `LabelNotFoundException`.
- E2E (`:app`, `LabelE2eTest`): every scenario of the `labels` spec that can run over HTTP, including the two simultaneous creates. `E2eTest` also cleans `board_service`.

## Risks / Trade-offs

- [ICU4J adds about 14 MB to the jar and brings its own Unicode version, which can be newer than the JDK's used for NFC] → Accepted (D7). Folding only depends on case mappings, which are stable for assigned characters.
- [`be-boards` works in the same module at the same time] → Label code is in its own classes. The shared touch points are `@NotNullWhenPresent` in `:common`, `board/build.gradle.kts`, `BoardTestApplication`, the `.gitkeep` files, the `module-boundaries` delta and the snapshot; whichever change lands second merges them. The archive guidance in `openspec/config.yaml` covers the spec delta.
- [No foreign key from `owner_id` to users] → Accepted by the module rules. No account deletion exists in v1, so no orphaned labels.
- [A table exists without a migration] → `prod` fails to start until the baseline migration is written, as `CLAUDE.md` already states. Nothing deploys before `infra-vps`. The migration must carry the unique constraint under the same name; `validate` does not check it (see tasks.md, Workflow follow-up).
- [No label limit (D6), no rate limit on label endpoints, and a complete in-memory sorted list] → One account can create labels without bound and then make `GET /api/v1/labels` load, sort and return all of them, which can exhaust the heap of the single instance. Accepted for v1 by D6. A cap needs a shared-spec change and the user's decision. To keep the cost per request down, the list sort folds each name once (it sorts by the stored key, not by calling `foldCase` inside the comparator).
- [Hibernate `ddl-auto: update` doesn't add a unique constraint to a table that already exists] → The table is new, so dev gets the constraint on first start. If the constraint name or columns change during implementation, the dev table must be dropped by hand.
