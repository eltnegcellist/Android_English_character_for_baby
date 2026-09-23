package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationEngineModeTest {
    @Test
    fun legacyLiteMigratesToStandard() {
        assertEquals(ConversationEngineMode.STANDARD, ConversationEngineMode.fromSaved("LITE"))
    }

    @Test
    fun newEditionValuesRoundTrip() {
        assertEquals(ConversationEngineMode.LITE, ConversationEngineMode.fromSaved("WEB_LITE"))
        assertEquals(ConversationEngineMode.STANDARD, ConversationEngineMode.fromSaved("STANDARD"))
        assertEquals(ConversationEngineMode.FULL, ConversationEngineMode.fromSaved("FULL"))
    }

    @Test
    fun missingValueDefaultsToStandard() {
        assertEquals(ConversationEngineMode.STANDARD, ConversationEngineMode.fromSaved(null))
    }
}
