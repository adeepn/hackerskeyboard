/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.baodeep.hackerskeyboard;

import android.content.Context;
import android.util.AttributeSet;

/** AndroidX vibration-duration preference with the legacy live preview. */
public class VibratePreferenceCompat extends SeekBarPreferenceStringCompat {
    public VibratePreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VibratePreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onPreviewValue(float value) {
        LatinIME ime = LatinIME.sInstance;
        if (ime != null) {
            ime.vibrate((int) value);
        }
    }
}
