package com.eltnegcellist.emma.ai

/**
 * Ordinary Lite conversation: three short, independently meaningful sentences
 * (6–12 words total, at most 4 words per sentence).
 *
 * The bank is hand-curated, not constrained by Gemma/Full. "Press to listen"
 * uses a separate phrase catalog with its own 1/3-sentence toggle.
 * Never add empty filler merely to reach three sentences.
 */
internal object LiteSpeechStyle {
    const val MIN_WORDS = 6
    const val MAX_WORDS = 12
    const val MIN_SENTENCES = 3
    const val MAX_SENTENCES = 3
    const val MAX_WORDS_PER_SENTENCE = 4

    val neutralClosers = listOf(
        "You are wonderful!",
        "You are loved.",
        "I'm right here.",
        "Hello, hello!",
        "Look with me.",
        "Listen with me.",
    )
}
