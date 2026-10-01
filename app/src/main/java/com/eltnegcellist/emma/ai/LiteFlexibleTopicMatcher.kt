package com.eltnegcellist.emma.ai

import java.text.Normalizer

/** Topic rules aligned with Mitsukotoba Web, including noun boundaries and ASR rescue. */
internal object LiteFlexibleTopicMatcher {
    private data class Topic(val words: List<String>, val actions: List<String>)
    private data class Fuzzy(val scene: String, val score: Int, val confidence: Double)
    private val topics = mapOf(
        "bath" to Topic(listOf("お風呂", "風呂", "沐浴", "湯船", "シャワー"), listOf("入ろ", "入る", "入り", "洗", "あらう", "あらお")),
        "milk" to Topic(listOf("ミルク", "母乳", "おっぱい", "哺乳瓶", "授乳"), listOf("飲", "のも", "のむ", "のん", "にしよう")),
        "sleep" to Topic(listOf("ねんね", "おやすみ", "昼寝", "睡眠", "就寝"), listOf("しよう", "時間", "じかん")),
        "wake" to Topic(listOf("おはよう", "目覚め"), listOf("起き", "おき")),
        "diaper" to Topic(listOf("おむつ", "うんち", "おしっこ"), listOf("替", "変え", "かえ", "出", "でた", "した")),
        "clothes" to Topic(listOf("着替え", "洋服", "パジャマ", "靴下"), listOf("着", "きま", "きよ", "脱", "ぬご", "はこ")),
        "hug" to Topic(listOf("抱っこ", "抱きしめ", "ぎゅー"), listOf("しよう", "する", "して")),
        "hands" to Topic(listOf("おてて"), listOf("握", "にぎ", "つか", "ばた")),
        "feet" to Topic(listOf("あんよ", "キック", "つま先"), listOf("動", "うご", "ばた", "蹴", "けっ")),
        "smile" to Topic(listOf("にこにこ", "笑顔", "にこっ"), listOf("笑", "わら", "して")),
        "cry" to Topic(listOf("ぐずぐず", "えーん"), listOf("泣", "して")),
        "voice" to Topic(listOf("おしゃべり", "喃語", "クーイング"), listOf("出", "でた", "して", "話", "はな")),
        "tummy" to Topic(listOf("げっぷ", "吐き戻し", "お腹いっぱい", "満腹"), listOf("出", "でた", "した", "しちゃ")),
        "play" to Topic(listOf("おもちゃ", "ガラガラ", "ぬいぐるみ", "メリー"), listOf("遊", "あそ", "振", "ふっ")),
        "outside" to Topic(listOf("散歩", "ベビーカー", "公園", "お外"), listOf("行", "いこ", "いく", "乗", "のろ", "出かけ", "でかけ")),
        "rain" to Topic(listOf("雨", "あめ", "雨音"), listOf("降", "ふっ", "ふる", "聞", "きこ")),
        "sun" to Topic(listOf("晴れ", "太陽", "お日様", "ぽかぽか"), listOf("出", "でて", "して")),
        "food" to Topic(listOf("ごはん", "離乳食", "スプーン", "いただきます"), listOf("食", "たべ", "にしよう")),
        "book" to Topic(listOf("絵本", "ページ"), listOf("読", "よも", "よみ", "めく", "見", "みよ")),
        "music" to Topic(listOf("音楽", "リズム"), listOf("聞", "きこ", "歌", "うた", "踊", "おど"))
    )
    private val actionPairs = mapOf(
        "sleep" to Regex("(?:^|そろそろ|もう|早く|はやく)(?:ねる|ねて|ねた|ねよう)"),
        "cry" to Regex("(?:^|また|もう|いっぱい)(?:ないて|ないた|なき)"),
        "bath" to Regex("(?:体|からだ)(?:を|も)?(?:洗|あら)"),
        "wake" to Regex("(?:目|め)(?:を|が)?(?:覚ま|さま)"),
        "clothes" to Regex("(?:洋服|ようふく|服|ふく)(?:を|も)?(?:着|きま|きよ|脱|ぬ)"),
        "hands" to Regex("(?:手|て|指|ゆび)(?:を|が|で|に|も)?(?:握|にぎ|つか|ばた)"),
        "feet" to Regex("(?:足|あし)(?:を|が|で|も)?(?:動|うご|蹴|けっ|ばた)"),
        "voice" to Regex("(?:声|こえ)(?:を|が|も)?(?:出|だ)"),
        "food" to Regex("(?:お腹|おなか)(?:が|も)?(?:すい|空い|減|へっ)"),
        "book" to Regex("(?:本|ほん)(?:を|も)?(?:読|よも|よみ)")
    )
    private val exclusions = mapOf(
        "bath" to listOf("風呂敷", "ふろしき"),
        "sleep" to listOf("寝返り", "ねがえり", "重ね", "かさね"),
        "hands" to listOf("手伝", "てつだ", "手続", "てつづ", "手紙", "てがみ", "手数"),
        "feet" to listOf("足り", "たり", "足す", "たす", "足し"),
        "voice" to listOf("声優", "せいゆう"),
        "music" to listOf("歌舞伎", "かぶき", "うたがう", "うたがっ", "うたがい")
    )
    private val nounTopics = mapOf(
        "hands" to Regex("(?:(?<!\\p{Script=Han})(?:両手|手|指)|(?:(?<![ぁ-ん])|この|その|あの|かわいい|ちいさな|小さな|あなたの|きみの|赤ちゃんの)ゆび|(?:^|この|その|あの|かわいい|ちいさな|小さな|あなたの|きみの|赤ちゃんの)て)(?:\$|だ|です|ね|よ|を|が|は|も|に|で|の|と|って|ちゃん)"),
        "feet" to Regex("(?:(?<!\\p{Script=Han})(?:両足|足)|(?:(?<![ぁ-ん])|この|その|あの|かわいい|ちいさな|小さな|あなたの|きみの|赤ちゃんの)あし(?!た|ら|あと|もと|おと|ば|なみ|どり))(?:\$|だ|です|ね|よ|を|が|は|も|に|で|の|と|って|ちゃん)"),
        "voice" to Regex("(?:(?<!\\p{Script=Han})声|(?:(?<![ぁ-ん])|この|その|あの|かわいい|ちいさな|小さな|あなたの|きみの|赤ちゃんの)こえ)(?:\$|だ|です|ね|よ|を|が|は|も|に|で|の|と|って|ちゃん)"),
        "book" to Regex("(?:(?<!\\p{Script=Han})本|(?:(?<![ぁ-ん])|この|その|あの|かわいい|ちいさな|小さな|あなたの|きみの|赤ちゃんの)ほん)(?:\$|だ|です|ね|よ|を|が|は|も|に|で|の|と|って|ちゃん)"),
        "clothes" to Regex("(?:(?<!\\p{Script=Han})服|(?:(?<![ぁ-ん])|この|その|あの|かわいい|ちいさな|小さな|あなたの|きみの|赤ちゃんの)ふく)(?:\$|だ|です|ね|よ|を|が|は|も|に|で|の|と|って|ちゃん)"),
        "music" to Regex("(?:(?<!\\p{Script=Han})歌|(?:(?<![ぁ-ん])|この|その|あの|かわいい|ちいさな|小さな|あなたの|きみの|赤ちゃんの)うた(?!がう|がっ|がい))(?:\$|だ|です|ね|よ|を|が|は|も|に|で|の|と|って|ちゃん)"),
        "tummy" to Regex("(?:お腹|おなか)(?:\$|だ|です|ね|よ|を|が|は|も|に|で|の|と|って|ちゃん)")
    )
    private val politeActions = listOf(
        listOf("寝", "ね") to "寝よう",
        listOf("眠り", "ねむり") to "眠ろう",
        listOf("起き", "おき") to "起きよう",
        listOf("食べ", "たべ") to "食べよう",
        listOf("飲み", "のみ") to "飲もう",
        listOf("読み", "よみ") to "読もう",
        listOf("遊び", "あそび") to "遊ぼう",
        listOf("歌い", "うたい") to "歌おう",
        listOf("踊り", "おどり") to "踊ろう"
    )

