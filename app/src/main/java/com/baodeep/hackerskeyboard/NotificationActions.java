// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;

/** Notification entry points; no activity launch through a broadcast trampoline. */
final class NotificationActions {
    static final String ACTION_REFRESH = "com.baodeep.hackerskeyboard.REFRESH_NOTIFICATION";
    private NotificationActions() {
    }

    static Intent showIntent(Context context) {
        // A context-registered receiver has no manifest ComponentName. Restrict
        // dispatch to this package and keep the receiver private to our UID.
        return new Intent(NotificationReceiver.ACTION_SHOW).setPackage(context.getPackageName());
    }

    static Intent settingsIntent(Context context) {
        return new Intent(context, LatinIMESettings.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static Intent refreshIntent(Context context) {
        return new Intent(ACTION_REFRESH).setPackage(context.getPackageName());
    }

    static void refresh(Context context) {
        // No desired-state extras: the private receiver reads the persisted preference.
        context.sendBroadcast(refreshIntent(context));
    }

    static PendingIntent showKeyboard(Context context) {
        return PendingIntent.getBroadcast(context, 1, showIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openSettings(Context context) {
        return PendingIntent.getActivity(context, 2, settingsIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void registerShowReceiver(Context context, BroadcastReceiver receiver) {
        ContextCompat.registerReceiver(context, receiver,
                new IntentFilter(NotificationReceiver.ACTION_SHOW), ContextCompat.RECEIVER_NOT_EXPORTED);
    }
}
