package com.eltnegcellist.emma.ai

enum class AudienceMode(
    val label: String,
    val description: String,
) {
    BABY(
        label = "呼びかけ",
        description = "親子の今の場面を手がかりに、AIが短くやさしい英語を差し込みます。親と赤ちゃんのやり取りが主役です。",
    ),
    PARENT(
        label = "会話",
        description = "親の発話や直前の流れを踏まえ、AIも親・赤ちゃんとの3者のやり取りに継続して参加します。",
    );

    companion object {
        fun fromSaved(value: String?): AudienceMode =
            entries.firstOrNull { it.name == value } ?: BABY
    }
}
