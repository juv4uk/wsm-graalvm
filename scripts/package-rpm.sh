#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?usage: package-rpm.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
PAYLOAD=${2:?usage: package-rpm.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
OUT=${3:?usage: package-rpm.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
PKG_VERSION=$(printf "%s" "$VERSION" | sed "s/^v//")

command -v rpmbuild >/dev/null || { echo "rpmbuild is required" >&2; exit 1; }
ROOT=$(mktemp -d)
trap 'rm -rf "$ROOT"' EXIT
mkdir -p "$ROOT"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
tar -C "$PAYLOAD" -czf "$ROOT/SOURCES/wsm-graalvm-$PKG_VERSION.tar.gz" .

cat > "$ROOT/SPECS/wsm-graalvm.spec" <<EOF
Name:           wsm-graalvm
Version:        $PKG_VERSION
Release:        1
Summary:        WSM Lisp runtime on GraalVM
License:        LicenseRef-VOLNIST
BuildArch:      x86_64
Source0:        wsm-graalvm-$PKG_VERSION.tar.gz

%description
Native WSM runtime executing the exact pinned my-lisp semantic authority.

%prep
mkdir -p %{_builddir}/wsm-graalvm-$PKG_VERSION
tar -xzf %{_sourcedir}/wsm-graalvm-$PKG_VERSION.tar.gz -C %{_builddir}/wsm-graalvm-$PKG_VERSION

%build

%install
rm -rf %{buildroot}
mkdir -p %{buildroot}/usr/lib/wsm-graalvm/$PKG_VERSION %{buildroot}/usr/bin %{buildroot}/usr/share/doc/wsm-graalvm
cp -a %{_builddir}/wsm-graalvm-$PKG_VERSION/. %{buildroot}/usr/lib/wsm-graalvm/$PKG_VERSION/
printf "%s\n" "#!/bin/sh" "exec /usr/lib/wsm-graalvm/$PKG_VERSION/run.sh \"\$@\"" > %{buildroot}/usr/bin/wsm
chmod 0755 %{buildroot}/usr/bin/wsm
cp %{buildroot}/usr/lib/wsm-graalvm/$PKG_VERSION/RELEASE.txt %{buildroot}/usr/share/doc/wsm-graalvm/RELEASE.txt
cp %{buildroot}/usr/lib/wsm-graalvm/$PKG_VERSION/RELEASE.json %{buildroot}/usr/share/doc/wsm-graalvm/RELEASE.json
cp %{buildroot}/usr/lib/wsm-graalvm/$PKG_VERSION/LICENSE %{buildroot}/usr/share/doc/wsm-graalvm/LICENSE

%files
/usr/bin/wsm
/usr/lib/wsm-graalvm
/usr/share/doc/wsm-graalvm
EOF

mkdir -p "$OUT"
rpmbuild --define "_topdir $ROOT" -bb "$ROOT/SPECS/wsm-graalvm.spec"
cp "$ROOT/RPMS/x86_64/"*.rpm "$OUT/wsm-graalvm-$VERSION-linux-x86_64.rpm"
echo "RPM-OK $OUT/wsm-graalvm-$VERSION-linux-x86_64.rpm"
