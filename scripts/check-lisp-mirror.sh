#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
MIRROR="$REPO/mirror/my-lisp"
UPSTREAM="$REPO/external/my-lisp"

[ -d "$MIRROR" ] || { echo "MIRROR-PATH-GATE FAIL-CLOSED: missing $MIRROR" >&2; exit 1; }
[ -d "$UPSTREAM" ] || { echo "MIRROR-PATH-GATE FAIL-CLOSED: missing $UPSTREAM" >&2; exit 1; }

python3 - "$REPO" <<'PY'
from pathlib import Path
import re
import subprocess
import sys

repo = Path(sys.argv[1]).resolve()
mirror = repo / "mirror" / "my-lisp"
upstream = repo / "external" / "my-lisp"

tracked = set(
    subprocess.check_output(
        ["git", "-C", str(upstream), "ls-files", "-z"],
        text=False,
    ).decode().split("\0")
)
tracked.discard("")

mirror_files = sorted(p.relative_to(mirror).as_posix()
                     for p in mirror.rglob("*") if p.is_file())

if not mirror_files:
    raise SystemExit("MIRROR-PATH-GATE FAIL-CLOSED: mirror contains no files")

for rel in mirror_files:
    if rel not in tracked:
        raise SystemExit(
            f"MIRROR-PATH-GATE FAIL-CLOSED: mirror path has no upstream twin: {rel}"
        )
    if any(part in {"java", "graal", "graalvm"} for part in Path(rel).parts):
        raise SystemExit(
            f"MIRROR-PATH-GATE FAIL-CLOSED: substrate-specific directory in path: {rel}"
        )
    if Path(rel).name.lower() in {"core-next.lisp", "graal-core.lisp"}:
        raise SystemExit(
            f"MIRROR-PATH-GATE FAIL-CLOSED: substrate-specific filename: {rel}"
        )

# Keep source artifacts drop-in by source path/name, not by consumer directory.
for rel in mirror_files:
    text = (mirror / rel).read_text(encoding="utf-8")
    if re.search(r"(?:com\.oracle|wsm\.graalvm|GraalVM|Graal-only|graal-only)", text):
        raise SystemExit(
            f"MIRROR-PATH-GATE FAIL-CLOSED: substrate leakage in Lisp source: {rel}"
        )

def matching_paren(src, start):
    depth = 1
    i = start + 1
    in_comment = False
    in_string = False
    escaped = False
    while i < len(src):
        c = src[i]
        if in_comment:
            if c == "\n":
                in_comment = False
        elif in_string:
            if escaped:
                escaped = False
            elif c == "\\": 
                escaped = True
            elif c == '"':
                in_string = False
        else:
            if c == ";":
                in_comment = True
            elif c == '"':
                in_string = True
            elif c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    return i
        i += 1
    raise ValueError("unbalanced parentheses")

def top_forms(src, start, end):
    out = []
    i = start
    while i < end:
        while i < end and src[i].isspace():
            i += 1
        if i >= end:
            break
        s = i
        if src[i] == "(":
            e = matching_paren(src, i)
            if e >= end:
                raise ValueError("form escapes boundary")
            out.append(src[s:e+1])
            i = e + 1
        else:
            while i < end and not src[i].isspace():
                i += 1
            out.append(src[s:i])
    return out

def cond_ranges(src):
    out = []
    i = 0
    in_comment = False
    in_string = False
    escaped = False
    while i < len(src):
        c = src[i]
        if in_comment:
            if c == "\n":
                in_comment = False
            i += 1
            continue
        if in_string:
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
            i += 1
            continue
        if c == ";":
            in_comment = True
            i += 1
            continue
        if c == '"':
            in_string = True
            i += 1
            continue
        if c == "(":
            j = i + 1
            while j < len(src) and src[j].isspace():
                j += 1
            if src[j:j+4] == "cond" and (
                j + 4 == len(src) or src[j+4].isspace() or src[j+4] in "()"
            ):
                e = matching_paren(src, i)
                out.append((i, e + 1))
                i = e + 1
                continue
        i += 1
    return out

for rel in mirror_files:
    if not rel.endswith(".lisp"):
        continue
    text = (mirror / rel).read_text(encoding="utf-8")
    for a, b in cond_ranges(text):
        seg = text[a:b]
        p = re.search(r"\bcond\b", seg)
        clauses = top_forms(seg, p.end(), len(seg) - 1)
        for clause in clauses:
            if not (clause.startswith("(") and clause.endswith(")")):
                raise SystemExit(
                    f"MIRROR-PATH-GATE FAIL-CLOSED: malformed cond clause in {rel}: {clause}"
                )
            parts = top_forms(clause, 1, len(clause) - 1)
            if len(parts) == 2:
                raise SystemExit(
                    f"MIRROR-PATH-GATE FAIL-CLOSED: two-part cond remains in {rel}: {clause}"
                )
            if len(parts) != 3:
                raise SystemExit(
                    f"MIRROR-PATH-GATE FAIL-CLOSED: non-canonical cond arity {len(parts)} in {rel}: {clause}"
                )

print("MIRROR-PATH-GATE-GREEN")
print(f"  mirror_files={len(mirror_files)}")
print("  path_identity=1:1")
print("  canonical_cond=three-part-only")
print("  substrate_leakage=none")
PY
