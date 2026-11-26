#!/usr/bin/env bash
# Fails unless every test file in the workspace is exactly what the bundles say.
#
#   tdd-check-tests.sh <workspace> <state-dir>
#
# Expected: the snapshots of all active bundles. Actual: every test file (see
# tdd.conf) that differs from <state-dir>/base, whether committed, staged,
# unstaged or untracked. A parked bundle is expected to be absent. Prints the
# differences and exits 1 if there are any.
set -euo pipefail

ws="${1:?workspace}"; state="${2:?state dir}"
here="$(cd "$(dirname "$0")" && pwd)"
base="$(cat "$state/base")"

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
bash "$here/tdd-snapshot.sh" "$ws" "$base" tests "$tmp/actual"
: > "$tmp/expected"
for dir in "$state"/bundles/*/; do
  [ -f "$dir/active" ] && cat "$dir/snapshot" >> "$tmp/expected"
done

LC_ALL=C sort -k2 "$tmp/actual" > "$tmp/a"
LC_ALL=C sort -k2 "$tmp/expected" > "$tmp/e"

if diff -q "$tmp/e" "$tmp/a" >/dev/null; then
  echo "OK: test files match the active bundles ($(wc -l < "$tmp/e" | tr -d ' ') file(s))"
  exit 0
fi

echo "CHANGED: test files differ from the active bundles"
LC_ALL=C join -j 2 -a 1 -a 2 -e MISSING -o 0,1.1,2.1 "$tmp/e" "$tmp/a" \
  | awk '$2 != $3 { printf "  %s  (expected: %s, now: %s)\n", $1, $2, $3 }'
exit 1
