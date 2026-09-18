#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
ROOT=${1:-}
if [ -z "$ROOT" ]; then
  ROOT=$(bash "$REPO/scripts/resolve-my-lisp.sh")
else
  ROOT=$(cd "$ROOT" && pwd -P)
fi

fail() {
  echo "authority verification FAIL-CLOSED: $*" >&2
  exit 1
}

required=(
  language-contract.lisp
  my-lisp-constitution.lisp
  lib/canon.lisp
  lib/surface/semantic-registry.lisp
  lib/core.lisp
  lib/macro.lisp
  tests/fixtures/conformance.lisp
)

[ -d "$ROOT" ] || fail "my-lisp working tree missing: $ROOT"
for path in "${required[@]}"; do
  [ -f "$ROOT/$path" ] || fail "missing $ROOT/$path"
done

# This repository consumes the current upstream working tree directly.
# It deliberately does not duplicate or pin semantic files here.
HEAD=$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || true)
echo "AUTHORITY-WORKTREE-GREEN"
echo "  root=$ROOT"
[ -n "$HEAD" ] && echo "  upstream-head=$HEAD"
