package com.eltnegcellist.emma.ai

enum class ConversationEngineMode(
    val savedValue: String,
    val label: String,
    val description: String,
) {
    LITE(
        savedValue = "WEB_LITE",
        label = "Lite",
        description = "最も軽い構成です。Moonshineで日本語を聞き取り、Kitten TTS Nanoで話します。Web版Liteと同じ音声モデルを使います。",
    ),
    STANDARD(
        savedValue = "STANDARD",
        label = "Standard",
        description = "Android向けの標準構成です。ReazonSpeechとSupertonic 3を使い、認識精度と声の自然さを高めます。",
    ),
    FULL(
        savedValue = "FULL",
        label = "Full",
        description = "Gemmaが会話の流れに合わせて、その場で英語を考えて話します。初回のみ2GB超の追加データが必要です。",
    );

    companion object {
        fun fromSaved(value: String?): ConversationEngineMode = when (value) {
            "LITE" -> STANDARD
            LITE.savedValue -> LITE
            STANDARD.savedValue -> STANDARD
            FULL.savedValue -> FULL
            else -> STANDARD
        }
    }
}
