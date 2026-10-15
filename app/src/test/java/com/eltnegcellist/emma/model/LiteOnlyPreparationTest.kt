package com.eltnegcellist.emma.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LiteOnlyPreparationTest {
    @Test fun noFullOrGemmaPreparation() {
        assertEquals(
            setOf("LITE", "ASR", "KITTEN", "SEMANTIC"),
            ModelPreparationKind.entries.map { it.name }.toSet()
        )
    }
}
