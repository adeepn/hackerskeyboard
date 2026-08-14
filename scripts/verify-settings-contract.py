#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Verify the persisted settings contract before and during AndroidX migration."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
RESOURCE_NAMESPACE = "http://schemas.android.com/apk/res-auto"
ATTRIBUTE_NAMESPACES = (ANDROID_NAMESPACE, RESOURCE_NAMESPACE)

PREFERENCE_RESOURCES = (
    "language_prefs.xml",
    "prefs.xml",
    "prefs_actions.xml",
    "prefs_feedback.xml",
    "prefs_for_debug.xml",
    "prefs_view.xml",
)
FIXTURE_PATH = Path("docs/settings-contract.json")

PROGRAMMATIC_PREFERENCES = (
    {
        "key": "input_language",
        "type": "string",
        "constant": "PREF_INPUT_LANGUAGE",
        "readers": ["LanguageSwitcher.java"],
        "writers": ["LanguageSwitcher.java"],
    },
    {
        "key": "selected_languages",
        "type": "string",
        "constant": "PREF_SELECTED_LANGUAGES",
        "readers": ["InputLanguageSelection.java", "LanguageSwitcher.java"],
        "writers": ["InputLanguageSelection.java"],
    },
)


def simple_name(tag: str) -> str:
    return tag.rsplit(".", 1)[-1]


def preference_attribute(element: ET.Element, name: str) -> str | None:
    values = {
        element.attrib[f"{{{namespace}}}{name}"]
        for namespace in ATTRIBUTE_NAMESPACES
        if f"{{{namespace}}}{name}" in element.attrib
    }
    if len(values) > 1:
        raise ValueError(
            f"{simple_name(element.tag)} has conflicting android/app {name} values"
        )
    return next(iter(values), None)


def value_type(tag: str) -> str:
    name = simple_name(tag)
    if name == "CheckBoxPreference":
        return "boolean"
    if name in {
        "AutoSummaryEditTextPreference",
        "AutoSummaryListPreference",
        "EditTextPreference",
        "ListPreference",
        "SeekBarPreferenceString",
        "VibratePreference",
    }:
        return "string"
    if name == "SeekBarPreference":
        return "float"
    if name in {"Preference", "PreferenceCategory", "PreferenceScreen"}:
        return "none"
    raise ValueError(f"unknown keyed preference widget: {tag}")


def resource_value(element: ET.Element) -> Any:
    name = simple_name(element.tag)
    if name in {"array", "integer-array", "string-array"}:
        return ["".join(item.itertext()).strip() for item in element]
    return "".join(element.itertext()).strip()


def referenced_resources(
    resources_root: Path, references: set[str]
) -> dict[str, dict[str, Any]]:
    catalog: dict[str, dict[str, Any]] = {}
    for values_dir in sorted(resources_root.glob("values*")):
        if not values_dir.is_dir():
            continue
        for path in sorted(values_dir.glob("*.xml")):
            root = ET.parse(path).getroot()
            for element in root:
                resource_type = simple_name(element.tag)
                if resource_type in {"string-array", "integer-array"}:
                    lookup_type = "array"
                else:
                    lookup_type = resource_type
                name = element.attrib.get("name")
                if lookup_type not in {"array", "bool", "string"} or name is None:
                    continue
                reference = f"@{lookup_type}/{name}"
                qualifier = values_dir.name
                variants = catalog.setdefault(reference, {})
                if qualifier in variants:
                    raise ValueError(
                        f"duplicate {reference} in resource qualifier {qualifier}"
                    )
                variants[qualifier] = resource_value(element)

    resolved: dict[str, dict[str, Any]] = {}
    pending = set(references)
    while pending:
        reference = sorted(pending)[0]
        pending.remove(reference)
        variants = catalog.get(reference)
        if variants is None:
            raise ValueError(f"unresolved preference resource: {reference}")
        resolved[reference] = variants
        for value in variants.values():
            values = value if isinstance(value, list) else [value]
            for nested in values:
                if re.fullmatch(r"@(bool|string|array)/[A-Za-z0-9_]+", nested):
                    if nested not in resolved:
                        pending.add(nested)
    return dict(sorted(resolved.items()))


