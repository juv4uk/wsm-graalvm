#!/usr/bin/env bash
# Build and stage the distributable native-image release bundle.
# Usage: bash scripts/build-release.sh <version> [native]
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
VERSION=${1:?usage: build-release.sh VERSION [native]}
MODE=${2:-native}
MYLISP=${MYLISP:-$REPO/external/my-lisp}

if [[ "${RUNNER_OS:-}" == "Windows" || "$(uname -s 2>/dev/null || true)" =~ ^(MINGW|MSYS|CYGWIN) ]]; then
  PLATFORM="windows-x64"
  BINARY_SUFFIX=".exe"
else
  PLATFORM="linux-x64"
  BINARY_SUFFIX=""
fi

bash "$REPO/scripts/build.sh"

if [ "$MODE" = "native" ]; then
  MYLISP="$MYLISP" RUNNER_OS="${RUNNER_OS:-}" bash "$REPO/scripts/build-native.sh"
else
  echo "only native release bundles are supported" >&2
  exit 2
fi

STAGE="${STAGE_DIR:-$REPO/.release-stage}"
OUT="${OUT_DIR:-$REPO/dist}"
rm -rf "$STAGE" "$OUT"
mkdir -p "$STAGE/bin" "$STAGE/authority" "$OUT"

BINARY="$REPO/native-wsm$BINARY_SUFFIX"
[ -f "$BINARY" ] || { echo "missing native image: $BINARY" >&2; exit 1; }
cp "$BINARY" "$STAGE/bin/"

# Keep the runtime source input exact and traceable. These are consumer inputs,
# not a second semantic authority.
cp "$REPO/README.md" "$REPO/CHANGELOG.md" "$REPO/LICENSE" "$REPO/VERSION" "$REPO/refs/RELEASE-v0.1.0.lisp" "$REPO/refs/lisp-dependency-manifest.lisp" "$STAGE/"

while IFS= read -r rel; do
  [ -n "$rel" ] || continue
  case "$rel" in
    */**)
      echo "wildcard path is not release-materializable: $rel" >&2
      exit 1
      ;;
  esac
  src="$MYLISP/$rel"
  [ -f "$src" ] || { echo "missing pinned authority path: $rel" >&2; exit 1; }
  mkdir -p "$STAGE/authority/$(dirname "$rel")"
  cp "$src" "$STAGE/authority/$rel"
done < "$REPO/refs/sparse-authority-paths.txt"

if [ "$PLATFORM" = "windows-x64" ]; then
  cp "$REPO/release/bin/wsm-graalvm.cmd" "$STAGE/bin/wsm-graalvm.cmd"
else
  cp "$REPO/release/bin/wsm-graalvm.sh" "$STAGE/bin/wsm-graalvm"
  chmod +x "$STAGE/bin/wsm-graalvm"
fi

cat > "$STAGE/BUILD-METADATA.txt" <<EOF
wsm-graalvm v$VERSION
platform=$PLATFORM
graalvm=${GRAALVM_VERSION:-25.3.4.1}
my-lisp-pin=$(git -C "$MYLISP" rev-parse HEAD)
native-binary=$(basename "$BINARY")
EOF

ARCHIVE_BASE="wsm-graalvm-$VERSION-$PLATFORM"
if [ "$PLATFORM" = "linux-x64" ]; then
  tar -czf "$OUT/$ARCHIVE_BASE.tar.gz" -C "$STAGE" .
  sha256sum "$OUT/$ARCHIVE_BASE.tar.gz" | awk '{print $1}' > "$OUT/$ARCHIVE_BASE.tar.gz.sha256"
else
  powershell.exe -NoProfile -Command \
    "Compress-Archive -Path '$STAGE/*' -DestinationPath '$OUT/$ARCHIVE_BASE.zip' -Force"
  powershell.exe -NoProfile -Command \
    "(Get-FileHash '$OUT/$ARCHIVE_BASE.zip' -Algorithm SHA256).Hash.ToLowerInvariant()" \
    > "$OUT/$ARCHIVE_BASE.zip.sha256"
fi

echo "RELEASE-BUNDLE-OK $OUT/$ARCHIVE_BASE"
