#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?usage: package-rpm.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
PAYLOAD=${2:?usage: package-rpm.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
OUT=${3:?usage: package-rpm.sh VERSION PAYLOAD_DIR OUTPUT_DIR}
PKG_VERSION=${VERSION#v}
command -v rpmbuild >/dev/null || { echo "rpmbuild is required" >&2; exit 1; }

ROOT=$(mktemp -d)
trap 'rm -rf "$ROOT"' EXIT
mkdir -p "$ROOT"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
mkdir -p "$ROOT/SOURCES/wsm-graalvm-$PKG_VERSION"
cp -R "$PAYLOAD/." "$ROOT/SOURCES/wsm-graalvm-$PKG_VERSION/"

cat > "$ROOT/SPECS/wsm-graalvm.spec" <<EOF
Name:           wsm-graalvm
Version:        $PKG_VERSION
Release:        1
Summary:        WSM Lisp runtime on GraalVM
License:        WSM-VOLNIST
BuildArch:      x86_64
%description
Native WSM runtime executing the pinned my-lisp semantic authority.

%prep
mkdir -p %{_builddir}/wsm-graalvm-$PKG_VERSION
cp -a %{_sourcedir}/wsm-graalvm-$PKG_VERSION/. %{_builddir}/wsm-graalvm-$PKG_VERSION/

%build

%install
rm -rf %{buildroot}
mkdir -p %{buildroot}/usr
cp -a %{_builddir}/wsm-graalvm-$PKG_VERSION/. %{buildroot}/usr/

%files
/usr/bin/wsm
/usr/lib/wsm-graalvm
/usr/share/doc/wsm-graalvm
EOF

rpmbuild --define "_topdir $ROOT" -bb "$ROOT/SPECS/wsm-graalvm.spec"
cp "$ROOT/RPMS/x86_64/"*.rpm "$OUT/"
echo "RPM-OK"
