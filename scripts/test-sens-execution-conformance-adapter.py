#!/usr/bin/env python3
"""Contract tests for the current SENS L0/ORACLE adapter boundary."""

from __future__ import annotations

import importlib.util
import json
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "check-sens-execution-conformance.py"

spec = importlib.util.spec_from_file_location("sens_adapter", MODULE_PATH)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def make_row(layer: str, *, legacy: bool = False) -> dict:
    program = "10 001 00 000 01"
    identity_trace = [{"domain": 3, "bits": "000"}]
    observable = {
        "result_kind": "VALUE",
        "value": "()",
        "output": "",
        "error_kind": None,
        "order_trace": [],
        "mechanism_status": "CALLABLE",
    }
    observable_digest = module.structured_digest(observable)
    return {
        "schema": module.SCHEMA,
        "case_id": module.case_id_for("canonical-source", program),
        "contract": module.CONTRACT,
        "upstream_sha": "a" * 40,
        "producer_layer": layer,
        "producer": "unit-test",
        "program_encoding": "canonical-source",
        "program": program,
        "program_digest": module.program_digest(program),
        "identity_trace": identity_trace,
        "identity_trace_digest": module.structured_digest(identity_trace),
        "observable": observable,
        "observable_digest": observable_digest,
        "oracle_digest": observable_digest,
        "parity_status": "ORACLE" if layer == "L0" else "PASS",
        "evidence_scope": "bounded-exhaustive",
        "exhaustive_bound": {
            "grammar_profile": "d1-d3-structural-predicate-v1",
            "domain_set": [1, 2, 3],
            "max_ast_depth": 3,
            "max_nodes": 11,
            "argument_value_bound": 2,
        },
        "legacy_identity_used": legacy,
    }


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def expect_failure(action, message: str) -> None:
    try:
        action()
    except ValueError:
        return
    raise AssertionError(message)


def main() -> None:
    with tempfile.TemporaryDirectory() as temp:
        path = Path(temp) / "corpus.jsonl"
        rows = [make_row("L0"), make_row("L1")]
        path.write_text(
            "\n".join(json.dumps(row) for row in rows) + "\n",
            encoding="utf-8",
        )

        selected = module.consume_oracles(path)
        require(len(selected) == 1, "adapter must select only L0 rows")
        require(
            selected[0]["producer_layer"] == "L0",
            "selected row must be L0",
        )
        require(
            selected[0]["exhaustive_bound"]["grammar_profile"]
            == "d1-d3-structural-predicate-v1",
            "grammar_profile must survive import",
        )

        legacy_path = Path(temp) / "legacy.jsonl"
        legacy_path.write_text(
            json.dumps(make_row("L0", legacy=True)) + "\n",
            encoding="utf-8",
        )
        expect_failure(
            lambda: module.consume_oracles(legacy_path),
            "legacy identity must fail closed",
        )

        width_path = Path(temp) / "width.jsonl"
        bad = make_row("L0")
        bad["identity_trace"] = [{"domain": 3, "bits": "00"}]
        width_path.write_text(
            json.dumps(bad) + "\n",
            encoding="utf-8",
        )
        expect_failure(
            lambda: module.consume_oracles(width_path),
            "wrong exact-domain width must fail closed",
        )

    print("SENS-ADAPTER-CONTRACT-GREEN")


if __name__ == "__main__":
    main()
