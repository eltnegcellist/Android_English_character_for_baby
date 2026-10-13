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
    fun firstLaunchAfterHidingFullMigratesExistingUsersToLite() {
        assertEquals(ConversationEngineMode.LITE, ConversationEngineMode.forLaunch("FULL", false))
        assertEquals(ConversationEngineMode.LITE, ConversationEngineMode.forLaunch("LITE", false))
        assertEquals(ConversationEngineMode.LITE, ConversationEngineMode.forLaunch(null, false))
    }

    @Test
    fun developerFullSelectionPersistsAfterMigration() {
        assertEquals(ConversationEngineMode.FULL, ConversationEngineMode.forLaunch("FULL", true))
        assertEquals(ConversationEngineMode.LITE, ConversationEngineMode.forLaunch("LITE", true))
    }

    @Test
    fun missingValueDefaultsToLite() {
        assertEquals(ConversationEngineMode.LITE, ConversationEngineMode.fromSaved(null))
    }
}
