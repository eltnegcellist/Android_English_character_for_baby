package com.eltnegcellist.emma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepetitionLabTest {
    @Test
    fun corpusHasBalancedEnglishAndJapaneseSamples() {
        val english = RepetitionLabCorpus.prompts.count { it.kind == RepetitionLabKind.ENGLISH_REPEAT }
        val japanese = RepetitionLabCorpus.prompts.count { it.kind == RepetitionLabKind.JAPANESE_CONTROL }

        assertEquals(16, english)
        assertEquals(16, japanese)
        assertEquals(32, RepetitionLabCorpus.prompts.size)
    }

    @Test
    fun corpusIdsAreUniqueAndPromptsAreNonBlank() {
        val prompts = RepetitionLabCorpus.prompts
        assertEquals(prompts.size, prompts.map { it.id }.toSet().size)
        assertTrue(prompts.all { it.id.isNotBlank() && it.text.isNotBlank() })
    }
}
