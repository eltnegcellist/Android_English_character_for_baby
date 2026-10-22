package com.eltnegcellist.emma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishResumeGreetingTest {
    @Test fun choosesOneEnglishSentence() {
        val choices = EnglishResumeGreeting.recordedCandidates
        assertEquals(4, choices.size)
        assertEquals("Hi there!", EnglishResumeGreeting.pick(choices, nextIndex = { 0 }))
        assertEquals("Hello, hello!", EnglishResumeGreeting.pick(choices, nextIndex = { it - 1 }))
        assertTrue(choices.all { it.all { ch -> ch.code < 128 } })
    }

    @Test fun neverRepeatsPreviousGreeting() {
        val choices = EnglishResumeGreeting.recordedCandidates
        for (previous in choices) {
            val selected = EnglishResumeGreeting.pick(choices, previous, nextIndex = { it - 1 })
            assertNotEquals(previous, selected)
            assertTrue(choices.contains(selected))
        }
    }

    @Test fun returnsNullWhenNoExistingRecordingIsEligible() {
        assertNull(EnglishResumeGreeting.pick(emptyList()))
    }
}
