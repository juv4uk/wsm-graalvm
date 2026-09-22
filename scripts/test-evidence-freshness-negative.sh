#!/usr/bin/env bash
# Prove the real freshness checker rejects evidence from a stale head.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
CHECKER="$REPO/scripts/check-evidence-freshness.sh"
SOURCE="$REPO/build/substrate-parity-smoke.json"
[ -s "$SOURCE" ] || { echo "missing generated parity evidence: $SOURCE" >&2; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cp "$SOURCE" "$TMP/substrate-parity-smoke.json"

python3 - "$TMP/substrate-parity-smoke.json" <<'PY'
import json, sys
from pathlib import Path
p = Path(sys.argv[1])
d = json.loads(p.read_text(encoding="utf-8"))
for row in d.get("rows", []):
    row["wsm_commit"] = "0000000000000000000000000000000000000000"
p.write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")
PY

set +e
EVIDENCE_ROOT="$TMP" EVIDENCE_FILES="substrate-parity-smoke.json" WSM_EVIDENCE_COMMIT="${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}"   bash "$CHECKER" >/tmp/wsm-freshness-negative.log 2>&1
status=$?
set -e

cat /tmp/wsm-freshness-negative.log
rm -f /tmp/wsm-freshness-negative.log

if [ "$status" -eq 0 ]; then
  echo "EVIDENCE-FRESHNESS-NEGATIVE FAIL: stale evidence was accepted" >&2
  exit 1
fi

echo "EVIDENCE-FRESHNESS-NEGATIVE-GREEN stale-head-rejected"
