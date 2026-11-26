#!/usr/bin/env bash
# Records the content of every file in a scope that differs from a base commit.
#
#   tdd-snapshot.sh <worktree> <base-rev> <tests|main> [out-file]
#
# Scope `tests`: files matching TDD_TEST_PATTERNS in tdd.conf, minus
#                TDD_IGNORE_PATTERNS.
# Scope `main`:  every other file, except the changes' tasks.md files.
#
# Compares the base commit with the working tree (committed, staged, unstaged
# and untracked changes alike), so a snapshot taken before a commit equals one
# taken after it when nothing changed in between. Each line is
# `<blob-hash-or-DELETED> <path>`, sorted. Blobs are written to the object
# store, so a snapshot can be restored later with `git cat-file -p <hash>`.
#
# Without out-file the snapshot goes to stdout.
set -euo pipefail

wt="${1:?worktree}"; base="${2:?base revision}"; scope="${3:?tests|main}"; out="${4:-}"
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tdd-lib.sh
. "$here/tdd-lib.sh"
tdd_pathspec "$scope"

cd "$wt"
git rev-parse --verify --quiet "$base^{commit}" >/dev/null || { echo "unknown base revision: $base" >&2; exit 2; }

{
  git diff --name-only "$base" -- "${TDD_PATHSPEC[@]}"
  git ls-files --others --exclude-standard -- "${TDD_PATHSPEC[@]}"
} | LC_ALL=C sort -u | while IFS= read -r path; do
  [ -z "$path" ] && continue
  if [ -f "$path" ]; then
    printf '%s %s\n' "$(git hash-object -w -- "$path")" "$path"
  else
    printf 'DELETED %s\n' "$path"
  fi
done > "${out:-/dev/stdout}"
