package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionReplyBankTest {
    private fun axes(topic: String, interaction: String, topicMargin: Double = 0.7, interactionProbability: Double = 0.9) =
        SemanticTopicPrediction(
            topic = topic, probability = 0.9, margin = topicMargin,
            interaction = interaction, interactionProbability = interactionProbability, interactionMargin = 0.5,
        )

    @Test fun allCombinedRepliesAreNaturalLengthAndThreeSentences() {
        assertEquals(135, RuriCompositeReplies.combinations.size)
        assertEquals(405, RuriCompositeReplies.combinations.values.sumOf { it.size })
        assertEquals(21, RuriCompositeReplies.combinations.keys.map { it.substringBefore('|') }.toSet().size)
        val seen=mutableSetOf<String>()
        val word = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
        val segments = Regex("(?<=[.!?])\\s+")
        for ((pair, phrases) in RuriCompositeReplies.combinations) {
            assertEquals(pair,3,phrases.size)
            val interaction=pair.substringAfter('|')
            assertTrue(pair, InteractionReplyBank.replies.containsKey(interaction))
            for (phrase in phrases) {
                val sentences=phrase.split(segments)
                assertEquals(pair+" "+phrase,3,sentences.size)
                assertTrue(pair+" "+phrase,word.findAll(phrase).count() in 6..12)
                assertTrue(pair+" "+phrase,sentences.all { word.findAll(it).count()<=4 })
                assertTrue("Duplicate: "+phrase,seen.add(phrase.lowercase()))
                if (interaction=="finish_check") assertTrue(pair,sentences.any { it.endsWith('?') })
            }
        }
    }

    @Test fun allFourRoutingCasesUseRuriAxesWithoutKeywordRules() {
        val engine=LiteResponseEngine()
        assertEquals("combined:milk:praise",
            engine.respondRuri("日本語に絵本と書いてもRuri判定がミルク", semantic=axes("milk","praise"), interactionEnabled=true).scene)
        assertEquals("combined:book:invitation",
            engine.respondRuri("日本語にミルクと書いてもRuri判定が絵本", semantic=axes("book","invitation"), interactionEnabled=true).scene)
        assertEquals("rain",
            engine.respondRuri("ミルクなんて書いてない", semantic=axes("rain","praise"), interactionEnabled=true).scene)
        assertEquals("interaction:affection",
            engine.respondRuri("単語とは無関係", semantic=axes("generic","affection"), interactionEnabled=true).scene)
        assertEquals("generic",
            engine.respondRuri("ミルクだよ", semantic=null, interactionEnabled=true).scene)
    }

    @Test fun newlyAddedMilkAffectionAndExistingMilkFinishCheckAreBothUsed() {
        val engine=LiteResponseEngine()
        val love=engine.respondRuri("ミルク飲んだね、かわいいね", semantic=axes("milk", "affection"), interactionEnabled=true)
        assertEquals("combined:milk:affection",love.scene)
        assertEquals("combined",love.routingReason)
        assertTrue(RuriCompositeReplies.forAxes("milk","affection")!!.contains(love.english))
        val finished=engine.respondRuri("ミルク飲み終わった？", semantic=axes("milk", "finish_check"), interactionEnabled=true)
        assertEquals("combined:milk:finish_check",finished.scene)
        val missing=engine.respondRuri("雨の話", semantic=axes("rain","praise"),interactionEnabled=true)
        assertEquals("rain",missing.scene)
        assertEquals("pair_missing",missing.routingReason)
    }

    @Test fun lowConfidenceAndNoneNeverInventPairings() {
        val engine=LiteResponseEngine()
        assertEquals("generic",
            engine.respondRuri("かわいいね",semantic=axes("milk","affection",0.01,0.1),interactionEnabled=true).scene)
        assertEquals("interaction:affection",
            engine.respondRuri("文字列は関係ない",semantic=axes("milk","affection",0.01,0.9),interactionEnabled=true).scene)
        assertEquals("milk",
            engine.respondRuri("かわいいね",semantic=axes("milk","none"),interactionEnabled=true).scene)
        assertEquals("milk",
            engine.respondRuri("かわいいね",semantic=axes("milk","affection"),interactionEnabled=false).scene)
        val rejected = engine.respondRuri("ミルク終わった？",
            semantic=axes("milk", "finish_check", interactionProbability=0.47), interactionEnabled=true)
        assertEquals("milk",rejected.scene)
        assertEquals("interaction_low_probability",rejected.routingReason)
    }

    @Test fun noPreviousTopicIsCarriedWhenNextRuriResultIsGeneric() {
        val engine=LiteResponseEngine()
        assertEquals("milk",engine.respondRuri("ミルク",semantic=axes("milk","none")).scene)
        assertEquals("interaction:affection",
            engine.respondRuri("かわいいね",semantic=axes("generic","affection"),interactionEnabled=true).scene)
        assertEquals("generic",engine.respondRuri("どうかな",semantic=null).scene)
    }

    @Test fun allFourteenInteractionLabelsHaveFallbackReplies() {
        assertEquals(14,InteractionReplyBank.replies.size)
        assertEquals(112,InteractionReplyBank.replies.values.sumOf { it.size })
        for ((label, phrases) in InteractionReplyBank.replies) {
            assertEquals(8,phrases.size)
            val r=LiteResponseEngine().respondRuri("分類はRuriの値のみ",semantic=axes("generic",label),interactionEnabled=true)
            assertEquals("interaction:"+label,r.scene)
            assertTrue(phrases.contains(r.english))
        }
    }
}
