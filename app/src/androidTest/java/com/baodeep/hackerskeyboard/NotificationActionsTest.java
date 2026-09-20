// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class NotificationActionsTest {
    @Test
    public void showIsPackageScopedAndImmutableAndStillDelivered() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = NotificationActions.showIntent(context);
        assertEquals(context.getPackageName(), intent.getPackage());
        assertEquals(NotificationReceiver.ACTION_SHOW, intent.getAction());

        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<Intent> received = new AtomicReference<>();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent message) {
                received.set(message);
                delivered.countDown();
            }
        };
        NotificationActions.registerShowReceiver(context, receiver);
        PendingIntent pending = NotificationActions.showKeyboard(context);
        try {
            assertEquals(context.getPackageName(), pending.getCreatorPackage());
            assertFalse(pending.isActivity());
            assertEquals(pending, NotificationActions.showKeyboard(context));
            if (Build.VERSION.SDK_INT >= 31) {
                assertTrue(pending.isImmutable());
            }
            pending.send(context, 0, new Intent("unexpected.action").putExtra("injected", true));
            assertTrue("SHOW was not delivered", delivered.await(5, TimeUnit.SECONDS));
            assertEquals(NotificationReceiver.ACTION_SHOW, received.get().getAction());
            assertEquals(context.getPackageName(), received.get().getPackage());
            assertFalse("Immutable PendingIntent accepted fill-in data", received.get().hasExtra("injected"));
        } finally {
            pending.cancel();
            context.unregisterReceiver(receiver);
        }
    }

    @Test
    public void settingsOpensPrivateActivityDirectly() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Intent intent = NotificationActions.settingsIntent(context);
        assertEquals(new ComponentName(context, LatinIMESettings.class), intent.getComponent());
        assertFalse(context.getPackageManager().getActivityInfo(intent.getComponent(), 0).exported);

        // Keep our app foreground; this tests routing, not SystemUI's tap privileges.
        Activity main = instrumentation.startActivitySync(
                new Intent(context, Main.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                LatinIMESettings.class.getName(), null, false);
        PendingIntent pending = NotificationActions.openSettings(context);
        Activity settings = null;
        try {
            assertTrue(pending.isActivity());
            if (Build.VERSION.SDK_INT >= 31) {
                assertTrue(pending.isImmutable());
            }
            pending.send();
            settings = monitor.waitForActivityWithTimeout(5000);
            assertNotNull("Settings PendingIntent did not open the activity", settings);
            assertEquals(LatinIMESettings.class, settings.getClass());
        } finally {
            Activity openedSettings = settings;
            instrumentation.runOnMainSync(() -> {
                if (openedSettings != null) openedSettings.finish();
                main.finish();
            });
            instrumentation.removeMonitor(monitor);
            pending.cancel();
        }
    }

    @Test
    public void receiverIgnoresMissingAndUnrelatedActions() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        NotificationReceiver receiver = new NotificationReceiver(null);
        receiver.onReceive(context, null);
        receiver.onReceive(context, new Intent());
        receiver.onReceive(context, new Intent("unrelated.action"));
        receiver.onReceive(context, new Intent(context.getPackageName() + ".SETTINGS"));
    }
}
