#!/usr/bin/env bash
# Fail-closed validation of the current M0/M1 Lisp dependency declaration.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
E="$REPO/external/my-lisp"
MANIFEST="$REPO/refs/lisp-dependency-manifest.lisp"

fail() { echo "dependency-manifest FAIL-CLOSED: $*" >&2; exit 1; }

test -f "$MANIFEST" || fail "manifest missing: $MANIFEST"
test -d "$E" || fail "authority submodule missing: $E"
test -f "$E/.git" || fail "authority submodule not initialized"

PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '{print $2}')
HEAD=$(git -C "$E" rev-parse HEAD)
test -n "$PIN" || fail "cannot read external/my-lisp gitlink pin"
test "$HEAD" = "$PIN" || fail "authority HEAD $HEAD differs from gitlink pin $PIN"

MANIFEST_PIN=$(sed -n 's/^[[:space:]]*(pin[[:space:]]\+\.[[:space:]]*"\([0-9a-f]\{40\}\)")[[:space:]]*$/\1/p' "$MANIFEST")
test -n "$MANIFEST_PIN" || fail "manifest has no 40-hex pin"
test "$MANIFEST_PIN" = "$PIN" || fail "manifest pin $MANIFEST_PIN differs from gitlink pin $PIN"

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
  test -f "$E/$path" || fail "required source missing: $path"
done

for path in "lib/canon.lisp" "lib/macro.lisp" "lib/core.lisp"; do
  grep -Fq "$path" "$MANIFEST" ||
    fail "bootstrap path absent from manifest: $path"
done

echo "DEPENDENCY-MANIFEST-GREEN"
echo "  pin=$PIN"
echo "  bootstrap=canon,macro,core"
echo "  witness=conformance"
