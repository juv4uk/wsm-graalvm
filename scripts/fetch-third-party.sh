#!/usr/bin/env bash
# Deterministic Truffle/Polyglot API fetch for fresh clones and CI.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
OUT="$REPO/third_party"
V="${GRAAL_ARTIFACT_VERSION:-25.3.4.1}"
M="https://repo.maven.apache.org/maven2"

mkdir -p "$OUT"

fetch() {
  local group=$1 artifact=$2 target=$3
  local rel="${group//.//}/$artifact/$V/$artifact-$V.jar"
  local out="$OUT/$target"
  local tmp="$out.tmp"

  if [ -s "$out" ] && jar tf "$out" >/dev/null 2>&1; then
    echo "cache hit: $target"
    return 0
  fi

  rm -f "$out" "$tmp"
  echo "fetch: $artifact@$V"
  curl --fail --location --silent --show-error     --retry 3 --retry-delay 1     "$M/$rel" -o "$tmp"
  jar tf "$tmp" >/dev/null
  mv "$tmp" "$out"
}

fetch org.graalvm.truffle truffle-api truffle-api.jar
fetch org.graalvm.polyglot polyglot polyglot.jar
fetch org.graalvm.truffle truffle-runtime truffle-runtime.jar
fetch org.graalvm.truffle truffle-dsl-processor truffle-dsl-processor.jar

echo "fetch-third-party: GREEN ($V)"
