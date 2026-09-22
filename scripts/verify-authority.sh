#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:?usage: verify-authority.sh <my-lisp-root>}"
test -d "$ROOT"

cat > "${TMPDIR:-/tmp}/wsm-authority.$$.sha256" <<'EOF'
615660828fe4a4abab73d910d095653d4dfe16b7c4143f140c0b9fcd5362e80c  PLACEHOLDER/language-contract.lisp
aa3ce61ed29de928909281ca78767b10528574a5c9cbf877a4305097ff7957b4  PLACEHOLDER/my-lisp-constitution.lisp
9b7b10861944b9b51d8b1a33aadb8109c7ee384b8e15d84485de71710007a991  PLACEHOLDER/lib/canon.lisp
17c6dbcbe01a6208d2cb2fdf10abaefb773a8060ed435c8eeba89b1c747beba7  PLACEHOLDER/lib/surface/semantic-registry.lisp
19ee5b5752b05f7db53c43dffd89fa36edb2247ea02c5f9dc822a2026217f8d3  PLACEHOLDER/tests/fixtures/conformance.lisp
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
