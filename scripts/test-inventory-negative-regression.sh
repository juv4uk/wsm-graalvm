#!/usr/bin/env bash
# Negative regression test for graal-java-residue-inventory checker.
# Verifies that the checker REJECTS fabricated method names.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
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

# Run checker logic against tampered inventory — MUST FAIL
set +e
python3 - "$TEMP_INV" "$REPO" <<'PY' 2>&1
import json, sys, re
from pathlib import Path
inventory_path = Path(sys.argv[1])
repo = Path(sys.argv[2])
data = json.loads(inventory_path.read_text(encoding="utf-8"))
src_file = repo / "src/main/java/wsm/graalvm/SemanticMechanismTable.java"
src_text = src_file.read_text(encoding="utf-8")
for e in data["entries"]:
    if "SemanticMechanismTable.java" in e["path"]:
        for method in e["methods"]:
            bare = method
            bare = re.sub(r"<[^>]*>", "", bare)
            bare = re.sub(r"\([^)]*\)", "", bare)
            bare = bare.replace(".<init>", "")
            bare = bare.split(".")[-1].split("$")[-1].strip()
            if method.endswith(".<init>"):
                class_name = method.split(".")[-2] if "." in method else bare
                pattern = rf"\b{re.escape(class_name)}\s*\("
            else:
                pattern = rf"\b{re.escape(bare)}\s*[\(<]"
            if not re.search(pattern, src_text):
                raise SystemExit(f"FAIL-CLOSED: {method} NOT FOUND in source")
        break
print("ERROR: checker should have failed on fabricated method")
sys.exit(1)
PY
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
