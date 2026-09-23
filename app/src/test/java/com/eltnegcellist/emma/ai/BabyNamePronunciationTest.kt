package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class BabyNamePronunciationTest {
    @Test
    fun hiraganaNameBecomesLatinSpokenName() {
        assertEquals("Hana", BabyNamePronunciation.toSpokenEnglish("はな"))
    }

    @Test
    fun katakanaNameBecomesLatinSpokenName() {
        assertEquals("Hana", BabyNamePronunciation.toSpokenEnglish("ハナ"))
    }

    @Test
    fun smallKanaAndSokuonAreHandled() {
        assertEquals("Kyouko", BabyNamePronunciation.toSpokenEnglish("きょうこ"))
        assertEquals("Kippei", BabyNamePronunciation.toSpokenEnglish("きっぺい"))
    }

    @Test
    fun latinNameIsPreserved() {
        assertEquals("Hana", BabyNamePronunciation.toSpokenEnglish("Hana"))
    }

    @Test
    fun explicitSpokenNameOverridesSavedName() {
        assertEquals("Hana", BabyNamePronunciation.toSpokenEnglish("花", "Hana"))
    }

    @Test
    fun kanjiRequiresExplicitPronunciation() {
        assertEquals("", BabyNamePronunciation.toSpokenEnglish("花"))
    }
    @Test
    fun spokenNameGetsChanForBabyAddress() {
        assertEquals("Hana-chan", BabyNamePronunciation.withChan("Hana"))
    }

    @Test
    fun chanSuffixIsNotDuplicated() {
        assertEquals("Hana-chan", BabyNamePronunciation.withChan("Hana-chan"))
    }

}
