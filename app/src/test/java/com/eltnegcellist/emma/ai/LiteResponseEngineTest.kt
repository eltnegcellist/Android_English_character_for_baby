package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteResponseEngineTest {
    @Test
    fun bathSpeechSelectsBathScene() {
        val response = LiteResponseEngine().respond("そろそろお風呂に入ろうね")
        assertEquals("bath", response.scene)
        assertTrue(response.score >= 3)
        assertTrue(response.english.contains("bath", ignoreCase = true))
    }

    @Test
    fun milkSpeechSelectsMilkScene() {
        val response = LiteResponseEngine().respond("ミルクいっぱい飲んだね")
        assertEquals("milk", response.scene)
        assertTrue(response.english.contains("milk", ignoreCase = true))
    }

    @Test
    fun unknownSpeechFallsBackSafely() {
        val response = LiteResponseEngine().respond("今日はなんだか不思議だね")
        assertEquals("generic", response.scene)
        assertTrue(response.english.isNotBlank())
    }

    @Test
    fun configuredNameCanBeInserted() {
        val engine = LiteResponseEngine()
        val outputs = (0 until 8).map { engine.respond("お風呂の時間だよ", "Hana").english }
        assertTrue(outputs.any { it.contains("Hana") })
        assertFalse(outputs.any { it.contains("{name}") })
    }


    @Test
    fun repliesStayCompactAndBabyDirected() {
        val samples = listOf(
            "そろそろお風呂に入ろうね",
            "ミルクいっぱい飲んだね",
            "眠そうだね",
            "お散歩に行こうね",
            "絵本を読もうね",
        )
        val engine = LiteResponseEngine()

        samples.forEach { input ->
            val text = engine.respond(input, "Hana").english
            val sentences = Regex("(?<=[.!?])\\s+")
                .split(text.trim())
                .filter { it.isNotBlank() }
            val words = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
                .findAll(text)
                .count()

            assertTrue(
                "sentence count for: $text",
                sentences.size in LiteSpeechStyle.MIN_SENTENCES..LiteSpeechStyle.MAX_SENTENCES,
            )
            assertTrue(
                "word count for: $text",
                words in LiteSpeechStyle.MIN_WORDS..LiteSpeechStyle.MAX_WORDS,
            )
            sentences.forEach { sentence ->
                val sentenceWords = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
                    .findAll(sentence)
                    .count()
                assertTrue(
                    "sentence too long ($sentenceWords words): $sentence",
                    sentenceWords <= LiteSpeechStyle.MAX_WORDS_PER_SENTENCE,
                )
            }
        }
    }


    @Test
    fun everyBakedReplyMatchesCompactLiteEnvelope() {
        LiteResponseEngine.allTemplatesForValidation().forEach { template ->
            val text = template.replace("{name}", "Hana")
            val sentences = Regex("(?<=[.!?])\\s+")
                .split(text.trim())
                .filter { it.isNotBlank() }
            val words = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
                .findAll(text)
                .count()

            assertEquals("sentence count for: $text", LiteSpeechStyle.MIN_SENTENCES, sentences.size)
            assertTrue(
                "word count for: $text",
                words in LiteSpeechStyle.MIN_WORDS..LiteSpeechStyle.MAX_WORDS,
            )
            sentences.forEach { sentence ->
                val sentenceWords = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
                    .findAll(sentence)
                    .count()
                assertTrue(
                    "sentence too long ($sentenceWords words): $sentence",
                    sentenceWords <= LiteSpeechStyle.MAX_WORDS_PER_SENTENCE,
                )
            }
        }
    }

    @Test
    fun configuredNameIsReusedAtFullLikeCadence() {
        val engine = LiteResponseEngine()
        val outputs = (0 until 7).map {
            engine.respond("お風呂の時間だよ", "Hana").english
        }
        val nameTurns = outputs.mapIndexedNotNull { index, text ->
            index.takeIf { text.contains("Hana") }
        }

        assertTrue(nameTurns.isNotEmpty())
        assertEquals(0, nameTurns.first())
        nameTurns.zipWithNext().forEach { (previous, next) ->
            assertTrue("name repeated too soon", next - previous >= 3)
        }
    }

    @Test
    fun consecutiveCallsAvoidImmediateDuplicateWhenAlternativesExist() {
        val engine = LiteResponseEngine()
        val first = engine.respond("お散歩に行こうね").english
        val second = engine.respond("お散歩に行こうね").english
        assertFalse(first == second)
    }
}
