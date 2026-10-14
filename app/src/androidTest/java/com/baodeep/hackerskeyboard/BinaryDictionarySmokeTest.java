/*
 * SPDX-License-Identifier: Apache-2.0
 */

// Modified for Hacker's Keyboard v2; see the repository history for details.
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.system.Os;
import android.system.OsConstants;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BinaryDictionarySmokeTest {
    @Test
    public void bundledDictionaryLookupCrossesJniBoundary() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals(36, context.getApplicationInfo().targetSdkVersion);
        String expectedPageSize = InstrumentationRegistry.getArguments().getString("expectedPageSize");
        if (expectedPageSize != null) {
            assertEquals("Emulator must actually use the requested page size",
                    Long.parseLong(expectedPageSize), Os.sysconf(OsConstants._SC_PAGESIZE));
        }
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
