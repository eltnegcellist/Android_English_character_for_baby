package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionReplyBankTest {
    private val examples = mapOf(
        "affection" to "かわいいね", "praise" to "よくできたね",
        "achievement" to "できたね", "effort" to "頑張ってるね",
        "encouragement" to "頑張ってね", "invitation" to "一緒にやろうか",
        "start" to "始めるよ", "finish" to "終わったね",
        "finish_check" to "終わったかな", "care" to "お疲れさま",
        "inquiry" to "どうしたの", "reassurance" to "大丈夫だよ",
        "repeat" to "もう一回", "pause" to "少し待ってね",
    )
    private fun guess(id: String) = SemanticTopicPrediction(
        topic = "generic", probability = 0.8, margin = 0.7,
        interaction = id, interactionProbability = 0.85, interactionMargin = 0.6,
    )

    @Test fun allInteractionLabelsAreGuardedAndHaveThreeMeaningfulSentences() {
        assertEquals(14, InteractionReplyBank.replies.size)
        assertEquals(112, InteractionReplyBank.replies.values.sumOf { it.size })
        for ((id, text) in examples) {
            assertEquals(id, InteractionReplyBank.choose(text, guess(id), false))
            assertNull(InteractionReplyBank.choose(text, guess(id), true))
            for (sentence in InteractionReplyBank.replies.getValue(id)) {
                val chunks = sentence.split(Regex("(?<=[.!?])\\s+"))
                assertEquals("three sentences " + id, 3, chunks.size)
                val words = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
                assertTrue("not empty " + id, words.findAll(sentence).count() in 6..12)
                assertTrue("short sentences " + id, chunks.all { words.findAll(it).count() <= 4 })
            }
        }
    }
    @Test fun negativesAndQuestionsCannotBecomeFalseSuccesses() {
        for ((text,id) in listOf(
            "まだ終わってないね" to "finish",
            "終わったかな" to "finish",
            "できなかったね" to "achievement",
            "大丈夫？" to "reassurance"
        )) assertNull(text, InteractionReplyBank.choose(text, guess(id), false))
    }
    @Test fun turnMemoryIsClearedByExplicitInteraction() {
        val engine=LiteResponseEngine()
        assertEquals("milk", engine.respond("ミルク飲もうね").scene)
        assertEquals("interaction:affection",
            engine.respond("かわいいね", semantic = guess("affection"), interactionEnabled = true).scene)
        assertEquals("generic", engine.respond("どうかな").scene)
    }
    @Test fun existingTopicsTakePriority() {
        val samples = mapOf("ミルク飲もうか" to "invitation", "おむつ終わったね" to "finish",
            "抱っこしようね" to "reassurance", "絵本読もうか" to "invitation")
        for ((text,id) in samples) {
            val response = LiteResponseEngine().respond(text, semantic=guess(id), interactionEnabled=true)
            assertFalse(text, response.scene.startsWith("interaction:"))
        }
    }
}
