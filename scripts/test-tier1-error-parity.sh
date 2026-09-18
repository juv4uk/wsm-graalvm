#!/usr/bin/env bash
set -euo pipefail
REPO=$(cd "$(dirname "$0")/.." && pwd)
JBIN=$(readlink -f "$(command -v java)")
G=$(dirname "$(dirname "$JBIN")")
CORPUS="$REPO/external/my-lisp/tests/fixtures/conformance.lisp"
REGISTRY="$REPO/external/my-lisp/lib/surface/semantic-registry.lisp"

bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/test-classes-error-parity"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/truffle-compiler.jar"

"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES"   "$REPO/src/test/java/wsm/graalvm/Tier1ErrorParityContract.java"

"$G/bin/java" -cp "$TEST_CLASSES:$CP"   -Dtruffle.class.path.append="$REPO/classes"   wsm.graalvm.Tier1ErrorParityContract "$CORPUS" "$REGISTRY"
