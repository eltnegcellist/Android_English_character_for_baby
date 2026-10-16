package com.eltnegcellist.emma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyDisplayHintsTest {
    @Test
    fun combinedRouteKeepsTopicVisibleAndAddsInteractionContext() {
        val hint = ReplyDisplayHints.fromScene("combined:milk:affection")
        assertEquals("ミルク・授乳", hint.topicLabel)
        assertTrue(hint.japaneseNote.contains("ミルク"))
        assertTrue(hint.japaneseNote.contains("大好き"))
    }

    @Test
    fun interactionOnlyRouteShowsInteractionAsTopicFallback() {
        val hint = ReplyDisplayHints.fromScene("interaction:praise")
        assertEquals("ほめる", hint.topicLabel)
        assertTrue(hint.japaneseNote.contains("できた"))
    }

    @Test
    fun genericReplyDoesNotClaimAnUnknownTopic() {
        val hint = ReplyDisplayHints.fromScene("generic")
        assertEquals("ふだんのことば", hint.topicLabel)
        assertTrue(hint.japaneseNote.isNotBlank())
    }

    @Test
    fun unknownRouteFallsBackWithoutCrashing() {
        assertEquals("ふだんのことば", ReplyDisplayHints.fromScene("some_new_route").topicLabel)
    }
}
