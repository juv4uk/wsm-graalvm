#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
VERSION=${1:?usage: stage-release.sh <version> <output-dir>}
OUT=${2:?usage: stage-release.sh <version> <output-dir>}

MYLISP="${MYLISP:-$REPO/external/my-lisp}"
BINARY="$REPO/native-wsm"
[ -f "$BINARY" ] || BINARY="$REPO/native-wsm.exe"
[ -f "$BINARY" ] || { echo "native-wsm binary not found" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/bin" "$OUT/authority"

cp "$BINARY" "$OUT/bin/"
cp "$REPO/README.md" "$REPO/CHANGELOG.md" "$REPO/LICENSE" "$REPO/VERSION" "$REPO/refs/RELEASE-v0.1.0.lisp" "$REPO/refs/lisp-dependency-manifest.lisp" "$OUT/"
cp "$REPO/release/bin/wsm-graalvm.sh" "$OUT/bin/wsm-graalvm.sh"
cp "$REPO/release/bin/wsm-graalvm.cmd" "$OUT/bin/wsm-graalvm.cmd"

while IFS= read -r rel; do
  [ -n "$rel" ] || continue
  case "$rel" in
    */**)
      # The release manifest currently admits concrete files only.
      echo "unsupported wildcard release path: $rel" >&2
      exit 1
      ;;
  esac
  src="$MYLISP/$rel"
  [ -f "$src" ] || { echo "missing pinned authority path: $rel" >&2; exit 1; }
  mkdir -p "$OUT/authority/$(dirname "$rel")"
  cp "$src" "$OUT/authority/$rel"
done < "$REPO/refs/sparse-authority-paths.txt"

cat > "$OUT/BUILD-METADATA.txt" <<EOF
wsm-graalvm release v$VERSION
platform=${RUNNER_OS:-unknown}
architecture=x64
graalvm=${GRAALVM_VERSION:-25.3.4.1}
my-lisp-pin=$(git -C "$MYLISP" rev-parse HEAD)
binary=$(basename "$BINARY")
EOF

chmod +x "$OUT/bin/wsm-graalvm.sh" 2>/dev/null || true
echo "RELEASE-STAGE-OK $OUT"
