#!/usr/bin/env python3
"""Fail-closed адаптер GraalVM до upstream SENS execution-conformance JSONL.

Семантичний контракт і його валідатор належать external/sens.
Цей файл перевіряє лише придатність уже валідного L0-рядка як входу
для майбутнього Truffle/L3 свідка.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

SCHEMA = "sens-execution-conformance/v1"


def validator_path(repo: Path) -> Path:
    path = (
        repo
        / "external"
        / "sens"
        / "benchmarks"
        / "execution-ladder-conformance"
        / "validate.py"
    )
    if not path.is_file():
        raise FileNotFoundError(
            f"upstream validator missing: {path}; "
            "requires juv4uk/sens#3575 or successor"
        )
    return path


def validate_upstream(validator: Path, jsonl: Path) -> None:
    subprocess.run(
        [sys.executable, str(validator), str(jsonl)],
        check=True,
    )


def consume_oracles(jsonl: Path) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for line_no, raw in enumerate(
        jsonl.read_text(encoding="utf-8").splitlines(), 1
    ):
        if not raw.strip():
            continue
        row = json.loads(raw)
        if row.get("schema") != SCHEMA:
            raise ValueError(f"line {line_no}: unexpected schema")
        if row.get("producer_layer") != "L0":
            raise ValueError(
                f"line {line_no}: Graal adapter consumes L0 oracle rows only"
            )
        if row.get("parity_status") != "ORACLE":
            raise ValueError(
                f"line {line_no}: L0 row must be ORACLE"
            )
        if row.get("legacy_identity_used") is not False:
            raise ValueError(
                f"line {line_no}: legacy identity is forbidden"
            )

        for item in row.get("identity_trace", []):
            domain = item.get("domain")
            bits = item.get("bits")
            if not isinstance(domain, int) or not isinstance(bits, str):
                raise ValueError(
                    f"line {line_no}: malformed exact-domain identity"
                )
            if len(bits) != domain:
                raise ValueError(
                    f"line {line_no}: payload width does not match D{domain}"
                )
        rows.append(row)

    if not rows:
        raise ValueError("oracle JSONL is empty")
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("jsonl", type=Path)
    args = parser.parse_args()

    repo = Path(__file__).resolve().parents[1]
    jsonl = args.jsonl.resolve()
    validator = validator_path(repo)

    validate_upstream(validator, jsonl)
    rows = consume_oracles(jsonl)

    for row in rows:
        domains = ",".join(
            f"D{item['domain']}:{item['bits']}"
            for item in row["identity_trace"]
        )
        print(
            "GRAAL-CONFORMANCE-CASE "
            f"case_id={row['case_id']} "
            f"oracle_digest={row['oracle_digest']} "
            f"identities={domains}"
        )

    print(f"GRAAL-CONFORMANCE-IMPORT-GREEN cases={len(rows)}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (
        OSError,
        ValueError,
        json.JSONDecodeError,
        subprocess.CalledProcessError,
    ) as exc:
        print(f"GRAAL-CONFORMANCE-IMPORT-RED: {exc}", file=sys.stderr)
        raise SystemExit(2)
