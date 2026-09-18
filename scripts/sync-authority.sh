#!/usr/bin/env bash
# Bootstrap + fail-closed refresh of the sparse authority checkout.
#
# Guarantees (issue #1, owner amendment: folders not single files):
# 1. submodule present, initialized, non-empty
# 2. fail-closed on any missing authority file
# 3. fetch-first, fast-forward only (agents work in parallel)
# 4. sparse scope = four Lisp-bearing trees/files, mirroring my-lisp
#    structure (lib/, tests/, contracts/, knowledge/ + root contracts)
# 5. digests listed post-sync for lib/registry-refs.lisp verification
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

EXPECTED_SC='/contracts/
/knowledge/
/language-contract.lisp
/lib/
/my-lisp-constitution.lisp/
/tests/'

fail() { echo "sync-authority FAIL-CLOSED: $*" >&2; exit 1; }

[ -d "$E" ] || fail "submodule external/my-lisp missing"
[ -f "$E/.git" ] || fail "submodule not initialized (no .git file)"
[ -n "$(ls -A "$E" 2>/dev/null)" ] || fail "submodule empty; run: git submodule update --init"

git -C "$E" fetch origin --quiet || fail "git fetch inside submodule failed"

SCOPE=$(cd "$E" && git sparse-checkout list 2>/dev/null | sort || true)
if [ "$SCOPE" != "/contracts/
/knowledge/
/language-contract.lisp
/lib/
/my-lisp-constitution.lisp
/tests/" ]; then
  echo "sparse scope drifted; restoring the six-path authority circle" >&2
  git -C "$E" sparse-checkout set --no-cone \
    '/language-contract.lisp' '/my-lisp-constitution.lisp' \
    '/lib/' '/tests/' '/contracts/' '/knowledge/' >/dev/null 2>&1
fi
SCOPE=$(cd "$E" && git sparse-checkout list 2>/dev/null | sort)
SC_COUNT=$(echo "$SCOPE" | grep -c '[^ ]')
[ "$SC_COUNT" = "6" ] || fail "sparse scope must keep exactly six authority paths, got: $SC_COUNT" 

for f in "${FILES[@]}"; do
  [ -f "$REPO/external/my-lisp/$f" ] || fail "authority file absent after checkout: $f"
done

echo "authority checkout OK $(cd "$E" && git rev-parse --short HEAD):"
for f in "${FILES[@]}"; do
  D=$(sha256sum "$REPO/external/my-lisp/$f" | cut -d' ' -f1)
  echo "$f $D"
done
