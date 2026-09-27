#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MARKDOWN=false
[ "${1:-}" = "--markdown" ] && MARKDOWN=true

HEAD=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
PIN=$(git -C "$REPO" ls-files -s external/sens | awk '$1 == "160000" {print $2}')
ACTUAL=$(git -C "$REPO/external/sens" rev-parse HEAD)
MANIFEST_PIN=$(sed -n 's/^[[:space:]]*(pin[[:space:]]\+\.[[:space:]]*"\([0-9a-f]\{40\}\)")[[:space:]]*$/\1/p' "$REPO/refs/lisp-dependency-manifest.lisp" | head -1)

if $MARKDOWN; then
  cat <<MD
## Exact-pair evidence

| Coordinate | Value |
|---|---|
| wsm-graalvm head | `$HEAD` |
| my-lisp gitlink | `$PIN` |
| my-lisp submodule HEAD | `$ACTUAL` |
| manifest pin | `$MANIFEST_PIN` |

### Freshness
MD
  WSM_EVIDENCE_COMMIT="$HEAD" bash "$REPO/scripts/check-evidence-freshness.sh" 2>&1 | sed 's/^/    /'
else
  echo "EXACT-PAIR-SUMMARY head=$HEAD pin=$PIN submodule=$ACTUAL manifest=$MANIFEST_PIN"
  WSM_EVIDENCE_COMMIT="$HEAD" bash "$REPO/scripts/check-evidence-freshness.sh"
fi
