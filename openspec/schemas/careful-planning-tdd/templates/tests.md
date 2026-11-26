# Test plan: <change-id>

<!-- Written by the tdd-test-designer agent from the change's planning
     artifacts only. Every test states where its expected result comes from.
     A test that asserts something no artifact states is a bug in this plan. -->

## Coverage

<!-- One row per scenario of every ADDED or MODIFIED requirement, and one row
     per requirement whose promise goes beyond its scenarios. Every row needs
     at least one test ID. -->

| Spec > Requirement > Scenario | Tests |
|---|---|
| `<capability>` > <requirement> > <scenario> | A01 |

## Acceptance tests

<!-- `A` IDs. Black-box tests through the public interface only, at the
     acceptance level .claude/tdd/project.md names. Written before any
     implementation exists, so they may use nothing but that interface and
     the existing test helpers. -->

### A01: <behavior in one line>
- Source: <spec path> > <requirement> > <scenario>
- Level: <acceptance level from project.md>
- Arrange: <state before the action>
- Act: <the call, request or user action>
- Expect: <result, error, visible state, effects observable from outside>
- Must fail when: <a concrete wrong implementation and what the test sees>

## Component tests

<!-- `C` IDs. Tests against the public surface the design names (types,
     functions, components, repositories, constraints), at the cheapest test
     level from project.md that can observe the behavior. Derive cases from
     the contract, not from an imagined implementation. -->

### C01: <behavior in one line>
- Source: <artifact > section, or spec path > requirement>
- Level: <a test level from project.md>
- Unit under test: <type and function as named in the design>
- Technique: <boundary | equivalence class | decision table | state transition | break vector: ...>
- Arrange: <...>
- Act: <...>
- Expect: <...>
- Must fail when: <a concrete wrong implementation and what the test sees>

## Open questions

<!-- Cases the artifacts do not decide. Do not pick an answer by writing a
     test for it. Each entry names the case, why it matters, your proposed
     answer, and the artifact that must decide it. Delete the section when
     there are none. -->

- Q1: <case> - <why a test needs it> - proposed: <answer> - decide in: <artifact>
