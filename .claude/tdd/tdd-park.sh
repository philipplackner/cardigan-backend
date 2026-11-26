#!/usr/bin/env bash
# Records test files as a bundle, and by default moves them out of the source tree.
#
#   tdd-park.sh <workspace> <state-dir> <bundle> <file>...
#   tdd-park.sh --keep <workspace> <state-dir> <bundle> <file>...
#
# Files are paths relative to the workspace that match TDD_TEST_PATTERNS in
# tdd.conf. <state-dir>/base holds the commit the bundles are measured against
# (written by the coordinator once the setup step is committed).
#
# Default: copies each file to <state-dir>/bundles/<bundle>/files/<path>, records
# its content in <state-dir>/bundles/<bundle>/snapshot, and puts the workspace
# back to the base for that path (a new file is removed, a changed one restored).
# The tests stay out of the build until `tdd-activate.sh` brings the bundle back.
#
# --keep: records the files as they are now and marks the bundle active, without
# touching the workspace. Used for tests that may stay in place (the adversary's
# tests) and to re-record a bundle after a dispute verdict changed one of its
# tests. Re-recording replaces the bundle's snapshot.
set -euo pipefail

keep=0
[ "${1:-}" = "--keep" ] && { keep=1; shift; }
ws="${1:?workspace}"; state="${2:?state dir}"; bundle="${3:?bundle name}"; shift 3
[ "$#" -gt 0 ] || { echo "no files given" >&2; exit 2; }
base="$(cat "$state/base" 2>/dev/null)" || { echo "missing $state/base" >&2; exit 2; }
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tdd-lib.sh
. "$here/tdd-lib.sh"

dir="$state/bundles/$bundle"
if [ -e "$dir" ] && [ "$keep" -eq 0 ]; then
  echo "bundle $bundle exists already" >&2; exit 2
fi

cd "$ws"
for path in "$@"; do
  tdd_is_test "$path" || { echo "not a test file (see tdd.conf): $path" >&2; exit 2; }
  [ -f "$path" ] || { echo "missing file: $path" >&2; exit 2; }
  # A file belongs to exactly one bundle.
  if [ -d "$state/bundles" ]; then
    for snap in "$state"/bundles/*/snapshot; do
      [ -f "$snap" ] && [ "$snap" != "$dir/snapshot" ] || continue
      if awk -v p="$path" '$2 == p { found = 1 } END { exit !found }' "$snap"; then
        echo "$path is already in bundle $(basename "$(dirname "$snap")")" >&2; exit 2
      fi
    done
  fi
done

rm -rf "$dir"
mkdir -p "$dir/files"
for path in "$@"; do
  mkdir -p "$dir/files/$(dirname "$path")"
  cp -p -- "$path" "$dir/files/$path"
  printf '%s %s\n' "$(git hash-object -w -- "$path")" "$path"
done | LC_ALL=C sort -k2 > "$dir/snapshot"

if [ "$keep" -eq 1 ]; then
  touch "$dir/active"
  echo "Recorded bundle $bundle ($# file(s)), active"
  exit 0
fi

for path in "$@"; do
  if git cat-file -e "$base:$path" 2>/dev/null; then
    git show "$base:$path" > "$path"
  else
    rm -f -- "$path"
  fi
done
echo "Parked bundle $bundle ($# file(s))"
