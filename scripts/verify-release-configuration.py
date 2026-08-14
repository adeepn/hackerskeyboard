#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Guard the stable v2 release identity and shrinking safety contracts."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = ROOT / "app" / "build.gradle"
PROGUARD_RULES = ROOT / "app" / "proguard-rules.pro"
RESOURCE_KEEP = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "res"
    / "raw"
    / "com_baodeep_hackerskeyboard_keep.xml"
)


def require(pattern: str, text: str, description: str) -> None:
    if re.search(pattern, text, flags=re.MULTILINE) is None:
        raise AssertionError(f"Missing release contract: {description}")


def main() -> int:
    build_gradle = BUILD_GRADLE.read_text(encoding="utf-8")
    proguard_rules = PROGUARD_RULES.read_text(encoding="utf-8")
    resource_keep = RESOURCE_KEEP.read_text(encoding="utf-8")

    require(r"^\s*versionCode\s+2000001\s*$", build_gradle, "v2 versionCode")
    require(
        r'^\s*versionName\s+["\']2\.0\.0-alpha01["\']\s*$',
        build_gradle,
        "v2 versionName",
    )
    require(r"^\s*minifyEnabled\s+true\s*$", build_gradle, "R8 minification")
    require(r"^\s*shrinkResources\s+true\s*$", build_gradle, "resource shrinking")
    require(
        r"-keep class com\.baodeep\.hackerskeyboard\.BinaryDictionary\s*\{",
        proguard_rules,
        "JNI BinaryDictionary keep rule",
    )
    require(r'tools:keep="@raw/main"', resource_keep, "dynamically loaded dictionary")

    forbidden = ("V2_RELEASE_KEYSTORE", "V2_RELEASE_STORE_PASSWORD", "signingConfigs")
    for marker in forbidden:
        if marker in build_gradle:
            raise AssertionError(f"Signing secret/configuration leaked into Gradle: {marker}")

    print("Verified v2 release configuration and shrinking safety contracts")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(error, file=sys.stderr)
        raise SystemExit(1) from error
