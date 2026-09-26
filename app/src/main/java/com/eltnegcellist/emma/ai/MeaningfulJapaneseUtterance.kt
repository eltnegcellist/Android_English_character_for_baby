package com.eltnegcellist.emma.ai

/**
 * Lightweight gate for deciding whether an ASR transcript contains enough
 * linguistic/semantic content to deserve an AI response.
 *
 * It intentionally rejects isolated cooing/babbling-like vowel strings while
 * allowing short childcare words such as "ミルク" or "寝よう".
 */
internal object MeaningfulJapaneseUtterance {
    private val semanticShortForms = listOf(
        "ミルク", "みるく", "みのく", "母乳", "おっぱい", "授乳", "哺乳瓶",
        "お風呂", "風呂", "沐浴", "シャワー",
        "寝る", "寝よう", "ねんね", "おやすみ", "昼寝", "眠い",
        "起きた", "起きよう", "おはよう",
        "おむつ", "オムツ", "うんち", "おしっこ",
        "着替え", "抱っこ", "だっこ", "ぎゅー",
        "おてて", "あんよ", "笑った", "笑顔", "泣く", "泣いて",
        "げっぷ", "吐き戻", "お腹いっぱい",
        "遊ぼ", "おもちゃ", "散歩", "お散歩", "ベビーカー",
        "雨", "晴れ", "お日様", "ごはん", "離乳食", "絵本", "音楽", "歌お",
    ).map(::normalize)

    private val fillerOnly = setOf(
        "あ", "ああ", "あー", "あーー",
        "う", "うう", "うー", "うーー",
        "え", "ええ", "えー", "えーー",
        "お", "おお", "おー", "おーー",
        "ん", "んん", "んー", "んーー",
        "あう", "うあ", "あうあう", "うあうあ", "あーうー", "うーあー",
        "えっと", "えーと", "あの", "うん", "うんうん",
    ).map(::normalize).toSet()

    fun isMeaningful(raw: String): Boolean {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return false
        if (normalized == "不明" || normalized == "unclear") return false
        if (normalized in fillerOnly) return false

        // Short but semantically strong childcare words must remain responsive.
        if (semanticShortForms.any { it.isNotBlank() && normalized.contains(it) }) return true

        // Pure cooing/babbling-like vowel/nasal strings are not conversational turns.
        if (normalized.matches(Regex("^[あいうえおんぁぃぅぇぉー〜]+$"))) return false
        if (normalized.matches(Regex("^[アイウエオンァィゥェォー〜]+$"))) return false

        // A longer natural Japanese phrase is usually enough even when it is generic.
        if (normalized.length >= 4) return true

        // Very short content words written with kanji/katakana are often meaningful.
        val contentScriptChars = normalized.count {
            it.code in 0x30A1..0x30FA ||
                it.code in 0x3400..0x4DBF ||
                it.code in 0x4E00..0x9FFF
        }
        return normalized.length >= 2 && contentScriptChars >= 1
    }

    private fun normalize(text: String): String = text
        .lowercase()
        .replace("[不明]", "")
        .replace("[unclear]", "")
        .replace(Regex("[\\s、。！？!?,.・「」『』（）()【】\\[\\]]"), "")
        .trim()
}
