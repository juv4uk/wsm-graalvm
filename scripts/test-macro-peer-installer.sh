#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

REGISTRY="$REPO/external/my-lisp/lib/surface/semantic-registry.lisp"
[ -f "$REGISTRY" ] || {
  echo "missing pinned registry: $REGISTRY" >&2
  exit 1
}

bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/test-classes-macro-peer-installer"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES"   "$REPO/src/test/java/wsm/graalvm/MacroPeerInstallerContract.java"

"$G/bin/java" -cp "$TEST_CLASSES:$CP"   wsm.graalvm.MacroPeerInstallerContract "$REGISTRY"

grep -q 'private static final String ID_DEFMACRO'   "$REPO/src/main/java/wsm/graalvm/Compiler.java" && {
    echo "Java-owned ID_DEFMACRO semantics still present" >&2
    exit 1
  }

grep -q 'compileDefmacro'   "$REPO/src/main/java/wsm/graalvm/Compiler.java" && {
    echo "Java-owned compileDefmacro semantics still present" >&2
    exit 1
  }

echo "LISP-OWNED-0012-GREEN"
