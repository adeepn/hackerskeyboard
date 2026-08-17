/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.baodeep.hackerskeyboard;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Value conversion shared by the AndroidX seek-bar preference and its dialog. */
final class SeekBarPreferenceValue {
    private static final Pattern FLOAT_RE = Pattern.compile("(\\d+\\.?\\d*).*");

    private final float mMin;
    private final float mMax;
    private final float mStep;
    private final boolean mAsPercent;
    private final boolean mLogScale;
    private final String mDisplayFormat;

    SeekBarPreferenceValue(float min, float max, float step, boolean asPercent,
            boolean logScale, String displayFormat) {
        mMin = min;
        mMax = max;
        mStep = step;
        mAsPercent = asPercent;
        mLogScale = logScale;
        mDisplayFormat = displayFormat;
    }

    static float parseLegacyString(String value) {
        if (value == null) {
            return 0.0f;
        }
        Matcher number = FLOAT_RE.matcher(value);
        if (!number.matches()) {
            return 0.0f;
        }
        return Float.valueOf(number.group(1));
    }

    String format(float value) {
        if (mAsPercent) {
            return String.format(Locale.getDefault(), "%d%%", (int) (value * 100));
        }
        if (mDisplayFormat != null) {
            return String.format(Locale.getDefault(), mDisplayFormat, value);
        }
        return Float.toString(value);
    }

    float valueForProgress(int progress) {
        if (mLogScale) {
            return roundForDisplay((float) Math.exp(valueForProgress(
                    progress, (float) Math.log(mMin), (float) Math.log(mMax), mStep)));
        }
        return valueForProgress(progress, mMin, mMax, mStep);
    }

    int progressForValue(float value) {
        if (mLogScale) {
            return percent((float) Math.log(value), (float) Math.log(mMin),
                    (float) Math.log(mMax));
        }
        return percent(value, mMin, mMax);
    }

    float getMin() {
        return mMin;
    }

    float getMax() {
        return mMax;
    }

    private static float valueForProgress(int progress, float min, float max, float step) {
        float delta = progress * (max - min) / 100;
        if (step != 0.0f) {
            delta = Math.round(delta / step) * step;
        }
        float value = min + delta;
        // Preserve the legacy two-significant-digit rounding used by the existing dialog.
        return roundForDisplay(value);
    }

    private static float roundForDisplay(float value) {
        return Float.valueOf(String.format(Locale.US, "%.2g", value));
    }

    private static int percent(float value, float min, float max) {
        return (int) (100 * (value - min) / (max - min));
    }
}
