#!/usr/bin/env bash
# Validate the current M0/M1 Lisp dependency declaration against the pinned tree.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
E="$REPO/external/my-lisp"
MANIFEST="$REPO/refs/lisp-dependency-manifest.lisp"

fail() {
  echo "dependency-manifest FAIL-CLOSED: $*" >&2
  exit 1
}

[ -f "$MANIFEST" ] || fail "manifest missing: $MANIFEST"
[ -d "$E" ] || fail "authority submodule missing: $E"
[ -f "$E/.git" ] || fail "authority submodule not initialized"

PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '{print $2}')
HEAD=$(git -C "$E" rev-parse HEAD)
[ -n "$PIN" ] || fail "cannot read external/my-lisp gitlink pin"
[ "$HEAD" = "$PIN" ] || fail "authority HEAD $HEAD differs from gitlink pin $PIN"

MANIFEST_PIN=$(sed -n 's/^[[:space:]]*(pin[[:space:]]\+\.[[:space:]]*"\([0-9a-f]\{40\}\)")[[:space:]]*$/\1/p' "$MANIFEST")
[ -n "$MANIFEST_PIN" ] || fail "manifest has no 40-hex pin"
[ "$MANIFEST_PIN" = "$PIN" ] || fail "manifest pin $MANIFEST_PIN differs from gitlink pin $PIN"

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
  case "$path" in
    *.lisp) ;;
    *) fail "non-Lisp file in runtime manifest: $path" ;;
  esac
done

for path in   "lib/canon.lisp"   "lib/macro.lisp"   "lib/core.lisp"; do
  grep -Fq ""$path"" "$MANIFEST" || fail "bootstrap path absent from manifest: $path"
done

# M0/M1 deliberately excludes machine-specific and generated projection trees
# from the bootstrap. This catches accidental materialization into the
# consumer closure while leaving those trees available for later witnesses.
if grep -Eq '^\s*\("lib/machine/' "$MANIFEST"; then
  true
fi

echo "DEPENDENCY-MANIFEST-GREEN"
echo "  pin=$PIN"
echo "  bootstrap=canon,macro,core"
echo "  evidence=conformance"
