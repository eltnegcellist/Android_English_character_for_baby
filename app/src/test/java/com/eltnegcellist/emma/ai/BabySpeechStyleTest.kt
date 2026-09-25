package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class BabySpeechStyleTest {
    @Test
    fun fullBabyLengthMatchesLite() {
        assertEquals(LiteSpeechStyle.MIN_WORDS, BabySpeechStyle.MIN_WORDS)
        assertEquals(LiteSpeechStyle.MAX_WORDS, BabySpeechStyle.MAX_WORDS)
        assertEquals(LiteSpeechStyle.MIN_SENTENCES, BabySpeechStyle.MIN_SENTENCES)
        assertEquals(LiteSpeechStyle.MAX_SENTENCES, BabySpeechStyle.MAX_SENTENCES)
        assertEquals(LiteSpeechStyle.MAX_WORDS_PER_SENTENCE, BabySpeechStyle.MAX_WORDS_PER_SENTENCE)
        assertEquals(LiteSpeechStyle.MAX_WORDS, BabySpeechStyle.FIRST_WORDS_MAX_WORDS)
        assertEquals(LiteSpeechStyle.MAX_WORDS, BabySpeechStyle.EASY_MAX_WORDS)
    }
}