    fun normalizeParentSpeech(raw: String): String {
        var value = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase()
            .map { if (it in 'ァ'..'ヶ') (it.code - 0x60).toChar() else it }.joinToString("")
            .replace(Regex("[\\s、。！？!?,.・「」『』（）()【】\\[\\]〜~]"), "")
        for ((stems, action) in politeActions) {
            val alternatives = stems.map {
                when (it) {
                    "ね" -> "(?<!かさ|重|たず|尋|訪|は|跳|ま|真)ね"
                    "おき" -> "(?<!て|で)おき"
                    "のみ" -> "(?<!た|こ|好|頼)のみ"
                    else -> it
                }
            }
            value = value.replace(Regex("(?:${alternatives.joinToString("|")})(?:ましょう|ませんか|ました|ます)"), action)
        }
        return value
            .replace(Regex("(?:寝|(?<!かさ|重|は|跳)ね)ちゃ(?:った|う)"), "寝た")
            .replace(Regex("(?:泣|な)いちゃ(?:った|う)"), "泣いた")
            .replace("わらって", "笑って").replace("わらった", "笑った")
            .replace("ねむく", "眠く").replace("ねむそう", "眠そう").replace("はれて", "晴れて")
    }

    fun detectDrinkingAction(raw: String): Boolean {
        val value = normalizeParentSpeech(raw)
        if (Regex("(?:飲み会|飲会|のみかい|飲酒|酒|ビール|びーる|服薬|薬|くすり)").containsMatchIn(value)) return false
        return Regex("飲(?:む|もう|みたい|みたが|んだ|んで|めた|める)").containsMatchIn(value) ||
            Regex("(?:^|そろそろ|もう|少し|すこし|いっぱい|ゆっくり|ひとくち|一口|を)(?:のむ|のもう|のみたい|のんだ|のんで|のめた|のめる)").containsMatchIn(value)
    }

