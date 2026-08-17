/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.baodeep.hackerskeyboard;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

/** AndroidX dialog preference preserving the legacy float seek-bar behavior. */
public class SeekBarPreferenceCompat extends DialogPreference {
    private SeekBarPreferenceValue mValueModel;
    private float mValue;

    public SeekBarPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SeekBarPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setDialogLayoutResource(R.layout.seek_bar_dialog);
        TypedArray attributes = context.obtainStyledAttributes(
                attrs, R.styleable.SeekBarPreference);
        float min = attributes.getFloat(R.styleable.SeekBarPreference_minValue, 0.0f);
        float max = attributes.getFloat(R.styleable.SeekBarPreference_maxValue, 100.0f);
        float step = attributes.getFloat(R.styleable.SeekBarPreference_step, 0.0f);
        boolean asPercent = attributes.getBoolean(
                R.styleable.SeekBarPreference_asPercent, false);
        boolean logScale = attributes.getBoolean(
                R.styleable.SeekBarPreference_logScale, false);
        String displayFormat = attributes.getString(
                R.styleable.SeekBarPreference_displayFormat);
        attributes.recycle();
        mValueModel = new SeekBarPreferenceValue(
                min, max, step, asPercent, logScale, displayFormat);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray attributes, int index) {
        return attributes.getFloat(index, 0.0f);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        float fallback = defaultValue instanceof Float ? (Float) defaultValue : 0.0f;
        setValueFromStorage(getPersistedFloat(fallback));
    }

    @Override
    public CharSequence getSummary() {
        return mValueModel.format(mValue);
    }

    final float getValue() {
        return mValue;
    }

    final SeekBarPreferenceValue getValueModel() {
        return mValueModel;
    }

    final void previewValue(float value) {
        onPreviewValue(value);
    }

    final void persistDialogValue(float value) {
        mValue = value;
        persistValue(value);
        notifyChanged();
    }

    protected final void setValueFromStorage(float value) {
        mValue = value;
    }

    protected void onPreviewValue(float value) {
        // Optional preview hook for subclasses.
    }

    protected void persistValue(float value) {
        if (shouldPersist()) {
            persistFloat(value);
        }
    }
}
