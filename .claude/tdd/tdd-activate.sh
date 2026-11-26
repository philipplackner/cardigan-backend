#!/usr/bin/env bash
# Brings a parked bundle of tests back into the workspace.
#
#   tdd-activate.sh <workspace> <state-dir> <bundle>
#
# The implementer runs this at the start of the group the bundle belongs to.
# Refuses, and copies nothing, when a target file no longer matches the base
# (something else changed it since the bundle was parked). Every copied file
# is checked against the bundle's snapshot, then the bundle is marked active.
# Running it again on an active bundle whose files are in place does nothing.
set -euo pipefail

ws="${1:?workspace}"; state="${2:?state dir}"; bundle="${3:?bundle name}"
dir="$state/bundles/$bundle"; base="$(cat "$state/base")"
[ -f "$dir/snapshot" ] || { echo "unknown bundle: $bundle" >&2; exit 2; }

cd "$ws"
if [ -f "$dir/active" ]; then
  while read -r hash path; do
    [ -f "$path" ] && [ "$(git hash-object -- "$path")" = "$hash" ] \
      || { echo "bundle $bundle is active but $path does not match it" >&2; exit 1; }
  done < "$dir/snapshot"
  echo "Bundle $bundle is already active"
  exit 0
fi

conflicts=""
while read -r hash path; do
  if git cat-file -e "$base:$path" 2>/dev/null; then
    current="$( [ -f "$path" ] && git hash-object -- "$path" || echo MISSING )"
    [ "$current" = "$(git rev-parse "$base:$path")" ] || conflicts="$conflicts $path"
  else
    [ -e "$path" ] && conflicts="$conflicts $path"
  fi
done < "$dir/snapshot"
if [ -n "$conflicts" ]; then
  echo "CONFLICT: these files changed since bundle $bundle was parked:"
  for p in $conflicts; do echo "  $p"; done
  exit 1
fi

while read -r hash path; do
  mkdir -p "$(dirname "$path")"
  cp -p -- "$dir/files/$path" "$path"
  [ "$(git hash-object -- "$path")" = "$hash" ] || { echo "parked copy of $path does not match its snapshot" >&2; exit 1; }
done < "$dir/snapshot"
touch "$dir/active"
echo "Activated bundle $bundle ($(wc -l < "$dir/snapshot" | tr -d ' ') file(s))"
