#!/usr/bin/env bash
# Print exact-pair evidence status for PR/release summaries.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MARKDOWN=false
[ "${1:-}" = "--markdown" ] && MARKDOWN=true

WSM_HEAD=${WSM_EVIDENCE_COMMIT:-$(git -C "$REPO" rev-parse HEAD)}
MY_LISP_PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
ACTUAL_MY_LISP_HEAD=$(git -C "$REPO/external/my-lisp" rev-parse HEAD)
SYNC="✅"
[ "$MY_LISP_PIN" = "$ACTUAL_MY_LISP_HEAD" ] || SYNC="❌"

if $MARKDOWN; then
  cat <<MD
## Exact Pair Evidence Summary

| Coordinate | Value |
|---|---|
| **wsm-graalvm HEAD** | `$WSM_HEAD` |
| **my-lisp pin** | `$MY_LISP_PIN` |
| **gitlink ↔ submodule sync** | $SYNC |

### Evidence Freshness
MD
  WSM_EVIDENCE_COMMIT="$WSM_HEAD" bash "$REPO/scripts/check-evidence-freshness.sh" 2>&1 | sed 's/^/  /'
else
  echo "Exact Pair:"
  echo "  wsm-graalvm HEAD: $WSM_HEAD"
  echo "  my-lisp pin:      $MY_LISP_PIN"
  echo "  gitlink ↔ submodule: $SYNC"
  echo
  WSM_EVIDENCE_COMMIT="$WSM_HEAD" bash "$REPO/scripts/check-evidence-freshness.sh"
fi
