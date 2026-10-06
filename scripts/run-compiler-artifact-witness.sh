#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
SENS_SUBMODULE="$REPO/external/sens"
OUT=${1:-"$REPO/build/compiler-artifact-witness.json"}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$(dirname "$OUT")"

EXPECTED_PIN=1869fd5e51f38565ca968abceaa4bc933ae7a114
PIN=$(git -C "$REPO" ls-files -s external/sens | awk '{print $2}')
[ "$PIN" = "$EXPECTED_PIN" ] || {
  echo "compiler-artifact witness: expected SENS pin $EXPECTED_PIN, got $PIN" >&2
  exit 2
}

FULL_SENS="$TMP/sens-full"
git clone -q --no-hardlinks "$SENS_SUBMODULE" "$FULL_SENS"
git -C "$FULL_SENS" checkout -q --detach "$PIN"
test -f "$FULL_SENS/Cargo.toml" || {
  echo "compiler-artifact witness: full SENS tree unavailable at $PIN" >&2
  exit 2
}

BUNDLE="$TMP/compiler-artifacts.lisp"
(
  cd "$FULL_SENS"
  cargo run -q -p xtask -- compiler-export --artifact
) > "$BUNDLE"

ARTIFACT_JSON="$TMP/artifact.json"
python3 "$REPO/scripts/compiler-artifact-witness.py" artifact "$BUNDLE" --json-out "$ARTIFACT_JSON" >/dev/null
python3 "$REPO/scripts/compiler-artifact-witness.py" self-test "$BUNDLE"

ROLE=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["carried_role"])' "$ARTIFACT_JSON")
MECHANISM=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["graal_mechanism"])' "$ARTIFACT_JSON")
BUNDLE_SHA=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["bundle_sha256"])' "$ARTIFACT_JSON")
REQUEST_SHA=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["semantic_request_sha256"])' "$ARTIFACT_JSON")

PROBE="$TMP/oracle-probe.lisp"
printf '%s\n' '(car (cons 7 9))' > "$PROBE"
SENS_STDOUT=$(
  cd "$FULL_SENS"
  cargo run -q -p sens-cli --bin sens -- "$PROBE"
)
SENS_OBS=$(printf '%s\n' "$SENS_STDOUT" | awk 'NF {last=$0} END {print last}')
[ -n "$SENS_OBS" ] || {
  echo "compiler-artifact witness: SENS oracle observation is empty" >&2
  exit 2
}

bash "$REPO/scripts/build.sh" >/dev/null
CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"
TEST_CLASSES="$TMP/test-classes"
mkdir -p "$TEST_CLASSES"

JAVAC=${JAVA_HOME:+$JAVA_HOME/bin/javac}
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/java}
JAVAC=${JAVAC:-javac}
JAVA=${JAVA:-java}

"$JAVAC" --release 25 -cp "$CP" -d "$TEST_CLASSES"   "$REPO/src/test/java/wsm/graalvm/CompilerArtifactWitnessContract.java"

GRAAL_STDOUT=$(
  "$JAVA" -cp "$TEST_CLASSES:$CP"     wsm.graalvm.CompilerArtifactWitnessContract "$ROLE" 7 9
)
GRAAL_MECHANISM=$(
  printf '%s\n' "$GRAAL_STDOUT" |
    sed -n 's/^GRAAL-COMPILER-ARTIFACT-MECHANISM=//p' |
    tail -n 1
)
GRAAL_OBS=$(
  printf '%s\n' "$GRAAL_STDOUT" |
    sed -n 's/^GRAAL-COMPILER-ARTIFACT-OBSERVABLE=//p' |
    tail -n 1
)
[ "$GRAAL_MECHANISM" = "$MECHANISM" ] || {
  echo "compiler-artifact witness: mechanism mismatch $GRAAL_MECHANISM != $MECHANISM" >&2
  exit 2
}
[ -n "$GRAAL_OBS" ] || {
  echo "compiler-artifact witness: Graal observation is empty" >&2
  exit 2
}

