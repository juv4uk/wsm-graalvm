#!/usr/bin/env bash
set -euo pipefail
REPO=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

REGISTRY="$REPO/external/my-lisp/lib/surface/semantic-registry.lisp"
[ -f "$REGISTRY" ] || {
  echo "missing pinned registry: $REGISTRY" >&2
  exit 1
}

bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/test-classes-frames"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES"   "$REPO/src/test/java/wsm/graalvm/TruffleFrameContract.java"

"$G/bin/java" -cp "$TEST_CLASSES:$CP"   wsm.graalvm.TruffleFrameContract "$REGISTRY"
