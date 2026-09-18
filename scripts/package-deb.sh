#!/usr/bin/env bash
set -euo pipefail

VERSION=$1
PAYLOAD=$2
OUT=$3
[ "$#" -eq 3 ] || { echo "usage: package-deb.sh VERSION PAYLOAD_DIR OUTPUT_DIR" >&2; exit 2; }
PKG_VERSION=$(printf '%s' "$VERSION" | sed 's/^v//')
case "$PKG_VERSION" in [0-9]*.[0-9]*.[0-9]*) ;; *) echo "invalid version" >&2; exit 1 ;; esac
command -v dpkg-deb >/dev/null || { echo "dpkg-deb is required" >&2; exit 1; }

ROOT="$OUT/root"
rm -rf "$OUT"
mkdir -p "$ROOT/DEBIAN" "$ROOT/usr"
cp -R "$PAYLOAD/." "$ROOT/usr/"

cat > "$ROOT/DEBIAN/control" <<EOF
Package: wsm-graalvm
Version: $PKG_VERSION
Section: devel
Priority: optional
Architecture: amd64
Maintainer: WSM Project
Depends: bash
Description: WSM Lisp runtime on GraalVM
 Native WSM runtime executing the pinned my-lisp semantic authority.
EOF

dpkg-deb --root-owner-group --build "$ROOT" "$OUT/wsm-graalvm-$PKG_VERSION-linux-x86_64.deb"
echo "DEB-OK $OUT/wsm-graalvm-$PKG_VERSION-linux-x86_64.deb"
