#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

bash "$REPO/scripts/sync-authority.sh"
bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/test-classes-self-tail-loop"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES"   "$REPO/src/test/java/wsm/graalvm/DeepRecursionProbe.java"   "$REPO/src/test/java/wsm/graalvm/SelfTailLoopContract.java"

tail_output=$("$G/bin/java" -cp "$TEST_CLASSES:$CP"   wsm.graalvm.DeepRecursionProbe "$REPO" tail 16384)
printf '%s\n' "$tail_output"
grep -Fq 'mode=tail depth=16384 outcome=value' <<<"$tail_output"

control_output=$("$G/bin/java" -cp "$TEST_CLASSES:$CP"   wsm.graalvm.DeepRecursionProbe "$REPO" non-tail 256)
printf '%s\n' "$control_output"
grep -Fq 'mode=non-tail depth=256 outcome=value' <<<"$control_output"
grep -Fq 'value-kind=(structural-kind pair)' <<<"$control_output"

"$G/bin/java" -cp "$TEST_CLASSES:$CP"   wsm.graalvm.SelfTailLoopContract "$REPO"

echo "SELF-TAIL-LOOP-PROTOTYPE-GREEN"
