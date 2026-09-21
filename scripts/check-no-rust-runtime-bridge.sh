#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
SRC="$REPO/src/main/java"

fail() {
  echo "NO-RUST-RUNTIME-BRIDGE FAIL-CLOSED: $*" >&2
  exit 1
}

[ -d "$SRC" ] || fail "missing production Java source tree"

# Production Graal execution must not spawn a helper runtime at all.
# If Java cannot create a subprocess, it cannot secretly shell out to cargo,
# rustc, my-lisp, or any replay helper. Documentation/comments may name Rust.
if grep -RInE   'new[[:space:]]+ProcessBuilder|Runtime\.getRuntime\(\)\.exec|\.exec\([[:space:]]*new[[:space:]]+String'   "$SRC"; then
  fail "production Java contains a subprocess bridge"
fi

echo "NO-RUST-RUNTIME-BRIDGE-GREEN"
