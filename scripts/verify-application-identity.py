#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Guard the permanent v2 identity and legacy compatibility exceptions."""

from __future__ import annotations

import hashlib
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


APPLICATION_ID = "com.baodeep.hackerskeyboard"
LEGACY_APPLICATION_ID = "org.pocketworkstation.pckeyboard"
LEGACY_PACKAGE_PATH = "org/pocketworkstation/pckeyboard"
LEGACY_PACKAGE_TOKEN = "org_pocketworkstation_pckeyboard"
LEGACY_DICTIONARY_ACTION = "org.pocketworkstation.DICT"
PREFERENCE_KEY_COUNT = 66
PREFERENCE_KEYS_SHA256 = (
    "892fcfdad06f2326d76c436ae074a76a299ef7e7ebf5c4d2e5b35d00ba764ba6"
)
ANDROID_KEY = "{http://schemas.android.com/apk/res/android}key"


def main() -> int:
    repository = Path(__file__).resolve().parents[1]
    app = repository / "app"
    errors: list[str] = []

    build_gradle = (app / "build.gradle").read_text(encoding="utf-8")
    for property_name in ("namespace", "applicationId"):
        pattern = rf"(?m)^\s*{property_name}\s+['\"]{re.escape(APPLICATION_ID)}['\"]\s*$"
        if not re.search(pattern, build_gradle):
            errors.append(f"app/build.gradle: {property_name} must be {APPLICATION_ID}")

    text_suffixes = {
        ".c",
        ".cc",
        ".cmake",
        ".cpp",
        ".gradle",
        ".h",
        ".hpp",
        ".java",
        ".kt",
        ".kts",
        ".pro",
        ".properties",
        ".txt",
        ".xml",
    }
    for path in sorted(app.rglob("*")):
        if not path.is_file() or path.suffix not in text_suffixes or "build" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        if any(
            legacy in text
            for legacy in (
                LEGACY_APPLICATION_ID,
                LEGACY_PACKAGE_PATH,
                LEGACY_PACKAGE_TOKEN,
            )
        ):
            errors.append(
                f"{path.relative_to(repository)}: obsolete application identity remains"
            )

    package_path = Path(*APPLICATION_ID.split("."))
    for source_set in ("main", "androidTest"):
        java_root = app / "src" / source_set / "java"
        expected_root = java_root / package_path
        if not expected_root.is_dir():
            errors.append(
                f"{expected_root.relative_to(repository)}: expected package directory is missing"
            )
        for path in sorted(java_root.rglob("*.java")):
            text = path.read_text(encoding="utf-8")
            if f"package {APPLICATION_ID};" not in text:
                errors.append(
                    f"{path.relative_to(repository)}: unexpected Java package declaration"
                )
            if expected_root not in path.parents:
                errors.append(
                    f"{path.relative_to(repository)}: Java source is outside the v2 package path"
                )

    plugin_manager = next(
        (app / "src" / "main" / "java" / package_path).glob("PluginManager.java"),
        None,
    )
    if plugin_manager is None:
        errors.append("PluginManager.java is missing")
    else:
        plugin_text = plugin_manager.read_text(encoding="utf-8")
        if plugin_text.count(LEGACY_DICTIONARY_ACTION) != 1:
            errors.append(
                "PluginManager.java must preserve exactly one legacy dictionary action"
            )

    native_source = app / "src" / "main" / "cpp" / (
        APPLICATION_ID.replace(".", "_") + "_BinaryDictionary.cpp"
    )
    native_class = APPLICATION_ID.replace(".", "/") + "/BinaryDictionary"
    if not native_source.is_file():
        errors.append(
            f"{native_source.relative_to(repository)}: renamed JNI source is missing"
        )
    elif native_class not in native_source.read_text(encoding="utf-8"):
        errors.append(f"{native_source.relative_to(repository)}: JNI class path is stale")

    preference_keys: list[str] = []
    for path in sorted((app / "src" / "main" / "res" / "xml").glob("prefs*.xml")):
        for element in ET.parse(path).iter():
            if ANDROID_KEY in element.attrib:
                preference_keys.append(element.attrib[ANDROID_KEY])
    preference_keys.sort()
    preference_digest = hashlib.sha256("\n".join(preference_keys).encode()).hexdigest()
    if len(preference_keys) != PREFERENCE_KEY_COUNT:
        errors.append(
            f"preference key count changed: {len(preference_keys)} != {PREFERENCE_KEY_COUNT}"
        )
    if preference_digest != PREFERENCE_KEYS_SHA256:
        errors.append(f"preference key set changed: {preference_digest}")

    if errors:
        print("Application identity verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        f"Application identity verified: {APPLICATION_ID}; "
        f"legacy dictionary action preserved; {len(preference_keys)} preference keys unchanged"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
