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

set +e
OUTPUT=$("$G/bin/java" -cp "$TEST_CLASSES:$CP"   wsm.graalvm.RealLispBootstrapContract "$REPO" 2>&1)
STATUS=$?
set -e
printf '%s\n' "$OUTPUT"
[ "$STATUS" -eq 0 ] || exit "$STATUS"
grep -Fq "REAL-LISP-BOOTSTRAP-GREEN" <<<"$OUTPUT" || {
  echo "REAL-LISP-BOOTSTRAP FAIL: missing GREEN marker" >&2
  exit 1
}

HEAD=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
GRAAL_VERSION=$("$G/bin/java" --version 2>&1 | head -n 1)
EVIDENCE_OUT=${BOOTSTRAP_EVIDENCE_OUT:-"$REPO/build/real-lisp-bootstrap-evidence.json"}
mkdir -p "$(dirname "$EVIDENCE_OUT")"

python3 - "$EVIDENCE_OUT" "$HEAD" "$PIN" "$GRAAL_VERSION" <<'PY'
import json, sys
from pathlib import Path
out, head, pin, graal = sys.argv[1:5]
doc = {
  "schema": "wsm-real-lisp-bootstrap-evidence/1",
  "gate_id": "real-lisp-bootstrap",
  "exact_pair": {"wsm_graalvm_head": head, "my_lisp_pin": pin},
  "execution_mode": "jvm",
  "toolchain": {"graalvm": graal},
  "source_closure": [
    "external/my-lisp/lib/surface/semantic-registry.lisp",
    "external/my-lisp/lib/canon.lisp",
    "external/my-lisp/lib/macro.lisp",
    "external/my-lisp/lib/core.lisp"
  ],
  "status": "green"
}
Path(out).write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
PY

echo "REAL-LISP-BOOTSTRAP-EVIDENCE-GREEN head=$HEAD pin=$PIN evidence=$EVIDENCE_OUT"
