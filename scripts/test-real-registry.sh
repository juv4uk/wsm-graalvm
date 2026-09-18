#!/usr/bin/env bash
set -euo pipefail

G=${G:?set G to the GraalVM distribution root}
REPO=$(cd "$(dirname "$0")/.." && pwd)
REG="$REPO/external/my-lisp/lib/surface/semantic-registry.lisp"

[ -f "$REG" ] || {
  echo "REAL-REGISTRY-GATE: missing $REG (run scripts/sync-authority.sh)" >&2
  exit 1
}

bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/test-classes-real-registry"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/graalvm-collections.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graal-sdk.jar"

"$G/bin/javac" --release 25 \
  -cp "$CP" \
  -d "$TEST_CLASSES" \
  "$REPO/tests/RealRegistryContract.java"

"$G/bin/java" \
  -cp "$TEST_CLASSES:$CP" \
  wsm.graalvm.RealRegistryContract "$REG"
