#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
G=${G:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}

G="$G" bash "$REPO/scripts/build.sh"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"
TEST_CLASSES="$REPO/test-classes-domain-identity"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

"$G/bin/javac" --release 25 \
  -cp "$CP" \
  -d "$TEST_CLASSES" \
  "$REPO/src/test/java/wsm/graalvm/DomainIdentityContract.java" \
  "$REPO/src/test/java/wsm/graalvm/DomainIdentityMechanismContract.java" \
  "$REPO/src/test/java/wsm/graalvm/LegacySid8ProjectionContract.java"

for test in \
  wsm.graalvm.DomainIdentityContract \
  wsm.graalvm.DomainIdentityMechanismContract \
  wsm.graalvm.LegacySid8ProjectionContract
do
  "$G/bin/java" \
    -Dpolyglot.engine.WarnInterpreterOnly=false \
    -Dtruffle.class.path.append="$REPO/classes" \
    -cp "$TEST_CLASSES:$CP" \
    "$test"
done
