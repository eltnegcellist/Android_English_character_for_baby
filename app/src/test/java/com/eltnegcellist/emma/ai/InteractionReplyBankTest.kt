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

    @Test fun pairedBabyNameIsRareLengthSafeAndPersistsAcrossRestarts() {
        val template = "Milk time, sweetheart! You're so lovely. I love you."
        assertEquals("Milk time, Hana! You're so lovely. I love you.",
            RuriPairedBabyName.insert(template, "Hana"))
        assertEquals("Milk time, Hana-chan! You're so lovely. I love you.",
            RuriPairedBabyName.insert(template, "Hana-chan"))
        assertEquals(template, RuriPairedBabyName.insert(template, "Hana".repeat(25)))
        assertEquals(template, RuriPairedBabyName.insert(template, ""))
        val untargeted = "Hello there, sweetheart! Your eyes look bright. I love you!"
        assertEquals(untargeted, RuriPairedBabyName.insert(untargeted, "Hana"))
        val cadence = RuriReplyDiversity()
        repeat(8) { cadence.recordName(used = false, paired = true) }
        assertTrue(cadence.canUsePairedName())
        cadence.recordName(used = true, paired = false)
        assertFalse(cadence.canUsePairedName())
        repeat(2) { cadence.recordName(used = false, paired = false) }
        assertTrue(cadence.canUsePairedName())
        cadence.recordName(used = true, paired = true)
        assertFalse(cadence.canUsePairedName())
        val saved = cadence.snapshot()
        assertTrue(saved.startsWith("v2\n"))
        val restored = RuriReplyDiversity()
        restored.restore(saved)
        assertFalse(restored.canUsePairedName())
        repeat(8) { restored.recordName(used = false, paired = true) }
        assertTrue(restored.canUsePairedName())
        val oldState = RuriReplyDiversity()
        oldState.restore("v1\ncombined:milk|affection#0\n\nmilk|affection=0~0")
        assertTrue(oldState.snapshot().contains("combined:milk|affection#0"))
        assertTrue(oldState.canUsePairedName())

        val word = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
        val parts = Regex("(?<=[.!?])\\s+")
        var matched = false
        for (key in RuriCompositeReplies.combinations.keys) {
            val topic = key.substringBefore('|')
            val interaction = key.substringAfter('|')
            val engine = LiteResponseEngine()
            // Six-field v2 state: blank lists and cycles, last name not recent
            // and a two-turn name-free gap.
            engine.restoreRuriReplyDiversity("v2\n\n\n\n8\n2")
            val response = engine.respondRuri("試験用", "Hana", axes(topic, interaction), true)
            if (!response.english.contains("Hana")) continue
            assertEquals("combined:$topic:$interaction", response.scene)
            val sentences = response.english.split(parts)
            assertEquals(3, sentences.size)
            assertTrue(sentences.all { word.findAll(it).count() <= 4 })
            assertTrue(word.findAll(response.english).count() <= 12)
            val engineReloaded = LiteResponseEngine()
            engineReloaded.restoreRuriReplyDiversity(engine.exportRuriReplyDiversity())
            val next = engineReloaded.respondRuri("次の日本語", "Hana", axes(topic, interaction), true)
            assertFalse("Name must not repeat on immediate next turn", next.english.contains("Hana"))
            matched = true
            break
        }
        assertTrue("at least one combined English set can safely insert baby's name", matched)
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
