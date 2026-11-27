#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Keep the release-age exception bounded; exercise unsafe policy mutations."""

from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

import yaml

ROOT = Path(__file__).resolve().parents[1]


class PolicyLoader(yaml.SafeLoader):
    """Reject duplicate keys, aliases and merges instead of guessing precedence."""

    def compose_node(self, parent, index):
        if self.check_event(yaml.AliasEvent):
            raise yaml.YAMLError("Aliases are unsupported in dependency policy")
        return super().compose_node(parent, index)

    def construct_mapping(self, node, deep=False):
        seen = set()
        for key_node, _ in node.value:
            if key_node.tag == "tag:yaml.org,2002:merge":
                raise yaml.YAMLError("Merge keys are unsupported in dependency policy")
            key = self.construct_object(key_node, deep=deep)
            try:
                if key in seen:
                    raise yaml.YAMLError(f"Duplicate mapping key: {key!r}")
                seen.add(key)
            except TypeError as error:
                raise yaml.YAMLError("Mapping keys must be hashable scalars") from error
        return super().construct_mapping(node, deep=deep)


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

    try:
        config = yaml.load(dependabot, Loader=PolicyLoader)
    except yaml.YAMLError as error:
        raise AssertionError(f"Invalid or ambiguous Dependabot YAML: {error}") from error
    if not isinstance(config, dict) or type(config.get("version")) is not int or config["version"] != 2:
        raise AssertionError("Dependabot must use configuration version 2")
    updates = config.get("updates")
    if not isinstance(updates, list) or len(updates) != 2:
        raise AssertionError("Require exactly the two supported update entries")
    ecosystems = []
    for update in updates:
        if not isinstance(update, dict):
            raise AssertionError("Every update entry must be a mapping")
        ecosystem = update.get("package-ecosystem")
        if ecosystem not in ("gradle", "github-actions"):
            raise AssertionError("Unsupported dependency ecosystem")
        ecosystems.append(ecosystem)
        cooldown = update.get("cooldown")
        if (not isinstance(cooldown, dict) or set(cooldown) != {"default-days"}
                or type(cooldown["default-days"]) is not int or cooldown["default-days"] != 7):
            raise AssertionError(f"{ecosystem}: require integer seven days without overrides")
    if set(ecosystems) != {"gradle", "github-actions"}:
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

    def test_accept_semantically_equivalent_yaml(self):
        for updates in [
            '  - directory: /\n    cooldown: {default-days: 7}\n    package-ecosystem: gradle\n'
            '  - directory: /\n    package-ecosystem: github-actions\n    cooldown: {default-days: 7}\n',
            '  - {directory: /, package-ecosystem: gradle, cooldown: {default-days: 7}}\n'
            '  - {cooldown: {default-days: 7}, package-ecosystem: github-actions, directory: /}\n',
        ]:
            with self.subTest(updates=updates):
                verify(self.lint, 'version: 2\nupdates:\n' + updates)

    def test_reject_every_additional_update_entry(self):
        for entry in [
            '  - directory: /\n    package-ecosystem: gradle\n',
            '  - {directory: /, package-ecosystem: gradle, cooldown: {default-days: 0}}\n',
            '  - {package-ecosystem: npm, cooldown: {default-days: 7}}\n',
        ]:
            with self.subTest(entry=entry), self.assertRaises(AssertionError):
                verify(self.lint, self.dependabot + '\n' + entry)

    def test_reject_invalid_reordered_and_flow_entries(self):
        for ecosystem in ['gradle', 'github-actions']:
            other = 'github-actions' if ecosystem == 'gradle' else 'gradle'
            for entry in [
                f'  - directory: /\n    package-ecosystem: {ecosystem}\n',
                f'  - {{directory: /, package-ecosystem: {ecosystem}, cooldown: {{default-days: 0}}}}\n',
            ]:
                config = ('version: 2\nupdates:\n' + entry +
                          f'  - {{package-ecosystem: {other}, cooldown: {{default-days: 7}}}}\n')
                with self.subTest(entry=entry), self.assertRaises(AssertionError):
                    verify(self.lint, config)

    def test_reject_duplicate_keys_aliases_and_merges(self):
        for config in [
            self.dependabot + '\nupdates: []\n',
            self.dependabot.replace('default-days: 7', 'default-days: 0\n      default-days: 7', 1),
            self.dependabot.replace('    directory: /', '    directory: /\n    package-ecosystem: gradle', 1),
            self.dependabot.replace('version: 2', 'version: &version 2\nunused: *version'),
            self.dependabot.replace('    cooldown:', '    <<: {cooldown: {default-days: 0}}\n    cooldown:', 1),
        ]:
            with self.subTest(config=config), self.assertRaises(AssertionError):
                verify(self.lint, config)

    def test_reject_noninteger_release_age_and_invalid_shapes(self):
        for age in ['"7"', '7.0', 'true', 'null']:
            with self.subTest(age=age), self.assertRaises(AssertionError):
                verify(self.lint, self.dependabot.replace('default-days: 7', f'default-days: {age}'))
        for config in ['[]', 'version: 2\nupdates: {}', 'version: 2\nupdates: [7, 8]',
                       self.dependabot.replace('package-ecosystem: github-actions', 'package-ecosystem: gradle')]:
            with self.subTest(config=config), self.assertRaises(AssertionError):
                verify(self.lint, config)

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
