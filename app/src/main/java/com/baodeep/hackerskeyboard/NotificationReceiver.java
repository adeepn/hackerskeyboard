// Modified for Hacker's Keyboard v2; see the repository history for details.
package com.baodeep.hackerskeyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;

public class NotificationReceiver extends BroadcastReceiver {
    static final String TAG = "PCKeyboard/Notification";
    static public final String ACTION_SHOW = "com.baodeep.hackerskeyboard.SHOW";

    private LatinIME mIME;

    NotificationReceiver(LatinIME ime) {
        super();
        mIME = ime;
        Log.i(TAG, "NotificationReceiver created, ime=" + mIME);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_SHOW.equals(intent.getAction())) {
            InputMethodManager imm = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInputFromInputMethod(mIME.mToken, InputMethodManager.SHOW_FORCED);
            }
        }
    }
}
