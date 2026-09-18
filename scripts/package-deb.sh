#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?usage: package-deb.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
PAYLOAD=${2:?usage: package-deb.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
OUT=${3:?usage: package-deb.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
PKG_VERSION=$(printf "%s" "$VERSION" | sed "s/^v//")

command -v dpkg-deb >/dev/null || { echo "dpkg-deb is required" >&2; exit 1; }
rm -rf "$OUT"
mkdir -p "$OUT/pkg/usr/lib/wsm-graalvm/$PKG_VERSION" "$OUT/pkg/usr/bin" "$OUT/pkg/usr/share/doc/wsm-graalvm"
cp -R "$PAYLOAD/." "$OUT/pkg/usr/lib/wsm-graalvm/$PKG_VERSION/"
printf "%s\n" "#!/bin/sh" "exec /usr/lib/wsm-graalvm/$PKG_VERSION/run.sh \"\$@\"" > "$OUT/pkg/usr/bin/wsm"
chmod 0755 "$OUT/pkg/usr/bin/wsm"
cp "$PAYLOAD/RELEASE.txt" "$OUT/pkg/usr/share/doc/wsm-graalvm/RELEASE.txt"
cp "$PAYLOAD/RELEASE.json" "$OUT/pkg/usr/share/doc/wsm-graalvm/RELEASE.json"
cp "$PAYLOAD/LICENSE" "$OUT/pkg/usr/share/doc/wsm-graalvm/LICENSE"
mkdir -p "$OUT/pkg/DEBIAN"
{
  echo "Package: wsm-graalvm"
  echo "Version: $PKG_VERSION"
  echo "Section: devel"
  echo "Priority: optional"
  echo "Architecture: amd64"
  echo "Maintainer: WSM Project"
  echo "Description: WSM Lisp runtime on GraalVM"
  echo " Native GraalVM substrate executing the exact pinned my-lisp semantic authority."
} > "$OUT/pkg/DEBIAN/control"
dpkg-deb --root-owner-group --build "$OUT/pkg" "$OUT/wsm-graalvm-$VERSION-linux-x86_64.deb"
echo "DEB-OK $OUT/wsm-graalvm-$VERSION-linux-x86_64.deb"