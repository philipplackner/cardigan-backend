# Artifact review (light): be-kover-coverage

**Verdict:** Ready
**Reviewed:** 2026-11-23, schema `careful-planning`, repo kind `code`

| Pass | Blocker | Major | Minor |
|---|---|---|---|
| Testability | 0 | 0 | 2 |
| Security | 0 | 0 | 1 |
| Hidden assumptions | 0 | 0 | 2 |
| Design contract | 0 | 2 | 0 |

## Findings

### D1: Test-fixture classes are probably measured, and nothing makes or checks that they aren't
**Severity:** major · **Status:** applied
**Location:** design.md > "Two convention plugins" and "Exclusions in one place"; tasks.md > 2.1, 2.2
**Current text:** spec: "Only main source sets SHALL be measured, never test or test-fixture code." The design and tasks name no mechanism for this and no check.
**Failure scenario:** Kover's JVM defaults leave out only the `test` source set. `:common` applies `java-test-fixtures`, so `MutableClock`, `PostgresTestConfig` and `DatabaseCleaner` can end up in the report. Every task check still passes, because none of them looks for test-fixture classes.
**Fix:** In design.md under "Two convention plugins", add: "`cardigan.kover` also sets `kover { currentProject { sources { excludedSourceSets.add(\"testFixtures\") } } }`, so only `main` is measured (D15)." In tasks.md 2.1, add to the verification: "and contains none of `MutableClock`, `PostgresTestConfig` or `DatabaseCleaner`."

### D2: The fallback package filter breaks when `app` is selected together with another module
**Severity:** major · **Status:** applied
**Location:** design.md > "Merged report with a per-run class filter" > Fallback
**Current text:** "`:app` is matched as `com.plcoding.cardigan.*` minus the other modules' packages."
**Failure scenario:** `-Pcoverage.modules=app,user`, built per module, gives includes `com.plcoding.cardigan.*` and `com.plcoding.cardigan.user.*`, plus excludes `user.*`, `board.*`, `common.*` for `app`. Kover excludes always win over includes, so every `:user` class disappears from a report that should contain it.
**Fix:** Replace the bullet with: "**Fallback:** a class filter built from the selection as a whole. If `app` is selected: include `com.plcoding.cardigan.*` and exclude `com.plcoding.cardigan.<m>.*` for every opted-in module `m` other than `app` that is *not* selected. If `app` is not selected: include `com.plcoding.cardigan.<m>.*` for each selected module and exclude nothing." In tasks.md 2.3, add `=app,user` to the verified selections, checking that it contains both `:app` and `:user` classes and no `:board` or `:common` classes.

### T1: The threshold format and how rounding affects the result are not defined
**Severity:** minor · **Status:** applied
**Location:** specs/test-coverage/spec.md > Requirement: Optional thresholds fail the report command
**Current text:** "numbers from 0 to 100. When a given threshold is above the selection's total coverage for that metric, the command SHALL fail"
**Failure scenario:** One implementation parses thresholds as `Int` and rejects `72.5`. Another accepts it. With an actual value of 79.96%, the summary prints `80.0%` while `minLine=80` fails, and the failure message reads "80.0% < 80%". A test can't tell which behavior is correct.
**Fix:** "numbers from 0 to 100 inclusive, as integers or decimals (for example `80` or `72.5`). The comparison uses the unrounded percentage. The summary prints percentages with one decimal, and the failure message prints the actual value with two decimals." Wrong implementation caught: `toInt()` parsing, which rejects `72.5`.

### T2: Empty entries and duplicates in `coverage.modules` are not defined
**Severity:** minor · **Status:** applied
**Location:** specs/test-coverage/spec.md > Requirement: A report can be limited to chosen modules
**Current text:** "An unknown name, a module that has not opted in, or an empty list SHALL fail the command before any test runs."
**Failure scenario:** `-Pcoverage.modules=user,` fails in one implementation, because `""` is an unknown name. Another silently ignores the empty entry. Both satisfy the text.
**Fix:** Append: "An empty entry, for example from a trailing comma, fails the same way as an unknown name. Duplicate names are accepted and reported once." Add a scenario: "**WHEN** `./gradlew coverageReport -Pcoverage.modules=user,` runs **THEN** the command fails before any test runs, with a message listing the valid module names." Wrong implementation caught: `split(",").filter { it.isNotBlank() }`.

