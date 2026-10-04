package com.eltnegcellist.emma

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationNotificationPermissionPolicyTest {
    @Test
    fun asksOnlyForAnActualScreenOffConversationOnAndroid13Plus() {
        assertTrue(
            shouldRequestConversationNotificationPermission(
                sdkInt = 36,
                continueScreenOff = true,
                permissionGranted = false,
                startPromptHandled = false,
            ),
        )
        assertFalse(
            shouldRequestConversationNotificationPermission(
                sdkInt = 36,
                continueScreenOff = false,
                permissionGranted = false,
                startPromptHandled = false,
            ),
        )
        assertFalse(
            shouldRequestConversationNotificationPermission(
                sdkInt = 32,
                continueScreenOff = true,
                permissionGranted = false,
                startPromptHandled = false,
            ),
        )
        assertFalse(
            shouldRequestConversationNotificationPermission(
                sdkInt = 36,
                continueScreenOff = true,
                permissionGranted = true,
                startPromptHandled = false,
            ),
        )
        assertFalse(
            shouldRequestConversationNotificationPermission(
                sdkInt = 36,
                continueScreenOff = true,
                permissionGranted = false,
                startPromptHandled = true,
            ),
        )
    }
}
