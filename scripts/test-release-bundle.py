#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Offline regression tests for release bundle validation failures."""

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location(
    "verify_release_bundle", Path(__file__).with_name("verify-release-bundle.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ReleaseBundleTest(unittest.TestCase):
    expected = {
        "package": "com.baodeep.hackerskeyboard", "versionCode": "2000004",
        "versionName": "2.0.0-alpha04", "minSdk": "24", "targetSdk": "36",
    }
    manifest = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="com.baodeep.hackerskeyboard" android:versionCode="2000004"
        android:versionName="2.0.0-alpha04">
        <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36"/>
        <queries>
            <intent><action android:name="org.pocketworkstation.DICT"/></intent>
            <intent><action android:name="com.menny.android.anysoftkeyboard.DICTIONARY"/></intent>
            <intent><action android:name="android.view.InputMethod"/></intent>
        </queries>
        <application android:debuggable="false"/>
    </manifest>'''

    def test_valid_manifest_and_implicit_false(self):
        MODULE.verify_manifest(self.manifest, self.expected)
        MODULE.verify_manifest(self.manifest.replace(' android:debuggable="false"', ""), self.expected)

    def test_bundle_must_request_16kb_alignment_for_generated_apks(self):
        config = {"optimizations": {"uncompressNativeLibraries": {"alignment": "PAGE_ALIGNMENT_16K"}}}
        MODULE.verify_page_alignment(json.dumps(config))
        config["optimizations"]["uncompressNativeLibraries"]["alignment"] = "PAGE_ALIGNMENT_4K"
        for invalid in (config, {}):
            with self.assertRaises(ValueError):
                MODULE.verify_page_alignment(json.dumps(invalid))

    def test_wrong_identity_versions_sdks_and_debuggable_rejected(self):
        mutations = (
            ("com.baodeep.hackerskeyboard", "example.wrong"),
            ("2000004", "2000003"), ("alpha04", "alpha03"),
            ('minSdkVersion="24"', 'minSdkVersion="25"'),
            ('targetSdkVersion="36"', 'targetSdkVersion="26"'),
            ('debuggable="false"', 'debuggable="true"'),
            ('<application android:debuggable="false"/>', ""),
        )
        for original, replacement in mutations:
            with self.subTest(original=original), self.assertRaises(ValueError):
                MODULE.verify_manifest(self.manifest.replace(original, replacement), self.expected)

    def test_wrong_dictionary_visibility_rejected(self):
        with self.assertRaisesRegex(ValueError, "Queries must match"):
            MODULE.verify_manifest(self.manifest.replace("org.pocketworkstation.DICT", "wrong.DICT"), self.expected)
        broad_permission = '<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>'
        with self.assertRaisesRegex(ValueError, "QUERY_ALL_PACKAGES"):
            MODULE.verify_manifest(self.manifest.replace("</manifest>", broad_permission + "</manifest>"), self.expected)

    def make_bundle(self, path, missing=None, corrupt_elf=False, extra_abi=False):
        with zipfile.ZipFile(path, "w") as bundle:
            for entry in ("BundleConfig.pb", "base/manifest/AndroidManifest.xml", "base/dex/classes.dex"):
                bundle.writestr(entry, b"fixture")
            for abi in MODULE.ABIS | ({"unexpected"} if extra_abi else set()):
                if abi != missing:
                    bundle.writestr(f"base/lib/{abi}/libjni_pckeyboard.so",
                                    b"bad" if corrupt_elf else b"\x7fELFfixture")

    def test_valid_archive_inventory(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.aab"
            self.make_bundle(path)
            MODULE.verify_archive(path)

    def test_missing_or_extra_abi_and_non_elf_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.aab"
            for options in ({"missing": abi} for abi in MODULE.ABIS):
                with self.subTest(options=options), self.assertRaises(ValueError):
                    self.make_bundle(path, **options)
                    MODULE.verify_archive(path)
            for options in ({"corrupt_elf": True}, {"extra_abi": True}):
                with self.subTest(options=options), self.assertRaises(ValueError):
                    self.make_bundle(path, **options)
                    MODULE.verify_archive(path)

    def test_apk_cannot_pass_as_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.aab"
            with zipfile.ZipFile(path, "w") as apk:
                apk.writestr("AndroidManifest.xml", b"fixture")
            with self.assertRaises(ValueError):
                MODULE.verify_archive(path)

    def test_untrusted_tool_checksum_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bundletool.jar"
            path.write_bytes(b"not the official release")
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                MODULE.get_bundletool(path)


if __name__ == "__main__":
    unittest.main()
