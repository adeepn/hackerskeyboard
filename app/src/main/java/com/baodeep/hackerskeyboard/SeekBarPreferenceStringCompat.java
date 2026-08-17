/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.baodeep.hackerskeyboard;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

/** AndroidX seek-bar preference that preserves the legacy string storage type. */
public class SeekBarPreferenceStringCompat extends SeekBarPreferenceCompat {
    public SeekBarPreferenceStringCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SeekBarPreferenceStringCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray attributes, int index) {
        return SeekBarPreferenceValue.parseLegacyString(attributes.getString(index));
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        float fallback = defaultValue instanceof Float ? (Float) defaultValue : 0.0f;
        String persisted = getPersistedString(Float.toString(fallback));
        // Initializing the in-memory value must not rewrite an existing legacy string.
        setValueFromStorage(SeekBarPreferenceValue.parseLegacyString(persisted));
    }

    @Override
    protected void persistValue(float value) {
        if (shouldPersist()) {
            persistString(Float.toString(value));
        }
    }
}
