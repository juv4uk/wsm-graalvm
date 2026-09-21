#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:?usage: verify-authority.sh <my-lisp-root>}"
REPO=$(cd "$(dirname "$0")/.." && pwd)
MANIFEST="$REPO/refs/lisp-dependency-manifest.lisp"

test -d "$ROOT" || {
  echo "FAIL-CLOSED: authority root missing: $ROOT" >&2
  exit 1
}
test -f "$MANIFEST" || {
  echo "FAIL-CLOSED: dependency manifest missing: $MANIFEST" >&2
  exit 1
}

MANIFEST_PIN=$(sed -n 's/.*(pin \. "\([0-9a-f]\{40\}\)").*/\1/p' "$MANIFEST" | head -1)
GITLINK_PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
HEAD_PIN=$(git -C "$ROOT" rev-parse HEAD)

[ -n "$MANIFEST_PIN" ] || {
  echo "FAIL-CLOSED: cannot parse pin from $MANIFEST" >&2
  exit 1
}
[ -n "$GITLINK_PIN" ] || {
  echo "FAIL-CLOSED: cannot read external/my-lisp gitlink" >&2
  exit 1
}

[ "$MANIFEST_PIN" = "$GITLINK_PIN" ] || {
  echo "FAIL-CLOSED: manifest pin and gitlink differ" >&2
  echo "  manifest=$MANIFEST_PIN" >&2
  echo "  gitlink=$GITLINK_PIN" >&2
  exit 1
}
[ "$HEAD_PIN" = "$GITLINK_PIN" ] || {
  echo "FAIL-CLOSED: checked-out authority differs from gitlink" >&2
  echo "  head=$HEAD_PIN" >&2
  echo "  gitlink=$GITLINK_PIN" >&2
  exit 1
}

for f in \
  language-contract.lisp \
  my-lisp-constitution.lisp \
  lib/canon.lisp \
  lib/macro.lisp \
  lib/core.lisp \
  lib/surface/semantic-registry.lisp \
  tests/fixtures/conformance.lisp; do
  test -f "$ROOT/$f" || {
    echo "FAIL-CLOSED: missing $ROOT/$f" >&2
    exit 1
  }
done

REGISTRY="$ROOT/lib/surface/semantic-registry.lisp"
grep -Eq '^[[:space:]]*\(binary[[:space:]]+8\)[[:space:]]*$' "$REGISTRY" || {
  echo "FAIL-CLOSED: authority registry is not the exact (binary 8) schema" >&2
  exit 1
}
grep -Eq '^[[:space:]]*\([01]{8}[[:space:]]' "$REGISTRY" || {
  echo "FAIL-CLOSED: authority registry has no exact 8-bit SID rows" >&2
  exit 1
}

echo "AUTHORITY-PIN-GREEN pin=$HEAD_PIN registry=binary-8"
