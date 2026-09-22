#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
SRC=${EVIDENCE_ROOT:-"$REPO/build"}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

FILES=(
  substrate-switch-proof.json
  substrate-parity-smoke.json
  cold-start-no-rust.json
  tier1-evidence.json
  real-lisp-bootstrap-evidence.json
)

for f in "${FILES[@]}"; do
  cp "$SRC/$f" "$TMP/$f"
done

# Corrupt one exact coordinate. The REAL freshness checker must reject it.
python3 - "$TMP/substrate-switch-proof.json" <<'PY'
import json
import sys
from pathlib import Path
p = Path(sys.argv[1])
d = json.loads(p.read_text(encoding="utf-8"))
d["exact_pair"]["wsm_graalvm_head"] = "0" * 40
p.write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")
PY

set +e
EVIDENCE_ROOT="$TMP" \
WSM_EVIDENCE_COMMIT="${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}" \
bash "$REPO/scripts/check-evidence-freshness.sh" >/tmp/evidence-freshness-negative.log 2>&1
STATUS=$?
set -e

cat /tmp/evidence-freshness-negative.log
rm -f /tmp/evidence-freshness-negative.log

if [ "$STATUS" -eq 0 ]; then
  echo "EVIDENCE-FRESHNESS-NEGATIVE FAIL: stale evidence was accepted" >&2
  exit 1
fi

echo "EVIDENCE-FRESHNESS-NEGATIVE-GREEN stale-coordinate-rejected"
