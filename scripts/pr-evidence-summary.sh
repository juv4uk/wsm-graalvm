#!/usr/bin/env bash
# Print exact pair and evidence status for PR summary.
# Usage: bash scripts/pr-evidence-summary.sh [--markdown]
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MARKDOWN=false
[ "${1:-}" = "--markdown" ] && MARKDOWN=true

WSM_HEAD=$(git -C "$REPO" rev-parse HEAD)
MY_LISP_PIN=$(git -C "$REPO" ls-files -s external/my-lisp | awk '$1 == "160000" {print $2}')
ACTUAL_MY_LISP_HEAD=$(git -C "$REPO/external/my-lisp" rev-parse HEAD)
GITLINK_SYNC="✅"
[ "$MY_LISP_PIN" = "$ACTUAL_MY_LISP_HEAD" ] || GITLINK_SYNC="❌"

if $MARKDOWN; then
    cat <<MD
## Exact Pair Evidence Summary

| Coordinate | Value |
|---|---|
| **wsm-graalvm HEAD** | \`$WSM_HEAD\` |
| **my-lisp pin** | \`$MY_LISP_PIN\` |
| **gitlink ↔ submodule sync** | $GITLINK_SYNC |

### Evidence Freshness
MD
    bash "$REPO/scripts/check-evidence-freshness.sh" 2>&1 | sed 's/^/  /'
else
    echo "Exact Pair:"
    echo "  wsm-graalvm HEAD: $WSM_HEAD"
    echo "  my-lisp pin:      $MY_LISP_PIN"
    echo "  gitlink ↔ submodule: $GITLINK_SYNC"
    echo
    echo "Evidence Freshness:"
    bash "$REPO/scripts/check-evidence-freshness.sh"
fi
