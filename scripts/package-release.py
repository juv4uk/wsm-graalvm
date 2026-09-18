#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import stat
import zipfile

FIXED_TIME = (2020, 1, 1, 0, 0, 0)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def add_file(zf: zipfile.ZipFile, path: Path, arcname: str, mode: int) -> None:
    info = zipfile.ZipInfo(arcname, FIXED_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3
    info.external_attr = (mode & 0xFFFF) << 16
    with path.open("rb") as f:
        zf.writestr(info, f.read())


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--version", required=True)
    p.add_argument("--platform", choices=("linux-x86_64", "windows-x86_64"), required=True)
    p.add_argument("--binary", required=True)
    p.add_argument("--upstream", required=True)
    p.add_argument("--upstream-pin", required=True)
    p.add_argument("--repo-sha", required=True)
    p.add_argument("--out", required=True)
    args = p.parse_args()

    root = Path.cwd()
    binary = Path(args.binary)
    if not binary.exists() and args.platform == "windows-x86_64" and Path(str(binary) + ".exe").exists():
        binary = Path(str(binary) + ".exe")
    if not binary.exists():
        raise SystemExit(f"missing native binary: {binary}")

    upstream = Path(args.upstream)
    required = [
        "lib/surface/semantic-registry.lisp",
        "lib/canon.lisp",
        "lib/macro.lisp",
        "lib/core.lisp",
    ]
    for rel in required:
        if not (upstream / rel).is_file():
            raise SystemExit(f"missing pinned authority file: {upstream / rel}")

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    pkg = out / f"wsm-graalvm-{args.version}-{args.platform}"
    if pkg.exists():
        shutil.rmtree(pkg)
    (pkg / "bin").mkdir(parents=True)
    (pkg / "authority" / "lib" / "surface").mkdir(parents=True)

    bin_name = "wsm-graalvm.exe" if args.platform == "windows-x86_64" else "wsm-graalvm"
    shutil.copy2(binary, pkg / "bin" / bin_name)
    if args.platform == "linux-x86_64":
        dst_bin = pkg / "bin" / bin_name
        dst_bin.chmod(dst_bin.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    for rel in required:
        dst = pkg / "authority" / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(upstream / rel, dst)

    shutil.copy2(root / "README.md", pkg / "README.md")
    shutil.copy2(root / "LICENSE", pkg / "LICENSE")

    (pkg / "run.sh").write_text(
        "#!/usr/bin/env sh\n"
        "set -eu\n"
        'HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)\n'
        'exec "$HERE/bin/wsm-graalvm" "$1" '
        '"$HERE/authority/lib/surface/semantic-registry.lisp" "$HERE/authority"\n',
        encoding="utf-8",
    )
    (pkg / "run.sh").chmod((pkg / "run.sh").stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    (pkg / "run.cmd").write_text(
        "@echo off\r\n"
        "setlocal\r\n"
        'set "HERE=%~dp0"\r\n'
        '"%HERE%bin\\wsm-graalvm.exe" "%~1" "%HERE%authority\\lib\\surface\\semantic-registry.lisp" "%HERE%authority"\r\n',
        encoding="utf-8",
    )

    (pkg / "MY_LISP_PIN.txt").write_text(args.upstream_pin + "\n", encoding="utf-8")
    (pkg / "RELEASE_SHA.txt").write_text(args.repo_sha + "\n", encoding="utf-8")
    (pkg / "RUNNING.txt").write_text(
        f"wsm-graalvm {args.version} ({args.platform})\n\n"
        f"Semantic authority: juv4uk/my-lisp@{args.upstream_pin}\n"
        f"Release source commit: {args.repo_sha}\n\n"
        "Linux/macOS shell:\n"
        "  ./run.sh path/to/program.lisp\n\n"
        "Windows Command Prompt:\n"
        "  run.cmd path\\to\\program.lisp\n\n"
        "Direct invocation:\n"
        "  <binary> <program.lisp> authority/lib/surface/semantic-registry.lisp authority\n",
        encoding="utf-8",
    )

    zip_path = out / f"wsm-graalvm-{args.version}-{args.platform}.zip"
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path in sorted(p for p in pkg.rglob("*") if p.is_file()):
            add_file(zf, path, path.relative_to(pkg).as_posix(), stat.S_IMODE(path.stat().st_mode))

    binary_checksum = out / f"wsm-graalvm-{args.version}-{args.platform}.binary.sha256"
    binary_checksum.write_text(
        f"{sha256(pkg / 'bin' / bin_name)}  {bin_name}\n", encoding="utf-8"
    )
    zip_checksum = out / f"wsm-graalvm-{args.version}-{args.platform}.zip.sha256"
    zip_checksum.write_text(
        f"{sha256(zip_path)}  {zip_path.name}\n", encoding="utf-8"
    )

    print(f"PACKAGE-OK {zip_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
