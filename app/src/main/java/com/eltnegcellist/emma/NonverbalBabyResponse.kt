package com.eltnegcellist.emma

/**
 * Conservative fallback used only when the audio turn contains no intelligible Japanese
 * but the endpoint detector still observed a voice-like sound. We deliberately avoid
 * claiming that the baby is crying, happy, hungry, etc. because the current ASR path does
 * not reliably classify those states yet.
 */
internal object NonverbalBabyResponse {
    private val replies = listOf(
        "Hi, little one! I hear your voice. Hello, hello! I'm right here.",
        "Oh, I hear you! Hi, little one. What a big voice! Hello there!",
        "Hello, little one! I hear you. Ahh, ahh! Your voice is here. Hi!",
        "Hi there! I hear your voice. Hello, little one! Talk, talk. I'm listening!",
    )

    fun next(seedMillis: Long = System.currentTimeMillis()): String =
        replies[((seedMillis / 1_000L) % replies.size).toInt()]
}
