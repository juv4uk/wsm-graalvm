#!/usr/bin/env bash
# Materialize the exact Graal/Truffle API jars used by this substrate.
# Jars stay untracked; the version pin is source-controlled here.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
OUT="$REPO/third_party"
VERSION="${GRAAL_ARTIFACT_VERSION:-25.3.4.1}"
BASE="https://repo.maven.apache.org/maven2"

mkdir -p "$OUT"

fetch() {
  local group=$1 artifact=$2 target=$3
  local path="${group//.//}/$artifact/$VERSION/$artifact-$VERSION.jar"
  local tmp="$OUT/.$target.tmp"
  echo "fetch $artifact@$VERSION"
  curl --fail --location --silent --show-error \
    --retry 3 --retry-delay 1 \
    "$BASE/$path" -o "$tmp"
  "$JAVA_HOME/bin/jar" tf "$tmp" >/dev/null
  mv "$tmp" "$OUT/$target"
}

fetch org.graalvm.truffle truffle-api truffle-api.jar
fetch org.graalvm.polyglot polyglot polyglot.jar
fetch org.graalvm.truffle truffle-runtime truffle-runtime.jar
fetch org.graalvm.truffle truffle-compiler truffle-compiler.jar
fetch org.graalvm.sdk collections graalvm-collections.jar
fetch org.graalvm.sdk nativeimage nativeimage.jar
fetch org.graalvm.sdk jniutils jniutils.jar
fetch org.graalvm.sdk nativebridge nativebridge.jar

echo "Graal artifact set ready: $VERSION"
sha256sum \
  "$OUT/truffle-api.jar" \
  "$OUT/polyglot.jar" \
  "$OUT/truffle-runtime.jar" \
  "$OUT/truffle-compiler.jar" \
  "$OUT/graalvm-collections.jar" \
  "$OUT/nativeimage.jar" \
  "$OUT/jniutils.jar" \
  "$OUT/nativebridge.jar"
