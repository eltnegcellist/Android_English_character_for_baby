package com.eltnegcellist.emma.ai

internal object BabySpeechStyle {
    const val MIN_WORDS = 20
    const val MAX_WORDS = 32
    const val MIN_SENTENCES = 5
    const val MAX_SENTENCES = 7
    const val MAX_WORDS_PER_SENTENCE = 5

    const val FIRST_WORDS_MAX_WORDS = 24
    const val EASY_MAX_WORDS = 30

    // Match Full mode: use the configured spoken name when it has not appeared
    // in either of the two most recent baby-directed replies.
    const val NAME_REPEAT_WINDOW = 2

    // Generic fillers are intentionally short. Lite should sound like the Full
    // baby prompt: several tiny phrases, not one long explanatory sentence.
    val neutralClosers = listOf(
        "Look right here with me.",
        "Listen right here with me.",
        "Here we go together now.",
        "Nice and easy, little one.",
        "Emma is right here now.",
        "Hello, hello, little one, hello!",
        "Stay right here with me.",
    )
}
