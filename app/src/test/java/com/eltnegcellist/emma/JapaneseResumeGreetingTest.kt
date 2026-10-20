package com.eltnegcellist.emma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseResumeGreetingTest {
    @Test fun choosesFromFixedJapaneseGreetingList() {
        assertEquals("おかえり！", JapaneseResumeGreeting.choose(nextIndex = { 0 }))
        assertEquals(3, JapaneseResumeGreeting.options.size)
    }

    @Test fun avoidsImmediateRepetition() {
        for (previous in JapaneseResumeGreeting.options) {
            val actual = JapaneseResumeGreeting.choose(previous, nextIndex = { it - 1 })
            assertNotEquals(previous, actual)
            assertTrue(JapaneseResumeGreeting.options.contains(actual))
        }
    }
}