def verify_programmatic_preferences(
    java_root: Path, persisted_xml_keys: set[str]
) -> list[dict[str, Any]]:
    sources = {
        path.name: path.read_text(encoding="utf-8")
        for path in sorted(java_root.glob("*.java"))
    }
    latin_ime = sources["LatinIME.java"]
    errors: list[str] = []
    contract: list[dict[str, Any]] = []
    constants: dict[str, str] = {}
    declaration_pattern = re.compile(
        r"\b(?P<constant>PREF_[A-Z0-9_]+)\s*=\s*\"(?P<key>[^\"]+)\""
    )
    for source in sources.values():
        for match in declaration_pattern.finditer(source):
            constant = match.group("constant")
            key = match.group("key")
            previous = constants.setdefault(constant, key)
            if previous != key:
                errors.append(
                    f"{constant}: conflicting values {previous!r} and {key!r}"
                )

    access_pattern = re.compile(
        r"\.(?P<operation>get|put)(?P<type>Boolean|String|StringSet|Float|Int|Long)"
        r"\(\s*(?:LatinIME\.)?(?P<constant>PREF_[A-Z0-9_]+)"
    )
    discovered_code_only: dict[str, set[str]] = {}
    for filename, source in sources.items():
        for match in access_pattern.finditer(source):
            constant = match.group("constant")
            key = constants.get(constant)
            if key is None:
                errors.append(f"{filename}: cannot resolve {constant}")
                continue
            if key in persisted_xml_keys:
                continue
            value_kind = match.group("type")
            normalized_type = value_kind[0].lower() + value_kind[1:]
            discovered_code_only.setdefault(key, set()).add(normalized_type)

    expected_code_only = {item["key"] for item in PROGRAMMATIC_PREFERENCES}
    if set(discovered_code_only) != expected_code_only:
        errors.append(
            "programmatic preference keys changed: "
            f"{sorted(discovered_code_only)} != {sorted(expected_code_only)}"
        )

    for item in PROGRAMMATIC_PREFERENCES:
        declaration = re.compile(
            rf"\b{re.escape(item['constant'])}\s*=\s*\"{re.escape(item['key'])}\""
        )
        if not declaration.search(latin_ime):
            errors.append(
                f"LatinIME.java: missing {item['constant']} = \"{item['key']}\""
            )
        discovered_types = discovered_code_only.get(item["key"], set())
        if discovered_types != {item["type"]}:
            errors.append(
                f"{item['key']}: storage types {sorted(discovered_types)} "
                f"!= {[item['type']]}"
            )
        read_defaults: set[str] = set()
        read_pattern = re.compile(
            rf"getString\(\s*LatinIME\.{re.escape(item['constant'])}\s*,\s*"
            rf"(?P<default>null|\"\")\s*\)"
        )
        for reader in item["readers"]:
            matches = list(read_pattern.finditer(sources[reader]))
            if not matches:
                errors.append(f"{reader}: missing reader for {item['key']}")
            for match in matches:
                read_defaults.add(
                    "empty string" if match.group("default") == '""' else "null"
                )
        for writer in item["writers"]:
            token = f"putString(LatinIME.{item['constant']}"
            if token not in sources[writer]:
                errors.append(f"{writer}: missing writer for {item['key']}")
        contract.append({**item, "read_defaults": sorted(read_defaults)})
    if errors:
        raise ValueError("; ".join(errors))
    return contract


