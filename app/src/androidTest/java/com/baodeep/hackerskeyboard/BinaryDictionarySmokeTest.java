/*
 * SPDX-License-Identifier: Apache-2.0
 */

// Modified for Hacker's Keyboard v2; see the repository history for details.
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BinaryDictionarySmokeTest {
    @Test
    public void bundledDictionaryLookupCrossesJniBoundary() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BinaryDictionary dictionary = new BinaryDictionary(
                context, new int[] {R.raw.main}, Suggest.DIC_MAIN);

        try {
            assertTrue(dictionary.getSize() > 0);
            assertTrue(dictionary.isValidWord("Android"));
            assertFalse(dictionary.isValidWord("Keyboard"));
        } finally {
            dictionary.close();
        }
    }
}
