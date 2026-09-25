package com.eltnegcellist.emma.ai

/**
 * Full BABY mode intentionally shares the same response-length envelope as Lite.
 *
 * Full still uses Gemma to choose the words dynamically, but it should not sound
 * more verbose than Lite when speaking to the baby. Referencing Lite constants
 * directly prevents the two modes from drifting apart again.
 */
internal object BabySpeechStyle {
    const val MIN_WORDS = LiteSpeechStyle.MIN_WORDS
    const val MAX_WORDS = LiteSpeechStyle.MAX_WORDS
    const val MIN_SENTENCES = LiteSpeechStyle.MIN_SENTENCES
    const val MAX_SENTENCES = LiteSpeechStyle.MAX_SENTENCES
    const val MAX_WORDS_PER_SENTENCE = LiteSpeechStyle.MAX_WORDS_PER_SENTENCE

    // Difficulty changes vocabulary/grammar, not response length in BABY mode.
    const val FIRST_WORDS_MAX_WORDS = LiteSpeechStyle.MAX_WORDS
    const val EASY_MAX_WORDS = LiteSpeechStyle.MAX_WORDS

    // Use the configured spoken name when it has not appeared in either of the
    // two most recent baby-directed replies.
    const val NAME_REPEAT_WINDOW = 2

    val neutralClosers = LiteSpeechStyle.neutralClosers
}
