// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import android.app.Activity;
import android.os.Build;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Keeps standalone activity controls clear of enforced edge-to-edge system bars. */
final class ActivityInsets {
    private ActivityInsets() {}

    static void apply(Activity activity) {
        if (Build.VERSION.SDK_INT < 35) {
            return;
        }
        View content = activity.findViewById(android.R.id.content);
        int left = content.getPaddingLeft();
        int top = content.getPaddingTop();
        int right = content.getPaddingRight();
        int bottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(left + safe.left, top + safe.top,
                    right + safe.right, bottom + safe.bottom);
            // This content owns the padding; descendants must not apply it a second time.
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
