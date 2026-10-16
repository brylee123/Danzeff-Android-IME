#!/usr/bin/env python3
"""Format Java with pinned google-java-format, then check JDK syntax-tree conventions."""

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.28.0"
SHA256 = "32342e7c1b4600f80df3471da46aee8012d3e1445d5ea1be1fb71289b07cc735"
URL = (
    "https://github.com/google/google-java-format/releases/download/"
    f"v{VERSION}/google-java-format-{VERSION}-all-deps.jar"
)
SOURCE_ROOTS = ("app", "core", "test", "tests", "benchmark", "benchmarks", "tools")
EXCLUDED = {"build", ".gradle", ".git", ".idea", "out", "target", "__pycache__"}


def sources():
    return sorted(
        path
        for directory in SOURCE_ROOTS
        for path in (ROOT / directory).rglob("*.java")
        if not EXCLUDED.intersection(path.relative_to(ROOT).parts)
    )


def digest(path):
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def formatter(path, cache):
    jar = path.resolve() if path else cache / f"google-java-format-{VERSION}-all-deps.jar"
    if not jar.exists():
        if path:
            raise ValueError(f"Formatter jar does not exist: {jar}")
        # Download to a temporary file. An incomplete or unverified jar is never executed.
        print(f"Downloading official google-java-format {VERSION}...", flush=True)
        with tempfile.NamedTemporaryFile(dir=cache, suffix=".download", delete=False) as out:
            temporary = Path(out.name)
            try:
                with urllib.request.urlopen(URL, timeout=60) as response:
                    shutil.copyfileobj(response, out)
            except BaseException:
                out.close()
                temporary.unlink(missing_ok=True)
                raise
        try:
            if digest(temporary) != SHA256:
                raise ValueError("Downloaded formatter SHA-256 does not match the pinned artifact.")
            temporary.replace(jar)
        finally:
            temporary.unlink(missing_ok=True)
    if digest(jar) != SHA256:
        raise ValueError(f"Formatter SHA-256 mismatch: {jar}; expected {SHA256}")
    return jar


def java_executable(explicit):
    if explicit:
        return str(explicit.resolve())
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        return str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java"))
    java = shutil.which("java")
    if not java:
        raise ValueError("JDK 17+ required: set JAVA_HOME, put java on PATH, or use --java PATH.")
    return java


def run(args, cwd=ROOT):
    return subprocess.run([str(arg) for arg in args], cwd=cwd, check=False).returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Report formatting drift without edits")
    parser.add_argument("--formatter-jar", type=Path, help="Use a checksum-verified local jar offline")
    parser.add_argument("--java", type=Path, help="Path to a JDK 17+ java executable")
    args = parser.parse_args()
    cache = ROOT / ".gradle" / "style"
    cache.mkdir(parents=True, exist_ok=True)
    jar = formatter(args.formatter_jar, cache)
    java = java_executable(args.java)
    files = sources()
    if not files:
        raise ValueError("No Java source files found.")
    with tempfile.TemporaryDirectory(dir=cache, prefix="check-") as work:
        manifest = Path(work) / "sources.txt"
        manifest.write_text("".join(f"{path}\n" for path in files), encoding="utf-8")
        # Use the JDK launcher argfile parser, which handles quotes and paths with spaces.
        # The formatter's own @file expansion splits on whitespace and cannot quote paths.
        arguments = Path(work) / "formatter.args"
        mode = ["--dry-run", "--set-exit-if-changed"] if args.check else ["--replace"]
        formatter_args = ["-jar", jar.as_posix(), "--aosp", *mode]
        formatter_args.extend(path.as_posix() for path in files)
        arguments.write_text(
            "".join('"' + arg.replace("\\", "\\\\").replace('"', '\\"') + '"\n'
                    for arg in formatter_args), encoding="utf-8"
        )
        formatted = run([java, f"@{arguments}"])
        checked = run([java, "--source", "17", ROOT / "tools/JavaStyleCheck.java", manifest])
        if formatted or checked:
            return 1
    print(f"Java format {'check' if args.check else 'and style check'} passed ({len(files)} files).")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, urllib.error.URLError) as error:
        print(f"Java style tool: {error}", file=sys.stderr)
        sys.exit(1)
