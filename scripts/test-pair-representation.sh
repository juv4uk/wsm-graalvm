#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

bash "$REPO/scripts/build.sh"

TEST_CLASSES="$REPO/test-classes-pair-representation"
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

"$G/bin/javac" --release 25 -cp "$CP" -d "$TEST_CLASSES" \
  "$REPO/src/test/java/wsm/graalvm/PairRepresentationContract.java"

"$G/bin/java" -cp "$TEST_CLASSES:$CP" \
  wsm.graalvm.PairRepresentationContract "$REPO"

PAIR_SOURCE="$REPO/src/main/java/wsm/graalvm/Value.java"
python3 - "$PAIR_SOURCE" <<'PY'
from pathlib import Path
import sys

text = Path(sys.argv[1]).read_text()
start = text.index("public static final class Pair")
end = text.index("\n    }\n\n    public record SemanticRef", start)
pair_block = text[start:end]
if "equals(" in pair_block or "hashCode(" in pair_block:
    raise SystemExit("Pair representation must not define equals/hashCode")
print("PAIR-JAVA-EQUALITY-FIREWALL-GREEN")
PY
