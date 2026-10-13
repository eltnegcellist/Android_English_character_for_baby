package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionFeatureSettingsTest {
    @Test fun unsetSettingIsEnabledByDefault() {
        assertTrue(InteractionFeatureSettings.DEFAULT_ENABLED)
        assertTrue(InteractionFeatureSettings.isEnabled { key, default ->
            assertEquals("interaction_experimental_enabled", key)
            default
        })
    }

    @Test fun savedExplicitOffRemainsOff() {
        assertFalse(InteractionFeatureSettings.isEnabled { _, _ -> false })
    }

    @Test fun savedExplicitOnRemainsOn() {
        assertTrue(InteractionFeatureSettings.isEnabled { _, _ -> true })
    }
}
