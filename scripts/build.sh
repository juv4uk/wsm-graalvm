#!/usr/bin/env bash
# Build the M0 substrate against a local GraalVM distribution.
# G=/path/to/graalvm-community-... bash scripts/build.sh
set -euo pipefail
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi
G=${G:?set G to graalvm-community bin parent}
REPO=$(cd "$(dirname "$0")/.." && pwd)
"$G/bin/javac" --release 25 \
  -cp "$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar" \
  -d "$REPO/classes" \
  $(find "$REPO/src" -name '*.java')
echo "BUILD-OK (interpreter classes; JIT comes from the GraalVM structural JIT)"
