// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

/** Editor-declared privacy restrictions, independent of suggestion preferences. */
final class EditorPrivacyPolicy {
    private EditorPrivacyPolicy() {}

    static boolean isPassword(int inputType) {
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        return (inputClass == InputType.TYPE_CLASS_TEXT
                && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
                || (inputClass == InputType.TYPE_CLASS_NUMBER
                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    static boolean allowsLearning(int inputType, int imeOptions) {
        return !isPassword(inputType)
                && (imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0;
    }

    static boolean allowsVoice(int inputType, int imeOptions) {
        return allowsLearning(inputType, imeOptions);
    }
}
