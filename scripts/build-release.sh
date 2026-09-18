#!/usr/bin/env bash
# Build and stage a distributable native-image release bundle.
# Usage: bash scripts/build-release.sh <version>
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
VERSION=${1:?usage: build-release.sh VERSION}
MYLISP=${MYLISP:-$REPO/external/my-lisp}
EXPECTED_VERSION=$(tr -d "\r\n " < "$REPO/VERSION")
case "$VERSION" in
  "$EXPECTED_VERSION") ;;
  *-dev) ;;
  *) echo "release version mismatch: VERSION=$EXPECTED_VERSION requested=$VERSION" >&2; exit 1 ;;
esac

if [[ "${RUNNER_OS:-}" == "Windows" || "$(uname -s 2>/dev/null || true)" =~ ^(MINGW|MSYS|CYGWIN) ]]; then
  PLATFORM="windows-x64"
  BINARY="native-wsm.exe"
else
  PLATFORM="linux-x64"
  BINARY="native-wsm"
fi

bash "$REPO/scripts/build-native.sh"

[ -f "$REPO/$BINARY" ] || { echo "missing native image: $REPO/$BINARY" >&2; exit 1; }

OUT="${OUT_DIR:-$REPO/dist/$PLATFORM}"
rm -rf "$OUT"
mkdir -p "$OUT/bin" "$OUT/authority"

cp "$REPO/$BINARY" "$OUT/bin/"

# The release carries the exact pinned Lisp inputs required by the consumer.
# These files are bundled inputs, not a second semantic authority.
cp "$REPO/README.md" "$REPO/CHANGELOG.md" "$REPO/LICENSE" "$REPO/VERSION" "$REPO/refs/RELEASE-v0.1.0.lisp" "$REPO/refs/lisp-dependency-manifest.lisp" "$OUT/"

while IFS= read -r rel; do
  [ -n "$rel" ] || continue
  case "$rel" in
    *\**)
      echo "wildcard path is not release-materializable: $rel" >&2
      exit 1
      ;;
  esac
  src="$MYLISP/$rel"
  [ -f "$src" ] || { echo "missing pinned authority path: $rel" >&2; exit 1; }
  mkdir -p "$OUT/authority/$(dirname "$rel")"
  cp "$src" "$OUT/authority/$rel"
done < "$REPO/refs/sparse-authority-paths.txt"

if [ "$PLATFORM" = "windows-x64" ]; then
  cp "$REPO/release/bin/wsm-graalvm.cmd" "$OUT/bin/wsm-graalvm.cmd"
else
  cp "$REPO/release/bin/wsm-graalvm.sh" "$OUT/bin/wsm-graalvm"
  chmod +x "$OUT/bin/wsm-graalvm"
fi

cat > "$OUT/BUILD-METADATA.txt" <<EOF
wsm-graalvm release v$VERSION
platform=$PLATFORM
graalvm=${GRAALVM_VERSION:-25.3.4.1}
my-lisp-pin=$(git -C "$MYLISP" rev-parse HEAD)
native-binary=$BINARY
EOF

echo "RELEASE-STAGE-OK $OUT"
