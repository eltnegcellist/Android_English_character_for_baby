package com.eltnegcellist.emma.ai

import org.junit.Assert.*
import org.junit.Test

/** Both platforms consume the same independently specified expected results. */
class LiteWebTopicParityTest {
    @Test fun publishedWebFixturesAgree() {
        val cases = LiteTopicContractFixtures.cases.map { Triple(it.text, it.scene, it.meaningful) }
        for ((text, expectedScene, meaningful) in cases) {
            assertEquals("Scene: $text", expectedScene, LiteResponseEngine().respond(text).scene)
            assertEquals("Gate: $text", meaningful, MeaningfulJapaneseUtterance.isMeaningful(text))
            if (expectedScene !in listOf("generic", "drink")) {
                val context = LiteResponseEngine()
                context.respond("ミルクの時間だよ")
                assertEquals("Switch: $text", expectedScene, context.respond(text).scene)
                assertEquals("Stateless: $text", expectedScene, LiteResponseEngine().detectConcreteTopic(text)?.scene)
            }
        }
    }
    @Test fun sharedConversationSequencesAgree() {
        for (sequence in LiteTopicContractFixtures.sequences) {
            val engine = LiteResponseEngine()
            for ((index, step) in sequence.steps.withIndex()) {
                if (step.reset) {
                    engine.resetConversationContext()
                    continue
                }
                val text = requireNotNull(step.text)
                val label = "${sequence.id} turn ${index + 1}: $text"
                assertEquals("Gate: $label", step.meaningful, MeaningfulJapaneseUtterance.isMeaningful(text))
                assertEquals(label, step.scene, engine.respond(text).scene)
            }
        }
    }
    @Test fun drinkingContextAndOneTurnExpiry() {
        for (text in listOf("飲むかい", "飲もうか", "のむ？", "飲みたい？")) {
            val engine = LiteResponseEngine()
            assertEquals("drink", engine.respond(text).scene)
            for (i in 0 until 1) assertEquals("drink", engine.respond("いい感じですね").scene)
            assertEquals("generic", engine.respond("いい感じですね").scene)
            engine.respond("ミルクの時間だよ")
            assertEquals("milk", engine.respond(text).scene)
            val water = engine.respond("お水を飲むかい")
            assertEquals("drink", water.scene)
            assertFalse(water.english.contains("milk", ignoreCase = true))
            assertEquals("drink", engine.respond("ゆっくりでいいよ").scene)
            engine.resetConversationContext()
            assertEquals("generic", engine.respond("いい感じですね").scene)
        }
    }
}
