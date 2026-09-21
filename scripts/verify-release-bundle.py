#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Validate an unsigned release AAB before the separate Play signing step."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

from verify_package_visibility import verify_queries
from verify_native_alignment import verify_archive as verify_native_alignment

ROOT = Path(__file__).resolve().parents[1]
# Official google/bundletool release asset and GitHub asset digest, 2026-09-14.
BUNDLETOOL_VERSION = "1.18.3"
BUNDLETOOL_SHA256 = "a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
BUNDLETOOL_URL = (
    "https://github.com/google/bundletool/releases/download/"
    f"{BUNDLETOOL_VERSION}/bundletool-all-{BUNDLETOOL_VERSION}.jar"
)
ANDROID = "{http://schemas.android.com/apk/res/android}"
ABIS = {"armeabi-v7a", "arm64-v8a", "x86", "x86_64"}


def verify_tool(path: Path) -> None:
    if hashlib.sha256(path.read_bytes()).hexdigest() != BUNDLETOOL_SHA256:
        raise ValueError("bundletool SHA-256 mismatch; refusing to execute JAR")


def get_bundletool(path: Path | None) -> Path:
    if path is not None:
        verify_tool(path)
        return path
    cache = ROOT / ".gradle" / "bundletool"
    cache.mkdir(parents=True, exist_ok=True)
    cached = cache / f"bundletool-all-{BUNDLETOOL_VERSION}.jar"
    if not cached.exists():
        with tempfile.NamedTemporaryFile(dir=cache, suffix=".jar", delete=False) as output:
            temporary = Path(output.name)
            try:
                with urllib.request.urlopen(BUNDLETOOL_URL, timeout=120) as response:
                    while chunk := response.read(1024 * 1024):
                        output.write(chunk)
                output.close()
                verify_tool(temporary)
                temporary.replace(cached)
            finally:
                temporary.unlink(missing_ok=True)
    verify_tool(cached)
    return cached


def expected_identity() -> dict[str, str]:
    properties = dict(
        line.split("=", 1)
        for line in (ROOT / "gradle.properties").read_text().splitlines()
        if line and not line.startswith("#") and "=" in line
    )
    build = (ROOT / "app" / "build.gradle").read_text()
    identity = {
        "package": "com.baodeep.hackerskeyboard",
        "versionCode": properties["appVersionCode"],
        "versionName": properties["appVersionName"],
    }
    for sdk in ("minSdk", "targetSdk"):
        matches = re.findall(rf"^\s*{sdk}\s+(\d+)\s*$", build, re.MULTILINE)
        if len(matches) != 1:
            raise ValueError(f"Expected exactly one pinned {sdk} in app/build.gradle")
        identity[sdk] = matches[0]
    return identity


def verify_manifest(xml: str, expected: dict[str, str]) -> None:
    manifest = ET.fromstring(xml)
    if manifest.tag != "manifest" or manifest.get("package") != expected["package"]:
        raise ValueError("Unexpected bundle application ID")
    verify_queries(manifest)
    for field in ("versionCode", "versionName"):
        if manifest.get(ANDROID + field) != expected[field]:
            raise ValueError(f"Bundle {field} differs from gradle.properties")
    application = manifest.find("application")
    if application is None or application.get(ANDROID + "debuggable", "false") != "false":
        raise ValueError("Bundle must contain a non-debuggable application")
    sdk = manifest.find("uses-sdk")
    for field in ("minSdk", "targetSdk"):
        if sdk is None or sdk.get(ANDROID + field + "Version") != expected[field]:
            raise ValueError(f"Bundle {field} differs from app/build.gradle")


def verify_archive(path: Path) -> None:
    with zipfile.ZipFile(path) as bundle:
        if bundle.testzip() is not None:
            raise ValueError("Corrupt AAB ZIP entry")
        names = bundle.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate AAB entries")
        for required in ("BundleConfig.pb", "base/manifest/AndroidManifest.xml", "base/dex/classes.dex"):
            if required not in names or bundle.getinfo(required).file_size == 0:
                raise ValueError(f"Missing or empty AAB entry: {required}")
        libraries = {
            name for name in names
            if name.startswith("base/lib/") and name.endswith("/libjni_pckeyboard.so")
        }
        expected = {f"base/lib/{abi}/libjni_pckeyboard.so" for abi in ABIS}
        if libraries != expected:
            raise ValueError("Bundle JNI ABI set differs from the four supported ABIs")
        for name in libraries:
            with bundle.open(name) as library:
                if library.read(4) != b"\x7fELF":
                    raise ValueError(f"Invalid native ELF entry: {name}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--bundletool", type=Path, help="Use an already downloaded, checksum-verified JAR")
    args = parser.parse_args()
    verify_archive(args.bundle)
    verify_native_alignment(args.bundle)
    tool = get_bundletool(args.bundletool)
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / "java") if java_home else "java"
    command = [java, "-jar", str(tool)]
    subprocess.run(command + ["validate", f"--bundle={args.bundle}"], check=True)
    manifest = subprocess.run(
        command + ["dump", "manifest", f"--bundle={args.bundle}", "--module=base"],
        check=True, capture_output=True, text=True,
    ).stdout
    expected = expected_identity()
    verify_manifest(manifest, expected)
    print(f"Verified release AAB: {expected}")
    print(f"aab_sha256={hashlib.sha256(args.bundle.read_bytes()).hexdigest()}")
    print("Unsigned build validation only; Play acceptance and signing are separate checks.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, zipfile.BadZipFile, ET.ParseError, subprocess.CalledProcessError) as error:
        print(f"Release AAB verification failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
