#!/usr/bin/env bash
# Fail-closed check: evidence records must belong to current exact pair.
# Evidence belongs to an exact (wsm-graalvm head + my-lisp pin) state.
# Stale evidence from old coordinates cannot certify current substrate.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)

fail() { echo "EVIDENCE-FRESHNESS FAIL-CLOSED: $*" >&2; exit 1; }

# Current exact pair
CURRENT_WSM_HEAD=$(git -C "$REPO" rev-parse HEAD)
CURRENT_MY_LISP_PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
[ -n "$CURRENT_WSM_HEAD" ] || fail "cannot read current wsm-graalvm HEAD"
[ -n "$CURRENT_MY_LISP_PIN" ] || fail "cannot read current my-lisp gitlink pin"

# Verify gitlink/submodule sync
ACTUAL_MY_LISP_HEAD=$(git -C "$REPO/external/my-lisp" rev-parse HEAD)
[ "$CURRENT_MY_LISP_PIN" = "$ACTUAL_MY_LISP_HEAD" ] || {
    fail "gitlink/submodule mismatch: pin=$CURRENT_MY_LISP_PIN head=$ACTUAL_MY_LISP_HEAD"
}

echo "CURRENT PAIR:"
echo "  wsm-graalvm HEAD: $CURRENT_WSM_HEAD"
echo "  my-lisp pin:      $CURRENT_MY_LISP_PIN"

# Evidence files to check (machine-readable records produced by gates)
EVIDENCE_FILES=(
    "build/substrate-switch-proof.json"
    "build/substrate-parity-smoke.json"
    "build/cold-start-no-rust.json"
)

MISSING=0
STALE=0
FRESH=0

for ef in "${EVIDENCE_FILES[@]}"; do
    if [ ! -f "$REPO/$ef" ]; then
        echo "MISSING: $ef"
        MISSING=$((MISSING + 1))
        continue
    fi

    # Extract recorded coordinates from evidence file
    RECORDED_WSM=""
    RECORDED_PIN=""

    # Try different JSON paths for exact pair
    RECORDED_WSM=$(python3 -c "
import json, sys
try:
    d=json.load(open('$REPO/$ef'))
    # Try exact_pair.wsm_graalvm_head
    if 'exact_pair' in d and 'wsm_graalvm_head' in d['exact_pair']:
        print(d['exact_pair']['wsm_graalvm_head'])
    # Try exact_pair.wsm_commit
    elif 'exact_pair' in d and 'wsm_commit' in d['exact_pair']:
        print(d['exact_pair']['wsm_commit'])
    # Try rows[0].wsm_commit (parity format)
    elif 'rows' in d and d['rows'] and 'wsm_commit' in d['rows'][0]:
        print(d['rows'][0]['wsm_commit'])
except:
    pass
" 2>/dev/null || true)

    RECORDED_PIN=$(python3 -c "
import json, sys
try:
    d=json.load(open('$REPO/$ef'))
    # Try exact_pair.my_lisp_pin
    if 'exact_pair' in d and 'my_lisp_pin' in d['exact_pair']:
        print(d['exact_pair']['my_lisp_pin'])
    # Try exact_pair.upstream_pin
    elif 'exact_pair' in d and 'upstream_pin' in d['exact_pair']:
        print(d['exact_pair']['upstream_pin'])
    # Try rows[0].upstream_pin (parity format)
    elif 'rows' in d and d['rows'] and 'upstream_pin' in d['rows'][0]:
        print(d['rows'][0]['upstream_pin'])
except:
    pass
" 2>/dev/null || true)

    if [ -z "$RECORDED_WSM" ] || [ -z "$RECORDED_PIN" ]; then
        echo "INVALID: $ef (cannot parse coordinates)"
        STALE=$((STALE + 1))
        continue
    fi

    if [ "$RECORDED_WSM" = "$CURRENT_WSM_HEAD" ] && [ "$RECORDED_PIN" = "$CURRENT_MY_LISP_PIN" ]; then
        echo "FRESH: $ef (wsm=$RECORDED_WSM pin=$RECORDED_PIN)"
        FRESH=$((FRESH + 1))
    else
        echo "STALE: $ef"
        echo "  recorded: wsm=$RECORDED_WSM pin=$RECORDED_PIN"
        echo "  current:  wsm=$CURRENT_WSM_HEAD pin=$CURRENT_MY_LISP_PIN"
        STALE=$((STALE + 1))
    fi
done

echo "SUMMARY: fresh=$FRESH stale=$STALE missing=$MISSING"

if [ $STALE -gt 0 ] || [ $MISSING -gt 0 ]; then
    exit 1
fi

exit 0
