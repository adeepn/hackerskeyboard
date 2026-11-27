// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;

import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.EditorInfo;
import android.text.InputType;
import android.view.inputmethod.InputMethodSubtype;
import android.app.Instrumentation;
import android.content.Context;


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
        final EditorInfo editor = new EditorInfo();
        @Override public EditorInfo getCurrentInputEditorInfo() { return editor; }
        void startEditor(int type, int options) {
            editor.inputType = type;
            editor.imeOptions = options;
            onStartInput(editor, false);
        }
        TestIme(Context context) {
            attachBaseContext(context);
        }
    }

    @Test
    public void unavailableVoiceImeHasNoLegacyRecognizerFallback() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Field switcherField = LatinIME.class.getDeclaredField("mVoiceImeSwitcher");
        switcherField.setAccessible(true);
        Method start = LatinIME.class.getDeclaredMethod("startVoiceInput");
        start.setAccessible(true);
        instrumentation.runOnMainSync(() -> {
            TestIme ime = new TestIme(instrumentation.getTargetContext());
            FakeBackend backend = new FakeBackend();
            try {
                ime.startEditor(InputType.TYPE_CLASS_TEXT, 0);
                switcherField.set(ime, new VoiceImeSwitcher(backend, OWN_PACKAGE));
                start.invoke(ime);
                assertNull(backend.selectedId);
                for (Field field : LatinIME.class.getDeclaredFields()) {
                    org.junit.Assert.assertFalse(field.getType().getName()
                            .startsWith("com.google.android.voiceime."));
                }
                try {
                    Class.forName("com.google.android.voiceime.VoiceRecognitionTrigger");
                    org.junit.Assert.fail("Legacy deferred recognizer must not be packaged");
                } catch (ClassNotFoundException expected) {
                    // No trigger can schedule a cross-editor deferred result.
                }
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    public void sensitiveSessionsBlockDirectVoiceDispatchAndOrdinarySessionRestoresIt()
            throws Exception {
        Field switcherField = LatinIME.class.getDeclaredField("mVoiceImeSwitcher");
        switcherField.setAccessible(true);
        Method start = LatinIME.class.getDeclaredMethod("startVoiceInput");
        start.setAccessible(true);
        Method show = LatinIME.class.getDeclaredMethod("shouldShowVoiceButton", EditorInfo.class);
        show.setAccessible(true);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            TestIme ime = new TestIme(instrumentation.getTargetContext());
            FakeBackend backend = new FakeBackend();
            backend.add("example.offline", subtype("voice"));
            try {
                switcherField.set(ime, new VoiceImeSwitcher(backend, OWN_PACKAGE));
                start.invoke(ime); // No editor session must fail closed.
                assertNull(backend.selectedId);
                for (int type : new int[] {
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD}) {
                    ime.startEditor(type, 0);
                    assertEquals(false, show.invoke(ime, ime.editor));
                    start.invoke(ime);
                    assertNull(backend.selectedId);
                }
                ime.startEditor(InputType.TYPE_CLASS_TEXT,
                        EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
                assertEquals(false, show.invoke(ime, ime.editor));
                start.invoke(ime);
                assertNull(backend.selectedId);
                ime.startEditor(InputType.TYPE_CLASS_TEXT, 0);
                assertEquals(true, show.invoke(ime, ime.editor));
                start.invoke(ime);
                assertEquals(backend.methods.get(0).getId(), backend.selectedId);
                Field keyboard = LatinIME.class.getDeclaredField("mKeyboardSwitcher");
                keyboard.setAccessible(true);
                java.lang.reflect.Constructor<KeyboardSwitcher> keyboardConstructor =
                        KeyboardSwitcher.class.getDeclaredConstructor();
                keyboardConstructor.setAccessible(true);
                keyboard.set(ime, keyboardConstructor.newInstance());
                ime.onFinishInput();
                backend.selectedId = null;
                assertEquals(false, show.invoke(ime, ime.editor));
                start.invoke(ime);
                assertNull(backend.selectedId);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
    }

}
