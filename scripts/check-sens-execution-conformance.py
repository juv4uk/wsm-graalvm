#!/usr/bin/env python3
"""Fail-closed adapter from upstream SENS execution-conformance JSONL.

The semantic contract and validator belong to external/sens. This script only
checks that an already-valid L0/ORACLE row is admissible as input to the
future Truffle/L3 witness; it does not invent semantic results.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

SCHEMA = "sens-execution-conformance/v1"
CONTRACT = "11.6"
CASE_ID = re.compile(r"^case-[0-9a-f]{64}$")
SHA40 = re.compile(r"^[0-9a-f]{40}$")
SHA64 = re.compile(r"^[0-9a-f]{64}$")

REQUIRED_FIELDS = {
    "schema",
    "case_id",
    "contract",
    "upstream_sha",
    "producer_layer",
    "producer",
    "program_encoding",
    "program",
    "program_digest",
    "identity_trace",
    "identity_trace_digest",
    "observable",
    "observable_digest",
    "oracle_digest",
    "parity_status",
    "evidence_scope",
    "exhaustive_bound",
    "legacy_identity_used",
}

EXHAUSTIVE_KEYS = {
    "grammar_profile",
    "domain_set",
    "max_ast_depth",
    "max_nodes",
    "argument_value_bound",
}


def validator_path(sens_dir: Path) -> Path:
    path = (
        sens_dir
        / "benchmarks"
        / "execution-ladder-conformance"
        / "validate.py"
    )
    if not path.is_file():
        raise FileNotFoundError(
            f"upstream validator missing: {path}; "
            "requires the current juv4uk/sens authority pin"
        )
    return path


def validate_upstream(validator: Path, jsonl: Path) -> None:
    subprocess.run(
        [sys.executable, str(validator), str(jsonl)],
        check=True,
    )


def exact_identity(item: Any, line_no: int) -> dict[str, Any]:
    if not isinstance(item, dict) or set(item) != {"domain", "bits"}:
        raise ValueError(
            f"line {line_no}: identity_trace item must contain exactly domain+bits"
        )

    domain = item["domain"]
    bits = item["bits"]
    if (
        not isinstance(domain, int)
        or isinstance(domain, bool)
        or not 1 <= domain <= 8
    ):
        raise ValueError(
            f"line {line_no}: identity domain must be integer D1..D8"
        )
    if (
        not isinstance(bits, str)
        or not bits
        or any(bit not in "01" for bit in bits)
        or len(bits) != domain
    ):
        raise ValueError(
            f"line {line_no}: exact-domain payload width/content invalid for D{domain}"
        )
    return {"domain": domain, "bits": bits}


def validate_row(row: dict[str, Any], line_no: int) -> None:
    if set(row) != REQUIRED_FIELDS:
        missing = sorted(REQUIRED_FIELDS - row.keys())
        extra = sorted(row.keys() - REQUIRED_FIELDS)
        raise ValueError(
            f"line {line_no}: schema fields differ; missing={missing}, extra={extra}"
        )

    if row["schema"] != SCHEMA:
        raise ValueError(f"line {line_no}: unexpected schema")
    if row["contract"] != CONTRACT:
        raise ValueError(
            f"line {line_no}: expected current Contract {CONTRACT}"
        )
    if row["producer_layer"] != "L0":
        raise ValueError(
            f"line {line_no}: Graal adapter consumes L0 oracle rows only"
        )
    if row["parity_status"] != "ORACLE":
        raise ValueError(f"line {line_no}: L0 row must be ORACLE")
    if row["legacy_identity_used"] is not False:
        raise ValueError(
            f"line {line_no}: legacy identity is forbidden on current route"
        )

    case_id = row["case_id"]
    if not isinstance(case_id, str) or not CASE_ID.fullmatch(case_id):
        raise ValueError(f"line {line_no}: invalid deterministic case_id")

    upstream_sha = row["upstream_sha"]
    if not isinstance(upstream_sha, str) or not SHA40.fullmatch(upstream_sha):
        raise ValueError(f"line {line_no}: invalid upstream_sha")

    for field in ("program_digest", "identity_trace_digest", "observable_digest", "oracle_digest"):
        value = row[field]
        if not isinstance(value, str) or not SHA64.fullmatch(value):
            raise ValueError(f"line {line_no}: invalid {field}")

    if not isinstance(row["identity_trace"], list):
        raise ValueError(f"line {line_no}: identity_trace must be an array")
    for item in row["identity_trace"]:
        exact_identity(item, line_no)

    bound = row["exhaustive_bound"]
    if row["evidence_scope"] == "bounded-exhaustive":
        if not isinstance(bound, dict) or set(bound) != EXHAUSTIVE_KEYS:
            raise ValueError(
                f"line {line_no}: bounded-exhaustive row must preserve grammar_profile + bounds"
            )
        profile = bound["grammar_profile"]
        if not isinstance(profile, str) or not profile.strip():
            raise ValueError(
                f"line {line_no}: bounded-exhaustive grammar_profile is required"
            )
    elif bound is not None:
        raise ValueError(
            f"line {line_no}: exhaustive_bound is only valid for bounded-exhaustive rows"
        )


def consume_oracles(jsonl: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line_no, raw in enumerate(
        jsonl.read_text(encoding="utf-8").splitlines(), 1
    ):
        if not raw.strip():
            continue
        try:
            row = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError(f"line {line_no}: invalid JSON: {exc}") from exc
        if not isinstance(row, dict):
            raise ValueError(f"line {line_no}: expected a JSON object")

        # The current bounded upstream artifact contains both L0 ORACLE and
        # L1 P1 rows for each case. The Graal adapter consumes only the L0
        # oracle rows; the upstream validator remains authoritative for the
        # complete file, including L1 rows.
        if row.get("producer_layer") != "L0":
            continue

        validate_row(row, line_no)
        rows.append(row)

    if not rows:
        raise ValueError("oracle JSONL contains no current L0/ORACLE rows")

    return rows


def normalized_row(row: dict[str, Any]) -> dict[str, Any]:
    """Preserve upstream evidence without adding Java-local expected values."""
    return {
        "schema": row["schema"],
        "contract": row["contract"],
        "upstream_sha": row["upstream_sha"],
        "case_id": row["case_id"],
        "producer_layer": row["producer_layer"],
        "producer": row["producer"],
        "program_encoding": row["program_encoding"],
        "program": row["program"],
        "program_digest": row["program_digest"],
        "identity_trace": row["identity_trace"],
        "identity_trace_digest": row["identity_trace_digest"],
        "observable": row["observable"],
        "observable_digest": row["observable_digest"],
        "oracle_digest": row["oracle_digest"],
        "parity_status": row["parity_status"],
        "evidence_scope": row["evidence_scope"],
        "exhaustive_bound": row["exhaustive_bound"],
        "legacy_identity_used": row["legacy_identity_used"],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("jsonl", type=Path)
    parser.add_argument(
        "--sens-dir",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "external" / "sens",
    )
    args = parser.parse_args()

    jsonl = args.jsonl.resolve()
    sens_dir = args.sens_dir.resolve()
    validator = validator_path(sens_dir)

    validate_upstream(validator, jsonl)
    rows = consume_oracles(jsonl)

    for row in rows:
        print(
            "GRAAL-CONFORMANCE-CASE "
            + json.dumps(
                normalized_row(row),
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )
        )

    print(f"GRAAL-CONFORMANCE-IMPORT-GREEN cases={len(rows)}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (
        OSError,
        ValueError,
        subprocess.CalledProcessError,
    ) as exc:
        print(f"GRAAL-CONFORMANCE-IMPORT-RED: {exc}", file=sys.stderr)
        raise SystemExit(2)
