/*
 * SPDX-License-Identifier: Apache-2.0
 */

// Modified for Hacker's Keyboard v2; see the repository history for details.
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ApplicationSmokeTest {
    private static final String EXPECTED_APPLICATION_ID =
            "com.baodeep.hackerskeyboard";
    private static final String EXPECTED_DISPLAY_NAME = "Hacker's Keyboard v2";
    private static final String DEFAULT_SHARED_PREFERENCES =
            EXPECTED_APPLICATION_ID + "_preferences";

    @Test
    public void installedApkRegistersImeAndLaunchesEssentialActivities() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();

        assertEquals(EXPECTED_APPLICATION_ID, context.getPackageName());
        assertEquals(EXPECTED_DISPLAY_NAME,
                context.getApplicationInfo().loadLabel(context.getPackageManager()).toString());
        assertEquals(EXPECTED_APPLICATION_ID + ".LatinIME", LatinIME.class.getName());
        assertEquals("org.pocketworkstation.DICT", PluginManager.HK_INTENT_DICT);
        assertImeIsRegistered(context);

        Main main = launchActivity(instrumentation, context, Main.class);
        try {
            assertEquals(EXPECTED_DISPLAY_NAME, main.getTitle().toString());
            assertVisible(main, R.id.main_description);
            assertVisible(main, R.id.main_setup_btn_configure_imes);
            assertVisible(main, R.id.main_setup_btn_set_ime);
            assertVisible(main, R.id.main_setup_btn_input_lang);
            assertVisible(main, R.id.main_setup_btn_settings);
        } finally {
            finishActivity(instrumentation, main);
        }

        LatinIMESettings settings = launchActivity(
                instrumentation, context, LatinIMESettings.class);
        try {
            assertVisible(settings, android.R.id.list);
        } finally {
            finishActivity(instrumentation, settings);
        }
    }

    @Test
    public void actionsSettingsReadsExistingValueAndSurvivesRecreation() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SharedPreferences preferences = context.getSharedPreferences(
                DEFAULT_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        String key = "pref_swipe_up";
        boolean hadOriginalValue = preferences.contains(key);
        String originalValue = preferences.getString(key, null);
        preferences.edit().putString(key, "settings").apply();
        assertEquals("settings", preferences.getString(key, null));

        PrefScreenActions activity = null;
        PrefScreenActions recreated = null;
        Instrumentation.ActivityMonitor monitor = null;
        try {
            Intent intent = new Intent(EXPECTED_APPLICATION_ID + ".PREFS_ACTIONS");
            intent.setPackage(EXPECTED_APPLICATION_ID);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Activity launched = instrumentation.startActivitySync(intent);
            instrumentation.waitForIdleSync();
            assertEquals(PrefScreenActions.class, launched.getClass());
            activity = (PrefScreenActions) launched;
            final ListPreference swipeUp = assertActionsPreferences(activity, true);
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    swipeUp.setValue("close");
                    swipeUp.setValue("settings");
                }
            });
            assertEquals("settings", preferences.getString(key, null));
            assertEquals(swipeUp.getEntry(), swipeUp.getSummary());

            monitor = instrumentation.addMonitor(
                    PrefScreenActions.class.getName(), null, false);
            final PrefScreenActions activityToRecreate = activity;
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    activityToRecreate.recreate();
                }
            });
            Activity recreatedActivity = monitor.waitForActivityWithTimeout(5_000);
            assertNotNull("PrefScreenActions was not recreated", recreatedActivity);
            instrumentation.waitForIdleSync();
            assertEquals(PrefScreenActions.class, recreatedActivity.getClass());
            recreated = (PrefScreenActions) recreatedActivity;
            assertActionsPreferences(recreated, false);
        } finally {
            if (monitor != null) {
                instrumentation.removeMonitor(monitor);
            }
            if (recreated != null) {
                finishActivity(instrumentation, recreated);
            } else if (activity != null) {
                finishActivity(instrumentation, activity);
            }
            SharedPreferences.Editor editor = preferences.edit();
            if (hadOriginalValue) {
                editor.putString(key, originalValue);
            } else {
                editor.remove(key);
            }
            editor.apply();
        }
    }

    @Test
    public void feedbackSeekBarPreservesStringAndPendingValueAcrossRecreation() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SharedPreferences preferences = context.getSharedPreferences(
                DEFAULT_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        String key = "pref_click_volume";
        boolean hadOriginalValue = preferences.contains(key);
        String originalValue = preferences.getString(key, null);
        preferences.edit().putString(key, "0.25%").apply();

        PrefScreenFeedback activity = null;
        PrefScreenFeedback recreated = null;
        Instrumentation.ActivityMonitor monitor = null;
        try {
            Intent intent = new Intent(EXPECTED_APPLICATION_ID + ".PREFS_FEEDBACK");
            intent.setPackage(EXPECTED_APPLICATION_ID);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Activity launched = instrumentation.startActivitySync(intent);
            instrumentation.waitForIdleSync();
            assertEquals(PrefScreenFeedback.class, launched.getClass());
            activity = (PrefScreenFeedback) launched;

            SeekBarPreferenceStringCompat clickVolume =
                    assertFeedbackPreferences(activity, "25%");
            assertEquals("0.25%", preferences.getString(key, null));

            SeekBarPreferenceDialogFragmentCompat cancelledDialog =
                    openSeekBarDialog(instrumentation, activity, clickVolume);
            dragSeekBar(instrumentation, cancelledDialog, 50);
            assertDialogValue(cancelledDialog, "50%");
            clickDialogButton(instrumentation, cancelledDialog,
                    DialogInterface.BUTTON_NEGATIVE);
            assertEquals("0.25%", preferences.getString(key, null));
            assertEquals("25%", clickVolume.getSummary());

            SeekBarPreferenceDialogFragmentCompat pendingDialog =
                    openSeekBarDialog(instrumentation, activity, clickVolume);
            dragSeekBar(instrumentation, pendingDialog, 75);
            assertDialogValue(pendingDialog, "75%");
            assertEquals("0.25%", preferences.getString(key, null));

            monitor = instrumentation.addMonitor(
                    PrefScreenFeedback.class.getName(), null, false);
            final PrefScreenFeedback activityToRecreate = activity;
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    activityToRecreate.recreate();
                }
            });
            Activity recreatedActivity = monitor.waitForActivityWithTimeout(5_000);
            assertNotNull("PrefScreenFeedback was not recreated", recreatedActivity);
            instrumentation.waitForIdleSync();
            recreated = (PrefScreenFeedback) recreatedActivity;

            SeekBarPreferenceStringCompat restoredPreference =
                    assertFeedbackPreferences(recreated, "25%");
            SeekBarPreferenceDialogFragmentCompat restoredDialog =
                    findSeekBarDialog(recreated);
            assertDialogValue(restoredDialog, "75%");
            assertEquals("0.25%", preferences.getString(key, null));

            clickDialogButton(instrumentation, restoredDialog,
                    DialogInterface.BUTTON_POSITIVE);
            assertEquals("0.75", preferences.getString(key, null));
            assertEquals("75%", restoredPreference.getSummary());
        } finally {
            if (monitor != null) {
                instrumentation.removeMonitor(monitor);
            }
            if (recreated != null) {
                finishActivity(instrumentation, recreated);
            } else if (activity != null) {
                finishActivity(instrumentation, activity);
            }
            SharedPreferences.Editor editor = preferences.edit();
            if (hadOriginalValue) {
                editor.putString(key, originalValue);
            } else {
                editor.remove(key);
            }
            editor.apply();
        }
    }

    private static ListPreference assertActionsPreferences(
            PrefScreenActions activity, boolean requireVisibleList) {
        assertEquals(1, activity.getSupportFragmentManager().getFragments().size());
        Fragment fragment = activity.getSupportFragmentManager().getFragments().get(0);
        assertNotNull("Actions preference fragment is missing", fragment);
        assertTrue(fragment instanceof PrefScreenActions.ActionsPreferenceFragment);

        PreferenceFragmentCompat preferences = (PreferenceFragmentCompat) fragment;
        assertNotNull(preferences.getPreferenceScreen());
        assertEquals("prefs_actions", preferences.getPreferenceScreen().getKey());
        if (requireVisibleList) {
            assertNotNull(preferences.getListView());
            assertEquals(View.VISIBLE, preferences.getListView().getVisibility());
        }

        ListPreference swipeUp = preferences.findPreference("pref_swipe_up");
        assertNotNull(swipeUp);
        assertEquals("settings", swipeUp.getValue());
        assertEquals(swipeUp.getEntry(), swipeUp.getSummary());
        return swipeUp;
    }

    private static SeekBarPreferenceStringCompat assertFeedbackPreferences(
            PrefScreenFeedback activity, String expectedSummary) {
        assertEquals(1, activity.getSupportFragmentManager().getFragments().size());
        Fragment fragment = activity.getSupportFragmentManager().getFragments().get(0);
        assertTrue(fragment instanceof PrefScreenFeedback.FeedbackPreferenceFragment);
        PreferenceFragmentCompat preferences = (PreferenceFragmentCompat) fragment;
        assertNotNull(preferences.getPreferenceScreen());
        assertEquals("feedback_settings", preferences.getPreferenceScreen().getKey());

        SeekBarPreferenceStringCompat clickVolume =
                preferences.findPreference("pref_click_volume");
        assertNotNull(clickVolume);
        assertEquals(expectedSummary, clickVolume.getSummary());
        return clickVolume;
    }

    private static SeekBarPreferenceDialogFragmentCompat openSeekBarDialog(
            Instrumentation instrumentation, PrefScreenFeedback activity,
            final SeekBarPreferenceStringCompat preference) {
        PrefScreenFeedback.FeedbackPreferenceFragment fragment =
                (PrefScreenFeedback.FeedbackPreferenceFragment)
                        activity.getSupportFragmentManager().getFragments().get(0);
        final ViewGroup preferenceList = fragment.getListView();
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                View preferenceRow = null;
                for (int index = 0; index < preferenceList.getChildCount(); index++) {
                    View child = preferenceList.getChildAt(index);
                    TextView title = (TextView) child.findViewById(android.R.id.title);
                    if (title != null && preference.getTitle().equals(title.getText())) {
                        preferenceRow = child;
                        break;
                    }
                }
                assertNotNull("Visible preference row is missing", preferenceRow);
                assertTrue("Preference row click was not handled", preferenceRow.performClick());
            }
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                fragment.getChildFragmentManager().executePendingTransactions();
            }
        });
        return findSeekBarDialog(activity);
    }

    private static SeekBarPreferenceDialogFragmentCompat findSeekBarDialog(
            PrefScreenFeedback activity) {
        PrefScreenFeedback.FeedbackPreferenceFragment fragment =
                (PrefScreenFeedback.FeedbackPreferenceFragment)
                        activity.getSupportFragmentManager().getFragments().get(0);
        Fragment dialog = fragment.getChildFragmentManager().findFragmentByTag(
                SeekBarPreferenceFragmentCompat.DIALOG_TAG);
        assertTrue(dialog instanceof SeekBarPreferenceDialogFragmentCompat);
        return (SeekBarPreferenceDialogFragmentCompat) dialog;
    }

    private static void dragSeekBar(
            Instrumentation instrumentation,
            final SeekBarPreferenceDialogFragmentCompat dialog,
            final int progress) {
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                SeekBar seekBar = (SeekBar) dialog.requireDialog()
                        .findViewById(R.id.seekBarPref);
                assertNotNull(seekBar);
                assertTrue("Seek bar must be laid out", seekBar.getWidth() > 0);
                float usableWidth = seekBar.getWidth()
                        - seekBar.getPaddingLeft() - seekBar.getPaddingRight();
                float x = seekBar.getPaddingLeft() + usableWidth * progress / 100.0f;
                float y = seekBar.getHeight() / 2.0f;
                long downTime = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(
                        downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent up = MotionEvent.obtain(
                        downTime, downTime + 16, MotionEvent.ACTION_UP, x, y, 0);
                seekBar.dispatchTouchEvent(down);
                seekBar.dispatchTouchEvent(up);
                down.recycle();
                up.recycle();
            }
        });
        instrumentation.waitForIdleSync();
    }

    private static void assertDialogValue(
            SeekBarPreferenceDialogFragmentCompat dialog, String expectedValue) {
        TextView value = (TextView) dialog.requireDialog().findViewById(R.id.seekVal);
        assertNotNull(value);
        assertEquals(expectedValue, value.getText().toString());
    }

    private static void clickDialogButton(
            Instrumentation instrumentation,
            final SeekBarPreferenceDialogFragmentCompat dialog,
            final int button) {
        final FragmentManager fragmentManager = dialog.getParentFragmentManager();
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                AlertDialog alertDialog = (AlertDialog) dialog.requireDialog();
                assertNotNull(alertDialog.getButton(button));
                alertDialog.getButton(button).performClick();
            }
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                fragmentManager.executePendingTransactions();
            }
        });
    }

    private static void assertImeIsRegistered(Context context) {
        InputMethodManager manager = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        assertNotNull(manager);

        String expectedPackage = context.getPackageName();
        String expectedService = LatinIME.class.getName();
        boolean found = false;
        for (InputMethodInfo inputMethod : manager.getInputMethodList()) {
            if (expectedPackage.equals(inputMethod.getServiceInfo().packageName)
                    && expectedService.equals(inputMethod.getServiceInfo().name)) {
                assertEquals(EXPECTED_DISPLAY_NAME,
                        inputMethod.loadLabel(context.getPackageManager()).toString());
                found = true;
                break;
            }
        }
        assertTrue("LatinIME is not registered as an input method", found);
    }

    private static <T extends Activity> T launchActivity(
            Instrumentation instrumentation, Context context, Class<T> activityClass) {
        Intent intent = new Intent(context, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Activity activity = instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        assertEquals(activityClass, activity.getClass());
        return activityClass.cast(activity);
    }

    private static void assertVisible(Activity activity, int viewId) {
        View view = activity.findViewById(viewId);
        assertNotNull("Missing required view " + viewId, view);
        assertEquals("Required view is not visible " + viewId, View.VISIBLE,
                view.getVisibility());
    }

    private static void finishActivity(
            final Instrumentation instrumentation, final Activity activity) {
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.finish();
            }
        });
        instrumentation.waitForIdleSync();
    }
}
