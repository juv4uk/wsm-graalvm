#!/usr/bin/env bash
# Build the substrate against the selected GraalVM host and pinned Truffle APIs.
set -euo pipefail
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi
G=${G:?set G to GraalVM bin parent}
REPO=$(cd "$(dirname "$0")/.." && pwd)

for jar in truffle-api polyglot truffle-runtime truffle-compiler graalvm-collections nativeimage jniutils nativebridge; do
  [ -f "$REPO/third_party/$jar.jar" ] || {
    echo "missing third_party/$jar.jar; run scripts/bootstrap-graal-deps.sh" >&2
    exit 1
  }
done

CP="$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/truffle-compiler.jar:$REPO/third_party/graalvm-collections.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/jniutils.jar:$REPO/third_party/nativebridge.jar"

"$G/bin/javac" --release 25 \
  -cp "$CP" \
  -d "$REPO/classes" \
  $(find "$REPO/src" -name '*.java')

echo "BUILD-OK"
