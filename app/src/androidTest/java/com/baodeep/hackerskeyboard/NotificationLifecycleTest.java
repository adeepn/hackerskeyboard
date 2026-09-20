// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Instrumentation;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Exercises the real receiver registration, notification posting and cancellation.
 * Does not start an IME session or assert SystemUI visibility/permission grants.
 */
@RunWith(AndroidJUnit4.class)
public class NotificationLifecycleTest {
    @Before
    public void allowNotificationsForDisposableTestInstall() throws Exception {
        NotificationTestSupport.allow();
    }

    private static class TestIme extends LatinIME {
        TestIme(Context context) {
            attachBaseContext(context);
        }
    }

    @Test
    public void repeatedEnableKeepsReceiverAndDisableCanBeRepeated() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Method setNotification = LatinIME.class.getDeclaredMethod("setNotification", boolean.class);
        setNotification.setAccessible(true);
        Field receiver = LatinIME.class.getDeclaredField("mNotificationReceiver");
        receiver.setAccessible(true);
        instrumentation.runOnMainSync(() -> {
            TestIme ime = new TestIme(instrumentation.getTargetContext());
            try {
                setNotification.invoke(ime, false);
                assertNull(receiver.get(ime));
                setNotification.invoke(ime, true);
                Object first = receiver.get(ime);
                assertNotNull(first);

                // Regression: the old else-if branch unregisters SHOW here.
                setNotification.invoke(ime, true);
                assertSame(first, receiver.get(ime));

                setNotification.invoke(ime, false);
                assertNull(receiver.get(ime));
                setNotification.invoke(ime, false);
                assertNull(receiver.get(ime));

                setNotification.invoke(ime, true);
                assertNotNull(receiver.get(ime));
                assertNotSame(first, receiver.get(ime));
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            } finally {
                try {
                    setNotification.invoke(ime, false);
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            }
        });
    }

    @Test
    public void androidBlockRemovesReceiverAndGrantAllowsEnableAgain() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Method setNotification = LatinIME.class.getDeclaredMethod("setNotification", boolean.class);
        setNotification.setAccessible(true);
        Field receiver = LatinIME.class.getDeclaredField("mNotificationReceiver");
        receiver.setAccessible(true);
        TestIme[] ime = new TestIme[1];
        instrumentation.runOnMainSync(() -> ime[0] = new TestIme(instrumentation.getTargetContext()));
        try {
            invokeAndCheck(instrumentation, setNotification, receiver, ime[0], true, true);
            NotificationTestSupport.setAppAllowed(false);
            invokeAndCheck(instrumentation, setNotification, receiver, ime[0], true, false);
            invokeAndCheck(instrumentation, setNotification, receiver, ime[0], true, false);
            NotificationTestSupport.setAppAllowed(true);
            invokeAndCheck(instrumentation, setNotification, receiver, ime[0], true, true);
        } finally {
            invokeAndCheck(instrumentation, setNotification, receiver, ime[0], false, false);
            NotificationTestSupport.setAppAllowed(true);
        }
    }

    private static void invokeAndCheck(Instrumentation instrumentation, Method method,
            Field receiver, TestIme ime, boolean desired, boolean registered) {
        instrumentation.runOnMainSync(() -> {
            try {
                method.invoke(ime, desired);
                if (registered) assertNotNull(receiver.get(ime));
                else assertNull(receiver.get(ime));
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    public void privateRefreshUsesPersistedChoiceNotBroadcastExtras() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String key = LatinIME.PREF_KEYBOARD_NOTIFICATION;
        boolean existed = prefs.contains(key);
        boolean previous = prefs.getBoolean(key, false);
        Field systemReceiver = LatinIME.class.getDeclaredField("mReceiver");
        systemReceiver.setAccessible(true);
        Field showReceiver = LatinIME.class.getDeclaredField("mNotificationReceiver");
        showReceiver.setAccessible(true);
        Method setNotification = LatinIME.class.getDeclaredMethod("setNotification", boolean.class);
        setNotification.setAccessible(true);
        TestIme[] ime = new TestIme[1];
        instrumentation.runOnMainSync(() -> ime[0] = new TestIme(context));
        BroadcastReceiver refreshReceiver = (BroadcastReceiver) systemReceiver.get(ime[0]);
        ContextCompat.registerReceiver(context, refreshReceiver,
                new IntentFilter(NotificationActions.ACTION_REFRESH), ContextCompat.RECEIVER_NOT_EXPORTED);
        try {
            prefs.edit().putBoolean(key, true).commit();
            context.sendBroadcast(NotificationActions.refreshIntent(context).putExtra(key, false));
            awaitReceiver(instrumentation, showReceiver, ime[0], true);
            prefs.edit().putBoolean(key, false).commit();
            context.sendBroadcast(NotificationActions.refreshIntent(context).putExtra(key, true));
            awaitReceiver(instrumentation, showReceiver, ime[0], false);
        } finally {
            context.unregisterReceiver(refreshReceiver);
            invokeAndCheck(instrumentation, setNotification, showReceiver, ime[0], false, false);
            SharedPreferences.Editor editor = prefs.edit();
            if (existed) editor.putBoolean(key, previous);
            else editor.remove(key);
            editor.commit();
        }
    }

    private static void awaitReceiver(Instrumentation instrumentation, Field receiver,
            TestIme ime, boolean registered) {
        boolean[] found = new boolean[1];
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            instrumentation.runOnMainSync(() -> {
                try {
                    found[0] = (receiver.get(ime) != null) == registered;
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            });
            if (found[0]) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("REFRESH did not apply persisted notification state");
    }
}
