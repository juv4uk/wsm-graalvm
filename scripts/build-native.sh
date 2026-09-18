#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)

if [[ "${RUNNER_OS:-}" == "Windows" || "$(uname -s 2>/dev/null || true)" =~ ^(MINGW|MSYS|CYGWIN) ]]; then
  WINDOWS_HOST=1
  CP_SEP=';'
  BINARY_SUFFIX='.exe'
else
  WINDOWS_HOST=0
  CP_SEP=':'
  BINARY_SUFFIX=''
fi

G="${G:-${GRAALVM_HOME:-}}"
if [ -z "$G" ] && [ -d /home/agents/graalvm-community-25.3.4.1+1.1 ]; then
  G=/home/agents/graalvm-community-25.3.4.1+1.1
fi
if [ -z "$G" ] && [ "$WINDOWS_HOST" -eq 0 ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

bash "$REPO/scripts/build.sh"

if [ "$WINDOWS_HOST" -eq 1 ]; then
  G_POSIX="$(cygpath -u "${GRAALVM_HOME:-$G}")"
  NATIVE_IMAGE=""
  for candidate in \
    "$G_POSIX/bin/native-image.cmd" \
    "$G_POSIX/bin/native-image.exe" \
    "$G_POSIX/bin/native-image"; do
    if [ -f "$candidate" ]; then
      NATIVE_IMAGE="$candidate"
      break
    fi
  done
  if [ -z "$NATIVE_IMAGE" ]; then
    NATIVE_IMAGE=$(command -v native-image.cmd 2>/dev/null || true)
  fi
  if [ -z "$NATIVE_IMAGE" ]; then
    NATIVE_IMAGE=$(command -v native-image 2>/dev/null || true)
  fi
  REPO_NATIVE=$(cygpath -w "$REPO")
  CP="$REPO_NATIVE\\classes"
  MODULE_PATH="$REPO_NATIVE\\third_party\\truffle-api.jar;$REPO_NATIVE\\third_party\\truffle-runtime.jar;$REPO_NATIVE\\third_party\\truffle-compiler.jar;$REPO_NATIVE\\third_party\\polyglot.jar;$REPO_NATIVE\\third_party\\collections.jar;$REPO_NATIVE\\third_party\\jniutils.jar;$REPO_NATIVE\\third_party\\nativeimage.jar;$REPO_NATIVE\\third_party\\word.jar"
else
  NATIVE_IMAGE=$(command -v native-image 2>/dev/null || true)
  if [ -z "$NATIVE_IMAGE" ] && [ -x "$G/bin/native-image" ]; then
    NATIVE_IMAGE="$G/bin/native-image"
  fi
  if [ -z "$NATIVE_IMAGE" ] && [ -x "$G/bin/native-image.cmd" ]; then
    NATIVE_IMAGE="$G/bin/native-image.cmd"
  fi
  CP="$REPO/classes"
  MODULE_PATH="$REPO/third_party/truffle-api.jar${CP_SEP}$REPO/third_party/truffle-runtime.jar${CP_SEP}$REPO/third_party/truffle-compiler.jar${CP_SEP}$REPO/third_party/polyglot.jar${CP_SEP}$REPO/third_party/collections.jar${CP_SEP}$REPO/third_party/jniutils.jar${CP_SEP}$REPO/third_party/nativeimage.jar${CP_SEP}$REPO/third_party/word.jar"
fi
[ -n "$NATIVE_IMAGE" ] || { echo "native-image not found in GraalVM" >&2; exit 1; }

OUT="$REPO/native-wsm$BINARY_SUFFIX"
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

if [ "$WINDOWS_HOST" -eq 1 ]; then
  TMP_ARG="$(cygpath -w "$TMP")"
  REGISTRY_ARG="$(cygpath -w "$REGISTRY")"
  MYLISP_ARG="$(cygpath -w "$MYLISP")"
else
  TMP_ARG="$TMP"
  REGISTRY_ARG="$REGISTRY"
  MYLISP_ARG="$MYLISP"
fi

"$OUT" "$TMP_ARG" "$REGISTRY_ARG" "$MYLISP_ARG" 2>&1 | tee "$REPO/native-canon.log"

grep -q "(canon-conformance satisfied)" "$REPO/native-canon.log"
echo "NATIVE-IMAGE-CANON-OK"
