// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.InputType;
import android.content.Context;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** No dictionaries/providers are installed: a boundary access fails immediately. */
@RunWith(AndroidJUnit4.class)
public class PrivacyLearningTest {
    @Test public void restrictedSessionsReturnBeforeAnyDictionaryOrEditorAccess() throws Exception {
        Method learn = LatinIME.class.getDeclaredMethod("checkAddToDictionary",
                CharSequence.class, int.class, boolean.class);
        learn.setAccessible(true);
        Field correction = LatinIME.class.getDeclaredField("mCorrectionMode");
        correction.setAccessible(true);
        Field allowed = LatinIME.class.getDeclaredField("mEditorAllowsLearning");
        allowed.setAccessible(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            LatinIME ime = new LatinIME();
            try {
                correction.setInt(ime, Suggest.CORRECTION_FULL_BIGRAM);
                EditorInfo editor = new EditorInfo();
                // Repeated transitions include ordinary -> restricted and restricted -> ordinary.
                for (int type : new int[] {
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                        InputType.TYPE_CLASS_TEXT}) {
                    editor.inputType = InputType.TYPE_CLASS_TEXT;
                    editor.imeOptions = 0;
                    ime.onStartInput(editor, false);
                    assertTrue(allowed.getBoolean(ime));
                    editor.inputType = type;
                    editor.imeOptions = EditorPrivacyPolicy.isPassword(type) ? 0
                            : EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
                    ime.onStartInput(editor, false);
                    assertFalse(allowed.getBoolean(ime));
                    assertFalse(ime.addWordToDictionary("SYNTHETIC_PRIVATE_WORD"));
                    learn.invoke(ime, "SYNTHETIC_PRIVATE_WORD", 1, true);
                    learn.invoke(ime, "SYNTHETIC_PRIVATE_WORD", 1, false);
                    ime.promoteToUserDictionary("SYNTHETIC_PRIVATE_WORD", 128);
                }
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
    }
    private static final class RecordingUserDictionary extends UserDictionary {
        int writes;
        RecordingUserDictionary(Context context) { super(context, ""); }
        @Override public void loadDictionary() { /* Never query a real provider. */ }
        @Override public synchronized void addWord(String word, int frequency) { writes++; }
        @Override public synchronized boolean isValidWord(CharSequence word) { return false; }
    }

    private static final class RecordingAutoDictionary extends AutoDictionary {
        int writes;
        RecordingAutoDictionary(Context context, LatinIME ime) {
            super(context, ime, "", Suggest.DIC_AUTO);
        }
        @Override public boolean isValidWord(CharSequence word) { return true; }
        @Override public void addWord(String word, int frequency) { writes++; }
        @Override public void flushPendingWrites() { }
    }

    private static final class RecordingBigramDictionary extends UserBigramDictionary {
        int writes;
        RecordingBigramDictionary(Context context, LatinIME ime) {
            super(context, ime, "", Suggest.DIC_USER);
        }
        @Override public int addBigrams(String first, String second) { writes++; return 1; }
        @Override public void flushPendingWrites() { }
    }

    private static final class TestIme extends LatinIME {
        TestIme(Context context) { attachBaseContext(context); }
        @Override public InputConnection getCurrentInputConnection() {
            return new BaseInputConnection(new android.view.View(this), false) {
                @Override public CharSequence getTextBeforeCursor(int length, int flags) {
                    return "synthetic_previous synthetic_current";
                }
            };
        }
    }

    private static void set(LatinIME ime, String name, Object value) throws Exception {
        Field field = LatinIME.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(ime, value);
    }

    @Test public void ordinarySessionRestoresActualWritesAndFinishRevokesThem() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            TestIme ime = new TestIme(context);
            RecordingUserDictionary user = new RecordingUserDictionary(context);
            RecordingAutoDictionary auto = new RecordingAutoDictionary(context, ime);
            RecordingBigramDictionary bigram = new RecordingBigramDictionary(context, ime);
            try {
                set(ime, "mUserDictionary", user);
                set(ime, "mAutoDictionary", auto);
                set(ime, "mUserBigramDictionary", bigram);
                set(ime, "mCorrectionMode", Suggest.CORRECTION_FULL_BIGRAM);
                set(ime, "mSentenceSeparators", ".!?");
                java.lang.reflect.Constructor<KeyboardSwitcher> keyboardConstructor =
                        KeyboardSwitcher.class.getDeclaredConstructor();
                keyboardConstructor.setAccessible(true);
                set(ime, "mKeyboardSwitcher", keyboardConstructor.newInstance());
                Method learn = LatinIME.class.getDeclaredMethod("checkAddToDictionary",
                        CharSequence.class, int.class, boolean.class);
                learn.setAccessible(true);
                EditorInfo editor = new EditorInfo();
                editor.inputType = InputType.TYPE_CLASS_TEXT;
                editor.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
                ime.onStartInput(editor, false);
                assertFalse(ime.addWordToDictionary("synthetic_word"));
                learn.invoke(ime, "synthetic_word", 1, false);
                ime.promoteToUserDictionary("synthetic_word", 128);
                assertTrue(user.writes == 0 && auto.writes == 0 && bigram.writes == 0);
                editor.imeOptions = 0;
                ime.onStartInput(editor, false);
                assertTrue(ime.addWordToDictionary("synthetic_word"));
                learn.invoke(ime, "synthetic_word", 1, false);
                ime.promoteToUserDictionary("synthetic_word", 128);
                assertTrue(user.writes == 2 && auto.writes == 1 && bigram.writes == 1);
                ime.onFinishInput();
                assertFalse(ime.addWordToDictionary("synthetic_word"));
                learn.invoke(ime, "synthetic_word", 1, false);
                ime.promoteToUserDictionary("synthetic_word", 128);
                assertTrue(user.writes == 2 && auto.writes == 1 && bigram.writes == 1);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            } finally {
                user.close();
            }
        });
    }

}
