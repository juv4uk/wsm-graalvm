#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
SENS=${SENS_DIR:-"$REPO/external/sens"}
ADAPTER="$REPO/scripts/check-sens-execution-conformance.py"
LANE="$SENS/benchmarks/execution-ladder-conformance"
MANIFEST="$SENS/benchmarks/current-en-vs-d1d8/fixtures/d3-smoke.json"

fail() { echo "SENS-CONFORMANCE-ADAPTER-RED: $*" >&2; exit 1; }

[ -f "$ADAPTER" ] || fail "adapter missing"
[ -f "$LANE/validate.py" ] || fail "upstream validator missing"
[ -f "$LANE/emit_oracle.py" ] || fail "upstream oracle emitter missing"
[ -f "$LANE/generate_bounded.py" ] || fail "upstream bounded generator missing"
[ -f "$MANIFEST" ] || fail "upstream D3 manifest missing"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

python3 "$LANE/selftest.py"

python3 "$LANE/emit_oracle.py" \
  --manifest "$MANIFEST" \
  --out "$TMP/real-oracle.jsonl"

REAL_OUT=$(python3 "$ADAPTER" --sens-dir "$SENS" "$TMP/real-oracle.jsonl")
printf '%s\n' "$REAL_OUT"
grep -Fq "GRAAL-CONFORMANCE-IMPORT-GREEN cases=2" <<<"$REAL_OUT" \
  || fail "expected two real D3 oracle rows"

HELPER="$SENS/target/release/examples/current_en_vs_d1d8_cpu"
[ -x "$HELPER" ] || fail "oracle helper not built by emit_oracle.py"

python3 "$LANE/generate_bounded.py" \
  --helper "$HELPER" \
  --out "$TMP/bounded.jsonl"

python3 - "$TMP/bounded.jsonl" "$TMP/bounded-l0.jsonl" <<'PY'
import json
import sys
from pathlib import Path

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
rows = [
    json.loads(line)
    for line in src.read_text(encoding="utf-8").splitlines()
    if line.strip()
]
l0 = [row for row in rows if row["producer_layer"] == "L0"]
if len(l0) != 28:
    raise SystemExit(f"expected 28 L0 bounded rows, got {len(l0)}")
with dst.open("w", encoding="utf-8") as handle:
    for row in l0:
        handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
PY

BOUNDED_OUT=$(python3 "$ADAPTER" --sens-dir "$SENS" "$TMP/bounded-l0.jsonl")
printf '%s\n' "$BOUNDED_OUT"
grep -Fq "GRAAL-CONFORMANCE-IMPORT-GREEN cases=28" <<<"$BOUNDED_OUT" \
  || fail "expected 28 bounded L0 rows"
grep -Fq '"grammar_profile":"d1-d3-structural-predicate-v1"' <<<"$BOUNDED_OUT" \
  || fail "grammar_profile was not preserved"

python3 - "$TMP/bounded-l0.jsonl" "$BOUNDED_OUT" <<'PY'
import json
import sys
from pathlib import Path

source = [
    json.loads(line)
    for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
    if line.strip()
]
output = []
for line in sys.argv[2].splitlines():
    prefix = "GRAAL-CONFORMANCE-CASE "
    if line.startswith(prefix):
        output.append(json.loads(line[len(prefix):]))

if source != output:
    raise SystemExit("adapter output is not lossless relative to upstream L0 rows")
PY

python3 - "$TMP/real-oracle.jsonl" "$TMP/legacy.jsonl" "$TMP/width.jsonl" <<'PY'
import json
import sys
from pathlib import Path

row = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()[0])

legacy = dict(row)
legacy["legacy_identity_used"] = True
Path(sys.argv[2]).write_text(
    json.dumps(legacy, ensure_ascii=False, sort_keys=True) + "\n",
    encoding="utf-8",
)

width = json.loads(json.dumps(row))
if not width["identity_trace"]:
    raise SystemExit("real oracle row unexpectedly has no identity trace")
width["identity_trace"][0]["bits"] += "0"
Path(sys.argv[3]).write_text(
    json.dumps(width, ensure_ascii=False, sort_keys=True) + "\n",
    encoding="utf-8",
)
PY

if python3 "$ADAPTER" --sens-dir "$SENS" "$TMP/legacy.jsonl"; then
  fail "legacy identity unexpectedly accepted"
fi

if python3 "$ADAPTER" --sens-dir "$SENS" "$TMP/width.jsonl"; then
  fail "wrong-width identity unexpectedly accepted"
fi

echo "SENS-CONFORMANCE-ADAPTER-GREEN real=2 bounded=28 lossless=yes negatives=2"
