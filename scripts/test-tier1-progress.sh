#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
BASELINE="$REPO/refs/tier1-baseline.properties"
MYLISP="$REPO/external/my-lisp"
CORPUS="$MYLISP/tests/fixtures/conformance.lisp"
REGISTRY="$MYLISP/lib/surface/semantic-registry.lisp"

[ -f "$BASELINE" ] || { echo "missing baseline: $BASELINE" >&2; exit 1; }
[ -f "$CORPUS" ] || { echo "missing corpus: $CORPUS" >&2; exit 1; }
[ -f "$REGISTRY" ] || { echo "missing registry: $REGISTRY" >&2; exit 1; }

# shellcheck disable=SC1090
source "$BASELINE"

ACTUAL_PIN=$(git -C "$MYLISP" rev-parse HEAD)
[ "$ACTUAL_PIN" = "$MY_LISP_PIN" ] || {
  echo "TIER1-LEDGER FAIL: upstream pin changed" >&2
  echo "  baseline=$MY_LISP_PIN" >&2
  echo "  actual=$ACTUAL_PIN" >&2
  exit 1
}

bash "$REPO/scripts/build.sh"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

set +e
REPORT=$("$JAVA_HOME/bin/java" -cp "$CP"   wsm.graalvm.ConformanceMain "$CORPUS" "$REGISTRY" 2>&1)
STATUS=$?
set -e
printf '%s
' "$REPORT"

SUMMARY=$(printf '%s
' "$REPORT" | grep -E 'conformance tier-1 report:' | tail -1 || true)
[ -n "$SUMMARY" ] || {
  echo "TIER1-LEDGER FAIL: missing conformance summary" >&2
  exit 1
}

number() {
  local key=$1
  printf '%s
' "$SUMMARY" | sed -n "s/.*$key=\([0-9][0-9]*\).*/\1/p"
}

TOTAL=$(number total)
PASS=$(number pass)
SKIPPED=$(number skipped-expected-error)
FAIL=$(number fail)

for pair in "total:$TOTAL" "pass:$PASS" "skipped:$SKIPPED" "fail:$FAIL"; do
  key=${pair%%:*}
  value=${pair#*:}
  [ -n "$value" ] || {
    echo "TIER1-LEDGER FAIL: could not parse $key from: $SUMMARY" >&2
    exit 1
  }
done

# Error fixtures have a separate executable parity gate; this ledger combines
# its 8/8 invariant with the value-fixture baseline without silently counting
# skipped errors as passed values.
[ "$TOTAL" -eq "$SELECTED" ] || {
  echo "TIER1-LEDGER FAIL: selected drift $TOTAL != $SELECTED" >&2; exit 1;
}
[ "$SKIPPED" -eq "$EXPECTED_ERROR_PASS" ] || {
  echo "TIER1-LEDGER FAIL: error-fixture count drift $SKIPPED != $EXPECTED_ERROR_PASS" >&2; exit 1;
}
[ "$PASS" -ge "$VALUE_PASS_MIN" ] || {
  echo "TIER1-LEDGER REGRESSION: value pass $PASS < $VALUE_PASS_MIN" >&2; exit 1;
}
[ "$FAIL" -le "$VALUE_FAIL_MAX" ] || {
  echo "TIER1-LEDGER REGRESSION: value fail $FAIL > $VALUE_FAIL_MAX" >&2; exit 1;
}

# A nonzero ConformanceMain is expected until value_fail reaches zero.
if [ "$FAIL" -gt 0 ] && [ "$STATUS" -eq 0 ]; then
  echo "TIER1-LEDGER FAIL: harness reported zero exit with known failures" >&2
  exit 1
fi
if [ "$FAIL" -eq 0 ] && [ "$STATUS" -ne 0 ]; then
  echo "TIER1-LEDGER FAIL: harness failed despite zero fixture failures" >&2
  exit 1
fi

printf 'TIER1-LEDGER-OK pin=%s selected=%d value-pass=%d expected-error-pass=%d value-fail=%d
'   "$ACTUAL_PIN" "$TOTAL" "$PASS" "$EXPECTED_ERROR_PASS" "$FAIL"
