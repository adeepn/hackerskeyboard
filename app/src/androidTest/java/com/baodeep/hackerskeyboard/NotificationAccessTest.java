// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class NotificationAccessTest {
    @Test
    public void manifestPermissionAndSystemSettingsRoutingAreScopedToOurApp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(Arrays.asList(context.getPackageManager().getPackageInfo(context.getPackageName(),
                PackageManager.GET_PERMISSIONS).requestedPermissions).contains(Manifest.permission.POST_NOTIFICATIONS));
        Intent intent = NotificationAccess.settingsIntent(context);
        if (Build.VERSION.SDK_INT >= 26) {
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.getAction());
            assertEquals(context.getPackageName(), intent.getStringExtra(Settings.EXTRA_APP_PACKAGE));
        } else {
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.getAction());
            assertEquals("package:" + context.getPackageName(), intent.getDataString());
        }
        assertEquals(context.getPackageName(), NotificationActions.refreshIntent(context).getPackage());
        assertNull(NotificationActions.refreshIntent(context).getExtras());
    }

    @Test
    public void deniedRuntimePermissionBlocksPostingEvenWhenAppOpAllowsIt() throws Exception {
        NotificationTestSupport.allow();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context denied = new ContextWrapper(context) {
            @Override
            public int checkPermission(String permission, int pid, int uid) {
                if (Manifest.permission.POST_NOTIFICATIONS.equals(permission)) return PackageManager.PERMISSION_DENIED;
                return super.checkPermission(permission, pid, uid);
            }
        };
        assertEquals(Build.VERSION.SDK_INT < 33, NotificationAccess.canPost(denied));
    }

    @Test
    public void blockedPreferenceSurvivesRecreationAndNeverAutoOpensSettings() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String key = LatinIME.PREF_KEYBOARD_NOTIFICATION;
        boolean existed = prefs.contains(key);
        boolean previous = prefs.getBoolean(key, false);
        NotificationTestSupport.allow();
        NotificationTestSupport.setAppAllowed(false);
        prefs.edit().putBoolean(key, true).commit();
        LatinIMESettings[] activity = new LatinIMESettings[1];
        Instrumentation.ActivityMonitor unexpectedSettings = instrumentation.addMonitor(
                new android.content.IntentFilter(NotificationAccess.settingsIntent(context).getAction()), null, true);
        try {
            activity[0] = (LatinIMESettings) instrumentation.startActivitySync(
                    new Intent(context, LatinIMESettings.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            assertUi(instrumentation, activity[0], true);
            Instrumentation.ActivityMonitor recreation = instrumentation.addMonitor(
                    LatinIMESettings.class.getName(), null, false);
            try {
                instrumentation.runOnMainSync(() -> activity[0].recreate());
                activity[0] = (LatinIMESettings) recreation.waitForActivityWithTimeout(5000);
                assertNotNull(activity[0]);
            } finally {
                instrumentation.removeMonitor(recreation);
            }
            assertUi(instrumentation, activity[0], true);
            assertTrue(prefs.getBoolean(key, false));
            assertEquals(0, unexpectedSettings.getHits());

            NotificationTestSupport.setAppAllowed(true);
            // The same refresh used after the permission callback and onResume.
            instrumentation.runOnMainSync(() -> {
                try {
                    java.lang.reflect.Method refresh = LatinIMESettings.MainPreferenceFragment.class
                            .getDeclaredMethod("refreshNotificationState");
                    refresh.setAccessible(true);
                    refresh.invoke(activity[0].getSupportFragmentManager().findFragmentByTag(LatinIMESettings.FRAGMENT_TAG));
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            });
            assertUi(instrumentation, activity[0], false);
            assertTrue(prefs.getBoolean(key, false));
            assertEquals(0, unexpectedSettings.getHits());
        } finally {
            instrumentation.runOnMainSync(() -> { if (activity[0] != null) activity[0].finish(); });
            instrumentation.removeMonitor(unexpectedSettings);
            NotificationTestSupport.setAppAllowed(true);
            SharedPreferences.Editor editor = prefs.edit();
            if (existed) editor.putBoolean(key, previous);
            else editor.remove(key);
            editor.commit();
        }
    }

    private static void assertUi(Instrumentation instrumentation, LatinIMESettings activity, boolean blocked) {
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            LatinIMESettings.MainPreferenceFragment fragment = (LatinIMESettings.MainPreferenceFragment)
                    activity.getSupportFragmentManager().findFragmentByTag(LatinIMESettings.FRAGMENT_TAG);
            CheckBoxPreference notification = fragment.findPreference(LatinIME.PREF_KEYBOARD_NOTIFICATION);
            assertTrue(notification.isChecked());
            assertEquals(activity.getString(blocked ? R.string.notification_blocked
                    : R.string.summary_keyboard_notification_true), notification.getSummaryOn());
            PreferenceGroup parent = notification.getParent();
            Preference access = null;
            for (int i = 0; i < parent.getPreferenceCount(); i++) {
                Preference candidate = parent.getPreference(i);
                if (activity.getString(R.string.notification_access_title).contentEquals(candidate.getTitle())) access = candidate;
            }
            assertNotNull(access);
            assertNull(access.getKey());
            assertFalse(access.isPersistent());
            assertEquals(blocked, access.isVisible());
            assertEquals(1, activity.getSupportFragmentManager().getFragments().size());
        });
    }
}
