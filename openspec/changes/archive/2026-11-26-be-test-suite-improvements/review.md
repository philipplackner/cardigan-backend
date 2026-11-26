# Artifact review (light): be-test-suite-improvements

**Verdict:** Ready
**Reviewed:** 2026-11-26, schema `careful-planning`, repo kind `code`

| Pass | Blocker | Major | Minor |
|---|---|---|---|
| Testability | 0 | 0 | 1 |
| Security | 0 | 1 | 0 |
| Hidden assumptions | 0 | 1 | 1 |
| Design contract | 0 | 2 | 1 |

## Findings

### D1: Tasks add targets that decisions.md doesn't set
**Severity:** major · **Status:** applied
**Location:** tasks.md > 4.3 and 5.6
**Current text:** 4.3: "Verify that the `:common` mutation score is ≥ 80% and that `infra/security` has no surviving or no-coverage mutants." 5.6: "Verify that it is ≥ 90% and that the `:user` module score is ≥ 90%"
**Failure scenario:** D3 sets 80% per module and 90% for the combined D1 scope (`:user` + `:common` `infra/security`). The tasks also require 100% in `infra/security` and 90% for `:user` alone. An implementer who reaches D3 still fails these checks and keeps adding tests, or stops and reports a miss that isn't one.
**Fix:** 4.3: "Verify that the `:common` mutation score is ≥ 80%. Note the `infra/security` counts for task 5.6." 5.6: "Verify that the auth-scope score is ≥ 90%."

### D2: The customizer fallback starts a Spring context without the integration tag
**Severity:** major · **Status:** applied
**Location:** design.md > Risks / Trade-offs (second bullet); tasks.md > 4.2
**Current text:** "If so, use a `StaticApplicationContext` with only the test controllers. It is still in memory with no Boot context or container, so no integration tag is needed."
**Failure scenario:** CLAUDE.md requires `@Tag("integration")` on every test class that "starts a Spring context", and the main `mutation-testing` spec says only tests that start no Spring context run against mutants. A `StaticApplicationContext` is a Spring context. Following the fallback breaks both rules. It also contradicts the design's own reason for rejecting `MockMvc` standalone.
**Fix:** Replace the bullet's fallback with: "If so, keep the customizer covered only by its existing Spring test, record its remaining mutants in the final report, and tell the user early, because `:common` then reaches 80% only if most other classes are fully killed." In task 4.2, remove "(or a `StaticApplicationContext` if needed, see design.md Risks)".

### S1: Behavior-neutral refactors can remove security checks
**Severity:** major · **Status:** applied
**Location:** design.md > Surviving mutants: assert harder, or simplify (D8); tasks.md > 3.5, 5.5, 6.3
**Current text:** "If no input can tell them apart (an equivalent mutant), first try to simplify the production code in a separate `refactor(<module>)` commit that keeps behavior and `openapi.json` unchanged."
**Failure scenario:** A survivor in `RequestSizeLimitFilter`, `ApiBearerTokenResolver`, `Bucket4jAuthRateLimiter`, `AuthService` or a password validator is judged equivalent because the right input wasn't found. The "simplification" deletes a real check. `./gradlew build` and the snapshot test pass if no existing test hits that path, and the hole ships.
**Fix:** Add to the D8 section: "In security code (`:common` `infra/security`, `api/security`, `RequestSizeLimitFilter`, `:user` `infra/security`, `infra/ratelimit`, `AuthService`, `SessionService` and the password and email validators), a check is removed only with a test that shows it is redundant, and the commit message names the mutant. Without such a test, keep the code and record the mutant in the final report."

### A1: Undefined what happens when a target can't be reached
**Severity:** major · **Status:** applied
**Location:** design.md > Surviving mutants; tasks.md > 4.3, 7.1, 7.2
**Current text:** "If that doesn't work either, list the mutant in the final report." / 4.3: "If the score is short, add tests for the largest remaining class before continuing"
**Failure scenario:** Equivalent mutants stay `SURVIVED` in `mutations.xml`. One implementer leaves them out of the score and reports the target met; another counts them and loops in 4.3 or reports a miss. The task has no exit when the remaining gap consists only of recorded equivalents.
**Fix:** Add to design.md under "Measuring the auth target" (or a new "Measuring the targets" section): "All targets are measured on the raw `mutationReport` numbers. Recorded equivalent mutants count as not killed. If a target is still missed after every non-equivalent mutant is killed, stop, and report the gap and the equivalent mutants to the user instead of adding tests."

