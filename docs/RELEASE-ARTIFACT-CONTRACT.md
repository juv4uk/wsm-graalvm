# v0.1.x Release Artifact Contract

This is the packaging contract for the first production releases of WSM/GraalVM. All platform packages are different transport/install formats for the same release payload.

## Semantic authority

The release MUST be built from one immutable WSM commit and one exact `external/my-lisp` gitlink commit.

The gitlink is the authority reference. Release jobs MUST NOT silently replace it with a moving branch such as `origin/main`. A release build fails closed when the checked-out submodule HEAD differs from the gitlink recorded by the WSM commit.

The package carries the complete pinned `my-lisp` source as executable input and provenance material; it is not a second semantic implementation.

## First-release targets

| Target | Architecture | Artifact |
| --- | --- | --- |
| Windows | x86_64 | installer + portable ZIP |
| Debian/Ubuntu | x86_64 | `.deb` + portable ZIP |
| RPM family | x86_64 | `.rpm` + portable ZIP |

## Canonical payload

Every installable package contains, directly or through its platform launcher:
- the Native Image WSM executable;
- the canonical launcher/CLI entry point;
- the exact complete pinned `my-lisp` source used by bootstrap;
- `RELEASE.txt` with release version, WSM commit, my-lisp commit and target platform;
- SPDX 2.3 SBOM metadata;
- license and release documentation.

The installed command MUST execute the same bootstrap path: `canon -> macro -> core -> user Lisp`.

No package may add a platform-specific semantic implementation.

## Filesystem contract

Linux packages use:
- executable entry point: `/usr/bin/wsm`;
- private runtime/payload: `/usr/lib/wsm-graalvm/<version>/`;
- release metadata: `/usr/share/doc/wsm-graalvm/RELEASE.txt`.

Windows installs into `Program Files\\WSM\\<version>` and exposes a `wsm.cmd` CLI entry point.

## Integrity and provenance

Checksums are calculated from the final bytes that are uploaded. Every published distributable receives a SHA-256 sidecar. No package is repacked after its checksum is generated.

Publication is fail-closed until package lifecycle smoke tests pass.

## Lifecycle tests

Before publication:
1. install on a clean target environment;
2. inspect version and authority pin;
3. execute the canonical bootstrap witness;
4. remove/uninstall and verify package-owned files are gone.

The smoke tests use the package being published, not a source-tree binary.

## Release gate

The GitHub Release/tag publication step is last. It is allowed only after:
- semantic/bootstrap gate;
- Linux Native Image;
- Windows Native Image;
- Windows installer smoke test;
- Debian install/run/remove;
- RPM install/run/remove;
- portable archive/checksum verification.

Package-manager repositories and feeds remain post-v0.1.x work.
