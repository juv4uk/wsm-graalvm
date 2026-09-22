#!/usr/bin/env bash
# Fail closed when evidence belongs to a different exact source/substrate pair.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
EVIDENCE_ROOT=${EVIDENCE_ROOT:-"$REPO/build"}
CURRENT_WSM_HEAD=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
CURRENT_MY_LISP_PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
MANIFEST="$REPO/refs/lisp-dependency-manifest.lisp"

fail() { echo "EVIDENCE-FRESHNESS FAIL-CLOSED: $*" >&2; exit 1; }

[[ "$CURRENT_WSM_HEAD" =~ ^[0-9a-f]{40}$ ]] || fail "invalid wsm head: $CURRENT_WSM_HEAD"
[[ "$CURRENT_MY_LISP_PIN" =~ ^[0-9a-f]{40}$ ]] || fail "cannot read exact my-lisp gitlink pin"
[ -f "$MANIFEST" ] || fail "dependency manifest missing"

ACTUAL_MY_LISP_HEAD=$(git -C "$REPO/external/my-lisp" rev-parse HEAD)
[ "$CURRENT_MY_LISP_PIN" = "$ACTUAL_MY_LISP_HEAD" ] ||   fail "gitlink/submodule mismatch: pin=$CURRENT_MY_LISP_PIN head=$ACTUAL_MY_LISP_HEAD"

MANIFEST_PIN=$(sed -n 's/^[[:space:]]*(pin[[:space:]]*\.[[:space:]]*"\([0-9a-f]\{40\}\)")[[:space:]]*$/\1/p' "$MANIFEST")
[ "$MANIFEST_PIN" = "$CURRENT_MY_LISP_PIN" ] ||   fail "manifest/gitlink mismatch: manifest=$MANIFEST_PIN gitlink=$CURRENT_MY_LISP_PIN"

DEFAULT_FILES="substrate-switch-proof.json substrate-parity-smoke.json tier1-evidence.json bootstrap-evidence.json cold-start-no-rust.json"
EVIDENCE_FILES=${EVIDENCE_FILES:-$DEFAULT_FILES}

echo "CURRENT EXACT PAIR"
echo "  wsm-graalvm: $CURRENT_WSM_HEAD"
echo "  my-lisp:     $CURRENT_MY_LISP_PIN"
echo "  manifest:    $MANIFEST_PIN"

fresh=0
stale=0
missing=0

for name in $EVIDENCE_FILES; do
  file="$EVIDENCE_ROOT/$name"
  if [ ! -s "$file" ]; then
    echo "MISSING: $name"
    missing=$((missing + 1))
    continue
  fi

  read -r recorded_wsm recorded_pin < <(
    python3 - "$file" <<'PY'
import json, sys
from pathlib import Path

d = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))

wsm = ""
pin = ""
pair = d.get("exact_pair")
if isinstance(pair, dict):
    wsm = pair.get("wsm_graalvm_head") or pair.get("wsm_commit") or ""
    pin = pair.get("my_lisp_pin") or pair.get("upstream_pin") or ""

if (not wsm or not pin) and isinstance(d.get("rows"), list) and d["rows"]:
    first = d["rows"][0]
    if isinstance(first, dict):
        wsm = wsm or first.get("wsm_commit", "")
        pin = pin or first.get("upstream_pin", "")

print(wsm, pin)
PY
  )

  if [ "$recorded_wsm" = "$CURRENT_WSM_HEAD" ] && [ "$recorded_pin" = "$CURRENT_MY_LISP_PIN" ]; then
    echo "FRESH: $name"
    fresh=$((fresh + 1))
  else
    echo "STALE: $name"
    echo "  recorded: wsm=$recorded_wsm pin=$recorded_pin"
    echo "  current:  wsm=$CURRENT_WSM_HEAD pin=$CURRENT_MY_LISP_PIN"
    stale=$((stale + 1))
  fi
done

echo "EVIDENCE-FRESHNESS-SUMMARY fresh=$fresh stale=$stale missing=$missing"

[ "$stale" -eq 0 ] || fail "$stale stale evidence record(s)"
[ "$missing" -eq 0 ] || fail "$missing missing evidence record(s)"

echo "EVIDENCE-FRESHNESS-GREEN head=$CURRENT_WSM_HEAD pin=$CURRENT_MY_LISP_PIN"
