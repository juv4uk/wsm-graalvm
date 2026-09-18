#!/usr/bin/env bash
# Validate the live sibling my-lisp authority. There is nothing to copy or pin.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
ROOT=$(bash "$REPO/scripts/resolve-my-lisp.sh")

bash "$REPO/scripts/verify-authority.sh" "$ROOT"

HEAD=$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || true)
echo "LIVE-LISP-AUTHORITY-GREEN"
echo "  root=$ROOT"
[ -n "$HEAD" ] && echo "  upstream-head=$HEAD"
echo "  transport=sibling-worktree"
echo "  copies=none"
