#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
BASELINE="$REPO/refs/tier1-baseline.properties"
MYLISP="$REPO/external/my-lisp"
CORPUS="$MYLISP/tests/fixtures/conformance.lisp"
REGISTRY="$MYLISP/lib/surface/semantic-registry.lisp"

[ -f "$BASELINE" ] || { echo "missing baseline: $BASELINE" >&2; exit 1; }
source "$BASELINE"

ACTUAL_PIN=$(git -C "$MYLISP" rev-parse HEAD)
[ "$ACTUAL_PIN" = "$MY_LISP_PIN" ] || {
  echo "TIER1-LEDGER FAIL: upstream pin changed" >&2
  echo "  baseline=$MY_LISP_PIN" >&2
  echo "  actual=$ACTUAL_PIN" >&2
  exit 1
}

WSM_GRAALVM_COMMIT=$(git -C "$REPO" rev-parse HEAD)
CONTRACT="$MYLISP/language-contract.lisp"
[ -f "$CONTRACT" ] || { echo "TIER1-LEDGER FAIL: missing language contract: $CONTRACT" >&2; exit 1; }
ACTUAL_CONTRACT_MAJOR=$(sed -n 's/^((major \. \([0-9][0-9]*\)) (minor \. \([0-9][0-9]*\)).*/\1/p' "$CONTRACT" | head -1)
ACTUAL_CONTRACT_MINOR=$(sed -n 's/^((major \. \([0-9][0-9]*\)) (minor \. \([0-9][0-9]*\)).*/\2/p' "$CONTRACT" | head -1)
[ -n "$ACTUAL_CONTRACT_MAJOR" ] && [ -n "$ACTUAL_CONTRACT_MINOR" ] || {
  echo "TIER1-LEDGER FAIL: cannot parse language contract version" >&2
  exit 1
}
[ "$ACTUAL_CONTRACT_MAJOR" -eq "$LANGUAGE_CONTRACT_MAJOR" ] && [ "$ACTUAL_CONTRACT_MINOR" -eq "$LANGUAGE_CONTRACT_MINOR" ] || {
  echo "TIER1-LEDGER FAIL: language contract selection changed" >&2
  echo "  baseline=$LANGUAGE_CONTRACT_MAJOR.$LANGUAGE_CONTRACT_MINOR" >&2
  echo "  actual=$ACTUAL_CONTRACT_MAJOR.$ACTUAL_CONTRACT_MINOR" >&2
  exit 1
}

bash "$REPO/scripts/build.sh"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"
TEST_CLASSES="$REPO/test-classes-ledger"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

"$JAVA_HOME/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES" "$REPO/src/test/java/wsm/graalvm/Tier1ErrorParityContract.java"

set +e
VALUE_REPORT=$(java -cp "$CP" -Dtruffle.class.path.append="$REPO/classes" wsm.graalvm.ConformanceMain "$CORPUS" "$REGISTRY" "$REPO" 2>&1)
VALUE_STATUS=$?
set -e
printf '%s\n' "$VALUE_REPORT"

VALUE_SUMMARY=$(printf '%s\n' "$VALUE_REPORT" | grep -E 'conformance tier-1 report:' | tail -1 || true)
[ -n "$VALUE_SUMMARY" ] || { echo "TIER1-LEDGER FAIL: missing value summary" >&2; exit 1; }

number_from() {
  local summary=$1 key=$2
  printf '%s\n' "$summary" | sed -n "s/.*$key=\([0-9][0-9]*\).*/\1/p"
}

TOTAL=$(number_from "$VALUE_SUMMARY" total)
PASS=$(number_from "$VALUE_SUMMARY" pass)
SKIPPED=$(number_from "$VALUE_SUMMARY" skipped-expected-error)
FAIL=$(number_from "$VALUE_SUMMARY" fail)

