#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:?usage: verify-authority.sh <my-lisp-root>}"
test -d "$ROOT"

cat > "${TMPDIR:-/tmp}/wsm-authority.$$.sha256" <<'EOF'
9768f683e90cfb56ca95675d1f6ac0e6ede91e21cebe97e20b455cf1b3094791  PLACEHOLDER/language-contract.lisp
2108c4bf61c88200d8f43f0c4d11fbc6abadd51430ec460fd9fe69ea685e183f  PLACEHOLDER/my-lisp-constitution.lisp
1f7fdec882f2146acc9b1bef499291c24b5cf6308455042db458da0c278c632a  PLACEHOLDER/lib/canon.lisp
f4cddfa0d248c1afdf0de17b213e706165ee51dfdcd2fce601cd5904910b87f2  PLACEHOLDER/lib/surface/semantic-registry.lisp
e942f74e14cfcbf9a45c93bffe589d3dac517268eb695b25b85df6e811d2f12e  PLACEHOLDER/tests/fixtures/conformance.lisp
EOF
CHECK="${TMPDIR:-/tmp}/wsm-authority.$$.sha256"
trap 'rm -f "$CHECK"' EXIT
sed -i "s#PLACEHOLDER/#$ROOT/#g" "$CHECK"

for f in language-contract.lisp my-lisp-constitution.lisp lib/canon.lisp lib/surface/semantic-registry.lisp tests/fixtures/conformance.lisp; do
  test -f "$ROOT/$f" || {
    echo "FAIL-CLOSED: missing $ROOT/$f" >&2
    exit 1
  }
done

sha256sum --check "$CHECK"
echo "AUTHORITY-DIGESTS-GREEN"
