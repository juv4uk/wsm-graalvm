#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/upstream/lib/surface"
for path in   "lib/surface/semantic-registry.lisp"   "lib/canon.lisp"   "lib/macro.lisp"   "lib/core.lisp"; do
  mkdir -p "$TMP/upstream/$(dirname "$path")"
  printf '; release packager smoke fixture\n' > "$TMP/upstream/$path"
done

printf 'not-a-real-native-binary\n' > "$TMP/fake-binary"

run_case() {
  local platform=$1
  local binary=$2
  local first second
  mkdir -p "$TMP/$platform"
  python "$REPO/scripts/package-release.py"     --version 9.9.9     --platform "$platform"     --binary "$binary"     --upstream "$TMP/upstream"     --upstream-pin "0123456789012345678901234567890123456789"     --repo-sha "fedcba9876543210fedcba9876543210fedcba98"     --out "$TMP/$platform/dist"

  if [ "$platform" = "linux-x86_64" ]; then
    test -f "$TMP/$platform/dist/assets/wsm-graalvm-9.9.9-linux-x86_64"
    test -f "$TMP/$platform/dist/wsm-graalvm-9.9.9-linux-x86_64.zip"
    test -f "$TMP/$platform/dist/wsm-graalvm-9.9.9-linux-x86_64.binary.sha256"
  else
    test -f "$TMP/$platform/dist/assets/wsm-graalvm-9.9.9-windows-x86_64.exe"
    test -f "$TMP/$platform/dist/wsm-graalvm-9.9.9-windows-x86_64.zip"
    test -f "$TMP/$platform/dist/wsm-graalvm-9.9.9-windows-x86_64.binary.sha256"
  fi

  first=$(sha256sum "$TMP/$platform/dist/"*.zip | cut -d' ' -f1)
  unzip -tq "$TMP/$platform/dist/"*.zip
  unzip -p "$TMP/$platform/dist/"*.zip "MY_LISP_PIN.txt" | grep -qx "0123456789012345678901234567890123456789"
  rm -rf "$TMP/$platform/dist"
  mkdir -p "$TMP/$platform/dist"
  python "$REPO/scripts/package-release.py"     --version 9.9.9     --platform "$platform"     --binary "$binary"     --upstream "$TMP/upstream"     --upstream-pin "0123456789012345678901234567890123456789"     --repo-sha "fedcba9876543210fedcba9876543210fedcba98"     --out "$TMP/$platform/dist"
  second=$(sha256sum "$TMP/$platform/dist/"*.zip | cut -d' ' -f1)
  test "$first" = "$second"
}

run_case linux-x86_64 "$TMP/fake-binary"
run_case windows-x86_64 "$TMP/fake-binary"

echo "PACKAGE-RELEASE-SMOKE-OK"
