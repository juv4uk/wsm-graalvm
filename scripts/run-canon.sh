#!/usr/bin/env bash
# Vertical acceptance: evaluate the real pinned Canon through the Graal substrate.
set -euo pipefail

if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

REPO=$(cd "$(dirname "$0")/.." && pwd)
MYLISP=${MYLISP:-"$REPO/external/my-lisp"}

[ -f "$MYLISP/lib/canon.lisp" ] || {
  echo "missing $MYLISP/lib/canon.lisp; run scripts/sync-authority.sh" >&2
  exit 1
}
[ -f "$MYLISP/lib/surface/semantic-registry.lisp" ] || {
  echo "missing semantic registry under $MYLISP" >&2
  exit 1
}

CP="$REPO/classes:$REPO/third_party/graalvm-collections.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/jniutils.jar:$REPO/third_party/nativebridge.jar:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/truffle-compiler.jar"

"$G/bin/java" \
  -Dpolyglot.engine.WarnInterpreterOnly=false \
  -Dwsm.registryPath="$MYLISP/lib/surface/semantic-registry.lisp" \
  --enable-native-access=ALL-UNNAMED \
  -cp "$CP" \
  wsm.graalvm.Main \
  "$MYLISP/lib/canon.lisp" \
  "$MYLISP/lib/surface/semantic-registry.lisp" \
  "$MYLISP"
