#!/usr/bin/env bash
# Derive the exact sparse authority/runtime/witness surface from the manifest.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MANIFEST="${1:-$REPO/refs/lisp-dependency-manifest.lisp}"

[ -f "$MANIFEST" ] || {
  echo "sparse-allowlist FAIL-CLOSED: missing manifest: $MANIFEST" >&2
  exit 1
}

awk '
  /^[[:space:]]*\(authority[[:space:]]*$/ { section="admit"; next }
  /^[[:space:]]*\(bootstrap-required[[:space:]]*$/ { section="admit"; next }
  /^[[:space:]]*\(witness[[:space:]]*$/ { section="admit"; next }
  /^[[:space:]]*\(not-bootstrap-by-default[[:space:]]*$/ { section="skip"; next }
  /^[[:space:]]*\(rule[[:space:]]*$/ { section="skip"; next }

  section == "admit" {
    if (match($0, /"[^"]+\.lisp"/)) {
      path=substr($0, RSTART + 1, RLENGTH - 2)
      print path
    }
  }
' "$MANIFEST" | LC_ALL=C sort -u
