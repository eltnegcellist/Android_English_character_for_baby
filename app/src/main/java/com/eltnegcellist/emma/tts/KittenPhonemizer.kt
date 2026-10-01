package com.eltnegcellist.emma.tts

import java.io.File
import java.util.Locale

/**
 * English phonemizer ported from the GPL-free Mitsukotoba Web implementation.
 *
 * CMUDict (BSD-style) is the primary lexicon. Context rules resolve common
 * heteronyms and a small local fallback handles out-of-vocabulary words.
 */
internal class KittenPhonemizer private constructor(
    private val dictionary: Map<String, List<List<String>>>,
) {
    fun phonemize(rawText: String): String {
        val text = rawText.lowercase(Locale.US)
        val parts = TOKEN_REGEX.findAll(text).map { it.value }.toList()
        val out = ArrayList<String>(parts.size)

        for (index in parts.indices) {
            val token = parts[index]
            when {
                token.firstOrNull()?.isLetter() == true -> out += lookup(token, parts, index)
                PUNCTUATION.contains(token) -> out += token
            }
        }
        return out.joinToString(" ").trim()
    }

    private fun lookup(rawWord: String, parts: List<String>, index: Int): String {
        val word = rawWord.replace('’', '\'').lowercase(Locale.US)
        CUSTOM_IPA[word]?.let { return it }

        val possessiveBase = word.removeSuffix("'s")
        val tries = listOf(word, word.replace("'", ""), possessiveBase).distinct()
        for (candidate in tries) {
            val variants = dictionary[candidate]
            if (!variants.isNullOrEmpty()) {
                return arpabetToIpa(chooseHeteronym(candidate, variants, parts, index))
            }
        }

        return simpleG2p(word)
    }

    private fun chooseHeteronym(
        word: String,
        candidates: List<List<String>>,
        parts: List<String>,
        index: Int,
    ): List<String> {
        if (candidates.size < 2) return candidates.first()

        val previous = previousLexical(parts, index)
        val previous2 = previousLexical(parts, index, 2)
        val next = nextLexical(parts, index)
        val nearby = nearbyLexical(parts, index).toSet()

        fun chooseByPhone(wanted: String): List<String>? =
            candidates.firstOrNull { phones ->
                phones.any { it.replace(Regex("""[012]$"""), "") == wanted }
            }

        return when (word) {
            "wind" -> {
                val nounish = previous in DETERMINERS ||
                    previous in setOf("strong", "cold", "warm", "gentle", "north", "south", "east", "west")
                val letUs = previous == "us" && previous2 == "let"
                val objectAhead = next in DETERMINERS
                val verbish = previous in AUX_BASE_FORM || letUs || objectAhead ||
                    next in setOf("up", "down", "back", "around")
                chooseByPhone(if (nounish && !verbish) "IH" else if (verbish) "AY" else "IH")
            }
            "read" -> {
                val past = previous in PERFECT_AUX || PAST_CUES.any(nearby::contains)
                val base = previous in AUX_BASE_FORM
                chooseByPhone(if (past && !base) "EH" else "IY")
            }
            "live" -> {
                val adjective = next in LIVE_ADJ_NOUNS || previous in DETERMINERS
                val verb = previous in AUX_BASE_FORM || previous in PRONOUNS
                chooseByPhone(if (adjective && !verb) "AY" else "IH")
            }
            "lead" -> {
                val metalPrevious = previous in setOf("contain", "contains", "contained", "containing", "has", "have", "had", "with", "of", "from", "made")
                val metal = next in LEAD_METAL_NOUNS || previous in setOf("metal", "poisoning") || metalPrevious
                val verb = previous in AUX_BASE_FORM || previous in PRONOUNS
                chooseByPhone(if (metal && !verb) "EH" else "IY")
            }
            "close" -> {
                val niceAndClose = previous == "and" && previous2 == "nice"
                val adjective = next in CLOSE_ADJ_NOUNS || previous in setOf("very", "too", "so") ||
                    next == "to" || niceAndClose
                val verb = previous in AUX_BASE_FORM || previous in PRONOUNS ||
                    previous == "please" || previous == "open"
                chooseByPhone(if (adjective && !verb) "S" else "Z")
            }
            "use" -> {
                val nounish = previous in DETERMINERS || previous in setOf("in", "for", "of")
                val verbish = previous in AUX_BASE_FORM || previous in PRONOUNS
                chooseByPhone(if (nounish && !verbish) "S" else "Z")
            }
            in STRESS_HETERONYMS -> {
                val verbish = previous in AUX_BASE_FORM || previous in PRONOUNS ||
                    (previous == "not" && previous2 in AUX_BASE_FORM)
                val nounish = previous in DETERMINERS
                chooseStress(candidates, if (verbish && !nounish) StressMode.LATE else StressMode.EARLY)
            }
            else -> null
        } ?: candidates.first()
    }

    private fun chooseStress(candidates: List<List<String>>, mode: StressMode): List<String> {
        val sorted = candidates.sortedBy { phones ->
            phones.indexOfFirst { it.endsWith("1") }.let { if (it < 0) Int.MAX_VALUE else it }
        }
        return if (mode == StressMode.LATE) sorted.last() else sorted.first()
    }

    private fun arpabetToIpa(phones: List<String>): String = buildString {
        for (raw in phones) {
            val match = PHONE_REGEX.matchEntire(raw) ?: continue
            val base = match.groupValues[1]
            val stress = match.groupValues.getOrNull(2).orEmpty()
            var phone = PHONE[base] ?: continue
            if (base == "AH" && stress == "0") phone = "ə"
            if (base == "ER" && stress == "0") phone = "ɚ"
            if (base in VOWELS) {
                if (stress == "1") phone = "ˈ" + phone
                else if (stress == "2") phone = "ˌ" + phone
            }
            append(phone)
        }
    }

    private fun simpleG2p(rawWord: String): String {
        val word = rawWord.lowercase(Locale.US)
        val rules = listOf(
            "tion" to "ʃən",
            "sh" to "ʃ",
            "ch" to "ʧ",
            "th" to "θ",
            "ph" to "f",
            "ng" to "ŋ",
            "oo" to "u",
            "ee" to "i",
            "ea" to "i",
        )
        return buildString {
            var index = 0
            while (index < word.length) {
                val rule = rules.firstOrNull { word.startsWith(it.first, index) }
                if (rule != null) {
                    append(rule.second)
                    index += rule.first.length
                } else {
                    SIMPLE_LETTER[word[index]]?.let(::append)
                    index++
                }
            }
        }
    }

    private fun previousLexical(parts: List<String>, index: Int, n: Int = 1): String {
        var found = 0
        for (cursor in index - 1 downTo 0) {
            val token = parts[cursor]
            if (token.firstOrNull()?.isLetter() == true) {
                found++
                if (found == n) return token.lowercase(Locale.US)
            }
        }
        return ""
    }

    private fun nextLexical(parts: List<String>, index: Int, n: Int = 1): String {
        var found = 0
        for (cursor in index + 1 until parts.size) {
            val token = parts[cursor]
            if (token.firstOrNull()?.isLetter() == true) {
                found++
                if (found == n) return token.lowercase(Locale.US)
            }
        }
        return ""
    }

    private fun nearbyLexical(parts: List<String>, index: Int, radius: Int = 5): List<String> {
        val start = maxOf(0, index - radius)
        val end = minOf(parts.lastIndex, index + radius)
        return (start..end)
            .filter { it != index }
            .map { parts[it] }
            .filter { it.firstOrNull()?.isLetter() == true }
            .map { it.lowercase(Locale.US) }
    }

    private enum class StressMode { EARLY, LATE }

    companion object {
        private val TOKEN_REGEX =
            Regex("""[a-z]+(?:'[a-z]+)?|[;:,.!?¡¿—…\"«»]|\S""", RegexOption.IGNORE_CASE)
        private val PHONE_REGEX = Regex("""^([A-Z]+)([012])?$""")
        private val PUNCTUATION = setOf(";", ":", ",", ".", "!", "?", "¡", "¿", "—", "…", "\"", "«", "»")

        private val PHONE = mapOf(
            "AA" to "ɑ", "AE" to "æ", "AH" to "ʌ", "AO" to "ɔ", "AW" to "aʊ", "AY" to "aɪ",
            "B" to "b", "CH" to "ʧ", "D" to "d", "DH" to "ð", "EH" to "ɛ", "ER" to "ɝ", "EY" to "eɪ",
            "F" to "f", "G" to "ɡ", "HH" to "h", "IH" to "ɪ", "IY" to "i", "JH" to "ʤ", "K" to "k",
            "L" to "l", "M" to "m", "N" to "n", "NG" to "ŋ", "OW" to "oʊ", "OY" to "ɔɪ", "P" to "p",
            "R" to "ɹ", "S" to "s", "SH" to "ʃ", "T" to "t", "TH" to "θ", "UH" to "ʊ", "UW" to "u",
            "V" to "v", "W" to "w", "Y" to "j", "Z" to "z", "ZH" to "ʒ",
            "AX" to "ə", "AXR" to "ɚ", "IX" to "ᵻ", "UX" to "ʊ", "DX" to "ɾ",
            "EL" to "l", "EM" to "m", "EN" to "n", "NX" to "ŋ",
        )

        private val VOWELS = setOf(
            "AA", "AE", "AH", "AO", "AW", "AY", "EH", "ER", "EY", "IH", "IY",
            "OW", "OY", "UH", "UW", "AX", "AXR", "IX", "UX",
        )

        private val SIMPLE_LETTER = mapOf(
            'a' to "æ", 'b' to "b", 'c' to "k", 'd' to "d", 'e' to "ɛ", 'f' to "f",
            'g' to "ɡ", 'h' to "h", 'i' to "ɪ", 'j' to "ʤ", 'k' to "k", 'l' to "l",
            'm' to "m", 'n' to "n", 'o' to "ɑ", 'p' to "p", 'q' to "k", 'r' to "ɹ",
            's' to "s", 't' to "t", 'u' to "ʌ", 'v' to "v", 'w' to "w", 'x' to "ks",
            'y' to "j", 'z' to "z",
        )

        private val AUX_BASE_FORM = setOf("will", "shall", "can", "could", "would", "should", "may", "might", "must", "do", "does", "did", "to", "please")
        private val PERFECT_AUX = setOf("have", "has", "had")
        private val DETERMINERS = setOf("a", "an", "the", "this", "that", "these", "those", "my", "your", "his", "her", "our", "their", "another", "each", "every", "one", "new")
        private val PAST_CUES = setOf("yesterday", "ago", "earlier", "previously", "last")
        private val PRONOUNS = setOf("i", "you", "we", "they", "he", "she", "it")
        private val LIVE_ADJ_NOUNS = setOf("music", "show", "broadcast", "stream", "concert", "performance", "event", "television", "tv", "coverage", "audience", "camera", "video", "feed", "bird")
        private val LEAD_METAL_NOUNS = setOf("pipe", "pipes", "paint", "metal", "poisoning", "ore", "battery", "batteries", "shot", "weight", "weights")
        private val CLOSE_ADJ_NOUNS = setOf("friend", "friends", "relationship", "relationships", "call", "calls", "race", "races", "match", "matches", "look", "contact")
        private val STRESS_HETERONYMS = setOf(
            "record", "present", "object", "project", "permit", "produce", "progress", "rebel", "refuse",
            "subject", "suspect", "conduct", "contract", "contrast", "convert", "digest", "discount",
            "escort", "export", "extract", "import", "increase", "insult", "invalid", "perfect", "protest",
            "reject", "survey", "transfer", "transport",
        )

        private val CUSTOM_IPA = mapOf(
            "peekaboo" to "pikəbˈu",
            "oo" to "ˈu",
            "pitter" to "pˈɪtɚ",
            "mmm" to "m",
            "bassinet" to "bˌæsɪnˈɛt",
            "breastmilk" to "bɹˈɛstmˌɪlk",
            "playtime" to "plˈeɪtˌaɪm",
            "tummytime" to "tˈʌmitˌaɪm",
        )

        fun fromFile(file: File): KittenPhonemizer = fromDictionaryText(file.readText())

        internal fun fromDictionaryText(text: String): KittenPhonemizer {
            val dictionary = HashMap<String, MutableList<List<String>>>(140_000)
            for (rawLine in text.lineSequence()) {
                val line = rawLine.trim()
                if (line.isBlank() || line.startsWith(";;;")) continue
                val split = line.indexOfFirst(Char::isWhitespace)
                if (split <= 0) continue
                val word = line.substring(0, split)
                    .lowercase(Locale.US)
                    .replace(Regex("""\(\d+\)$"""), "")
                val phones = line.substring(split).trim().split(Regex("""\s+"""))
                if (phones.isEmpty()) continue
                dictionary.getOrPut(word) { ArrayList(1) }.add(phones)
            }
            return KittenPhonemizer(dictionary)
        }
    }
}
