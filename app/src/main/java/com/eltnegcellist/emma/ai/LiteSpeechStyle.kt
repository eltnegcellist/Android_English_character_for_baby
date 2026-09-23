package com.eltnegcellist.emma.ai

/**
 * Runtime Lite replies are intentionally shorter than Full.
 *
 * The reply bank is meant to be generated offline during development with the
 * same Gemma Baby-mode intent, then baked into the APK. Runtime Lite never
 * loads Gemma just to choose a reply.
 */
internal object LiteSpeechStyle {
    const val MIN_WORDS = 6
    const val MAX_WORDS = 12
    const val MIN_SENTENCES = 3
    const val MAX_SENTENCES = 3
    const val MAX_WORDS_PER_SENTENCE = 4

    val neutralClosers = listOf(
        "Here we go!",
        "Nice and easy.",
        "I'm right here.",
        "Hello, hello!",
        "Look with me.",
        "Listen with me.",
    )
}
