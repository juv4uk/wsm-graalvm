#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
NATIVE_WSM=${NATIVE_WSM:-"$REPO/native-wsm"}
MYLISP=${MYLISP:-"$REPO/external/my-lisp"}
REGISTRY="$MYLISP/lib/surface/semantic-registry.lisp"

[ -x "$NATIVE_WSM" ] || { echo "missing native WSM executable: $NATIVE_WSM" >&2; exit 1; }
[ -f "$REGISTRY" ] || { echo "missing pinned registry: $REGISTRY" >&2; exit 1; }

OK=$(mktemp --suffix=.lisp)
TYPE=$(mktemp --suffix=.lisp)
trap 'rm -f "$OK" "$TYPE"' EXIT

# Match the already-merged JVM PairRepresentationContract exactly:
# canonical three-part COND compares query result against expected DATA.
cat > "$OK" <<'LISP'
(cond ((quote (1 2)) (1 2) (quote matched))
      ((quote never-selected) never-selected (quote wrong)))
LISP

"$NATIVE_WSM" "$OK" "$REGISTRY" "$MYLISP" 2>&1 | tee /tmp/pair-native-structural.log
grep -q "result: matched" /tmp/pair-native-structural.log

cat > "$TYPE" <<'LISP'
(eq (quote (1 2)) (quote (1 2)))
LISP

if "$NATIVE_WSM" "$TYPE" "$REGISTRY" "$MYLISP" > /tmp/pair-native-eq.log 2>&1; then
  echo "PAIR-NATIVE-PARITY-FAIL: 0003 accepted Pair operands" >&2
  cat /tmp/pair-native-eq.log >&2
  exit 1
fi
cat /tmp/pair-native-eq.log
grep -q "WsmError: Type: 0003 expects two atoms" /tmp/pair-native-eq.log

echo "PAIR-NATIVE-PARITY-OK"
