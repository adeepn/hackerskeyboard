#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Guard the narrow notification dispatch and runtime receiver policy."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/baodeep/hackerskeyboard"
FILES = ("LatinIME.java", "NotificationReceiver.java", "NotificationActions.java",
         "NotificationAccess.java", "LatinIMESettings.java")


def read_sources():
    sources = {name: (SOURCE / name).read_text() if (SOURCE / name).exists() else ""
               for name in FILES}
    sources["AndroidManifest.xml"] = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
    return sources


def verify(sources):
    sources = {name: re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)
               for name, text in sources.items()}
    ime = sources["LatinIME.java"]
    receiver = sources["NotificationReceiver.java"]
    actions = sources["NotificationActions.java"]
    access = sources["NotificationAccess.java"]
    settings = sources["LatinIMESettings.java"]
    errors = []

    def require(pattern, text, label):
        if not re.search(pattern, text, flags=re.S):
            errors.append(label)

    for name, factory, code in (("showKeyboard", "getBroadcast", 1), ("openSettings", "getActivity", 2)):
        require(rf"PendingIntent {name}\(Context context\).*?PendingIntent\.{factory}\(context, {code},"
                rf".*?PendingIntent.FLAG_UPDATE_CURRENT\s*\|\s*PendingIntent.FLAG_IMMUTABLE\)",
                actions, f"immutable {name} PendingIntent")
        require(rf"NotificationActions\.{name}\(this\)", ime, f"use {name} factory")
    require(r"new Intent\(NotificationReceiver.ACTION_SHOW\)\s*\.setPackage\(context.getPackageName\(\)\)",
            actions, "package-scoped SHOW")
    require(r"new Intent\(context, LatinIMESettings.class\)", actions, "explicit settings activity")
    require(r"ContextCompat.registerReceiver\(context, receiver,\s*"
            r"new IntentFilter\(NotificationReceiver.ACTION_SHOW\),\s*ContextCompat.RECEIVER_NOT_EXPORTED\)",
            actions, "private SHOW receiver")
    require(r"NotificationActions.registerShowReceiver\(this, mNotificationReceiver\)",
            ime, "register private notification receiver")
    require(r"else if \(!visible\)\s*\{\s*"
            r"mNotificationManager.cancel\(NOTIFICATION_ONGOING_ID\);\s*"
            r"if \(mNotificationReceiver != null\)", ime,
            "idempotent disable and unconditional stale notification cancellation")
    require(r"void onDestroy\(\)\s*\{\s*setNotification\(false\);", ime,
            "cancel notification before IME teardown")
    require(r'uses-permission android:name="android.permission.POST_NOTIFICATIONS"',
            sources["AndroidManifest.xml"], "declared notification permission")
    require(r"visible = visible && NotificationAccess.canPost\(this\);", ime, "guard notification posting")
    require(r"catch \(SecurityException permissionRevoked\)\s*\{\s*setNotification\(false\);",
            ime, "cleanup after permission revocation race")
    require(r"ContextCompat.checkSelfPermission\(context,\s*Manifest.permission.POST_NOTIFICATIONS\)",
            access, "check runtime notification permission")
    require(r"areNotificationsEnabled\(\)", access, "check app notification block")
    require(r"channel.getImportance\(\) != NotificationManager.IMPORTANCE_NONE", access, "check channel block")
    require(r"new Intent\(ACTION_REFRESH\).setPackage\(context.getPackageName\(\)\)",
            actions, "package-scoped notification refresh")
    require(r"filter.addAction\(NotificationActions.ACTION_REFRESH\)", ime, "live private refresh receiver")
    require(r"Build.VERSION.SDK_INT >= 33 && requireContext\(\).getApplicationInfo\(\).targetSdkVersion >= 33",
            settings, "runtime prompt gated by device and target SDK")
    require(r"registerForActivityResult\(new ActivityResultContracts.RequestPermission\(\),\s*"
            r"granted -> refreshNotificationState\(\)\)", settings, "permission result refreshes state only")
    if "requestPermissions(" in ime or ".launch(Manifest.permission.POST_NOTIFICATIONS)" in ime:
        errors.append("permission prompt from IME service")
    for receiver_name, filter_name in (("mPluginManager", "pFilter"), ("mReceiver", "filter")):
        require(rf"ContextCompat.registerReceiver\(this, {receiver_name}, {filter_name},\s*"
                r"ContextCompat.RECEIVER_NOT_EXPORTED\)", ime, f"system receiver {receiver_name}")
    require(r'pFilter.addDataScheme\("package"\)', ime, "package data scheme")
    for action in ("PACKAGE_ADDED", "PACKAGE_REPLACED", "PACKAGE_REMOVED", "RINGER_MODE_CHANGED_ACTION"):
        require(action, ime, f"system action {action}")
    if re.search(r"(?<![\w.])registerReceiver\(", ime):
        errors.append("unflagged platform receiver registration")
    if "startActivity(" in receiver or "ACTION_SETTINGS" in receiver:
        errors.append("settings notification trampoline")
    require(r"intent != null && ACTION_SHOW.equals\(intent.getAction\(\)\)", receiver, "null-safe SHOW dispatch")
    require(r"showSoftInputFromInputMethod\(mIME.mToken, InputMethodManager.SHOW_FORCED\)",
            receiver, "preserved live IME SHOW request")
    if "FLAG_MUTABLE" in actions:
        errors.append("mutable notification PendingIntent")
    return errors


if __name__ == "__main__":
    failures = verify(read_sources())
    if failures:
        sys.exit("Notification contract failed: " + "; ".join(failures))
    print("Notification intents and receiver policy verified")
