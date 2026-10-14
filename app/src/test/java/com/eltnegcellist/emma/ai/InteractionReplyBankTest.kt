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

    @Test fun configuredBabyNameDoesNotAlterCombinedVoiceLine() {
        val normal = LiteResponseEngine()
        val configured = LiteResponseEngine()
        for (key in RuriCompositeReplies.combinations.keys.take(20)) {
            val topic = key.substringBefore('|')
            val interaction = key.substringAfter('|')
            val a = normal.respondRuri("試験用", "", axes(topic, interaction), true)
            val b = configured.respondRuri("試験用", "Hana", axes(topic, interaction), true)
            assertEquals(a.scene, b.scene)
            assertEquals("Name must not modify a recorded English line", a.english, b.english)
            assertFalse(b.english.contains("{name}"))
        }
        val state = configured.exportRuriReplyDiversity()
        assertTrue(state.startsWith("v3\\n"))
        val reloaded = RuriReplyDiversity()
        reloaded.restore(state)
        assertEquals(state, reloaded.snapshot())
        // Backward-compatibility for stored replies from releases with name cooldown.
        val old = RuriReplyDiversity()
        old.restore("v2\\ncombined:milk|affection#0\\n\\nmilk|affection=0~0\\n8\\n2")
        assertTrue(old.snapshot().contains("combined:milk|affection#0"))
    }

    @Test fun allCombinedRepliesAreNaturalLengthAndThreeSentences() {
        assertEquals(135, RuriCompositeReplies.combinations.size)
        assertEquals(495, RuriCompositeReplies.combinations.values.sumOf { it.size })
        assertEquals(21, RuriCompositeReplies.combinations.keys.map { it.substringBefore('|') }.toSet().size)
        val seen=mutableSetOf<String>()
        val word = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
        val segments = Regex("(?<=[.!?])\\s+")
        for ((pair, phrases) in RuriCompositeReplies.combinations) {
            assertEquals(pair,if (pair in RuriFrequentReplyVariants.combinations) 8 else 3,phrases.size)
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

    @Test fun curatedEnglishIsConcreteAndNoOldAwkwardSentencesRemain() {
        val all = InteractionReplyBank.replies.values.flatten() + RuriCompositeReplies.combinations.values.flatten()
        assertTrue(InteractionReplyBank.replies["affection"]!!.contains("Look at you! My sweet baby. I love you!"))
        assertTrue(InteractionReplyBank.replies["effort"]!!.contains("You're trying again! Look at you go! I'm here!"))
        assertTrue(RuriCompositeReplies.forAxes("milk","achievement")!!.contains(
            "You drank some milk! Good job, baby! I'm right here."
        ))
        assertTrue(RuriCompositeReplies.forAxes("milk","repeat")!!.contains(
            "Want more milk? Just a little? Take your time."
        ))
        assertTrue(RuriCompositeReplies.forAxes("book","inquiry")!!.contains(
            "Look at this picture! What do you see? Let's look again!"
        ))
        val phrases=Regex("that worked out|i see your effort|you can decide|absolutely adorable",RegexOption.IGNORE_CASE)
        assertTrue("Awkward phrases should have been removed",all.none { phrases.containsMatchIn(it) })
    }

    @Test fun latestInfantSpeechRefinementsMatchSimpleChildcareLanguage() {
        val all=RuriCompositeReplies.combinations.values.flatten()
        val expected=listOf(
            "You drank your milk! Little sips add up. Well done, baby!",
            "You took little sips! Look at you go! Good job, baby!",
            "Let's sit together now. Your bottle is ready. Want a little sip?",
            "Are you upset, baby? Come into my arms. I'll hold you.",
            "Look at those dimples! What a happy face. I love you!",
            "Let's take a break. Here's your little drink. Want a little sip?",
        )
        expected.forEach { assertTrue("Missing refined reply: $it", it in all) }
        val old=Regex("fancy a sip|you managed those sips|having big feelings|i treasure you|lovely drinking", RegexOption.IGNORE_CASE)
        assertTrue(all.none { old.containsMatchIn(it) })
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

    @Test fun repeatedPairedRuriTurnsCycleToSingleAxesAndPersistTheirHistory() {
        val engine = LiteResponseEngine()
        val candidates = mutableSetOf<String>()
        repeat(8) {
            val r=engine.respondRuri("ミルクかわいいね",semantic=axes("milk","affection"),interactionEnabled=true)
            assertEquals("combined:milk:affection",r.scene)
            assertEquals("combined",r.routingReason)
            assertTrue("repeated combination before exhaustion",candidates.add(r.english))
        }
        repeat(6) { i ->
            val r=engine.respondRuri("ミルクかわいいね",semantic=axes("milk","affection"),interactionEnabled=true)
            assertEquals(if (i % 2 == 0) "diversity_interaction" else "diversity_topic",r.routingReason)
            assertEquals(if (i % 2 == 0) "interaction:affection" else "milk",r.scene)
        }
        val next=engine.respondRuri("ミルクかわいいね",semantic=axes("milk","affection"),interactionEnabled=true)
        assertEquals("combined:milk:affection",next.scene)
        val restored=LiteResponseEngine()
        restored.restoreRuriReplyDiversity(engine.exportRuriReplyDiversity())
        val after=restored.respondRuri("ミルクかわいいね",semantic=axes("milk","affection"),interactionEnabled=true)
        assertEquals("combined:milk:affection",after.scene)
        assertFalse("must avoid most recently spoken phrase after restart", after.english == next.english)
        val baseline=LiteResponseEngine()
        val first=baseline.respondRuri("ミルクかわいいね",semantic=axes("milk","affection"),interactionEnabled=true)
        val resumed=LiteResponseEngine()
        resumed.restoreRuriReplyDiversity(baseline.exportRuriReplyDiversity())
        val second=resumed.respondRuri("ミルクかわいいね",semantic=axes("milk","affection"),interactionEnabled=true)
        assertFalse("persistent cycles should rotate eight pairs", first.english == second.english)
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
