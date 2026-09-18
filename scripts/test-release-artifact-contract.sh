#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MANIFEST="$REPO/refs/lisp-dependency-manifest.lisp"
MYLISP="$REPO/external/my-lisp"

for script in scripts/stage-release-payload.sh scripts/package-deb.sh scripts/package-rpm.sh scripts/write-release-metadata.sh; do
  bash -n "$REPO/$script"
done

PIN=$(git -C "$REPO" ls-tree HEAD external/my-lisp | awk '{print $3}')
HEAD=$(git -C "$MYLISP" rev-parse HEAD)
MODE=$(git -C "$REPO" ls-tree HEAD external/my-lisp | awk '{print $1}')
[ "$MODE" = "160000" ] || { echo "FAIL-CLOSED: external/my-lisp is not a gitlink: $MODE" >&2; exit 1; }
[ -n "$PIN" ] && [ "$PIN" = "$HEAD" ] || {
  echo "FAIL-CLOSED: gitlink/submodule mismatch: $PIN != $HEAD" >&2
  exit 1
}

grep -Fq "(pin . \"$PIN\")" "$MANIFEST" || {
  echo "FAIL-CLOSED: dependency manifest does not name gitlink pin $PIN" >&2
  exit 1
}

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
GRAALVM_VERSION=${GRAALVM_VERSION:-25.3.4.1} bash "$REPO/scripts/write-release-metadata.sh" v0.1.0 "$TMP/release" linux-x86_64

grep -Fq "my-lisp-commit: $PIN" "$TMP/release/RELEASE.txt"
grep -Fq "semantic-authority: external/my-lisp@$PIN" "$TMP/release/RELEASE.txt"
grep -Fqx "$PIN" "$TMP/release/MY_LISP_PIN.txt"
grep -Fq 'Package: wsm-graalvm' "$REPO/scripts/package-deb.sh"
grep -Fq 'License:        WSM-VOLNIST' "$REPO/scripts/package-rpm.sh"
grep -Fq 'bootstrap: canon -> macro -> core -> user Lisp' "$TMP/release/RELEASE.txt"

printf '#!/bin/sh\nexit 0\n' > "$TMP/native-wsm"
chmod +x "$TMP/native-wsm"
bash "$REPO/scripts/stage-release-payload.sh" v0.1.0 linux-x86_64 "$TMP/native-wsm" "$TMP/payload"

RUNTIME="$TMP/payload/lib/wsm-graalvm/0.1.0"
test -x "$TMP/payload/bin/wsm"
bash -n "$TMP/payload/bin/wsm"
test -x "$RUNTIME/native-wsm"
test -f "$RUNTIME/my-lisp/lib/canon.lisp"
test -f "$RUNTIME/my-lisp/lib/macro.lisp"
test -f "$RUNTIME/my-lisp/lib/core.lisp"
test -f "$RUNTIME/my-lisp/lib/surface/semantic-registry.lisp"
! find "$RUNTIME/my-lisp" -name .git -print -quit | grep -q .
EXPECTED=$(git -C "$MYLISP" ls-files | LC_ALL=C sort)
ACTUAL=$(find "$RUNTIME/my-lisp" \( -type f -o -type l \) ! -name .git -printf '%P\n' | LC_ALL=C sort)
diff -u <(printf '%s\n' "$EXPECTED") <(printf '%s\n' "$ACTUAL")

echo "RELEASE-ARTIFACT-CONTRACT-OK pin=$PIN full_mylisp_files=$(printf '%s\n' "$ACTUAL" | wc -l)"
