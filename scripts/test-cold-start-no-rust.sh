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
  cat > "$POISON/$tool" <<EOF
#!/bin/sh
echo "$tool invoked" >> "$MARKER"
exit 97
EOF
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

GREEN_LINE=$(printf '%s\n' "$OUTPUT" | grep -F "COLD-START-NO-RUST-GREEN" | tail -n 1 || true)
[ -n "$GREEN_LINE" ] || {
  echo "missing cold-start GREEN marker" >&2
  exit 1
}

MISSING_OWNER=$(printf '%s\n' "$GREEN_LINE" | sed -n 's/.* missing-owner=\([^ ]*\).*/\1/p')
ABS_VALUE=$(printf '%s\n' "$GREEN_LINE" | sed -n 's/.* abs=\([^ ]*\) equal=.*/\1/p')
EQUAL_VALUE=$(printf '%s\n' "$GREEN_LINE" | sed -n 's/.* equal=\(.*\) macro-peers=.*/\1/p')
MACRO_PEERS=$(printf '%s\n' "$GREEN_LINE" | sed -n 's/.* macro-peers=\([^ ]*\).*/\1/p')
GRAAL_VERSION=$("$G/bin/java" --version 2>&1 | head -n 1)

[ -n "$MISSING_OWNER" ] && [ -n "$ABS_VALUE" ] && [ -n "$EQUAL_VALUE" ] && [ -n "$MACRO_PEERS" ] || {
  echo "cold-start evidence parse failed: $GREEN_LINE" >&2
  exit 1
}

EVIDENCE_FILE=${COLD_START_EVIDENCE_OUT:-"$REPO/build/cold-start-no-rust.json"}
mkdir -p "$(dirname "$EVIDENCE_FILE")"

python3 - "$EVIDENCE_FILE" "$WSM_COMMIT" "$MY_LISP_PIN" "$GRAAL_VERSION" "$MISSING_OWNER" "$ABS_VALUE" "$EQUAL_VALUE" "$MACRO_PEERS" <<'PY'
import json, sys
from pathlib import Path
out, head, pin, graal, missing_owner, abs_value, equal_value, macro_peers = sys.argv[1:9]
doc = {
  "schema": "wsm-cold-start-evidence/1",
  "gate_id": "cold-start-no-rust",
  "exact_pair": {"wsm_graalvm_head": head, "my_lisp_pin": pin},
  "execution_mode": "jvm",
  "toolchain": {"graalvm": graal},
  "witnesses": {
    "missing_abs_mechanism": {"status": "green", "expected_failure": missing_owner},
    "macro_bootstrap": {"status": "green", "admitted_peer_count": int(macro_peers)},
    "core_bootstrap": {"status": "green"},
    "abs_execution": {"status": "green", "result": abs_value},
    "equal_execution": {"status": "green", "result": equal_value},
    "rust_runtime_unavailable": {"status": "green"},
    "java_subprocess_bridge_absent": {"status": "green"}
  },
  "status": "green"
}
Path(out).write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
PY

echo "COLD-START-RUST-UNAVAILABLE-GREEN wsm=$WSM_COMMIT my-lisp=$MY_LISP_PIN evidence=$EVIDENCE_FILE"
