// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotificationPermissionPolicyTest {
    @Test
    public void permissionDenialOnlyBlocksPostingOnAndroid13AndLater() {
        assertTrue(NotificationPermissionPolicy.canPost(24, false, true, true));
        assertTrue(NotificationPermissionPolicy.canPost(32, false, true, true));
        assertFalse(NotificationPermissionPolicy.canPost(33, false, true, true));
        assertFalse(NotificationPermissionPolicy.canPost(37, false, true, true));
    }

    @Test
    public void allPlatformAndTargetCombinationsRespectAppAndChannelBlocks() {
        for (int sdk : new int[] {24, 26, 32, 33, 37}) {
            for (int target : new int[] {26, 32, 33, 36, 37}) {
                assertFalse(NotificationPermissionPolicy.canPost(sdk, true, false, true));
                assertFalse(NotificationPermissionPolicy.canPost(sdk, true, true, false));
                assertEquals(NotificationPermissionPolicy.Action.OPEN_SETTINGS,
                        NotificationPermissionPolicy.onEnable(sdk, target, true, false, true));
                assertEquals(NotificationPermissionPolicy.Action.OPEN_SETTINGS,
                        NotificationPermissionPolicy.onEnable(sdk, target, true, true, false));
                assertEquals(NotificationPermissionPolicy.Action.NONE,
                        NotificationPermissionPolicy.onEnable(sdk, target, true, true, true));
            }
        }
    }

    @Test
    public void onlyModernTargetsCanControlTheRuntimePrompt() {
        assertEquals(NotificationPermissionPolicy.Action.REQUEST_PERMISSION,
                NotificationPermissionPolicy.onEnable(33, 33, false, false, true));
        assertEquals(NotificationPermissionPolicy.Action.REQUEST_PERMISSION,
                NotificationPermissionPolicy.onEnable(37, 36, false, false, false));
        assertEquals(NotificationPermissionPolicy.Action.OPEN_SETTINGS,
                NotificationPermissionPolicy.onEnable(37, 26, false, false, true));
        assertEquals(NotificationPermissionPolicy.Action.OPEN_SETTINGS,
                NotificationPermissionPolicy.onEnable(33, 32, false, false, true));
        assertEquals(NotificationPermissionPolicy.Action.NONE,
                NotificationPermissionPolicy.onEnable(24, 36, false, true, true));
    }
}
