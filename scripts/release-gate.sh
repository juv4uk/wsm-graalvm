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

require_pin() {
  [ -d "$MYLISP/.git" ] || {
    echo "RELEASE-GATE FAIL: missing pinned my-lisp checkout: $MYLISP" >&2
    exit 1
  }
  local pin manifest_pin
  pin=$(git -C "$MYLISP" rev-parse HEAD)
  manifest_pin=$(sed -n 's/^[[:space:]]*(pin \. "\([0-9a-f]\{40\}\)").*/\1/p' "$REPO/refs/lisp-dependency-manifest.lisp")
  [ -n "$manifest_pin" ] || {
    echo "RELEASE-GATE FAIL: unreadable authority manifest pin" >&2
    exit 1
  }
  [ "$pin" = "$manifest_pin" ] || {
    echo "RELEASE-GATE FAIL: my-lisp checkout $pin != manifest $manifest_pin" >&2
    exit 1
  }
  printf '%s\n' "$pin" > "$REPO/release-my-lisp-pin.txt"
}

echo "RELEASE-GATE: authority"
bash "$REPO/scripts/check-gitlink-topology.sh"
bash "$REPO/scripts/verify-authority.sh" "$MYLISP"
require_pin
bash "$REPO/scripts/check-lisp-mechanism-budget.sh"
bash "$REPO/scripts/check-java-spelling-firewall.sh"

echo "RELEASE-GATE: conformance/bootstrap"
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

echo "RELEASE-GATE: native-image"
MYLISP="$MYLISP" bash "$REPO/scripts/build-native.sh"

[ -x "$REPO/native-wsm" ] || {
  echo "RELEASE-GATE FAIL: Linux Native Image executable missing" >&2
  exit 1
}

echo "RELEASE-GATE-OK"
