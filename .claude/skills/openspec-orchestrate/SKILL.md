---
name: openspec-orchestrate
description: Work through the open OpenSpec changes in parallel, in any codebase and with any build system. Builds the dependency graph of all open changes, then acts as orchestrator - one sub-agent per change implements it in its own git worktree, in the order the graph allows. Use when the user asks to orchestrate, apply or implement all open changes or a list of changes, to run changes in parallel, or to continue an orchestration after changes were merged. Also use when the user only asks for the order of the open changes, a dependency graph, or what can be parallelized; then it stops after the graph. When orchestrating, it starts sub-agents that write code and commit it on local branches, and it deletes the worktree of every change that has been merged.
---

# OpenSpec orchestrate

Two parts:

1. **Dependency graph.** Read every open change, work out which files each one
   touches, and print the order and what can run in parallel. Read-only.
2. **Orchestrate.** Act as the orchestrator: start one sub-agent per ready
   change, each in its own git worktree, follow the graph, and report.
   Every worktree is deleted once its change is merged (Step 7).

If the user only asked for the graph or the order, stop after Part 1.

**Scope.** Arguments are change ids. With no arguments, work on all open
changes. With arguments, work only on those, but still build the graph over
all open changes so the dependencies are known. An id that is not an open
change: stop and list the valid ids. The user can also give a limit for
parallel sub-agents; the default is 3.

The unit is the **change**, not the task group. One change is applied in one
worktree by one sub-agent at a time. The order of groups inside a change
comes from its `tasks.md` and is the sub-agent's job.

This skill does not depend on a language, framework or build system. Whatever
the project builds and tests with comes from the project's own instructions
(`CLAUDE.md`, `AGENTS.md`, the README) and from the OpenSpec apply
instruction. The only build-system-specific rule is the one for Gradle in
Step 9.

---

# Part 1: Dependency graph

## What the graph is made of

| Thing | Meaning |
|---|---|
| Node | One open change. |
| `declared` edge | A change names another change as a prerequisite in its `proposal.md`. |
| `needs` edge | Found by file analysis: the change uses a file, type, module, package, or config entry that another open change creates. |
| `order` edge | Found by file analysis: two changes rewrite the same existing file, so one has to go first. The direction is a choice, not a requirement. |
| Overlap note | Two changes each add their own lines to the same file. No edge. They stay parallel and the second one to merge rebases. |

## Step 1: Collect the open changes

```bash
openspec list --json
git worktree list
```

Open changes are the folders in `openspec/changes/` except `archive/`. If
there are none, say so and stop.

For each change read `proposal.md`, `tasks.md` and `design.md` in full. Read
the delta specs only when the other three leave a file or type unclear.

The **integration branch** is the branch that finished changes are merged
into. Take it from `openspec/config.yaml` or the project's instructions if
one of them names it. Otherwise it is the branch checked out in the main
checkout. Name it in the graph header so the user can correct it.

Status of a change comes from the checkboxes in `tasks.md`, counted in two
places: on the integration branch, and in the change's worktree if it has one
(see Step 9 for where worktrees live).

```bash
git show <integration-branch>:openspec/changes/<change-id>/tasks.md | grep -c '^- \[ \]'
grep -c '^- \[ \]' <worktree>/openspec/changes/<change-id>/tasks.md
```

| Status | Condition |
|---|---|
| **merged** | No unchecked task on the integration branch. Leave it out of the waves. It blocks nothing. |
| **implemented** | No unchecked task in its worktree, but unchecked tasks on the integration branch. Its branches are waiting to be merged. It still blocks the changes that wait for it. |
| **in progress** | Some tasks checked in its worktree. |
| **not started** | Everything else. |

A change without `tasks.md` is still a node. Build its file map from
`proposal.md` and `design.md` and mark it as rough. It cannot be applied.

## Step 2: Build the file map for every change

This is the careful part.

For each change list what it **creates**, **edits**, **deletes or moves**, and
what it only **uses**. Sources, most trusted first:

1. The task lines in `tasks.md`: paths in backticks, module or package names,
   named classes, functions, components, routes, config keys, dependency
   entries. Read every group; the map is the sum of all of them.
