#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
: "${G:=}"

if [ -z "$G" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

bash "$REPO/scripts/sync-authority.sh"
bash "$REPO/scripts/check-no-rust-runtime-bridge.sh"
G="$G" bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/test-classes-cold-start-no-rust"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES"   "$REPO/src/test/java/wsm/graalvm/ColdStartNoRustContract.java"

WSM_COMMIT=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
MY_LISP_PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
MY_LISP_HEAD=$(git -C "$REPO/external/my-lisp" rev-parse HEAD)

[ -n "$MY_LISP_PIN" ] || { echo "cannot read my-lisp gitlink pin" >&2; exit 1; }
[ "$MY_LISP_PIN" = "$MY_LISP_HEAD" ] || {
  echo "my-lisp pin/head mismatch: $MY_LISP_PIN != $MY_LISP_HEAD" >&2
  exit 1
}

POISON=$(mktemp -d)
MARKER=$(mktemp)
rm -f "$MARKER"
trap 'rm -rf "$POISON" "$MARKER"' EXIT

for tool in cargo rustc my-lisp my-lisp-cli; do
  cat > "$POISON/$tool" <<EOSH
#!/usr/bin/env bash
echo "$tool invoked" >> "$MARKER"
exit 97
EOSH
  chmod +x "$POISON/$tool"
done

set +e
OUTPUT=$(
  env -i     PATH="$POISON"     HOME="${HOME:-/tmp}"     JAVA_HOME="$G"     WSM_COLD_START_NO_RUST=1     "$G/bin/java"       -cp "$TEST_CLASSES:$CP"       wsm.graalvm.ColdStartNoRustContract       "$REPO" "$WSM_COMMIT" "$MY_LISP_PIN" 2>&1
)
STATUS=$?
set -e

printf '%s\n' "$OUTPUT"

[ "$STATUS" -eq 0 ] || {
  echo "cold-start witness failed with status $STATUS" >&2
  exit "$STATUS"
}

[ ! -e "$MARKER" ] || {
  echo "Rust/CLI poison shim was invoked:" >&2
  cat "$MARKER" >&2
  exit 1
}

grep -Fq "COLD-START-NO-RUST-GREEN" <<<"$OUTPUT" || {
  echo "missing cold-start GREEN marker" >&2
  exit 1
}

# Extract structured data from output for machine-readable evidence
MISSING_OWNER=$(echo "$OUTPUT" | sed -n 's/.*missing-owner=\([^ ]*\).*/\1/p')
ABS_VALUE=$(echo "$OUTPUT" | sed -n 's/.*abs=\([^ ]*\).*/\1/p')
EQUAL_VALUE=$(echo "$OUTPUT" | sed -n 's/.*equal=\([^ ]*\).*/\1/p')
MACRO_PEERS=$(echo "$OUTPUT" | sed -n 's/.*macro-peers=\([^ ]*\).*/\1/p')

# Write machine-readable evidence
EVIDENCE_FILE="$REPO/build/cold-start-no-rust.json"
mkdir -p "$(dirname "$EVIDENCE_FILE")"
python3 - "$EVIDENCE_FILE" "$WSM_COMMIT" "$MY_LISP_PIN" "$MISSING_OWNER" "$ABS_VALUE" "$EQUAL_VALUE" "$MACRO_PEERS" <<'PY'
import json
import sys
from pathlib import Path

evidence_file = sys.argv[1]
wsm_commit = sys.argv[2]
my_lisp_pin = sys.argv[3]
missing_owner = sys.argv[4]
abs_value = sys.argv[5]
equal_value = sys.argv[6]
macro_peers = int(sys.argv[7])

doc = {
    "schema": "wsm-cold-start-evidence/1",
    "exact_pair": {
        "wsm_graalvm_head": wsm_commit,
        "my_lisp_pin": my_lisp_pin,
    },
    "witnesses": {
        "missing_abs_mechanism": {
            "status": "green",
            "expected_failure": missing_owner,
            "claim": "Lisp-owned abs has no Java mechanism before bootstrap"
        },
        "canon_bootstrap": {
            "status": "green",
            "claim": "Canon closure loads and executes successfully"
        },
        "macro_bootstrap": {
            "status": "green",
            "admitted_peer_count": macro_peers,
            "claim": "lib/macro.lisp returns MacroValue with admitted peers"
        },
        "core_bootstrap": {
            "status": "green",
            "claim": "lib/core.lisp executes and establishes Lisp-owned bindings"
        },
        "abs_execution": {
            "status": "green",
            "result": abs_value,
            "claim": "Lisp-owned abs executes via live binding after cold start"
        },
        "equal_execution": {
            "status": "green",
            "result": equal_value,
            "claim": "Lisp-owned equal? executes via live binding after cold start"
        },
        "no_java_abs_mechanism_post_bootstrap": {
            "status": "green",
            "claim": "Cold start does not manufacture Java abs mechanism"
        },
        "no_java_equal_mechanism_post_bootstrap": {
            "status": "green",
            "claim": "Lisp-owned equal? still has no Java mechanism"
        }
    }
}

Path(evidence_file).write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
PY

echo "COLD-START-RUST-UNAVAILABLE-GREEN wsm=$WSM_COMMIT my-lisp=$MY_LISP_PIN"
