#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
VERSION=$1
OUT=$2
PLATFORM=$3
[ "$#" -eq 3 ] || { echo "usage: write-release-metadata.sh VERSION OUTPUT_DIR PLATFORM" >&2; exit 2; }

RELEASE_VERSION=$(printf '%s' "$VERSION" | sed 's/^v//')
case "$RELEASE_VERSION" in
  [0-9]*.[0-9]*.[0-9]*) ;;
  *) echo "invalid release version: $VERSION" >&2; exit 1 ;;
esac

PIN=$(git -C "$REPO" ls-tree HEAD external/my-lisp | awk '{print $3}')
HEAD=$(git -C "$REPO/external/my-lisp" rev-parse HEAD)
[ -n "$PIN" ] && [ "$PIN" = "$HEAD" ] || {
  echo "FAIL-CLOSED: my-lisp HEAD $HEAD differs from gitlink $PIN" >&2
  exit 1
}
MANIFEST_PIN=$(sed -n 's/.*(pin \. "\([^"]*\)").*/\1/p' "$REPO/refs/lisp-dependency-manifest.lisp" | head -n1)
[ "$MANIFEST_PIN" = "$PIN" ] || {
  echo "FAIL-CLOSED: manifest pin $MANIFEST_PIN differs from gitlink $PIN" >&2
  exit 1
}

mkdir -p "$OUT"
cat > "$OUT/RELEASE.txt" <<EOF
WSM/GraalVM Release
===================
version: v$RELEASE_VERSION
wsm-commit: $(git -C "$REPO" rev-parse HEAD)
my-lisp-commit: $PIN
platform: $PLATFORM
graalvm: ${GRAALVM_VERSION:-unknown}
semantic-authority: external/my-lisp@$PIN
bootstrap: canon -> macro -> core -> user Lisp
EOF
printf '%s\n' "$PIN" > "$OUT/MY_LISP_PIN.txt"

cat > "$OUT/RELEASE.json" <<EOF
{
  "version": "v$RELEASE_VERSION",
  "wsm_commit": "$(git -C "$REPO" rev-parse HEAD)",
  "my_lisp_commit": "$PIN",
  "platform": "$PLATFORM",
  "graalvm": "${GRAALVM_VERSION:-unknown}",
  "semantic_authority": "external/my-lisp@$PIN",
  "bootstrap": ["canon", "macro", "core", "user-lisp"]
}
EOF
