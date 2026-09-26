#!/usr/bin/env python3
"""Self-test the #93 parity evidence validator's RED/GREEN boundaries."""

from __future__ import annotations

import copy
import importlib.util
import json
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VALIDATOR = ROOT / "scripts" / "check-substrate-parity-evidence.py"

spec = importlib.util.spec_from_file_location("parity_validator", VALIDATOR)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

PIN = "6089ebf945ff448f8d23f3432333e31292f6e8e4"
WSM = "1111111111111111111111111111111111111111"

BASE = {
    "schema": "wsm-substrate-parity-evidence/1",
    "authority": {
        "semantic_owner": "juv4uk/sens",
        "rule": "Lisp-owned expected evidence judges both substrates.",
    },
    "rows": [],
}

PARITY = {
    "id": "fixture-atom-1",
    "kind": "parity",
    "status": "green",
    "upstream_pin": PIN,
    "wsm_commit": WSM,
    "contract_id": "canon-atom-observation",
    "fixture_path": "tests/fixtures/example.lisp",
    "sid": "00000010",
    "source_owner": {"repository": "juv4uk/sens", "path": "lib/canon.lisp"},
    "normalization_rule": "canonical-sexpr",
    "expected": {
        "repository": "juv4uk/sens",
        "path": "tests/fixtures/example.expected",
        "normalized": "(structural-kind atom)",
    },
    "rust": {
        "substrate": "rust",
        "provenance": "my-lisp-cli exact pin",
        "normalized": "(structural-kind atom)",
    },
    "graal": {
        "substrate": "graalvm",
        "mode": "jvm",
        "execution_owner": "substrate-mechanism:00000010",
        "provenance": "wsm-graalvm exact commit",
        "normalized": "(structural-kind atom)",
    },
}

NEGATIVE = {
    "id": "negative-no-fallback-1",
    "kind": "negative-route",
    "status": "green",
    "upstream_pin": PIN,
    "wsm_commit": WSM,
    "contract_id": "fail-closed-owner-selection",
    "fixture_path": "tests/fixtures/example.lisp",
    "sid": "00000010",
    "source_owner": {"repository": "juv4uk/sens", "path": "language-contract.lisp"},
    "normalization_rule": "error-kind",
    "expected": {
        "repository": "juv4uk/sens",
        "path": "language-contract.lisp",
        "normalized": "MechanismUnavailable",
    },
    "graal": {
        "substrate": "graalvm",
        "mode": "jvm",
        "provenance": "mechanism deliberately disabled",
        "fallback_used": False,
        "normalized": "MechanismUnavailable",
    },
    "negative_route": {
        "disabled_execution_owner": "substrate-mechanism:00000010",
        "observed_failure": "MechanismUnavailable",
        "fallback_used": False,
    },
}


def expect_fail(doc, label: str) -> None:
    try:
        module.validate(doc, require_ready=False)
    except module.EvidenceError:
        return
    raise AssertionError(f"expected fail-closed validation: {label}")


def main() -> None:
    ready = copy.deepcopy(BASE)
    ready["rows"] = [copy.deepcopy(PARITY), copy.deepcopy(NEGATIVE)]
    rows, parity, negative = module.validate(ready, require_ready=True)
    assert (rows, parity, negative) == (2, 1, 1)

    rust_only_oracle = copy.deepcopy(ready)
    rust_only_oracle["rows"][0]["expected"]["repository"] = "juv4uk/wsm-graalvm"
    expect_fail(rust_only_oracle, "Java/Graal-authored expected datum")

    both_wrong = copy.deepcopy(ready)
    both_wrong["rows"][0]["rust"]["normalized"] = "wrong"
    both_wrong["rows"][0]["graal"]["normalized"] = "wrong"
    expect_fail(both_wrong, "Rust and Graal agree with each other but not upstream")

    fallback = copy.deepcopy(ready)
    fallback["rows"][1]["negative_route"]["fallback_used"] = True
    expect_fail(fallback, "silent fallback")

    stale_shape = copy.deepcopy(ready)
    stale_shape["rows"][0]["sid"] = "0002"
    expect_fail(stale_shape, "legacy non-byte SID")

    empty = copy.deepcopy(BASE)
    module.validate(empty, require_ready=False)
    try:
        module.validate(empty, require_ready=True)
    except module.EvidenceError:
        pass
    else:
        raise AssertionError("empty inventory must not satisfy ready gate")

    print("SUBSTRATE-PARITY-EVIDENCE-VALIDATOR-SELFTEST-GREEN")


if __name__ == "__main__":
    main()
