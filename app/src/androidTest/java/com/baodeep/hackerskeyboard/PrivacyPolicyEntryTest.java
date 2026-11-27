/* SPDX-License-Identifier: Apache-2.0 */
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.ContextWrapper;
import android.content.IntentFilter;
import android.os.PatternMatcher;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class PrivacyPolicyEntryTest {
    @Test
    public void installedApkHasNoNetworkAdvertisingOrMicrophonePermissions()
            throws PackageManager.NameNotFoundException {
        android.content.Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        PackageInfo installed = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_PERMISSIONS);
        Set<String> permissions = installed.requestedPermissions == null
                ? Collections.<String>emptySet()
                : new HashSet<>(Arrays.asList(installed.requestedPermissions));
        for (String forbidden : new String[] {
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "com.google.android.gms.permission.AD_ID",
                "android.permission.RECORD_AUDIO",
                "android.permission.QUERY_ALL_PACKAGES"
        }) {
            assertFalse("Installed merged manifest requests " + forbidden,
                    permissions.contains(forbidden));
        }
    }

    @Test
    public void clickOpensOnlyPolicyUrlWithoutEditorExtras() {
        exerciseEntry(false);
    }

    @Test
    public void missingBrowserDoesNotCrashSetup() {
        exerciseEntry(true);
    }

    private void exerciseEntry(final boolean missingBrowser) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Intent[] captured = new Intent[1];
        IntentFilter filter = new IntentFilter(Intent.ACTION_VIEW);
        filter.addDataScheme("https");
        filter.addDataAuthority("baodeep.com", null);
        filter.addDataPath("/hkkpolicy", PatternMatcher.PATTERN_LITERAL);
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                filter, new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
        instrumentation.addMonitor(monitor);
        final Main activity = (Main) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), Main.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        try {
            instrumentation.waitForIdleSync();
            assertEquals("Opening setup must not open the browser", 0, monitor.getHits());
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    View entry = activity.findViewById(R.id.main_privacy_policy);
                    assertNotNull(entry);
                    assertEquals(View.VISIBLE, entry.getVisibility());
                    assertTrue(entry.isEnabled());
                    if (missingBrowser) {
                        activity.openPrivacyPolicy(new ContextWrapper(activity) {
                            @Override
                            public void startActivity(Intent intent) {
                                captured[0] = intent;
                                throw new ActivityNotFoundException(
                                        "Synthetic unavailable browser");
                            }
                        });
                    } else {
                        assertTrue(entry.performClick());
                    }
                }
            });
            instrumentation.waitForIdleSync();
            if (missingBrowser) {
                assertNotNull(captured[0]);
                assertEquals(Intent.ACTION_VIEW, captured[0].getAction());
                assertEquals("https://baodeep.com/hkkpolicy", captured[0].getDataString());
                assertNull(captured[0].getExtras());
                assertNull(captured[0].getClipData());
                assertNull(captured[0].getType());
            } else {
                assertEquals("Click must dispatch exactly the policy URL", 1, monitor.getHits());
            }
            assertTrue(!activity.isFinishing());
        } finally {
            instrumentation.removeMonitor(monitor);
            instrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    activity.finish();
                }
            });
            instrumentation.waitForIdleSync();
        }
    }
}
