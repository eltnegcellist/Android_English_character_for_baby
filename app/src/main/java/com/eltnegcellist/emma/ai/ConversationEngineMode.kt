package com.eltnegcellist.emma.ai

enum class ConversationEngineMode(
    val savedValue: String,
    val label: String,
    val description: String,
) {
    LITE(
        savedValue = "LITE",
        label = "Lite",
        description = "軽くてすぐ使える構成です。Moonshineで日本語を聞き取り、LiteResponseEngineで返答を選び、Kitten TTS Nano / Kikiで話します。",
    ),
    FULL(
        savedValue = "FULL",
        label = "Full",
        description = "Liteと同じMoonshineとKittenを使いながら、Gemmaが文字起こし・元音声・会話履歴を受け取り、その場で英語を考えます。初回のみ2GB超のGemma追加データが必要です。",
    );

    companion object {
        fun fromSaved(value: String?): ConversationEngineMode = when (value) {
            LITE.savedValue,
            "WEB_LITE",
            "STANDARD",
            -> LITE
            FULL.savedValue -> FULL
            else -> LITE
        }
    }
}