2. `proposal.md`, section Impact: modules or packages touched, shared files,
   and statements like "No edits to `<file>`". A "no edits" statement rules
   that file out for the change.
3. `design.md`: file layout, decisions that name files or types.
4. The repository on the integration branch: does the path exist today?
   Check with `git ls-files <path>` or by reading the directory.

Rules for the map:

- Turn a module, package or workspace name into its directory, using the
  project's own layout.
- Use the exact file when the change names it. Otherwise use the narrowest
  directory the change does name. Don't invent file names.
- A path that does not exist in the repository is a **create** for the change
  that adds it and a **use** or **edit** for every other change that mentions
  it. Exception: a list-like file that each change only adds its own lines to
  (for example a translation file) is created by whichever change lands
  first. Treat it as an additive overlap, not as a dependency.
- A path or symbol that a change uses, that does not exist in the repository,
  and that no open change creates is a gap. Report it under "Problems found".
- Conditional edits ("add the dependency if it isn't there yet") count as
  edits, marked `maybe`.
- Record types and other symbols the same way as files: a shared type is
  created by one change and used by others even if no path is given.
- Two changes that add different new files to the same directory do not
  overlap.

Keep the map in your working notes. Only the shared files go into the reply.

## Step 3: Derive the edges

Work through these in order.

