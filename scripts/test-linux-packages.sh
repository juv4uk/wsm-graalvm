#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
VERSION=$1
PAYLOAD=$2
DEB=$3
RPM=$4
[ "$#" -eq 4 ] || { echo "usage: test-linux-packages.sh VERSION PAYLOAD DEB RPM" >&2; exit 2; }

command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }
command -v dpkg-deb >/dev/null || { echo "dpkg-deb is required" >&2; exit 1; }
command -v rpmbuild >/dev/null || { echo "rpmbuild is required" >&2; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
bash "$REPO/scripts/package-deb.sh" v0.0.9 "$PAYLOAD" "$TMP/deb-old"
bash "$REPO/scripts/package-rpm.sh" v0.0.9 "$PAYLOAD" "$TMP/rpm-old"

printf '(quote 42)\n' > "$TMP/smoke.lisp"

docker run --rm \
  -v "$TMP/deb-old/wsm-graalvm-0.0.9-linux-x86_64.deb:/tmp/old.deb:ro" \
  -v "$DEB:/tmp/new.deb:ro" \
  -v "$TMP/smoke.lisp:/tmp/smoke.lisp:ro" \
  debian:bookworm-slim bash -euxo pipefail -c '
    dpkg -i /tmp/old.deb
    dpkg -i /tmp/new.deb
    test "$(dpkg-query -W -f=\${Version} wsm-graalvm)" != "0.0.9"
    /usr/bin/wsm /tmp/smoke.lisp | grep -q "42"
    dpkg -r wsm-graalvm
    test ! -e /usr/bin/wsm
  '

docker run --rm \
  -v "$TMP/rpm-old/wsm-graalvm-0.0.9-linux-x86_64.rpm:/tmp/old.rpm:ro" \
  -v "$RPM:/tmp/new.rpm:ro" \
  -v "$TMP/smoke.lisp:/tmp/smoke.lisp:ro" \
  fedora:latest bash -euxo pipefail -c '
    rpm -Uvh /tmp/old.rpm
    rpm -Uvh /tmp/new.rpm
    test "$(rpm -q --qf "%{VERSION}" wsm-graalvm)" != "0.0.9"
    /usr/bin/wsm /tmp/smoke.lisp | grep -q "42"
    dnf remove -y wsm-graalvm
    test ! -e /usr/bin/wsm
  '

echo "LINUX-PACKAGE-LIFECYCLE-OK"
