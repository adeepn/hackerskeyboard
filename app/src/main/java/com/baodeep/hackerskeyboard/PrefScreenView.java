/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

// Modified for Hacker's Keyboard v2; see the repository history for details.
package com.baodeep.hackerskeyboard;

import android.app.backup.BackupManager;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;

public class PrefScreenView extends FragmentActivity {
    static final String FRAGMENT_TAG = "view_preferences";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new ViewPreferenceFragment(), FRAGMENT_TAG)
                    .commitNow();
        }
    }

    public static class ViewPreferenceFragment extends SeekBarPreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private SharedPreferences mPreferences;
        private ListPreference mRenderModePreference;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.prefs_view, rootKey);
            mPreferences = getPreferenceManager().getSharedPreferences();
            mRenderModePreference = findPreference(LatinIME.PREF_RENDER_MODE);
        }

        @Override
        public void onStart() {
            super.onStart();
            mPreferences.registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onStop() {
            mPreferences.unregisterOnSharedPreferenceChangeListener(this);
            super.onStop();
        }

        @Override
        public void onResume() {
            super.onResume();
            if (LatinKeyboardBaseView.sSetRenderMode == null) {
                mRenderModePreference.setEnabled(false);
                mRenderModePreference.setSummary(R.string.render_mode_unavailable);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            (new BackupManager(requireContext())).dataChanged();
        }
    }
}
