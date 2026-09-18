# v0.1.x Release Artifact Contract

This is the packaging contract for the first production releases of WSM/GraalVM. All platform packages are different transport/install formats for the same release payload.

## Semantic authority

The release MUST be built from one immutable WSM commit and one exact `external/my-lisp` gitlink commit.

The gitlink is the authority reference. Release jobs MUST NOT silently replace it with a moving branch such as `origin/main`. A release build fails closed when the checked-out submodule HEAD differs from the gitlink recorded by the WSM commit.

The portable release payload and installable packages MUST carry the complete pinned `my-lisp` source tree (excluding nested `.git` metadata). It is provenance material and executable input, not a second semantic authority.

## First-release targets

| Target | Architecture | Artifact |
| --- | --- | --- |
| Windows | x86_64 | installer/package + portable ZIP |
| Debian/Ubuntu | x86_64 | `.deb` + portable tar.gz |
| RPM family | x86_64 | `.rpm` + portable tar.gz |

ARM and additional operating systems are follow-up work and MUST NOT alter the v0.1.x contract.

## Canonical payload

Every installable package contains, directly or through its platform launcher:
- the Native Image WSM executable;
- the canonical launcher/CLI entry point;
- the complete pinned `my-lisp` source tree required by bootstrap;
- `RELEASE.txt` containing release version, WSM commit, my-lisp commit, target platform and GraalVM build version;
- license and release documentation.

The installed command MUST execute the same bootstrap path: `canon -> macro -> core -> user Lisp`.

No package may add a platform-specific semantic implementation.

## Filesystem contract

Linux packages use an FHS-compatible layout:
- executable entry point: `/usr/bin/wsm`;
- private runtime/payload: `/usr/lib/wsm-graalvm/<version>/`;
- release metadata: `/usr/share/doc/wsm-graalvm/RELEASE.txt`.

Windows installs into a stable per-product Program Files location and exposes the same `wsm` CLI entry point. The exact installer technology is an implementation detail; the installed payload and CLI contract are not.

## Versioning and naming

Release version is SemVer-compatible and comes from the release tag, e.g. `v0.1.0`.

Canonical names:
- `wsm-graalvm-<version>-windows-x86_64`;
- `wsm-graalvm-<version>-linux-x86_64.deb`;
- `wsm-graalvm-<version>-linux-x86_64.rpm`;
- `wsm-graalvm-<version>-linux-x86_64.tar.gz`;
- `wsm-graalvm-<version>-windows-x86_64.zip`.

Package manager versions MUST strip the leading `v`.

## Integrity and provenance

The release job emits:
- one SHA-256 checksum manifest covering every published payload;
- `RELEASE.txt` for human-readable provenance;
- machine-readable provenance metadata;
- SBOM metadata when the selected packaging toolchain can emit it.

Checksums are calculated from the final bytes that are uploaded. A package is never repacked after its checksum is generated.

## Lifecycle tests

Before publication:
1. install on a clean target environment;
2. inspect version and authority pin;
3. execute the canonical bootstrap witness;
4. upgrade from the previous package when a previous package exists;
5. remove/uninstall and verify that package-owned files are gone.

Package tests MUST use the package being published, not a source-tree binary.

## Release gate

The GitHub Release/tag publication step is last.

Publication is allowed only when all required first-release artifacts and their package-level smoke tests pass:
- Windows installer/package;
- Debian/Ubuntu `.deb`;
- RPM-family `.rpm`;
- portable archives;
- checksum/provenance verification.

PR validation may build and test packages, but it MUST NOT publish releases. Tag/manual release jobs are the only publishing path.

## Reproducibility rule

Given the same WSM commit, the same `my-lisp` gitlink commit, the same release version and the same declared toolchain inputs, packaging scripts SHOULD produce byte-stable archives. Where a native installer format cannot be made byte-identical, its provenance MUST still identify all immutable inputs.

## Non-goals

This contract does not introduce an APT/YUM repository, signing service, or automatic package-manager feed. Those are post-v0.1.x distribution work.
