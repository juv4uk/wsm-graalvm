#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi
G=${G:?set G to GraalVM root}

bash "$REPO/scripts/fetch-third-party.sh"

CP="$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

rm -rf "$REPO/classes"
mkdir -p "$REPO/classes"

"$G/bin/javac" --release 25 \
  -cp "$CP" \
  -d "$REPO/classes" \
  $(find "$REPO/src/main/java" -name '*.java')

if [ -d "$REPO/src/main/resources" ]; then
  cp -R "$REPO/src/main/resources/." "$REPO/classes/"
fi

echo "BUILD-OK"