set +e
ERROR_REPORT=$("$JAVA_HOME/bin/java" -cp "$TEST_CLASSES:$CP" -Dtruffle.class.path.append="$REPO/classes" wsm.graalvm.Tier1ErrorParityContract "$CORPUS" "$REGISTRY" 2>&1)
ERROR_STATUS=$?
set -e
printf '%s\n' "$ERROR_REPORT"

ERROR_SUMMARY=$(printf '%s\n' "$ERROR_REPORT" | grep -E 'TIER1-ERROR-PARITY-SUMMARY' | tail -1 || true)
[ -n "$ERROR_SUMMARY" ] || { echo "TIER1-LEDGER FAIL: missing ErrorKind summary" >&2; exit 1; }
ERROR_TOTAL=$(number_from "$ERROR_SUMMARY" total)
ERROR_PASS=$(number_from "$ERROR_SUMMARY" pass)
ERROR_BLOCKED=$(number_from "$ERROR_SUMMARY" blocked)
ERROR_FAIL=$(number_from "$ERROR_SUMMARY" fail)

[ "$TOTAL" -eq "$SELECTED" ] || { echo "TIER1-LEDGER FAIL: selected drift $TOTAL != $SELECTED" >&2; exit 1; }
[ "$SKIPPED" -eq "$ERROR_FIXTURES" ] || { echo "TIER1-LEDGER FAIL: error fixture accounting drift $SKIPPED != $ERROR_FIXTURES" >&2; exit 1; }
[ "$PASS" -ge "$VALUE_PASS_MIN" ] || { echo "TIER1-LEDGER REGRESSION: value pass $PASS < $VALUE_PASS_MIN" >&2; exit 1; }
[ "$FAIL" -le "$VALUE_FAIL_MAX" ] || { echo "TIER1-LEDGER REGRESSION: value fail $FAIL > $VALUE_FAIL_MAX" >&2; exit 1; }
[ "$ERROR_TOTAL" -eq "$ERROR_FIXTURES" ] || { echo "TIER1-LEDGER FAIL: error total $ERROR_TOTAL != $ERROR_FIXTURES" >&2; exit 1; }
[ "$ERROR_PASS" -ge "$ERROR_PASS_MIN" ] || { echo "TIER1-LEDGER REGRESSION: error pass $ERROR_PASS < $ERROR_PASS_MIN" >&2; exit 1; }
[ "$ERROR_BLOCKED" -le "$ERROR_BLOCKED_MAX" ] || { echo "TIER1-LEDGER REGRESSION: error blocked $ERROR_BLOCKED > $ERROR_BLOCKED_MAX" >&2; exit 1; }
[ "$ERROR_FAIL" -eq 0 ] || { echo "TIER1-LEDGER FAIL: error parity fail=$ERROR_FAIL" >&2; exit 1; }
[ "$ERROR_STATUS" -eq 0 ] || { echo "TIER1-LEDGER FAIL: error parity runner exited $ERROR_STATUS" >&2; exit 1; }

if [ "$FAIL" -gt 0 ] && [ "$VALUE_STATUS" -eq 0 ]; then
  echo "TIER1-LEDGER FAIL: value counter exited zero despite known failures" >&2; exit 1
fi
if [ "$FAIL" -eq 0 ] && [ "$VALUE_STATUS" -ne 0 ]; then
  echo "TIER1-LEDGER FAIL: value counter failed despite zero failures" >&2; exit 1
fi

printf 'TIER1-LEDGER-OK upstream-pin=%s wsm-commit=%s contract=%s.%s selected=%d value-pass=%d value-fail=%d error-pass=%d error-blocked=%d error-fail=%d\n' "$ACTUAL_PIN" "$WSM_GRAALVM_COMMIT" "$ACTUAL_CONTRACT_MAJOR" "$ACTUAL_CONTRACT_MINOR" "$TOTAL" "$PASS" "$FAIL" "$ERROR_PASS" "$ERROR_BLOCKED" "$ERROR_FAIL"
