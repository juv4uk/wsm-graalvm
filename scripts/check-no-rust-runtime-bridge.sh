#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
SRC="$REPO/src/main/java"

fail() {
  echo "NO-RUST-RUNTIME-BRIDGE FAIL-CLOSED: $*" >&2
  exit 1
}

[ -d "$SRC" ] || fail "missing production Java source tree"

# Production Graal execution must not shell out to Rust or the my-lisp CLI.
# Keep this deliberately narrow: documentation may name Rust, but runtime Java
# must not contain process-spawn APIs or executable/toolchain paths for it.
if grep -RInE   'ProcessBuilder|Runtime\.getRuntime\(\)\.exec|(^|[^A-Za-z0-9_])(cargo|rustc)([^A-Za-z0-9_]|$)|target/(debug|release)/my-lisp|my-lisp(\.exe)?[[:space:]]'   "$SRC"; then
  fail "production Java contains a Rust/runtime process bridge"
fi

echo "NO-RUST-RUNTIME-BRIDGE-GREEN"
