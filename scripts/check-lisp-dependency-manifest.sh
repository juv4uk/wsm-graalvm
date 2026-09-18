#!/usr/bin/env bash
# Fail-closed validation of the current M0/M1 Lisp dependency declaration.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
E=$(bash "$REPO/scripts/resolve-my-lisp.sh")
MANIFEST="$REPO/refs/lisp-dependency-manifest.lisp"

fail() { echo "dependency-manifest FAIL-CLOSED: $*" >&2; exit 1; }

[ -f "$MANIFEST" ] || fail "manifest missing: $MANIFEST"
grep -Fq '(authority-mode . "sibling-worktree")' "$MANIFEST" || fail "manifest is not sibling-worktree authority mode"
grep -Fq '(default-root . "../my-lisp")' "$MANIFEST" || fail "manifest default root is not ../my-lisp"

required=(
  "language-contract.lisp"
  "my-lisp-constitution.lisp"
  "lib/surface/semantic-registry.lisp"
  "lib/canon.lisp"
  "lib/macro.lisp"
  "lib/core.lisp"
  "tests/fixtures/conformance.lisp"
)

for path in "${required[@]}"; do
  [ -f "$E/$path" ] || fail "required source missing: $path"
done

for path in "lib/canon.lisp" "lib/macro.lisp" "lib/core.lisp"; do
  grep -Fq "\"$path\"" "$MANIFEST" || fail "bootstrap path absent from manifest: $path"
done

echo "DEPENDENCY-MANIFEST-GREEN"
echo "  authority=$E"
echo "  mode=sibling-worktree"
echo "  bootstrap=canon,macro,core"
echo "  witness=conformance"
