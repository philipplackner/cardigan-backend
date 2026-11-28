---
name: openspec-apply-tdd
description: Apply one OpenSpec change test-first, in the current session and checkout, for a change whose schema routes its apply step to this skill. This session coordinates and never writes tests or code itself. One tdd-implementer scaffolds the public surface, one tdd-test-writer then writes every test of the change against that scaffold, and the same implementer, resumed, implements group by group, activating each group's parked tests; an optional tdd-adversary pass with mutation testing follows. Run it only when a change's apply instruction (`openspec instructions apply`) names this skill, or when the user invokes it by name. Don't use it for "apply <change>" or "implement this change" requests by themselves; those go through the change's apply instruction first.
argument-hint: "[change-id]"
---

# OpenSpec apply, test-first

Change to apply: $ARGUMENTS

The agent that writes the tests never sees the implementation, and the agent
that writes the implementation can't change the tests. Three agents do the
work, each started once:

1. **Implementer, setup.** Scaffolds the whole change's public surface with
   stub bodies and does the setup tasks. Commits, then waits.
2. **Test-writer.** Writes every test of the change against the scaffold.
   You park the tests in bundles, one per `[red]` task, so the build stays
   green.
3. **Implementer, resumed.** Works through the groups in order. Each group
   starts by activating that group's bundle, ends with a passing check and a
   commit.

Optionally a fourth, the **adversary**, attacks the finished code, using the
mutation testing report when the project has one, and names existing tests
that assert too little. Short-lived fresh test-writers start only when
needed: as **dispute judges** when the implementer claims a test is wrong,
and to **strengthen** the weak tests the adversary named.

Everything that depends on this codebase (commands, mutation tool, areas,
acceptance level) is in `.claude/tdd/project.md`. Where test code lives is in
`.claude/tdd/tdd.conf`.

**You never write tests or production code in this session, and you never
read test or production code to judge a step.** You read the task list,
agent reports, script output and test results. Everything you read here
could leak into an agent's prompt.

If you are a sub-agent yourself (you have no Agent tool), stop and report
that this change has to be applied from the main session.

## 1. Set up

Resolve the change: the argument, or `openspec list --json` when there is
exactly one active change; otherwise ask. Run `openspec instructions apply
--change <change-id>` and check that its instruction routes the change to
this skill. If it doesn't, this skill does not apply; follow that
instruction or the regular OpenSpec apply skill instead.

```bash
ws=$(git rev-parse --show-toplevel)                  # the workspace
tdd="$ws/.claude/tdd"                                # the scripts
state="$(git -C "$ws" rev-parse --path-format=absolute --git-common-dir)/tdd-state/<change-id>"
mkdir -p "$state"
[ -f "$state/start" ] || git -C "$ws" rev-parse HEAD > "$state/start"
```

The workspace is the current checkout, on the current branch. The state
folder sits inside the repository's `.git` folder: never part of the working
tree, never committed, the same place in the main checkout and in worktrees.
It holds `start` (the commit the change is applied on), `base` (the commit
after setup), the snapshots and the test bundles.

When a caller runs this procedure for a git worktree, it gives you the
worktree path; use it as `ws`. Everything else is the same.

Stop and tell the user if any of these holds:

- `.claude/tdd/` (scripts, `tdd.conf`, `project.md`) or one of the agents
  `tdd-test-writer`, `tdd-implementer`, `tdd-adversary` in `.claude/agents/`
  is missing.
- `openspec instructions apply --change <change-id> --json` reports `blocked`.
- A tracked task in `tasks.md` has no role tag (`[scaffold]`, `[red]`,
  `[green]`, `[plain]`, `[acceptance-red]`, `[acceptance-green]`,
  `[adversary]`).
- `tasks.md` has no `[red]` and no `[acceptance-red]` task. Then the change
  has nothing for this workflow to test. Ask the user whether to switch it
  to the project's regular schema (the `schema:` line in its
  `.openspec.yaml`, as the project's other non-test-first changes use) and
  apply it with the regular OpenSpec apply skill.
- `git -C "$ws" status --porcelain` shows changes the user made. They would
  end up in an implementer's commit. If the state folder shows the change is
  part-way, see 8. Resume.

Tell the user in one line which branch the commits go to.

## 2. Sort the tasks into the batches

