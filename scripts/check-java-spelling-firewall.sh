#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
REGISTRY="$REPO/external/my-lisp/lib/surface/semantic-registry.lisp"
SRC="$REPO/src/main/java"
EXCEPTIONS="$REPO/refs/java-surface-spelling-exceptions.txt"

fail() { echo "java-spelling-firewall FAIL-CLOSED: $*" >&2; exit 1; }

[ -f "$REGISTRY" ] || fail "missing pinned semantic registry"
[ -d "$SRC" ] || fail "missing production Java source tree"
[ -f "$EXCEPTIONS" ] || fail "missing spelling exception ledger"

SURFACES=$(mktemp)
ALLOW=$(mktemp)
HITS=$(mktemp)
trap 'rm -f "$SURFACES" "$ALLOW" "$HITS"' EXIT

grep -oE '\((en|uk|ukr|sa|sym|compat) [^()[:space:]]+ (stable|compatibility-only)\)' "$REGISTRY"   | awk '{print $2}'   | grep -v '^—$'   | LC_ALL=C sort -u > "$SURFACES"

awk -F'|' '
  /^[[:space:]]*#/ || /^[[:space:]]*$/ { next }
  {
    gsub(/^[[:space:]]+|[[:space:]]+$/, "", $1)
    if ($1 != "") print $1
  }
' "$EXCEPTIONS" | LC_ALL=C sort -u > "$ALLOW"

[ -s "$SURFACES" ] || fail "no admitted registry surfaces extracted"

while IFS= read -r surface; do
  grep -Fxq "$surface" "$ALLOW" && continue
  if grep -R -n -F -- "\"$surface\"" "$SRC" > "$HITS"; then
    echo "Public Lisp surface hardcoded in production Java: $surface" >&2
    cat "$HITS" >&2
    fail "route public spellings through CanonRegistry/semantic ID or document a narrow exception"
  fi
done < "$SURFACES"

echo "JAVA-SPELLING-FIREWALL-GREEN"
echo "  admitted-surfaces=$(wc -l < "$SURFACES" | tr -d ' ')"
echo "  explicit-exceptions=$(wc -l < "$ALLOW" | tr -d ' ')"
