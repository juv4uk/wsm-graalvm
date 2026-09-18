#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MYLISP=${MYLISP:-$REPO/external/my-lisp}

if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi
G=${G:?GraalVM root is required}
export G MYLISP

manifest_pin() {
  sed -n 's/^[[:space:]]*(pin \. "\([0-9a-f]\{40\}\)").*/\1/p' "$REPO/refs/lisp-dependency-manifest.lisp"
}

[ -d "$MYLISP/.git" ] || { echo "RELEASE-GATE FAIL: missing external/my-lisp checkout" >&2; exit 1; }
PIN=$(git -C "$MYLISP" rev-parse HEAD)
MANIFEST_PIN=$(manifest_pin)
[ "$PIN" = "$MANIFEST_PIN" ] || {
  echo "RELEASE-GATE FAIL: authority pin mismatch $PIN != $MANIFEST_PIN" >&2
  exit 1
}
printf "%s\n" "$PIN" > "$REPO/release-my-lisp-pin.txt"

bash "$REPO/scripts/check-gitlink-topology.sh"
bash "$REPO/scripts/verify-authority.sh" "$MYLISP"
bash "$REPO/scripts/check-lisp-mechanism-budget.sh"
bash "$REPO/scripts/check-java-spelling-firewall.sh"

bash "$REPO/scripts/build.sh"
bash "$REPO/scripts/test-reader-string.sh"
bash "$REPO/scripts/test-conformance-inventory.sh"
bash "$REPO/scripts/test-real-tier1-inventory.sh"
bash "$REPO/scripts/test-truffle-frames.sh"
bash "$REPO/scripts/test-macro-peer-installer.sh"
bash "$REPO/scripts/test-materialized-define-binder.sh"
bash "$REPO/scripts/test-migration-cond-truthiness.sh"
bash "$REPO/scripts/test-semantic-eval.sh"
bash "$REPO/scripts/check-lisp-dependency-manifest.sh"
bash "$REPO/scripts/test-exact-q-comparison.sh"
bash "$REPO/scripts/test-bootstrap-closure-loader.sh"
bash "$REPO/scripts/test-let-raw-expansion.sh"
bash "$REPO/scripts/test-real-lisp-bootstrap.sh"
bash "$REPO/scripts/test-variadic-lambda.sh"
bash "$REPO/scripts/test-tier1-error-parity.sh"
MYLISP="$MYLISP" bash "$REPO/scripts/run-canon.sh"
bash "$REPO/scripts/test-tier1-progress.sh"
bash "$REPO/scripts/test-write-to-string.sh"
bash "$REPO/scripts/test-context.sh" "$MYLISP/lib/surface/semantic-registry.lisp"

echo "RELEASE-GATE-OK"