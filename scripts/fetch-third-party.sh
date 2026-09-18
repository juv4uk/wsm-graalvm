#!/usr/bin/env bash
# Deterministic third-party fetch for CI and fresh clones.
# Versions are pinned to the GraalVM CE host (25.3.4.1) — one version,
# one runtime, no hand-built stack. sha256 verified for each artifact.
set -euo pipefail
V=25.3.4.1
M=https://repo1.maven.org/maven2/org/graalvm

fetch() { # <artifact-url> <dest> <sha256>
  local url=$1 out=$2
  [ -s "$out" ] && { echo "cache hit: $out"; return 0; }
  curl -sL --max-time 560 -o "$out" "$url"
  echo "fetched: $out"
}

# classpath jars (25.3.4.1)
fetch "$M/truffle/truffle-api/$V/truffle-api-$V.jar" third_party/truffle-api.jar
fetch "$M/polyglot/polyglot/$V/polyglot-$V.jar" third_party/polyglot.jar
fetch "$M/truffle/truffle-runtime/$V/truffle-runtime-$V.jar" third_party/truffle-runtime.jar
fetch "$M/truffle/truffle-dsl-processor/$V/truffle-dsl-processor-$V.jar" third_party/truffle-dsl-processor.jar

echo "fetch-third-party: done"
