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

[ "$STATUS" -eq 0 ] || { echo "cold-start witness failed with status $STATUS" >&2; exit "$STATUS"; }
[ ! -e "$MARKER" ] || {
  echo "Rust/CLI poison shim was invoked:" >&2
  cat "$MARKER" >&2
  exit 1
}
grep -Fq "COLD-START-NO-RUST-GREEN" <<<"$OUTPUT" || {
  echo "missing cold-start GREEN marker" >&2
  exit 1
}

MISSING_OWNER=$(printf '%s\n' "$OUTPUT" | sed -n 's/.*missing-owner=\([^ ]*\).*/\1/p' | tail -n 1)
ABS_VALUE=$(printf '%s\n' "$OUTPUT" | sed -n 's/.*abs=\([^ ]*\).*/\1/p' | tail -n 1)
EQUAL_VALUE=$(printf '%s\n' "$OUTPUT" | sed -n 's/.*equal=\(.*\) macro-peers=.*/\1/p' | tail -n 1)
MACRO_PEERS=$(printf '%s\n' "$OUTPUT" | sed -n 's/.*macro-peers=\([^ ]*\).*/\1/p' | tail -n 1)
GRAAL_VERSION=$("$G/bin/java" --version 2>&1 | head -n 1)

mkdir -p "$REPO/build"
python3 - "$REPO/build/cold-start-no-rust.json" "$WSM_COMMIT" "$MY_LISP_PIN" "$GRAAL_VERSION" "$MISSING_OWNER" "$ABS_VALUE" "$EQUAL_VALUE" "$MACRO_PEERS" <<'PY'
import json, sys
from pathlib import Path

out, head, pin, graal, missing, abs_value, equal_value, peers = sys.argv[1:]
doc = {
    "schema": "wsm-cold-start-evidence/1",
    "exact_pair": {
        "wsm_graalvm_head": head,
        "my_lisp_pin": pin,
    },
    "gate": {
        "id": "cold-start-no-rust",
        "execution_mode": "jvm",
        "graalvm": graal,
        "rust_runtime_available": False,
        "status": "green",
    },
    "observations": {
        "missing_owner": missing,
        "abs": abs_value,
        "equal": equal_value,
        "macro_peers": int(peers),
    },
}
Path(out).write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
PY

echo "COLD-START-RUST-UNAVAILABLE-GREEN wsm=$WSM_COMMIT my-lisp=$MY_LISP_PIN"
