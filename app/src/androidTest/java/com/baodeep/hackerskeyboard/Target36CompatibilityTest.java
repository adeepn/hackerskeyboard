// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Run only on disposable emulator/device test installs; restores enabled/default IME. */
@RunWith(AndroidJUnit4.class)
public class Target36CompatibilityTest {
    @Test
    public void activityInsetsDoNotAccumulateAcrossDispatches() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        assertEquals(36, context.getApplicationInfo().targetSdkVersion);
        assertEquals(24, context.getApplicationInfo().minSdkVersion);
        if (Build.VERSION.SDK_INT < 35) {
            return; // No edge-to-edge enforcement on this device.
        }
        for (Class<?> screen : new Class<?>[] {Main.class, LatinIMESettings.class,
                PrefScreenActions.class, PrefScreenFeedback.class, PrefScreenView.class,
                InputLanguageSelection.class}) {
            Activity activity = instrumentation.startActivitySync(new Intent(context, screen)
                    .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try {
                instrumentation.runOnMainSync(() -> {
                    View content = activity.findViewById(android.R.id.content);
                    WindowInsetsCompat bars = new WindowInsetsCompat.Builder()
                            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(7, 19, 11, 23))
                            .build();
                    ViewCompat.dispatchApplyWindowInsets(content, bars);
                    assertEquals(7, content.getPaddingLeft());
                    assertEquals(19, content.getPaddingTop());
                    assertEquals(11, content.getPaddingRight());
                    assertEquals(23, content.getPaddingBottom());
                    ViewCompat.dispatchApplyWindowInsets(content, bars);
                    assertEquals(19, content.getPaddingTop());
                    assertEquals(23, content.getPaddingBottom());
                    ViewCompat.dispatchApplyWindowInsets(content, new WindowInsetsCompat.Builder()
                            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE).build());
                    assertEquals(0, content.getPaddingTop());
                    assertEquals(0, content.getPaddingBottom());
                });
            } finally {
                instrumentation.runOnMainSync(activity::finish);
            }
        }
    }

    @Test
    public void realImeSessionTypesDeletesAndOpensSettings() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        String ownId = context.getPackageName() + "/.LatinIME";
        String oldId = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        boolean wasEnabled = manager.getEnabledInputMethodList().stream()
                .anyMatch(method -> ownId.equals(method.getId()));
        Main main = null;
        LatinIMESettings settings = null;
        try {
            shell(instrumentation, "ime enable " + ownId);
            shell(instrumentation, "ime set " + ownId);
            main = (Main) instrumentation.startActivitySync(new Intent(context, Main.class)
                    .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            EditText editor = main.findViewById(R.id.main_setup_edit_test);
            instrumentation.runOnMainSync(() -> {
                editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                editor.setText("");
                editor.requestFocus();
                manager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            });
            LatinIME[] active = new LatinIME[1];
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (active[0] == null && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync(() -> {
                    LatinIME ime = LatinIME.sInstance;
                    if (ime != null && ime.isInputViewShown() && ime.getCurrentInputConnection() != null
                            && ime.getCurrentInputEditorInfo() != null
                            && context.getPackageName().equals(ime.getCurrentInputEditorInfo().packageName)) {
                        active[0] = ime;
                    } else {
                        manager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
                    }
                });
                if (active[0] == null) SystemClock.sleep(100);
            }
            assertNotNull("IME must start a real editor session", active[0]);
            instrumentation.runOnMainSync(() -> {
                active[0].onKey('a', new int[] {'a'}, 0, 0);
                active[0].onKey('b', new int[] {'b'}, 0, 0);
                active[0].onKey(Keyboard.KEYCODE_DELETE, null, 0, 0);
                active[0].onKey('c', new int[] {'c'}, 0, 0);
                active[0].getCurrentInputConnection().finishComposingText();
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> assertEquals("ac", editor.getText().toString()));
            instrumentation.runOnMainSync(() -> active[0].onKey(
                    LatinKeyboardView.KEYCODE_OPTIONS, null, 0, 0));
            settings = ApplicationSmokeTest.waitForResumedActivity(
                    instrumentation, LatinIMESettings.class, null);
            assertNotNull("Settings must launch from the service with NEW_TASK", settings);
        } finally {
            Activity settingsToFinish = settings;
            Activity mainToFinish = main;
            instrumentation.runOnMainSync(() -> {
                if (settingsToFinish != null) settingsToFinish.finish();
                if (mainToFinish != null) mainToFinish.finish();
            });
            try {
                if (oldId != null && oldId.matches("[A-Za-z0-9_.$/]+")) {
                    shell(instrumentation, "ime set " + oldId);
                }
            } finally {
                if (!wasEnabled) shell(instrumentation, "ime disable " + ownId);
            }
        }
    }

    private static void shell(Instrumentation instrumentation, String command) throws Exception {
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(command);
                ParcelFileDescriptor.AutoCloseInputStream input =
                        new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { /* Drain only non-sensitive IME management output. */ }
        }
    }
}
