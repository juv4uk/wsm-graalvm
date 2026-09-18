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
if [ -n "${NATIVE_WSM:-}" ]; then
  MYLISP=${MYLISP:-"$REPO/external/my-lisp"}
  REGISTRY="$MYLISP/lib/surface/semantic-registry.lisp"
  [ -x "$NATIVE_WSM" ] || { echo "missing native WSM executable: $NATIVE_WSM" >&2; exit 1; }

  TMP_OK=$(mktemp --suffix=.lisp)
  TMP_TYPE=$(mktemp --suffix=.lisp)
  trap 'rm -f "$TMP_OK" "$TMP_TYPE"' EXIT
  printf "%s\n" "(cond ((quote (pair-data)) (quote (pair-data)) (quote pair-structural-native-ok)))" > "$TMP_OK"
  "$NATIVE_WSM" "$TMP_OK" "$REGISTRY" "$MYLISP" 2>&1 | tee /tmp/pair-native-structural.log
  grep -q "pair-structural-native-ok" /tmp/pair-native-structural.log

  printf "%s\n" "(eq (quote (pair-data)) (quote (pair-data)))" > "$TMP_TYPE"
  if "$NATIVE_WSM" "$TMP_TYPE" "$REGISTRY" "$MYLISP" > /tmp/pair-native-eq.log 2>&1; then
    echo "pair-identity-invariant FAIL: Native Image 0003 accepted pair operands" >&2
    exit 1
  fi
  cat /tmp/pair-native-eq.log
  grep -q "error-kind=Type" /tmp/pair-native-eq.log
  echo "pair-identity-invariant Native Image PASS"
fi
