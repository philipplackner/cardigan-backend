#!/usr/bin/env bash
# Shared helpers for the scripts in .claude/tdd/ and the tdd hook.
# Loads tdd.conf next to this file. Works with the bash 3.2 that ships on macOS.

TDD_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TDD_TEST_PATTERNS=()
TDD_IGNORE_PATTERNS=()
TDD_TEST_COMMAND_REGEX=''
# shellcheck source=/dev/null
. "$TDD_LIB_DIR/tdd.conf"

if [ "${#TDD_TEST_PATTERNS[@]}" -eq 0 ]; then
  echo "tdd: TDD_TEST_PATTERNS in $TDD_LIB_DIR/tdd.conf is empty" >&2
  return 2 2>/dev/null || exit 2
fi

# Succeeds when the repo-relative path is test code (ignored files included).
tdd_is_test() {
  local pat
  for pat in "${TDD_TEST_PATTERNS[@]}"; do
    # shellcheck disable=SC2053
    [[ "$1" == $pat ]] && return 0
  done
  return 1
}

# Fills the array TDD_PATHSPEC with the git pathspecs of a snapshot scope.
#   tests: test code, minus the ignored files
#   main:  everything else, minus the changes' tasks.md files
tdd_pathspec() {
  local pat
  TDD_PATHSPEC=()
  case "$1" in
    tests)
      for pat in "${TDD_TEST_PATTERNS[@]}"; do TDD_PATHSPEC+=("$pat"); done
      for pat in ${TDD_IGNORE_PATTERNS[@]+"${TDD_IGNORE_PATTERNS[@]}"}; do
        TDD_PATHSPEC+=(":(exclude)$pat")
      done
      ;;
    main)
      TDD_PATHSPEC=('.')
      for pat in "${TDD_TEST_PATTERNS[@]}"; do TDD_PATHSPEC+=(":(exclude)$pat"); done
      TDD_PATHSPEC+=(':(exclude)openspec/changes/*/tasks.md')
      ;;
    *) echo "scope must be tests or main" >&2; return 2 ;;
  esac
}
