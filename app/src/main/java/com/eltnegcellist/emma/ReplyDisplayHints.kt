package com.eltnegcellist.emma

// Parent-facing one-line contextual notes, not verbatim English translations.
// Exact translations should eventually come from reviewed per-phrase assets.
internal data class ReplyDisplayHint(val topicLabel: String, val japaneseNote: String)

internal object ReplyDisplayHints {
    private val topics = mapOf(
        "bath" to ("お風呂" to "お風呂に入ろうね。"),
        "milk" to ("ミルク・授乳" to "ミルクの時間だよ。"),
        "sleep" to ("ねんね" to "ゆっくり休もうね。"),
        "wake" to ("おはよう" to "おはよう、起きたね。"),
        "diaper" to ("おむつ" to "おむつを替えようね。"),
        "clothes" to ("お着替え" to "お着替えしようね。"),
        "hug" to ("抱っこ" to "ぎゅっと抱っこしよう。"),
        "hands" to ("おてて" to "小さなおててだね。"),
        "feet" to ("あんよ" to "あんよを動かそうね。"),
        "smile" to ("笑顔" to "いい笑顔だね。"),
        "cry" to ("泣く・ぐずぐず" to "そばにいるよ。"),
        "voice" to ("おしゃべり" to "かわいい声だね。"),
        "tummy" to ("おなか・げっぷ" to "おなかのことだよ。"),
        "play" to ("あそぼう" to "一緒に遊ぼう。"),
        "outside" to ("おさんぽ" to "お外を見てみよう。"),
        "rain" to ("雨" to "雨の音を聞こうね。"),
        "sun" to ("おひさま" to "おひさまが出ているね。"),
        "food" to ("ごはん" to "ごはんの時間だよ。"),
        "book" to ("絵本" to "絵本を読もうね。"),
        "music" to ("音楽" to "歌を楽しもうね。"),
        "drink" to ("飲む" to "少しずつ飲もうね。"),
    )
    private val interactions = mapOf(
        "affection" to ("だいすき" to "大好きだよ。"),
        "praise" to ("ほめる" to "よくできたね。"),
        "achievement" to ("できた" to "できたね！"),
        "effort" to ("がんばる" to "がんばっているね。"),
        "encouragement" to ("応援" to "ゆっくりでいいよ。"),
        "invitation" to ("誘う" to "一緒にやってみよう。"),
        "start" to ("はじめる" to "さあ、始めよう。"),
        "finish" to ("おしまい" to "おしまいだよ。"),
        "finish_check" to ("おわった？" to "もう終わりかな？"),
        "care" to ("いたわる" to "少し休もうね。"),
        "inquiry" to ("たずねる" to "どうしたのかな？"),
        "reassurance" to ("安心" to "そばにいるよ。"),
        "repeat" to ("もう一度" to "もう一回やってみる？"),
    )

    fun fromScene(scene: String): ReplyDisplayHint {
        val route = scene.split(':')
        val topicId = when (route.firstOrNull()) {
            "combined" -> route.getOrNull(1)
            "interaction" -> null
            else -> route.firstOrNull()
        }
        val interactionId = when (route.firstOrNull()) {
            "combined" -> route.getOrNull(2)
            "interaction" -> route.getOrNull(1)
            else -> null
        }
        val topic = topics[topicId]
        val interaction = interactions[interactionId]
        val note = listOfNotNull(topic?.second, interaction?.second).joinToString(" ")
            .ifEmpty { "赤ちゃんへのやさしい呼びかけ。" }
        return ReplyDisplayHint(topic?.first ?: interaction?.first ?: "ふだんのことば", note)
    }
}
