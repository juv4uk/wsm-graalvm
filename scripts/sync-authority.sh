#!/usr/bin/env bash
# Pin-safe refresh of the local authority checkout (sparse submodule).
#
# Guarantees:
# 1. fetch BEFORE anything else (agents work in parallel)
# 2. fast-forward only — no rewrite of authority history
# 3. sparse paths stay limited to the four authority files; never the
#    whole my-lisp guts, this repo is a consumer, not a mirror
# 4. digests written after sync so lib/registry-refs.lisp can verify
set -euo pipefail
REPO=$(cd "$(dirname "$0")/.." && pwd)
E=$REPO/external/my-lisp

git -C "$E" fetch origin --quiet
git -C "$E" pull --ff-only origin main | tail -1

if ! diff -q <(printf 'lib/surface/semantic-registry.lisp\nlib/canon.lisp\ntests/fixtures/conformance.lisp\nmy-lisp-constitution.lisp\n') \
     "$E/../../.git/modules/external/my-lisp/info/sparse-checkout" >/dev/null;
then
  printf 'lib/surface/semantic-registry.lisp\nlib/canon.lisp\ntests/fixtures/conformance.lisp\nmy-lisp-constitution.lisp\n' \
    | git -C "$E" init 2>/dev/null >/dev/null || true
  echo "sync-authority: sparse-checkout paths refreshed"
fi

echo "digests: (run awk/sha256sum for registry-refs verification)"
for f in lib/surface/semantic-registry.lisp lib/canon.lisp \
         tests/fixtures/conformance.lisp my-lisp-constitution.lisp; do
  if [ -f "$REPO/external/my-lisp/$f" ]; then
    D=$(sha256sum "$REPO/external/my-lisp/$f" | cut -d' ' -f1)
    echo "$f $D"
  fi
done
