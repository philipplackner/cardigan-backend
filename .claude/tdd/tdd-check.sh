#!/usr/bin/env bash
# Fails when files in a scope changed after a snapshot was taken.
#
#   tdd-check.sh <worktree> <base-rev> <tests|main> <snapshot-file>
#
# Takes a fresh snapshot (see tdd-snapshot.sh) and compares it with the saved
# one. Prints the paths that were added, changed or removed since, and exits 1
# if there are any. Exits 0 when the scope is untouched.
set -euo pipefail

wt="${1:?worktree}"; base="${2:?base revision}"; scope="${3:?tests|main}"; saved="${4:?snapshot file}"
here="$(cd "$(dirname "$0")" && pwd)"

now="$(mktemp)"; trap 'rm -f "$now"' EXIT
bash "$here/tdd-snapshot.sh" "$wt" "$base" "$scope" "$now"

if diff -q "$saved" "$now" >/dev/null; then
  echo "OK: $scope unchanged since the snapshot"
  exit 0
fi

echo "CHANGED: $scope files differ from the snapshot"
LC_ALL=C join -j 2 -a 1 -a 2 -e MISSING -o 0,1.1,2.1 \
  <(LC_ALL=C sort -k2 "$saved") \
  <(LC_ALL=C sort -k2 "$now") \
  | awk '$2 != $3 { printf "  %s  (snapshot: %s, now: %s)\n", $1, $2, $3 }'
exit 1
