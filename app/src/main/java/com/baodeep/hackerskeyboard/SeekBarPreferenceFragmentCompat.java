/*
 * SPDX-License-Identifier: Apache-2.0
 */
package com.baodeep.hackerskeyboard;

import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/** Preference fragment base that displays Hacker's Keyboard seek-bar dialogs. */
public abstract class SeekBarPreferenceFragmentCompat extends PreferenceFragmentCompat {
    static final String DIALOG_TAG = "hackerskeyboard.seek_bar_dialog";

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (!(preference instanceof SeekBarPreferenceCompat)) {
            super.onDisplayPreferenceDialog(preference);
            return;
        }

        FragmentManager fragmentManager = getChildFragmentManager();
        if (fragmentManager.findFragmentByTag(DIALOG_TAG) != null) {
            return;
        }
        SeekBarPreferenceDialogFragmentCompat.newInstance(preference.getKey())
                .show(fragmentManager, DIALOG_TAG);
    }
}
