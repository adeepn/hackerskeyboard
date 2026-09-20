// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

final class NotificationAccess {
    static final String CHANNEL_ID = "PCKeyboard";

    private NotificationAccess() { }

    static boolean permissionGranted(Context context) {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean channelEnabled(Context context) {
        if (Build.VERSION.SDK_INT < 26) return true;
        NotificationChannel channel = context.getSystemService(NotificationManager.class)
                .getNotificationChannel(CHANNEL_ID);
        // A missing channel can be created after permission is granted.
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static boolean canPost(Context context) {
        return NotificationPermissionPolicy.canPost(Build.VERSION.SDK_INT, permissionGranted(context),
                NotificationManagerCompat.from(context).areNotificationsEnabled(), channelEnabled(context));
    }

    static NotificationPermissionPolicy.Action onEnable(Context context) {
        return NotificationPermissionPolicy.onEnable(Build.VERSION.SDK_INT,
                context.getApplicationInfo().targetSdkVersion, permissionGranted(context),
                NotificationManagerCompat.from(context).areNotificationsEnabled(), channelEnabled(context));
    }

    static Intent settingsIntent(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        }
        return appDetailsIntent(context);
    }

    static Intent appDetailsIntent(Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.getPackageName(), null));
    }
}
