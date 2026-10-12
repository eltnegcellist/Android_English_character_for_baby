package com.eltnegcellist.emma.ai

import java.util.ArrayDeque
import kotlin.math.max

internal data class LiteResponse(
    val english: String,
    val scene: String,
    val score: Int,
    val ruleScene: String = "generic",
    val semanticUsed: Boolean = false,
    val contextUsed: Boolean = false,
)

internal data class LiteTopicDetection(
    val scene: String,
    val score: Int,
    val strongEvidence: Boolean,
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
    private var activeSceneId: String? = null
    private var activeSceneTurnsRemaining = 0

    fun respond(
        transcript: String,
        spokenBabyName: String = "",
        semantic: SemanticTopicPrediction? = null,
    ): LiteResponse {
        val normalized = normalize(transcript)
        val flexible = LiteFlexibleTopicMatcher.detect(transcript)
        val ranked = scenes.map { scene -> scene to max(score(scene, normalized), flexible[scene.id] ?: 0) }
            .sortedByDescending { it.second }
        val best = ranked.firstOrNull()
        val selected = if (best != null && best.second >= MIN_SCENE_SCORE) best else null
        val rescued = if (selected == null) {
            LitePhoneticSceneMatcher.match(
                transcript = LiteFlexibleTopicMatcher.normalizeParentSpeech(transcript),
                scenePhrases = scenes.associate { scene ->
                    scene.id to (
                        sceneChildcareAnchors[scene.id].orEmpty() + sceneRescuePhrases[scene.id].orEmpty()
                        )
                },
                sceneExclusions = sceneSpeechExclusions,
            )
        } else {
            null
        }

        val detectedScene = selected?.first
            ?: rescued?.sceneId?.let(::findScene)
        val milkScene = findScene("milk")!!
        val drinking = LiteFlexibleTopicMatcher.detectDrinkingAction(transcript)
        val explicitMilk = "milk" in flexible || hasStrongTopicEvidence(milkScene, normalized)
        val drinkingScene = if (drinking && !explicitMilk) {
            if (activeSceneId == "milk" && !LiteFlexibleTopicMatcher.hasNonMilkDrink(transcript)) milkScene else drinkScene
        } else {
            null
        }
        val useDrinking = drinkingScene != null && (
            detectedScene == null ||
                detectedScene.id == "milk" ||
                !hasStrongTopicEvidence(detectedScene, normalized)
            )
        val candidateScene = if (useDrinking) drinkingScene else detectedScene
        val candidateScore = if (useDrinking) 4 else selected?.second ?: rescued?.score ?: 0
        val candidateHasStrongTopicEvidence = candidateScene != null &&
            (
                useDrinking ||
                    rescued != null ||
                    candidateScene.id in flexible ||
                    hasStrongTopicEvidence(candidateScene, normalized)
                )
        val explicitScene = when {
            candidateScene == null -> null
            activeSceneId == null -> candidateScene
            candidateScene.id == activeSceneId -> candidateScene
            candidateHasStrongTopicEvidence -> candidateScene
            else -> null
        }
        val explicitScore = if (explicitScene != null) candidateScore else 0
        val contextualScene = if (explicitScene == null && activeSceneTurnsRemaining > 0) {
            findScene(activeSceneId)
        } else {
            null
        }

        val ruleScene = explicitScene ?: contextualScene
        val requested = if (semantic?.topic == "generic") null else findScene(semantic?.topic)
        val validSemantic = semantic != null && (semantic.topic == "generic" || requested != null)
        val semanticUsed = validSemantic && (
            semantic?.mode == "semantic" ||
                (semantic?.mode == "guard" && ruleScene == null)
            )
        var scene = if (semanticUsed) requested else ruleScene
        var contextUsed = !semanticUsed && contextualScene != null

        val plainFollowup = PLAIN_FOLLOWUP.matches(normalized)
        val implicitMilkDrink =
            useDrinking &&
                drinkingScene?.id == "milk" &&
                activeSceneId == "milk" &&
                !explicitMilk &&
                !LiteFlexibleTopicMatcher.hasNonMilkDrink(transcript)
        val semanticFollowup =
            activeSceneTurnsRemaining > 0 && (plainFollowup || implicitMilkDrink)

        val semanticClear =
            !semanticFollowup &&
                requested != null &&
                (
                    semantic == null ||
                        (semantic.probability >= SEMANTIC_CLEAR_PROBABILITY &&
                            semantic.margin >= SEMANTIC_CLEAR_MARGIN)
                    )
        val ruleClear =
            !semanticFollowup &&
                explicitScene != null &&
                candidateHasStrongTopicEvidence

        var clearScene: Scene? = null
        if (semanticUsed) {
            if (activeSceneId == null || semanticClear) {
                clearScene = requested
            } else if (activeSceneTurnsRemaining > 0 && ruleClear) {
                clearScene = explicitScene
            }

            if (clearScene != null) {
                scene = clearScene
            } else if (activeSceneTurnsRemaining > 0) {
                scene = findScene(activeSceneId)
                contextUsed = scene != null
            }
        }

        val sceneScore = when {
            semanticUsed && contextUsed -> CONTEXT_SCENE_SCORE
            semanticUsed -> 0
            explicitScene != null -> explicitScore
            contextualScene != null -> CONTEXT_SCENE_SCORE
            else -> 0
        }

        if (semanticUsed) {
            when {
                clearScene != null -> {
                    activeSceneId = clearScene.id
                    activeSceneTurnsRemaining = TOPIC_HOLD_TURNS
                }
                contextUsed -> {
                    activeSceneTurnsRemaining--
                    if (activeSceneTurnsRemaining <= 0) activeSceneId = null
                }
            }
        } else if (explicitScene != null) {
            activeSceneId = explicitScene.id
            activeSceneTurnsRemaining = TOPIC_HOLD_TURNS
        } else if (contextualScene != null) {
            activeSceneTurnsRemaining--
            if (activeSceneTurnsRemaining <= 0) activeSceneId = null
        }

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
            score = sceneScore,
            ruleScene = ruleScene?.id ?: "generic",
            semanticUsed = semanticUsed,
            contextUsed = contextUsed,
        )
    }

    fun respondGeneric(spokenBabyName: String = ""): LiteResponse {
        val safeName = sanitizeName(spokenBabyName)
        val forceName = safeName.isNotBlank() &&
            turnsSinceName >= BabySpeechStyle.NAME_REPEAT_WINDOW
        val suppressName = safeName.isNotBlank() &&
            turnsSinceName < BabySpeechStyle.NAME_REPEAT_WINDOW
        val raw = chooseReply(genericReplies, "", forceName, suppressName)
        val named = applyName(raw, safeName, forceName)
        val styled = alignToBabyStyle(named, "", null)

        remember(raw, styled)
        if (safeName.isNotBlank() && containsName(styled, safeName)) {
            turnsSinceName = 0
        } else {
            turnsSinceName++
        }
        turnCounter++
        return LiteResponse(
            english = styled,
            scene = "generic",
            score = 0,
        )
    }

    fun resetConversationContext() {
        activeSceneId = null
        activeSceneTurnsRemaining = 0
    }

    /**
     * Stateless topic sensing shared with Full mode.
     *
     * This never returns "generic": it only reports a concrete Lite scene candidate.
     * Full mode can additionally require [LiteTopicDetection.strongEvidence] so weak
     * generic-adjacent matches do not become persistent topic context.
     */
    fun detectConcreteTopic(transcript: String): LiteTopicDetection? {
        val normalized = normalize(transcript)
        val flexible = LiteFlexibleTopicMatcher.detect(transcript)
        val ranked = scenes.map { scene -> scene to max(score(scene, normalized), flexible[scene.id] ?: 0) }
            .sortedByDescending { it.second }
        val best = ranked.firstOrNull()
        val selected = if (best != null && best.second >= MIN_SCENE_SCORE) best else null
        val rescued = if (selected == null) {
            LitePhoneticSceneMatcher.match(
                transcript = LiteFlexibleTopicMatcher.normalizeParentSpeech(transcript),
                scenePhrases = scenes.associate { scene ->
                    scene.id to (
                        sceneChildcareAnchors[scene.id].orEmpty() + sceneRescuePhrases[scene.id].orEmpty()
                        )
                },
                sceneExclusions = sceneSpeechExclusions,
            )
        } else {
            null
        }

        val detected = selected?.first
            ?: rescued?.sceneId?.let { sceneId -> scenes.firstOrNull { it.id == sceneId } }
        val milkScene = scenes.first { it.id == "milk" }
        val useDrinking = LiteFlexibleTopicMatcher.detectDrinkingAction(transcript) &&
            !("milk" in flexible || hasStrongTopicEvidence(milkScene, normalized)) &&
            (detected == null || detected.id == "milk" || !hasStrongTopicEvidence(detected, normalized))
        val candidate = (if (useDrinking) drinkScene else detected) ?: return null
        val candidateScore = if (useDrinking) 4 else selected?.second ?: rescued?.score ?: 0
        return LiteTopicDetection(
            scene = candidate.id,
            score = candidateScore,
            strongEvidence = useDrinking || rescued != null || candidate.id in flexible || hasStrongTopicEvidence(candidate, normalized),
        )
    }

    private fun findScene(id: String?): Scene? = when (id) {
        null -> null
        "drink" -> drinkScene
        else -> scenes.firstOrNull { it.id == id }
    }

    private fun hasStrongTopicEvidence(scene: Scene, transcript: String): Boolean {
        val strongPhrases =
            sceneChildcareAnchors[scene.id].orEmpty() +
                sceneSpeechHints[scene.id].orEmpty()
        return strongPhrases
            .asSequence()
            .map(::normalize)
            .filter { it.isNotBlank() }
            .any { transcript.contains(it) }
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

        val anchors = sceneChildcareAnchors[scene.id].orEmpty()
            .asSequence()
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toList()
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
        val anchorMatches = anchors.count { transcript.contains(it) }
        val hintMatched = hints.any { transcript.contains(it) }
        val excluded = exclusions.any { transcript.contains(it) }

        if (excluded && !hintMatched) return 0
        if (anchorMatches > 0) total = max(total, 6 + minOf(2, anchorMatches - 1))
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

    private fun normalize(text: String): String = LiteFlexibleTopicMatcher.normalizeParentSpeech(text)
        .replace("おふろ", "お風呂").replace("お風呂", "風呂")
        .replace("ねよっか", "寝よっか")
        .replace(Regex("(?<!かさ|重|たず|尋|訪|は|跳|ま|真)ねよう"), "寝よう")
        .replace(Regex("(?<![ぁ-ん一-龯])ねる"), "寝る")
        .replace(Regex("(?<!かさ|重|たず|尋|訪|は|跳|ま|真)ねます"), "寝ます")
        .replace(Regex("(?<!かさ|重|たず|尋|訪|は|跳|ま|真)ねて"), "寝て")
        .replace(Regex("(?<!かさ|重|たず|尋|訪|は|跳|ま|真)ねた"), "寝た")

    companion object {
        private val drinkScene = Scene(
            "drink", emptyList(), listOf(
                "Ready for a drink? Little sips. How does it taste?",
                "Let's have a drink! Sip, sip! Is it tasty?",
                "Here's a little drink. Take a sip. Yum, yum!",
                "A little drink! Sip, sip! Does that taste good?",
                "Drink time! Let's have a sip. Mmm, yum!",
                "{name}, want a drink? Little sips. Yum, yum!",
                "{name}, here's a drink! Sip, sip! Is it yummy?",
            ),
        )
        private const val MIN_SCENE_SCORE = 3
        private const val CONTEXT_SCENE_SCORE = 2
        private const val TOPIC_HOLD_TURNS = 1
        private const val SEMANTIC_CLEAR_PROBABILITY = 0.65
        private const val SEMANTIC_CLEAR_MARGIN = 0.15
        private val PLAIN_FOLLOWUP = Regex(
            "^(いいね|そうだね|いい感じだね|いい感じですね|気持ちいいね|気持ちいいですね|" +
                "ゆっくりでいいよ|ゆっくりね|もう少し|もうちょっと|どうかな|上手だね|かわいいね)$"
        )
        private const val RECENT_REPLY_WINDOW = 5
        private const val RECENT_OPENER_WINDOW = 3

        /**
         * Static runtime bank. The production direction is to generate/refresh
         * these candidates offline with Gemma using the Full Baby-mode intent,
         * validate them, then bake only the resulting English strings into Lite.
         */
        /**
         * High-confidence childcare vocabulary. These are intentionally more
         * specific than the general scene keywords: one anchor is enough to make
         * a scene a strong candidate, while short topic nouns use lexical boundaries in
         * LiteFlexibleTopicMatcher.
         */
        private val sceneChildcareAnchors = mapOf(
            "bath" to listOf("沐浴", "お風呂", "風呂", "湯船", "シャワー"),
            "milk" to listOf("ミルク", "みるく", "みのく", "母乳", "おっぱい", "哺乳瓶", "授乳"),
            "sleep" to listOf("ねんね", "おねんね", "おやすみ", "昼寝", "お昼寝", "寝る", "寝よう", "眠い", "眠そう"),
            "wake" to listOf("おはよう", "起きた", "起きよう", "目覚め"),
            "diaper" to listOf("おむつ", "オムツ", "おむづ", "うんち", "おしっこ"),
            "clothes" to listOf("着替え", "お着替え", "パジャマ"),
            "hug" to listOf("抱っこ", "だっこ", "ぎゅー", "抱きしめ"),
            "hands" to listOf("おてて", "指つか", "指握", "手バタ"),
            "feet" to listOf("あんよ", "足バタ", "キック", "つま先"),
            "smile" to listOf("にこにこ", "ニコニコ", "笑顔", "笑った", "笑う", "にこっ"),
            "cry" to listOf("泣く", "泣い", "泣き", "ぐず", "ぐずぐず", "えーん"),
            "voice" to listOf("喃語", "クーイング", "おしゃべり", "あーって", "うーって", "あうあう"),
            "tummy" to listOf("げっぷ", "ゲップ", "吐き戻", "お腹いっぱい", "おなかいっぱい"),
            "play" to listOf("遊ぼ", "あそぼ", "おもちゃ", "ガラガラ", "ぬいぐるみ", "メリー"),
            "outside" to listOf("散歩", "お散歩", "ベビーカー", "公園", "お外"),
            "rain" to listOf("雨", "雨音"),
            "sun" to listOf("お日様", "太陽", "晴れ", "ぽかぽか"),
            "food" to listOf("離乳食", "ごはん", "ご飯", "いただきます", "スプーン"),
            "book" to listOf("絵本", "ページめく"),
            "music" to listOf("音楽", "歌お", "うたお", "リズム")
        )

        private val sceneSpeechHints = mapOf(
            "bath" to listOf("お風呂入", "風呂入", "おふろはい", "シャワー浴", "体洗", "洗お", "湯船入"),
            "milk" to listOf("ミルク飲", "みるく飲", "おっぱい飲", "授乳", "哺乳瓶", "ミルクにし", "おっぱいにし"),
            "sleep" to listOf("寝よ", "寝る", "寝ます", "寝て", "寝た", "寝かし", "寝かせ", "眠ろ", "眠る", "眠い", "眠そう", "眠く", "ねんね", "おねんね", "おやすみ", "昼寝", "お昼寝", "睡眠", "就寝"),
            "wake" to listOf("起きよ", "起きる", "起きて", "起きた", "目覚め", "おはよう", "朝だ"),
            "diaper" to listOf("おむつ替", "オムツ替", "おむつかえ", "うんち出", "うんちした", "おしっこ出", "おしっこした", "お尻拭", "おしり拭"),
            "clothes" to listOf("着替えよ", "着替えよう", "着替えよっか", "服着", "服脱", "着せよ", "脱ご", "パジャマ着", "靴下はこ"),
            "hug" to listOf("抱っこし", "だっこし", "抱っこする", "だっこする", "ぎゅー", "ぎゅっ", "抱きしめ"),
            "hands" to listOf("おてて", "手握", "手にぎ", "指つか", "指握", "手バタ"),
            "feet" to listOf("あんよ", "足バタ", "足けり", "足蹴", "キック", "つま先", "足動"),
            "smile" to listOf("にこにこ", "ニコニコ", "笑った", "笑って", "笑顔", "微笑", "にやっ", "にこっ"),
            "cry" to listOf("泣い", "泣く", "泣き", "涙", "えーん", "ぐず", "ぐずぐず", "ぐずって"),
            "voice" to listOf("声出", "おしゃべり", "喃語", "クーイング", "あーって", "うーって", "あうあう", "話してる", "しゃべって"),
            "tummy" to listOf("げっぷ", "ゲップ", "お腹いっぱい", "おなかいっぱい", "満腹", "吐き戻", "吐いた", "お腹苦", "おなか苦"),
            "play" to listOf("遊ぼ", "あそぼ", "遊ぶ", "おもちゃ", "ガラガラ", "ぬいぐるみ", "メリー", "ボールで遊"),
            "outside" to listOf("散歩行", "お散歩行", "さんぽ行", "外行", "お外行", "出かけ", "ベビーカー乗", "公園行"),
            "rain" to listOf("雨降", "雨だ", "あめ降", "雨音", "傘さ"),
            "sun" to listOf("晴れ", "晴れた", "晴れてる", "いい天気", "お日様", "太陽", "ぽかぽか"),
            "food" to listOf("ごはん食", "ご飯食", "離乳食", "食べよ", "たべよ", "食べる", "食べた", "いただきます", "スプーン", "お腹すい", "おなかすい", "お腹減", "おなか減"),
            "book" to listOf("絵本読", "えほん読", "本読", "読も", "よもっか", "ページめく", "絵本見", "本見"),
            "music" to listOf("歌お", "うたお", "歌う", "うたう", "音楽聞", "曲聞", "踊ろ", "リズム", "歌って")
        )

        private val sceneSpeechExclusions = mapOf(
            "bath" to listOf("風呂敷"),
            "sleep" to listOf("寝返り", "ねがえり", "重ね", "かさね"),
            "hands" to listOf("手伝", "手続", "手紙", "手数"),
            "feet" to listOf("足り", "足す", "足し"),
            "tummy" to listOf("お腹すい", "おなかすい", "お腹減", "おなか減"),
            "voice" to listOf("声優"),
            "music" to listOf("歌舞伎", "うたがう", "うたがっ", "うたがい")
        )

        // Human-curated baby-directed generic fallback. These short phrases are
        // packaged offline; Lite never invokes a model to select them at runtime.
        private val sceneRescuePhrases = mapOf(
            "bath" to listOf("お風呂入りますか", "お風呂入ろうか", "お風呂入ろうね", "お風呂に入ろうか"),
            "milk" to listOf("ミルク飲みますか", "ミルク飲む", "ミルク飲もうか", "ミルク飲もうね", "ミルクにしようか", "ミルクの時間だよ"),
            "sleep" to listOf("そろそろ寝ようか", "ねんねしようか", "もう寝ようね", "眠くなってきたね"),
            "wake" to listOf("そろそろ起きようか", "起きる時間だよ", "おはよう起きたね"),
            "diaper" to listOf("おむつ替えようか", "おむつ替えようね", "おむつ替えますか"),
            "clothes" to listOf("着替えようか", "お着替えしようか", "着替えようね"),
            "hug" to listOf("抱っこしようか", "抱っこする", "抱っこしようね"),
            "play" to listOf("一緒に遊ぼうか", "遊ぼうね", "おもちゃで遊ぼうか"),
            "outside" to listOf("お散歩行こうか", "お外行こうか", "散歩に行こうね"),
            "food" to listOf("ごはん食べようか", "ごはん食べますか", "ごはんにしようか"),
            "book" to listOf("絵本読もうか", "絵本読もうね", "絵本を読みますか"),
            "music" to listOf("歌を歌おうか", "お歌歌おうか", "音楽聞こうか")
        )

        private val genericReplies = listOf(
            "Hello, little one! I'm here with you. Hi, baby!",
            "Hi there, sweetheart! Good to see you. Hello again!",
            "You're so precious! I'm glad you're here. Hello, baby!",
            "Hello, dear baby! You make me smile. I'm right here.",
            "Hi, my darling! You are so loved. Big hugs!",
            "Look who's here! My lovely little baby. Hello, sweetheart!",
            "You're wonderful! I'm happy you're here. Hello, baby!",
            "Hey, sweet baby! Let's say hello. Hi there!",
            "You're so special! I love you lots. Big, big hugs!",
            "My little sunshine! You're a lovely baby. Hello there!",
            "Hello, sweet pea! You are so loved. I'm here.",
            "Sweet little baby! I'm glad you're here. Hi there!",
            "Let's say hello! Hello, my love! Hi, hi!",
            "Can you hear me? I'm talking to you. Hello there!",
            "Shall we play? I have an idea. Peekaboo!",
            "Peekaboo, baby! There you are! Hello again!",
            "Let's play peekaboo! One, two, three. Here I am!",
            "Want a little song? La, la, la! Sing with me!",
            "Let's sing together! Listen to this tune. La, la!",
            "Here's a little song! La, la, la! Just for you!",
            "Shall we listen? Listen to my voice. Hi, hi!",
            "Want to hear something? I have a song. La, la!",
            "Let's count together! One, two, three! Good job!",
            "One, two, three! Let's count again! Well done!",
            "Let's wave hello! Wave, wave, wave! Hi there!",
            "Can you wave? Hello, hello! Wave with me!",
            "You're growing every day! How wonderful! I love you!",
            "I'm so happy! You're here with me. Hello, sweetheart!",
            "Let's have a cuddle! Come close, sweet baby. So cozy!",
            "You're my little star! Twinkle, twinkle! I love you!",
            "Shall we have fun? Let's play together. Yay, yay!",
            "Want to try something? Let's make a sound. Ba, ba!",
            "Let's say baa! Baa, baa, baa! What fun!",
            "Can you say boo? Boo, boo! Hello again!",
            "I love your voice! Talk to me, baby. I'm listening.",
            "You're good company! I love being here. Hello, dear!",
            "What's on your mind? I'm listening, little one. Tell me!",
            "Something to tell me? I'm listening, baby. Go ahead!",
            "How are you today? I'm happy you're here. Big hugs!",
            "Let's smile together! Big, happy smiles. Hello, sweetheart!",
            "You're my sunshine! I love you. Hello, dear!",
            "Let's have some fun! What's next, little one? You choose!",
            "Let me say hello! Hello to you! My sweet baby!",
            "Do you hear that? It's me saying hi. Hello again!",
        )

        private val sceneFillers = mapOf(
            "bath" to listOf("Splash, splash!", "Let's get clean!"),
            "milk" to listOf("Sip, sip!", "Is it yummy?"),
            "sleep" to listOf("Night-night.", "Rest, rest."),
            "wake" to listOf("Hello, hello!", "Good morning!"),
            "diaper" to listOf("Let's change you!", "All fresh!"),
            "clothes" to listOf("Let's get dressed!", "All ready."),
            "hug" to listOf("Big cuddle!", "Nice and close."),
            "hands" to listOf("Squeeze, squeeze!", "Wiggle, wiggle!"),
            "feet" to listOf("Kick, kick!", "Wiggle, wiggle!"),
            "smile" to listOf("Smile, smile!", "Hello, hello!"),
            "cry" to listOf("I'm right here.", "Nice and gentle."),
            "voice" to listOf("Ooh, ahh!", "I'm listening."),
            "tummy" to listOf("Big hugs!", "I'm with you."),
            "play" to listOf("Look, look!", "Let's have fun!"),
            "outside" to listOf("Look around!", "What do you see?"),
            "rain" to listOf("Pitter-patter!", "Listen, listen!"),
            "sun" to listOf("Bright, bright!", "Look, look!"),
            "food" to listOf("Yum, yum!", "Is it tasty?"),
            "book" to listOf("Look, look!", "Turn the page."),
            "music" to listOf("La-la-la!", "Listen, listen!"),
        )

        private val scenes = listOf(
            Scene(
                "bath",
                listOf("お風呂", "風呂", "湯船", "お湯", "シャワー", "体洗", "あったかいお湯"),
                listOf(
                    "Bath time! Splash, splash! Let's get clean!",
                    "Warm bath! Feel the water. Splish, splash!",
                    "Bath time! Wash, wash! All clean.",
                    "Let's wash up! Bath time! Splash, splash!",
                    "{name}, bath time! Splash, splash! Let's get clean!",
                ),
            ),
            Scene(
                "milk",
                listOf("ミルク", "母乳", "おっぱい", "飲んだ", "飲もう", "飲めた", "哺乳瓶", "授乳"),
                listOf(
                    "Milk time! Let's have milk. Yum, yum!",
                    "Here comes milk! Is it tasty? Mmm, yummy!",
                    "Little sips. Milk for your tummy. Mmm, yum!",
                    "Milk is yummy! Let's have a sip. Mmm, yum!",
                    "Time for milk! Let's get cozy. Sip, sip!",
                    "Milk, milk! Are you enjoying it? Yum, yum!",
                    "{name}, it's milk time! Hello, little one. Yum, yum!",
                    "{name}, here comes milk! Let's have a sip. Yum, yum!",
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
                    "Hello there! You're awake! Good to see you!",
                    "{name}, good morning! You're awake! Hello, hello!",
                ),
            ),
            Scene(
                "diaper",
                listOf("おむつ", "オムツ", "うんち", "おしっこ", "替えよう", "替える", "お尻"),
                listOf(
                    "Diaper time! A fresh diaper. All clean!",
                    "Fresh diaper! Let's get comfy. Clean and dry!",
                    "Diaper change! Wipe, wipe! All clean.",
                    "Let's change you! Diaper time! Fresh and clean!",
                    "{name}, diaper time! Let's change you. All fresh!",
                ),
            ),
            Scene(
                "clothes",
                listOf("着替え", "服着", "服脱", "お洋服", "パジャマ", "靴下", "帽子"),
                listOf(
                    "Clothes on! One arm in. Other arm in!",
                    "Time to dress! One little arm. Now the other!",
                    "Getting dressed! Arms in first. Looking lovely!",
                    "Clothes time! Socks on next. So cozy!",
                    "{name}, clothes on! Looking lovely! So cozy!",
                ),
            ),
            Scene(
                "hug",
                listOf("抱っこ", "だっこ", "ぎゅ", "抱きしめ", "抱っこしよう", "腕の中"),
                listOf(
                    "Big cuddle! Up, up! Nice and close.",
                    "Cuddle time! Come close, baby. Big warm hugs!",
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
                    "How's your tummy? Are you feeling okay? I'm right here.",
                    "Little tummy! Does it feel full? Let's have a cuddle.",
                    "Is your tummy happy? Let's get comfortable. I'm here.",
                    "Your little tummy. Let's get cozy. I'm with you.",
                    "Tummy, tummy! I'm here to help. Big hugs!",
                    "Let's have a cuddle. Is that better? I'm right here.",
                    "{name}, how's your tummy? I'll stay right here. Big hugs!",
                    "{name}, little tummy! Let's get cozy. I'm here!",
                ),
            ),
            Scene(
                "play",
                listOf("遊ぼう", "遊ん", "おもちゃ", "ガラガラ", "ぬいぐるみ", "メリー", "ボール"),
                listOf(
                    "Play time! Let's have fun. Peekaboo!",
                    "Let's play! Can you find me? Peekaboo!",
                    "Play, play! Look, look! So much fun!",
                    "Time to play! Hello, hello! Let's play!",
                    "{name}, play time! Let's have fun. Peekaboo!",
                ),
            ),
            Scene(
                "outside",
                listOf("散歩", "お散歩", "外行", "お外", "公園", "ベビーカー", "出かけ"),
                listOf(
                    "Outside time! Look at the sky. What do you see?",
                    "Out we go! Look, look! Listen with me.",
                    "Outside, outside! Feel the fresh air. What's over there?",
                    "Time outside! Look with me. Listen, listen!",
                    "{name}, outside time! Look at the sky. What do you see?",
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
                    "Bright, bright! Hello, sunshine! What a sunny day!",
                    "{name}, bright day! Hello, sunshine! Look, look!",
                ),
            ),
            Scene(
                "food",
                listOf("ごはん", "離乳食", "食べよう", "食べた", "おいしい", "いただきます", "スプーン"),
                listOf(
                    "Food time! Let's try a bite. Yum, yum!",
                    "Is it tasty? Mmm, that's yummy! Another little bite?",
                    "Ready to eat? Open wide, little one! Here comes food!",
                    "A little spoon! A little bite! Yum, yum!",
                    "Let's taste this! Does it taste good? Mmm, yum!",
                    "Food is fun! Let's taste it. Yum, yum!",
                    "{name}, it's food time! One little bite. Mmm, yum!",
                    "{name}, let's eat! Tiny little bites. Mmm, yummy!",
                ),
            ),
            Scene(
                "book",
                listOf("絵本", "本読", "読もう", "お話", "ページ", "めく"),
                listOf(
                    "Book time! Look, look! Turn the page.",
                    "Let's read! Look with me. Turn the page.",
                    "Book time! Turn a page. What comes next?",
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
                    "Music, music! Tap, tap! Hear that beat?",
                    "Song time! La-la-la! Listen, listen!",
                    "{name}, music time! La-la-la! Listen, listen!",
                ),
            ),
        )

        internal fun allTemplatesForValidation(): List<String> =
            genericReplies + scenes.flatMap { it.replies }
    }
}