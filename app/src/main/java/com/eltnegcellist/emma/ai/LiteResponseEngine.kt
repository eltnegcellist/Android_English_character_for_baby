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
        val rescued = if (selected == null) {
            LitePhoneticSceneMatcher.match(
                transcript = transcript,
                scenePhrases = scenes.associate { scene ->
                    scene.id to (scene.keywords + sceneSpeechHints[scene.id].orEmpty())
                },
                sceneExclusions = sceneSpeechExclusions,
            )
        } else {
            null
        }

        val scene = selected?.first
            ?: rescued?.sceneId?.let { sceneId -> scenes.firstOrNull { it.id == sceneId } }
        val score = selected?.second ?: rescued?.score ?: 0
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
                if (normalizedKeyword.length >= 2 && transcript == normalizedKeyword) {
                    total += 2
                } else if (
                    normalizedKeyword.length == 2 &&
                    transcript.length <= normalizedKeyword.length + 3 &&
                    (transcript.startsWith(normalizedKeyword) || transcript.endsWith(normalizedKeyword))
                ) {
                    total += 1
                }
            }
        }

        val hints = sceneSpeechHints[scene.id].orEmpty()
            .asSequence()
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toList()
        val exclusions = sceneSpeechExclusions[scene.id].orEmpty()
            .asSequence()
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toList()
        val hintMatched = hints.any { transcript.contains(it) }
        val excluded = exclusions.any { transcript.contains(it) }

        if (excluded && !hintMatched) return 0
        if (hintMatched) total = max(total, 4)
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
        .replace("ねよっか", "寝よっか")
        .replace("ねよう", "寝よう")
        .replace("ねる", "寝る")
        .replace("ねます", "寝ます")
        .replace("ねて", "寝て")
        .replace("ねた", "寝た")

    companion object {
        private const val MIN_SCENE_SCORE = 3
        private const val RECENT_REPLY_WINDOW = 5
        private const val RECENT_OPENER_WINDOW = 3

        /**
         * Static runtime bank. The production direction is to generate/refresh
         * these candidates offline with Gemma using the Full Baby-mode intent,
         * validate them, then bake only the resulting English strings into Lite.
         */
        private val sceneSpeechHints = mapOf(
            "bath" to listOf(
                "お風呂入", "風呂入", "おふろはい", "シャワー浴", "体洗", "洗お", "湯船入",
            ),
            "milk" to listOf(
                "ミルク飲", "みるく飲", "おっぱい飲", "授乳", "哺乳瓶", "ミルクにし", "おっぱいにし",
            ),
            "sleep" to listOf(
                "寝よ", "寝る", "寝ます", "寝て", "寝た", "寝かし", "寝かせ", "眠ろ", "眠る",
                "眠い", "眠そう", "眠く", "ねんね", "おねんね", "おやすみ", "昼寝", "お昼寝", "睡眠", "就寝",
            ),
            "wake" to listOf(
                "起きよ", "起きる", "起きて", "起きた", "目覚め", "おはよう", "朝だ",
            ),
            "diaper" to listOf(
                "おむつ替", "オムツ替", "おむつかえ", "うんち出", "うんちした", "おしっこ出",
                "おしっこした", "お尻拭", "おしり拭",
            ),
            "clothes" to listOf(
                "着替えよ", "着替えよう", "着替えよっか", "服着", "服脱", "着せよ", "脱ご",
                "パジャマ着", "靴下はこ",
            ),
            "hug" to listOf(
                "抱っこし", "だっこし", "抱っこする", "だっこする", "ぎゅー", "ぎゅっ", "抱きしめ",
            ),
            "hands" to listOf(
                "おてて", "手握", "手にぎ", "指つか", "指握", "手バタ",
            ),
            "feet" to listOf(
                "あんよ", "足バタ", "足けり", "足蹴", "キック", "つま先", "足動",
            ),
            "smile" to listOf(
                "にこにこ", "ニコニコ", "笑った", "笑って", "笑顔", "微笑", "にやっ", "にこっ",
            ),
            "cry" to listOf(
                "泣い", "泣く", "泣き", "涙", "えーん", "ぐず", "ぐずぐず", "ぐずって",
            ),
            "voice" to listOf(
                "声出", "おしゃべり", "喃語", "クーイング", "あーって", "うーって", "あうあう",
                "話してる", "しゃべって",
            ),
            "tummy" to listOf(
                "げっぷ", "ゲップ", "お腹いっぱい", "おなかいっぱい", "満腹", "吐き戻", "吐いた",
                "お腹苦", "おなか苦",
            ),
            "play" to listOf(
                "遊ぼ", "あそぼ", "遊ぶ", "おもちゃ", "ガラガラ", "ぬいぐるみ", "メリー", "ボールで遊",
            ),
            "outside" to listOf(
                "散歩行", "お散歩行", "さんぽ行", "外行", "お外行", "出かけ", "ベビーカー乗", "公園行",
            ),
            "rain" to listOf(
                "雨降", "雨だ", "あめ降", "雨音", "傘さ",
            ),
            "sun" to listOf(
                "晴れ", "晴れた", "晴れてる", "いい天気", "お日様", "太陽", "ぽかぽか",
            ),
            "food" to listOf(
                "ごはん食", "ご飯食", "離乳食", "食べよ", "たべよ", "食べる", "食べた",
                "いただきます", "スプーン", "お腹すい", "おなかすい", "お腹減", "おなか減",
            ),
            "book" to listOf(
                "絵本読", "えほん読", "本読", "読も", "よもっか", "ページめく", "絵本見", "本見",
            ),
            "music" to listOf(
                "歌お", "うたお", "歌う", "うたう", "音楽聞", "曲聞", "踊ろ", "リズム", "歌って",
            ),
        )

        private val sceneSpeechExclusions = mapOf(
            "bath" to listOf("風呂敷"),
            "sleep" to listOf("寝返り"),
            "hands" to listOf("手伝", "手続", "手紙", "手数"),
            "feet" to listOf("足り", "足す", "足し"),
            "tummy" to listOf("お腹すい", "おなかすい", "お腹減", "おなか減"),
            "voice" to listOf("声優"),
            "music" to listOf("歌舞伎"),
        )

        // Generic is a deliberate fallback experience, not an error message.
        // Keep it broad and scene-neutral so ASR misses still feel varied and baby-directed.
        // Entries are interleaved across greeting, presence, looking, listening, curiosity,
        // encouragement, connection, and gentle sound/rhythm themes.
        private val genericReplies = listOf(
            "Hello, little one! Emma is here. Hi, hi!",
            "I'm right here. We are together. Nice and easy.",
            "Look, look! So much around us. What do you see?",
            "Listen, listen! So many little sounds. What can you hear?",
            "Ooh, what's this? Let's wonder together. Take a look.",
            "You're doing great. Keep noticing things. Here we go!",
            "Hello, sweet one! I'm here with you. Nice and close.",
            "Tap, tap! Little sounds everywhere. Listen with me.",
            "Hi there, little one! Emma says hello. Hello, hello!",
            "Here I am. Stay here with me. We are together.",
            "Look this way! Near and far. So much to see.",
            "Listen with me. Quiet or loud? What do you hear?",
            "Ooh, look around! Something feels new. Let's see together.",
            "Nice and easy. Take your time. Emma is here.",
            "Hello there! I'm right beside you. Here we are.",
            "Tap, tap, tap! Hear that rhythm? Listen, listen!",
            "Hey there, little one! Hi from Emma. Hi there!",
            "I'm here with you. One little moment. Here we go.",
            "Take a look! What is nearby? Look with me.",
            "Listen closely! So many sounds. Here we go.",
            "Ooh, I wonder! What comes next? Let's find out.",
            "Keep looking around. So much to notice. Nice and easy.",
            "Hi, sweet one! Emma is right here. Hello again!",
            "Soft little sounds. Tap and pause. Listen with me.",
            "Hello again, little one! Emma is here. Hi, hi!",
            "Right here together. No rush at all. Nice and easy.",
            "Look around slowly. Near, far, everywhere. What do you see?",
            "Listen, little one! Sounds come and go. Hear them?",
            "Ooh, how interesting! Let's look together. Here we go.",
            "Take your time. Little moments matter. Emma is here.",
            "Hello, hello! I'm staying with you. Nice and close.",
            "Tap, pause, tap! Little rhythms happen. Listen, listen!",
            "Hi, little one! Good to see you. Hello there!",
            "Emma is here. We have this moment. Here we go.",
            "Look over here! Then look around. So much nearby.",
            "Listen over here. Then listen around. So many sounds.",
            "Ooh, what's nearby? Let's be curious. Take a look.",
            "Nice job noticing. Keep looking gently. Here we go.",
            "Hello, sweet one! We're here together. Hi there!",
            "Little beat, little pause. Tap, tap! Listen with me.",
            "Hello there, little one! Hi from Emma. Hello, hello!",
            "I'm right here. This moment is ours. Nice and easy.",
            "Look with me. What catches your eye? Take a look.",
            "Listen with me. What sounds are here? Listen, listen!",
            "Ooh, let's see! So much can happen. Here we go.",
            "You're doing nicely. Keep taking it in. No rush.",
            "Hi, sweet one! I'm here beside you. Hello there!",
            "Tap, tap! Pause, pause. Hear the little rhythm?",
            "Hi again, little one! Emma is here. Hi there!",
            "Here we are. One moment together. Nice and easy.",
            "Look around you. So much to notice. Look, look!",
            "Listen around you. So much to hear. Listen, listen!",
            "Ooh, what now? Let's wonder a little. Here we go.",
            "Take it slowly. Keep noticing around you. Emma is here.",
            "Hello, sweet one! Right here with you. Hi, hi!",
            "Tap and listen. Pause and listen. Here we go.",
            "{name}, hello there! Emma is here. Hi, hi!",
            "{name}, I'm right here. We are together. Nice and easy.",
            "{name}, look with me! So much around us. Take a look.",
            "{name}, listen with me! So many sounds. Listen, listen!",
            "{name}, let's wonder! What is around us? Here we go.",
            "{name}, nice and easy. Take your time. Emma is here.",
            "{name}, hello, sweet one! I'm here with you. Hi there!",
            "{name}, tap, tap! Little sounds everywhere. Listen with me.",
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
                listOf(
                    "眠い", "眠そう", "ねむい", "ねむそう", "ねんね", "寝よう", "寝る", "寝るよ",
                    "寝ます", "寝て", "寝た", "寝かせ", "寝かしつけ", "おやすみ", "昼寝", "お昼寝",
                    "睡眠", "就寝", "眠く",
                ),
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