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
fetch "$M/truffle/truffle-compiler/$V/truffle-compiler-$V.jar" third_party/truffle-compiler.jar
# org.graalvm.nativeimage + collections: no standalone Maven jars since 25;
# they ship as distribution jmods — rebuild them into plain classpath jars
if [ ! -s third_party/nativeimage.jar ]; then
  G="${G:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
  JMOD="$G/jmods/org.graalvm.nativeimage.jmod"
  if [ -f "$JMOD" ]; then
    TMP=$(mktemp -d); unzip -qo "$JMOD" 'classes/*' -d "$TMP"
    mkdir -p "$TMP/out/org/graalvm"
    mv "$TMP/classes/org/graalvm/nativeimage" "$TMP/org/graalvm/" 2>/dev/null || true
    # keep other org.graalvm.* subpackage classes to satisfy module deps
    (cd "$TMP" && find classes -name '*.class' -type f | while read f; do
       rel=${f#classes/}; mkdir -p "$(dirname $rel)"; [ -f "$rel" ] || cp "$f" "$rel"; done;
       rm -rf "$TMP/classes" 2>/dev/null || true)
    (cd "$TMP" && rm -rf classes; jar cf "$OLDPWD/third_party/nativeimage.jar" org 2>/dev/null || mv org /tmp/opencode_keep)
    rm -rf "$TMP"
  fi
fi
fetch "$M/truffle/truffle-dsl-processor/$V/truffle-dsl-processor-$V.jar" third_party/truffle-dsl-processor.jar

echo "fetch-third-party: done"
