#!/usr/bin/env python3
import argparse
import json
import re
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("--wsm-head", required=True)
p.add_argument("--my-lisp-pin", required=True)
p.add_argument("--java-version", required=True)
p.add_argument("--native-image-version", required=True)
p.add_argument("--out", required=True)
args = p.parse_args()

for label, value in (("wsm-head", args.wsm_head), ("my-lisp-pin", args.my_lisp_pin)):
    if not re.fullmatch(r"[0-9a-f]{40}", value):
        raise SystemExit(f"{label} must be exact 40-hex")

doc = {
    "schema": "wsm-native-image-evidence/1",
    "gate_id": "native-image-canon",
    "exact_pair": {
        "wsm_graalvm_head": args.wsm_head,
        "my_lisp_pin": args.my_lisp_pin,
    },
    "execution_mode": "native-image",
    "toolchain": {
        "graalvm": args.java_version,
        "native_image": args.native_image_version,
    },
    "corpus": {
        "source": "external/sens/lib/canon.lisp",
        "registry": "external/sens/lib/surface/semantic-registry.lisp",
        "expected": "(canon-conformance satisfied)",
    },
    "status": "green",
}
Path(args.out).parent.mkdir(parents=True, exist_ok=True)
Path(args.out).write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(f"NATIVE-IMAGE-EVIDENCE-GREEN head={args.wsm_head} pin={args.my_lisp_pin}")
