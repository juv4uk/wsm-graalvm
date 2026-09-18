#!/usr/bin/env bash
# Bootstrap + fail-closed refresh of the approved sparse my-lisp authority checkout.
#
# Owner rule:
#   external/my-lisp is a pinned Git submodule. Its working tree exposes only
#   the Lisp authority/runtime/witness paths derived from
#   refs/lisp-dependency-manifest.lisp.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
E="$REPO/external/my-lisp"
ALLOWLIST="$REPO/refs/sparse-authority-paths.txt"
GENERATOR="$REPO/scripts/build-sparse-authority-allowlist.sh"

fail() {
  echo "sync-authority FAIL-CLOSED: $*" >&2
  exit 1
}

[ -f "$ALLOWLIST" ] || fail "missing sparse allowlist: $ALLOWLIST"
[ -x "$GENERATOR" ] || fail "missing sparse allowlist generator: $GENERATOR"

GENERATED=$(mktemp)
EXPECTED=$(mktemp)
ACTUAL=$(mktemp)
trap 'rm -f "$GENERATED" "$EXPECTED" "$ACTUAL"' EXIT

bash "$GENERATOR" > "$GENERATED"
cmp -s "$GENERATED" "$ALLOWLIST"   || {
    echo "Generated sparse allowlist differs from committed allowlist:" >&2
    diff -u "$ALLOWLIST" "$GENERATED" >&2 || true
    fail "refs/sparse-authority-paths.txt is stale"
  }

mapfile -t FILES < <(sed '/^[[:space:]]*$/d' "$ALLOWLIST")
[ "${#FILES[@]}" -gt 0 ] || fail "sparse allowlist is empty"

for f in "${FILES[@]}"; do
  case "$f" in
    /*|../*|*/../*|*/..)
      fail "unsafe sparse path: $f"
      ;;
    *.lisp)
      ;;
    *)
      fail "non-Lisp path admitted by sparse allowlist: $f"
      ;;
  esac
done

[ -d "$E" ] || fail "submodule external/my-lisp missing"
[ -f "$E/.git" ] || fail "submodule not initialized (no .git file)"
[ -n "$(ls -A "$E" 2>/dev/null)" ]   || fail "submodule empty; run: git submodule update --init external/my-lisp"

git -C "$E" fetch origin --quiet   || fail "git fetch inside submodule failed"

PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '{print $2}')
HEAD=$(git -C "$E" rev-parse HEAD)
[ -n "$PIN" ] || fail "cannot read submodule gitlink pin"
[ "$HEAD" = "$PIN" ]   || fail "submodule HEAD $HEAD differs from superproject pin $PIN"

printf '/%s\n' "${FILES[@]}"   | git -C "$E" sparse-checkout set --no-cone --stdin >/dev/null   || fail "cannot enable exact sparse checkout"

git -C "$E" sparse-checkout reapply >/dev/null   || fail "cannot reapply exact sparse checkout"

SC=$(git -C "$E" config --bool core.sparseCheckout || true)
CONE=$(git -C "$E" config --bool core.sparseCheckoutCone || true)
[ "$SC" = "true" ] || fail "sparseCheckout is not enabled"
[ "$CONE" != "true" ] || fail "exact sparse checkout unexpectedly entered cone mode"

for f in "${FILES[@]}"; do
  [ -f "$E/$f" ] || fail "required sparse authority file absent: $f"
done

printf '%s\n' "${FILES[@]}" | LC_ALL=C sort -u > "$EXPECTED"
find "$E" -type f ! -path "$E/.git" -printf '%P\n' | LC_ALL=C sort -u > "$ACTUAL"

if ! cmp -s "$EXPECTED" "$ACTUAL"; then
  echo "Expected sparse materialization:" >&2
  cat "$EXPECTED" >&2
  echo "Actual sparse materialization:" >&2
  cat "$ACTUAL" >&2
  fail "materialized working tree differs from refs/sparse-authority-paths.txt"
fi

echo "Sparse authority checkout OK:"
echo "  pin=$HEAD"
echo "  materialized-lisp-files=${#FILES[@]}"
echo "  allowlist=refs/sparse-authority-paths.txt"
echo "  source=refs/lisp-dependency-manifest.lisp"

for f in "${FILES[@]}"; do
  D=$(sha256sum "$E/$f" | cut -d' ' -f1)
  echo "$f $D"
done
