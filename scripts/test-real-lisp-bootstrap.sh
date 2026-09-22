#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

bash "$REPO/scripts/sync-authority.sh"
bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/test-classes-real-lisp-bootstrap"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES"   "$REPO/src/test/java/wsm/graalvm/RealLispBootstrapContract.java"

OUTPUT=$("$G/bin/java" -cp "$TEST_CLASSES:$CP"   wsm.graalvm.RealLispBootstrapContract "$REPO")
printf '%s\n' "$OUTPUT"

grep -Fq "REAL-LISP-BOOTSTRAP-GREEN" <<<"$OUTPUT" || {
  echo "missing real Lisp bootstrap GREEN marker" >&2
  exit 1
}

WSM_COMMIT=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
MY_LISP_PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
GRAAL_VERSION=$("$G/bin/java" --version 2>&1 | head -n 1)
PEERS=$(printf '%s\n' "$OUTPUT" | sed -n 's/.*REAL-LISP-BOOTSTRAP-GREEN peers=\([0-9][0-9]*\).*/\1/p' | tail -n 1)
[ -n "$PEERS" ] || { echo "cannot parse bootstrap peer count" >&2; exit 1; }

mkdir -p "$REPO/build"
python3 - "$REPO/build/bootstrap-evidence.json" "$WSM_COMMIT" "$MY_LISP_PIN" "$GRAAL_VERSION" "$PEERS" <<'PY'
import json, sys
from pathlib import Path

out, head, pin, graal, peers = sys.argv[1:]
doc = {
    "schema": "wsm-bootstrap-evidence/1",
    "exact_pair": {
        "wsm_graalvm_head": head,
        "my_lisp_pin": pin,
    },
    "gate": {
        "id": "real-lisp-bootstrap",
        "closure": [
            "lib/surface/semantic-registry.lisp",
            "lib/canon.lisp",
            "lib/macro.lisp",
            "lib/core.lisp",
        ],
        "execution_mode": "jvm",
        "graalvm": graal,
        "macro_peer_count": int(peers),
        "status": "green",
    },
}
Path(out).write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
PY

echo "BOOTSTRAP-EVIDENCE-GREEN head=$WSM_COMMIT pin=$MY_LISP_PIN peers=$PEERS"
