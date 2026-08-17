#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Unit tests for the settings contract verifier."""

from __future__ import annotations

import copy
import importlib.util
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("verify-settings-contract.py")
sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("verify_settings_contract", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"cannot import {SCRIPT_PATH}")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SettingsContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.repository = Path(__file__).resolve().parents[1]
        cls.contract = MODULE.build_contract(cls.repository)

    def test_current_storage_surface_is_explicit(self) -> None:
        self.assertEqual(66, self.contract["xml_entry_count"])
        self.assertEqual(48, len(self.contract["persisted_xml_keys"]))
        self.assertEqual(2, len(self.contract["programmatic_preferences"]))
        self.assertEqual(
            {"boolean", "none", "string"},
            {
                entry["type"]
                for entries in self.contract["xml_entries"].values()
                for entry in entries
            },
        )

    def test_nested_entry_value_resources_are_resolved(self) -> None:
        variants = self.contract["referenced_resource_variants"]
        self.assertEqual("0", variants["@string/settings_key_mode_auto"]["values"])
        self.assertEqual(
            [
                "@string/settings_key_mode_auto",
                "@string/settings_key_mode_always_show",
                "@string/settings_key_mode_always_hide",
            ],
            variants["@array/settings_key_modes_values"]["values"],
        )

    def test_androidx_attribute_namespace_preserves_semantics(self) -> None:
        android_element = ET.fromstring(
            '<CheckBoxPreference xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:key="example" />'
        )
        androidx_element = ET.fromstring(
            '<androidx.preference.CheckBoxPreference '
            'xmlns:app="http://schemas.android.com/apk/res-auto" '
            'app:key="example" />'
        )
        self.assertEqual(
            MODULE.preference_attribute(android_element, "key"),
            MODULE.preference_attribute(androidx_element, "key"),
        )

    def test_androidx_custom_widgets_preserve_string_storage(self) -> None:
        self.assertEqual(
            "string",
            MODULE.value_type(
                "com.baodeep.hackerskeyboard.SeekBarPreferenceStringCompat"
            ),
        )
        self.assertEqual(
            "string",
            MODULE.value_type("com.baodeep.hackerskeyboard.VibratePreferenceCompat"),
        )

    def test_type_mutation_is_reported(self) -> None:
        mutated = copy.deepcopy(self.contract)
        mutated["xml_entries"]["prefs.xml"][2]["type"] = "float"
        differences = MODULE.describe_differences(self.contract, mutated)
        self.assertTrue(
            any("prefs.xml" in difference and ".type" in difference for difference in differences),
            differences,
        )

    def test_removed_key_is_reported(self) -> None:
        mutated = copy.deepcopy(self.contract)
        mutated["persisted_xml_keys"].remove("voice_mode")
        differences = MODULE.describe_differences(self.contract, mutated)
        self.assertTrue(
            any("persisted_xml_keys" in difference for difference in differences),
            differences,
        )


if __name__ == "__main__":
    unittest.main()
