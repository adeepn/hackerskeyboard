/*
 * SPDX-License-Identifier: Apache-2.0
 */

// Modified for Hacker's Keyboard v2; see the repository history for details.
package io.github.baodeep.hackerskeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BinaryDictionarySmokeTest {
    @Test
    public void bundledDictionaryLookupCrossesJniBoundary() {
        Context context = InstrumentationRegistry.getTargetContext();
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
