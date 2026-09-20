// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PackageVisibilityTest {
    @Test
    public void installedManifestPreservesBothDictionaryQueries() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        List<String> actions = new ArrayList<>();
        int queryDepth = -1;
        // This checks packaging on both API tiers, not target>=30 filtering.
        try (XmlResourceParser manifest = context.getAssets().openXmlResourceParser("AndroidManifest.xml")) {
            for (int event = manifest.getEventType(); event != XmlPullParser.END_DOCUMENT;
                    event = manifest.next()) {
                if (event == XmlPullParser.START_TAG && "queries".equals(manifest.getName())) {
                    queryDepth = manifest.getDepth();
                } else if (event == XmlPullParser.END_TAG && manifest.getDepth() == queryDepth) {
                    queryDepth = -1;
                } else if (event == XmlPullParser.START_TAG && queryDepth > 0
                        && "action".equals(manifest.getName())) {
                    actions.add(manifest.getAttributeValue(
                            "http://schemas.android.com/apk/res/android", "name"));
                }
            }
        }
        assertEquals(2, actions.size());
        assertEquals(new HashSet<>(Arrays.asList(PluginManager.HK_INTENT_DICT,
                "com.menny.android.anysoftkeyboard.DICTIONARY")), new HashSet<>(actions));
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) {
            assertFalse(Arrays.asList(info.requestedPermissions)
                    .contains("android.permission.QUERY_ALL_PACKAGES"));
        }
    }
}
