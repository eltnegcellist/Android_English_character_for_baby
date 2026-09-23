package com.eltnegcellist.emma.ai

internal object BabyNamePronunciation {
    fun toSpokenEnglish(savedName: String, overrideName: String = ""): String {
        val explicit = sanitizeEnglishName(overrideName)
        if (explicit.isNotBlank()) return explicit

        val cleaned = savedName
            .filterNot { it.isISOControl() }
            .trim()
        if (cleaned.isBlank()) return ""

        val ascii = sanitizeEnglishName(cleaned)
        if (ascii.isNotBlank() && cleaned.all { it.isAsciiNameChar() }) return ascii

        if (cleaned.any { it.isHanCharacter() }) return ""
        val romanized = romanizeKana(cleaned) ?: return ""
        return romanized
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { first ->
                    if (first.isLowerCase()) first.titlecase() else first.toString()
                }
            }
            .take(MAX_SPOKEN_NAME_CHARS)
    }

    fun withChanSuffix(spokenName: String, enabled: Boolean = true): String {
        val base = spokenName.trim()
        if (base.isBlank() || !enabled) return base
        if (Regex("(?:-|\\s)?chan$", RegexOption.IGNORE_CASE).containsMatchIn(base)) return base
        return "$base-chan".take(MAX_SPOKEN_NAME_WITH_SUFFIX_CHARS)
    }

    private fun sanitizeEnglishName(value: String): String = value
        .filterNot { it.isISOControl() }
        .replace(Regex("[^A-Za-z'’ -]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_SPOKEN_NAME_CHARS)

    private fun romanizeKana(value: String): String? {
        val hiragana = buildString(value.length) {
            value.forEach { ch ->
                append(
                    when (ch) {
                        in '\u30A1'..'\u30F6' -> (ch.code - 0x60).toChar()
                        else -> ch
                    },
                )
            }
        }

        val out = StringBuilder()
        var index = 0
        var doubleNextConsonant = false
        while (index < hiragana.length) {
            val ch = hiragana[index]
            if (ch.isWhitespace() || ch == '・' || ch == '-' || ch == '‐' || ch == '‑') {
                if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
                index++
                continue
            }
            if (ch == 'っ') {
                doubleNextConsonant = true
                index++
                continue
            }
            if (ch == 'ー') {
                val vowel = out.lastOrNull { it in "aeiou" }
                if (vowel != null) out.append(vowel)
                index++
                continue
            }

            val pair = if (index + 1 < hiragana.length) "${hiragana[index]}${hiragana[index + 1]}" else ""
            val pairRoman = COMBO[pair]
            val roman = pairRoman ?: SINGLE[ch] ?: return null
            if (doubleNextConsonant && roman.isNotBlank()) {
                val first = roman.first()
                if (first !in "aeioun") out.append(first)
            }
            doubleNextConsonant = false
            out.append(roman)
            index += if (pairRoman != null) 2 else 1
        }
        return out.toString().trim().takeIf { it.isNotBlank() }
    }

    private fun Char.isAsciiNameChar(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this == '\'' || this == '’' || this == ' ' || this == '-'

    private fun Char.isHanCharacter(): Boolean =
        this in '\u3400'..'\u4DBF' || this in '\u4E00'..'\u9FFF' || this in '\uF900'..'\uFAFF'

    private val SINGLE = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "o", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ゔ' to "vu",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
    )

    private val COMBO = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo",
        "てぃ" to "ti", "でぃ" to "di", "とぅ" to "tu", "どぅ" to "du",
        "ゔぁ" to "va", "ゔぃ" to "vi", "ゔぇ" to "ve", "ゔぉ" to "vo",
    )

    private const val MAX_SPOKEN_NAME_CHARS = 40
    private const val MAX_SPOKEN_NAME_WITH_SUFFIX_CHARS = 45
}
