package com.eltnegcellist.emma.ai

enum class ConversationEngineMode(
    val label: String,
    val description: String,
) {
    LITE(
        label = "Emma",
        description = "日常の育児場面に合わせて、あらかじめ用意した短い英語をKokoroの声で話します。",
    ),
    FULL(
        label = "Emma Full",
        description = "Gemma 4 E2Bが直前の会話まで理解し、その場で新しい英語を考えて返します。初回のみ2GB超のAIモデルを準備します。",
    );

    companion object {
        fun fromSaved(value: String?): ConversationEngineMode =
            entries.firstOrNull { it.name == value } ?: FULL
    }
}
