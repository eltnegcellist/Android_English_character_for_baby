package com.eltnegcellist.emma.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KittenTextProcessorTest {
    @Test
    fun expandsCommonContractionsAndNumbers() {
        val normalized = KittenTextProcessor.normalize("Let's read 2 books.")
        assertEquals("let us read two books.", normalized)
    }

    @Test
    fun producesPaddedKittenTokenIds() {
        val ids = KittenTextProcessor.cleanPhonemes("hɛlˈoʊ ,")
        assertTrue(ids.size > 5)
        assertEquals(0L, ids.first())
        assertEquals(0L, ids.last())
        assertEquals(10L, ids[ids.lastIndex - 1])
    }
}
