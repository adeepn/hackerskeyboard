/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.baodeep.hackerskeyboard;

import android.app.Dialog;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/** Lifecycle-safe dialog for {@link SeekBarPreferenceCompat}. */
public class SeekBarPreferenceDialogFragmentCompat extends DialogFragment
        implements DialogInterface.OnClickListener {
    private static final String ARG_KEY = "key";
    private static final String STATE_PENDING_VALUE = "pending_value";
    private static final String STATE_HAS_PENDING_VALUE = "has_pending_value";

    private float mPendingValue;
    private boolean mHasPendingValue;

    static SeekBarPreferenceDialogFragmentCompat newInstance(String key) {
        SeekBarPreferenceDialogFragmentCompat fragment =
                new SeekBarPreferenceDialogFragmentCompat();
        Bundle arguments = new Bundle(1);
        arguments.putString(ARG_KEY, key);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mHasPendingValue = savedInstanceState.getBoolean(STATE_HAS_PENDING_VALUE);
            mPendingValue = savedInstanceState.getFloat(STATE_PENDING_VALUE);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        SeekBarPreferenceCompat preference = requireSeekBarPreference();
        if (!mHasPendingValue) {
            mPendingValue = preference.getValue();
            mHasPendingValue = true;
        }

        View content = LayoutInflater.from(requireContext())
                .inflate(preference.getDialogLayoutResource(), null);
        bindSeekBar(content, preference);

        CharSequence positiveText = preference.getPositiveButtonText();
        CharSequence negativeText = preference.getNegativeButtonText();
        if (TextUtils.isEmpty(positiveText)) {
            positiveText = getText(android.R.string.ok);
        }
        if (TextUtils.isEmpty(negativeText)) {
            negativeText = getText(android.R.string.cancel);
        }

        return new AlertDialog.Builder(requireContext())
                .setTitle(preference.getDialogTitle())
                .setView(content)
                .setPositiveButton(positiveText, this)
                .setNegativeButton(negativeText, this)
                .create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_HAS_PENDING_VALUE, mHasPendingValue);
        outState.putFloat(STATE_PENDING_VALUE, mPendingValue);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            requireSeekBarPreference().persistDialogValue(mPendingValue);
        }
    }

    private void bindSeekBar(View content, final SeekBarPreferenceCompat preference) {
        final SeekBarPreferenceValue valueModel = preference.getValueModel();
        final TextView valueText = (TextView) content.findViewById(R.id.seekVal);
        TextView minText = (TextView) content.findViewById(R.id.seekMin);
        TextView maxText = (TextView) content.findViewById(R.id.seekMax);
        final SeekBar seekBar = (SeekBar) content.findViewById(R.id.seekBarPref);

        valueText.setText(valueModel.format(mPendingValue));
        minText.setText(valueModel.format(valueModel.getMin()));
        maxText.setText(valueModel.format(valueModel.getMax()));
        seekBar.setProgress(valueModel.progressForValue(mPendingValue));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar bar) {}

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    mPendingValue = valueModel.valueForProgress(progress);
                    preference.previewValue(mPendingValue);
                    bar.setProgress(valueModel.progressForValue(mPendingValue));
                }
                valueText.setText(valueModel.format(mPendingValue));
            }
        });
    }

    private SeekBarPreferenceCompat requireSeekBarPreference() {
        Fragment parent = getParentFragment();
        if (!(parent instanceof PreferenceFragmentCompat)) {
            throw new IllegalStateException(
                    "Seek-bar dialog must be a child of PreferenceFragmentCompat");
        }
        String key = requireArguments().getString(ARG_KEY);
        Preference preference = ((PreferenceFragmentCompat) parent).findPreference(key);
        if (!(preference instanceof SeekBarPreferenceCompat)) {
            throw new IllegalStateException("Missing seek-bar preference for key " + key);
        }
        return (SeekBarPreferenceCompat) preference;
    }
}
