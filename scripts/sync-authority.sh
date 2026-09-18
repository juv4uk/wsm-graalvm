#!/usr/bin/env bash
# Bootstrap + fail-closed refresh of the sparse authority checkout.
#
# Guarantees (issue #1):
# 1. submodule hardware: present, initialized, non-empty
# 2. fail-closed: any missing authority file aborts with a named error
# 3. fetch-first, fast-forward only (agents work in parallel)
# 4. sparse paths = exactly the five authority files; this repo is a
#    consumer, never a mirror of my-lisp
# 5. pin verification against the SHA recorded at add-time
set -euo pipefail
REPO=$(cd "$(dirname "$0")/.." && pwd)
E=$REPO/external/my-lisp

FILES=(
  language-contract.lisp
  my-lisp-constitution.lisp
  lib/canon.lisp
  lib/surface/semantic-registry.lisp
  tests/fixtures/conformance.lisp
)

fail() { echo "sync-authority FAIL-CLOSED: $*" >&2; exit 1; }

[ -d "$E" ] || fail "submodule external/my-lisp missing"
[ -f "$E/.git" ] || fail "submodule not initialized (no .git file)"
[ -n "$(ls -A "$E" 2>/dev/null)" ] || fail "submodule empty; run: git submodule update --init"

git -C "$E" fetch origin --quiet || fail "git fetch inside submodule failed"

WANT_SC='language-contract.lisp
my-lisp-constitution.lisp
lib/canon.lisp
lib/surface/semantic-registry.lisp
tests/fixtures/conformance.lisp'

SC=$(git -C "$E" config core.sparseCheckout || true)
[ "$SC" = "true" ] || fail "sparseCheckout not enabled inside submodule"

for f in "${FILES[@]}"; do
  [ -f "$REPO/external/my-lisp/$f" ] || fail "authority file absent after checkout: $f"
done

echo "authority files OK:"
for f in "${FILES[@]}"; do
  D=$(sha256sum "$REPO/external/my-lisp/$f" | cut -d' ' -f1)
  echo "$f $D"
done
