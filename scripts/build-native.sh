#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
: "${G:=}"

bash "$REPO/scripts/build.sh"

CP="$REPO/classes"
MODULE_PATH="$REPO/third_party/truffle-api.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/truffle-compiler.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/collections.jar:$REPO/third_party/jniutils.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/word.jar"

rm -f "$REPO/native-wsm"

"$G/bin/native-image"   --module-path "$MODULE_PATH"   --no-fallback   --initialize-at-build-time=wsm.graalvm.providers.WsmLanguageProvider   -H:IncludeResources='META-INF/services/com[.]oracle[.]truffle[.]api[.]provider[.]TruffleLanguageProvider'   -cp "$CP"   wsm.graalvm.Main   "$REPO/native-wsm"

MYLISP=${MYLISP:-$REPO/external/my-lisp}
"$REPO/native-wsm"   "$MYLISP/lib/canon.lisp"   "$MYLISP/lib/surface/semantic-registry.lisp"   "$REPO" 2>&1 | tee "$REPO/native-canon.log"

grep -q "(canon-conformance satisfied)" "$REPO/native-canon.log"
echo "NATIVE-IMAGE-CANON-OK"
