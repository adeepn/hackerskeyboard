/*
 * SPDX-License-Identifier: Apache-2.0
 */

// Modified for Hacker's Keyboard v2; see the repository history for details.
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ApplicationSmokeTest {
    private static final String EXPECTED_APPLICATION_ID =
            "com.baodeep.hackerskeyboard";
    private static final String EXPECTED_DISPLAY_NAME = "Hacker's Keyboard v2";

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
