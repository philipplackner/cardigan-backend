# Proposal

## Why

The coverage and mutation tasks are new, and their first run shows a gap. Line coverage is high in every module (93.8–99.6%), but the mutation score is only 48.9% overall. `:common` is at 34.7%. Most mutants that aren't killed have no unit-test coverage at all: the code is only reached by integration and E2E tests, which PIT leaves out. This change raises the floor once, so the unit test suite actually catches broken logic in that code.

## What Changes

- Add plain unit tests (no Spring, no Docker) for the code that only integration and E2E tests reach today:
  - `CardiganOpenApiCustomizer`, `CommonExceptionHandler`, `ProblemErrorController`, `ProblemResponseWriter`, `ProblemDetailBodyAdvice`, `RequestSizeLimitFilter`, `PublicRouteRegistry`, `ApiBearerTokenResolver` and the other small `:common` web helpers
  - the controllers in `:user` and `:board`
  - DTO getters, checked field by field through mapper and controller tests
- Add tests for the surviving mutants in validators, services, mappers and domain helpers in all three modules.
- Where a surviving mutant shows redundant production code, simplify it in a separate refactor commit, with no behavior change.
- Exclude JPA adapters (`@Repository`) and JPA entities (`@Entity`) from mutation testing, the same way `@Configuration` classes already are. Persistence and E2E tests keep testing them, and Kover still measures them.
- One-time targets for this change, which are not recorded anywhere else and not enforced: line coverage of at least 80% and a mutation score of at least 80% in each of `:common`, `:user` and `:board`. A mutation score of at least 90% for `:user` plus the JWT code in `:common` `infra/security`.

No API, data or security behavior changes. Nothing is breaking.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `mutation-testing`: the "Non-logic classes are not mutated" requirement also excludes classes annotated `@Repository` or `@Entity`.

### Shared specs

This change implements no shared-spec requirement and changes none. The new tests check backend behavior that already implements shared specs, mainly `api-errors`, `api-conventions`, `api-auth-tokens` and `text-length`. They check it as it is and don't restate it.

## Impact

- **Tests**: new unit tests in `common/src/test`, `user/src/test` and `board/src/test`. They might need a small shared fake or helper in the module's `fake` package.
- **Build logic**: `NON_LOGIC_CLASS_ANNOTATIONS` in `build-logic/convention/src/main/kotlin/NonLogicClassScanTask.kt` and its tests.
- **Production code**: only behavior-neutral simplifications that surviving mutants point to.
- **Specs**: `openspec/specs/mutation-testing/spec.md` (delta).
- **Unchanged**: the OpenAPI snapshot, endpoints, schema, the coverage tooling and every default threshold. `CLAUDE.md` changes only in the mutation-testing bullet, to name `@Repository` and `@Entity` classes.
