#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
VERSION=${1:?usage: package-deb.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
PAYLOAD=${2:?usage: package-deb.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
OUT=${3:?usage: package-deb.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
PKG_VERSION=${VERSION#v}

command -v dpkg-deb >/dev/null || { echo "dpkg-deb is required" >&2; exit 1; }
rm -rf "$OUT"
mkdir -p "$OUT/wsm-graalvm-$PKG_VERSION/DEBIAN"

cat > "$OUT/wsm-graalvm-$PKG_VERSION/DEBIAN/control" <<EOF
Package: wsm-graalvm
Version: $PKG_VERSION
Section: devel
Priority: optional
Architecture: amd64
Maintainer: WSM Project
Description: WSM Lisp runtime on GraalVM
 Native GraalVM substrate executing the pinned my-lisp semantic authority.
EOF

mkdir -p "$OUT/wsm-graalvm-$PKG_VERSION/usr"
cp -R "$PAYLOAD/." "$OUT/wsm-graalvm-$PKG_VERSION/usr/"

dpkg-deb --root-owner-group --build "$OUT/wsm-graalvm-$PKG_VERSION" "$OUT/wsm-graalvm-$PKG_VERSION-amd64.deb"
echo "DEB-OK $OUT/wsm-graalvm-$PKG_VERSION-amd64.deb"
