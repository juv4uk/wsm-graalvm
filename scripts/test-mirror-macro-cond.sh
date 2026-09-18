#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MIRROR="$REPO/mirror/my-lisp/lib/macro.lisp"
REGISTRY="$REPO/external/my-lisp/lib/surface/semantic-registry.lisp"
MYLISP="$REPO/external/my-lisp"

[ -f "$MIRROR" ] || { echo "missing mirror: $MIRROR" >&2; exit 1; }
[ -f "$REGISTRY" ] || { echo "missing registry: $REGISTRY" >&2; exit 1; }

python3 - "$MIRROR" <<'PY'
import sys

src = open(sys.argv[1], encoding="utf-8").read()
i = 0
n = len(src)

def skip():
    global i
    while i < n:
        if src[i] == ';':
            while i < n and src[i] != '\n':
                i += 1
        elif src[i].isspace():
            i += 1
        else:
            break

def atom():
    global i
    start = i
    while i < n and not src[i].isspace() and src[i] not in '();':
        if src[i] == '"':
            i += 1
            while i < n:
                if src[i] == '\\\\':
                    i += 2
                elif src[i] == '"':
                    i += 1
                    break
                else:
                    i += 1
            continue
        i += 1
    return src[start:i]

def form():
    global i
    skip()
    if i >= n:
        return None
    if src[i] != '(':
        return atom()
    i += 1
    out = []
    while True:
        skip()
        if i >= n:
            raise SystemExit("unterminated list")
        if src[i] == ')':
            i += 1
            return out
        out.append(form())

forms = []
while True:
    skip()
    if i >= n:
        break
    forms.append(form())

def walk(x):
    if isinstance(x, list):
        if x and x[0] == 'cond':
            for clause in x[1:]:
                if not isinstance(clause, list):
                    raise SystemExit("malformed cond clause")
                if len(clause) == 2:
                    raise SystemExit("two-part COND remains: " + repr(clause))
        for y in x:
            walk(y)

for x in forms:
    walk(x)
PY

if [ -z "${G:-}" ]; then
  JBIN=$(readlink -f "$(command -v java)")
  G=$(dirname "$(dirname "$JBIN")")
fi

bash "$REPO/scripts/build.sh"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"
"$G/bin/java" \
  -Dpolyglot.engine.WarnInterpreterOnly=false \
  -Dwsm.registryPath="$REGISTRY" \
  -Dwsm.rootDir="$MYLISP" \
  -cp "$CP" \
  wsm.graalvm.Main "$MIRROR" "$REGISTRY" "$MYLISP"

echo "MIRROR-MACRO-CANONICAL-COND-OK"
