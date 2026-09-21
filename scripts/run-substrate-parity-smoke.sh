#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MYLISP="$REPO/external/my-lisp"
OUT=${1:-"$REPO/build/substrate-parity-smoke.json"}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$(dirname "$OUT")"

PIN=$(git -C "$MYLISP" rev-parse HEAD)
WSM_COMMIT=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
CONSTITUTION="$MYLISP/my-lisp-constitution.lisp"
REGISTRY="$MYLISP/lib/surface/semantic-registry.lisp"

[ -f "$CONSTITUTION" ] || { echo "missing constitution: $CONSTITUTION" >&2; exit 1; }
[ -f "$REGISTRY" ] || { echo "missing registry: $REGISTRY" >&2; exit 1; }

EXPECTED=$(python3 - "$CONSTITUTION" <<'PY'
import json
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
needle = '(fixture (expr . "(atom (quote radio))")'
for line in path.read_text(encoding="utf-8").splitlines():
    if needle not in line:
        continue
    match = re.search(r'\(expected \. "((?:\\.|[^"])*)"\)', line)
    if not match:
        raise SystemExit("fixture found but expected field missing")
    print(json.loads('"' + match.group(1) + '"'))
    break
else:
    raise SystemExit("upstream constitution fixture not found: (atom (quote radio))")
PY
)

printf '%s\n' '(atom (quote radio))' > "$TMP/probe.lisp"

# Graal intentionally consumes a sparse Lisp-only authority closure. The Rust
# reference substrate needs the complete source tree, but must remain at the
# exact same pin. Clone locally from the already-fetched submodule object store
# instead of widening the Graal authority closure or fetching a different ref.
RUST_TREE="$TMP/my-lisp-rust"
git clone -q --no-hardlinks "$MYLISP" "$RUST_TREE"
git -C "$RUST_TREE" checkout -q --detach "$PIN"
test -f "$RUST_TREE/Cargo.toml" || {
  echo "Rust reference clone is not a full tree at $PIN" >&2
  exit 1
}

echo "parity: building/running Rust substrate at $PIN" >&2
RUST_STDOUT=$(
  cd "$RUST_TREE"
  cargo run -q -p my-lisp-cli --bin my-lisp -- "$TMP/probe.lisp"
)
RUST_OBS=$(printf '%s\n' "$RUST_STDOUT" | awk 'NF {last=$0} END {print last}')
[ -n "$RUST_OBS" ] || { echo "Rust observation empty" >&2; exit 1; }

echo "parity: building/running Graal substrate at $WSM_COMMIT" >&2
bash "$REPO/scripts/build.sh" >/dev/null

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"
cat > "$TMP/ParityProbe.java" <<'JAVA'
import org.graalvm.polyglot.Context;

public final class ParityProbe {
    public static void main(String[] args) {
        System.setProperty("wsm.registryPath", args[0]);
        try (Context context = Context.newBuilder("wsm").build()) {
            context.eval("wsm", "(atom (quote radio))");
        }
    }
}
JAVA

JAVAC=${JAVA_HOME:+$JAVA_HOME/bin/javac}
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/java}
JAVAC=${JAVAC:-javac}
JAVA=${JAVA:-java}

"$JAVAC" --release 25 -cp "$CP" -d "$TMP" "$TMP/ParityProbe.java"
GRAAL_STDOUT=$("$JAVA" -cp "$CP:$TMP" -Dtruffle.class.path.append="$REPO/classes" ParityProbe "$REGISTRY")
GRAAL_OBS=$(
  printf '%s\n' "$GRAAL_STDOUT" |
    sed -n 's/^\[wsm-graalvm M0\] result: //p' |
    tail -n 1
)
[ -n "$GRAAL_OBS" ] || { echo "Graal observation empty" >&2; exit 1; }

export PIN WSM_COMMIT EXPECTED RUST_OBS GRAAL_OBS OUT
python3 - <<'PY'
import json
import os
from pathlib import Path

expected = os.environ["EXPECTED"]
rust = os.environ["RUST_OBS"]
graal = os.environ["GRAAL_OBS"]

doc = {
    "schema": "wsm-substrate-parity-evidence/1",
    "authority": {
        "semantic_owner": "juv4uk/my-lisp",
        "rule": "Lisp-owned expected evidence judges both substrates; Rust and GraalVM are witnesses, never each other's sole oracle.",
    },
    "rows": [
        {
            "id": "tier1-atom-radio-jvm",
            "kind": "parity",
            "status": "green" if rust == expected and graal == expected else "red",
            "upstream_pin": os.environ["PIN"],
            "wsm_commit": os.environ["WSM_COMMIT"],
            "contract_id": "constitution-tier1-atom-radio",
            "fixture_path": "my-lisp-constitution.lisp",
            "sid": "00000010",
            "source_owner": {
                "repository": "juv4uk/my-lisp",
                "path": "my-lisp-constitution.lisp",
            },
            "normalization_rule": "exact canonical S-expression text",
            "expected": {
                "repository": "juv4uk/my-lisp",
                "path": "my-lisp-constitution.lisp",
                "normalized": expected,
            },
            "rust": {
                "substrate": "rust",
                "provenance": "my-lisp-cli file execution at exact upstream pin",
                "normalized": rust,
            },
            "graal": {
                "substrate": "graalvm",
                "mode": "jvm",
                "execution_owner": "substrate-mechanism:00000010",
                "provenance": "wsm Context.eval exact-byte SID route at exact wsm commit",
                "normalized": graal,
            },
        }
    ],
}

Path(os.environ["OUT"]).write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

if rust != expected or graal != expected:
    raise SystemExit(
        "PARITY-RED expected={!r} rust={!r} graal={!r}".format(expected, rust, graal)
    )

print("SUBSTRATE-PARITY-SMOKE-GREEN expected={} rust={} graal={}".format(expected, rust, graal))
PY

python3 "$REPO/scripts/check-substrate-parity-evidence.py" "$OUT"
