#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE=${1:-}
if [ -z "$SOURCE" ] || [ $# -ne 1 ]; then
  echo "usage: wsm-graalvm.sh <program.lisp>" >&2
  exit 2
fi
exec "$ROOT/bin/native-wsm" "$SOURCE" "$ROOT/authority/lib/surface/semantic-registry.lisp" "$ROOT/authority"
