// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import android.os.Build;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Reserves space below the key rows in an edge-to-edge IME window. */
final class ImeNavigationInsets {
    private ImeNavigationInsets() {}

    static void apply(View inputView) {
        if (Build.VERSION.SDK_INT < 35) {
            return;
        }
        int left = inputView.getPaddingLeft();
        int top = inputView.getPaddingTop();
        int right = inputView.getPaddingRight();
        int bottom = inputView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(inputView, (view, insets) -> {
            // The IME window already fits the other sides. Its own IME inset describes
            // the editor's occlusion and must never be added back to the key surface.
            int navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(left, top, right, bottom + navigationBottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(inputView);
    }
}
