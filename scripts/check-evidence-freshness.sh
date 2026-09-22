#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
ROOT=${EVIDENCE_ROOT:-"$REPO/build"}
EXPECTED_HEAD=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
GITLINK_PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
ACTUAL_PIN=$(git -C "$REPO/external/my-lisp" rev-parse HEAD)
MANIFEST="$REPO/refs/lisp-dependency-manifest.lisp"
MANIFEST_PIN=$(sed -n 's/^[[:space:]]*(pin[[:space:]]\+\.[[:space:]]*"\([0-9a-f]\{40\}\)")[[:space:]]*$/\1/p' "$MANIFEST" | head -1)

fail() { echo "EVIDENCE-FRESHNESS FAIL-CLOSED: $*" >&2; exit 1; }

[[ "$EXPECTED_HEAD" =~ ^[0-9a-f]{40}$ ]] || fail "expected WSM head is not exact 40-hex: $EXPECTED_HEAD"
[[ "$GITLINK_PIN" =~ ^[0-9a-f]{40}$ ]] || fail "cannot read exact external/my-lisp gitlink pin"
[[ "$MANIFEST_PIN" =~ ^[0-9a-f]{40}$ ]] || fail "cannot parse exact manifest pin"

[ "$GITLINK_PIN" = "$ACTUAL_PIN" ] || fail "gitlink/submodule mismatch: $GITLINK_PIN != $ACTUAL_PIN"
[ "$GITLINK_PIN" = "$MANIFEST_PIN" ] || fail "gitlink/manifest mismatch: $GITLINK_PIN != $MANIFEST_PIN"

if [ -n "${EVIDENCE_FILES:-}" ]; then
  read -r -a FILES <<<"$EVIDENCE_FILES"
else
  FILES=(
    substrate-switch-proof.json
    substrate-parity-smoke.json
    cold-start-no-rust.json
    tier1-evidence.json
    real-lisp-bootstrap-evidence.json
  )
fi

python3 - "$ROOT" "$EXPECTED_HEAD" "$GITLINK_PIN" "${FILES[@]}" <<'PY'
import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
expected_head = sys.argv[2]
expected_pin = sys.argv[3]
files = sys.argv[4:]
hex40 = re.compile(r"^[0-9a-f]{40}$")

if not files:
    raise SystemExit("EVIDENCE-FRESHNESS FAIL-CLOSED: no evidence files requested")

def fail(msg: str) -> None:
    raise SystemExit("EVIDENCE-FRESHNESS FAIL-CLOSED: " + msg)

def exact_pair(doc, name):
    pair = doc.get("exact_pair")
    if isinstance(pair, dict):
        head = pair.get("wsm_graalvm_head") or pair.get("wsm_commit")
        pin = pair.get("my_lisp_pin") or pair.get("upstream_pin")
        if head and pin:
            return head, pin

    rows = doc.get("rows")
    if isinstance(rows, list) and rows:
        coords = {(r.get("wsm_commit"), r.get("upstream_pin")) for r in rows if isinstance(r, dict)}
        if len(coords) != 1:
            fail(f"{name}: parity rows do not share one exact pair")
        head, pin = next(iter(coords))
        if head and pin:
            return head, pin

    fail(f"{name}: cannot locate exact pair")

for filename in files:
    path = root / filename
    if not path.is_file():
        fail(f"missing evidence: {path}")
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"{filename}: invalid JSON: {exc}")

    head, pin = exact_pair(doc, filename)
    if not (isinstance(head, str) and hex40.fullmatch(head)):
        fail(f"{filename}: WSM coordinate is not exact 40-hex: {head!r}")
    if not (isinstance(pin, str) and hex40.fullmatch(pin)):
        fail(f"{filename}: my-lisp coordinate is not exact 40-hex: {pin!r}")
    if head != expected_head or pin != expected_pin:
        fail(
            f"{filename}: stale pair recorded=({head},{pin}) "
            f"current=({expected_head},{expected_pin})"
        )

    # If an execution mode uses Native Image, version provenance is mandatory.
    mode = doc.get("execution_mode")
    toolchain = doc.get("toolchain") if isinstance(doc.get("toolchain"), dict) else {}
    if mode == "native-image" and not toolchain.get("native_image"):
        fail(f"{filename}: native-image evidence lacks native_image version")

    # JVM-facing machine-readable records must identify the Graal/Java runtime.
    if mode in {"jvm", "rust-reference+jvm", "jvm-proof-chain"}:
        graal = toolchain.get("graalvm") or doc.get("exact_pair", {}).get("graalvm")
        if not graal:
            fail(f"{filename}: JVM evidence lacks GraalVM version provenance")

    print(f"FRESH {filename} head={head} pin={pin}")

print(
    "EVIDENCE-FRESHNESS-GREEN"
    f" head={expected_head} pin={expected_pin} files={len(files)}"
)
PY
