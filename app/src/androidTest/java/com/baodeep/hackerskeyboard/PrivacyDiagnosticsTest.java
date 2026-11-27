// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;

/** Exercises the actual service dump, using synthetic text only. */
@RunWith(AndroidJUnit4.class)
public class PrivacyDiagnosticsTest {
    @Test public void serviceDumpAllowsOnlyOperationalState() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            LatinIME ime = new LatinIME();
            try {
                Field composing = LatinIME.class.getDeclaredField("mComposing");
                composing.setAccessible(true);
                ((StringBuilder) composing.get(ime)).append("SYNTHETIC_PRIVATE_COMPOSING");
                StringWriter output = new StringWriter();
                ime.dump(null, new PrintWriter(output), new String[] {"SYNTHETIC_PRIVATE_ARG"});
                String dump = output.toString();
                assertTrue(dump.contains("LatinIME state :"));
                assertTrue(dump.contains("mCorrectionMode="));
                assertFalse(dump.contains("SYNTHETIC_PRIVATE"));
                assertFalse(dump.contains("mComposing"));
                assertFalse(dump.contains("mExtractedText"));
                assertFalse(dump.contains("mInputEditorInfo"));
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
    }
}
