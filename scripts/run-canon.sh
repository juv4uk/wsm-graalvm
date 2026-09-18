#!/usr/bin/env bash
# Vertical M0 acceptance: evaluate (canon-conforms?) on the real Canon file,
# consuming the real numeric surface registry. No spellings hardcoded here.
set -euo pipefail
G=${G:?set G=graalvm-community path}
REPO=$(cd "$(dirname "$0")/.." && pwd)
MYLISP=${MYLISP:-$REPO/../my-lisp}
"$G/bin/java" -Dpolyglot.engine.WarnInterpreterOnly=false \
  -Dwsm.registryPath="$MYLISP/lib/surface/semantic-registry.lisp" \
  -cp "$REPO/classes:$REPO/third_party/graalvm-collections.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graal-sdk.jar" \
  wsm.graalvm.Main "$MYLISP/lib/canon.lisp" "$MYLISP/lib/surface/semantic-registry.lisp" "$MYLISP"
