package com.eltnegcellist.emma.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeaningfulJapaneseUtteranceTest {
    @Test
    fun rejectsCooingAndFillerOnlyTurns() {
        listOf(
            "",
            "あー",
            "うー",
            "えーー",
            "んー",
            "あうあう",
            "うんうん",
            "えっと",
        ).forEach { input ->
            assertFalse("should reject: $input", MeaningfulJapaneseUtterance.isMeaningful(input))
        }
    }

    @Test
    fun allowsShortMeaningfulChildcareSpeech() {
        listOf(
            "ミルク",
            "寝よう",
            "抱っこ",
            "おむつ",
            "絵本",
            "雨",
            "かわいいね",
            "どうしたの",
        ).forEach { input ->
            assertTrue("should allow: $input", MeaningfulJapaneseUtterance.isMeaningful(input))
        }
    }
}
