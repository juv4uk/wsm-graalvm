#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)

if [[ "${RUNNER_OS:-}" == "Windows" || "$(uname -s 2>/dev/null || true)" =~ ^(MINGW|MSYS|CYGWIN) ]]; then
  WINDOWS_HOST=1
  CP_SEP=';'
else
  WINDOWS_HOST=0
  CP_SEP=':'
fi

if [ -z "${G:-}" ]; then
  G="${GRAALVM_HOME:-}"
fi

# Debian/local fallback: preserve the pinned GraalVM distribution on non-CI hosts.
if [ -z "$G" ] && [ -d /home/agents/graalvm-community-25.3.4.1+1.1 ]; then
  G=/home/agents/graalvm-community-25.3.4.1+1.1
fi
if [ -z "${G:-}" ] && [ "$WINDOWS_HOST" -eq 0 ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi
if [ -z "${G:-}" ]; then
  G="$(cygpath -m "${GRAALVM_HOME:-}")"
fi
G=${G:?set G to GraalVM root}

if [ "$WINDOWS_HOST" -eq 1 ]; then
  JAVAC=$(command -v javac)
  REPO_NATIVE=$(cygpath -w "$REPO")
  CP="$REPO_NATIVE\\third_party\\truffle-api.jar;$REPO_NATIVE\\third_party\\polyglot.jar;$REPO_NATIVE\\third_party\\truffle-runtime.jar;$REPO_NATIVE\\third_party\\graalvm-collections.jar"
  OUT_DIR="$REPO_NATIVE\\classes"
else
  JAVAC="$G/bin/javac"
  [ -x "$JAVAC" ] || JAVAC=$(command -v javac)
  CP="$REPO/third_party/truffle-api.jar${CP_SEP}$REPO/third_party/polyglot.jar${CP_SEP}$REPO/third_party/truffle-runtime.jar${CP_SEP}$REPO/third_party/graalvm-collections.jar"
  OUT_DIR="$REPO/classes"
fi

bash "$REPO/scripts/fetch-third-party.sh"

rm -rf "$REPO/classes"
mkdir -p "$REPO/classes"

"$JAVAC" --release 25 \
  -cp "$CP" \
  -d "$OUT_DIR" \
  $(find "$REPO/src/main/java" -name '*.java')

if [ -d "$REPO/src/main/resources" ]; then
  cp -R "$REPO/src/main/resources/." "$REPO/classes/"
fi

echo "BUILD-OK"
