#!/usr/bin/env bash
# Resolve the single upstream my-lisp authority working tree.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
ROOT=${WSM_LISP_HOME:-${MYLISP:-"$REPO/../my-lisp"}}

if [[ "$ROOT" != /* ]]; then
  ROOT="$REPO/$ROOT"
fi

fail() {
  echo "my-lisp authority FAIL-CLOSED: $*" >&2
  exit 1
}

[ -d "$ROOT" ] || fail "working tree not found: $ROOT (set WSM_LISP_HOME or keep my-lisp beside wsm-graalvm)"
ROOT=$(cd "$ROOT" && pwd -P)

required=(
  language-contract.lisp
  my-lisp-constitution.lisp
  lib/canon.lisp
  lib/surface/semantic-registry.lisp
  lib/core.lisp
  lib/macro.lisp
  tests/fixtures/conformance.lisp
)

for path in "${required[@]}"; do
  [ -f "$ROOT/$path" ] || fail "required upstream Lisp source missing: $path"
done

printf '%s\n' "$ROOT"
