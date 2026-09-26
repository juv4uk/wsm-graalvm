#!/usr/bin/env python3
"""Fail-closed validator for #93 cross-substrate parity evidence.

This checker validates evidence shape and the three-way proof rule:

    Lisp-owned expected datum
           /          \
       Rust          Graal
           \          /
        same normalized observable

It deliberately does not execute either substrate. Runtime collection belongs
to a later runner layered on this contract after the exact-byte cutover (#230).
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

SCHEMA = "wsm-substrate-parity-evidence/1"
PIN = re.compile(r"^[0-9a-f]{40}$")
SID = re.compile(r"^[01]{8}$")
ALLOWED_KIND = {"parity", "negative-route"}
ALLOWED_STATUS = {"green", "red", "blocked"}
ALLOWED_GRAAL_MODE = {"jvm", "native-image"}


class EvidenceError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise EvidenceError(message)


def nonempty(value: Any, field: str) -> str:
    require(isinstance(value, str) and value.strip() != "", f"{field}: expected non-empty string")
    return value


def normalized_observation(obj: Any, field: str) -> Any:
    require(isinstance(obj, dict), f"{field}: expected object")
    require("normalized" in obj, f"{field}.normalized: missing")
    return obj["normalized"]


def validate_common(row: dict[str, Any], index: int) -> None:
    p = f"rows[{index}]"
    nonempty(row.get("id"), f"{p}.id")
    require(row.get("kind") in ALLOWED_KIND, f"{p}.kind: invalid")
    require(row.get("status") in ALLOWED_STATUS, f"{p}.status: invalid")

    upstream = nonempty(row.get("upstream_pin"), f"{p}.upstream_pin")
    require(PIN.fullmatch(upstream) is not None, f"{p}.upstream_pin: must be exact 40-hex pin")

    wsm = nonempty(row.get("wsm_commit"), f"{p}.wsm_commit")
    require(PIN.fullmatch(wsm) is not None, f"{p}.wsm_commit: must be exact 40-hex commit")

    nonempty(row.get("contract_id"), f"{p}.contract_id")
    nonempty(row.get("fixture_path"), f"{p}.fixture_path")
    nonempty(row.get("normalization_rule"), f"{p}.normalization_rule")

    source = row.get("source_owner")
    require(isinstance(source, dict), f"{p}.source_owner: expected object")
    require(source.get("repository") == "juv4uk/sens",
            f"{p}.source_owner.repository: semantic authority must be juv4uk/sens")
    nonempty(source.get("path"), f"{p}.source_owner.path")

    expected = row.get("expected")
    require(isinstance(expected, dict), f"{p}.expected: expected object")
    require(expected.get("repository") == "juv4uk/sens",
            f"{p}.expected.repository: expected datum must be Lisp-owned")
    nonempty(expected.get("path"), f"{p}.expected.path")
    normalized_observation(expected, f"{p}.expected")

    sid = row.get("sid")
    if sid is not None:
        require(isinstance(sid, str) and SID.fullmatch(sid) is not None,
                f"{p}.sid: when present, must be exact 8-bit SID")


def validate_parity(row: dict[str, Any], index: int) -> None:
    p = f"rows[{index}]"
    expected = normalized_observation(row["expected"], f"{p}.expected")
    rust = row.get("rust")
    graal = row.get("graal")

    require(isinstance(rust, dict), f"{p}.rust: expected object")
    require(rust.get("substrate") == "rust", f"{p}.rust.substrate: must be rust")
    nonempty(rust.get("provenance"), f"{p}.rust.provenance")
    rust_value = normalized_observation(rust, f"{p}.rust")

    require(isinstance(graal, dict), f"{p}.graal: expected object")
    require(graal.get("substrate") == "graalvm", f"{p}.graal.substrate: must be graalvm")
    require(graal.get("mode") in ALLOWED_GRAAL_MODE, f"{p}.graal.mode: invalid")
    nonempty(graal.get("execution_owner"), f"{p}.graal.execution_owner")
    nonempty(graal.get("provenance"), f"{p}.graal.provenance")
    graal_value = normalized_observation(graal, f"{p}.graal")

    if row["status"] == "green":
        require(rust_value == expected,
                f"{p}: GREEN forbidden: Rust disagrees with Lisp-owned expected datum")
        require(graal_value == expected,
                f"{p}: GREEN forbidden: Graal disagrees with Lisp-owned expected datum")


def validate_negative_route(row: dict[str, Any], index: int) -> None:
    p = f"rows[{index}]"
    neg = row.get("negative_route")
    require(isinstance(neg, dict), f"{p}.negative_route: expected object")
    nonempty(neg.get("disabled_execution_owner"), f"{p}.negative_route.disabled_execution_owner")
    require(neg.get("fallback_used") is False,
            f"{p}.negative_route.fallback_used: must be false")
    failure = nonempty(neg.get("observed_failure"), f"{p}.negative_route.observed_failure")

    graal = row.get("graal")
    require(isinstance(graal, dict), f"{p}.graal: expected object")
    require(graal.get("substrate") == "graalvm", f"{p}.graal.substrate: must be graalvm")
    require(graal.get("mode") in ALLOWED_GRAAL_MODE, f"{p}.graal.mode: invalid")
    nonempty(graal.get("provenance"), f"{p}.graal.provenance")

    if row["status"] == "green":
        require(failure in {"MechanismUnavailable", "InvalidForm", "Type"},
                f"{p}: GREEN negative route must expose a named fail-closed class")
        require(graal.get("fallback_used") is False,
                f"{p}: GREEN forbidden if Graal reports any semantic fallback")


def validate(doc: Any, require_ready: bool) -> tuple[int, int, int]:
    require(isinstance(doc, dict), "document: expected object")
    require(doc.get("schema") == SCHEMA, f"schema: expected {SCHEMA}")

    authority = doc.get("authority")
    require(isinstance(authority, dict), "authority: expected object")
    require(authority.get("semantic_owner") == "juv4uk/sens",
            "authority.semantic_owner must be juv4uk/sens")
    nonempty(authority.get("rule"), "authority.rule")

    rows = doc.get("rows")
    require(isinstance(rows, list), "rows: expected array")

    ids: set[str] = set()
    green_parity = 0
    green_negative = 0

    for index, row in enumerate(rows):
        require(isinstance(row, dict), f"rows[{index}]: expected object")
        validate_common(row, index)
        rid = row["id"]
        require(rid not in ids, f"rows[{index}].id: duplicate {rid}")
        ids.add(rid)

        if row["kind"] == "parity":
            validate_parity(row, index)
            if row["status"] == "green":
                green_parity += 1
        else:
            validate_negative_route(row, index)
            if row["status"] == "green":
                green_negative += 1

    if require_ready:
        require(green_parity > 0, "ready gate: requires at least one GREEN three-way parity row")
        require(green_negative > 0, "ready gate: requires at least one GREEN negative-route row")

    return len(rows), green_parity, green_negative


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("evidence", type=Path)
    parser.add_argument(
        "--require-ready",
        action="store_true",
        help="fail unless at least one GREEN parity row and one GREEN negative-route row exist",
    )
    args = parser.parse_args()

    try:
        doc = json.loads(args.evidence.read_text(encoding="utf-8"))
        rows, parity, negative = validate(doc, args.require_ready)
    except (OSError, json.JSONDecodeError, EvidenceError) as exc:
        print(f"SUBSTRATE-PARITY-EVIDENCE FAIL-CLOSED: {exc}", file=sys.stderr)
        return 1

    gate = "READY" if parity > 0 and negative > 0 else "NOT_READY"
    print(
        "SUBSTRATE-PARITY-EVIDENCE-SCHEMA-GREEN"
        f" rows={rows} green-parity={parity} green-negative={negative} gate={gate}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
