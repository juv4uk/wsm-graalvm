#!/usr/bin/env bash
# Build the M0 substrate against a local GraalVM distribution.
# G=/path/to/graalvm-community-... bash scripts/build.sh
set -euo pipefail
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi
# fail-closed: either an explicit GraalVM or one on PATH
command -v java >/dev/null 2>&1 || { echo "no java on PATH (set G to graalvm-community path)"; exit 1; }
# classpath deps: fetch pinned jars if absent (e.g. fresh clone / CI runner)
bash "$(dirname "$0")/fetch-third-party.sh"
TC="$G/lib/truffle/truffle-compiler.jar"
if [ ! -f "$TC" ]; then
  # copy from the distribution once; runtime classpath will pick it up
  cp "$TC" "$REPO/third_party/" 2>/dev/null || true
  TC="$REPO/third_party/truffle-compiler.jar"
fi
[ -f "$TC" ] || cp "$REPO/third_party/truffle-compiler.jar" "$REPO/third_party/truffle-compiler.jar" 2>/dev/null || true
REPO=$(cd "$(dirname "$0")/.." && pwd)
"$G/bin/javac" --release 25 \
  -cp "$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar:$TC" \
  -d "$REPO/classes" \
  $(find "$REPO/src" -name '*.java')
echo "BUILD-OK (interpreter classes; JIT comes from the GraalVM structural JIT)"
