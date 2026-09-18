#!/usr/bin/env bash
# M1 lexical-frame witnesses. Runs the real Truffle reader/compiler/runtime.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MYLISP=${MYLISP:-"$REPO/external/my-lisp"}
REGISTRY="$MYLISP/lib/surface/semantic-registry.lisp"

[ -f "$REGISTRY" ] || {
  echo "missing pinned registry: $REGISTRY" >&2
  exit 1
}

[ -d "$REPO/classes" ] || {
  echo "missing classes; run scripts/build.sh first" >&2
  exit 1
}

TMP=$(mktemp --suffix=.lisp)
OUT=$(mktemp)
trap 'rm -f "$TMP" "$OUT"' EXIT

cat >"$TMP" <<'EOF'
((lambda (x)
   ((lambda (y) x) 7))
 42)

((lambda (x)
   ((lambda (x) x) 7))
 42)

((lambda (x)
   ((lambda (y)
      ((lambda (z) x) 9))
    8))
 42)
EOF

if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

TC="$G/lib/truffle/truffle-compiler.jar"
[ -f "$TC" ] || TC="$REPO/third_party/truffle-compiler.jar"

"$G/bin/java" \
  -Dpolyglot.engine.WarnInterpreterOnly=false \
  -Dwsm.registryPath="$REGISTRY" \
  -Dtruffle.class.path.append="$REPO/classes" \
  --enable-native-access=ALL-UNNAMED \
  -cp "$REPO/classes:$REPO/third_party/graalvm-collections.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$TC" \
  wsm.graalvm.Main "$TMP" "$REGISTRY" "$MYLISP" | tee "$OUT"

# The current M0 launcher reports only the final form. Re-run the exact three
# expressions individually so each witness has an observable result.
expected=(42 7 42)
for i in 0 1 2; do
  form=$(case "$i" in 0) sed -n '1,3p' "$TMP" ;; 1) sed -n '5,7p' "$TMP" ;; 2) sed -n '9,13p' "$TMP" ;; esac)
  one=$(mktemp --suffix=.lisp)
  trap 'rm -f "$TMP" "$OUT" "$one"' EXIT
  printf '%s\n' "$form" >"$one"
  result=$(
    "$G/bin/java" \
      -Dpolyglot.engine.WarnInterpreterOnly=false \
      -Dwsm.registryPath="$REGISTRY" \
      -Dtruffle.class.path.append="$REPO/classes" \
      --enable-native-access=ALL-UNNAMED \
      -cp "$REPO/classes:$REPO/third_party/graalvm-collections.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$TC" \
      wsm.graalvm.Main "$one" "$REGISTRY" "$MYLISP" 2>&1 |
      awk -F'result: ' '/\[wsm-graalvm M0\] result:/{print $2}' | tail -1
  )
  [ "$result" = "${expected[$i]}" ] || {
    echo "LEXICAL-FRAME-FAIL witness=$i expected=${expected[$i]} got=$result" >&2
    exit 1
  }
  rm -f "$one"
done

echo "LEXICAL-FRAME-GREEN"
