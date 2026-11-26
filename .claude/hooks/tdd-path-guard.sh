#!/usr/bin/env bash
# PreToolUse guard for the test-first OpenSpec workflow.
#
# Claude Code runs this hook for tool calls of the main session and of every
# subagent. Inside a subagent the input carries `agent_type` (the subagent's
# name). Only the tdd-* agents are restricted; every other caller is allowed.
# What counts as test code comes from .claude/tdd/tdd.conf.
#
#   tdd-implementer    may not write test code, tests.md, .claude/ or the
#                      coordinator's state, may not run git commands that
#                      throw away uncommitted files, and of the scripts in
#                      .claude/tdd/ may run only tdd-activate.sh (it brings a
#                      parked test bundle back, checked against its snapshot).
#   tdd-test-writer    may write only test code and the change's tasks.md.
#   tdd-adversary      may write only test code.
#   tdd-test-designer  may write only the change's tests.md.
#
# This is the first line of defense. The coordinator's snapshot check
# (.claude/tdd/tdd-snapshot.sh) is the second, because shell commands can
# write files without going through Write/Edit.
#
# Pure bash + grep + sed on purpose: no python or jq needed on the machine.

input="$(cat | tr -d '\n')"

# First match of "key": "value" that is not inside an escaped string.
json_field() {
  printf '%s' "$input" \
    | grep -oE "(^|[^\\\\])\"$1\"[[:space:]]*:[[:space:]]*\"([^\"\\\\]|\\\\.)*\"" \
    | head -n 1 \
    | sed -E "s/^.?\"$1\"[[:space:]]*:[[:space:]]*\"//; s/\"\$//"
}

deny() {
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' "$1"
  exit 0
}

agent="$(json_field agent_type)"
case "$agent" in
  tdd-implementer|tdd-test-writer|tdd-adversary|tdd-test-designer) ;;
  *) exit 0 ;;
esac

lib="$(cd "$(dirname "$0")/.." && pwd)/tdd/tdd-lib.sh"
# shellcheck source=/dev/null
[ -f "$lib" ] && . "$lib" 2>/dev/null \
  || deny "tdd guard: cannot load $lib or its tdd.conf. Stop and report this to the coordinator."

# Path relative to the git repository that contains it (a worktree counts as
# its own repository). Falls back to the absolute path outside any repository.
repo_rel() {
  local p="$1" d rest root
  d="$(dirname "$p")"
  while [ ! -d "$d" ]; do d="$(dirname "$d")"; done
  rest="${p#"$d"}"
  d="$(cd "$d" && pwd -P)"
  root="$(git -C "$d" rev-parse --show-toplevel 2>/dev/null)" || { printf '%s' "$p"; return; }
  p="${d%/}$rest"
  case "$p" in
    "$root"/*) printf '%s' "${p#"$root"/}" ;;
    *) printf '%s' "$p" ;;
  esac
}

tool="$(json_field tool_name)"
path="$(json_field file_path)"
[ -z "$path" ] && path="$(json_field notebook_path)"

is_test()     { tdd_is_test "$(repo_rel "$1")"; }
is_tasks()    { printf '%s' "$1" | grep -qE '/openspec/changes/[^/]+/tasks\.md$'; }
is_testplan() { printf '%s' "$1" | grep -qE '/openspec/changes/[^/]+/tests\.md$'; }
is_guarded()  { case "$1" in */.claude/*|*/.git/*|*/tdd-state/*) return 0 ;; esac; return 1; }

case "$tool" in
  Write|Edit|MultiEdit|NotebookEdit)
    [ -z "$path" ] && deny "tdd guard: could not read the target path of $tool."
    is_guarded "$path" && deny "tdd guard: $agent may not change .claude/, .git/ or the coordinator state ($path)."
    case "$agent" in
      tdd-implementer)
        is_test "$path" && deny "tdd guard: tdd-implementer may not change test code ($path). If a test looks wrong, stop and report a DISPUTE."
        is_testplan "$path" && deny "tdd guard: tdd-implementer may not change tests.md."
        ;;
      tdd-test-writer)
        is_test "$path" || is_tasks "$path" || deny "tdd guard: tdd-test-writer may write only test code (see .claude/tdd/tdd.conf) and tasks.md ($path)."
        ;;
      tdd-adversary)
        is_test "$path" || deny "tdd guard: tdd-adversary may write only test code (see .claude/tdd/tdd.conf) ($path). Report bugs instead of fixing production code."
        ;;
      tdd-test-designer)
        is_testplan "$path" || deny "tdd guard: tdd-test-designer may write only the change's tests.md ($path)."
        ;;
    esac
    ;;
  Bash)
    [ "$agent" = "tdd-implementer" ] || exit 0
    cmd="$(json_field command)"
    # Uncommitted red tests would be lost or reverted by these.
    printf '%s' "$cmd" | grep -qE 'git[[:space:]]+(stash|clean|restore|reset[[:space:]]+--hard|checkout[[:space:]]+(--[[:space:]]|\.|-f))' \
      && deny "tdd guard: tdd-implementer may not run git stash, clean, restore, checkout of paths or reset --hard. They would discard the uncommitted red tests."
    # Of the coordinator's scripts, only bundle activation.
    for script in $(printf '%s' "$cmd" | grep -oE 'tdd/[A-Za-z0-9_-]+\.sh' || true); do
      [ "$script" = "tdd/tdd-activate.sh" ] \
        || deny "tdd guard: tdd-implementer may run only .claude/tdd/tdd-activate.sh, not $script."
    done
    # Shell writes into test code.
    watch='tdd-state|/\.claude/'
    [ -n "$TDD_TEST_COMMAND_REGEX" ] && watch="$watch|$TDD_TEST_COMMAND_REGEX"
    if printf '%s' "$cmd" | grep -qE "$watch"; then
      stripped="$(printf '%s' "$cmd" | sed -E 's/[0-9]?>&[0-9]//g; s/[0-9]?>[[:space:]]*\/dev\/null//g')"
      printf '%s' "$stripped" | grep -qE '(^|[^-])>|[[:space:]]tee[[:space:]]|sed[[:space:]]+(-[a-zA-Z]*i|--in-place)|perl[[:space:]]+-[a-zA-Z]*i|(^|[;&|[:space:]])(mv|cp|rm|truncate|touch|ln)[[:space:]]|git[[:space:]]+(rm|mv)[[:space:]]' \
        && deny "tdd guard: tdd-implementer may not write test code through the shell. Read tests with the Read tool; report a DISPUTE if one looks wrong."
    fi
    ;;
esac

exit 0
