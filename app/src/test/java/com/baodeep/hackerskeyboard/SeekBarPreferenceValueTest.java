/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SeekBarPreferenceValueTest {
    @Test
    public void parsesLegacyStringSuffixesWithoutChangingTheirMeaning() {
        assertEquals(40.0f, SeekBarPreferenceValue.parseLegacyString("40 ms"), 0.0f);
        assertEquals(0.25f, SeekBarPreferenceValue.parseLegacyString("0.25%"), 0.0f);
        assertEquals(1.0f, SeekBarPreferenceValue.parseLegacyString("1.0"), 0.0f);
        assertEquals(0.0f, SeekBarPreferenceValue.parseLegacyString("invalid"), 0.0f);
        assertEquals(0.0f, SeekBarPreferenceValue.parseLegacyString(null), 0.0f);
    }

    @Test
    public void preservesLinearStepAndPercentFormatting() {
        SeekBarPreferenceValue value = new SeekBarPreferenceValue(
                0.5f, 1.0f, 0.05f, true, false, null);

        assertEquals(0.8f, value.valueForProgress(57), 0.0f);
        assertEquals(60, value.progressForValue(0.8f));
        assertEquals("80%", value.format(0.8f));
    }

    @Test
    public void preservesLegacyLogarithmicRoundingAndDisplayFormat() {
        SeekBarPreferenceValue value = new SeekBarPreferenceValue(
                5.0f, 200.0f, 0.0f, false, true, "%.0f ms");

        assertEquals(33.0f, value.valueForProgress(50), 0.0f);
        assertEquals("33 ms", value.format(33.0f));
    }
}
