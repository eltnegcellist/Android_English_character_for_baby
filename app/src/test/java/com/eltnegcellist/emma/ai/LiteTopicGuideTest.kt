package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteTopicGuideTest {
    @Test
    fun bundledExamplesPassSpeechGateAndMatchTheirDisplayedTopic() {
        assertEquals(21, LiteTopicGuide.topics.map { it.scene }.toSet().size)
        assertEquals(45, LiteTopicGuide.topics.sumOf { it.examples.size })
        for (topic in LiteTopicGuide.topics) {
            for (example in topic.examples) {
                assertTrue("Speech gate: $example", MeaningfulJapaneseUtterance.isMeaningful(example))
                assertEquals("Guide example: $example", topic.scene, LiteResponseEngine().respond(example).scene)
            }
        }
    }
}
