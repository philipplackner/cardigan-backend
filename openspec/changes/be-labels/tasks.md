# Tasks

## 1. Module setup

- [ ] 1.1 Add `com.ibm.icu:icu4j` (latest stable release, looked up at implementation) to `gradle/libs.versions.toml` and to `board/build.gradle.kts`, together with the test dependencies `:board` needs (`spring-boot-starter-data-jpa-test`, `:common` test fixtures, as in `user/build.gradle.kts`). Verify with `./gradlew :board:dependencies` listing ICU4J and `./gradlew build` passing.
- [ ] 1.2 Add `BoardTestApplication` in `board/src/test` (as `UserTestApplication`), unless `be-boards` already added it. Verify with an empty `@DataJpaTest` context starting against Testcontainers Postgres.

## 2. Case folding and domain model

- [ ] 2.1 Add `foldCase` and the `codePointOrder` comparator in `:board` `domain/text`. Verify with adversarial unit tests: `Bug`/`bug`, `Straße`/`STRASSE`, capital `ẞ`, Greek `Σ`/`σ`/`ς`, Turkish `İ` and `I` (locale-independent), the ligature `ﬃ` folding to three code points, text that is already folded, and the order `apple`, `Zebra`, `Äpfel`, `ｚ` (U+FF5A), `😀`.
- [ ] 2.2 Add `LabelColor` (20 upper-case constants), `Label` (with the computed `nameKey`), `LabelChanges`, `LabelRepository`, `LabelNotFoundException` and `LabelNameAlreadyUsedException` (field `name`) in `:board` `domain`. Remove the `.gitkeep` of each package that now holds code. Verify with unit tests that `LabelColor` has exactly the 20 names from `decisions.md` D1 and that `nameKey` follows the name, and with a check that `domain` imports no Spring and no `jakarta.persistence`.

## 3. Persistence

- [ ] 3.1 Add `LabelEntity` (table `labels`, schema `board_service`, columns and the unique constraint `labels_owner_name_key_unique` from `design.md`), `SpringDataLabelRepository`, `JpaLabelRepository` with owner-scoped queries and `saveAndFlush`, and `LabelEntityMappers.kt`. Verify with `@DataJpaTest` tests: round trip with millisecond timestamps, `findByIdAndOwnerId` returning nothing for another owner, list by owner returning only that owner's labels, delete by ID and owner leaving another owner's label alone, and a native insert of a second row with the same owner and `name_key` rejected by the database.
- [ ] 3.2 Translate a violation of `labels_owner_name_key_unique` in `JpaLabelRepository.save` into `LabelNameAlreadyUsedException`, and rethrow every other integrity violation. Verify with a `@DataJpaTest` test that saves two labels with the names `Bug` and `bug` for one owner through the adapter and expects the exception, and one that saves the same names for two owners and succeeds.

## 4. Service

- [ ] 4.1 Add `LabelService` with `create`, `list`, `get`, `update` and `delete`, using the `Clock` bean and the rules in `design.md` (clash check excluding the label itself, no save and no new `updatedAt` when nothing differs, sorted list). Add a fake `LabelRepository` in `board/src/test/.../fake`. Verify with unit tests, one per break vector: clash on create by folded name, same name for another owner, rename to another label's folded name, rename to a case variant of the own name (allowed, `updatedAt` moves), same color again (no change), same name again (no change), one of two fields differing, empty changes, `createdAt` never changing, update and delete of another owner's label and of an unknown ID (`LabelNotFoundException`), name free again after delete, and list order.
- [ ] 4.2 Verify uniqueness under concurrency with a `@SpringBootTest` module integration test in `:board`: two threads create `Bug` and `bug` for one owner at the same time, exactly one succeeds, the other gets `LabelNameAlreadyUsedException`, and one row exists.

## 5. API

- [ ] 5.1 Add `LabelColorDto`, `LabelDto`, `LabelListDto`, `CreateLabelRequest` (`name`: `@Required` + `@ValidText(min = 1, max = 30)`, `color`: `@Required` + `@ValidEnum(LabelColorDto::class)`) and `LabelDtoMappers.kt`, which calls `normalizeText` for the name. Verify with unit tests for the mappers: the name is normalized, every `LabelColorDto` maps to a `LabelColor` and back, and the two enums have the same constant names.
- [ ] 5.2 Check whether `be-boards` already added the PATCH null check to `:common` `api/validation`. If not, add the "sent properties" base type and `@NotNullWhenPresent` there, next to `@Required` (D8), and verify with a unit test in `:common` on the validator: an absent property passes, a property sent as `null` gives `REQUIRED` at that property's path, and a sent value is checked by its own constraints. If it exists, reuse it and add nothing. Then add `UpdateLabelRequest` with optional `name` and `color` on top of it.
- [ ] 5.3 Add `LabelController` under `/api/v1/labels` with the tag `Labels`, the operationIds `createLabel`, `listLabels`, `updateLabel` and `deleteLabel`, `@ApiErrors` for 409 and 404 as in `design.md`, `@RequestBody` before `@PathVariable`, and `RequestValidator.validate` after the label was resolved on update. Add `LabelE2eTest` in `:app` and make `E2eTest` clean `board_service` too. Verify with E2E tests for every scenario of `specs/labels/spec.md` except the snapshot ones: representation, colors (`TEAL`, `Red`, `red`, `14`), create (including `{}` and no token), list (order, empty, 601 labels), update (partial, null, whitespace-only, validation storing nothing), unchanged values with the clock moved forward, name clashes, delete (including twice), and isolation between two signed-in users (foreign, unknown, malformed and uppercase IDs, 404 before field errors).

## 6. API contract

- [ ] 6.1 Run `./gradlew :app:updateOpenApiSnapshot` and review the diff of `app/src/test/resources/openapi/openapi.json`: only the four label operations and the label schemas are added, `color` is a string enum with the 20 upper-case values in `LabelDto` and both requests, `CreateLabelRequest` requires `name` and `color`, `UpdateLabelRequest` requires nothing, no request property is nullable, and every operation documents its error statuses. Verify with `OpenApiSnapshotTest` passing and an `ApiDocsE2eTest` case for the `labels` scenarios "Operations and tag", "Color enum" and "Request fields".

## 7. Integration and project rules

- [ ] 7.1 Update the "Current state" paragraph in `AGENTS.md` (`CLAUDE.md` is a symlink to it): `:board` has the `labels` table and the label endpoints, and colors are upper-case constants. Verify by reading the paragraph against the code.
- [ ] 7.2 Start the app with `SPRING_PROFILES_ACTIVE=dev ./gradlew :app:bootRun` and check through the Supabase MCP (dev branch) that `board_service.labels` exists with the unique constraint on (`owner_id`, `name_key`). Verify by listing the table and its constraints.
- [ ] 7.3 Run `./gradlew build` and `openspec validate be-labels --strict`. Verify that both pass, then commit per the Git workflow in `CLAUDE.md`.

## Workflow follow-up

- The shared-spec dependency (`decisions.md` D2) is met, so this change can be archived once it is implemented and verified.
- When archiving, follow the archive guidance in `openspec/config.yaml` for the `module-boundaries` delta, which `be-boards` may also touch.
