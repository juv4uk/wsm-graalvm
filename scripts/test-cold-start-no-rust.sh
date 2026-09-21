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
#!/usr/bin/env bash
echo "$tool invoked" >> "$MARKER"
exit 97
EOF
  chmod +x "$POISON/$tool"
done

# Runtime execution itself gets no normal PATH at all. Java is invoked by
# absolute path; any accidental Rust/CLI subprocess lookup can only hit the
# poison shims and will make the witness fail.
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

echo "COLD-START-RUST-UNAVAILABLE-GREEN wsm=$WSM_COMMIT my-lisp=$MY_LISP_PIN"
