#!/usr/bin/env bash
# Bootstrap + fail-closed refresh of the Lisp-only my-lisp authority checkout.
set -euo pipefail
REPO=$(cd "$(dirname "$0")/.." && pwd)
E=$REPO/external/my-lisp

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

fail() { echo "sync-authority FAIL-CLOSED: $*" >&2; exit 1; }

[ -d "$E" ] || fail "submodule external/my-lisp missing"
[ -f "$E/.git" ] || fail "submodule not initialized (no .git file)"

git -C "$E" fetch origin --quiet || fail "git fetch inside submodule failed"

# Owner rule: materialize Lisp source only. The submodule still pins the whole
# upstream commit, but the Graal working tree consumes only *.lisp files.
git -C "$E" sparse-checkout init --no-cone >/dev/null
git -C "$E" sparse-checkout set --no-cone '*.lisp' >/dev/null

[ "$(git -C "$E" config core.sparseCheckout || true)" = "true" ]   || fail "sparse checkout not enabled"

for f in "${FILES[@]}"; do
  [ -f "$E/$f" ] || fail "authority Lisp file absent after checkout: $f"
done

# Fail closed if an ordinary non-Lisp file leaked into the materialized tree.
LEAK=$(find "$E" -type f ! -name '*.lisp' ! -name '.git' -print -quit)
[ -z "$LEAK" ] || fail "non-Lisp file materialized in authority checkout: $LEAK"

echo "Lisp-only authority checkout OK $(git -C "$E" rev-parse --short HEAD):"
for f in "${FILES[@]}"; do
  D=$(sha256sum "$E/$f" | cut -d' ' -f1)
  echo "$f $D"
done
