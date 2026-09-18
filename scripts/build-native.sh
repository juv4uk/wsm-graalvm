#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
: "${G:=}"

if [[ "${RUNNER_OS:-}" == "Windows" || "$(uname -s 2>/dev/null || true)" =~ ^(MINGW|MSYS|CYGWIN) ]]; then
  CP_SEP=';'
  BIN_SUFFIX='.exe'
else
  CP_SEP=':'
  BIN_SUFFIX=''
fi

if [ -z "${G:-}" ]; then
  G="${GRAALVM_HOME:-}"
fi
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

bash "$REPO/scripts/build.sh"

CP="$REPO/classes"
MODULE_PATH="$REPO/third_party/truffle-api.jar${CP_SEP}$REPO/third_party/truffle-runtime.jar${CP_SEP}$REPO/third_party/truffle-compiler.jar${CP_SEP}$REPO/third_party/polyglot.jar${CP_SEP}$REPO/third_party/collections.jar${CP_SEP}$REPO/third_party/jniutils.jar${CP_SEP}$REPO/third_party/nativeimage.jar${CP_SEP}$REPO/third_party/word.jar"

NATIVE_IMAGE=$(command -v native-image 2>/dev/null || true)
if [ -z "$NATIVE_IMAGE" ] && [ -x "$G/bin/native-image" ]; then
  NATIVE_IMAGE="$G/bin/native-image"
fi
if [ -z "$NATIVE_IMAGE" ] && [ -x "$G/bin/native-image.cmd" ]; then
  NATIVE_IMAGE="$G/bin/native-image.cmd"
fi
[ -n "$NATIVE_IMAGE" ] || { echo "native-image not found in GraalVM" >&2; exit 1; }

OUT="$REPO/native-wsm$BIN_SUFFIX"
rm -f "$OUT"

"$NATIVE_IMAGE" \
  --module-path "$MODULE_PATH" \
  --no-fallback \
  --initialize-at-build-time=wsm.graalvm.providers.WsmLanguageProvider \
  -H:IncludeResources='META-INF/services/com[.]oracle[.]truffle[.]api[.]provider[.]TruffleLanguageProvider' \
  -cp "$CP" \
  wsm.graalvm.Main \
  "$OUT"

MYLISP=${MYLISP:-$REPO/external/my-lisp}
CANON="$MYLISP/lib/canon.lisp"
REGISTRY="$MYLISP/lib/surface/semantic-registry.lisp"

[ -f "$CANON" ] || { echo "missing pinned canon: $CANON" >&2; exit 1; }
[ -f "$REGISTRY" ] || { echo "missing pinned registry: $REGISTRY" >&2; exit 1; }

TMP=$(mktemp --suffix=.lisp)
trap 'rm -f "$TMP"' EXIT
cat "$CANON" > "$TMP"
printf '\n(canon-conforms?)\n' >> "$TMP"

"$OUT" "$TMP" "$REGISTRY" "$MYLISP" 2>&1 | tee "$REPO/native-canon.log"

grep -q "(canon-conformance satisfied)" "$REPO/native-canon.log"
echo "NATIVE-IMAGE-CANON-OK"
