package com.eltnegcellist.emma.ai

import java.util.ArrayDeque
import kotlin.math.max

internal data class LiteResponse(
    val english: String,
    val scene: String,
    val score: Int,
)

internal class LiteResponseEngine {
    private data class Scene(
        val id: String,
        val keywords: List<String>,
        val replies: List<String>,
    )

    private val recentReplies = ArrayDeque<String>()
    private val recentOpeners = ArrayDeque<String>()
    private var turnCounter = 0
    private var turnsSinceName = BabySpeechStyle.NAME_REPEAT_WINDOW

    fun respond(transcript: String, spokenBabyName: String = ""): LiteResponse {
        val normalized = normalize(transcript)
        val ranked = scenes.map { scene -> scene to score(scene, normalized) }
            .sortedByDescending { it.second }
        val best = ranked.firstOrNull()
        val selected = if (best != null && best.second >= MIN_SCENE_SCORE) best else null

        val scene = selected?.first
        val score = selected?.second ?: 0
        val replies = scene?.replies ?: genericReplies
        val safeName = sanitizeName(spokenBabyName)
        val addressedName = BabyNamePronunciation.withChan(safeName)
        val forceName = addressedName.isNotBlank() &&
            turnsSinceName >= BabySpeechStyle.NAME_REPEAT_WINDOW
        val suppressName = addressedName.isNotBlank() &&
            turnsSinceName < BabySpeechStyle.NAME_REPEAT_WINDOW
        val raw = chooseReply(replies, normalized, forceName, suppressName)
        val named = applyName(raw, addressedName, forceName)
        val styled = alignToBabyStyle(named)

        remember(raw, styled)
        if (addressedName.isNotBlank() && containsName(styled, addressedName)) {
            turnsSinceName = 0
        } else {
            turnsSinceName++
        }
        turnCounter++
        return LiteResponse(
            english = styled,
            scene = scene?.id ?: "generic",
            score = score,
        )
    }

    private fun score(scene: Scene, transcript: String): Int {
        var total = 0
        for (keyword in scene.keywords) {
            val normalizedKeyword = normalize(keyword)
            if (normalizedKeyword.isNotEmpty() && transcript.contains(normalizedKeyword)) {
                total += max(2, normalizedKeyword.length)
                if (normalizedKeyword.length >= 4) total += 2
            }
        }
        return total
    }

    private fun chooseReply(
        replies: List<String>,
        transcript: String,
        forceName: Boolean,
        suppressName: Boolean,
    ): String {
        val eligible = when {
            forceName -> replies.filter { "{name}" in it }.ifEmpty { replies }
            suppressName -> replies.filterNot { "{name}" in it }.ifEmpty { replies }
            else -> replies
        }
        if (eligible.size == 1) return eligible.first()

        val start = ((transcript.hashCode().toLong() + turnCounter.toLong()).let {
            if (it < 0) -it else it
        } % eligible.size).toInt()

        for (offset in eligible.indices) {
            val candidate = eligible[(start + offset) % eligible.size]
            if (candidate !in recentReplies && openerKey(candidate) !in recentOpeners) {
                return candidate
            }
        }
        for (offset in eligible.indices) {
            val candidate = eligible[(start + offset) % eligible.size]
            if (candidate !in recentReplies) return candidate
        }
        return eligible[start]
    }

    private fun remember(template: String, reply: String) {
        recentReplies.addLast(template)
        while (recentReplies.size > RECENT_REPLY_WINDOW) recentReplies.removeFirst()

        recentOpeners.addLast(openerKey(reply))
        while (recentOpeners.size > RECENT_OPENER_WINDOW) recentOpeners.removeFirst()
    }

    private fun sanitizeName(spokenBabyName: String): String = spokenBabyName.trim()
        .filter { it.isLetter() || it == '\'' || it == '’' || it == '-' || it == ' ' }
        .take(40)

    private fun applyName(reply: String, safeName: String, forceName: Boolean): String {
        return when {
            "{name}" in reply && safeName.isBlank() ->
                reply.replace("{name}, ", "").replace("{name}", "little one")
            "{name}" in reply ->
                reply.replace("{name}", safeName)
            forceName && safeName.isNotBlank() ->
                "$safeName! $reply"
            else -> reply
        }
    }

    private fun alignToBabyStyle(reply: String): String {
        // Keep replies short and natural; do not add filler only to reach a word quota.
        val source = splitSentences(reply).take(BabySpeechStyle.MAX_SENTENCES)
        val selected = mutableListOf<String>()

        for (sentence in source) {
            val proposed = selected + sentence
            val proposedWords = wordCount(proposed.joinToString(" "))
            if (
                selected.size < BabySpeechStyle.MIN_SENTENCES ||
                proposedWords <= BabySpeechStyle.MAX_WORDS
            ) {
                selected += sentence
            } else {
                break
            }
        }

        while (
            wordCount(selected.joinToString(" ")) > BabySpeechStyle.MAX_WORDS &&
            selected.size > BabySpeechStyle.MIN_SENTENCES
        ) {
            selected.removeAt(selected.lastIndex)
        }

        return selected.joinToString(" ").trim()
    }

    private fun splitSentences(text: String): List<String> =
        Regex("(?<=[.!?])\\s+")
            .split(text.trim())
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun wordCount(text: String): Int =
        Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
            .findAll(text)
            .count()

