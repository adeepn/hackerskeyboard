/*
 * SPDX-License-Identifier: Apache-2.0
 */

// Modified for Hacker's Keyboard v2; see the repository history for details.
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
        try {
            Intent intent = new Intent(EXPECTED_APPLICATION_ID + ".PREFS_FEEDBACK");
            intent.setPackage(EXPECTED_APPLICATION_ID);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Activity launched = instrumentation.startActivitySync(intent);
            instrumentation.waitForIdleSync();
            assertEquals(PrefScreenFeedback.class, launched.getClass());
            activity = (PrefScreenFeedback) launched;

            SeekBarPreferenceStringCompat clickVolume =
                    assertFeedbackPreferences(instrumentation, activity, "25%");
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

            final PrefScreenFeedback activityToRecreate = activity;
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    activityToRecreate.recreate();
                }
            });
            recreated = waitForResumedActivity(
                    instrumentation, PrefScreenFeedback.class, activityToRecreate);

            SeekBarPreferenceStringCompat restoredPreference =
                    assertFeedbackPreferences(instrumentation, recreated, "25%");
            SeekBarPreferenceDialogFragmentCompat restoredDialog =
                    waitForSeekBarDialog(instrumentation, recreated);
            assertDialogValue(restoredDialog, "75%");
            assertEquals("0.25%", preferences.getString(key, null));

            clickDialogButton(instrumentation, restoredDialog,
                    DialogInterface.BUTTON_POSITIVE);
            assertEquals("0.75", preferences.getString(key, null));
            assertEquals("75%", restoredPreference.getSummary());
        } finally {
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
    public void viewSettingsReadExistingStringsAndSurviveRecreation() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SharedPreferences preferences = context.getSharedPreferences(
                DEFAULT_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        String hintKey = "pref_hint_mode";
        String scaleKey = "pref_top_row_scale";
        boolean hadOriginalHint = preferences.contains(hintKey);
        boolean hadOriginalScale = preferences.contains(scaleKey);
        String originalHint = preferences.getString(hintKey, null);
        String originalScale = preferences.getString(scaleKey, null);
        preferences.edit()
                .putString(hintKey, "2")
                .putString(scaleKey, "0.75%")
                .apply();

        PrefScreenView activity = null;
        PrefScreenView recreated = null;
        try {
            Intent intent = new Intent(EXPECTED_APPLICATION_ID + ".PREFS_VIEW");
            intent.setPackage(EXPECTED_APPLICATION_ID);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Activity launched = instrumentation.startActivitySync(intent);
            instrumentation.waitForIdleSync();
            assertEquals(PrefScreenView.class, launched.getClass());
            activity = (PrefScreenView) launched;

            assertViewPreferences(instrumentation, activity);
            assertEquals("2", preferences.getString(hintKey, null));
            assertEquals("0.75%", preferences.getString(scaleKey, null));

            final PrefScreenView activityToRecreate = activity;
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    activityToRecreate.recreate();
                }
            });
            recreated = waitForResumedActivity(
                    instrumentation, PrefScreenView.class, activityToRecreate);

            assertViewPreferences(instrumentation, recreated);
            assertEquals("2", preferences.getString(hintKey, null));
            assertEquals("0.75%", preferences.getString(scaleKey, null));
        } finally {
            if (recreated != null) {
                finishActivity(instrumentation, recreated);
            } else if (activity != null) {
                finishActivity(instrumentation, activity);
            }
            SharedPreferences.Editor editor = preferences.edit();
            if (hadOriginalHint) {
                editor.putString(hintKey, originalHint);
            } else {
                editor.remove(hintKey);
            }
            if (hadOriginalScale) {
                editor.putString(scaleKey, originalScale);
            } else {
                editor.remove(scaleKey);
            }
            editor.apply();
        }
    }

    @Test
    public void languageSettingsPreserveConsolidatedStringsAndLegacyFallback() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SharedPreferences preferences = context.getSharedPreferences(
                DEFAULT_SHARED_PREFERENCES, Context.MODE_PRIVATE);
        String selectedKey = "selected_languages";
        String inputKey = "input_language";
        boolean hadOriginalSelected = preferences.contains(selectedKey);
        boolean hadOriginalInput = preferences.contains(inputKey);
        String originalSelected = preferences.getString(selectedKey, null);
        String originalInput = preferences.getString(inputKey, null);
        preferences.edit()
                .putString(selectedKey, "en_US,ru_PH,")
                .putString(inputKey, "ru_PH")
                .apply();

        InputLanguageSelection activity = null;
        InputLanguageSelection recreated = null;
        try {
            Intent intent = new Intent(
                    EXPECTED_APPLICATION_ID + ".INPUT_LANGUAGE_SELECTION");
            intent.setPackage(EXPECTED_APPLICATION_ID);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Activity launched = instrumentation.startActivitySync(intent);
            instrumentation.waitForIdleSync();
            assertEquals(InputLanguageSelection.class, launched.getClass());
            activity = (InputLanguageSelection) launched;

            InputLanguageSelection.LanguagePreferenceFragment fragment =
                    assertLanguagePreferences(instrumentation, activity, false);
            assertEquals("en_US,ru_PH,", preferences.getString(selectedKey, null));
            assertEquals("ru_PH", preferences.getString(inputKey, null));

            final CheckBoxPreference german = findLanguagePreference(fragment, "de");
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    german.setChecked(true);
                }
            });

            final InputLanguageSelection activityToRecreate = activity;
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    activityToRecreate.recreate();
                }
            });
            recreated = waitForResumedActivity(
                    instrumentation, InputLanguageSelection.class, activityToRecreate);

            final InputLanguageSelection.LanguagePreferenceFragment recreatedFragment =
                    assertLanguagePreferences(instrumentation, recreated, true);
            String canonicalSelection = preferences.getString(selectedKey, null);
            assertNotNull(canonicalSelection);
            assertTrue(canonicalSelection.endsWith(","));
            Set<String> selectedLanguages = new HashSet<String>(
                    Arrays.asList(canonicalSelection.split(",")));
            assertTrue(selectedLanguages.contains("en"));
            assertTrue(selectedLanguages.contains("ru_PH"));
            assertTrue(selectedLanguages.contains("de"));
            assertFalse(selectedLanguages.contains("en_US"));
            assertEquals("ru_PH", preferences.getString(inputKey, null));

            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    for (int index = 0;
                            index < recreatedFragment.getPreferenceScreen()
                                    .getPreferenceCount(); index++) {
                        CheckBoxPreference preference = (CheckBoxPreference)
                                recreatedFragment.getPreferenceScreen().getPreference(index);
                        preference.setChecked(false);
                    }
                }
            });
            finishActivity(instrumentation, recreated);
            recreated = null;
            activity = null;
            assertFalse(preferences.contains(selectedKey));
            assertEquals("ru_PH", preferences.getString(inputKey, null));
        } finally {
            if (recreated != null) {
                finishActivity(instrumentation, recreated);
            } else if (activity != null) {
                finishActivity(instrumentation, activity);
            }
            SharedPreferences.Editor editor = preferences.edit();
            if (hadOriginalSelected) {
                editor.putString(selectedKey, originalSelected);
            } else {
                editor.remove(selectedKey);
            }
            if (hadOriginalInput) {
                editor.putString(inputKey, originalInput);
            } else {
                editor.remove(inputKey);
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
            Instrumentation instrumentation, PrefScreenFeedback activity,
            String expectedSummary) {
        PrefScreenFeedback.FeedbackPreferenceFragment fragment =
                waitForFeedbackFragment(instrumentation, activity);
        assertEquals(1, activity.getSupportFragmentManager().getFragments().size());
        PreferenceFragmentCompat preferences = fragment;
        assertNotNull(preferences.getPreferenceScreen());
        assertEquals("feedback_settings", preferences.getPreferenceScreen().getKey());

        SeekBarPreferenceStringCompat clickVolume =
                preferences.findPreference("pref_click_volume");
        assertNotNull(clickVolume);
        assertEquals(expectedSummary, clickVolume.getSummary());
        return clickVolume;
    }

    private static void assertViewPreferences(
            Instrumentation instrumentation, PrefScreenView activity) {
        PrefScreenView.ViewPreferenceFragment fragment = waitForPreferenceFragment(
                instrumentation,
                activity,
                PrefScreenView.FRAGMENT_TAG,
                PrefScreenView.ViewPreferenceFragment.class,
                "view");
        assertEquals(1, activity.getSupportFragmentManager().getFragments().size());
        assertNotNull(fragment.getPreferenceScreen());
        assertEquals("prefs_view", fragment.getPreferenceScreen().getKey());

        ListPreference hintMode = fragment.findPreference("pref_hint_mode");
        assertNotNull(hintMode);
        assertEquals("2", hintMode.getValue());
        assertEquals(hintMode.getEntry(), hintMode.getSummary());

        SeekBarPreferenceStringCompat labelScale =
                fragment.findPreference("pref_label_scale_v2");
        SeekBarPreferenceStringCompat candidateScale =
                fragment.findPreference("pref_candidate_scale");
        SeekBarPreferenceStringCompat topRowScale =
                fragment.findPreference("pref_top_row_scale");
        assertNotNull(labelScale);
        assertNotNull(candidateScale);
        assertNotNull(topRowScale);
        assertEquals("75%", topRowScale.getSummary());

        ListPreference keyboardLayout = fragment.findPreference("pref_keyboard_layout");
        ListPreference renderMode = fragment.findPreference("pref_render_mode");
        assertNotNull(keyboardLayout);
        assertNotNull(renderMode);
        assertEquals(keyboardLayout.getEntry(), keyboardLayout.getSummary());
        if (LatinKeyboardBaseView.sSetRenderMode == null) {
            assertFalse(renderMode.isEnabled());
            assertEquals(activity.getString(R.string.render_mode_unavailable),
                    renderMode.getSummary());
        } else {
            assertTrue(renderMode.isEnabled());
            assertEquals(renderMode.getEntry(), renderMode.getSummary());
        }
    }

    private static InputLanguageSelection.LanguagePreferenceFragment
            assertLanguagePreferences(
                    Instrumentation instrumentation,
                    InputLanguageSelection activity,
                    boolean expectGerman) {
        InputLanguageSelection.LanguagePreferenceFragment fragment =
                waitForPreferenceFragment(
                        instrumentation,
                        activity,
                        InputLanguageSelection.FRAGMENT_TAG,
                        InputLanguageSelection.LanguagePreferenceFragment.class,
                        "language");
        assertEquals(1, activity.getSupportFragmentManager().getFragments().size());
        assertNotNull(fragment.getPreferenceScreen());
        assertTrue(fragment.getPreferenceScreen().getPreferenceCount() > 30);

        for (int index = 0;
                index < fragment.getPreferenceScreen().getPreferenceCount(); index++) {
            Preference preference = fragment.getPreferenceScreen().getPreference(index);
            assertTrue(preference instanceof CheckBoxPreference);
            assertFalse(preference.isPersistent());
            assertNull(preference.getKey());
        }

        CheckBoxPreference english = findLanguagePreference(fragment, "en");
        CheckBoxPreference phoneticRussian = findLanguagePreference(fragment, "ru_PH");
        CheckBoxPreference german = findLanguagePreference(fragment, "de");
        assertTrue(english.isChecked());
        assertTrue(phoneticRussian.isChecked());
        if (expectGerman) {
            assertTrue(german.isChecked());
        } else {
            assertFalse(german.isChecked());
        }
        assertNotNull(english.getSummary());
        assertTrue(english.getSummary().toString().contains("5-row"));
        assertTrue(english.getSummary().toString().contains("4-row"));
        return fragment;
    }

    private static CheckBoxPreference findLanguagePreference(
            InputLanguageSelection.LanguagePreferenceFragment fragment,
            String localeCode) {
        String titleSuffix = " [" + localeCode + "]";
        for (int index = 0;
                index < fragment.getPreferenceScreen().getPreferenceCount(); index++) {
            Preference preference = fragment.getPreferenceScreen().getPreference(index);
            if (preference.getTitle() != null
                    && preference.getTitle().toString().endsWith(titleSuffix)) {
                return (CheckBoxPreference) preference;
            }
        }
        throw new AssertionError("Missing dynamic language preference " + localeCode);
    }

    private static SeekBarPreferenceDialogFragmentCompat openSeekBarDialog(
            Instrumentation instrumentation, PrefScreenFeedback activity,
            final SeekBarPreferenceStringCompat preference) {
        PrefScreenFeedback.FeedbackPreferenceFragment fragment =
                waitForFeedbackFragment(instrumentation, activity);
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
        return waitForSeekBarDialog(instrumentation, activity);
    }

    private static PrefScreenFeedback.FeedbackPreferenceFragment waitForFeedbackFragment(
            Instrumentation instrumentation, final PrefScreenFeedback activity) {
        return waitForPreferenceFragment(
                instrumentation,
                activity,
                PrefScreenFeedback.FRAGMENT_TAG,
                PrefScreenFeedback.FeedbackPreferenceFragment.class,
                "feedback");
    }

    private static <T extends Fragment> T waitForPreferenceFragment(
            Instrumentation instrumentation,
            final FragmentActivity activity,
            final String tag,
            final Class<T> fragmentClass,
            final String description) {
        final Fragment[] result = new Fragment[1];
        long deadline = SystemClock.uptimeMillis() + 5_000;
        while (result[0] == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    FragmentManager manager = activity.getSupportFragmentManager();
                    if (!manager.isDestroyed()) {
                        manager.executePendingTransactions();
                        Fragment candidate = manager.findFragmentByTag(tag);
                        if (fragmentClass.isInstance(candidate) && candidate.isAdded()) {
                            result[0] = candidate;
                        }
                    }
                }
            });
            if (result[0] == null) {
                SystemClock.sleep(50);
            }
        }
        assertNotNull("Attached " + description + " preference fragment is missing",
                result[0]);
        return fragmentClass.cast(result[0]);
    }

    private static SeekBarPreferenceDialogFragmentCompat waitForSeekBarDialog(
            Instrumentation instrumentation, PrefScreenFeedback activity) {
        final PrefScreenFeedback.FeedbackPreferenceFragment parent =
                waitForFeedbackFragment(instrumentation, activity);
        final SeekBarPreferenceDialogFragmentCompat[] result =
                new SeekBarPreferenceDialogFragmentCompat[1];
        long deadline = SystemClock.uptimeMillis() + 5_000;
        while (result[0] == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    FragmentManager manager = parent.getChildFragmentManager();
                    if (!manager.isDestroyed()) {
                        manager.executePendingTransactions();
                        Fragment candidate = manager.findFragmentByTag(
                                SeekBarPreferenceFragmentCompat.DIALOG_TAG);
                        if (candidate instanceof SeekBarPreferenceDialogFragmentCompat
                                && candidate.isAdded()
                                && ((SeekBarPreferenceDialogFragmentCompat) candidate).getDialog()
                                        != null) {
                            result[0] = (SeekBarPreferenceDialogFragmentCompat) candidate;
                        }
                    }
                }
            });
            if (result[0] == null) {
                SystemClock.sleep(50);
            }
        }
        assertNotNull("Attached seek-bar dialog fragment is missing", result[0]);
        return result[0];
    }

    private static <T extends Activity> T waitForResumedActivity(
            Instrumentation instrumentation, final Class<T> activityClass,
            final Activity previousActivity) {
        final Activity[] result = new Activity[1];
        long deadline = SystemClock.uptimeMillis() + 5_000;
        while (result[0] == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    Collection<Activity> resumed = ActivityLifecycleMonitorRegistry
                            .getInstance().getActivitiesInStage(Stage.RESUMED);
                    for (Activity candidate : resumed) {
                        if (activityClass.isInstance(candidate)
                                && candidate != previousActivity
                                && !candidate.isFinishing()
                                && !candidate.isDestroyed()) {
                            result[0] = candidate;
                            break;
                        }
                    }
                }
            });
            if (result[0] == null) {
                SystemClock.sleep(50);
            }
        }
        assertNotNull(activityClass.getSimpleName() + " was not resumed after recreation",
                result[0]);
        return activityClass.cast(result[0]);
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