    fun hasNonMilkDrink(raw: String): Boolean =
        Regex("(?:水|みず|お茶|おちゃ|麦茶|むぎちゃ|白湯|さゆ|ジュース|じゅーす|飲み物|のみもの)").containsMatchIn(normalizeParentSpeech(raw))

    fun detectNounTopics(raw: String): Map<String, Int> {
        val value = normalizeParentSpeech(raw)
        return nounTopics.filter { (scene, pattern) ->
            pattern.containsMatchIn(value) && !(scene == "tummy" &&
                Regex("(?:お腹|おなか)(?:が|も)?(?:すい|空い|減|へっ)").containsMatchIn(value))
        }.mapValues { 6 }
    }

    fun detect(raw: String): Map<String, Int> {
        val text = normalizeParentSpeech(raw)
        val sound = LitePhoneticSceneMatcher.phoneticKey(text)
        val evidence = detectNounTopics(raw).toMutableMap()
        val fuzzy = mutableListOf<Fuzzy>()
        for ((scene, topic) in topics) {
            if (scene in evidence) continue
            if (exclusions[scene].orEmpty().any { text.contains(normalizeParentSpeech(it)) }) continue
            if (actionPairs[scene]?.containsMatchIn(text) == true) {
                evidence[scene] = 6
                continue
            }
            val supported = if (scene == "voice")
                Regex("(?:出|でた|して|話|はな(?:す|し|そ)|しゃべ)").containsMatchIn(text)
                else topic.actions.any { text.contains(normalizeParentSpeech(it)) }
            for (word in topic.words) {
                val key = LitePhoneticSceneMatcher.phoneticKey(normalizeParentSpeech(word))
                val exact = if (key.length >= 3) sound.contains(key)
                    else text.contains(normalizeParentSpeech(word)) || sound == key
                if (exact) {
                    evidence[scene] = 6
                    break
                }
                if (supported && 'っ' in word && sound.contains(
                        LitePhoneticSceneMatcher.phoneticKey(normalizeParentSpeech(word.replace("っ", "つ"))))) {
                    fuzzy += Fuzzy(scene, 5, .9)
                    continue
                }
                if (key.length < 3 || (!supported && key.length < 5)) continue
                val confidence = windowSimilarity(sound, key)
                val minimum = if (supported) { if (key.length <= 3) 2.0 / 3 else .74 } else .8
                if (confidence + 1e-9 >= minimum) fuzzy += Fuzzy(scene, if (supported) 5 else 4, confidence)
            }
        }
        if (evidence.isEmpty() && fuzzy.isNotEmpty()) {
            val ranked = fuzzy.groupBy { it.scene }.values.map { matches -> matches.maxBy { it.confidence } }
                .sortedByDescending { it.confidence }
            if (ranked.size == 1 || ranked[0].confidence - ranked[1].confidence >= .08)
                evidence[ranked[0].scene] = ranked[0].score
        }
        return evidence
    }

    private fun windowSimilarity(source: String, target: String): Double {
        var best = 0.0
        val edits = if (target.length >= 7) 2 else 1
        for (size in maxOf(3, target.length - edits)..target.length + edits) {
            for (start in 0..source.length - size) {
                val word = source.substring(start, start + size)
                var row = IntArray(target.length + 1) { it }
                for (i in word.indices) {
                    val next = IntArray(target.length + 1)
                    next[0] = i + 1
                    for (j in target.indices) next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1,
                        row[j] + if (word[i] == target[j]) 0 else 1)
                    row = next
                }
                if (row[target.length] <= edits)
                    best = maxOf(best, 1.0 - row[target.length].toDouble() / maxOf(word.length, target.length))
            }
        }
        return best
    }
}
