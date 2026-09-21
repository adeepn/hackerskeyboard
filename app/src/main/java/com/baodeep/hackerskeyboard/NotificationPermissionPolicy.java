// SPDX-License-Identifier: Apache-2.0
package com.baodeep.hackerskeyboard;

/** Platform-independent decisions; never changes the user's desired preference. */
final class NotificationPermissionPolicy {
    enum Action { NONE, REQUEST_PERMISSION, OPEN_SETTINGS }

    private NotificationPermissionPolicy() { }

    static boolean canPost(int sdk, boolean granted, boolean appEnabled, boolean channelEnabled) {
        return (sdk < 33 || granted) && appEnabled && channelEnabled;
    }

    static Action onEnable(int sdk, int target, boolean granted,
            boolean appEnabled, boolean channelEnabled) {
        if (canPost(sdk, granted, appEnabled, channelEnabled)) return Action.NONE;
        if (sdk >= 33 && target >= 33 && !granted) return Action.REQUEST_PERMISSION;
        return Action.OPEN_SETTINGS;
    }
}
