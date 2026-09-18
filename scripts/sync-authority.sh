#!/usr/bin/env bash
# Bootstrap + fail-closed refresh of the Lisp-only my-lisp authority checkout.
#
# Owner rule:
#   external/my-lisp is a pinned Git submodule, but its working tree exposes
#   only *.lisp files. Graal consumes Lisp authority, not Rust/tooling/docs.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
E="$REPO/external/my-lisp"

FILES=(
  language-contract.lisp
  my-lisp-constitution.lisp
  lib/canon.lisp
  lib/surface/semantic-registry.lisp
  lib/core.lisp
  lib/macro.lisp
  lib/meta-eval.lisp
  lib/machine/authority-boundary.lisp
  tests/fixtures/conformance.lisp
)

fail() {
  echo "sync-authority FAIL-CLOSED: $*" >&2
  exit 1
}

[ -d "$E" ] || fail "submodule external/my-lisp missing"
[ -f "$E/.git" ] || fail "submodule not initialized (no .git file)"
[ -n "$(ls -A "$E" 2>/dev/null)" ]   || fail "submodule empty; run: git submodule update --init external/my-lisp"

# The superproject gitlink is the dependency pin. Fetch may discover upstream
# changes, but this command never advances the submodule beyond that pin.
git -C "$E" fetch origin --quiet   || fail "git fetch inside submodule failed"

PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '{print $2}')
HEAD=$(git -C "$E" rev-parse HEAD)
[ -n "$PIN" ] || fail "cannot read submodule gitlink pin"
[ "$HEAD" = "$PIN" ]   || fail "submodule HEAD $HEAD differs from superproject pin $PIN"

# Non-cone mode accepts gitignore-style file patterns. A slashless *.lisp
# pattern matches Lisp files recursively while preserving their directory tree.
printf '%s\n' '*.lisp'   | git -C "$E" sparse-checkout set --no-cone --stdin >/dev/null   || fail "cannot enable Lisp-only sparse checkout"

git -C "$E" sparse-checkout reapply >/dev/null   || fail "cannot reapply Lisp-only sparse checkout"

SC=$(git -C "$E" config --bool core.sparseCheckout || true)
CONE=$(git -C "$E" config --bool core.sparseCheckoutCone || true)
[ "$SC" = "true" ] || fail "sparseCheckout is not enabled"
[ "$CONE" != "true" ] || fail "Lisp-only checkout unexpectedly entered cone mode"

for f in "${FILES[@]}"; do
  [ -f "$E/$f" ] || fail "required Lisp authority file absent: $f"
done

# Strict materialization rule: apart from the submodule's .git control file,
# every regular file visible in the authority working tree must be Lisp.
NON_LISP=$(
  find "$E" -type f     ! -path "$E/.git"     ! -name '*.lisp'     -print
)
[ -z "$NON_LISP" ] || {
  printf '%s\n' "$NON_LISP" >&2
  fail "non-Lisp files materialized inside external/my-lisp"
}

LISP_COUNT=$(find "$E" -type f -name '*.lisp' | wc -l | tr -d ' ')
[ "$LISP_COUNT" -gt 0 ] || fail "Lisp-only checkout materialized zero Lisp files"

echo "Lisp-only authority checkout OK:"
echo "  pin=$HEAD"
echo "  materialized-lisp-files=$LISP_COUNT"
echo "  sparse-pattern=*.lisp"

for f in "${FILES[@]}"; do
  D=$(sha256sum "$E/$f" | cut -d' ' -f1)
  echo "$f $D"
done
