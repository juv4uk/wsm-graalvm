#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$REPO/build/substrate-switch-proof.json"}
mkdir -p "$(dirname "$OUT")"

: "${G:=}"
if [ -z "$G" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

HEAD=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
[ -n "$PIN" ] || { echo "cannot read external/my-lisp gitlink pin" >&2; exit 1; }

bash "$REPO/scripts/sync-authority.sh"
ACTUAL_PIN=$(git -C "$REPO/external/my-lisp" rev-parse HEAD)
[ "$PIN" = "$ACTUAL_PIN" ] || {
  echo "gitlink/submodule mismatch: $PIN != $ACTUAL_PIN" >&2
  exit 1
}

# Fresh evidence replay on this exact pair.
bash "$REPO/scripts/check-lisp-dependency-manifest.sh"
bash "$REPO/scripts/check-graal-java-residue-inventory.sh"
bash "$REPO/scripts/test-inventory-negative-regression.sh"
WSM_EVIDENCE_COMMIT="$HEAD" G="$G"   bash "$REPO/scripts/test-real-tier1-inventory.sh"
WSM_EVIDENCE_COMMIT="$HEAD" G="$G"   bash "$REPO/scripts/test-real-lisp-bootstrap.sh"
WSM_EVIDENCE_COMMIT="$HEAD" JAVA_HOME="$G"   bash "$REPO/scripts/run-substrate-parity-smoke.sh" "$REPO/build/substrate-parity-smoke.json"
WSM_EVIDENCE_COMMIT="$HEAD" G="$G"   bash "$REPO/scripts/test-cold-start-no-rust.sh"

GRAAL_VERSION=$("$G/bin/java" --version 2>&1 | head -n 1)

python3 "$REPO/scripts/write-substrate-switch-proof.py"   --checklist "$REPO/refs/substrate-switch-checklist.json"   --parity "$REPO/build/substrate-parity-smoke.json"   --inventory "$REPO/refs/graal-java-residue-inventory.json"   --mechanism-budget "$REPO/refs/lisp-mechanism-budget.lisp"   --wsm-head "$HEAD"   --my-lisp-pin "$PIN"   --graal-version "$GRAAL_VERSION"   --out "$OUT"
