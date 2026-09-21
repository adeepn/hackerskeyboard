#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check reviewed dictionary and voice-IME visibility in source or decoded manifests."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
DICTIONARY_ACTIONS = {
    "org.pocketworkstation.DICT",
    "com.menny.android.anysoftkeyboard.DICTIONARY",
}
ALLOWED_ACTIONS = DICTIONARY_ACTIONS | {"android.view.InputMethod"}
ROOT = Path(__file__).resolve().parents[1]


def verify_queries(manifest: ET.Element) -> None:
    if manifest.tag != "manifest":
        raise ValueError("Expected an Android manifest")
    for permission in manifest:
        if permission.tag.startswith("uses-permission") and permission.get(ANDROID + "name") == (
                "android.permission.QUERY_ALL_PACKAGES"):
            raise ValueError("QUERY_ALL_PACKAGES is forbidden")
    queries = manifest.findall("queries")
    if len(queries) != 1:
        raise ValueError("Expected one queries element for the dictionary protocols")
    actions = []
    for intent in queries[0]:
        if intent.tag != "intent" or intent.attrib:
            raise ValueError("Only reviewed intent queries are allowed; no package/provider grants")
        children = list(intent)
        if len(children) != 1 or children[0].tag != "action":
            raise ValueError("Each dictionary query must contain only one action")
        action = children[0]
        if set(action.attrib) != {ANDROID + "name"} or list(action):
            raise ValueError("Unexpected dictionary action attributes/content")
        actions.append(action.get(ANDROID + "name"))
    if len(actions) != len(ALLOWED_ACTIONS) or set(actions) != ALLOWED_ACTIONS:
        raise ValueError("Queries must match exactly HK, ASK and the voice-IME contract")


def main() -> None:
    if len(sys.argv) == 1:
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    elif sys.argv[1:] == ["--stdin"]:
        manifest = ET.fromstring(sys.stdin.read())
    else:
        raise ValueError("Usage: verify_package_visibility.py [--stdin]")
    verify_queries(manifest)
    print("Verified narrow HK/ASK and voice-IME queries; no broad package visibility")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, ET.ParseError) as error:
        sys.exit(f"Package visibility verification failed: {error}")
