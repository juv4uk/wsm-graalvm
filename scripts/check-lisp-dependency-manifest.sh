#!/usr/bin/env bash
# Fail-closed validation of the current M0/M1 Lisp dependency declaration.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
E="$REPO/external/my-lisp"
MANIFEST="$REPO/refs/lisp-dependency-manifest.lisp"

fail() { echo "dependency-manifest FAIL-CLOSED: $*" >&2; exit 1; }

[ -f "$MANIFEST" ] || fail "manifest missing: $MANIFEST"
[ -d "$E" ] || fail "authority submodule missing: $E"
[ -f "$E/.git" ] || fail "authority submodule not initialized"

ALLOWLIST="$REPO/refs/sparse-authority-paths.txt"
GENERATOR="$REPO/scripts/build-sparse-authority-allowlist.sh"

[ -f "$ALLOWLIST" ] || fail "sparse allowlist missing: $ALLOWLIST"
[ -f "$GENERATOR" ] || fail "sparse allowlist generator missing: $GENERATOR"

GENERATED=$(mktemp)
trap 'rm -f "$GENERATED"' EXIT
bash "$GENERATOR" "$MANIFEST" > "$GENERATED"
cmp -s "$GENERATED" "$ALLOWLIST" || {
  echo "Committed sparse allowlist is stale:" >&2
  diff -u "$ALLOWLIST" "$GENERATED" >&2 || true
  fail "manifest-derived sparse allowlist drift"
}

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
done

for path in "lib/canon.lisp" "lib/macro.lisp" "lib/core.lisp"; do
  grep -Fq ""$path"" "$MANIFEST" || fail "bootstrap path absent from manifest: $path"
done

LOAD_ORDER=$(awk '
  /^[[:space:]]*\(load-order[[:space:]]*$/ { in_order=1; next }
  in_order && /^[[:space:]]*\(witness[[:space:]]*$/ { exit }
  in_order && match($0, /"[^"]+\.lisp"/) {
    print substr($0, RSTART + 1, RLENGTH - 2)
  }
' "$MANIFEST")

EXPECTED_LOAD_ORDER=$(printf '%s\n'   "lib/surface/semantic-registry.lisp"   "lib/canon.lisp"   "lib/macro.lisp"   "lib/core.lisp")

[ "$LOAD_ORDER" = "$EXPECTED_LOAD_ORDER" ] || {
  echo "Expected bootstrap load order:" >&2
  printf '%s\n' "$EXPECTED_LOAD_ORDER" >&2
  echo "Manifest bootstrap load order:" >&2
  printf '%s\n' "$LOAD_ORDER" >&2
  fail "bootstrap load order diverges from #31 closure order"
}

echo "DEPENDENCY-MANIFEST-GREEN"
echo "  pin=$PIN"
echo "  bootstrap=canon,macro,core"
echo "  witness=conformance"
