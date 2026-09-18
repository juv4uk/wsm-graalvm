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

[ -x "$REPO/native-wsm" ] || { echo "missing native-wsm" >&2; exit 1; }
[ -d "$MYLISP" ] || { echo "missing pinned my-lisp checkout" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/bin" "$OUT/lib/wsm-graalvm" "$OUT/share/doc/wsm-graalvm"

cp "$REPO/native-wsm" "$OUT/lib/wsm-graalvm/wsm"
cp -R "$MYLISP" "$OUT/lib/wsm-graalvm/my-lisp"

GRAAL_VERSION=${GRAALVM_VERSION:-unknown}
GRAALVM_VERSION="$GRAAL_VERSION" bash "$REPO/scripts/write-release-metadata.sh" "$VERSION" "$OUT/share/doc/wsm-graalvm" "$PLATFORM"

cat > "$OUT/bin/wsm" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
SELF="$(cd "$(dirname "$0")/.." && pwd)"
exec "$SELF/lib/wsm-graalvm/wsm" "$@"
EOF
chmod +x "$OUT/bin/wsm"

cp "$REPO/LICENSE" "$OUT/share/doc/wsm-graalvm/LICENSE" 2>/dev/null || true
cp "$REPO/README.md" "$OUT/share/doc/wsm-graalvm/README.md" 2>/dev/null || true

echo "RELEASE-PAYLOAD-OK $VERSION $PLATFORM"
