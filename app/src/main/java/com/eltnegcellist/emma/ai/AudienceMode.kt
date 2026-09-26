package com.eltnegcellist.emma.ai

enum class AudienceMode(
    val label: String,
    val description: String,
) {
    BABY(
        label = "赤ちゃんへ",
        description = "親の日本語を手がかりに、赤ちゃん本人へ短く楽しい英語で話します。",
    ),
    PARENT(
        label = "親へ",
        description = "親の日本語に、AIが英語で自然に会話参加します。",
    );

    companion object {
        fun fromSaved(value: String?): AudienceMode =
            entries.firstOrNull { it.name == value } ?: BABY
    }
}
