#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
TABLE="$REPO/src/main/java/wsm/graalvm/SemanticMechanismTable.java"
LEDGER="$REPO/refs/lisp-mechanism-budget.lisp"
UPSTREAM="$REPO/external/my-lisp"

fail() { echo "lisp-mechanism-budget FAIL-CLOSED: $*" >&2; exit 1; }

[ -f "$TABLE" ] || fail "missing mechanism table"
[ -f "$LEDGER" ] || fail "missing mechanism ledger"
[ -d "$UPSTREAM" ] || fail "missing external/my-lisp authority"

TABLE_IDS=$(mktemp)
LEDGER_ROWS=$(mktemp)
DEBT_IDS=$(mktemp)
trap 'rm -f "$TABLE_IDS" "$LEDGER_ROWS" "$DEBT_IDS"' EXIT

grep -oE '"[0-9]{4}"' "$TABLE" | tr -d '"' | LC_ALL=C sort -u > "$TABLE_IDS"

sed -n 's/^[[:space:]]*(mechanism "\([0-9][0-9][0-9][0-9]\)" \([^[:space:]]*\).*/\1 \2/p' "$LEDGER"   | LC_ALL=C sort -u > "$LEDGER_ROWS"

[ -s "$TABLE_IDS" ] || fail "no semantic IDs discovered in SemanticMechanismTable"
[ -s "$LEDGER_ROWS" ] || fail "no mechanism rows discovered in ledger"

TABLE_ONLY=$(comm -23 "$TABLE_IDS" <(awk '{print $1}' "$LEDGER_ROWS"))
LEDGER_ONLY=$(comm -13 "$TABLE_IDS" <(awk '{print $1}' "$LEDGER_ROWS"))

[ -z "$TABLE_ONLY" ] || {
  echo "Unclassified Java mechanism IDs:" >&2
  echo "$TABLE_ONLY" >&2
  fail "new Java mechanism requires #77 classification"
}

[ -z "$LEDGER_ONLY" ] || {
  echo "Stale active ledger IDs not present in Java table:" >&2
  echo "$LEDGER_ONLY" >&2
  fail "move retired Java mechanisms from mechanism rows to retired rows"
}

awk '$2 == "lisp-defined-retire-java" {print $1}' "$LEDGER_ROWS" > "$DEBT_IDS"
MAX_DEBT=$(sed -n 's/^[[:space:]]*(max-lisp-defined-java-debt[[:space:]]*\.[[:space:]]*\([0-9][0-9]*\)).*/\1/p' "$LEDGER")
[ -n "$MAX_DEBT" ] || fail "missing max-lisp-defined-java-debt"
DEBT_COUNT=$(wc -l < "$DEBT_IDS" | tr -d ' ')
[ "$DEBT_COUNT" -le "$MAX_DEBT" ] || fail "Lisp-defined Java debt grew: $DEBT_COUNT > $MAX_DEBT"

while IFS= read -r path; do
  [ -f "$UPSTREAM/$path" ] || fail "ledger evidence not materialized from sparse authority: $path"
done < <(grep -oE '"[^"]+\.lisp"' "$LEDGER" | tr -d '"' | LC_ALL=C sort -u)

echo "LISP-MECHANISM-BUDGET-GREEN"
echo "  classified-java-ids=$(wc -l < "$TABLE_IDS" | tr -d ' ')"
echo "  lisp-defined-java-debt=$DEBT_COUNT/$MAX_DEBT"
if [ "$DEBT_COUNT" -gt 0 ]; then
  echo "  retire-from-java=$(paste -sd, "$DEBT_IDS")"
fi