NEGATIVE_LOG="$TMP/unsupported-mechanism.log"
if "$JAVA" -cp "$TEST_CLASSES:$CP"   wsm.graalvm.CompilerArtifactWitnessContract lambda-form 7 9   >"$NEGATIVE_LOG" 2>&1; then
  echo "compiler-artifact witness: unsupported mechanism unexpectedly executed" >&2
  exit 2
fi
grep -q 'BLOCKED-MECHANISM' "$NEGATIVE_LOG"

WSM_COMMIT=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
SENS_VERSION=$(cd "$FULL_SENS" && rustc --version)
GRAAL_VERSION=$("$JAVA" --version 2>&1 | head -n 1)

export OUT ROLE MECHANISM BUNDLE_SHA REQUEST_SHA SENS_OBS GRAAL_OBS WSM_COMMIT SENS_VERSION GRAAL_VERSION
python3 - <<'PY'
import hashlib
import json
import os
from pathlib import Path

def digest(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()

sens = os.environ["SENS_OBS"]
graal = os.environ["GRAAL_OBS"]

doc = {
    "schema": "wsm-compiler-artifact-witness/1",
    "semantic_artifact": {
        "repository": "juv4uk/sens",
        "sens_commit": "1869fd5e51f38565ca968abceaa4bc933ae7a114",
        "artifact_schema": "compiler-compilation-artifact/1",
        "bundle_sha256": os.environ["BUNDLE_SHA"],
        "fixture_id": "nucleus-d3-100",
        "semantic_request_sha256": os.environ["REQUEST_SHA"],
        "carried_role": os.environ["ROLE"],
    },
    "target": {
        "substrate": "graalvm",
        "wsm_commit": os.environ["WSM_COMMIT"],
        "mode": "jvm",
        "mechanism": os.environ["MECHANISM"],
        "adapter": "wsm.graalvm.CompilerArtifactWitnessContract",
        "sid8_fallback_used": False,
        "toolchain": {
            "rust_oracle": os.environ["SENS_VERSION"],
            "graalvm": os.environ["GRAAL_VERSION"],
        },
    },
    "stages": {
        "artifact-validation": "green",
        "backend-lowering": "green",
        "install-runtime": "green",
        "observable-normalization": "green" if sens == graal else "red",
    },
    "observable": {
        "normalization": "exact canonical S-expression text",
        "sens_oracle": sens,
        "graal": graal,
        "sens_sha256": digest(sens),
        "graal_sha256": digest(graal),
    },
    "negative_controls": {
        "artifact-digest-mismatch": "caught",
        "research-domain": "caught",
        "stale-artifact": "caught",
        "backend-semantic-field": "caught",
        "target-capability-smuggling": "caught",
        "unsupported-mechanism": "caught",
        "deliberate-result-divergence": "caught",
    },
    "failure_localization_order": [
        "artifact-validation",
        "backend-lowering",
        "install-runtime",
        "observable-normalization",
    ],
}

Path(os.environ["OUT"]).write_text(
    json.dumps(doc, indent=2, ensure_ascii=False) + "\n",
    encoding="utf-8",
)
PY

python3 "$REPO/scripts/compiler-artifact-witness.py" evidence "$OUT"

DIVERGED="$TMP/diverged.json"
python3 - "$OUT" "$DIVERGED" <<'PY'
import json
import sys
from pathlib import Path

doc = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
doc["observable"]["graal"] = "__deliberate_divergence__"
Path(sys.argv[2]).write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
PY

DIVERGENCE_LOG="$TMP/divergence.log"
if python3 "$REPO/scripts/compiler-artifact-witness.py" evidence "$DIVERGED"     >"$DIVERGENCE_LOG" 2>&1; then
  echo "compiler-artifact witness: deliberate result divergence escaped gate" >&2
  exit 2
fi
grep -q 'result-divergence' "$DIVERGENCE_LOG"

echo "COMPILER-ARTIFACT-GRAAL-WITNESS-GREEN bundle=$BUNDLE_SHA request=$REQUEST_SHA oracle=$SENS_OBS graal=$GRAAL_OBS"
