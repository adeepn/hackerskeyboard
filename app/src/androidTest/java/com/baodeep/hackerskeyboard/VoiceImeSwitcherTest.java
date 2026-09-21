// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;

import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;
import android.app.Instrumentation;
import android.content.Context;

import com.google.android.voiceime.VoiceRecognitionTrigger;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Real Android metadata with an injected system-service boundary; no microphone use. */
@RunWith(AndroidJUnit4.class)
public class VoiceImeSwitcherTest {
    private static final String OWN_PACKAGE = "com.baodeep.hackerskeyboard";

    private static InputMethodSubtype subtype(String mode) {
        return new InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeMode(mode).setSubtypeLocale("en_US").build();
    }

    private static final class FakeBackend implements VoiceImeSwitcher.Backend {
        final List<InputMethodInfo> methods = new ArrayList<>();
        final Map<String, List<InputMethodSubtype>> subtypes = new HashMap<>();
        String selectedId;
        InputMethodSubtype selectedSubtype;
        RuntimeException failure;
        boolean failDiscovery;

        InputMethodInfo add(String packageName, InputMethodSubtype... enabledSubtypes) {
            InputMethodInfo method = new InputMethodInfo(packageName,
                    packageName + ".VoiceService", "Test IME", null);
            methods.add(method);
            subtypes.put(method.getId(), Arrays.asList(enabledSubtypes));
            return method;
        }

        @Override
        public List<InputMethodInfo> enabledMethods() {
            if (failDiscovery) {
                throw new SecurityException("Test discovery denial");
            }
            return methods;
        }

        @Override
        public List<InputMethodSubtype> enabledSubtypes(InputMethodInfo method) {
            return subtypes.get(method.getId());
        }

        @Override
        public void switchTo(String id, InputMethodSubtype subtype) {
            if (failure != null) {
                throw failure;
            }
            selectedId = id;
            selectedSubtype = subtype;
        }
    }

    @Test
    public void nonGoogleEnabledVoiceSubtypeIsSelectedExactly() {
        FakeBackend backend = new FakeBackend();
        backend.add("example.keyboard", subtype("keyboard"));
        InputMethodSubtype voice = subtype("voice");
        InputMethodInfo provider = backend.add("example.offline", subtype("keyboard"), voice);
        assertEquals(VoiceImeSwitcher.Result.REQUESTED,
                new VoiceImeSwitcher(backend, OWN_PACKAGE).start());
        assertEquals(provider.getId(), backend.selectedId);
        assertSame(voice, backend.selectedSubtype);
    }

    @Test
    public void unavailableDoesNotSwitchSelfKeyboardOrDisabledSubtype() {
        FakeBackend backend = new FakeBackend();
        VoiceImeSwitcher switcher = new VoiceImeSwitcher(backend, OWN_PACKAGE);
        assertEquals(VoiceImeSwitcher.Result.UNAVAILABLE, switcher.start());
        backend.add(OWN_PACKAGE, subtype("voice"));
        backend.add("example.keyboard", subtype("keyboard"));
        backend.add("example.disabledVoice");
        assertEquals(VoiceImeSwitcher.Result.UNAVAILABLE, switcher.start());
        assertNull(backend.selectedId);
    }

    @Test
    public void everyRequestUsesFreshSystemOrderWithoutGooglePreference() {
        FakeBackend backend = new FakeBackend();
        InputMethodInfo first = backend.add("example.offline", subtype("voice"));
        InputMethodInfo second = backend.add("com.google.android.example", subtype("voice"));
        VoiceImeSwitcher switcher = new VoiceImeSwitcher(backend, OWN_PACKAGE);
        assertEquals(VoiceImeSwitcher.Result.REQUESTED, switcher.start());
        assertEquals(first.getId(), backend.selectedId);
        backend.methods.remove(first);
        assertEquals(VoiceImeSwitcher.Result.REQUESTED, switcher.start());
        assertEquals(second.getId(), backend.selectedId);
        backend.methods.clear();
        backend.selectedId = null;
        assertEquals(VoiceImeSwitcher.Result.UNAVAILABLE, switcher.start());
        assertNull(backend.selectedId);
    }

    @Test
    public void providerRemovalAndSecurityDenialAreNotAnUnavailableFallback() {
        for (RuntimeException failure : new RuntimeException[] {
                new SecurityException("Test token rejection"),
                new IllegalArgumentException("Test provider removal")}) {
            FakeBackend backend = new FakeBackend();
            backend.add("example.offline", subtype("voice"));
            backend.failure = failure;
            assertEquals(VoiceImeSwitcher.Result.FAILED,
                    new VoiceImeSwitcher(backend, OWN_PACKAGE).start());
            assertNull(backend.selectedId);
        }
        FakeBackend backend = new FakeBackend();
        backend.failDiscovery = true;
        assertEquals(VoiceImeSwitcher.Result.FAILED,
                new VoiceImeSwitcher(backend, OWN_PACKAGE).start());
    }

    private static final class TestIme extends LatinIME {
        TestIme(Context context) {
            attachBaseContext(context);
        }
    }

    private static final class LegacyTrigger extends VoiceRecognitionTrigger {
        int starts;
        boolean installed = true;

        LegacyTrigger(TestIme ime) {
            super(ime);
        }

        @Override
        public boolean isInstalled() {
            return installed;
        }

        @Override
        public void startVoiceRecognition() {
            starts++;
        }
    }

    @Test
    public void latinImeFallsBackOnlyWhenNoEnabledVoiceSubtypeExists() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Field switcherField = LatinIME.class.getDeclaredField("mVoiceImeSwitcher");
        switcherField.setAccessible(true);
        Field legacyField = LatinIME.class.getDeclaredField("mVoiceRecognitionTrigger");
        legacyField.setAccessible(true);
        Method start = LatinIME.class.getDeclaredMethod("startVoiceInput");
        start.setAccessible(true);
        instrumentation.runOnMainSync(() -> {
            TestIme ime = new TestIme(instrumentation.getTargetContext());
            FakeBackend backend = new FakeBackend();
            LegacyTrigger legacy = new LegacyTrigger(ime);
            try {
                switcherField.set(ime, new VoiceImeSwitcher(backend, OWN_PACKAGE));
                legacyField.set(ime, legacy);
                start.invoke(ime);
                assertEquals(1, legacy.starts);
                legacy.installed = false;
                start.invoke(ime);
                assertEquals(1, legacy.starts);
                legacy.installed = true;
                backend.add("example.offline", subtype("voice"));
                start.invoke(ime);
                assertEquals(1, legacy.starts);
                backend.failure = new SecurityException("Test provider loss");
                start.invoke(ime);
                assertEquals(1, legacy.starts);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
    }
}
