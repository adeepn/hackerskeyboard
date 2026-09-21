// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.app.Instrumentation;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import androidx.core.app.NotificationManagerCompat;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;

/** Disposable test installs only. Modern permission and legacy app-op are distinct. */
final class NotificationTestSupport {
    private NotificationTestSupport() { }

    static void allow() throws IOException {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.getUiAutomation().grantRuntimePermission(
                    instrumentation.getTargetContext().getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
            assertEquals(true, NotificationManagerCompat.from(instrumentation.getTargetContext())
                    .areNotificationsEnabled());
            return;
        }
        setLegacyAppAllowed(true);
    }

    static void setLegacyAppAllowed(boolean allowed) throws IOException {
        if (Build.VERSION.SDK_INT >= 33) {
            throw new IllegalStateException("Legacy app-op is not a modern permission denial fixture");
        }
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String packageName = instrumentation.getTargetContext().getPackageName();
        assertEquals("com.baodeep.hackerskeyboard", packageName);
        try (ParcelFileDescriptor.AutoCloseInputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(
                        "appops set " + packageName + " POST_NOTIFICATION " + (allowed ? "allow" : "ignore")))) {
            byte[] buffer = new byte[1024];
            while (stream.read(buffer) != -1) { /* Wait for command completion. */ }
        }
        NotificationManagerCompat manager = NotificationManagerCompat.from(instrumentation.getTargetContext());
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (manager.areNotificationsEnabled() != allowed && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50);
        }
        assertEquals("Notification app-op did not change", allowed, manager.areNotificationsEnabled());
    }
}
