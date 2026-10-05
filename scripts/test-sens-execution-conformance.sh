#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
SENS=${SENS_DIR:-"$REPO/external/sens"}
ADAPTER="$REPO/scripts/check-sens-execution-conformance.py"
LANE="$SENS/benchmarks/execution-ladder-conformance"
MANIFEST="$SENS/benchmarks/current-en-vs-d1d8/fixtures/d3-smoke.json"
ARTIFACT="$LANE/artifacts/bounded-d1-d3.jsonl"
SUMMARY="$LANE/artifacts/bounded-d1-d3.summary.json"

fail() { echo "SENS-CONFORMANCE-ADAPTER-RED: $*" >&2; exit 1; }

[ -f "$ADAPTER" ] || fail "adapter missing"
[ -f "$LANE/validate.py" ] || fail "upstream validator missing"
[ -f "$LANE/emit_oracle.py" ] || fail "upstream oracle emitter missing"
[ -f "$MANIFEST" ] || fail "upstream D3 manifest missing"
[ -f "$ARTIFACT" ] || fail "committed upstream bounded artifact missing"
[ -f "$SUMMARY" ] || fail "committed upstream bounded summary missing"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

python3 "$LANE/selftest.py"

python3 "$LANE/emit_oracle.py" \
  --manifest "$MANIFEST" \
  --out "$TMP/real-oracle.jsonl"

REAL_OUT=$(python3 "$ADAPTER" --sens-dir "$SENS" "$TMP/real-oracle.jsonl")
printf '%s\n' "$REAL_OUT"

python3 - "$ARTIFACT" "$SUMMARY" "$TMP/bounded-l0.jsonl" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

artifact = Path(sys.argv[1])
summary_path = Path(sys.argv[2])
out = Path(sys.argv[3])

summary = json.loads(summary_path.read_text(encoding="utf-8"))
raw = artifact.read_bytes()
actual_hash = hashlib.sha256(raw).hexdigest()
if actual_hash != summary["artifact_sha256"]:
    raise SystemExit(
        f"artifact hash mismatch: {actual_hash} != {summary['artifact_sha256']}"
    )

rows = [
    json.loads(line)
    for line in raw.decode("utf-8").splitlines()
    if line.strip()
]
if len(rows) != summary["rows"]:
    raise SystemExit(f"row count mismatch: {len(rows)} != {summary['rows']}")

case_ids = {row["case_id"] for row in rows}
if len(case_ids) != summary["case_ids"]:
    raise SystemExit(
        f"case-id count mismatch: {len(case_ids)} != {summary['case_ids']}"
    )

source_shas = {row["upstream_sha"] for row in rows}
if source_shas != {summary["source_sha"]}:
    raise SystemExit(
        f"artifact provenance mismatch: {sorted(source_shas)} != {summary['source_sha']}"
    )

if any(row["legacy_identity_used"] for row in rows):
    raise SystemExit("committed upstream artifact contains legacy identity")

l0 = [row for row in rows if row["producer_layer"] == "L0"]
l1 = [row for row in rows if row["producer_layer"] == "L1"]
if len(l0) != summary["l0_rows"] or len(l1) != summary["l1_rows"]:
    raise SystemExit("layer counts differ from upstream summary")

for row in rows:
    bound = row["exhaustive_bound"]
    if row["evidence_scope"] != "bounded-exhaustive" or not isinstance(bound, dict):
        raise SystemExit("committed bounded artifact contains non-bounded row")
    if bound["grammar_profile"] != summary["grammar_profile"]:
        raise SystemExit("grammar_profile differs from upstream summary")
    for key, expected in summary["bound"].items():
        if bound[key] != expected:
            raise SystemExit(f"bound field {key} differs from upstream summary")

with out.open("w", encoding="utf-8", newline="\n") as handle:
    for row in l0:
        handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")

print(
    "UPSTREAM-BOUNDED-ARTIFACT-GREEN "
    f"source={summary['source_sha']} cases={summary['case_ids']} "
    f"rows={summary['rows']} l0={summary['l0_rows']}"
)
PY

BOUNDED_OUT=$(python3 "$ADAPTER" --sens-dir "$SENS" "$TMP/bounded-l0.jsonl")
printf '%s\n' "$BOUNDED_OUT"

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
print(f"GRAAL-BOUNDED-IMPORT-LOSSLESS cases={len(source)}")
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
    newline="\n",
)

width = json.loads(json.dumps(row))
if not width["identity_trace"]:
    raise SystemExit("real oracle row unexpectedly has no identity trace")
width["identity_trace"][0]["bits"] += "0"
Path(sys.argv[3]).write_text(
    json.dumps(width, ensure_ascii=False, sort_keys=True) + "\n",
    encoding="utf-8",
    newline="\n",
)
PY

if python3 "$ADAPTER" --sens-dir "$SENS" "$TMP/legacy.jsonl"; then
  fail "legacy identity unexpectedly accepted"
fi

if python3 "$ADAPTER" --sens-dir "$SENS" "$TMP/width.jsonl"; then
  fail "wrong-width identity unexpectedly accepted"
fi

echo "SENS-CONFORMANCE-ADAPTER-GREEN committed-upstream=yes lossless=yes negatives=2"
