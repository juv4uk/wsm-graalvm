#!/usr/bin/env bash
# Ordered semantic gates. Fail on the first divergence; speed work is downstream.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)

gate() {
  local name=$1
  shift
  echo "== SEMANTIC GATE: $name =="
  "$@"
  echo "== GREEN: $name =="
}

gate "0 authority precedence" "$REPO/scripts/gate-authority-contracts.sh"
gate "1 real pinned registry" "$REPO/scripts/test-real-registry.sh"
gate "1b Polyglot registry wiring" "$REPO/scripts/test-polyglot-wiring.sh"
gate "2 SemanticRef identity" "$REPO/scripts/test-semantic-identity.sh"
gate "3 ID-only mechanism table" "$REPO/scripts/test-semantic-mechanism-table.sh"
gate "4 one-way resolver" "$REPO/scripts/test-semantic-resolver.sh"
gate "5 current Canon control" "$REPO/scripts/test-current-canon-control.sh"
gate "6 closed ErrorKind vocabulary" "$REPO/scripts/test-error-kind.sh"
gate "7 runtime Truffle lexical frames" "$REPO/scripts/test-lexical-frames.sh"
gate "8 quote identity and apostrophe reader" "$REPO/scripts/test-quote-reader.sh"
gate "9 compatibility semantic ID 1000" "$REPO/scripts/test-def-compatibility.sh"
gate "10 real canon.lisp conformance" "$REPO/scripts/test-real-canon-conformance.sh"

echo "SEMANTIC-GATES-GREEN: optimization may consume these admitted semantics; it may not redefine them."
