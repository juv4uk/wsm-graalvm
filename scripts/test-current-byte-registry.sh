#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
G=${G:-${JAVA_HOME:-}}
if [ -z "$G" ]; then
  echo "test-current-byte-registry: set G or JAVA_HOME to GraalVM/JDK 25" >&2
  exit 2
fi

bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/build/test-current-byte-registry"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES" \
  "$REPO/src/test/java/wsm/graalvm/CurrentByteRegistryContract.java"

"$G/bin/java" -cp "$TEST_CLASSES:$CP" \
  wsm.graalvm.CurrentByteRegistryContract
