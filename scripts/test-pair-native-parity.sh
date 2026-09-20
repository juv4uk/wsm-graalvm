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
STRUCT_LOG=$(mktemp)
EQ_LOG=$(mktemp)
trap 'rm -f "$OK" "$TYPE" "$STRUCT_LOG" "$EQ_LOG"' EXIT

cat > "$OK" <<'LISP'
(cond ((quote (1 2)) (1 2) (quote matched))
      ((quote never-selected) never-selected (quote wrong)))
LISP

"$NATIVE_WSM" "$OK" "$REGISTRY" "$MYLISP" 2>&1 | tee "$STRUCT_LOG"
grep -q "result: matched" "$STRUCT_LOG"

cat > "$TYPE" <<'LISP'
(eq (quote (1 2)) (quote (1 2)))
LISP

if "$NATIVE_WSM" "$TYPE" "$REGISTRY" "$MYLISP" >"$EQ_LOG" 2>&1; then
  echo "PAIR-NATIVE-PARITY-FAIL: 0003 accepted Pair operands" >&2
  cat "$EQ_LOG" >&2
  exit 1
fi
cat "$EQ_LOG"
grep -q "error-kind=Type: 0003 expects two atoms" "$EQ_LOG"

echo "PAIR-NATIVE-PARITY-OK"
