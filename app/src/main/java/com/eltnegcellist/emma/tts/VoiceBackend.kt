package com.eltnegcellist.emma.tts

enum class VoiceBackend(
    val savedValue: String,
) {
    KOKORO("KOKORO"),
    ANDROID("ANDROID");

    companion object {
        fun fromSaved(value: String?): VoiceBackend =
            entries.firstOrNull { it.savedValue == value } ?: KOKORO
    }
}
