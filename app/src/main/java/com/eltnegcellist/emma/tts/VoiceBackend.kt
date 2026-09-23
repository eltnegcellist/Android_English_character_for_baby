package com.eltnegcellist.emma.tts

enum class VoiceBackend(
    val savedValue: String,
) {
    SUPERTONIC("SUPERTONIC"),
    ANDROID("ANDROID");

    companion object {
        fun fromSaved(value: String?): VoiceBackend =
            when (value) {
                "KOKORO" -> SUPERTONIC
                else -> entries.firstOrNull { it.savedValue == value } ?: SUPERTONIC
            }
    }
}
