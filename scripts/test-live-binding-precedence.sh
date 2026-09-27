#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

bash "$REPO/scripts/sync-authority.sh"
bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/test-classes-live-binding-precedence"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES"   "$REPO/src/test/java/wsm/graalvm/LiveBindingPrecedenceContract.java"

WSM_COMMIT=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
MY_LISP_PIN=$(git -C "$REPO" ls-files -s external/sens | awk '$1 == "160000" {print $2}')

"$G/bin/java" -cp "$TEST_CLASSES:$CP" \
  wsm.graalvm.LiveBindingPrecedenceContract "$REPO"

# Write machine-readable evidence record
EVIDENCE_FILE="$REPO/build/live-binding-precedence.json"
mkdir -p "$(dirname "$EVIDENCE_FILE")"
python3 - "$EVIDENCE_FILE" "$WSM_COMMIT" "$MY_LISP_PIN" <<'PY'
import json
import sys
from pathlib import Path

evidence_file = sys.argv[1]
wsm_commit = sys.argv[2]
my_lisp_pin = sys.argv[3]

doc = {
    "schema": "wsm-live-binding-precedence-evidence/1",
    "exact_pair": {
        "wsm_graalvm_head": wsm_commit,
        "my_lisp_pin": my_lisp_pin,
    },
    "witnesses": {
        "global_live_binding": {
            "status": "green",
            "claim": "Ordinary Lisp definition installs callable in global environment"
        },
        "lexical_shadow": {
            "status": "green",
            "claim": "Lexical binding shadows global/registry route for same name"
        },
        "self_recursion": {
            "status": "green",
            "claim": "Recursive top-level definition resolves through live binding"
        },
        "no_java_mechanism": {
            "status": "green",
            "claim": "Lisp-owned identity has no Java substrate mechanism"
        }
    }
}

Path(evidence_file).write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
PY
