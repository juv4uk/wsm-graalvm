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

readarray -t UPSTREAM_EXPECTED < <(python3 - "$CONSTITUTION" <<'PY'
import json
import re
import sys
from pathlib import Path

text = Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()

def expected(expr: str) -> str:
    needle = f'(fixture (expr . "{expr}")'
    for line in text:
        if needle not in line:
            continue
        match = re.search(r'\(expected \. "((?:\\.|[^"])*)"\)', line)
        if not match:
            raise SystemExit(f"fixture found but expected field missing: {expr}")
        return json.loads('"' + match.group(1) + '"')
    raise SystemExit(f"upstream constitution fixture not found: {expr}")

print(expected("(atom (quote radio))"))
print(expected("(abs -5)"))
PY
)
EXPECTED=${UPSTREAM_EXPECTED[0]}
ABS_EXPECTED=${UPSTREAM_EXPECTED[1]}

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
RUST_VERSION=$(cd "$RUST_TREE" && rustc --version)

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

        // Negative route: 00010000/abs is Lisp-owned in lib/core.lisp.
        // Deliberately do NOT bootstrap core here. The exact SID must fail
        // closed rather than finding a hidden Java semantic duplicate.
        try (Context context = Context.newBuilder("wsm").build()) {
            context.eval("wsm", "(00010000 -5)");
            System.out.println("NEGATIVE-ROUTE-UNEXPECTED-RESULT");
            System.exit(23);
        } catch (org.graalvm.polyglot.PolyglotException error) {
            String message = error.getMessage();
            if (message == null) message = "";
            if (message.contains("Type:")) {
                System.out.println("NEGATIVE-ROUTE-FAILURE: Type");
            } else if (message.contains("MechanismUnavailable:")) {
                System.out.println("NEGATIVE-ROUTE-FAILURE: MechanismUnavailable");
            } else {
                System.out.println("NEGATIVE-ROUTE-FAILURE: Unexpected:" + message);
                System.exit(24);
            }
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

NEGATIVE_FAILURE=$(
  printf '%s\n' "$GRAAL_STDOUT" |
    sed -n 's/^NEGATIVE-ROUTE-FAILURE: //p' |
    tail -n 1
)
[ -n "$NEGATIVE_FAILURE" ] || {
  echo "negative route produced neither named failure nor explicit evidence" >&2
  exit 1
}

GRAAL_VERSION=$("$JAVA" --version 2>&1 | head -n 1)

export PIN WSM_COMMIT EXPECTED ABS_EXPECTED RUST_OBS GRAAL_OBS NEGATIVE_FAILURE OUT RUST_VERSION GRAAL_VERSION
python3 - <<'PY'
import json
import os
from pathlib import Path

expected = os.environ["EXPECTED"]
rust = os.environ["RUST_OBS"]
graal = os.environ["GRAAL_OBS"]

doc = {
    "schema": "wsm-substrate-parity-evidence/1",
    "gate_id": "differential-substrate-parity",
    "exact_pair": {
        "wsm_graalvm_head": os.environ["WSM_COMMIT"],
        "my_lisp_pin": os.environ["PIN"],
    },
    "execution_mode": "rust-reference+jvm",
    "toolchain": {
        "rust": os.environ["RUST_VERSION"],
        "graalvm": os.environ["GRAAL_VERSION"],
    },
    "corpus": {
        "authority": "my-lisp-constitution.lisp",
        "fixture": "(atom (quote radio))",
    },
    "authority": {
        "semantic_owner": "juv4uk/sens",
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
                "repository": "juv4uk/sens",
                "path": "my-lisp-constitution.lisp",
            },
            "normalization_rule": "exact canonical S-expression text",
            "expected": {
                "repository": "juv4uk/sens",
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
        },
        {
            "id": "negative-abs-no-java-fallback-jvm",
            "kind": "negative-route",
            "status": "green",
            "upstream_pin": os.environ["PIN"],
            "wsm_commit": os.environ["WSM_COMMIT"],
            "contract_id": "lisp-owned-abs-no-substrate-fallback",
            "fixture_path": "my-lisp-constitution.lisp",
            "sid": "00010000",
            "source_owner": {
                "repository": "juv4uk/sens",
                "path": "lib/core.lisp",
            },
            "normalization_rule": "named failure class after deliberate Lisp-owner suppression",
            "expected": {
                "repository": "juv4uk/sens",
                "path": "my-lisp-constitution.lisp",
                "normalized": os.environ["ABS_EXPECTED"],
            },
            "graal": {
                "substrate": "graalvm",
                "mode": "jvm",
                "provenance": "core.lisp deliberately not bootstrapped; exact SID invoked directly",
                "fallback_used": False,
                "normalized": os.environ["NEGATIVE_FAILURE"],
            },
            "negative_route": {
                "disabled_execution_owner": "lisp-binding:00010000",
                "observed_failure": os.environ["NEGATIVE_FAILURE"],
                "fallback_used": False,
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

python3 "$REPO/scripts/check-substrate-parity-evidence.py" "$OUT" --require-ready
