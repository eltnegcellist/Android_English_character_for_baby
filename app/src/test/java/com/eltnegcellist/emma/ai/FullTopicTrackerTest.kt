package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullTopicTrackerTest {
    @Test
    fun genericTurnsAreOmittedButStillAgeTheTwoTurnWindow() {
        val tracker = FullTopicTracker()

        val first = tracker.observe("ミルク飲もうね")
        assertEquals("milk", first.currentExplicitTopic)
        assertEquals("milk", first.carriedTopic)
        assertTrue(first.recentConcreteTopics.isEmpty())

        listOf("どうかな", "いい感じだね").forEach { input ->
            val context = tracker.observe(input)
            assertNull("generic turn should not become an explicit topic: $input", context.currentExplicitTopic)
            assertEquals("milk", context.carriedTopic)
            assertEquals(listOf("milk"), context.recentConcreteTopics)
        }

        val expired = tracker.observe("かわいいね")
        assertNull(expired.currentExplicitTopic)
        assertNull(expired.carriedTopic)
        assertTrue(expired.recentConcreteTopics.isEmpty())
    }

    @Test
    fun explicitNewConcreteTopicOverridesOlderTopicImmediately() {
        val tracker = FullTopicTracker()

        tracker.observe("ミルク飲もうね")
        tracker.observe("どうかな")

        val bath = tracker.observe("お風呂入ろうね")
        assertEquals("bath", bath.currentExplicitTopic)
        assertEquals("bath", bath.carriedTopic)
        assertEquals(listOf("milk"), bath.recentConcreteTopics)

        val followUp = tracker.observe("気持ちいいね")
        assertNull(followUp.currentExplicitTopic)
        assertEquals("bath", followUp.carriedTopic)
        assertEquals(listOf("bath"), followUp.recentConcreteTopics)
    }
}
