#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/upstream/lib/surface"
for path in lib/surface/semantic-registry.lisp lib/canon.lisp lib/macro.lisp lib/core.lisp; do
  mkdir -p "$TMP/upstream/$(dirname "$path")"
  printf '; release packager smoke fixture\n' > "$TMP/upstream/$path"
done
printf 'fake-native-binary\n' > "$TMP/native"

run_case() {
  local platform="$1"
  local dist="$TMP/$platform/dist"
  python "$REPO/scripts/package-release.py" \
    --version 9.9.9 \
    --platform "$platform" \
    --binary "$TMP/native" \
    --upstream "$TMP/upstream" \
    --upstream-pin 0123456789012345678901234567890123456789 \
    --repo-sha fedcba9876543210fedcba9876543210fedcba98 \
    --out "$dist"

  unzip -tq "$dist/"*.zip
  unzip -p "$dist/"*.zip MY_LISP_PIN.txt | grep -qx 0123456789012345678901234567890123456789
  unzip -p "$dist/"*.zip run.sh | grep -q "my-lisp/lib/canon.lisp"
  unzip -p "$dist/"*.zip run.sh | grep -q "my-lisp/lib/macro.lisp"
  unzip -p "$dist/"*.zip run.sh | grep -q "my-lisp/lib/core.lisp"
  unzip -p "$dist/"*.zip run.cmd | grep -q "my-lisp\\\\lib\\\\canon.lisp"
  unzip -p "$dist/"*.zip run.cmd | grep -q "my-lisp\\\\lib\\\\macro.lisp"
  unzip -p "$dist/"*.zip run.cmd | grep -q "my-lisp\\\\lib\\\\core.lisp"
  unzip -p "$dist/"*.zip wsm.cmd | grep -q "run.cmd"

  local first second
  first=$(sha256sum "$dist/"*.zip | cut -d" " -f1)
  rm -rf "$dist"
  mkdir -p "$dist"
  python "$REPO/scripts/package-release.py" \
    --version 9.9.9 \
    --platform "$platform" \
    --binary "$TMP/native" \
    --upstream "$TMP/upstream" \
    --upstream-pin 0123456789012345678901234567890123456789 \
    --repo-sha fedcba9876543210fedcba9876543210fedcba98 \
    --out "$dist"
  second=$(sha256sum "$dist/"*.zip | cut -d" " -f1)
  test "$first" = "$second"
}

run_case linux-x86_64
run_case windows-x86_64
echo "PACKAGE-RELEASE-SMOKE-OK"