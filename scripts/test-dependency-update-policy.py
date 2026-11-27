#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Keep the release-age exception bounded; exercise unsafe policy mutations."""

from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def verify(lint_xml: str, dependabot: str) -> None:
    lint = ET.fromstring(lint_xml)
    if lint.tag != "lint" or lint.attrib or len(lint) != 1:
        raise AssertionError("Lint policy must contain only the wrapper exception")
    issue = lint[0]
    if issue.tag != "issue" or issue.attrib != {"id": "AndroidGradlePluginVersion"}:
        raise AssertionError("Only wrapper freshness may be excluded")
    if len(issue) != 1 or issue[0].tag != "ignore" or issue[0].attrib != {
        "path": "gradle/wrapper/gradle-wrapper.properties"
    } or len(issue[0]):
        raise AssertionError("The exception must match only the wrapper properties file")

    # Deliberately accept only the repository's simple, two-space YAML layout.
    # Fail closed on overrides, anchors, aliases, or flow mappings in cooldown.
    blocks = re.split(r"^  - package-ecosystem: ", dependabot, flags=re.MULTILINE)[1:]
    ecosystems = set()
    for block in blocks:
        ecosystem = block.splitlines()[0]
        ecosystems.add(ecosystem)
        # YAML blank lines and comments do not terminate a mapping. Inspect
        # every indented child until the next sibling instead of matching a
        # consecutive run of six-space lines.
        lines = [line for line in block.splitlines()
                 if line.strip() and not line.lstrip().startswith("#")]
        cooldown = []
        for index, line in enumerate(lines):
            if line.lstrip().startswith("cooldown:"):
                if line != "    cooldown:":
                    raise AssertionError("Unsupported cooldown mapping syntax")
                children = []
                for child in lines[index + 1:]:
                    indentation = len(child) - len(child.lstrip(" "))
                    if indentation <= 4:
                        break
                    children.append(child)
                cooldown.append(children)
        if cooldown != [["      default-days: 7"]]:
            raise AssertionError(f"{ecosystem}: require seven days without cooldown overrides")
    if ecosystems != {"gradle", "github-actions"} or len(blocks) != 2:
        raise AssertionError("Both dependency ecosystems must have the release-age policy")


class PolicyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lint = (ROOT / "lint.xml").read_text(encoding="utf-8")
        cls.dependabot = (ROOT / ".github/dependabot.yml").read_text(encoding="utf-8")

    def test_repository_policy(self):
        verify(self.lint, self.dependabot)

    def test_allow_blank_lines_and_comments_in_cooldown(self):
        formatted = self.dependabot.replace(
            '    cooldown:\n      default-days: 7\n',
            '    cooldown:\n\n    # Release-age policy\n      default-days: 7\n\n',
        )
        verify(self.lint, formatted)

    def test_reject_widened_lint_exception(self):
        for old, new in [
            ('gradle/wrapper/gradle-wrapper.properties', '*'),
            ('id="AndroidGradlePluginVersion"', 'id="all"'),
            ('id="AndroidGradlePluginVersion"', 'id="AndroidGradlePluginVersion" severity="ignore"'),
            ('</lint>', '<issue id="NewApi" severity="ignore" /></lint>'),
        ]:
            with self.subTest(new=new), self.assertRaises(AssertionError):
                verify(self.lint.replace(old, new), self.dependabot)

    def test_reject_shortened_or_bypassed_cooldown(self):
        for replacement in [
            '',
            '    cooldown:\n      default-days: 6\n',
            '    cooldown:\n      default-days: 7\n      exclude: ["*"]\n',
            '    cooldown:\n      default-days: 7\n      semver-patch-days: 0\n',
            '    cooldown:\n      default-days: 7\n\n      exclude: ["*"]\n',
            '    cooldown:\n      default-days: 7\n    # Still inside the cooldown mapping\n      exclude: ["*"]\n',
            '    cooldown:\n      default-days: 7\n\n      semver-patch-days: 0\n',
            '    cooldown:\n      default-days: 7\n      # Do not bypass release age\n      semver-patch-days: 0\n',
        ]:
            for occurrence in [0, 1]:
                blocks = self.dependabot.split('    cooldown:\n      default-days: 7\n')
                mutated = blocks[0]
                for index, block in enumerate(blocks[1:]):
                    mutated += (replacement if index == occurrence else
                                '    cooldown:\n      default-days: 7\n') + block
                with self.subTest(replacement=replacement, occurrence=occurrence):
                    with self.assertRaises(AssertionError):
                        verify(self.lint, mutated)


if __name__ == "__main__":
    unittest.main()
