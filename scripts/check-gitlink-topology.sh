#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
cd "$REPO"

fail() {
  echo "gitlink-topology FAIL-CLOSED: $*" >&2
  exit 1
}

EXPECTED="external/my-lisp"

mapfile -t GITLINKS < <(git ls-files -s | awk '$1 == "160000" {print $4}' | LC_ALL=C sort)

[ "${#GITLINKS[@]}" -eq 1 ] || {
  printf 'gitlinks found:\n' >&2
  printf '  %s\n' "${GITLINKS[@]:-<none>}" >&2
  fail "expected exactly one gitlink: $EXPECTED"
}

[ "${GITLINKS[0]}" = "$EXPECTED" ] || fail "unexpected gitlink: ${GITLINKS[0]}"

[ -f .gitmodules ] || fail "missing .gitmodules"
mapfile -t MODULE_PATHS < <(git config -f .gitmodules --get-regexp '^submodule\..*\.path$' | awk '{print $2}' | LC_ALL=C sort)

[ "${#MODULE_PATHS[@]}" -eq 1 ] || {
  printf '.gitmodules paths:\n' >&2
  printf '  %s\n' "${MODULE_PATHS[@]:-<none>}" >&2
  fail "expected exactly one .gitmodules path: $EXPECTED"
}

[ "${MODULE_PATHS[0]}" = "$EXPECTED" ] || fail "unexpected .gitmodules path: ${MODULE_PATHS[0]}"

echo "GITLINK-TOPOLOGY-OK path=$EXPECTED"