### S1: uvx pins only diff-cover, not its dependencies
**Severity:** minor · **Status:** applied
**Location:** design.md > "`diffCoverage` runs diff-cover through `uvx`"; spec > Requirement: Diff coverage needs only uv > Scenario: Pinned version
**Current text:** "Runs `uvx diff-cover==<version> …`"
**Failure scenario:** `diff-cover==11.0.4` resolves its dependencies (Jinja2, Pygments, chardet, pluggy) to whatever is newest on PyPI at first fetch. Two machines can run different dependency code, and a compromised dependency release reaches every developer who runs `diffCoverage` for the first time. The "Pinned version" scenario still passes.
**Fix:** In design.md step 3, add: "`uvx` is called with `--exclude-newer <date>`, a date stored next to the `diff-cover` version in `libs.versions.toml` and bumped together with it, so dependency resolution is the same on every machine." This keeps D10, which settles `uvx` with a pinned version and `uv` as the only prerequisite.

### A1: The base-ref semantics and the freshness of `origin/master` are not stated
**Severity:** minor · **Status:** applied
**Location:** specs/test-coverage/spec.md > Requirement: Diff coverage shows coverage of changed lines
**Current text:** "report coverage of only the lines changed relative to a base ref."
**Failure scenario:** diff-cover diffs against the merge base (`<ref>...HEAD`). If an implementer runs `git fetch` first, results change between runs. If nobody fetches and the branch was rebased onto a newer `master`, other people's commits count as changed lines. The spec allows both.
**Fix:** "…lines changed since the merge base of `HEAD` and the base ref, plus uncommitted and untracked changes. `diffCoverage` never fetches. It uses the ref as it exists locally, so a stale `origin/master` needs a `git fetch` first." Mention the `git fetch` in the CLAUDE.md note in task 4.1.

### A2: The working directory and source-root paths for diff-cover are not stated
**Severity:** minor · **Status:** applied
**Location:** design.md > "`diffCoverage` runs diff-cover through `uvx`" > step 3
**Current text:** "`--src-roots <src/main/kotlin of every opted-in module>`"
**Failure scenario:** diff-cover matches `<src-root>/<package path>/<file>` against the repo-relative paths from `git diff`. If the task passes absolute paths, or runs from a directory other than the repo root, no file matches. Every run then reports "no lines with coverage information" and still succeeds, so the failure stays silent.
**Fix:** Add: "diff-cover runs with the root project directory as its working directory, which is the Git top level. `--src-roots` are paths relative to it, such as `user/src/main/kotlin`." Task 3.1's first check already catches the silent case, as long as it asserts that specific changed line numbers are listed.

## Trust boundaries

- Command-line `coverage.*` properties: the developer's own input. They are passed as argv through `ExecOperations` with no shell, so shell injection isn't possible. Not an attacker-reachable boundary.
- PyPI through `uvx`: diff-cover is pinned, but its dependencies are not (S1). This is partly stopped.
- Kover XML parsing: the spec turns off external DTD loading. Stopped.
- Git refs passed to `git rev-parse` and diff-cover: local developer input. Not attacker-reachable.
- HTML reports with source code under `build/`: local only and git-ignored. No new exposure.
- Build logic in the repo: a malicious change could already run code through Gradle. This change adds no new boundary.

## Coverage

Checked all 13 requirements and 31 scenarios in `specs/test-coverage/spec.md`, plus proposal.md, design.md, tasks.md and decisions.md. Also read CLAUDE.md, `openspec/config.yaml`, the root `build.gradle.kts`, the plugin section of `libs.versions.toml`, the build-logic file list, `common/build.gradle.kts` and the `delivery-pipeline` requirement names. No other active changes. No shared specs are involved (D1). This light review did not read the convention plugin sources or the Kover and diff-cover docs. D1 and A2 rest on known tool defaults, and tasks 2.1 and 3.1 are where they get confirmed. Configuration-cache behavior of `taskGraph.whenReady` was not checked beyond what task 2.5 already verifies.

## Applied changes

Applied all seven findings on 2026-11-23, at the user's request.

- `specs/test-coverage/spec.md`: added the trailing-comma and duplicate-name scenarios (T2). Added two requirements: "Threshold values are decimals compared unrounded" (T1) and "Diff coverage uses the local base ref without fetching" (A1). Changed the diff requirement to say "since the merge base". The new text sits in separate requirements and scenarios because strict validation rejects requirement text over 500 characters.
- `design.md`: `cardigan.kover` excludes the `testFixtures` source set (D1). The fallback filter is now built from the whole selection (D2). Added the threshold parsing and printing rules (T1), `uvx --exclude-newer` with a `diff-cover-exclude-newer` catalog entry (S1), and the working directory, repo-relative `--src-roots` and merge-base/no-fetch behavior (A2, A1).
- `tasks.md`: updated 1.2, 2.1, 2.3, 2.6, 3.1 and 4.1 to match. No checkbox changed.
- `proposal.md`: the `diffCoverage` bullet now mentions the pinned resolution date, the merge base and that it never fetches.
- `decisions.md` is not edited and has no contradictions: S1 keeps D10, and A1 matches D8 and D9.
- No non-standard artifacts in the change folder.
- `openspec validate be-kover-coverage --strict` passes.
