#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
cd "$REPO"

run_gate() {
  local label="$1"
  shift
  echo "RELEASE-GATE :: $label"
  "$@"
}

run_gate "unexpected gitlinks" bash scripts/check-gitlink-topology.sh
run_gate "Lisp-first mechanism budget" bash scripts/check-lisp-mechanism-budget.sh
run_gate "Java public-surface spelling firewall" bash scripts/check-java-spelling-firewall.sh
run_gate "build" bash scripts/build.sh

run_gate "reader/string" bash scripts/test-reader-string.sh
run_gate "conformance inventory" bash scripts/test-conformance-inventory.sh
run_gate "real Tier-1 inventory" bash scripts/test-real-tier1-inventory.sh
run_gate "Truffle frames" bash scripts/test-truffle-frames.sh
run_gate "macro peer installer" bash scripts/test-macro-peer-installer.sh
run_gate "materialized DEFINE binder" bash scripts/test-materialized-define-binder.sh
run_gate "migration COND truthiness" bash scripts/test-migration-cond-truthiness.sh
run_gate "semantic eval" bash scripts/test-semantic-eval.sh
run_gate "dependency manifest" bash scripts/check-lisp-dependency-manifest.sh
run_gate "exact-Q comparison" bash scripts/test-exact-q-comparison.sh
run_gate "bootstrap closure loader" bash scripts/test-bootstrap-closure-loader.sh
run_gate "let raw expansion" bash scripts/test-let-raw-expansion.sh
run_gate "real Lisp bootstrap" bash scripts/test-real-lisp-bootstrap.sh
run_gate "variadic lambda" bash scripts/test-variadic-lambda.sh
run_gate "Tier-1 ErrorKind parity" bash scripts/test-tier1-error-parity.sh
run_gate "Canon self-verdict" bash scripts/run-canon.sh
run_gate "Tier-1 progress ledger" bash scripts/test-tier1-progress.sh
run_gate "write-to-string" bash scripts/test-write-to-string.sh
run_gate "context persistence" bash scripts/test-context.sh "$REPO/external/my-lisp/lib/surface/semantic-registry.lisp"

echo "RELEASE-GATE-OK"
