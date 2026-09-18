#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
VERSION=$1
PLATFORM=$2
BINARY=$3
OUT=$4
MYLISP=${MYLISP:-$REPO/external/my-lisp}
RELEASE_VERSION=$(printf '%s' "$VERSION" | sed 's/^v//')

[ "$#" -eq 4 ] || { echo "usage: stage-release-payload.sh VERSION PLATFORM NATIVE_BINARY OUTPUT_DIR" >&2; exit 2; }
case "$VERSION" in
  v[0-9]*.[0-9]*.[0-9]*) ;;
  *) echo "invalid release version: $VERSION" >&2; exit 1 ;;
esac
case "$PLATFORM" in
  linux-x86_64|windows-x86_64) ;;
  *) echo "unsupported platform: $PLATFORM" >&2; exit 1 ;;
esac

[ -f "$BINARY" ] || { echo "native binary missing: $BINARY" >&2; exit 1; }
[ -d "$MYLISP" ] || { echo "pinned my-lisp checkout missing: $MYLISP" >&2; exit 1; }

PIN=$(git -C "$REPO" ls-tree HEAD external/my-lisp | awk '{print $3}')
HEAD=$(git -C "$MYLISP" rev-parse HEAD)
[ -n "$PIN" ] && [ "$PIN" = "$HEAD" ] || {
  echo "FAIL-CLOSED: my-lisp HEAD $HEAD differs from gitlink $PIN" >&2
  exit 1
}

rm -rf "$OUT"
mkdir -p "$OUT/bin" "$OUT/lib/wsm-graalvm/$RELEASE_VERSION" "$OUT/share/doc/wsm-graalvm"
RUNTIME="$OUT/lib/wsm-graalvm/$RELEASE_VERSION"
cp "$BINARY" "$RUNTIME/"

AUTHORITY="$RUNTIME/my-lisp"
while IFS= read -r path; do
  [ -n "$path" ] || continue
  case "$path" in \#*) continue ;; esac
  src="$MYLISP/$path"
  [ -f "$src" ] || { echo "missing declared authority path: $path" >&2; exit 1; }
  mkdir -p "$AUTHORITY/$(dirname "$path")"
  cp "$src" "$AUTHORITY/$path"
done < "$REPO/refs/sparse-authority-paths.txt"

GRAALVM_VERSION=${GRAALVM_VERSION:-25.3.4.1} bash "$REPO/scripts/write-release-metadata.sh" "$VERSION" "$OUT/share/doc/wsm-graalvm" "$PLATFORM"
PIN=$(cat "$OUT/share/doc/wsm-graalvm/MY_LISP_PIN.txt")
PYTHON=$(command -v python3 2>/dev/null || command -v python 2>/dev/null || true)
[ -n "$PYTHON" ] || { echo "python3/python is required for SBOM generation" >&2; exit 1; }
VERSION="$VERSION" WSM_COMMIT="$(git -C "$REPO" rev-parse HEAD)" MY_LISP_PIN="$PIN" PLATFORM="$PLATFORM" GRAALVM_VERSION=${GRAALVM_VERSION:-25.3.4.1} \
  "$PYTHON" "$REPO/scripts/write-release-sbom.py" > "$OUT/share/doc/wsm-graalvm/SBOM.spdx.json"
cp "$REPO/refs/lisp-dependency-manifest.lisp" "$OUT/share/doc/wsm-graalvm/"
cp "$REPO/refs/sparse-authority-paths.txt" "$OUT/share/doc/wsm-graalvm/"
cp "$REPO/LICENSE" "$OUT/share/doc/wsm-graalvm/LICENSE"
cp "$REPO/README.md" "$OUT/share/doc/wsm-graalvm/README.md" 2>/dev/null || true

if [ "$PLATFORM" = "linux-x86_64" ]; then
  cat > "$OUT/bin/wsm" <<EOF
#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${1:-}"
if [ -z "$SOURCE" ] || [ "$#" -ne 1 ]; then
  echo "usage: wsm <program.lisp>" >&2
  exit 2
fi
AUTH="$HERE/lib/wsm-graalvm/$RELEASE_VERSION/my-lisp"
TMP="$(mktemp --suffix=.lisp)"
trap 'rm -f "$TMP"' EXIT
cat "$AUTH/lib/canon.lisp" "$AUTH/lib/macro.lisp" "$AUTH/lib/core.lisp" "$SOURCE" > "$TMP"
"$HERE/lib/wsm-graalvm/$RELEASE_VERSION/native-wsm" "$TMP" "$AUTH/lib/surface/semantic-registry.lisp" "$HERE"
EOF
  chmod +x "$OUT/bin/wsm"
else
  cat > "$OUT/bin/wsm.cmd" <<EOF
@echo off
setlocal
if "%~1"=="" (
  echo usage: wsm ^<program.lisp^> 1>&2
  exit /b 2
)
set "ROOT=%~dp0.."
set "AUTH=%ROOT%\lib\wsm-graalvm\$RELEASE_VERSION\my-lisp"
set "TMP=%TEMP%\wsm-%RANDOM%-%RANDOM%.lisp"
type "%AUTH%\lib\canon.lisp" > "%TMP%"
type "%AUTH%\lib\macro.lisp" >> "%TMP%"
type "%AUTH%\lib\core.lisp" >> "%TMP%"
type "%~1" >> "%TMP%"
"%ROOT%\lib\wsm-graalvm\$RELEASE_VERSION\native-wsm.exe" "%TMP%" "%AUTH%\lib\surface\semantic-registry.lisp" "%ROOT%"
set "ERR=%ERRORLEVEL%"
del /q "%TMP%" >nul 2>&1
exit /b %ERR%
EOF
fi

echo "RELEASE-PAYLOAD-OK $VERSION $PLATFORM PIN=$PIN"
