#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Guard the stable v2 release identity and shrinking safety contracts."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = ROOT / "app" / "build.gradle"
GRADLE_PROPERTIES = ROOT / "gradle.properties"
PROGUARD_RULES = ROOT / "app" / "proguard-rules.pro"
RELEASE_WORKFLOW = ROOT / ".github" / "workflows" / "release.yml"
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


def read_gradle_properties() -> dict[str, str]:
    properties: dict[str, str] = {}
    for raw_line in GRADLE_PROPERTIES.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    return properties


def main() -> int:
    build_gradle = BUILD_GRADLE.read_text(encoding="utf-8")
    properties = read_gradle_properties()
    proguard_rules = PROGUARD_RULES.read_text(encoding="utf-8")
    release_workflow = RELEASE_WORKFLOW.read_text(encoding="utf-8")
    resource_keep = RESOURCE_KEEP.read_text(encoding="utf-8")

    version_code_text = properties.get("appVersionCode", "")
    version_name = properties.get("appVersionName", "")
    if not version_code_text.isdecimal() or int(version_code_text) <= 2_000_001:
        raise AssertionError("appVersionCode must be greater than the alpha01 baseline")
    if re.fullmatch(r"2\.\d+\.\d+(?:-(?:alpha|beta|rc)\d+)?", version_name) is None:
        raise AssertionError("appVersionName must follow the v2 release naming policy")

    require(
        r"^\s*versionCode\s+providers\.gradleProperty\('appVersionCode'\)"
        r"\.get\(\)\.toInteger\(\)\s*$",
        build_gradle,
        "canonical v2 versionCode property",
    )
    require(
        r"^\s*versionName\s+providers\.gradleProperty\('appVersionName'\)"
        r"\.get\(\)\s*$",
        build_gradle,
        "canonical v2 versionName property",
    )
    require(r"^\s*minifyEnabled\s+true\s*$", build_gradle, "R8 minification")
    require(r"^\s*shrinkResources\s+true\s*$", build_gradle, "resource shrinking")
    require(
        r"-keep class com\.baodeep\.hackerskeyboard\.BinaryDictionary\s*\{",
        proguard_rules,
        "JNI BinaryDictionary keep rule",
    )
    require(r'tools:keep="@raw/main"', resource_keep, "dynamically loaded dictionary")
    require(
        r"name: hackers-keyboard-v2-\$\{\{ steps\.sign\.outputs\.version_name \}\}",
        release_workflow,
        "version-derived signed artifact name",
    )
    require(
        r'echo "version_name=\$\{version_name\}"',
        release_workflow,
        "validated release version output",
    )
    if re.search(r"2\.0\.0-(?:alpha|beta|rc)\d+", release_workflow):
        raise AssertionError("Release workflow contains a hard-coded release version")

    forbidden = ("V2_RELEASE_KEYSTORE", "V2_RELEASE_STORE_PASSWORD", "signingConfigs")
    for marker in forbidden:
        if marker in build_gradle:
            raise AssertionError(f"Signing secret/configuration leaked into Gradle: {marker}")

    print(
        "Verified v2 release configuration and shrinking safety contracts "
        f"for {version_name} ({version_code_text})"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(error, file=sys.stderr)
        raise SystemExit(1) from error
