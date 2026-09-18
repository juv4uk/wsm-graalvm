#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${G:-}" ]; then
  if [ -n "${GRAALVM_HOME:-}" ]; then
    G="$GRAALVM_HOME"
  elif [ -n "${JAVA_HOME:-}" ]; then
    G="$JAVA_HOME"
  else
    JBIN=$(command -v java)
    if command -v readlink >/dev/null 2>&1; then
      JBIN=$(readlink -f "$JBIN" 2>/dev/null || true)
    fi
    [ -n "$JBIN" ] || { echo "missing java for GraalVM discovery" >&2; exit 1; }
    G=$(cd "$(dirname "$JBIN")/.." && pwd)
  fi
fi
G="${G%/}"
[ -x "$G/bin/javac" ] || { echo "missing GraalVM javac: $G/bin/javac" >&2; exit 1; }

# Local/CI fallback used by the repository's pinned GraalVM environment.
if [ ! -x "$G/bin/javac" ] && [ -d /home/agents/graalvm-community-25.3.4.1+1.1 ]; then
  G=/home/agents/graalvm-community-25.3.4.1+1.1
fi
G=${G:?set G to GraalVM root}

bash "$REPO/scripts/fetch-third-party.sh"

case "$(uname -s 2>/dev/null || true)" in
  MINGW*|MSYS*|CYGWIN*) PATH_SEP=";" ;;
  *) PATH_SEP=":" ;;
esac
CP="$REPO/third_party/truffle-api.jar${PATH_SEP}$REPO/third_party/polyglot.jar${PATH_SEP}$REPO/third_party/truffle-runtime.jar${PATH_SEP}$REPO/third_party/graalvm-collections.jar"

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