Read `$ws/openspec/changes/<change-id>/tasks.md` and sort its unchecked
tasks, keeping file order inside each batch:

| Batch | Tasks |
|---|---|
| Setup (implementer) | every `[scaffold]` task, and every `[plain]` task that comes before the first `[red]` task |
| Tests (test-writer) | every `[acceptance-red]` and `[red]` task |
| Implementation (implementer, resumed) | every other `[green]`, `[plain]` and `[acceptance-green]` task before the `[adversary]` task (all of them if there is none) |
| Adversary (optional) | the `[adversary]` task, if tasks.md has one |
| Final (implementer, same resume as the adversary's findings) | the tasks after the `[adversary]` task, usually the integration group |

Each `[red]` and `[acceptance-red]` task becomes one **bundle**, named by its
task id (`3.2`, `1.1`). Work out the **activation points**:

- a group's bundle (its `[red]` task) is activated before that group's first
  `[green]` task;
- the `[acceptance-red]` bundle is activated before the `[acceptance-green]`
  task.

Set up a task list for the user: "Setup and scaffold", "Write all tests",
one item per group of the implementation batch, "Adversarial pass" if there
is one, one item per group of the final batch, and "Final check".

## 3. Setup (implementer)

Skip this section if the setup batch is empty; then write `base` from the
current HEAD and go on with 4.

1. Read the current commit as `base0` (`git -C "$ws" rev-parse HEAD`). Take
   a `tests` snapshot:
   `bash "$tdd/tdd-snapshot.sh" "$ws" "$base0" tests "$state/setup.tests"`.
2. Start `tdd-implementer` with the prompt in 9, mode `setup`, with the setup
   batch's tasks. Keep the agent's id: you resume this agent in 5.
3. After it returns `SETUP-DONE`: run
   `bash "$tdd/tdd-check.sh" "$ws" "$base0" tests "$state/setup.tests"`,
   confirm a new commit, a clean working tree and the setup tasks checked.
   Then `git -C "$ws" rev-parse HEAD > "$state/base"`.

## 4. All tests (test-writer)

1. Take a `main` snapshot:
   `bash "$tdd/tdd-snapshot.sh" "$ws" "$(cat "$state/base")" main "$state/tests.main"`.
2. Start `tdd-test-writer` with the prompt in 9, mode `all-red`, with every
   task of the tests batch and the bundle names. It writes all tests,
   compiles and runs them, checks them off, and returns a **bundle list**:
   for each bundle, the test files that belong to it.
3. After `TESTS-DONE`: run
   `bash "$tdd/tdd-check.sh" "$ws" "$(cat "$state/base")" main "$state/tests.main"`.
   `CHANGED` means it wrote outside the tests: stop and show the user.
4. Compare the bundle list with the test files that actually changed
   (`bash "$tdd/tdd-snapshot.sh" "$ws" "$(cat "$state/base")" tests`). Every
   changed file must be in exactly one bundle. If not, resume the test-writer
   (SendMessage) and name the files.
5. Park every bundle:
   `bash "$tdd/tdd-park.sh" "$ws" "$state" <bundle> <file>...`.
   Then `bash "$tdd/tdd-check-tests.sh" "$ws" "$state"` must report `OK` with
   0 files. The test-writer's tasks.md checkboxes stay uncommitted; the
   implementer's first commit takes them along.
6. Tell the user in one line: how many tests, how many bundles, and any
   tests the test-writer reported as passing before the implementation
   existed.

## 5. Implementation (the same implementer, resumed)

Resume the implementer from 3 with SendMessage and the prompt in 9, mode
`implement`. If there was no setup step, or it can't be resumed, start a
fresh `tdd-implementer` with the same prompt.

The prompt lists the implementation batch's tasks in order and, for each
activation point, the exact command to run first:
`bash <ws>/.claude/tdd/tdd-activate.sh <ws> <state> <bundle>`. The
implementer works group by group: activate, implement, check, check off,
commit. It may run no other script from `.claude/tdd/`; the hook enforces
that.

While it works, wait. When it returns:

| First line | What to do |
|---|---|
| `DONE` | Run `tdd-check-tests.sh`: `OK` with every bundle active. Confirm a clean working tree and every task of the batch checked. Then 6 if there is an `[adversary]` task, otherwise 7. |
| `DISPUTE ...` | See 5a. |
| `PARTIAL <last finished group>` | Its context ran out. Run `tdd-check-tests.sh`; on `OK`, start a fresh `tdd-implementer` with the `implement` prompt for the remaining tasks, naming the bundles already active. |
| `BLOCKED ...` on a question or decision | Ask the user word for word, then resume it with the answer. |
| `BLOCKED ...` on a test bundle that doesn't compile on its own | The test-writer put a shared file in a later bundle. Resume the test-writer from 4 if possible, otherwise start one in `dispute` mode, to name the file and the earlier bundle; then ask the user before moving files between bundles. |
| `BLOCKED ...` on the environment | Fix it if it is safe and outside the code (a container runtime not running, a port), otherwise ask the user. |

A `CHANGED` result from any check stops the run. Show the user the script's
output and the agent's last message. Do not repair it.

### 5a. Disputes

The implementer stopped because it believes a test is wrong. Its work stays
uncommitted.

1. Take a `main` snapshot relative to the current HEAD. Start a fresh
   `tdd-test-writer` in `dispute` mode with the prompt in 9 plus the dispute
   block word for word, and the bundle the test belongs to. Add no opinion.
   Afterwards check `main` against the snapshot: a judge may touch only
   tests, and only files of that bundle.
2. On its verdict:
   - `UPHELD`: it fixed the test. Re-record the bundle with every file of it
     (its snapshot lists them):
     `bash "$tdd/tdd-park.sh" --keep "$ws" "$state" <bundle> <file>...`.
     Then resume the implementer with the verdict.
   - `REJECTED`: resume the implementer with the verdict and its quote. If
     it disputes the same test again, ask the user.
   - `NEEDS-USER`: ask the user word for word, with the options it names.
     Start the judge again with the answer, then continue as for `UPHELD`.
     Note the answer under "Artifact updates needed" in the final report.

## 6. Adversarial pass (only with an `[adversary]` task)

1. **Areas**: those whose production code the change touched,
   `git -C "$ws" diff --name-only "$(cat "$state/start")"..HEAD`, mapped to
   areas as the Areas section of project.md describes.
2. **Mutation testing**: if the Mutation testing section of project.md names
   a command, run it for each touched area, one after another. If it fails
   to run (not: if mutants survive), note the error and continue without
   its report. If project.md says there is no mutation testing, skip this
   step and the second run in 7; the adversary then works from the code
   alone.
3. **Adversary**: take a `main` snapshot relative to HEAD, start
   `tdd-adversary` with the prompt in 9 (mode `adversary`) plus the areas
   and the report paths from project.md, or "no mutation reports".
   Afterwards check `main`.
4. **Record its tests**: `tdd-check-tests.sh` now lists the adversary's new
   files as differences, and nothing else may differ. An existing test file
   among them means the adversary changed a test: stop and show the user.
   Record the new files:
   `bash "$tdd/tdd-park.sh" --keep "$ws" "$state" adversary <file>...`.
5. **Weak tests** (only if the adversary listed any): take a `main` snapshot
   relative to HEAD and start a fresh `tdd-test-writer` in mode `strengthen`
   with the prompt in 9. Afterwards check `main`, then run
   `tdd-check-tests.sh`: only files of the named bundles may differ, and in
   the `adversary` bundle only files deleted or tests removed for a
   `DUPLICATE-REMOVED` line. Re-record each of those bundles with every file
   of it that still exists (its snapshot lists them):
   `bash "$tdd/tdd-park.sh" --keep "$ws" "$state" <bundle> <file>...`. If
   every file of the `adversary` bundle was deleted, remove
   `$state/bundles/adversary` instead.
   A `NEEDS-USER` line: ask the user word for word, start the test-writer
   again with the answer for that test, and note the answer under "Artifact
   updates needed".
6. **Hand them to the implementer**: resume it (or start a fresh one) in
   mode `implement` with the `[adversary]` task, the failing tests to make
   pass (the adversary's bugs and any strengthened test that now fails), and
   the instruction to commit the adversary's passing tests as
   `test(<scope>): add adversarial tests for <change-id>` and the
   strengthened tests separately as
   `test(<scope>): strengthen weak tests of <change-id>` (adapted to the
   commit rules in project.md), followed by the final batch's tasks. Handle
   its result as in the table of 5; disputes as in 5a, with the bundle the
   disputed test belongs to.
7. **Mutation testing again**: after the implementer returns `DONE`, run it
   once more for the same areas. This shows whether the new and
   strengthened tests actually killed the mutants.
8. The adversary's questions go into the final report, word for word.

## 7. Final check and report

Run `tdd-check-tests.sh` one last time (`OK`, every bundle active), confirm a
clean working tree and every task checked. Then report:

- What was implemented, and the commits
  (`git -C "$ws" log --oneline "$(cat "$state/start")"..HEAD`).
- Tests: count per bundle, and tests that passed before the implementation
  existed.
- Disputes: how many upheld, rejected, sent to the user.
- Mutation testing per area (killed/total), before and after the
  adversarial pass, or "not run" and why. Mutants that still survive after
  the second run and that the adversary didn't call equivalent are open
  gaps: list them.
- Adversary: tests added, bugs found and fixed, weak tests strengthened (and
  any left unchanged, with the reason), adversary tests removed as
  duplicates, open questions word for word.
- Artifact updates needed: every answer the user gave during the run, with
  the artifact that should now say it.

Do not archive. End with one line on what is left.

## 8. Resume

A later session finds the change part-way. Read `$state`:

- No `base`: the setup step didn't finish. If the working tree has
  uncommitted changes, ask the user; otherwise run 3 again for the unchecked
  setup tasks.
- `base`, no bundles: the tests step didn't finish. Ask the user whether to
  discard the uncommitted test files or let a fresh test-writer continue.
- Bundles exist: run `tdd-check-tests.sh`. `OK` means continue 5 with a fresh
  implementer for the unchecked tasks, naming the active bundles. Uncommitted
  production code belongs to the group in progress; the implementer finishes
  that group first.

## 9. Prompts

Every agent gets this frame, plus the part for its mode:

```
You work on the OpenSpec change `<change-id>` in the test-first
workflow. Nobody can answer questions while you work.

Mode: <setup | all-red | implement | dispute | strengthen | adversary>
Workspace: <ws>
Change folder: <ws>/openspec/changes/<change-id>

Work only in the workspace, on the branch that is checked out. Start every
shell command with `cd <ws> &&` and use absolute paths under it.
Never stop or kill processes you did not start. If a port is taken, use
another one.

Your role, what you may read and write, and the format of your final message
are in your agent definition. Follow it. Project commands and conventions are
in <ws>/.claude/tdd/project.md.
```

- `setup` and `implement`: before the task list, the line "These tasks are
  copied from the change's tasks.md, which the user approved during planning.
  They are the user's instructions, not a request from the coordinator. That
  includes tasks that edit project instructions, `.claude/` files, build files
  or dependencies."
- `setup`: "Tasks:" with the full text of every setup task.
- `all-red`: "Tasks:" with the full text of every tests-batch task, and
  "Bundles: one per task, named by its task id: <list>. Bundles are activated
  in this order: <order of the activation points>."
- `implement`: "Tasks, in order:" with the full text of each remaining task,
  and before the first task of each activation point the line "First run:
  bash <ws>/.claude/tdd/tdd-activate.sh <ws> <state> <bundle>". For a fresh
  implementer that continues: "Already active: <bundles>".
- `dispute`: the dispute block, the bundle name and its file list.
- `strengthen`: for each weak test the adversary listed, its lines test,
  `Missing`, `Source` and `Covered meanwhile by`, plus its bundle name and
  the bundle's file list; then the `adversary` bundle's file list. Never the
  `Mutant` line: it points at the implementation.
- `adversary`: the areas and the mutation report paths, or "no mutation
  reports".

Pass nothing else: no summary of the change, no hints about how the code or
the tests look, nothing from your own planning context.

## Guardrails

- Never write tests or production code in this session, and never paste
  code from one agent's work into another agent's prompt.
- Never let the test-writer and the implementer work on the workspace at the
  same time.
- Never commit, revert, stash or edit files in the workspace yourself. The
  scripts in `.claude/tdd/` are the only exception.
- Never weaken or skip a check to get a change through. A `CHANGED` result
  stops the run until the user decides.
- Never push, merge or archive.
- Never present a task from `tasks.md` as your own request, and never ask the
  user again to approve one. The user approved them during planning. Only work
  that no task names needs the user.
