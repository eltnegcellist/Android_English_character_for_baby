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
        interactionEnabled: Boolean = false,
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
        // New explicit caregiving utterance may replace the previous held Topic,
        // but never overrides a concrete topic in this current utterance.
        val interactionId = if (interactionEnabled) {
            InteractionReplyBank.choose(transcript, semantic, candidateHasStrongTopicEvidence)
        } else null
        if (interactionId != null) return respondInteraction(interactionId, normalized)

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

    private fun respondInteraction(id: String, normalized: String): LiteResponse {
        activeSceneId = null
        activeSceneTurnsRemaining = 0
        val raw = chooseReply(InteractionReplyBank.replies.getValue(id), normalized, false, false)
        remember(raw, raw)
        turnsSinceName++
        turnCounter++
        return LiteResponse(
            english = raw, scene = "interaction:" + id, score = 0,
            ruleScene = "generic", semanticUsed = true, contextUsed = false,
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
                "Would you like some? Here's a little sip. There you go!",
                "Here's your drink! Ready for a sip? Here you are!",
                "Drink time! One little sip. How does it taste?",
                "Want a little sip? Here's your drink. Sip if you like!",
                "Want another sip? No need to hurry. Here you go!",
                "{name}, your drink! Here's a little sip. There you go!",
                "{name}, thirsty? Let's have a drink. Sip when you're ready!",
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

        // Curated baby-directed English; Play Topics uses a separate one/three-sentence
        // catalog, so ordinary conversation replies can evolve independently.
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
            "Hello, sweet baby! You're so loved. I'm right here.",
            "Hi, little one! It's nice being together. Hello again!",
            "Look at you! What a sweet face. I'm glad you're here!",
            "You're very special. I'm happy you're here. Hello, sweetheart!",
            "Hello again, baby! It's lovely seeing you. Shall we say hello?",
            "You're my little sunshine. I love you. Big hugs!",
            "Can you hear me? It's my voice. Hello there!",
            "Let's say hello! Hi, little one! Hello again!",
            "I'm glad we're together. Let's spend time here. Hello, sweetie!",
            "Hello, sweetheart! Look at my face. Here I am!",
            "Shall we talk? I'm listening, baby. Your turn!",
            "Tell me more! I'm listening to you. Hello, baby!",
            "You're so precious. I love you lots. Big hugs!",
            "Hi there, baby! It's lovely seeing you. Hello!",
            "Hello, little one! Can you hear me? Hi there!",
            "Let's make a sound! Ba, ba, ba! Your turn!",
            "One, two, three! Let's count together. Shall we go again?",
            "Shall we wave hello? Hi, hi, hi! Here comes my hand!",
            "Look at my hands! Let's wave hello. Hi, hi!",
            "Let's say good morning! Hello, sweet baby. Hi there!",
            "You're my sweet baby. I'm here with you. Big hugs!",
            "Can you see me? Here I am! Hello, hello!",
            "What do you think? Tell me, little one. I'm listening!",
            "Do you hear that? That's my voice! Hello, baby!",
            "{name}, hello there! I'm here with you. Big hugs!",
            "{name}, my sweetheart! You're so loved. Hello, baby!",
            "{name}, look here! It's me again. Hello there!",
            "{name}, how are you? I'm listening, baby. Your turn!",
            "{name}, my little star! I love you. Hello, sweetheart!",
            "{name}, hello again! I'm happy you're here. Big hugs!",
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
                    "Bath time is here! Is the water warm? Splash, splash!",
                    "Let's wash your hands. One, then the other. All clean!",
                    "Your little toes! Let's wash them gently. Splash, splash!",
                    "Feel the warm water. Can you hear it? Splish, splash!",
                    "We're taking a bath. Listen to the water. Splash, splash!",
                    "Let's wash your tummy. A little splash. There you go!",
                    "{name}, bath time! Feel the warm water. Splash, splash!",
                    "{name}, look! Water on your toes. Splish, splash!",
                ),
            ),
            Scene(
                "milk",
                listOf("ミルク", "母乳", "おっぱい", "飲んだ", "飲もう", "飲めた", "哺乳瓶", "授乳"),
                listOf(
                    "Here's your milk! Ready for a sip? Here you go!",
                    "Can you taste it? A little milk. Mmm, yummy!",
                    "Milk for you! One little sip. Then another!",
                    "Are you still hungry? We can have more. I'm here!",
                    "It's milk time! Take little sips. I'm here with you.",
                    "Milk time! Are you hungry? Let's have some!",
                    "{name}, your milk! Here's a little sip. Mmm, yummy!",
                    "{name}, milk time! Take a little sip. I'm here!",
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
                    "Are you sleepy? Let's get cozy. Night-night!",
                    "Time to rest. Let's get cozy now. I'll stay close.",
                    "Let's close our eyes. I'll stay nearby. Sleep well!",
                    "Shh, little one. Let's have quiet time. I'm right here.",
                    "Getting sleepy? I'll stay with you. Rest well!",
                    "Rest well, baby. I'll stay close by. Sweet dreams!",
                    "{name}, sleep well. I'm right here. Sweet dreams!",
                    "{name}, night-night! Let's get cozy. Rest well!",
                ),
            ),
            Scene(
                "wake",
                listOf("起きた", "おはよう", "目覚め", "起きよう", "起きて", "朝だ"),
                listOf(
                    "You're awake! Hello, little one. It's nice seeing you!",
                    "Your eyes are open! Hello, little one. How are you?",
                    "Look who's awake! How are you? Hello, little one!",
                    "Hello again! Did you rest well? I'm glad you're awake.",
                    "Look who's awake! Did you rest well? Hello there!",
                    "Hello there! You're awake now. Let's say hello!",
                    "{name}, you're awake! How was your rest? Hello, sweetheart!",
                    "{name}, you're awake! How are you? Hello again!",
                ),
            ),
            Scene(
                "diaper",
                listOf("おむつ", "オムツ", "うんち", "おしっこ", "替えよう", "替える", "お尻"),
                listOf(
                    "Time for a change. Let's get you dry. All fresh!",
                    "New diaper time! Let's wipe your bottom. All clean!",
                    "Let's change your diaper. One little wipe. All fresh!",
                    "Off comes your diaper. Let's clean you up. There!",
                    "Let's change your diaper. I'll be gentle. Nice and fresh!",
                    "Diaper change! I'll be gentle. You're doing great!",
                    "{name}, diaper time! Let's get you clean. All fresh!",
                    "{name}, let's change you. One little wipe. All clean!",
                ),
            ),
            Scene(
                "clothes",
                listOf("着替え", "服着", "服脱", "お洋服", "パジャマ", "靴下", "帽子"),
                listOf(
                    "Let's put clothes on. One arm first. Other arm next!",
                    "Your little shirt! Let's put it on. One arm goes in!",
                    "Here come your socks. One little foot. Then another!",
                    "Time to get dressed. Arms through the sleeves. All ready!",
                    "Let's change your clothes. This arm first. There you go!",
                    "Look at your clothes! Shall we get dressed? One arm first!",
                    "{name}, clothes on! One arm in. Looking lovely!",
                    "{name}, let's get dressed. Socks on next. All ready!",
                ),
            ),
            Scene(
                "hug",
                listOf("抱っこ", "だっこ", "ぎゅ", "抱きしめ", "抱っこしよう", "腕の中"),
                listOf(
                    "Come here, baby. Let's have a cuddle. Big hugs!",
                    "Up you go! Into my arms. I've got you!",
                    "A little cuddle? I'll hold you close. So cozy!",
                    "Let's have a hug. Close to my heart. I love you!",
                    "Here are my arms. Come close, sweetheart. Big hug!",
                    "Hug time! You're warm and cozy. I'm here!",
                    "{name}, come here! A big warm hug. I love you!",
                    "{name}, cuddle time! I'll hold you close. So cozy!",
                ),
            ),
            Scene(
                "hands",
                listOf("手", "おてて", "握って", "にぎって", "ぎゅっと", "指", "つかん"),
                listOf(
                    "Look at your hands! Ten tiny fingers. Wiggle, wiggle!",
                    "Open your hands. Close them again. Good job!",
                    "Those little fingers! Can you wiggle them? Wiggle, wiggle!",
                    "Here's your little hand. Hold my finger. So tiny!",
                    "One hand, two hands! Let's wave hello. Hi there!",
                    "Your fingers are moving! Open and close. Look at that!",
                    "{name}, tiny hands! Hold my finger. Hello there!",
                    "{name}, look! Your hands are moving. Wiggle, wiggle!",
                ),
            ),
            Scene(
                "feet",
                listOf("足", "あんよ", "キック", "蹴って", "つま先", "足バタ"),
                listOf(
                    "Look at your feet! Ten tiny toes. Wiggle, wiggle!",
                    "Kick those little legs! Kick, kick! Look at you!",
                    "Your toes are moving! One, two, three. So tiny!",
                    "Let's count your toes. One, two, three. More to go!",
                    "Little feet up! Little feet down! Kick, kick!",
                    "Can you kick? There go your feet! Good job!",
                    "{name}, little feet! Kick, kick! Look at you!",
                    "{name}, tiny toes! Wiggle those feet. So cute!",
                ),
            ),
            Scene(
                "smile",
                listOf("笑った", "笑って", "笑顔", "にこにこ", "ニコニコ", "微笑"),
                listOf(
                    "What a smile! I love seeing it. You're so sweet!",
                    "There's your smile! I love seeing it. So sweet!",
                    "You're smiling! That makes me smile. Thank you!",
                    "Such a big smile! Your cheeks look lovely. Hello, baby!",
                    "I see a smile! Are you happy? I am too!",
                    "Hello, little smiler! That's a lovely grin. I see you!",
                    "{name}, that smile! You make me happy. Hello, sweetheart!",
                    "{name}, you're smiling! I love that smile. So sweet!",
                ),
            ),
            Scene(
                "cry",
                listOf("泣いて", "泣いた", "泣いちゃ", "涙", "えーん", "ぐず", "ぐずぐず"),
                listOf(
                    "I hear you crying. I'm right here. You're not alone.",
                    "Are you feeling upset? I'm listening, baby. I'm right here.",
                    "Are you upset? I'll stay close. Would you like cuddles?",
                    "I hear you crying. Would a cuddle help? I'm here.",
                    "Are you upset? I'm here with you. Big hugs!",
                    "You can cry. I'll stay beside you. I love you.",
                    "{name}, I'm here. I hear you crying. Big hugs!",
                    "{name}, it's okay. I'm listening to you. I'm here!",
                ),
            ),
            Scene(
                "voice",
                listOf("声出", "あーって", "うーって", "おしゃべり", "喃語", "クーイング", "あうあう"),
                listOf(
                    "I hear your voice! Tell me more. I'm listening!",
                    "Are you talking? Hello, little one. Your turn!",
                    "What a lovely sound! Say it again. I'm listening!",
                    "Ooh, ahh! I hear you! Hello there!",
                    "You're making sounds! I love your voice. Talk to me!",
                    "Was that a coo? I heard you! Hello, baby!",
                    "{name}, I hear you! Tell me more. I'm listening!",
                    "{name}, your voice! I love hearing it. Hello, sweetheart!",
                ),
            ),
            Scene(
                "tummy",
                listOf("お腹", "おなか", "げっぷ", "ゲップ", "お腹いっぱい", "満腹", "吐き戻"),
                listOf(
                    "How's your tummy? Do you feel full? I'm here!",
                    "Let's pat your back. Maybe a little burp. There!",
                    "Your little tummy! Let's have a cuddle. So cozy!",
                    "Is that a burp? Let's take it slowly. I'm right here.",
                    "Let's get comfortable. I'll hold you close. Big hugs!",
                    "Full tummy? We can rest now. I'm right here.",
                    "{name}, little tummy! Let's get comfortable. Big hugs!",
                    "{name}, feeling full? I'll hold you close. So cozy!",
                ),
            ),
            Scene(
                "play",
                listOf("遊ぼう", "遊ん", "おもちゃ", "ガラガラ", "ぬいぐるみ", "メリー", "ボール"),
                listOf(
                    "Let's play together! What should we try? Peekaboo!",
                    "Here's your toy! Can you see it? Look here!",
                    "Ready to play? Shall we start together? Here's a little game!",
                    "Peekaboo! Where did I go? Here I am!",
                    "Let's shake the rattle. Can you hear it? Shake, shake!",
                    "Look at this toy! It's here for you. Let's play!",
                    "{name}, let's play! Here's your toy. Look at that!",
                    "{name}, peekaboo! Where am I? Here I am!",
                ),
            ),
            Scene(
                "outside",
                listOf("散歩", "お散歩", "外行", "お外", "公園", "ベビーカー", "出かけ"),
                listOf(
                    "Let's go outside! Look at the sky. What do you see?",
                    "Fresh air outside! Can you feel it? Hello, world!",
                    "Time for a stroll. Let's look around. What's over there?",
                    "We're going outside! Let's look all around. What do you see?",
                    "Look up, baby! There's the sky. So big!",
                    "Out we go! What can you hear? Let's listen!",
                    "{name}, outside time! Look at the sky. Hello, world!",
                    "{name}, let's stroll! What can you see? Look around!",
                ),
            ),
            Scene(
                "rain",
                listOf("雨", "降ってる", "降ってきた", "雨音", "傘"),
                listOf(
                    "Rain is falling! Can you hear it? Pitter-patter!",
                    "Listen to the rain. Drip, drop! Drip, drop!",
                    "Rain on the window! Tap, tap, tap. Can you hear?",
                    "It's raining outside. Look at the drops. Drip, drop!",
                    "The rain sounds soft. Listen with me. Pitter-patter!",
                    "Raindrops are falling. Listen to their sound. Drip, drop!",
                    "{name}, it's raining! Listen to the drops. Drip, drop!",
                    "{name}, hear that? It's the rain. Pitter-patter!",
                ),
            ),
            Scene(
                "sun",
                listOf("晴れ", "いい天気", "お日様", "太陽", "明るい", "ぽかぽか"),
                listOf(
                    "The sun is shining! What a bright day. Hello, sunshine!",
                    "Look at the sunlight! It's bright outside. So lovely!",
                    "Hello, sunshine! The sky is bright. Look up!",
                    "A sunny day! Let's look outside. Can you see?",
                    "See the bright sky? The sun is out. Hello, sunshine!",
                    "Look at the sunshine! Can you see it? So bright!",
                    "{name}, it's sunny! Look at the light. So bright!",
                    "{name}, sunshine today! The sky is bright. Hello, sunshine!",
                ),
            ),
            Scene(
                "food",
                listOf("ごはん", "離乳食", "食べよう", "食べた", "おいしい", "いただきます", "スプーン"),
                listOf(
                    "Time for a bite! Here's your spoon. Open wide!",
                    "Let's try some food. One little bite. How is it?",
                    "Ready to eat? A little spoonful. There you go!",
                    "Do you like it? Want another bite? No need to hurry.",
                    "Here's a little bite. Let's taste it. Mmm, yummy!",
                    "All done eating? Shall we wipe up? You did well!",
                    "{name}, food time! One little spoonful. Is it yummy?",
                    "{name}, let's eat! Here's your spoon. Open wide!",
                ),
            ),
            Scene(
                "book",
                listOf("絵本", "本読", "読もう", "お話", "ページ", "めく"),
                listOf(
                    "Let's read a book! Look at the pictures. What's that?",
                    "Here's your book! Turn the page. What comes next?",
                    "Look at this picture! What can you see? Let's look together!",
                    "One more page! Let's see what's there. Turn, turn!",
                    "Story time! Listen to my voice. Here comes a story!",
                    "Let's look together. See that picture? What do you notice?",
                    "{name}, book time! Look at the pictures. What's that?",
                    "{name}, let's read! Turn the page. What comes next?",
                ),
            ),
            Scene(
                "music",
                listOf("歌", "音楽", "うた", "歌おう", "踊ろう", "リズム", "曲"),
                listOf(
                    "Let's sing together! La, la, la! Your turn!",
                    "Listen to the music. Can you hear it? Tap, tap!",
                    "Here's a little song. Listen with me. La, la!",
                    "Hear the music? Tap, tap! Let's sing!",
                    "Let's make some music! Clap, clap! What fun!",
                    "A song for you! La, la, la! Sing with me!",
                    "{name}, let's sing! La, la, la! Your turn!",
                    "{name}, hear that song? Let's listen together. Tap, tap!",
                ),
            ),
        )

        internal fun allTemplatesForValidation(): List<String> =
            genericReplies + scenes.flatMap { it.replies } + drinkScene.replies
    }
}