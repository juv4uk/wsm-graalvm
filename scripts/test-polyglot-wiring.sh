#!/usr/bin/env bash
# End-to-end M0 smoke: real pinned registry -> numeric IDs -> Polyglot -> mechanism.
set -euo pipefail

G=${G:?set G to the GraalVM distribution root}
REPO=$(cd "$(dirname "$0")/.." && pwd)
REG="$REPO/external/my-lisp/lib/surface/semantic-registry.lisp"

[ -f "$REG" ] || {
  echo "POLYGLOT-WIRING-GATE: missing pinned registry" >&2
  exit 1
}

bash "$REPO/scripts/build.sh"

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
printf '%s\n' '(0005 (0004 1 2))' > "$TMP"

CP="$REPO/classes:$REPO/third_party/graalvm-collections.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graal-sdk.jar"

OUT=$("$G/bin/java" \
  -Dpolyglot.engine.WarnInterpreterOnly=false \
  -cp "$CP" \
  wsm.graalvm.Main "$TMP" "$REG" "$REPO/external/my-lisp" 2>&1)

printf '%s\n' "$OUT"
printf '%s\n' "$OUT" | grep -Fq '[wsm-graalvm M0] result: 1' || {
  echo "POLYGLOT-WIRING-GATE: numeric-ID smoke did not produce 1" >&2
  exit 1
}

echo "POLYGLOT-WIRING-GATE-OK"
