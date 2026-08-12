#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Verify the prefix-independent semantics of legacy keyboard XML resources."""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


RESOURCE_NAMESPACE = "http://schemas.android.com/apk/res-auto"
EXPECTED_FILE_COUNT = 76
EXPECTED_ATTRIBUTE_COUNT = 15_667
EXPECTED_APP_PREFIX_COUNT = 58
EXPECTED_SHA256 = "e2bcefc1d797ccc54acbfb6352a91fdaadc2f893dfd8b9bbdcda90ffc1cf0db3"
MODIFICATION_NOTICE = b"Modified for Hacker's Keyboard v2"


def update_digest(digest: Any, value: str) -> None:
    encoded = value.encode("utf-8")
    digest.update(len(encoded).to_bytes(8, "big"))
    digest.update(encoded)


def add_element(digest: Any, element: ET.Element) -> int:
    update_digest(digest, "start")
    update_digest(digest, element.tag)

    attribute_count = 0
    for name, value in sorted(element.attrib.items()):
        update_digest(digest, name)
        update_digest(digest, value)
        attribute_count += 1

    if element.text and element.text.strip():
        update_digest(digest, "text")
        update_digest(digest, element.text.strip())

    for child in element:
        attribute_count += add_element(digest, child)
        if child.tail and child.tail.strip():
            update_digest(digest, "tail")
            update_digest(digest, child.tail.strip())

    update_digest(digest, "end")
    update_digest(digest, element.tag)
    return attribute_count


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--print-digest",
        action="store_true",
        help="print the current semantic digest without enforcing the snapshot",
    )
    args = parser.parse_args()

    repository = Path(__file__).resolve().parents[1]
    resources = repository / "app" / "src" / "main" / "res"
    namespace_declaration = re.compile(
        rb"xmlns:(?P<prefix>[A-Za-z_][\w.-]*)=[\"']"
        + re.escape(RESOURCE_NAMESPACE.encode("ascii"))
        + rb"[\"']"
    )

    files: list[tuple[Path, bytes, str]] = []
    errors: list[str] = []
    app_prefix_count = 0
    for path in sorted(resources.rglob("*.xml")):
        raw = path.read_bytes()
        matches = list(namespace_declaration.finditer(raw))
        if not matches:
            continue
        prefix = matches[0].group("prefix").decode("ascii")
        files.append((path, raw, prefix))
        if len(matches) != 1:
            errors.append(
                f"{path.relative_to(repository)}: expected one resource namespace, "
                f"found {len(matches)}"
            )
        if prefix == "app":
            app_prefix_count += 1
            if not args.print_digest and MODIFICATION_NOTICE not in raw:
                errors.append(
                    f"{path.relative_to(repository)}: missing v2 modification notice"
                )
        if not args.print_digest and prefix == "android":
            errors.append(
                f"{path.relative_to(repository)}: android prefix must use the Android namespace"
            )

    digest = hashlib.sha256()
    attribute_count = 0
    for path, _raw, _prefix in files:
        relative_path = path.relative_to(repository).as_posix()
        update_digest(digest, relative_path)
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            errors.append(f"{relative_path}: {error}")
            continue
        attribute_count += add_element(digest, root)

    actual_sha256 = digest.hexdigest()
    summary = (
        f"files={len(files)} attributes={attribute_count} "
        f"sha256={actual_sha256}"
    )
    if args.print_digest:
        print(summary)
        return 0

    if len(files) != EXPECTED_FILE_COUNT:
        errors.append(f"expected {EXPECTED_FILE_COUNT} files, found {len(files)}")
    if attribute_count != EXPECTED_ATTRIBUTE_COUNT:
        errors.append(
            f"expected {EXPECTED_ATTRIBUTE_COUNT} attributes, found {attribute_count}"
        )
    if app_prefix_count != EXPECTED_APP_PREFIX_COUNT:
        errors.append(
            f"expected {EXPECTED_APP_PREFIX_COUNT} app-prefixed files, "
            f"found {app_prefix_count}"
        )
    if actual_sha256 != EXPECTED_SHA256:
        errors.append(f"semantic digest changed: {actual_sha256}")

    if errors:
        print("Keyboard XML semantic verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        print(summary, file=sys.stderr)
        return 1

    print(f"Keyboard XML semantics verified: {summary}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
