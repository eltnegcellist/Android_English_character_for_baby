package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteTopicGuideTest {
    @Test
    fun bundledExamplesPassSpeechGateAndMatchTheirDisplayedTopic() {
        assertEquals(LiteTopicGuide.topics.size, LiteTopicGuide.topics.map { it.scene }.toSet().size)
        val cases = LiteTopicContractFixtures.cases.associateBy { it.text }
        for (topic in LiteTopicGuide.topics) {
            for (example in topic.examples) {
                assertEquals("Guide must use shared expectations: $example", topic.scene, cases[example]?.scene)
                assertTrue("Speech gate: $example", MeaningfulJapaneseUtterance.isMeaningful(example))
                assertEquals("Guide example: $example", topic.scene, LiteResponseEngine().respond(example).scene)
            }
        }
    }
}
