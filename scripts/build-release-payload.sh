#!/usr/bin/env bash
set -euo pipefail

# Build the common release staging payload. Platform-specific package builders
# consume this directory; they must not rebuild or mutate the payload.
REPO=$(cd "$(dirname "$0")/.." && pwd)
VERSION=${1:?usage: build-release-payload.sh VERSION OUTPUT_DIR [PLATFORM]}
OUT=${2:?usage: build-release-payload.sh VERSION OUTPUT_DIR [PLATFORM]}
PLATFORM=${3:-linux-x86_64}
MYLISP=${MYLISP:-$REPO/external/my-lisp}

bash "$REPO/scripts/build-native.sh"

if [[ "$PLATFORM" == windows-* ]]; then
  BINARY="$REPO/native-wsm.exe"
  [ -f "$BINARY" ] || { echo "missing native-wsm.exe" >&2; exit 1; }
else
  BINARY="$REPO/native-wsm"
  [ -x "$BINARY" ] || { echo "missing native-wsm" >&2; exit 1; }
fi
[ -d "$MYLISP" ] || { echo "missing pinned my-lisp checkout" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/bin" "$OUT/lib/wsm-graalvm/$VERSION" "$OUT/share/doc/wsm-graalvm"

cp "$BINARY" "$OUT/lib/wsm-graalvm/$VERSION/"
AUTHORITY="$OUT/lib/wsm-graalvm/$VERSION/my-lisp"
mkdir -p "$AUTHORITY"

# Package only the declared sparse authority slice; never copy the full
# upstream checkout into a distributable artifact.
while IFS= read -r path; do
  [ -n "$path" ] || continue
  case "$path" in #*) continue ;; esac
  src="$MYLISP/$path"
  [ -f "$src" ] || { echo "missing declared authority path: $path" >&2; exit 1; }
  mkdir -p "$AUTHORITY/$(dirname "$path")"
  cp "$src" "$AUTHORITY/$path"
done < "$REPO/refs/sparse-authority-paths.txt"

GRAAL_VERSION=${GRAALVM_VERSION:-unknown}
GRAALVM_VERSION="$GRAAL_VERSION" bash "$REPO/scripts/write-release-metadata.sh" "$VERSION" "$OUT/share/doc/wsm-graalvm" "$PLATFORM"

cat > "$OUT/bin/wsm" <<EOF
#!/usr/bin/env bash
set -euo pipefail
SELF="$(cd "$(dirname "$0")/.." && pwd)"
exec "$SELF/lib/wsm-graalvm/$VERSION/$(basename "$BINARY")" "$@"
EOF
chmod +x "$OUT/bin/wsm"

cp "$REPO/LICENSE" "$OUT/share/doc/wsm-graalvm/LICENSE"
cp "$REPO/README.md" "$OUT/share/doc/wsm-graalvm/README.md" 2>/dev/null || true

echo "RELEASE-PAYLOAD-OK $VERSION $PLATFORM"
