package com.eltnegcellist.emma.ai

enum class ConversationEngineMode(
    val label: String,
    val description: String,
) {
    LITE(
        label = "標準",
        description = "赤ちゃん向けの短い英語を、今の場面に合わせて端末内で選んで話します。普段はこちらがおすすめです。",
    ),
    FULL(
        label = "Full",
        description = "より自由に、あなたの話や直前の会話に合わせてAIがその場で英語を考えて話します。初回のみ2GB超の追加データが必要です。",
    );

    companion object {
        fun fromSaved(value: String?): ConversationEngineMode =
            entries.firstOrNull { it.name == value } ?: FULL
    }
}
