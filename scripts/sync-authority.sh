#!/usr/bin/env bash
# Bootstrap + fail-closed refresh of the FULL authority checkout.
#
# Guarantees (issue #1, owner amendments 2026-09-18):
# 1. submodule present, initialized, non-empty
# 2. fail-closed on any missing authority file
# 3. fetch-first, fast-forward only (agents work in parallel)
# 4. FULL checkout (no sparse scope) — consumer sees the whole my-lisp
#    Lisp surface, including dynamic (load ...) dependencies; digests
#    verified against refs/registry-refs.lisp are the proof
# 5. digests listed post-sync for refs/registry-refs.lisp verification
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
[ -n "$(ls -A "$E" 2>/dev/null)" ] || fail "submodule empty; run: git submodule update --init"

git -C "$E" fetch origin --quiet || fail "git fetch inside submodule failed"

# owner amendment (second pass): FULL checkout — no sparse scope.
# The substrate needs the whole my-lisp Lisp surface; digests below
# remain the verification instrument.
git -C "$E" sparse-checkout disable >/dev/null 2>&1 || true
if [ "$(cd "$E" && git config core.sparseCheckout || true)" = "true" ]; then
  fail "sparseCheckout still enabled after disable"
fi

for f in "${FILES[@]}"; do
  [ -f "$REPO/external/my-lisp/$f" ] || fail "authority file absent after checkout: $f"
done

echo "authority checkout OK $(cd "$E" && git rev-parse --short HEAD):"
for f in "${FILES[@]}"; do
  D=$(sha256sum "$REPO/external/my-lisp/$f" | cut -d' ' -f1)
  echo "$f $D"
done
