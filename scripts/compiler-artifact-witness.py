#!/usr/bin/env python3
"""Fail-closed validator for the canonical SENS compiler artifact witness.

This module never derives semantic meaning from D-domain coordinates.  It
accepts the SENS-carried execution-role and admits only a Graal-owned mechanism
for the bounded witness case.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

SCHEMA = "compiler-compilation-artifact/1"
REQUEST_SCHEMAS = {"compiler-semantic-input/1", "compiler-semantic-input/2"}
EVIDENCE_SCHEMA = "wsm-compiler-artifact-witness/1"
EXPECTED_SENS_COMMIT = "1869fd5e51f38565ca968abceaa4bc933ae7a114"
EXPECTED_BUNDLE_SHA256 = "9fed905899983d1d3b2928002fcb60333c605344cf9cc449b2c0fc2264935fc6"
EXPECTED_FIXTURE = "nucleus-d3-100"
EXPECTED_ROLE = "selector-head"
EXPECTED_REQUEST_SHA256 = "c3a4b4aa951ffd2d952f72b20ab69126f5c1a2f53e9874d5944220c13e4bc3e8"
GRAAL_MECHANISM = "graal.jvm.selector-head-v1"
FORBIDDEN_SEMANTIC_TOKENS = ("graal", "cuda", "ptx", "cubin", "sass", "fpga", "slot-vm", "sid8", "sens8")


class WitnessError(ValueError):
    def __init__(self, stage: str, code: str, detail: str):
        super().__init__(detail)
        self.stage = stage
        self.code = code
        self.detail = detail

    def as_json(self) -> dict[str, str]:
        return {"status": "red", "stage": self.stage, "code": self.code, "detail": self.detail}


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_text(text: str) -> str:
    return sha256_bytes(text.encode("utf-8"))


def require(condition: bool, stage: str, code: str, detail: str) -> None:
    if not condition:
        raise WitnessError(stage, code, detail)


def dotted_quoted(text: str, field: str) -> str:
    match = re.search(rf'\({re.escape(field)}\s+\.\s+"([^"]*)"\)', text)
    if not match:
        raise WitnessError("artifact-validation", "missing-field", field)
    return match.group(1)


def dotted_symbol(text: str, field: str) -> str:
    match = re.search(rf"\({re.escape(field)}\s+\.\s+([^\s)]+)\)", text)
    if not match:
        raise WitnessError("artifact-validation", "missing-field", field)
    return match.group(1)


def balanced_form_after(text: str, marker: str) -> tuple[int, int, str]:
    pos = text.find(marker)
    if pos < 0:
        raise WitnessError("artifact-validation", "missing-field", "semantic-request")
    start = pos + len(marker)
    while start < len(text) and text[start].isspace():
        start += 1
    require(start < len(text) and text[start] == "(", "artifact-validation", "malformed", "semantic-request is not a form")

    depth = 0
    in_string = False
    escaped = False
    for i in range(start, len(text)):
        ch = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return start, i + 1, text[start : i + 1]
            if depth < 0:
                break
    raise WitnessError("artifact-validation", "malformed", "unterminated semantic-request")


def split_artifacts(bundle: str) -> list[str]:
    starts = [m.start() for m in re.finditer(r"\(compilation-artifact\b", bundle)]
    require(bool(starts), "artifact-validation", "empty-bundle", "no compilation-artifact forms")
    out: list[str] = []
    for start in starts:
        depth = 0
        in_string = False
        escaped = False
        for i in range(start, len(bundle)):
            ch = bundle[i]
            if in_string:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == '"':
                    in_string = False
                continue
            if ch == '"':
                in_string = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    out.append(bundle[start : i + 1])
                    break
        else:
            raise WitnessError("artifact-validation", "malformed", "unterminated compilation-artifact")
    return out


def validate_request(request: str) -> dict[str, str]:
    lowered = request.casefold()
    for token in FORBIDDEN_SEMANTIC_TOKENS:
        require(token not in lowered, "artifact-validation", "backend-semantic-field", token)

    require(
        request.lstrip().startswith("(compiler-semantic-request"),
        "artifact-validation",
        "wrong-request-envelope",
        "embedded form is not compiler-semantic-request",
    )
    request_schema = dotted_symbol(request, "schema")
    require(
        request_schema in REQUEST_SCHEMAS,
        "artifact-validation",
        "wrong-request-schema",
        request_schema,
    )
    if request_schema == "compiler-semantic-input/1":
        require("(identity . ((" in request, "artifact-validation", "malformed", "v1 identity container missing")
        require("(provenance . ((" in request, "artifact-validation", "malformed", "v1 provenance container missing")
    else:
        require("(domain-coordinate . ((" in request, "artifact-validation", "malformed", "v2 domain-coordinate container missing")
        require("(authority-chain . ((" in request, "artifact-validation", "malformed", "v2 authority-chain container missing")
    require(
        dotted_symbol(request, "semantic-status") == "current",
        "artifact-validation",
        "non-current-semantic",
        dotted_symbol(request, "semantic-status"),
    )
    require(
        dotted_symbol(request, "mechanism-status") == "unknown",
        "artifact-validation",
        "semantic-mechanism-leak",
        dotted_symbol(request, "mechanism-status"),
    )
    require(
        "(mechanism-ref . ())" in request,
        "artifact-validation",
        "semantic-mechanism-leak",
        "mechanism-ref must be empty",
    )

    domain = dotted_symbol(request, "domain")
    bits = dotted_symbol(request, "bits")
    require(domain != "D8", "artifact-validation", "research-domain", "D8 is not admitted")
    require(re.fullmatch(r"D[1-7]", domain) is not None, "artifact-validation", "unsupported-domain", domain)
    width = int(domain[1:])
    require(len(bits) == width and set(bits) <= {"0", "1"}, "artifact-validation", "invalid-identity", f"{domain}/{bits}")

    repository = dotted_quoted(request, "repository")
    revision = dotted_quoted(request, "revision")
    contract = dotted_symbol(request, "contract")
    require(repository == "juv4uk/sens", "artifact-validation", "wrong-authority", repository)
    require(revision == EXPECTED_SENS_COMMIT, "artifact-validation", "stale-artifact", revision)
    require(contract == "11.6", "artifact-validation", "wrong-contract", contract)

    return {
        "fixture_id": dotted_quoted(request, "fixture-id"),
        "domain": domain,
        "bits": bits,
        "role": dotted_symbol(request, "execution-role"),
        "repository": repository,
        "revision": revision,
        "contract": contract,
        "authority_sha256": dotted_quoted(request, "authority-sha256"),
        "compiler_nucleus_sha256": dotted_quoted(request, "compiler-nucleus-sha256"),
    }


def validate_artifact(artifact: str) -> dict[str, Any]:
    start, end, request = balanced_form_after(artifact, "(semantic-request . ")
    outer = artifact[:start] + artifact[end:]
    require(dotted_symbol(outer, "schema") == SCHEMA, "artifact-validation", "wrong-schema", dotted_symbol(outer, "schema"))
    require(
        dotted_symbol(outer, "artifact-status") == "canonical-backend-neutral",
        "artifact-validation",
        "wrong-artifact-status",
        dotted_symbol(outer, "artifact-status"),
    )
    require("(required-capabilities . ())" in outer, "artifact-validation", "target-capability-smuggling", "required-capabilities must be empty")

    outer_lower = outer.casefold()
    for token in FORBIDDEN_SEMANTIC_TOKENS:
        require(token not in outer_lower, "artifact-validation", "backend-artifact-field", token)

    fixture = dotted_quoted(outer, "fixture-id")
    request_sha = dotted_quoted(outer, "semantic-request-sha256")
    require(re.fullmatch(r"[0-9a-f]{64}", request_sha) is not None, "artifact-validation", "invalid-request-digest", request_sha)
    actual = sha256_text(request)
    require(actual == request_sha, "artifact-validation", "artifact-digest-mismatch", f"expected {request_sha}, got {actual}")

    parsed = validate_request(request)
    require(parsed["fixture_id"] == fixture, "artifact-validation", "fixture-mismatch", f"{fixture} != {parsed['fixture_id']}")
    return {
        "fixture_id": fixture,
        "semantic_request_sha256": request_sha,
        "semantic_request": request,
        "request": parsed,
    }


def validate_bundle(bundle: str, *, require_cross_lane_digest: bool = True) -> dict[str, Any]:
    bundle_sha = sha256_text(bundle)
    if require_cross_lane_digest:
        require(
            bundle_sha == EXPECTED_BUNDLE_SHA256,
            "artifact-validation",
            "cross-lane-bundle-digest-mismatch",
            f"expected {EXPECTED_BUNDLE_SHA256}, got {bundle_sha}",
        )

    artifacts = [validate_artifact(item) for item in split_artifacts(bundle)]
    require(len(artifacts) == 9, "artifact-validation", "wrong-closure-size", str(len(artifacts)))
    selected = [item for item in artifacts if item["fixture_id"] == EXPECTED_FIXTURE]
    require(len(selected) == 1, "artifact-validation", "fixture-cardinality", str(len(selected)))
    selected_artifact = selected[0]
    request = selected_artifact["request"]

    require(
        selected_artifact["semantic_request_sha256"] == EXPECTED_REQUEST_SHA256,
        "artifact-validation",
        "cross-lane-request-digest-mismatch",
        selected_artifact["semantic_request_sha256"],
    )
    require(request["role"] == EXPECTED_ROLE, "backend-lowering", "unsupported-carried-role", request["role"])

    return {
        "status": "green",
        "bundle_sha256": bundle_sha,
        "fixture_id": EXPECTED_FIXTURE,
        "semantic_request_sha256": selected_artifact["semantic_request_sha256"],
        "identity": {"domain": request["domain"], "bits": request["bits"]},
        "carried_role": request["role"],
        "graal_mechanism": GRAAL_MECHANISM,
        "semantic_provenance": {
            "repository": request["repository"],
            "commit": request["revision"],
            "contract": request["contract"],
            "authority_sha256": request["authority_sha256"],
            "compiler_nucleus_sha256": request["compiler_nucleus_sha256"],
        },
    }


def rehash_selected(bundle: str, transform) -> str:
    artifacts = split_artifacts(bundle)
    out: list[str] = []
    changed = False
    for artifact in artifacts:
        if f'(fixture-id . "{EXPECTED_FIXTURE}")' not in artifact:
            out.append(artifact)
            continue
        start, end, request = balanced_form_after(artifact, "(semantic-request . ")
        new_request = transform(request)
        old_sha = dotted_quoted(artifact[:start] + artifact[end:], "semantic-request-sha256")
        new_sha = sha256_text(new_request)
        updated = artifact[:start] + new_request + artifact[end:]
        updated = updated.replace(
            f'(semantic-request-sha256 . "{old_sha}")',
            f'(semantic-request-sha256 . "{new_sha}")',
            1,
        )
        out.append(updated)
        changed = True
    require(changed, "artifact-validation", "test-fixture-missing", EXPECTED_FIXTURE)
    return "\n\n".join(out) + "\n"


def expect_error(bundle: str, code: str) -> None:
    try:
        validate_bundle(bundle, require_cross_lane_digest=False)
    except WitnessError as exc:
        require(exc.code == code, "artifact-validation", "wrong-negative-control", f"expected {code}, got {exc.code}")
        return
    raise WitnessError("artifact-validation", "negative-control-failed", code)


def self_test(bundle: str) -> None:
    validate_bundle(bundle)

    # Consumer-first v2 readiness: only envelope/container spellings change.
    first_artifact = split_artifacts(bundle)[0]
    _, _, first_request = balanced_form_after(first_artifact, "(semantic-request . ")
    v2_request = (
        first_request
        .replace("compiler-semantic-input/1", "compiler-semantic-input/2", 1)
        .replace("(identity . ", "(domain-coordinate . ", 1)
        .replace("(provenance . ", "(authority-chain . ", 1)
    )
    validate_request(v2_request)

    digest_mismatch = bundle.replace("(domain . D3) (bits . 100)", "(domain . D3) (bits . 101)", 1)
    expect_error(digest_mismatch, "artifact-digest-mismatch")

    d8 = rehash_selected(
        bundle,
        lambda request: request.replace(
            "(identity . ((domain . D3) (bits . 100)))",
            "(identity . ((domain . D8) (bits . 00000100)))",
            1,
        ).replace(
            "(domain-coordinate . ((domain . D3) (bits . 100)))",
            "(domain-coordinate . ((domain . D8) (bits . 00000100)))",
            1,
        ),
    )
    expect_error(d8, "research-domain")

    stale = rehash_selected(
        bundle,
        lambda request: request.replace(EXPECTED_SENS_COMMIT, "0" * 40, 1),
    )
    expect_error(stale, "stale-artifact")

    backend_field = rehash_selected(
        bundle,
        lambda request: request.replace(
            "(provenance . ",
            "(target . graal)\n           (provenance . ",
            1,
        ).replace(
            "(authority-chain . ",
            "(target . graal)\n           (authority-chain . ",
            1,
        ),
    )
    expect_error(backend_field, "backend-semantic-field")

    capabilities = bundle.replace(
        "(required-capabilities . ())",
        "(required-capabilities . (graal))",
        1,
    )
    expect_error(capabilities, "target-capability-smuggling")


def validate_evidence(doc: Any) -> None:
    require(isinstance(doc, dict), "observable-normalization", "malformed-evidence", "root")
    require(doc.get("schema") == EVIDENCE_SCHEMA, "observable-normalization", "wrong-evidence-schema", str(doc.get("schema")))

    semantic = doc.get("semantic_artifact")
    require(isinstance(semantic, dict), "artifact-validation", "missing-evidence-stage", "semantic_artifact")
    require(semantic.get("bundle_sha256") == EXPECTED_BUNDLE_SHA256, "artifact-validation", "cross-lane-bundle-digest-mismatch", str(semantic.get("bundle_sha256")))
    require(semantic.get("semantic_request_sha256") == EXPECTED_REQUEST_SHA256, "artifact-validation", "cross-lane-request-digest-mismatch", str(semantic.get("semantic_request_sha256")))
    require(semantic.get("sens_commit") == EXPECTED_SENS_COMMIT, "artifact-validation", "stale-artifact", str(semantic.get("sens_commit")))

    target = doc.get("target")
    require(isinstance(target, dict), "backend-lowering", "missing-evidence-stage", "target")
    require(target.get("substrate") == "graalvm", "backend-lowering", "wrong-target", str(target.get("substrate")))
    require(target.get("mechanism") == GRAAL_MECHANISM, "backend-lowering", "unsupported-mechanism", str(target.get("mechanism")))
    require(target.get("sid8_fallback_used") is False, "backend-lowering", "sid8-fallback", "must be false")

    stages = doc.get("stages")
    require(isinstance(stages, dict), "observable-normalization", "missing-stages", "stages")
    for stage in ("artifact-validation", "backend-lowering", "install-runtime", "observable-normalization"):
        require(stages.get(stage) == "green", stage, "stage-not-green", str(stages.get(stage)))

    observable = doc.get("observable")
    require(isinstance(observable, dict), "observable-normalization", "missing-observable", "observable")
    require(observable.get("sens_oracle") == observable.get("graal"), "observable-normalization", "result-divergence", f"{observable.get('sens_oracle')} != {observable.get('graal')}")
    require(observable.get("sens_sha256") == observable.get("graal_sha256"), "observable-normalization", "result-digest-divergence", "digest mismatch")

    negatives = doc.get("negative_controls")
    require(isinstance(negatives, dict), "observable-normalization", "missing-negative-controls", "negative_controls")
    for key in ("artifact-digest-mismatch", "research-domain", "stale-artifact", "backend-semantic-field", "target-capability-smuggling", "unsupported-mechanism", "deliberate-result-divergence"):
        require(negatives.get(key) == "caught", "observable-normalization", "negative-control-not-caught", key)


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    p_validate = sub.add_parser("artifact")
    p_validate.add_argument("bundle", type=Path)
    p_validate.add_argument("--json-out", type=Path)
    p_self = sub.add_parser("self-test")
    p_self.add_argument("bundle", type=Path)
    p_evidence = sub.add_parser("evidence")
    p_evidence.add_argument("evidence", type=Path)
    args = parser.parse_args()

    try:
        if args.command == "artifact":
            result = validate_bundle(args.bundle.read_text(encoding="utf-8"))
            rendered = json.dumps(result, sort_keys=True, indent=2) + "\n"
            if args.json_out:
                args.json_out.write_text(rendered, encoding="utf-8")
            print(rendered, end="")
        elif args.command == "self-test":
            self_test(args.bundle.read_text(encoding="utf-8"))
            print("COMPILER-ARTIFACT-WITNESS-NEGATIVES-GREEN")
        else:
            doc = json.loads(args.evidence.read_text(encoding="utf-8"))
            validate_evidence(doc)
            print("COMPILER-ARTIFACT-EVIDENCE-GREEN")
    except (OSError, json.JSONDecodeError, WitnessError) as exc:
        if isinstance(exc, WitnessError):
            print(json.dumps(exc.as_json(), sort_keys=True), file=sys.stderr)
        else:
            print(json.dumps({"status": "red", "stage": "artifact-validation", "code": "io-or-json", "detail": str(exc)}, sort_keys=True), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