    private fun openerKey(text: String): String =
        splitSentences(text)
            .firstOrNull()
            .orEmpty()
            .replace("{name}", "")
            .lowercase()
            .replace(Regex("[^a-z]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .take(3)
            .joinToString(" ")

    private fun containsName(text: String, safeName: String): Boolean =
        safeName.isNotBlank() && text.contains(safeName, ignoreCase = true)

    private fun positiveIndex(value: Int, size: Int): Int {
        if (size <= 0) return 0
        val mod = value % size
        return if (mod < 0) mod + size else mod
    }

    private fun normalize(text: String): String = text
        .lowercase()
        .replace(Regex("[\\s、。！？!?,.・「」『』（）()ー〜~]"), "")
        .replace("おふろ", "お風呂")
        .replace("お風呂", "風呂")

    companion object {
        private const val MIN_SCENE_SCORE = 3
        private const val RECENT_REPLY_WINDOW = 5
        private const val RECENT_OPENER_WINDOW = 3

        private val genericReplies = listOf(
            "Hi, little one! I'm right here. Hello, hello! Let's enjoy this moment together.",
            "Hello, little one! I hear you. Here we are! Nice and easy, one little moment at a time.",
            "Hi there! Emma is here. Hello, hello! Let's look, listen, and enjoy this little moment.",
            "Hey, little one! I'm with you. So nice! Here we go, nice and easy.",
            "Hello! I'm right here with you. Hi, hi! Let's have a sweet little moment together.",
        )

        private val scenes = listOf(
            Scene(
                "bath",
                listOf("お風呂", "風呂", "湯船", "お湯", "シャワー", "体洗", "あったかいお湯"),
                CanonicalEmmaPhrases.forScene("bath"),
            ),
            Scene(
                "milk",
                listOf("ミルク", "母乳", "おっぱい", "飲んだ", "飲もう", "飲めた", "哺乳瓶", "授乳"),
                CanonicalEmmaPhrases.forScene("milk"),
            ),
            Scene(
                "sleep",
                listOf("眠い", "眠そう", "ねんね", "寝よう", "寝る", "おやすみ", "昼寝", "眠く"),
                CanonicalEmmaPhrases.forScene("sleep"),
            ),
            Scene(
                "wake",
                listOf("起きた", "おはよう", "目覚め", "起きよう", "起きて", "朝だ"),
                CanonicalEmmaPhrases.forScene("wake"),
            ),
            Scene(
                "diaper",
                listOf("おむつ", "オムツ", "うんち", "おしっこ", "替えよう", "替える", "お尻"),
                CanonicalEmmaPhrases.forScene("diaper"),
            ),
            Scene(
                "clothes",
                listOf("着替え", "服着", "服脱", "お洋服", "パジャマ", "靴下", "帽子"),
                CanonicalEmmaPhrases.forScene("clothes"),
            ),
            Scene(
                "hug",
                listOf("抱っこ", "だっこ", "ぎゅ", "抱きしめ", "抱っこしよう", "腕の中"),
                CanonicalEmmaPhrases.forScene("hug"),
            ),
            Scene(
                "hands",
                listOf("手", "おてて", "握って", "にぎって", "ぎゅっと", "指", "つかん"),
                CanonicalEmmaPhrases.forScene("hands"),
            ),
            Scene(
                "feet",
                listOf("足", "あんよ", "キック", "蹴って", "つま先", "足バタ"),
                CanonicalEmmaPhrases.forScene("feet"),
            ),
            Scene(
                "smile",
                listOf("笑った", "笑って", "笑顔", "にこにこ", "ニコニコ", "微笑"),
                CanonicalEmmaPhrases.forScene("smile"),
            ),
            Scene(
                "cry",
                listOf("泣いて", "泣いた", "泣いちゃ", "涙", "えーん", "ぐず", "ぐずぐず"),
                CanonicalEmmaPhrases.forScene("cry"),
            ),
            Scene(
                "voice",
                listOf("声出", "あーって", "うーって", "おしゃべり", "喃語", "クーイング", "あうあう"),
                CanonicalEmmaPhrases.forScene("voice"),
            ),
            Scene(
                "tummy",
                listOf("お腹", "おなか", "げっぷ", "ゲップ", "お腹いっぱい", "満腹", "吐き戻"),
                CanonicalEmmaPhrases.forScene("tummy"),
            ),
            Scene(
                "play",
                listOf("遊ぼう", "遊ん", "おもちゃ", "ガラガラ", "ぬいぐるみ", "メリー", "ボール"),
                CanonicalEmmaPhrases.forScene("play"),
            ),
            Scene(
                "outside",
                listOf("散歩", "お散歩", "外行", "お外", "公園", "ベビーカー", "出かけ"),
                CanonicalEmmaPhrases.forScene("outside"),
            ),
            Scene(
                "rain",
                listOf("雨", "降ってる", "降ってきた", "雨音", "傘"),
                CanonicalEmmaPhrases.forScene("rain"),
            ),
            Scene(
                "sun",
                listOf("晴れ", "いい天気", "お日様", "太陽", "明るい", "ぽかぽか"),
                CanonicalEmmaPhrases.forScene("sun"),
            ),
            Scene(
                "food",
                listOf("ごはん", "離乳食", "食べよう", "食べた", "おいしい", "いただきます", "スプーン"),
                CanonicalEmmaPhrases.forScene("food"),
            ),
            Scene(
                "book",
                listOf("絵本", "本読", "読もう", "お話", "ページ", "めく"),
                CanonicalEmmaPhrases.forScene("book"),
            ),
            Scene(
                "music",
                listOf("歌", "音楽", "うた", "歌おう", "踊ろう", "リズム", "曲"),
                CanonicalEmmaPhrases.forScene("music"),
            ),
        )
    }
}
