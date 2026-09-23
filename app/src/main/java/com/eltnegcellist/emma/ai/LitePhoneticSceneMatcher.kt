package com.eltnegcellist.emma.ai

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.roundToInt

internal data class PhoneticSceneMatch(
    val sceneId: String,
    val confidence: Double,
    val score: Int,
    val matchedPhrase: String,
)

/**
 * Lightweight rescue matcher for Moonshine near-misses.
 *
 * This is deliberately used only after the normal LiteResponseEngine matcher
 * fails. It does not try to understand arbitrary Japanese. Instead it compares
 * the ASR result with the existing, bounded Emma scene phrase bank after a
 * speech-oriented normalization and accepts a scene only when the best match
 * is both strong and clearly ahead of the runner-up.
 */
internal object LitePhoneticSceneMatcher {
    private data class CandidateScore(
        val sceneId: String,
        val confidence: Double,
        val matchedPhrase: String,
        val keyLength: Int,
    )

    fun match(
        transcript: String,
        scenePhrases: Map<String, List<String>>,
        sceneExclusions: Map<String, List<String>> = emptyMap(),
    ): PhoneticSceneMatch? {
        val source = phoneticKey(transcript)
        if (source.length < MIN_SOURCE_LENGTH) return null

        val plainTranscript = plainNormalize(transcript)
        val ranked = scenePhrases.mapNotNull { (sceneId, phrases) ->
            if (sceneExclusions[sceneId].orEmpty().any { exclusion ->
                    val plainExclusion = plainNormalize(exclusion)
                    plainExclusion.isNotEmpty() && plainTranscript.contains(plainExclusion)
                }
            ) {
                return@mapNotNull null
            }

            phrases.asSequence()
                .map { phrase -> phrase to phoneticKey(phrase) }
                .filter { (_, key) -> key.length >= MIN_CANDIDATE_LENGTH }
                .map { (phrase, key) ->
                    CandidateScore(
                        sceneId = sceneId,
                        confidence = bestWindowSimilarity(source, key),
                        matchedPhrase = phrase,
                        keyLength = key.length,
                    )
                }
                .maxByOrNull { it.confidence }
        }.sortedByDescending { it.confidence }

        val best = ranked.firstOrNull() ?: return null
        val second = ranked.drop(1).firstOrNull()
        val requiredConfidence = when {
            best.keyLength >= 7 -> 0.72
            best.keyLength >= 5 -> 0.76
            else -> 0.82
        }
        if (best.confidence < requiredConfidence) return null

        val runnerUp = second?.confidence ?: 0.0
        if (best.confidence - runnerUp < MIN_CONFIDENCE_MARGIN) return null

        return PhoneticSceneMatch(
            sceneId = best.sceneId,
            confidence = best.confidence,
            score = max(MIN_RESCUE_SCORE, (best.confidence * 10.0).roundToInt()),
            matchedPhrase = best.matchedPhrase,
        )
    }

    internal fun phoneticKey(text: String): String {
        var value = plainNormalize(text)
        speechAliases.forEach { (from, to) ->
            value = value.replace(from, to)
        }
        value = value.map(::katakanaToHiragana).joinToString("")
        value = Normalizer.normalize(value, Normalizer.Form.NFD)
            .filterNot { it == '\u3099' || it == '\u309A' }
        value = value
            .replace("ぁ", "あ")
            .replace("ぃ", "い")
            .replace("ぅ", "う")
            .replace("ぇ", "え")
            .replace("ぉ", "お")
            .replace("ゃ", "や")
            .replace("ゅ", "ゆ")
            .replace("ょ", "よ")
            .replace("ゎ", "わ")
            .replace("っ", "")
            .replace("ー", "")
        return value
    }

    private fun plainNormalize(text: String): String = text
        .lowercase()
        .replace(Regex("[\\s、。！？!?,.・「」『』（）()【】\\[\\]ー〜~]"), "")