1. **Declared.** A prerequisite change named in `proposal.md` ("must be merged
   first", "Prerequisite changes", "Changes that wait on this one") gives an
   edge between the two changes. If the proposal names a change that is not
   open, check `openspec/changes/archive/`: archived means merged, missing
   means report it.
2. **Needs.** For every file or symbol a change edits or uses: if another open
   change creates it, moves it or renames it, add a `needs` edge from that
   change. Skip it if a declared edge already covers it.
3. **Same file, both edit.** Decide which case it is:
   - *Additive*: each change only adds its own lines to a list-like file
     (dependency manifest, module or route registration, dependency injection
     setup, translation or string resources). No edge. Add an overlap note.
   - *Rewrite*: a change rewrites, restructures or deletes the file, or both
     change the same existing block. Add an `order` edge. Put first the change
     that more other changes wait for; if equal, the one that comes first
     alphabetically. Say in the reply that the direction was chosen.
4. **Compare declared and found.** Do not change the declared order. Report:
   - a change that needs something from an open change it does not name as a
     prerequisite,
   - a declared prerequisite that the file map gives no reason for (this one
     is only a note; the author may know more),
   - two proposals that disagree (A says B waits on it, B does not list A).
5. **Cycles.** If the edges form a cycle, list the changes in it with the
   reason for each edge and stop the whole skill there. Don't break a cycle
   yourself.

Every `needs` and `order` edge must name the file or symbol that causes it.
No edge from a general feeling that two things are related.

## Step 4: Order the graph

- Remove merged changes and the edges that start at them.
- **Waves**: wave 1 is every change with no open dependency. Wave n is every
  change whose dependencies are all in earlier waves.
- **Longest chain**: the path with the most changes from a wave 1 change to a
  change nothing waits for.
- Before printing, drop edges that other edges already imply (if A → B → C,
  don't also print A → C).

## Step 5: Print the graph

Print this to the user before starting any sub-agent. Use exactly this
structure. Leave out a section only where the template says so. No task
lists, no group numbers, no copies of the proposals.

```markdown
## Dependency graph: <n> open changes (<m> merged, <i> implemented, <p> in progress)

Integration branch: `<branch>`

### Order
| Wave | Change | Waits for | Unblocks | Status |
|---|---|---|---|---|
| merged | <change-id> | - | <change-ids> | all tasks checked on <branch> |
| 1 | <change-id> | - | <change-ids> | not started |
| 1 | <change-id> | - | - | implemented, waiting to be merged |
| 2 | <change-id> | <change-ids> | - | 4/25 tasks |

### Shared files
| File | Touched by | Kind | What to do |
|---|---|---|---|
| `<path>` | `<change-id>`, `<change-id>` | additive | Parallel is fine; second to merge rebases |
| `<path>` | ... | rewrite | `<a>` first, then `<b>` (order chosen) |

List a file only when at least two of the changes that touch it are not
already ordered by an edge. Omit the section if there is no such file.

### Dependencies found by file analysis
- `<a>` → `<b>`: needs `<file or symbol>` created there.

Only edges that are not declared in the proposals. Omit if there are none.

### Longest chain
`<a>` → `<b>` → ... (<k> changes).

Omit if no open change waits for another open change.

### Problems found
- Mismatches between declared and found dependencies, gaps, rough nodes,
  chosen directions that deserve a second look, merged changes that are not
  archived.

Omit if there are none.
```

If the user only asked for the graph, add one sentence on which changes can
start now and stop here.

---

# Part 2: Orchestrate

From here on you are the **orchestrator**. Your job is to start sub-agents,
watch their results, and keep the user informed.

- You do not implement tasks, edit change documents, or fix code yourself.
  All of that happens in sub-agents inside worktrees.
- You do not merge, push, rebase or archive.
- You do not answer product or design questions for a sub-agent. Those go to
  the user.
- Invoking this skill is the go-ahead. After printing the graph, continue
  without asking for confirmation, unless Step 6 tells you to stop.

> **IMPORTANT: no worktree outlives its change.** Every worktree this skill
> creates MUST be deleted as soon as its change is merged into the
> integration branch. This is a required part of every run, not optional
> housekeeping: a run that leaves the worktree of a merged change on disk is
> not finished. Step 7 says when and how. The only worktrees that stay are
> those of changes that are not merged yet.

## Step 6: Check that the run can start

If the integration branch has an upstream, run `git fetch` and compare the
two. Stop and tell the user if this fails:

- Behind only: if the main checkout is on that branch and clean, run
  `git pull --ff-only`. Otherwise stop.
- Diverged: stop.
- Ahead only, or no upstream: go on.

Leave out a single change, and say why, if one of these holds:

- Its folder is not fully committed on the integration branch
  (`git status --short openspec/changes/<change-id>` prints something).
  A worktree would not contain it.
- `openspec instructions apply --change <change-id> --json` reports the state
  `blocked` (a required artifact is missing).
- The graph has a gap or an undeclared missing prerequisite for it.

## Step 7: Delete the worktrees of merged changes

**This step is mandatory.** Run it three times: now, before any sub-agent
starts; again whenever you learn during the run that a change was merged; and
once more right before the final report. Each worktree holds a full checkout
with its own dependencies and build output, and a leftover one makes a later
run misread the status of its change.

```bash
root=$(git rev-parse --show-toplevel)
wtdir="$(dirname "$root")/$(basename "$root")-worktrees"
git worktree list --porcelain
```

Look at every worktree directly under `$wtdir` whose folder name is the id of
an open or archived change. Other worktrees were not created by this skill:
leave them alone. Delete the worktree when all of these hold:

- The change is merged: on the integration branch its `tasks.md` has no
  unchecked task, or its folder is in `openspec/changes/archive/` there.
- `git -C "$wt" status --porcelain` prints nothing.
- No sub-agent is working in it.

```bash
git worktree remove "$wt"
git worktree prune
```

If the worktree has its own Gradle home (Step 9), stop its daemons first:
`GRADLE_USER_HOME="$wt/.gradle-home" "$wt/gradlew" --stop`.

Then run `git worktree list` and confirm the path is gone. The branches of
the change stay, so no commit is lost.

Never use `--force`. If the change is merged but its worktree has uncommitted
files, or `git worktree remove` refuses, keep the worktree and list it under
"Worktrees" in the final report with the reason and the command the user has
to run.

## Step 8: Decide what is ready

A change is **ready** when all of these hold:

- it is in scope (Scope, above) and was not left out in Step 6,
- its status is `not started` or `in progress`,
- no sub-agent is working on it,
- every change it waits for (`declared`, `needs` and `order` edges) is
  **merged**. `implemented` is not enough: every change branches from the
  integration branch.

Recompute this list at the start and after every sub-agent result. Start
ready changes up to the parallel limit, earliest wave first, and within a
wave the change that unblocks the most others first.

Tell the user in one short list what starts now, what is queued behind the
limit, and what waits for which merge.

## Step 9: Prepare the worktree

One worktree per change, next to the repository, reused on later runs until
the change is merged and Step 7 deletes it:

```bash
root=$(git rev-parse --show-toplevel)
wt="$(dirname "$root")/$(basename "$root")-worktrees/<change-id>"
```

If `git worktree list` already shows `$wt`, reuse it as it is. Otherwise:

```bash
git worktree add --detach "$wt" <integration-branch>
```

A new worktree contains only tracked files. If the project's instructions
name git-ignored local files that the build or the tests need, copy them
from the main checkout. Copy regular files only (`[ -f <file> ]`), and never
print their content. Dependencies are not copied: the sub-agent installs
them in its worktree the way the project's instructions say.

**Gradle only.** If the project builds with Gradle, give the worktree its own
Gradle home at `$wt/.gradle-home`, so parallel builds don't fight over
daemons and locks. Link the download caches of the main Gradle home
(`caches/modules-2`, `wrapper/dists`) into it, copy its `gradle.properties`,
and add `.gradle-home` to the repository's `info/exclude`. For every other
build system there is nothing to set up here.

The worktree starts detached. The sub-agent creates the branch or branches
of the change. If the apply instruction defines a branch scheme, that scheme
applies. Otherwise the change gets one branch named `<change-id>`.

## Step 10: Start the sub-agents

Start one general-purpose sub-agent per ready change with the Agent tool.
Send all launches of one round in a single message so they run at the same
time, and run them in the background if the tool allows it.

A sub-agent knows nothing about this conversation. Give each one this prompt,
with every placeholder filled in and absolute paths:

```
You implement one OpenSpec change on your own: `<change-id>`.
Nobody can answer questions while you work.

Workspace: <worktree path>
This is a git worktree of the repository, created from `<integration-branch>`.
Work only there. Start every shell command with `cd <worktree path> &&` and
use absolute paths under it for every file you read or edit. The main
checkout at <main checkout path> and the other worktrees belong to other
agents. Do not change anything in them.

What to do:
1. Read the project's instructions in the workspace (CLAUDE.md, AGENTS.md)
   and follow them. They say how to install dependencies, build and test.
2. Apply the change with the `openspec-apply-change` skill. If you cannot load
   it, run `openspec instructions apply --change <change-id>` in the workspace
   and follow the output. Its instruction section is mandatory, including its
   branch scheme and its gates. If it defines no branch scheme, create one
   branch named `<change-id>` before your first commit.
3. <Fresh start: "Nothing has been done yet.">
   <Continuation: "An earlier session already worked on this change. Run
   `git status`, `git branch --list '<change-id>*'` and look at the checked
   tasks first. Uncommitted files belong to the group that was in progress:
   review them, finish that group, then continue. Do not redo finished
   groups."> <Plus anything the earlier session reported, and the user's
   answer if this continues after a question.>

Rules:
- Other agents build and test at the same time in their own worktrees. Never
  stop or kill build daemons, dev servers, containers, emulators or other
  processes you did not start. If a port is taken, use another one.
- <Gradle projects only: "Put `GRADLE_USER_HOME=<worktree path>/.gradle-home`
  in front of every Gradle command, and never run `./gradlew --stop`.">
- Finish, check off and commit one group before you start the next.
- Do not decide open product or design questions yourself and do not add a
  dependency that still needs approval. If a task needs such a decision, stop.
- Commit locally. Do not push, do not merge anything, do not commit to
  `<integration-branch>`, do not touch branches of other changes, do not
  archive.
- <One line per overlap note of this change: "`<other change>` adds its own
  lines to `<file>` in parallel. Keep your edit there to the lines your tasks
  name.">

Your final message starts with exactly one of these lines:
- `DONE <change-id>`: every task checked, all gates pass, everything
  committed. List the branches in the order they have to be merged.
- `PARTIAL <change-id>`: you stopped before the end without a blocker. Name
  the last finished group and say whether the group in progress is committed.
- `BLOCKED <change-id>`: name the task, what blocks it, the exact error or
  question, and what you tried.
After that line, in every case: the gate results of the last finished group,
and "Assumptions made" (what, why, the alternative) or "none".
```

## Step 11: Handle each result

Don't take a report at face value. Check the state yourself first:

```bash
grep -c '^- \[ \]' "$wt/openspec/changes/<change-id>/tasks.md"   # open tasks
git -C "$wt" status --porcelain                                 # uncommitted work
git branch --list '<change-id>*'                                # its branches
git -C "$wt" log --oneline <integration-branch>..HEAD           # its commits
```

| What you find | What to do |
|---|---|
| No open tasks, clean worktree, commits on the change's branches | The change is `implemented`. Record the branches and the assumptions. |
| `DONE` reported but one of those three is not true | Start a continuation sub-agent and name exactly what is missing. |
| `PARTIAL`, or no clear first line, and more tasks are checked than before | Start a continuation sub-agent in the same worktree. A large change normally takes several sessions. |
| No new checked task in two sessions in a row | Treat it as blocked. Don't start a third. |
| `BLOCKED` on a question, a decision or an approval | Pass the question to the user right away, word for word, while the other changes keep running. When the user answers, start a continuation sub-agent with the answer in its prompt. |
| `BLOCKED` on the environment (a missing local file, a lock, a tool that is not installed or not logged in) | Fix the environment if that is safe and within Step 9, then start a continuation. Otherwise report it. |

A continuation is a new sub-agent with the Step 10 prompt and the continuation
text in point 3. If the tool lets you continue the same sub-agent with its
context intact, prefer that.

After every result, redo Step 8 and fill free slots. While sub-agents are
running, do not start a second sub-agent on the same change or worktree.

The run ends when no sub-agent is running and no change is ready. Before
the final report, run Step 7 one more time.

## Step 12: Final report

```markdown
## Orchestration result

| Change | Result | Branches | Notes |
|---|---|---|---|
| `<change-id>` | implemented | `<branch>` → `<branch>` | |
| `<change-id>` | blocked at task 2.3 | `<branch>` | <one line> |
| `<change-id>` | not started | - | waits for `<change-id>` to be merged |
| `<change-id>` | left out | - | <reason from Step 6> |

### Needs you
- Open questions from blocked changes, each with the change and the task.
- What to merge to unblock what: "Merge `<a>` (`<branches>`) into
  `<integration-branch>` to free `<b>` and `<c>`."
- Branches that will need a rebase: "`<b>` and `<c>` both add lines to
  `<file>`. After one is merged, the other has to be rebased."

### Assumptions made
<Per change, the assumptions its sub-agents reported. "None" if none.>

### Worktrees
| Change | Worktree | State |
|---|---|---|
| `<change-id>` | `<path>` | deleted, change is merged |
| `<change-id>` | `<path>` | kept, waiting to be merged |
| `<change-id>` | `<path>` | NOT deleted: <reason>. Run `<command>` |
```

The Worktrees table lists every worktree that existed during the run. A
merged change may only appear there as `deleted` or `NOT deleted` with a
reason.

Close with one sentence: after merging, run this skill again; it deletes the
worktrees of the merged changes and continues with the changes that became
ready.

## Guardrails

- Never state an order you can't back with a declared line or a named file
  or symbol. If the changes don't say enough to decide, list the question
  under "Problems found" instead of guessing.
- Never start a change before everything it waits for is merged into the
  integration branch, even if the user is waiting and the prerequisite is
  already implemented. Say what has to be merged instead.
- Never merge, push, force-push, rebase a branch of a change, or archive a
  change. Report these as next steps.
- **Always delete the worktree of a merged change (Step 7).** Never end a run
  while the worktree of a merged change still exists, unless it is listed in
  the final report as not deleted with the reason.
- Never delete the worktree of a change that is not merged, never delete a
  worktree with uncommitted files, and never use `--force` to remove one.
- One sub-agent per change at a time, one worktree per change.
- Don't fix the plan. Missing prerequisites, cycles and problems inside a
  change's `tasks.md` are reported, not edited.
