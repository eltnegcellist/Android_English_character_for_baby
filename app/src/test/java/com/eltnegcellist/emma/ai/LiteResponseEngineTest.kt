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
    fun shortSleepWordsSelectSleepScene() {
        listOf(
            "寝る",
            "もう寝るよ",
            "寝たね",
            "寝かしつけよう",
            "睡眠の時間だよ",
            "お昼寝しよう",
            "寝ようか",
            "寝よっか",
            "ねようか",
            "ねよっか",
            "もう寝よ",
            "ねんねしよっか",
            "眠ろうか",
            "眠る時間だよ",
        ).forEach { input ->
            val response = LiteResponseEngine().respond(input)
            assertEquals("sleep scene for: $input", "sleep", response.scene)
            assertTrue("sleep score for: $input", response.score >= 3)
        }
    }

    @Test
    fun rollingOverIsNotMistakenForSleep() {
        val response = LiteResponseEngine().respond("寝返りしたね")
        assertFalse(response.scene == "sleep")
    }

    @Test
    fun naturalParentPhrasesSelectEveryScene() {
        val samples = listOf(
            "お風呂入ろっか" to "bath",
            "ミルク飲もっか" to "milk",
            "寝よっか" to "sleep",
            "そろそろ起きよっか" to "wake",
            "おむつ替えよっか" to "diaper",
            "着替えよっか" to "clothes",
            "抱っこする？" to "hug",
            "おてて握ってるね" to "hands",
            "あんよバタバタだね" to "feet",
            "にこって笑ったね" to "smile",
            "泣いてるね" to "cry",
            "いっぱいおしゃべりしてるね" to "voice",
            "げっぷ出たね" to "tummy",
            "一緒に遊ぼっか" to "play",
            "お散歩行こっか" to "outside",
            "雨だね" to "rain",
            "今日は晴れてるね" to "sun",
            "ごはん食べよっか" to "food",
            "絵本読もっか" to "book",
            "歌おっか" to "music",
        )

        samples.forEach { (input, expected) ->
            val response = LiteResponseEngine().respond(input)
            assertEquals("scene for: $input", expected, response.scene)
            assertTrue("score for: $input", response.score >= 3)
        }
    }

    @Test
    fun ambiguousJapaneseDoesNotTriggerWrongScene() {
        val forbidden = listOf(
            Triple("寝返りしたね", "sleep", "sleep"),
            Triple("手伝ってね", "hands", "hands"),
            Triple("足りないね", "feet", "feet"),
            Triple("風呂敷だね", "bath", "bath"),
            Triple("声優さんだね", "voice", "voice"),
            Triple("歌舞伎だね", "music", "music"),
        )

        forbidden.forEach { (input, forbiddenScene, _) ->
            val response = LiteResponseEngine().respond(input)
            assertFalse("$input should not be $forbiddenScene", response.scene == forbiddenScene)
        }

        val hunger = LiteResponseEngine().respond("お腹すいたね")
        assertEquals("food", hunger.scene)
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
