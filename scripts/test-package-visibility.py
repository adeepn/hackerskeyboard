#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Positive and mutation tests for source and merged-manifest visibility checks."""

import sys
import unittest
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from verify_package_visibility import ANDROID, DICTIONARY_ACTIONS, ROOT, verify_queries


class PackageVisibilityTest(unittest.TestCase):
    def manifest(self):
        return ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()

    def test_source_and_plugin_actions_match(self):
        verify_queries(self.manifest())
        plugin = (ROOT / "app/src/main/java/com/baodeep/hackerskeyboard/PluginManager.java").read_text()
        for action in DICTIONARY_ACTIONS:
            self.assertIn('"' + action + '"', plugin)

    def test_missing_queries_rejected(self):
        manifest = self.manifest()
        manifest.remove(manifest.find("queries"))
        with self.assertRaisesRegex(ValueError, "queries"):
            verify_queries(manifest)

    def test_voice_ime_query_is_required_but_recognizer_query_is_not_yet_approved(self):
        manifest = self.manifest()
        queries = manifest.find("queries")
        voice = next(intent for intent in queries
                     if intent[0].get(ANDROID + "name") == "android.view.InputMethod")
        queries.remove(voice)
        with self.assertRaises(ValueError):
            verify_queries(manifest)
        voice[0].set(ANDROID + "name", "android.speech.action.RECOGNIZE_SPEECH")
        queries.append(voice)
        with self.assertRaises(ValueError):
            verify_queries(manifest)

    def test_missing_renamed_or_duplicate_action_rejected(self):
        for change in ("missing", "renamed", "duplicate"):
            with self.subTest(change=change):
                manifest = self.manifest()
                queries = manifest.find("queries")
                if change == "missing":
                    queries.remove(queries[0])
                elif change == "renamed":
                    queries[0][0].set(ANDROID + "name", "com.baodeep.hackerskeyboard.DICT")
                else:
                    queries.append(ET.fromstring(ET.tostring(queries[0])))
                with self.assertRaises(ValueError):
                    verify_queries(manifest)

    def test_broad_permission_in_either_form_rejected(self):
        for tag in ("uses-permission", "uses-permission-sdk-23"):
            with self.subTest(tag=tag):
                manifest = self.manifest()
                ET.SubElement(manifest, tag, {ANDROID + "name": "android.permission.QUERY_ALL_PACKAGES"})
                with self.assertRaisesRegex(ValueError, "QUERY_ALL_PACKAGES"):
                    verify_queries(manifest)

    def test_extra_queries_and_restrictive_categories_rejected(self):
        additions = (
            '<package android:name="example.package"/>',
            '<provider android:authorities="example.provider"/>',
            '<intent><action android:name="android.intent.action.MAIN"/></intent>',
        )
        for extra in additions:
            with self.subTest(extra=extra):
                manifest = self.manifest()
                wrapper = ET.fromstring('<queries xmlns:android="' + ANDROID[1:-1] + '">' + extra + '</queries>')
                manifest.find("queries").append(wrapper[0])
                with self.assertRaises(ValueError):
                    verify_queries(manifest)
        manifest = self.manifest()
        ET.SubElement(manifest.find("queries/intent"), "category", {ANDROID + "name": "android.intent.category.DEFAULT"})
        with self.assertRaises(ValueError):
            verify_queries(manifest)


if __name__ == "__main__":
    unittest.main()
