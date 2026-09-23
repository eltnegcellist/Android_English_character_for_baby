package com.eltnegcellist.emma.ai

enum class ConversationEngineMode(
    val savedValue: String,
    val label: String,
    val description: String,
) {
    LITE(
        savedValue = "WEB_LITE",
        label = "Lite",
        description = "最も軽い構成です。Moonshineで日本語を聞き取り、LiteResponseEngineが返答を選び、Kitten TTS Nano / Kikiで話します。",
    ),
    FULL(
        savedValue = "FULL",
        label = "Full",
        description = "Moonshineの文字起こしと元音声の両方をGemmaに渡し、会話の流れに合わせてその場で英語を考えます。音声はLiteと同じKitten TTS Nano / Kikiです。",
    );

    companion object {
        fun fromSaved(value: String?): ConversationEngineMode = when (value) {
            LITE.savedValue, "LITE", "STANDARD" -> LITE
            FULL.savedValue -> FULL
            else -> LITE
        }
    }
}
