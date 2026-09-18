#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:?usage: verify-authority.sh <my-lisp-root>}"
test -d "$ROOT"

cat > "${TMPDIR:-/tmp}/wsm-authority.$$.sha256" <<'EOF'
737cc3373abca40ae511ea0114609fdcb7a172b918e69dced615a458ff5818a9  PLACEHOLDER/language-contract.lisp
65f0f19cbb7c6a911430754349cc4828c1ce2df9be94a4693c9cece215a12369  PLACEHOLDER/my-lisp-constitution.lisp
9b7b10861944b9b51d8b1a33aadb8109c7ee384b8e15d84485de71710007a991  PLACEHOLDER/lib/canon.lisp
f64f5ea7341a5f8d2c873d1f5f2731e7b30befda8b6593a9af3a596942e51d72  PLACEHOLDER/lib/surface/semantic-registry.lisp
e0b50161d1919f2c26c8c7549b583876acb5cebad437441ed0e1d47514bec3f3  PLACEHOLDER/tests/fixtures/conformance.lisp
EOF
CHECK="${TMPDIR:-/tmp}/wsm-authority.$$.sha256"
trap 'rm -f "$CHECK"' EXIT
sed -i "s#PLACEHOLDER/#$ROOT/#g" "$CHECK"

for f in   language-contract.lisp   my-lisp-constitution.lisp   lib/canon.lisp   lib/surface/semantic-registry.lisp   tests/fixtures/conformance.lisp; do
  test -f "$ROOT/$f" || {
    echo "FAIL-CLOSED: missing $ROOT/$f" >&2
    exit 1
  }
done

sha256sum --check "$CHECK"
echo "AUTHORITY-DIGESTS-GREEN"
