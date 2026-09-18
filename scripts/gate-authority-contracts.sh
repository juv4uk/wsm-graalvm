#!/usr/bin/env bash
# Gate #9.0 — prove which pinned Lisp-owned authority defines current control.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
E="$REPO/external/my-lisp"

[ -x "$REPO/scripts/sync-authority.sh" ] || {
  echo "AUTHORITY-GATE: scripts/sync-authority.sh missing; integrate #1 first" >&2
  exit 1
}

"$REPO/scripts/sync-authority.sh" >/dev/null

CONTROL="$E/contracts/control-dispatch-contract.lisp"
STRUCTURAL="$E/contracts/structural-observation-contract.lisp"
FIXTURES="$E/tests/fixtures/control-dispatch-v1.lisp"
CONFORMANCE="$E/tests/fixtures/conformance.lisp"
REGISTRY="$E/lib/surface/semantic-registry.lisp"

for f in "$CONTROL" "$STRUCTURAL" "$FIXTURES" "$CONFORMANCE" "$REGISTRY"; do
  [ -f "$f" ] || {
    echo "AUTHORITY-GATE: missing pinned authority file: $f" >&2
    exit 1
  }
done

require_literal() {
  local file=$1
  local literal=$2
  grep -Fq "$literal" "$file" || {
    echo "AUTHORITY-GATE: expected authority fact absent" >&2
    echo "  file: $file" >&2
    echo "  fact: $literal" >&2
    exit 1
  }
}

require_literal "$CONTROL" "(identity . \"0007\")"
require_literal "$CONTROL" "(selection-rule . explicit-result-equality)"
require_literal "$CONTROL" "(generic-truth-coercion . forbidden)"
require_literal "$CONTROL" "(empty-list-as-false . forbidden)"
require_literal "$CONTROL" "(arbitrary-nonempty-as-true . forbidden)"
require_literal "$CONTROL" "(compatibility . historical-two-part-cond)"
require_literal "$CONTROL" "(status . migration-only)"
require_literal "$CONTROL" "(semantic-authority . forbidden)"

require_literal "$STRUCTURAL" "Historical two-part cond remains migration-only."
require_literal "$FIXTURES" "#217 — canonical explicit-result dispatch witnesses."

# Do not silently bless the whole legacy conformance corpus as current control
# authority. Record the known migration debt if it is still present.
if grep -Fq '(cond (0 (quote truthy)) (t (quote falsy)))' "$CONFORMANCE"; then
  echo "AUTHORITY-GATE NOTE: legacy truthiness fixture still exists in conformance.lisp;"
  echo "AUTHORITY-GATE NOTE: current control authority remains contracts/control-dispatch-contract.lisp."
fi

echo "AUTHORITY-CONTRACT-GATE-OK"
