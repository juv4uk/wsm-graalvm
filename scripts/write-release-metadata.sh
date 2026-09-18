#!/usr/bin/env bash
set -euo pipefail

# Write immutable release provenance into a package staging directory.
# Usage: write-release-metadata.sh <version> <output-dir> [platform]
REPO=$(cd "$(dirname "$0")/.." && pwd)
VERSION=${1:?usage: write-release-metadata.sh VERSION OUTPUT_DIR [PLATFORM]}
OUT=${2:?usage: write-release-metadata.sh VERSION OUTPUT_DIR [PLATFORM]}
PLATFORM=${3:-unknown}

mkdir -p "$OUT"

WSM_COMMIT=$(git -C "$REPO" rev-parse HEAD)
MYLISP_PIN=$(git -C "$REPO" ls-tree HEAD external/my-lisp | awk '{print $3}')
MYLISP_HEAD=$(git -C "$REPO/external/my-lisp" rev-parse HEAD 2>/dev/null || true)
GRAAL_VERSION=${GRAALVM_VERSION:-unknown}

[ -n "$MYLISP_PIN" ] || { echo "missing external/my-lisp gitlink in WSM commit" >&2; exit 1; }
[ -n "$MYLISP_HEAD" ] || { echo "cannot resolve external/my-lisp HEAD" >&2; exit 1; }
[ "$MYLISP_HEAD" = "$MYLISP_PIN" ] || {
  echo "FAIL-CLOSED: my-lisp HEAD $MYLISP_HEAD differs from gitlink pin $MYLISP_PIN" >&2
  exit 1
}

RELEASE_VERSION="${VERSION#v}"
case "$RELEASE_VERSION" in
  [0-9]*.[0-9]*.[0-9]*) ;;
  *) echo "invalid release version: $VERSION" >&2; exit 1 ;;
esac

cat > "$OUT/RELEASE.txt" <<EOF
WSM/GraalVM Release
===================
version: v$RELEASE_VERSION
wsm-commit: $WSM_COMMIT
my-lisp-commit: $MYLISP_PIN
platform: $PLATFORM
graalvm: $GRAAL_VERSION
semantic-authority: external/my-lisp@$MYLISP_PIN
bootstrap: canon -> macro -> core -> user Lisp
EOF

cat > "$OUT/RELEASE.json" <<EOF
{
  "version": "$VERSION",
  "wsm_commit": "$WSM_COMMIT",
  "my_lisp_commit": "$MYLISP_PIN",
  "platform": "$PLATFORM",
  "graalvm": "$GRAAL_VERSION",
  "semantic_authority": "external/my-lisp@$MYLISP_PIN",
  "bootstrap": ["canon", "macro", "core", "user-lisp"]
}
EOF

printf '%s\n' "$MYLISP_PIN" > "$OUT/MY_LISP_PIN.txt"
echo "RELEASE-METADATA-OK v$RELEASE_VERSION $MYLISP_PIN"
