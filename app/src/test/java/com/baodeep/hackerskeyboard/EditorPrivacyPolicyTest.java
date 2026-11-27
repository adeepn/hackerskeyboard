// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.*;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import org.junit.Test;

public class EditorPrivacyPolicyTest {
    @Test public void sensitiveEditorsBlockLearningAndVoice() {
        for (int type : new int[] {
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD}) {
            assertTrue(EditorPrivacyPolicy.isPassword(type));
            assertFalse(EditorPrivacyPolicy.allowsLearning(type, 0));
            assertFalse(EditorPrivacyPolicy.allowsVoice(type, 0));
        }
        assertFalse(EditorPrivacyPolicy.allowsLearning(InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING));
        assertFalse(EditorPrivacyPolicy.allowsVoice(InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING));
    }
    @Test public void ordinaryEditorsKeepLearningAndVoice() {
        assertTrue(EditorPrivacyPolicy.allowsLearning(InputType.TYPE_CLASS_TEXT, 0));
        assertTrue(EditorPrivacyPolicy.allowsVoice(InputType.TYPE_CLASS_NUMBER, 0));
        assertFalse(EditorPrivacyPolicy.isPassword(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_TEXT_VARIATION_PASSWORD));
    }
}
