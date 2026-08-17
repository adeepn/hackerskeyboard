#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0

"""Guard the approved AndroidX dependency and incremental migration boundary."""

from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path


EXPECTED_DEPENDENCIES = {
    "implementation": {
        "androidx.core:core:1.19.0",
        "androidx.preference:preference:1.2.1",
    },
    "androidTestImplementation": {
        "androidx.test.ext:junit:1.3.0",
        "androidx.test:runner:1.7.0",
    },
}
EXPECTED_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
EXPECTED_SDK_LEVELS = {
    "compileSdk": "37",
    "minSdk": "24",
    "targetSdk": "26",
}
FORBIDDEN_TOKENS = (
    "android.support.",
    "android/support/",
    "com.android.support",
    "androidx.appcompat.",
    "androidx.appcompat:",
    "androidx.test.espresso.",
    "androidx.test.espresso:",
)
DEPENDENCY_PATTERN = re.compile(
    r"(?m)^\s*(implementation|androidTestImplementation)\s+['\"]([^'\"]+)['\"]"
)


def main() -> int:
    repository = Path(__file__).resolve().parents[1]
    app = repository / "app"
    build_gradle_path = app / "build.gradle"
    build_gradle = build_gradle_path.read_text(encoding="utf-8")
    gradle_properties = (repository / "gradle.properties").read_text(encoding="utf-8")
    errors: list[str] = []

    if not re.search(
        r"(?m)^\s*android\.useAndroidX\s*=\s*true\s*$", gradle_properties
    ):
        errors.append("gradle.properties: android.useAndroidX must be true")
    if re.search(
        r"(?m)^\s*android\.enableJetifier\s*=\s*true\s*$", gradle_properties
    ):
        errors.append("gradle.properties: Jetifier must remain disabled")

    for property_name, expected_value in EXPECTED_SDK_LEVELS.items():
        pattern = rf"(?m)^\s*{property_name}\s+{expected_value}\s*$"
        if not re.search(pattern, build_gradle):
            errors.append(
                f"app/build.gradle: {property_name} must remain {expected_value}"
            )

    runner_pattern = rf"testInstrumentationRunner\s+['\"]{re.escape(EXPECTED_RUNNER)}['\"]"
    if not re.search(runner_pattern, build_gradle):
        errors.append(f"app/build.gradle: runner must be {EXPECTED_RUNNER}")

    actual_dependencies: dict[str, set[str]] = {
        configuration: set() for configuration in EXPECTED_DEPENDENCIES
    }
    for configuration, coordinate in DEPENDENCY_PATTERN.findall(build_gradle):
        if coordinate.startswith("androidx."):
            actual_dependencies[configuration].add(coordinate)
    for configuration, expected in EXPECTED_DEPENDENCIES.items():
        actual = actual_dependencies[configuration]
        if actual != expected:
            errors.append(
                f"app/build.gradle: {configuration} AndroidX dependencies "
                f"{sorted(actual)} != {sorted(expected)}"
            )

    text_paths = [build_gradle_path, app / "lint-baseline.xml"]
    text_paths.extend(sorted((app / "src").rglob("*.java")))
    for path in text_paths:
        text = path.read_text(encoding="utf-8")
        for token in FORBIDDEN_TOKENS:
            if token in text:
                errors.append(f"{path.relative_to(repository)}: forbidden token {token}")

    actions_activity = (
        app
        / "src"
        / "main"
        / "java"
        / "com"
        / "baodeep"
        / "hackerskeyboard"
        / "PrefScreenActions.java"
    ).read_text(encoding="utf-8")
    for token in (
        "extends FragmentActivity",
        "extends PreferenceFragmentCompat",
        "if (icicle == null)",
        ".commitNow()",
        "registerOnSharedPreferenceChangeListener(this)",
        "unregisterOnSharedPreferenceChangeListener(this)",
        "new BackupManager(requireContext())",
    ):
        if token not in actions_activity:
            errors.append(f"PrefScreenActions.java: missing migration guard {token}")
    for token in ("android.preference.", "PreferenceActivity", "setTargetFragment("):
        if token in actions_activity:
            errors.append(f"PrefScreenActions.java: forbidden migration token {token}")

    actions_xml = (app / "src" / "main" / "res" / "xml" / "prefs_actions.xml")
    actions_xml_text = actions_xml.read_text(encoding="utf-8")
    if 'xmlns:app="http://schemas.android.com/apk/res-auto"' not in actions_xml_text:
        errors.append("prefs_actions.xml: AndroidX app namespace is missing")
    if actions_xml_text.count("<ListPreference") != 6:
        errors.append("prefs_actions.xml: expected six AndroidX ListPreference nodes")
    if actions_xml_text.count('app:useSimpleSummaryProvider="true"') != 6:
        errors.append("prefs_actions.xml: every action must retain an automatic summary")
    if "AutoSummaryListPreference" in actions_xml_text:
        errors.append("prefs_actions.xml: legacy custom ListPreference remains")

    feedback_activity = (
        app
        / "src"
        / "main"
        / "java"
        / "com"
        / "baodeep"
        / "hackerskeyboard"
        / "PrefScreenFeedback.java"
    ).read_text(encoding="utf-8")
    for token in (
        "extends FragmentActivity",
        "extends SeekBarPreferenceFragmentCompat",
        "if (icicle == null)",
        ".commitNow()",
        "registerOnSharedPreferenceChangeListener(this)",
        "unregisterOnSharedPreferenceChangeListener(this)",
        "new BackupManager(requireContext())",
    ):
        if token not in feedback_activity:
            errors.append(f"PrefScreenFeedback.java: missing migration guard {token}")
    for token in ("android.preference.", "PreferenceActivity", "setTargetFragment("):
        if token in feedback_activity:
            errors.append(f"PrefScreenFeedback.java: forbidden migration token {token}")

    feedback_xml_text = (
        app / "src" / "main" / "res" / "xml" / "prefs_feedback.xml"
    ).read_text(encoding="utf-8")
    for token in (
        'xmlns:app="http://schemas.android.com/apk/res-auto"',
        "<com.baodeep.hackerskeyboard.VibratePreferenceCompat",
        "<com.baodeep.hackerskeyboard.SeekBarPreferenceStringCompat",
        "<ListPreference",
        'app:useSimpleSummaryProvider="true"',
    ):
        if token not in feedback_xml_text:
            errors.append(f"prefs_feedback.xml: missing AndroidX token {token}")
    for token in (
        "AutoSummaryListPreference",
        "com.baodeep.hackerskeyboard.VibratePreference\n",
        "com.baodeep.hackerskeyboard.SeekBarPreferenceString\n",
    ):
        if token in feedback_xml_text:
            errors.append(f"prefs_feedback.xml: legacy widget remains: {token.strip()}")

    view_activity = (
        app
        / "src"
        / "main"
        / "java"
        / "com"
        / "baodeep"
        / "hackerskeyboard"
        / "PrefScreenView.java"
    ).read_text(encoding="utf-8")
    for token in (
        "extends FragmentActivity",
        "extends SeekBarPreferenceFragmentCompat",
        "if (icicle == null)",
        ".commitNow()",
        "registerOnSharedPreferenceChangeListener(this)",
        "unregisterOnSharedPreferenceChangeListener(this)",
        "new BackupManager(requireContext())",
        "LatinKeyboardBaseView.sSetRenderMode == null",
        "R.string.render_mode_unavailable",
    ):
        if token not in view_activity:
            errors.append(f"PrefScreenView.java: missing migration guard {token}")
    for token in ("android.preference.", "PreferenceActivity", "setTargetFragment("):
        if token in view_activity:
            errors.append(f"PrefScreenView.java: forbidden migration token {token}")

    view_xml_text = (
        app / "src" / "main" / "res" / "xml" / "prefs_view.xml"
    ).read_text(encoding="utf-8")
    if 'xmlns:app="http://schemas.android.com/apk/res-auto"' not in view_xml_text:
        errors.append("prefs_view.xml: AndroidX app namespace is missing")
    if view_xml_text.count("<ListPreference") != 3:
        errors.append("prefs_view.xml: expected three AndroidX ListPreference nodes")
    if view_xml_text.count('app:useSimpleSummaryProvider="true"') != 3:
        errors.append("prefs_view.xml: every list preference must retain a summary")
    if view_xml_text.count(
        "<com.baodeep.hackerskeyboard.SeekBarPreferenceStringCompat"
    ) != 3:
        errors.append("prefs_view.xml: expected three AndroidX string seek bars")
    for token in (
        "AutoSummaryListPreference",
        "com.baodeep.hackerskeyboard.SeekBarPreferenceString\n",
    ):
        if token in view_xml_text:
            errors.append(f"prefs_view.xml: legacy widget remains: {token.strip()}")

    seek_dialog = (
        app
        / "src"
        / "main"
        / "java"
        / "com"
        / "baodeep"
        / "hackerskeyboard"
        / "SeekBarPreferenceDialogFragmentCompat.java"
    ).read_text(encoding="utf-8")
    for token in ("setTargetFragment(", "putParcelable(", "putSerializable("):
        if token in seek_dialog:
            errors.append(
                f"SeekBarPreferenceDialogFragmentCompat.java: forbidden state token {token}"
            )
    if 'arguments.putString(ARG_KEY, key)' not in seek_dialog:
        errors.append(
            "SeekBarPreferenceDialogFragmentCompat.java: stable preference key argument missing"
        )

    manifest = (app / "src" / "main" / "AndroidManifest.xml").read_text(
        encoding="utf-8"
    )
    actions_manifest = re.search(
        r'<activity\s+android:name="PrefScreenActions"(?P<attributes>[^>]*)>',
        manifest,
    )
    if actions_manifest is None or 'android:theme="@style/SettingsTheme"' not in (
        actions_manifest.group("attributes") if actions_manifest else ""
    ):
        errors.append("AndroidManifest.xml: PrefScreenActions SettingsTheme is missing")
    feedback_manifest = re.search(
        r'<activity\s+android:name="PrefScreenFeedback"(?P<attributes>[^>]*)>',
        manifest,
    )
    if feedback_manifest is None or 'android:theme="@style/SettingsTheme"' not in (
        feedback_manifest.group("attributes") if feedback_manifest else ""
    ):
        errors.append("AndroidManifest.xml: PrefScreenFeedback SettingsTheme is missing")
    view_manifest = re.search(
        r'<activity\s+android:name="PrefScreenView"(?P<attributes>[^>]*)>',
        manifest,
    )
    if view_manifest is None or 'android:theme="@style/SettingsTheme"' not in (
        view_manifest.group("attributes") if view_manifest else ""
    ):
        errors.append("AndroidManifest.xml: PrefScreenView SettingsTheme is missing")

    styles = (app / "src" / "main" / "res" / "values" / "styles.xml").read_text(
        encoding="utf-8"
    )
    if (
        '<style name="SettingsTheme" parent="@android:style/Theme.Material.NoActionBar">'
        not in styles
    ) or (
        '<item name="preferenceTheme">@style/PreferenceThemeOverlay</item>' not in styles
    ):
        errors.append("styles.xml: compatible AndroidX preference theme is missing")

    jar_count = 0
    for path in sorted((app / "libs").glob("*.jar")):
        jar_count += 1
        try:
            with zipfile.ZipFile(path) as archive:
                for name in archive.namelist():
                    data = archive.read(name)
                    if b"android/support/" in data:
                        errors.append(
                            f"{path.relative_to(repository)}:{name}: "
                            "Support Library bytecode reference requires review"
                        )
        except zipfile.BadZipFile:
            errors.append(f"{path.relative_to(repository)}: invalid JAR archive")

    if errors:
        print("AndroidX migration verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "AndroidX migration verified: Core 1.19.0; Preference 1.2.1; "
        "Test runner 1.7.0; "
        f"JUnit extension 1.3.0; Jetifier disabled; {jar_count} JAR checked"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
