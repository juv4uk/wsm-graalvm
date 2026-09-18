#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
: "${G:=}"

bash "$REPO/scripts/build.sh"

CP="$REPO/classes"
case "$(uname -s 2>/dev/null || true)" in
  MINGW*|MSYS*|CYGWIN*) PATH_SEP=";" ;;
  *) PATH_SEP=":" ;;
esac
MODULE_PATH="$REPO/third_party/truffle-api.jar${PATH_SEP}$REPO/third_party/truffle-runtime.jar${PATH_SEP}$REPO/third_party/truffle-compiler.jar${PATH_SEP}$REPO/third_party/polyglot.jar${PATH_SEP}$REPO/third_party/collections.jar${PATH_SEP}$REPO/third_party/jniutils.jar${PATH_SEP}$REPO/third_party/nativeimage.jar${PATH_SEP}$REPO/third_party/word.jar"

rm -f "$REPO/native-wsm" "$REPO/native-wsm.exe"
"$G/bin/native-image"   --module-path "$MODULE_PATH"   --no-fallback   --initialize-at-build-time=wsm.graalvm.providers.WsmLanguageProvider   -H:IncludeResources='META-INF/services/com[.]oracle[.]truffle[.]api[.]provider[.]TruffleLanguageProvider'   -cp "$CP"   wsm.graalvm.Main   "$REPO/native-wsm"

MYLISP=${MYLISP:-$REPO/external/my-lisp}
CANON="$MYLISP/lib/canon.lisp"
REGISTRY="$MYLISP/lib/surface/semantic-registry.lisp"

[ -f "$CANON" ] || { echo "missing pinned canon: $CANON" >&2; exit 1; }
[ -f "$REGISTRY" ] || { echo "missing pinned registry: $REGISTRY" >&2; exit 1; }

# Match the JVM Canon acceptance path exactly: loading canon.lisp only defines
# the witness and naturally returns the last closure. The acceptance program
# must explicitly invoke the Lisp-owned observer.
TMP=$(mktemp --suffix=.lisp)
trap 'rm -f "$TMP"' EXIT
cat "$CANON" > "$TMP"
printf '\n(canon-conforms?)\n' >> "$TMP"

"$REPO/native-wsm"   "$TMP"   "$REGISTRY"   "$MYLISP" 2>&1 | tee "$REPO/native-canon.log"

grep -q "(canon-conformance satisfied)" "$REPO/native-canon.log"
echo "NATIVE-IMAGE-CANON-OK"
