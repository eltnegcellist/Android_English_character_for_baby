package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationEngineModeTest {
    @Test
    fun legacyModesMigrateToLite() {
        assertEquals(ConversationEngineMode.LITE, ConversationEngineMode.fromSaved("LITE"))
        assertEquals(ConversationEngineMode.LITE, ConversationEngineMode.fromSaved("WEB_LITE"))
        assertEquals(ConversationEngineMode.LITE, ConversationEngineMode.fromSaved("STANDARD"))
    }

    @Test
    fun fullRoundTrips() {
        assertEquals(ConversationEngineMode.FULL, ConversationEngineMode.fromSaved("FULL"))
    }

    @Test
    fun missingValueDefaultsToLite() {
        assertEquals(ConversationEngineMode.LITE, ConversationEngineMode.fromSaved(null))
    }
}