### D3: Proposal says CLAUDE.md is unchanged, but task 1.2 edits it
**Severity:** minor · **Status:** applied
**Location:** proposal.md > Impact > Unchanged
**Current text:** "**Unchanged**: the OpenAPI snapshot, endpoints, schema, `CLAUDE.md`, the coverage tooling and every default threshold."
**Failure scenario:** A reviewer of the branch sees the CLAUDE.md diff from task 1.2 and flags it as out of scope, or the implementer skips 1.2 to match the proposal.
**Fix:** "**Unchanged**: the OpenAPI snapshot, endpoints, schema, the coverage tooling and every default threshold. `CLAUDE.md` changes only in the mutation-testing bullet, to name `@Repository` and `@Entity` classes."

### A2: `@Repository` excludes more than JPA adapters
**Severity:** minor · **Status:** applied
**Location:** design.md > Exclude `@Repository` and `@Entity` by annotation (D5)
**Current text:** "So the exclusion matches D5 exactly."
**Failure scenario:** D5 excludes JPA adapters. A later class annotated `@Repository` that isn't a JPA adapter (for example an adapter with real mapping logic over another store) silently loses mutation testing, and nobody notices because the report just has fewer mutants.
**Fix:** Append: "This holds today. From now on, `@Repository` means a JPA adapter in this project: a class with logic worth mutating must not carry it."

### T1: No scenario for a new adapter or entity
**Severity:** minor · **Status:** applied
**Location:** specs/mutation-testing/spec.md > Requirement: Non-logic classes are not mutated
**Current text:** "New classes of those kinds SHALL be excluded without changing the mutation setup." (only scenario: "A new configuration class")
**Failure scenario:** An implementation that lists the six existing adapters and entities by name passes every scenario but fails the requirement for the next entity.
**Fix:** Add:
```
#### Scenario: A new entity or adapter
- **WHEN** a new `@Entity` class or `@Repository` class is added to an opted-in module
- **THEN** the next report has no mutant in it, with no change to the mutation setup
```
Wrong implementation caught: a class-name list of today's `Jpa*Repository` and `*Entity` classes.

## Trust boundaries

The change adds tests and build logic. It changes no runtime boundary. The only path to production is the D8 refactors.
- Production code via D8 refactors: a misjudged "equivalent" mutant leads to removing a real check in auth, rate limiting or the size filter. Not stopped (S1).
- Build logic (`NonLogicClassScanTask`): reads local class files only, no external input. Nothing to attack.
- Fakes in unit tests: new tests use fakes for rate limiter, hasher and clock. The existing integration and E2E tests stay and still run in `./gradlew build`, so the change adds no new way for a fake to hide a production regression. Not a finding.

## Coverage

Checked: all 5 artifacts (decisions, proposal, the `mutation-testing` delta with 1 modified requirement and 5 scenarios, design, 24 tasks), the main `mutation-testing` spec, `openspec/config.yaml`, CLAUDE.md. Confirmed in code: `NON_LOGIC_CLASS_ANNOTATIONS`, the six `@Repository` and six `@Entity` classes and no others, PIT `targetClasses` per module (so `infra/security` is in the `:common` report), `AuthController` register order (rate limit before validation) and the refresh/logout transport branches, the `CommonExceptionHandler` methods, and that spring-test mock servlet objects reach all modules' tests via `:common` test fixtures. Not read: the other named helper classes in detail, `MutationReportTask`, the board controllers and services, and shared specs (the change implements none).

## Applied changes

- `tasks.md`: 4.2 drops the `StaticApplicationContext` option (D2). 4.3 keeps only the ≥ 80% `:common` check and gains the stop rule (D1, A1). 5.6 checks only the auth-scope score (D1). 3.5 and 5.5 point to the security-code rule (S1).
- `design.md`: D8 section gains the security-code rule (S1). New "Measuring the targets" section (A1). D5 section states that `@Repository` means a JPA adapter (A2). The Risks fallback no longer uses a `StaticApplicationContext` (D2).
- `proposal.md`: Impact says `CLAUDE.md` changes only in the mutation-testing bullet (D3).
- `specs/mutation-testing/spec.md`: new scenario "A new entity or adapter" (T1).
- Task 6.3 (`refactor(board)`) wasn't given the security rule, because `:board` has no code in the security list.
- No contradictions with `decisions.md`. No non-standard artifacts. `openspec validate --strict` passes.

