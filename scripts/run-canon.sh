#!/usr/bin/env bash
# Vertical M0 acceptance: evaluate (canon-conforms?) on the real Canon file,
# consuming the real numeric surface registry. No spellings hardcoded here.
set -euo pipefail
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi
REPO=$(cd "$(dirname "$0")/.." && pwd)
bash "$REPO/scripts/fetch-third-party.sh"
MYLISP=$REPO  # authority files reachable via the same-structure symlinks at repo root
if [ -f "$REPO/lib/canon.lisp" ]; then
  MYLISP=${MYLISP:-$REPO/../my-lisp}
fi  # or local submodule $REPO/external/my-lisp
"$G/bin/java" -Dpolyglot.engine.WarnInterpreterOnly=false \
  -Dwsm.registryPath="$MYLISP/lib/surface/semantic-registry.lisp" \
  --enable-native-access=ALL-UNNAMED -Dpolyglot.engine.WarnInterpreterOnly=false \
  -cp "$REPO/classes:$REPO/third_party/graalvm-collections.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/truffle-compiler.jar" \
  wsm.graalvm.Main "$MYLISP/lib/canon.lisp" "$MYLISP/lib/surface/semantic-registry.lisp" "$MYLISP"
