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
        val forceName = safeName.isNotBlank() &&
            turnsSinceName >= BabySpeechStyle.NAME_REPEAT_WINDOW
        val suppressName = safeName.isNotBlank() &&
            turnsSinceName < BabySpeechStyle.NAME_REPEAT_WINDOW
        val raw = chooseReply(replies, normalized, forceName, suppressName)
        val named = applyName(raw, safeName, forceName)
        val styled = alignToBabyStyle(named, normalized, scene?.id)

        remember(raw, styled)
        if (safeName.isNotBlank() && containsName(styled, safeName)) {
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

    private fun alignToBabyStyle(reply: String, transcript: String, sceneId: String?): String {
        val sentences = splitSentences(reply).toMutableList()
        val closers = (LiteSpeechStyle.neutralClosers + sceneFillers[sceneId].orEmpty()).distinct()
        var offset = positiveIndex(transcript.hashCode() + turnCounter, closers.size)

        while (sentences.size > LiteSpeechStyle.MAX_SENTENCES) {
            sentences.removeAt(sentences.lastIndex)
        }

        while (
            (sentences.size < LiteSpeechStyle.MIN_SENTENCES ||
                wordCount(sentences.joinToString(" ")) < LiteSpeechStyle.MIN_WORDS) &&
            sentences.size < LiteSpeechStyle.MAX_SENTENCES
        ) {
            val candidate = closers[offset % closers.size]
            offset++
            val proposed = sentences + candidate
            if (wordCount(proposed.joinToString(" ")) <= LiteSpeechStyle.MAX_WORDS) {
                sentences += candidate
            } else {
                break
            }
        }

        while (
            wordCount(sentences.joinToString(" ")) > LiteSpeechStyle.MAX_WORDS &&
            sentences.size > LiteSpeechStyle.MIN_SENTENCES
        ) {
            sentences.removeAt(sentences.lastIndex)
        }

        return sentences.joinToString(" ").trim()
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

        /**
         * Static runtime bank. The production direction is to generate/refresh
         * these candidates offline with Gemma using the Full Baby-mode intent,
         * validate them, then bake only the resulting English strings into Lite.
         */
        private val genericReplies = listOf(
            "Hello, little one! Emma is here. Hello, hello!",
            "Hi there, little one! I'm right here. Hello, hello!",
            "Hello, hello! Emma is here. Look with me.",
            "Hi, little one! I'm right here. Nice and easy.",
            "Hello there! Stay with me. Here we go!",
        )

        private val sceneFillers = mapOf(
            "bath" to listOf("Splash, splash!", "Here we go!"),
            "milk" to listOf("Sip, sip!", "Nice and slow."),
            "sleep" to listOf("Night-night.", "Rest, rest."),
            "wake" to listOf("Hello, hello!", "Good morning!"),
            "diaper" to listOf("Here we go!", "Nice and easy."),
            "clothes" to listOf("Here we go!", "All ready."),
            "hug" to listOf("Big cuddle!", "Nice and close."),
            "hands" to listOf("Squeeze, squeeze!", "Wiggle, wiggle!"),
            "feet" to listOf("Kick, kick!", "Wiggle, wiggle!"),
            "smile" to listOf("Smile, smile!", "Hello, hello!"),
            "cry" to listOf("I'm right here.", "Nice and gentle."),
            "voice" to listOf("Ooh, ahh!", "I'm listening."),
            "tummy" to listOf("Nice and easy.", "Take your time."),
            "play" to listOf("Look, look!", "Here we go!"),
            "outside" to listOf("Look around!", "Here we go!"),
            "rain" to listOf("Pitter-patter!", "Listen, listen!"),
            "sun" to listOf("Bright, bright!", "Look, look!"),
            "food" to listOf("Yum, yum!", "Nice and slow."),
            "book" to listOf("Look, look!", "Turn the page."),
            "music" to listOf("La-la-la!", "Listen, listen!"),
        )

        private val scenes = listOf(
            Scene(
                "bath",
                listOf("お風呂", "風呂", "湯船", "お湯", "シャワー", "体洗", "あったかいお湯"),
                listOf(
                    "Bath time! Splash, splash! Here we go!",
                    "Warm bath! Splash, splash! Nice and easy.",
                    "Bath time! Wash, wash! All clean.",
                    "Here we go! Bath time! Splash, splash!",
                    "{name}, bath time! Splash, splash! Here we go!",
                ),
            ),
            Scene(
                "milk",
                listOf("ミルク", "母乳", "おっぱい", "飲んだ", "飲もう", "飲めた", "哺乳瓶", "授乳"),
                listOf(
                    "Milk time! Sip, sip! Nice and slow.",
                    "Yummy milk! Sip, sip! Mmm, yummy!",
                    "Milk, milk! Little sips. Nice and easy.",
                    "Time for milk! Sip, sip! All done.",
                    "{name}, milk time! Sip, sip! Nice and slow.",
                ),
            ),
            Scene(
                "sleep",
                listOf("眠い", "眠そう", "ねんね", "寝よう", "寝る", "おやすみ", "昼寝", "眠く"),
                listOf(
                    "So sleepy. Night-night. Rest, little one.",
                    "Sleepy time. Nice and quiet. Night-night.",
                    "Time to sleep. Rest, rest. Nice and cozy.",
                    "Sleepy eyes. Night-night. Rest, little one.",
                    "{name}, sleepy time. Night-night. Rest nice and easy.",
                ),
            ),
            Scene(
                "wake",
                listOf("起きた", "おはよう", "目覚め", "起きよう", "起きて", "朝だ"),
                listOf(
                    "Good morning! You're awake! Hello, hello!",
                    "You're awake! Hello, hello! Good morning!",
                    "Morning, little one! Eyes open. Hello, hello!",
                    "Hello there! You're awake! Here we go!",
                    "{name}, good morning! You're awake! Hello, hello!",
                ),
            ),
            Scene(
                "diaper",
                listOf("おむつ", "オムツ", "うんち", "おしっこ", "替えよう", "替える", "お尻"),
                listOf(
                    "Diaper time! Nice and easy. Here we go!",
                    "Fresh diaper! Here we go! Nice and easy.",
                    "Diaper change! Wipe, wipe! All clean.",
                    "Here we go! Diaper time! Nice and clean.",
                    "{name}, diaper time! Nice and easy. Here we go!",
                ),
            ),
            Scene(
                "clothes",
                listOf("着替え", "服着", "服脱", "お洋服", "パジャマ", "靴下", "帽子"),
                listOf(
                    "Clothes on! Here we go! Nice and easy.",
                    "Time to dress! One little arm. Here we go!",
                    "Getting dressed! Nice and easy. All ready.",
                    "Clothes time! Here we go! All cozy.",
                    "{name}, clothes on! Nice and easy. All ready.",
                ),
            ),
            Scene(
                "hug",
                listOf("抱っこ", "だっこ", "ぎゅ", "抱きしめ", "抱っこしよう", "腕の中"),
                listOf(
                    "Big cuddle! Up, up! Nice and close.",
                    "Cuddle time! Nice and close. Here we go!",
                    "Up we go! Big hug. So cozy.",
                    "Big hug! Nice and close. I'm right here.",
                    "{name}, cuddle time! Big hug. Nice and close.",
                ),
            ),
            Scene(
                "hands",
                listOf("手", "おてて", "握って", "にぎって", "ぎゅっと", "指", "つかん"),
                listOf(
                    "Tiny hands! Squeeze, squeeze! Wiggle, wiggle!",
                    "Little hands! Open, close. Wiggle, wiggle!",
                    "Tiny fingers! Squeeze, squeeze! Little hands!",
                    "Hands, hands! Open and close. Wiggle, wiggle!",
                    "{name}, tiny hands! Squeeze, squeeze! Wiggle, wiggle!",
                ),
            ),
            Scene(
                "feet",
                listOf("足", "あんよ", "キック", "蹴って", "つま先", "足バタ"),
                listOf(
                    "Little feet! Kick, kick! Wiggle, wiggle!",
                    "Tiny feet! Kick, kick! Little toes!",
                    "Feet, feet! Up and down. Kick, kick!",
                    "Little toes! Wiggle, wiggle! Kick, kick!",
                    "{name}, little feet! Kick, kick! Wiggle, wiggle!",
                ),
            ),
            Scene(
                "smile",
                listOf("笑った", "笑って", "笑顔", "にこにこ", "ニコニコ", "微笑"),
                listOf(
                    "Big smile! Smile, smile! Hello, little one!",
                    "What a smile! Hello, hello! Smile, smile!",
                    "Smile, smile! There it is! Hello there!",
                    "Happy smile! Hello, little one! So sweet.",
                    "{name}, big smile! Hello, hello! Smile, smile!",
                ),
            ),
            Scene(
                "cry",
                listOf("泣いて", "泣いた", "泣いちゃ", "涙", "えーん", "ぐず", "ぐずぐず"),
                listOf(
                    "I hear you. I'm right here. Nice and gentle.",
                    "I hear you. Here with you. Nice and close.",
                    "Hello, little one. I hear you. I'm right here.",
                    "I hear your voice. Nice and gentle. I'm right here.",
                    "{name}, I hear you. I'm right here. Nice and gentle.",
                ),
            ),
            Scene(
                "voice",
                listOf("声出", "あーって", "うーって", "おしゃべり", "喃語", "クーイング", "あうあう"),
                listOf(
                    "I hear you! Hello, hello! I'm listening.",
                    "What a voice! Ooh, ahh! I hear you.",
                    "Hello, little one! I hear you. Ooh, ahh!",
                    "You're talking! Hello, hello! I'm listening.",
                    "{name}, I hear you! Ooh, ahh! I'm listening.",
                ),
            ),
            Scene(
                "tummy",
                listOf("お腹", "おなか", "げっぷ", "ゲップ", "お腹いっぱい", "満腹", "吐き戻"),
                listOf(
                    "Little tummy. Nice and easy. Take your time.",
                    "Little tummy. Nice and gentle. Here we go.",
                    "Nice and slow. Little tummy. Take your time.",
                    "Easy, easy. Little tummy. I'm right here.",
                    "{name}, little tummy. Nice and easy. Take your time.",
                ),
            ),
            Scene(
                "play",
                listOf("遊ぼう", "遊ん", "おもちゃ", "ガラガラ", "ぬいぐるみ", "メリー", "ボール"),
                listOf(
                    "Play time! Look, look! Here we go!",
                    "Let's play! Look with me. Here we go!",
                    "Play, play! Look, look! So much fun!",
                    "Time to play! Hello, hello! Let's play!",
                    "{name}, play time! Look, look! Here we go!",
                ),
            ),
            Scene(
                "outside",
                listOf("散歩", "お散歩", "外行", "お外", "公園", "ベビーカー", "出かけ"),
                listOf(
                    "Outside time! Look around! Here we go!",
                    "Out we go! Look, look! Listen with me.",
                    "Outside, outside! Look around! Here we go!",
                    "Time outside! Look with me. Listen, listen!",
                    "{name}, outside time! Look around! Here we go!",
                ),
            ),
            Scene(
                "rain",
                listOf("雨", "降ってる", "降ってきた", "雨音", "傘"),
                listOf(
                    "Rain, rain! Pitter-patter! Listen, listen!",
                    "Rain outside! Drip, drop! Listen with me.",
                    "Pitter-patter! Rain, rain! Drip, drop!",
                    "Listen, listen! Rain outside! Pitter-patter!",
                    "{name}, rain outside! Pitter-patter! Listen, listen!",
                ),
            ),
            Scene(
                "sun",
                listOf("晴れ", "いい天気", "お日様", "太陽", "明るい", "ぽかぽか"),
                listOf(
                    "Bright day! Hello, sunshine! Look, look!",
                    "Sunshine! Bright, bright! Look with me.",
                    "Hello, sunshine! Bright day! Look, look!",
                    "Bright, bright! Sunshine! Here we go!",
                    "{name}, bright day! Hello, sunshine! Look, look!",
                ),
            ),
            Scene(
                "food",
                listOf("ごはん", "離乳食", "食べよう", "食べた", "おいしい", "いただきます", "スプーン"),
                listOf(
                    "Food time! Yum, yum! Nice and slow.",
                    "Yummy food! Little bite. Nice and easy.",
                    "Time to eat! Yum, yum! Here we go!",
                    "Food, food! Little bite. Yum, yum!",
                    "{name}, food time! Yum, yum! Nice and slow.",
                ),
            ),
            Scene(
                "book",
                listOf("絵本", "本読", "読もう", "お話", "ページ", "めく"),
                listOf(
                    "Book time! Look, look! Turn the page.",
                    "Let's read! Look with me. Turn the page.",
                    "Book, book! Look, look! Here we go!",
                    "Story time! Turn the page. Let's see!",
                    "{name}, book time! Look, look! Turn the page.",
                ),
            ),
            Scene(
                "music",
                listOf("歌", "音楽", "うた", "歌おう", "踊ろう", "リズム", "曲"),
                listOf(
                    "Music time! La-la-la! Listen, listen!",
                    "Let's sing! La-la-la! Listen with me.",
                    "Music, music! Tap, tap! Here we go!",
                    "Song time! La-la-la! Listen, listen!",
                    "{name}, music time! La-la-la! Listen, listen!",
                ),
            ),
        )

        internal fun allTemplatesForValidation(): List<String> =
            genericReplies + scenes.flatMap { it.replies }
    }
}