    private fun katakanaToHiragana(ch: Char): Char =
        if (ch.code in 0x30A1..0x30F6) (ch.code - 0x60).toChar() else ch

    private fun bestWindowSimilarity(source: String, target: String): Double {
        if (source.contains(target)) return 1.0
        if (target.contains(source) && source.length >= MIN_CANDIDATE_LENGTH) {
            return source.length.toDouble() / target.length.toDouble()
        }

        val minLength = max(MIN_CANDIDATE_LENGTH, target.length - WINDOW_LENGTH_TOLERANCE)
        val maxLength = minOf(source.length, target.length + WINDOW_LENGTH_TOLERANCE)
        var best = similarity(source, target)

        if (minLength > maxLength) return best

        for (windowLength in minLength..maxLength) {
            if (windowLength > source.length) continue
            for (start in 0..(source.length - windowLength)) {
                val window = source.substring(start, start + windowLength)
                best = max(best, similarity(window, target))
                if (best >= 1.0) return 1.0
            }
        }
        return best
    }

    private fun similarity(left: String, right: String): Double {
        if (left == right) return 1.0
        val denominator = max(left.length, right.length)
        if (denominator == 0) return 1.0
        return 1.0 - levenshtein(left, right).toDouble() / denominator.toDouble()
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val substitution = previous[j] + if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    substitution,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    /**
     * Small, deterministic reading aliases for the vocabulary Emma already
     * supports. These are not a general Japanese tokenizer; they make common
     * ASR homophone/kanji substitutions comparable to the baked scene bank.
     */
    private val speechAliases = linkedMapOf(
        "お風呂" to "おふろ",
        "風呂" to "ふろ",
        "お袋" to "おふろ",
        "年々" to "ねんね",
        "使用" to "しよう",
        "睡眠" to "すいみん",
        "就寝" to "しゅうしん",
        "昼寝" to "ひるね",
        "寝" to "ね",
        "眠" to "ねむ",
        "起" to "お",
        "目覚" to "めざ",
        "着替" to "きが",
        "洋服" to "ようふく",
        "服" to "ふく",
        "靴下" to "くつした",
        "抱っこ" to "だっこ",
        "抱" to "だ",
        "手" to "て",
        "指" to "ゆび",
        "足" to "あし",
        "笑顔" to "えがお",
        "笑" to "わら",
        "泣" to "な",
        "涙" to "なみだ",
        "声" to "こえ",
        "喃語" to "なんご",
        "満腹" to "まんぷく",
        "お腹" to "おなか",
        "腹" to "なか",
        "遊" to "あそ",
        "散歩" to "さんぽ",
        "公園" to "こうえん",
        "外" to "そと",
        "雨音" to "あまおと",
        "雨" to "あめ",
        "晴" to "は",
        "天気" to "てんき",
        "太陽" to "たいよう",
        "離乳食" to "りにゅうしょく",
        "ご飯" to "ごはん",
        "食" to "た",
        "絵本" to "えほん",
        "本" to "ほん",
        "読" to "よ",
        "音楽" to "おんがく",
        "歌" to "うた",
        "踊" to "おど",
        "替え" to "かえ",
        "替" to "かえ",
        "変え" to "かえ",
        "入ろ" to "はいろ",
        "入る" to "はいる",
        "入" to "はい",
        "飲" to "の",
        "授乳" to "じゅにゅう",
        "哺乳瓶" to "ほにゅうびん",
        "湯船" to "ゆぶね",
        "お湯" to "おゆ",
        "体洗" to "からだあら",
        "洗" to "あら",
        "尻" to "しり",
    )

    private const val MIN_SOURCE_LENGTH = 4
    private const val MIN_CANDIDATE_LENGTH = 3
    private const val WINDOW_LENGTH_TOLERANCE = 2
    private const val MIN_CONFIDENCE_MARGIN = 0.08
    private const val MIN_RESCUE_SCORE = 3
}
