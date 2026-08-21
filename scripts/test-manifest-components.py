#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Mutation tests for the Android manifest component verifier."""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("verify-manifest-components.py")
sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("verify_manifest_components", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"cannot import {SCRIPT_PATH}")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ManifestComponentsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        repository = Path(__file__).resolve().parents[1]
        cls.manifest = (
            repository / "app" / "src" / "main" / "AndroidManifest.xml"
        ).read_text(encoding="utf-8")
        cls.method = (
            repository / "app" / "src" / "main" / "res" / "xml" / "method.xml"
        ).read_text(encoding="utf-8")

    def verify(self, manifest: str | None = None, method: str | None = None) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest_path = root / "AndroidManifest.xml"
            method_path = root / "method.xml"
            manifest_path.write_text(manifest or self.manifest, encoding="utf-8")
            method_path.write_text(method or self.method, encoding="utf-8")
            return MODULE.verify(manifest_path, method_path)

    def test_current_component_contract_is_valid(self) -> None:
        self.assertEqual([], self.verify())

    def test_missing_exported_declaration_is_rejected(self) -> None:
        mutated = self.manifest.replace(
            '                android:exported="true"\n', "", 1
        )
        errors = self.verify(manifest=mutated)
        self.assertTrue(any("LatinIME" in error and "exported" in error for error in errors))

    def test_non_exported_ime_service_is_rejected(self) -> None:
        mutated = self.manifest.replace(
            '<service android:name="LatinIME"\n'
            '                android:label="@string/english_ime_name"\n'
            '                android:exported="true"',
            '<service android:name="LatinIME"\n'
            '                android:label="@string/english_ime_name"\n'
            '                android:exported="false"',
        )
        errors = self.verify(manifest=mutated)
        self.assertTrue(any("LatinIME" in error and "must be true" in error for error in errors))

    def test_exported_internal_activity_is_rejected(self) -> None:
        mutated = self.manifest.replace(
            '<activity android:name="PrefScreenActions"\n'
            '                android:label="@string/pref_screen_actions_title"\n'
            '                android:exported="false"',
            '<activity android:name="PrefScreenActions"\n'
            '                android:label="@string/pref_screen_actions_title"\n'
            '                android:exported="true"',
        )
        errors = self.verify(manifest=mutated)
        self.assertTrue(
            any("PrefScreenActions" in error and "must be false" in error for error in errors)
        )

    def test_missing_ime_permission_is_rejected(self) -> None:
        mutated = self.manifest.replace(
            '\n                android:permission="android.permission.BIND_INPUT_METHOD"', ""
        )
        errors = self.verify(manifest=mutated)
        self.assertTrue(any("BIND_INPUT_METHOD" in error for error in errors))

    def test_changed_settings_activity_is_rejected(self) -> None:
        mutated = self.method.replace(
            "com.baodeep.hackerskeyboard.LatinIMESettings",
            "com.baodeep.hackerskeyboard.Main",
        )
        errors = self.verify(method=mutated)
        self.assertTrue(any("settingsActivity" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
