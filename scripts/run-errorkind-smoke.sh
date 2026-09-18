#!/usr/bin/env bash
# Contract-visible ErrorKind smoke corpus for the current Truffle substrate.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MYLISP=${MYLISP:-"$REPO/external/my-lisp"}
REGISTRY="$MYLISP/lib/surface/semantic-registry.lisp"

[ -f "$REGISTRY" ] || {
  echo "missing pinned registry: $REGISTRY" >&2
  exit 1
}
[ -d "$REPO/classes" ] || {
  echo "missing classes; run scripts/build.sh first" >&2
  exit 1
}

if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

TC="$G/lib/truffle/truffle-compiler.jar"
[ -f "$TC" ] || TC="$REPO/third_party/truffle-compiler.jar"

run_expect_error() {
  local name="$1" expected="$2" source="$3"
  local one out status
  one=$(mktemp --suffix=.lisp)
  out=$(mktemp)
  trap 'rm -f "$one" "$out"' RETURN

  printf '%s\n' "$source" >"$one"
  set +e
  "$G/bin/java" \
    -Dpolyglot.engine.WarnInterpreterOnly=false \
    -Dwsm.registryPath="$REGISTRY" \
    -Dtruffle.class.path.append="$REPO/classes" \
    --enable-native-access=ALL-UNNAMED \
    -cp "$REPO/classes:$REPO/third_party/graalvm-collections.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$TC" \
    wsm.graalvm.Main "$one" "$REGISTRY" "$MYLISP" >"$out" 2>&1
  status=$?
  set -e

  grep -Fq "error-kind=$expected" "$out" || {
    echo "ERRORKIND-FAIL name=$name expected=$expected status=$status" >&2
    cat "$out" >&2
    exit 1
  }
  [ "$status" -ne 0 ] || {
    echo "ERRORKIND-FAIL name=$name expected nonzero exit status" >&2
    cat "$out" >&2
    exit 1
  }
}

run_expect_error "parse" "Parse" "( "
run_expect_error "unknown-symbol" "UnknownSymbol" "(never-defined-symbol)"
run_expect_error "arity" "Arity" "(atom)"
run_expect_error "type" "Type" "(car 42)"
run_expect_error "invalid-form" "InvalidForm" "(def atom 1)"

echo "ERRORKIND-SMOKE-GREEN"
