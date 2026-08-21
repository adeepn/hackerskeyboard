#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Verify the least-privileged Android manifest component contract."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"


def android_attribute(name: str) -> str:
    return f"{{{ANDROID_NAMESPACE}}}{name}"


EXPECTED_COMPONENTS = {
    ("service", "LatinIME"): True,
    ("activity", "Main"): True,
    ("activity", "LatinIMESettings"): False,
    ("activity", "InputLanguageSelection"): False,
    ("activity", "PrefScreenActions"): False,
    ("activity", "PrefScreenView"): False,
    ("activity", "PrefScreenFeedback"): False,
}

EXPECTED_ACTIONS = {
    ("service", "LatinIME"): {"android.view.InputMethod"},
    ("activity", "Main"): {"android.intent.action.MAIN"},
    ("activity", "LatinIMESettings"): {
        "android.intent.action.MAIN",
        "${applicationId}.SETTINGS",
    },
    ("activity", "InputLanguageSelection"): {
        "android.intent.action.MAIN",
        "${applicationId}.INPUT_LANGUAGE_SELECTION",
    },
    ("activity", "PrefScreenActions"): {
        "android.intent.action.MAIN",
        "${applicationId}.PREFS_ACTIONS",
    },
    ("activity", "PrefScreenView"): {
        "android.intent.action.MAIN",
        "${applicationId}.PREFS_VIEW",
    },
    ("activity", "PrefScreenFeedback"): {
        "android.intent.action.MAIN",
        "${applicationId}.PREFS_FEEDBACK",
    },
}

EXPECTED_CATEGORIES = {
    ("service", "LatinIME"): set(),
    ("activity", "Main"): {"android.intent.category.LAUNCHER"},
    ("activity", "LatinIMESettings"): {"android.intent.category.DEFAULT"},
    ("activity", "InputLanguageSelection"): {"android.intent.category.DEFAULT"},
    ("activity", "PrefScreenActions"): {"android.intent.category.DEFAULT"},
    ("activity", "PrefScreenView"): {"android.intent.category.DEFAULT"},
    ("activity", "PrefScreenFeedback"): {"android.intent.category.DEFAULT"},
}


def component_actions(component: ET.Element) -> set[str]:
    return {
        action.attrib[android_attribute("name")]
        for action in component.findall("./intent-filter/action")
        if android_attribute("name") in action.attrib
    }


def component_categories(component: ET.Element) -> set[str]:
    return {
        category.attrib[android_attribute("name")]
        for category in component.findall("./intent-filter/category")
        if android_attribute("name") in category.attrib
    }


def verify(manifest_path: Path, method_path: Path) -> list[str]:
    errors: list[str] = []
    application = ET.parse(manifest_path).getroot().find("application")
    if application is None:
        return ["AndroidManifest.xml: application element is missing"]

    components: dict[tuple[str, str], ET.Element] = {}
    for component_type in ("activity", "service", "receiver", "provider"):
        for component in application.findall(component_type):
            name = component.attrib.get(android_attribute("name"), "")
            key = (component_type, name)
            if key in components:
                errors.append(f"{component_type} {name}: duplicate declaration")
            components[key] = component
            if component.find("intent-filter") is not None:
                exported = component.attrib.get(android_attribute("exported"))
                if exported not in {"true", "false"}:
                    errors.append(
                        f"{component_type} {name}: intent filter requires explicit "
                        "android:exported"
                    )
                if key not in EXPECTED_COMPONENTS:
                    errors.append(
                        f"{component_type} {name}: filtered component has no reviewed "
                        "export policy"
                    )

    for key, expected_exported in EXPECTED_COMPONENTS.items():
        component = components.get(key)
        component_type, name = key
        if component is None:
            errors.append(f"{component_type} {name}: required component is missing")
            continue

        expected_value = str(expected_exported).lower()
        actual_value = component.attrib.get(android_attribute("exported"))
        if actual_value != expected_value:
            errors.append(
                f"{component_type} {name}: android:exported must be {expected_value}, "
                f"found {actual_value!r}"
            )

        actual_actions = component_actions(component)
        expected_actions = EXPECTED_ACTIONS[key]
        if actual_actions != expected_actions:
            errors.append(
                f"{component_type} {name}: actions changed: "
                f"{sorted(actual_actions)} != {sorted(expected_actions)}"
            )

        actual_categories = component_categories(component)
        expected_categories = EXPECTED_CATEGORIES[key]
        if actual_categories != expected_categories:
            errors.append(
                f"{component_type} {name}: categories changed: "
                f"{sorted(actual_categories)} != {sorted(expected_categories)}"
            )

    ime = components.get(("service", "LatinIME"))
    if ime is not None:
        permission = ime.attrib.get(android_attribute("permission"))
        if permission != "android.permission.BIND_INPUT_METHOD":
            errors.append(
                "service LatinIME: android.permission.BIND_INPUT_METHOD is required"
            )
        metadata = {
            item.attrib.get(android_attribute("name")): item.attrib.get(
                android_attribute("resource")
            )
            for item in ime.findall("meta-data")
        }
        if metadata.get("android.view.im") != "@xml/method":
            errors.append("service LatinIME: android.view.im metadata must use @xml/method")

    method = ET.parse(method_path).getroot()
    settings_activity = method.attrib.get(android_attribute("settingsActivity"))
    expected_settings = "com.baodeep.hackerskeyboard.LatinIMESettings"
    if settings_activity != expected_settings:
        errors.append(
            "method.xml: settingsActivity must be "
            f"{expected_settings}, found {settings_activity!r}"
        )

    return errors


def main() -> int:
    repository = Path(__file__).resolve().parents[1]
    errors = verify(
        repository / "app" / "src" / "main" / "AndroidManifest.xml",
        repository / "app" / "src" / "main" / "res" / "xml" / "method.xml",
    )
    if errors:
        print("Manifest component verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "Manifest components verified: IME and launcher exported; "
        "five settings activities internal"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
