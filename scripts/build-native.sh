#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
: "${G:=}"

# Debian/local fallback: same pinned distribution on the Guix/Debian host
if [ -z "$G" ] && [ -d /home/agents/graalvm-community-25.3.4.1+1.1 ]; then
  G=/home/agents/graalvm-community-25.3.4.1+1.1
fi

bash "$REPO/scripts/build.sh"

CP="$REPO/classes"
MODULE_PATH="$REPO/third_party/truffle-api.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/truffle-compiler.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/collections.jar:$REPO/third_party/jniutils.jar:$REPO/third_party/nativeimage.jar:$REPO/third_party/word.jar"

rm -f "$REPO/native-wsm"

"$G/bin/native-image"   --module-path "$MODULE_PATH"   --no-fallback   --initialize-at-build-time=wsm.graalvm.providers.WsmLanguageProvider   -H:IncludeResources='META-INF/services/com[.]oracle[.]truffle[.]api[.]provider[.]TruffleLanguageProvider'   -cp "$CP"   wsm.graalvm.Main   "$REPO/native-wsm"

MYLISP=${MYLISP:-$REPO/external/my-lisp}
CANON="$MYLISP/lib/canon.lisp"
REGISTRY="$MYLISP/lib/surface/semantic-registry.lisp"

[ -f "$CANON" ] || { echo "missing pinned canon: $CANON" >&2; exit 1; }
[ -f "$REGISTRY" ] || { echo "missing pinned registry: $REGISTRY" >&2; exit 1; }

# Match the JVM Canon acceptance path exactly: loading canon.lisp only defines
# the witness and naturally returns the last closure. The acceptance program
# must explicitly invoke the Lisp-owned observer.
TMP=$(mktemp --suffix=.lisp)
trap 'rm -f "$TMP"' EXIT
cat "$CANON" > "$TMP"
printf '\n(canon-conforms?)\n' >> "$TMP"

"$REPO/native-wsm"   "$TMP"   "$REGISTRY"   "$MYLISP" 2>&1 | tee "$REPO/native-canon.log"

grep -q "(canon-conformance satisfied)" "$REPO/native-canon.log"

WSM_COMMIT=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
MY_LISP_PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
NATIVE_VERSION=$("$G/bin/native-image" --version 2>&1 | head -n 1)

mkdir -p "$REPO/build"
python3 - "$REPO/build/native-image-evidence.json" "$WSM_COMMIT" "$MY_LISP_PIN" "$NATIVE_VERSION" <<'PY'
import json, sys
from pathlib import Path

out, head, pin, version = sys.argv[1:]
doc = {
    "schema": "wsm-native-image-evidence/1",
    "exact_pair": {
        "wsm_graalvm_head": head,
        "my_lisp_pin": pin,
    },
    "gate": {
        "id": "native-canon-witness",
        "execution_mode": "native-image",
        "native_image": version,
        "corpus": "external/my-lisp/lib/canon.lisp",
        "status": "green",
    },
}
Path(out).write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
PY

echo "NATIVE-IMAGE-CANON-OK head=$WSM_COMMIT pin=$MY_LISP_PIN"
