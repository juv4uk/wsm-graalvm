#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)

# Keep the scripts usable from both POSIX shells and Git Bash on Windows.
if [[ "${RUNNER_OS:-}" == "Windows" || "$(uname -s 2>/dev/null || true)" =~ ^(MINGW|MSYS|CYGWIN) ]]; then
  CP_SEP=';'
else
  CP_SEP=':'
fi

if [ -z "${G:-}" ]; then
  if [ -n "${GRAALVM_HOME:-}" ]; then
    G="$GRAALVM_HOME"
  else
    JBIN=$(readlink -f "$(command -v java)")
    G=$(dirname "$(dirname "$JBIN")")
  fi
fi
G=${G:?set G to GraalVM root}
JAVAC="$G/bin/javac"
if [ ! -x "$JAVAC" ]; then
  JAVAC=$(command -v javac)
fi

bash "$REPO/scripts/fetch-third-party.sh"

CP="$REPO/third_party/truffle-api.jar${CP_SEP}$REPO/third_party/polyglot.jar${CP_SEP}$REPO/third_party/truffle-runtime.jar${CP_SEP}$REPO/third_party/graalvm-collections.jar"

rm -rf "$REPO/classes"
mkdir -p "$REPO/classes"

"$JAVAC" --release 25 \
  -cp "$CP" \
  -d "$REPO/classes" \
  $(find "$REPO/src/main/java" -name '*.java')

if [ -d "$REPO/src/main/resources" ]; then
  cp -R "$REPO/src/main/resources/." "$REPO/classes/"
fi

echo "BUILD-OK"
