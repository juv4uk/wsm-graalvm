#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

bash "$REPO/scripts/sync-authority.sh"
bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/test-classes-deep-recursion"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES"   "$REPO/src/test/java/wsm/graalvm/DeepRecursionProbe.java"

depths=(64 256 1024 4096 16384)
for mode in tail non-tail; do
  for depth in "${depths[@]}"; do
    "$G/bin/java" -cp "$TEST_CLASSES:$CP"       wsm.graalvm.DeepRecursionProbe "$REPO" "$mode" "$depth"
  done
done
