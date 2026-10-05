#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
G=${G:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}

G="$G" bash "$REPO/scripts/build.sh"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"
TEST_CLASSES="$REPO/test-classes-current-canonical"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

"$G/bin/javac" --release 25 \
  -cp "$CP" \
  -d "$TEST_CLASSES" \
  "$REPO/src/test/java/wsm/graalvm/CurrentCanonicalSourceContract.java"

"$G/bin/java" \
  -Dpolyglot.engine.WarnInterpreterOnly=false \
  -Dtruffle.class.path.append="$REPO/classes" \
  -cp "$TEST_CLASSES:$CP" \
  wsm.graalvm.CurrentCanonicalSourceContract
