#!/usr/bin/env bash
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

PAIR_SRC="src/main/java/wsm/graalvm/Value.java"
WSM_SRC="src/main/java/wsm/graalvm/WsmNode.java"
MARKER="PAIR_IDENTITY_IS_NOT_STRUCTURAL_EQUALITY"

grep -q "$MARKER" "$PAIR_SRC" || {
  echo "pair-identity-invariant RED: missing explicit Pair identity marker $MARKER" >&2
  exit 1
}

PAIR_BLOCK=$(awk '
  /public static final class Pair / {in_pair=1}
  in_pair {print}
  in_pair && /^    }$/ {exit}
' "$PAIR_SRC")

if grep -Eq 'boolean[[:space:]]+equals\(|int[[:space:]]+hashCode\(' <<<"$PAIR_BLOCK"; then
  echo "pair-identity-invariant FAIL: Value.Pair must not override equals/hashCode" >&2
  exit 1
fi

if grep -E 'Pair[^;]*\.equals\(' "$WSM_SRC" >/dev/null; then
  echo "pair-identity-invariant FAIL: pair semantics must use explicit Structural.equals, not Java Pair.equals" >&2
  exit 1
fi

G=${G:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}
CP="classes:third_party/truffle-api.jar:third_party/graalvm-collections.jar:third_party/nativeimage.jar:third_party/polyglot.jar:third_party/truffle-runtime.jar:third_party/truffle-compiler.jar"
TEST_CLASSES="test-classes-pair-identity"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"
"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES" src/test/java/wsm/graalvm/PairIdentityContract.java
java -cp "$TEST_CLASSES:$CP" wsm.graalvm.PairIdentityContract
