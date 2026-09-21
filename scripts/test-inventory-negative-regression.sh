#!/usr/bin/env bash
# Negative regression test for graal-java-residue-inventory checker.
# Verifies that the REAL checker REJECTS fabricated method names (FAIL-CLOSED).
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
CHECKER="$REPO/scripts/check-graal-java-residue-inventory.sh"
INVENTORY="$REPO/refs/graal-java-residue-inventory.json"

# Create a temp inventory with a fabricated method
TEMP_INV=$(python3 - "$INVENTORY" <<'PY'
import json, sys, tempfile
from pathlib import Path
data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
for e in data["entries"]:
    if "SemanticMechanismTable.java" in e["path"]:
        if "invoke00000010_FABRICATED" not in e["methods"]:
            e["methods"].append("invoke00000010_FABRICATED")
        break
with tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False) as f:
    json.dump(data, f, indent=2)
    print(f.name)
PY
)

# Run the REAL checker against tampered inventory — MUST FAIL (FAIL-CLOSED)
set +e
INVENTORY="$TEMP_INV" "$CHECKER" 2>&1
RESULT=$?
set -e

rm -f "$TEMP_INV"

if [ $RESULT -eq 0 ]; then
    echo "NEGATIVE REGRESSION FAIL: checker accepted fabricated method"
    exit 1
else
    echo "NEGATIVE REGRESSION PASS: checker correctly rejected fabricated method"
    exit 0
fi
