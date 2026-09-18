#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import shutil
import stat
import zipfile

FIXED_TIME = (2020, 1, 1, 0, 0, 0)

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def add_file(zf: zipfile.ZipFile, path: Path, arcname: str, mode: int) -> None:
    info = zipfile.ZipInfo(arcname, FIXED_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3
    info.external_attr = (mode & 0xFFFF) << 16
    with path.open("rb") as handle:
        zf.writestr(info, handle.read())

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--platform", choices=("linux-x86_64", "windows-x86_64"), required=True)
    parser.add_argument("--binary", required=True)
    parser.add_argument("--upstream", required=True)
    parser.add_argument("--upstream-pin", required=True)
    parser.add_argument("--repo-sha", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    root = Path.cwd()
    binary = Path(args.binary)
    if not binary.exists() and args.platform == "windows-x86_64":
        candidate = Path(str(binary) + ".exe")
        if candidate.exists():
            binary = candidate
    if not binary.exists():
        raise SystemExit(f"missing native binary: {binary}")

    upstream = Path(args.upstream)
    authority = [
        "lib/surface/semantic-registry.lisp",
        "lib/canon.lisp",
        "lib/macro.lisp",
        "lib/core.lisp",
    ]
    for rel in authority:
        if not (upstream / rel).is_file():
            raise SystemExit(f"missing pinned authority file: {upstream / rel}")

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    package = out / f"wsm-graalvm-{args.version}-{args.platform}"
    if package.exists():
        shutil.rmtree(package)
    (package / "bin").mkdir(parents=True)
    (package / "authority" / "lib" / "surface").mkdir(parents=True)

    binary_name = "wsm-graalvm.exe" if args.platform == "windows-x86_64" else "wsm-graalvm"
    shutil.copy2(binary, package / "bin" / binary_name)
    if args.platform == "linux-x86_64":
        dst = package / "bin" / binary_name
        dst.chmod(dst.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    for rel in authority:
        dst = package / "authority" / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(upstream / rel, dst)

    shutil.copy2(root / "README.md", package / "README.md")
    shutil.copy2(root / "LICENSE", package / "LICENSE")

    (package / "run.sh").write_text(
        "#!/usr/bin/env sh\n"
        "set -eu\n"
        'HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)\n'
        'TMP=$(mktemp "${TMPDIR:-/tmp}/wsm-graalvm-XXXXXX.lisp")\n'
        'trap "rm -f \"$TMP\"" EXIT HUP INT TERM\n'
        'cat "$HERE/authority/lib/canon.lisp" "$HERE/authority/lib/macro.lisp" "$HERE/authority/lib/core.lisp" "$1" > "$TMP"\n'
        '"$HERE/bin/wsm-graalvm" "$TMP" "$HERE/authority/lib/surface/semantic-registry.lisp" "$HERE/authority"\n',
        encoding="utf-8",
    )
    (package / "run.sh").chmod((package / "run.sh").stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    (package / "run.cmd").write_text(
        "@echo off\r\n"
        "setlocal\r\n"
        'set "HERE=%~dp0"\r\n'
        'set "TMP=%TEMP%\\wsm-graalvm-%RANDOM%%RANDOM%.lisp"\r\n'
        '> "%TMP%" type nul\r\n'
        'type "%HERE%authority\\lib\\canon.lisp" >> "%TMP%"\r\n'
        'type "%HERE%authority\\lib\\macro.lisp" >> "%TMP%"\r\n'
        'type "%HERE%authority\\lib\\core.lisp" >> "%TMP%"\r\n'
        'type "%~1" >> "%TMP%"\r\n'
        '"%HERE%bin\\wsm-graalvm.exe" "%TMP%" "%HERE%authority\\lib\\surface\\semantic-registry.lisp" "%HERE%authority"\r\n'
        'set "RC=%ERRORLEVEL%"\r\n'
        'del /q "%TMP%" >nul 2>nul\r\n'
        'exit /b %RC%\r\n',
        encoding="utf-8",
    )

    (package / "MY_LISP_PIN.txt").write_text(args.upstream_pin + "\n", encoding="utf-8")
    (package / "RELEASE_SHA.txt").write_text(args.repo_sha + "\n", encoding="utf-8")
    (package / "RUNNING.txt").write_text(
        f"wsm-graalvm {args.version} ({args.platform})\n\n"
        f"Semantic authority: juv4uk/my-lisp@{args.upstream_pin}\n"
        f"Release source commit: {args.repo_sha}\n\n"
        "Linux/macOS shell:\n"
        "  ./run.sh path/to/program.lisp\n"
        "  launcher bootstraps canon -> macro -> core first\n\n"
        "Windows Command Prompt:\n"
        "  run.cmd path\\to\\program.lisp\n"
        "  launcher bootstraps canon -> macro -> core first\n"
        encoding="utf-8",
    )

    assets = out / "assets"
    assets.mkdir(parents=True, exist_ok=True)
    raw_name = f"wsm-graalvm-{args.version}-{args.platform}" + (".exe" if args.platform == "windows-x86_64" else "")
    raw_binary = assets / raw_name
    shutil.copy2(package / "bin" / binary_name, raw_binary)

    zip_path = out / f"wsm-graalvm-{args.version}-{args.platform}.zip"
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path in sorted(p for p in package.rglob("*") if p.is_file()):
            add_file(zf, path, path.relative_to(package).as_posix(), stat.S_IMODE(path.stat().st_mode))

    (out / f"wsm-graalvm-{args.version}-{args.platform}.binary.sha256").write_text(
        f"{sha256(raw_binary)}  {raw_binary.name}\n", encoding="utf-8"
    )
    (out / f"wsm-graalvm-{args.version}-{args.platform}.zip.sha256").write_text(
        f"{sha256(zip_path)}  {zip_path.name}\n", encoding="utf-8"
    )

    print(f"PACKAGE-OK {zip_path}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())