def build_contract(repository: Path) -> dict[str, Any]:
    app = repository / "app"
    resources_root = app / "src" / "main" / "res"
    xml_root = resources_root / "xml"
    java_root = (
        app
        / "src"
        / "main"
        / "java"
        / "com"
        / "baodeep"
        / "hackerskeyboard"
    )
    build_gradle = (app / "build.gradle").read_text(encoding="utf-8")
    application_id_match = re.search(
        r"(?m)^\s*applicationId\s+['\"]([^'\"]+)['\"]", build_gradle
    )
    if application_id_match is None:
        raise ValueError("app/build.gradle: applicationId not found")
    application_id = application_id_match.group(1)

    entries_by_resource: dict[str, list[dict[str, Any]]] = {}
    navigation_actions: dict[str, list[str]] = {}
    references: set[str] = set()
    entry_count = 0
    persisted_keys: set[str] = set()

    for filename in PREFERENCE_RESOURCES:
        path = xml_root / filename
        root = ET.parse(path).getroot()
        entries: list[dict[str, Any]] = []
        actions: list[str] = []
        for element in root.iter():
            action = preference_attribute(element, "action")
            if action is not None:
                actions.append(action)
            key = preference_attribute(element, "key")
            if key is None:
                continue
            kind = value_type(element.tag)
            default = preference_attribute(element, "defaultValue")
            entry_values = preference_attribute(element, "entryValues")
            display_entries = preference_attribute(element, "entries")
            dependency = preference_attribute(element, "dependency")
            for reference in (default, entry_values):
                if reference and reference.startswith("@"):
                    references.add(reference)
            entries.append(
                {
                    "key": key,
                    "type": kind,
                    "widget": simple_name(element.tag),
                    "default": default,
                    "entries": display_entries,
                    "entry_values": entry_values,
                    "dependency": dependency,
                }
            )
            entry_count += 1
            if kind != "none":
                persisted_keys.add(key)
        entries_by_resource[filename] = entries
        navigation_actions[filename] = actions

    return {
        "_license": "SPDX-License-Identifier: Apache-2.0",
        "schema": 1,
        "application_id": application_id,
        "default_shared_preferences_name": f"{application_id}_preferences",
        "preference_resources": list(PREFERENCE_RESOURCES),
        "xml_entry_count": entry_count,
        "persisted_xml_keys": sorted(persisted_keys),
        "xml_entries": entries_by_resource,
        "navigation_actions": navigation_actions,
        "programmatic_preferences": verify_programmatic_preferences(
            java_root, persisted_keys
        ),
        "referenced_resource_variants": referenced_resources(
            resources_root, references
        ),
    }


def describe_differences(expected: Any, actual: Any, path: str = "contract") -> list[str]:
    if type(expected) is not type(actual):
        return [
            f"{path}: type changed from {type(expected).__name__} "
            f"to {type(actual).__name__}"
        ]
    if isinstance(expected, dict):
        differences: list[str] = []
        expected_keys = set(expected)
        actual_keys = set(actual)
        for key in sorted(expected_keys - actual_keys):
            differences.append(f"{path}.{key}: missing")
        for key in sorted(actual_keys - expected_keys):
            differences.append(f"{path}.{key}: unexpected")
        for key in sorted(expected_keys & actual_keys):
            differences.extend(
                describe_differences(expected[key], actual[key], f"{path}.{key}")
            )
        return differences
    if isinstance(expected, list):
        if len(expected) != len(actual):
            return [f"{path}: length changed from {len(expected)} to {len(actual)}"]
        differences = []
        for index, (expected_item, actual_item) in enumerate(zip(expected, actual)):
            differences.extend(
                describe_differences(
                    expected_item, actual_item, f"{path}[{index}]"
                )
            )
        return differences
    if expected != actual:
        return [f"{path}: {expected!r} != {actual!r}"]
    return []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--print-contract",
        action="store_true",
        help="print the derived contract instead of comparing it with the fixture",
    )
    args = parser.parse_args()

    repository = Path(__file__).resolve().parents[1]
    try:
        actual = build_contract(repository)
    except (ET.ParseError, KeyError, OSError, ValueError) as error:
        print(f"Settings contract extraction failed: {error}", file=sys.stderr)
        return 1

    if args.print_contract:
        print(json.dumps(actual, ensure_ascii=False, indent=2, sort_keys=True))
        return 0

    fixture = repository / FIXTURE_PATH
    try:
        expected = json.loads(fixture.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as error:
        print(f"Settings contract fixture cannot be read: {error}", file=sys.stderr)
        return 1

    differences = describe_differences(expected, actual)
    if differences:
        print("Settings contract verification failed:", file=sys.stderr)
        for difference in differences[:50]:
            print(f"- {difference}", file=sys.stderr)
        if len(differences) > 50:
            print(f"- ... and {len(differences) - 50} more", file=sys.stderr)
        print(
            "Review the semantic change, then regenerate with "
            "scripts/verify-settings-contract.py --print-contract.",
            file=sys.stderr,
        )
        return 1

    print(
        "Settings contract verified: "
        f"{actual['xml_entry_count']} XML entries; "
        f"{len(actual['persisted_xml_keys'])} persisted XML keys; "
        f"{len(actual['programmatic_preferences'])} programmatic keys"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
