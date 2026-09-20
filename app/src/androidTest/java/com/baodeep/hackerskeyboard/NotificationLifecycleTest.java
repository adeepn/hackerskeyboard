// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Instrumentation;
import android.content.Context;

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
}
