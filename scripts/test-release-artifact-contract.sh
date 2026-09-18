#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MANIFEST="$REPO/refs/lisp-dependency-manifest.lisp"
SPARSE="$REPO/refs/sparse-authority-paths.txt"
MYLISP="$REPO/external/my-lisp"

for script in scripts/stage-release-payload.sh scripts/package-deb.sh scripts/package-rpm.sh scripts/write-release-metadata.sh; do
  bash -n "$REPO/$script"
done

PIN=$(git -C "$REPO" ls-tree HEAD external/my-lisp | awk '{print $3}')
HEAD=$(git -C "$MYLISP" rev-parse HEAD)
[ -n "$PIN" ] && [ "$PIN" = "$HEAD" ] || {
  echo "FAIL-CLOSED: gitlink/submodule mismatch: $PIN != $HEAD" >&2
  exit 1
}

grep -Fq "(pin . \"$PIN\")" "$MANIFEST" || {
  echo "FAIL-CLOSED: dependency manifest does not name gitlink pin $PIN" >&2
  exit 1
}

count=0
while IFS= read -r path; do
  [ -n "$path" ] || continue
  case "$path" in \#*) continue ;; esac
  [ -f "$MYLISP/$path" ] || {
    echo "FAIL-CLOSED: declared sparse authority path missing: $path" >&2
    exit 1
  }
  count=$((count + 1))
done < "$SPARSE"
[ "$count" -gt 0 ] || { echo "FAIL-CLOSED: empty sparse authority manifest" >&2; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
GRAALVM_VERSION=${GRAALVM_VERSION:-25.3.4.1}   bash "$REPO/scripts/write-release-metadata.sh" v0.1.0 "$TMP/release" linux-x86_64

grep -Fq "my-lisp-commit: $PIN" "$TMP/release/RELEASE.txt"
grep -Fq "semantic-authority: external/my-lisp@$PIN" "$TMP/release/RELEASE.txt"
grep -Fqx "$PIN" "$TMP/release/MY_LISP_PIN.txt"

grep -Fq 'Package: wsm-graalvm' "$REPO/scripts/package-deb.sh"
grep -Fq 'License:        WSM-VOLNIST' "$REPO/scripts/package-rpm.sh"
grep -Fq 'bootstrap: canon -> macro -> core -> user Lisp' "$TMP/release/RELEASE.txt"

echo "RELEASE-ARTIFACT-CONTRACT-OK pin=$PIN sparse_paths=$count"
