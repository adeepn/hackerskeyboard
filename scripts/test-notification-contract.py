#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation tests for the notification source contract; device tests cover dispatch."""

import importlib.util
from pathlib import Path
import sys
import unittest

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location(
    "notification_contract", Path(__file__).with_name("verify-notification-contract.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class NotificationContractTest(unittest.TestCase):
    def test_current_contract(self):
        self.assertEqual([], module.verify(module.read_sources()))

    def test_unsafe_mutations_are_rejected(self):
        mutations = (
            ("NotificationActions.java", "FLAG_IMMUTABLE", "FLAG_MUTABLE"),
            ("NotificationActions.java", ".setPackage(context.getPackageName())", ""),
            ("NotificationActions.java", "getActivity", "getBroadcast"),
            ("NotificationActions.java", "RECEIVER_NOT_EXPORTED", "RECEIVER_EXPORTED"),
            ("LatinIME.java", "ContextCompat.RECEIVER_NOT_EXPORTED", "ContextCompat.RECEIVER_EXPORTED"),
            ("LatinIME.java", 'pFilter.addDataScheme("package");', ""),
            ("NotificationReceiver.java", "intent != null &&", ""),
            ("NotificationReceiver.java", "InputMethodManager.SHOW_FORCED", "0"),
            ("LatinIME.java", "else if (!visible)", "else if (mNotificationReceiver != null)"),
            ("LatinIME.java", "setNotification(false);", ""),
            ("AndroidManifest.xml", "android.permission.POST_NOTIFICATIONS", "android.permission.INTERNET"),
            ("LatinIME.java", "visible = visible && NotificationAccess.canPost(this);", ""),
            ("NotificationActions.java", "new Intent(ACTION_REFRESH).setPackage(context.getPackageName())", "new Intent(ACTION_REFRESH)"),
            ("NotificationAccess.java", "areNotificationsEnabled()", "unconditionallyAllow()"),
            ("NotificationAccess.java", "channel.getImportance() != NotificationManager.IMPORTANCE_NONE", "true"),
            ("LatinIMESettings.java", "targetSdkVersion >= 33", "targetSdkVersion >= 26"),
        )
        for name, before, after in mutations:
            with self.subTest(file=name, mutation=before):
                sources = module.read_sources()
                self.assertIn(before, sources[name])
                sources[name] = sources[name].replace(before, after)
                self.assertTrue(module.verify(sources))


if __name__ == "__main__":
    unittest.main()
