#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:?usage: verify-authority.sh <my-lisp-root>}"
test -d "$ROOT"

cat > "${TMPDIR:-/tmp}/wsm-authority.$$.sha256" <<'EOF'
aaaa9fecf72da3cf30e30cc52a0b6e379913f4e0ec788e367453838680cb493f  PLACEHOLDER/language-contract.lisp
62e65f3d91f050bb758ff9acb4dd4d3e9219cca4da95a726fe0335b223cacc93  PLACEHOLDER/my-lisp-constitution.lisp
1f7fdec882f2146acc9b1bef499291c24b5cf6308455042db458da0c278c632a  PLACEHOLDER/lib/canon.lisp
f4cddfa0d248c1afdf0de17b213e706165ee51dfdcd2fce601cd5904910b87f2  PLACEHOLDER/lib/surface/semantic-registry.lisp
587ce479113ecda0f15a9e6d39875165c2f5e17a081397bbe739aaf9cbbe0263  PLACEHOLDER/tests/fixtures/conformance.lisp
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
