#!/usr/bin/env bash
set -euo pipefail
REPO=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

CORPUS="$REPO/external/my-lisp/tests/fixtures/conformance.lisp"
[ -f "$CORPUS" ] || { echo "missing pinned corpus: $CORPUS" >&2; exit 1; }

bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/test-classes-real-inventory"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES"   "$REPO/src/test/java/wsm/graalvm/RealTier1InventoryContract.java"

OUTPUT=$("$G/bin/java" -cp "$TEST_CLASSES:$CP"   wsm.graalvm.RealTier1InventoryContract "$CORPUS")
printf '%s\n' "$OUTPUT"

grep -Fq "REAL-TIER1-INVENTORY-CONTRACT-OK" <<<"$OUTPUT" || {
  echo "missing Tier-1 GREEN marker" >&2
  exit 1
}

SELECTED=$(printf '%s\n' "$OUTPUT" | sed -n 's/.*REAL-TIER1-INVENTORY-CONTRACT-OK selected=\([0-9][0-9]*\).*/\1/p' | tail -n 1)
[ -n "$SELECTED" ] || { echo "cannot parse Tier-1 selected count" >&2; exit 1; }

WSM_COMMIT=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
MY_LISP_PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
GRAAL_VERSION=$("$G/bin/java" --version 2>&1 | head -n 1)

mkdir -p "$REPO/build"
python3 - "$REPO/build/tier1-evidence.json" "$WSM_COMMIT" "$MY_LISP_PIN" "$GRAAL_VERSION" "$SELECTED" <<'PY'
import json, sys
from pathlib import Path

out, head, pin, graal, selected = sys.argv[1:]
doc = {
    "schema": "wsm-tier1-evidence/1",
    "exact_pair": {
        "wsm_graalvm_head": head,
        "my_lisp_pin": pin,
    },
    "gate": {
        "id": "real-tier1-inventory",
        "corpus": "external/my-lisp/tests/fixtures/conformance.lisp",
        "selected": int(selected),
        "execution_mode": "jvm",
        "graalvm": graal,
        "status": "green",
    },
}
Path(out).write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
PY

echo "TIER1-EVIDENCE-GREEN head=$WSM_COMMIT pin=$MY_LISP_PIN selected=$SELECTED"
