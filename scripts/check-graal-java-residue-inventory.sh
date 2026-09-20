#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
INVENTORY="$REPO/refs/graal-java-residue-inventory.json"

fail() { echo "graal-java-residue-inventory FAIL-CLOSED: $*" >&2; exit 1; }

[ -s "$INVENTORY" ] || fail "missing inventory: $INVENTORY"

python3 - "$INVENTORY" "$REPO" <<'PY'
import json, sys
from pathlib import Path

inventory_path = Path(sys.argv[1])
repo = Path(sys.argv[2])
data = json.loads(inventory_path.read_text(encoding="utf-8"))

required = {
    "src/main/java/wsm/graalvm/SemanticMechanismTable.java",
    "src/main/java/wsm/graalvm/Compiler.java",
    "src/main/java/wsm/graalvm/WsmNode.java",
    "src/main/java/wsm/graalvm/Value.java",
    "src/main/java/wsm/graalvm/RegistryPeerInstaller.java",
    "src/main/java/wsm/graalvm/SemanticResolver.java",
    "src/main/java/wsm/graalvm/BootstrapClosureLoader.java",
    "src/main/java/wsm/graalvm/BootstrapRuntime.java",
}

entries = data.get("entries")
if not isinstance(entries, list):
    raise SystemExit("entries must be a list")

paths = {e.get("path") for e in entries if isinstance(e, dict)}
missing = sorted(required - paths)
if missing:
    raise SystemExit("missing mandatory inventory paths: " + ", ".join(missing))

allowed = set(data.get("classifications", []))
for e in entries:
    if not isinstance(e, dict):
        raise SystemExit("inventory entry is not an object")
    for key in ("path", "class", "classification", "owner", "methods", "witness", "note"):
        if key not in e:
            raise SystemExit(f"{e.get('path','<unknown>')}: missing field {key}")
    if e["classification"] not in allowed:
        raise SystemExit(f"{e['path']}: unknown classification {e['classification']}")
    if not e["methods"]:
        raise SystemExit(f"{e['path']}: empty method inventory")
    witness = repo / e["witness"]
    if not witness.is_file():
        raise SystemExit(f"{e['path']}: missing witness {e['witness']}")
    if not e["owner"]:
        raise SystemExit(f"{e['path']}: empty owner")

temp = [e["path"] for e in entries if e["classification"] == "temporary-bootstrap"]
for e in entries:
    if e["classification"] == "temporary-bootstrap" and not e.get("retirement"):
        raise SystemExit(f"{e['path']}: temporary-bootstrap entry lacks retirement target")

print("GRAAL-JAVA-RESIDUE-INVENTORY-GREEN")
print(f"  entries={len(entries)}")
print(f"  temporary-bootstrap={len(temp)}")
print("  unclassified=0")
PY
