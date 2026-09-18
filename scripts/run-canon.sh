#!/usr/bin/env bash
# Vertical acceptance harness: pinned Canon + explicit self-verdict observer.
set -euo pipefail

if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

REPO=$(cd "$(dirname "$0")/.." && pwd)
bash "$REPO/scripts/fetch-third-party.sh"

MYLISP=${MYLISP:-"$REPO/external/my-lisp"}
CANON="$MYLISP/lib/canon.lisp"
REGISTRY="$MYLISP/lib/surface/semantic-registry.lisp"

[ -f "$CANON" ] || { echo "missing pinned canon: $CANON" >&2; exit 1; }
[ -f "$REGISTRY" ] || { echo "missing pinned registry: $REGISTRY" >&2; exit 1; }

TMP=$(mktemp --suffix=.lisp)
trap 'rm -f "$TMP"' EXIT
cat "$CANON" > "$TMP"
printf '\n(canon-conforms?)\n' >> "$TMP"

TC="$G/lib/truffle/truffle-compiler.jar"
[ -f "$TC" ] || TC="$REPO/third_party/truffle-compiler.jar"

"$G/bin/java"   -Dpolyglot.engine.WarnInterpreterOnly=false   -Dwsm.registryPath="$REGISTRY"   -Dtruffle.class.path.append="$REPO/classes"   --enable-native-access=ALL-UNNAMED   -cp "$REPO/classes:$REPO/third_party/graalvm-collections.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$TC"   wsm.graalvm.Main "$TMP" "$REGISTRY" "$MYLISP"
