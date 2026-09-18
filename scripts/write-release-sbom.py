#!/usr/bin/env python3
import json
import os
from datetime import datetime, timezone

version = os.environ["VERSION"].lstrip("v")
wsm_commit = os.environ["WSM_COMMIT"]
my_lisp = os.environ["MY_LISP_PIN"]
platform = os.environ["PLATFORM"]
graal = os.environ.get("GRAALVM_VERSION", "unknown")

epoch = int(os.environ.get("SOURCE_DATE_EPOCH", "0"))
created = datetime.fromtimestamp(epoch, timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")

doc = {
    "spdxVersion": "SPDX-2.3",
    "dataLicense": "CC0-1.0",
    "SPDXID": "SPDXRef-DOCUMENT",
    "name": f"wsm-graalvm-{version}-{platform}",
    "documentNamespace": f"https://wsm-graalvm.invalid/spdx/{wsm_commit}/{platform}",
    "creationInfo": {
        "created": created,
        "creators": ["Tool: wsm-graalvm release tooling"],
    },
    "packages": [
        {
            "SPDXID": "SPDXRef-Package-WsmGraalVM",
            "name": "wsm-graalvm",
            "versionInfo": version,
            "downloadLocation": "NOASSERTION",
            "filesAnalyzed": False,
            "licenseConcluded": "NOASSERTION",
            "licenseDeclared": "NOASSERTION",
            "supplier": "Organization: WSM Project",
            "externalRefs": [
                {"referenceCategory": "OTHER", "referenceType": "git", "referenceLocator": wsm_commit},
                {"referenceCategory": "OTHER", "referenceType": "graalvm", "referenceLocator": graal},
            ],
        },
        {
            "SPDXID": "SPDXRef-Package-MyLisp",
            "name": "my-lisp",
            "versionInfo": my_lisp,
            "downloadLocation": "NOASSERTION",
            "filesAnalyzed": False,
            "licenseConcluded": "NOASSERTION",
            "licenseDeclared": "NOASSERTION",
            "supplier": "Organization: WSM Project",
            "externalRefs": [
                {"referenceCategory": "OTHER", "referenceType": "git", "referenceLocator": my_lisp}
            ],
        },
    ],
    "relationships": [
        {
            "spdxElementId": "SPDXRef-DOCUMENT",
            "relatedSpdxElement": "SPDXRef-Package-WsmGraalVM",
            "relationshipType": "DESCRIBES",
        },
        {
            "spdxElementId": "SPDXRef-Package-WsmGraalVM",
            "relatedSpdxElement": "SPDXRef-Package-MyLisp",
            "relationshipType": "CONTAINS",
        },
    ],
    "comment": "Minimal release SBOM/provenance inventory. It records the immutable WSM/my-lisp release components; it is not a complete dependency SBOM.",
}
print(json.dumps(doc, indent=2, sort_keys=True))
