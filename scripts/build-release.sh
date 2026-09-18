#!/usr/bin/env bash
# Release build: JVM bundle (linux/windows portable) + optional native image.
# Usage: bash scripts/build-release.sh <tag> [native]
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
TAG=${1:?usage: build-release.sh TAG [native]}

if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

bash "$REPO/scripts/build.sh"

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
OUT="$REPO/dist"
mkdir -p "$OUT"

cp -r "$REPO/classes" "$STAGE/classes"
mkdir  "$STAGE/third_party"
for j in truffle-api polyglot truffle-runtime graalvm-collections nativeimage truffle-compiler; do
  cp "$REPO/third_party/$j.jar" "$STAGE/third_party/"
done
mkdir -p "$STAGE/scripts"
cp "$REPO/scripts/run-canon.sh" "$REPO/scripts/fetch-third-party.sh" "$REPO/scripts/build.sh" "$STAGE/scripts/" 2>/dev/null || true
cp "$REPO/refs/RELEASE-$TAG.lisp" "$STAGE/refs-RELEASE.lisp" 2>/dev/null || true
cp "$REPO/CHANGELOG.md" "$REPO/LICENSE" "$REPO/README.md" "$STAGE/" 2>/dev/null || true

cat > "$STAGE/launch.sh" << 'INNER'
#!/usr/bin/env bash
# portable launcher: runs the substrate JVM from any directory.
set -euo pipefail
SELF="$(cd "$(dirname "$0")" && pwd)"
JAVA=$JAVA_HOME/bin/java
[ -x "$JAVA" ] || JAVA=$(command -v java)
exec "$JAVA" --enable-native-access=ALL-UNNAMED \
  -Dpolyglot.engine.WarnInterpreterOnly=false \
  -Dwsm.registryPath="$SELF/external/my-lisp/lib/surface/semantic-registry.lisp" \
  -Dtruffle.class.path.append="$SELF/classes" \
  -cp "$SELF/classes:$SELF/third_party/truffle-api.jar:$SELF/third_party/polyglot.jar:$SELF/third_party/truffle-runtime.jar:$SELF/third_party/graalvm-collections.jar:$SELF/third_party/nativeimage.jar:$SELF/third_party/truffle-compiler.jar" \
  wsm.graalvm.Main "${1:-$SELF/external/my-lisp/lib/canon.lisp}" \
                   "$SELF/external/my-lisp/lib/surface/semantic-registry.lisp" \
                   "$SELF"
INNER
chmod +x "$STAGE/launch.sh"

OUT_MARK="jvm"
if [ "${2:-}" = "native" ]; then
  bash "$REPO/scripts/build-native.sh"
  cp "$REPO/native-wsm" "$STAGE/native-wsm"
  OUT_MARK="native+$jvm"
fi

tar -czf "$OUT/wsm-graalvm-$TAG-$OUT_MARK.tar.gz" -C "$STAGE" .
echo "release bundle: $OUT/wsm-graalvm-$TAG-$OUT_MARK.tar.gz"
