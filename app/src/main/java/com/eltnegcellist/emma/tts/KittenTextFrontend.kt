package com.eltnegcellist.emma.tts

import java.text.Normalizer

internal object KittenTextFrontend {
    private const val PAD = "$"
    private const val PUNCTUATION = ";:,.!?¡¿—…\"«»\"\" "
    private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val LETTERS_IPA =
        "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘'̩'ᵻ"

    private val symbols: List<Char> = buildList {
        add(PAD[0])
        PUNCTUATION.forEach(::add)
        LETTERS.forEach(::add)
        LETTERS_IPA.forEach(::add)
    }
    private val symbolToId: Map<Char, Int> = buildMap {
        symbols.forEachIndexed { index, char -> put(char, index) }
    }

    fun preprocess(raw: String): String {
        var text = Normalizer.normalize(raw, Normalizer.Form.NFC)
            .replace('’', '\'')
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b[\\w.+-]+@[\\w-]+\\.[A-Za-z]{2,}\\b"), "")

        val contractions = listOf(
            Regex("\\bcan't\\b", RegexOption.IGNORE_CASE) to "cannot",
            Regex("\\bwon't\\b", RegexOption.IGNORE_CASE) to "will not",
            Regex("\\bshan't\\b", RegexOption.IGNORE_CASE) to "shall not",
            Regex("\\bain't\\b", RegexOption.IGNORE_CASE) to "is not",
            Regex("\\blet's\\b", RegexOption.IGNORE_CASE) to "let us",
            Regex("\\b(\\w+)n't\\b", RegexOption.IGNORE_CASE) to "\$1 not",
            Regex("\\b(\\w+)'re\\b", RegexOption.IGNORE_CASE) to "\$1 are",
            Regex("\\b(\\w+)'ve\\b", RegexOption.IGNORE_CASE) to "\$1 have",
            Regex("\\b(\\w+)'ll\\b", RegexOption.IGNORE_CASE) to "\$1 will",
            Regex("\\b(\\w+)'d\\b", RegexOption.IGNORE_CASE) to "\$1 would",
            Regex("\\b(\\w+)'m\\b", RegexOption.IGNORE_CASE) to "\$1 am",
            Regex("\\bit's\\b", RegexOption.IGNORE_CASE) to "it is",
        )
        for ((pattern, replacement) in contractions) text = text.replace(pattern, replacement)

        text = text.replace(Regex("\\b(\\d+)(st|nd|rd|th)\\b", RegexOption.IGNORE_CASE)) { match ->
            ordinalToWords(match.groupValues[1].toLongOrNull() ?: return@replace match.value)
        }

        text = text.replace(Regex("(?<![A-Za-z])-?[\\d,]+(?:\\.\\d+)?")) { match ->
            val normalized = match.value.replace(",", "")
            if (normalized.contains('.')) decimalToWords(normalized) else numberToWords(normalized.toLongOrNull() ?: 0L)
        }

        text = text.replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s.,?!;:\\-'—–…]"), " ")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
        return text
    }

    fun cleanPhonemes(phonemes: String): LongArray {
        val tokenized = basicEnglishTokenize(phonemes).joinToString(" ")
        val ids = ArrayList<Long>(tokenized.length + 3)
        ids += 0L
        for (char in tokenized) {
            symbolToId[char]?.let { ids += it.toLong() }
        }
        ids += 10L
        ids += 0L
        return ids.toLongArray()
    }

    private fun basicEnglishTokenize(text: String): List<String> {
        val out = mutableListOf<String>()
        val word = StringBuilder()

        fun flush() {
            if (word.isNotEmpty()) {
                out += word.toString()
                word.clear()
            }
        }

        for (char in text) {
            val type = Character.getType(char)
            val isWord = char.isLetterOrDigit() || char == '_' ||
                type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
            when {
                isWord -> word.append(char)
                char.isWhitespace() -> flush()
                else -> {
                    flush()
                    out += char.toString()
                }
            }
        }
        flush()
        return out
    }

    private fun decimalToWords(raw: String): String {
        val negative = raw.startsWith("-")
        val value = raw.removePrefix("-")
        val parts = value.split('.', limit = 2)
        val integer = numberToWords(parts[0].toLongOrNull() ?: 0L)
        val fraction = parts.getOrNull(1).orEmpty().mapNotNull { digitWord(it) }.joinToString(" ")
        val result = if (fraction.isBlank()) integer else "$integer point $fraction"
        return if (negative) "negative $result" else result
    }

    private fun ordinalToWords(value: Long): String {
        val cardinal = numberToWords(value)
        val lastSpace = cardinal.lastIndexOf(' ')
        val prefix = if (lastSpace >= 0) cardinal.substring(0, lastSpace + 1) else ""
        val last = if (lastSpace >= 0) cardinal.substring(lastSpace + 1) else cardinal
        val ordinal = when (last) {
            "one" -> "first"
            "two" -> "second"
            "three" -> "third"
            "four" -> "fourth"
            "five" -> "fifth"
            "six" -> "sixth"
            "seven" -> "seventh"
            "eight" -> "eighth"
            "nine" -> "ninth"
            "twelve" -> "twelfth"
            else -> when {
                last.endsWith("y") -> last.dropLast(1) + "ieth"
                last.endsWith("e") -> last.dropLast(1) + "th"
                else -> last + "th"
            }
        }
        return prefix + ordinal
    }

    private fun numberToWords(value: Long): String {
        if (value == 0L) return "zero"
        if (value < 0L) return "negative " + numberToWords(-value)

        val scales = listOf(
            1_000_000_000_000L to "trillion",
            1_000_000_000L to "billion",
            1_000_000L to "million",
            1_000L to "thousand",
        )
        var remaining = value
        val parts = mutableListOf<String>()
        for ((size, name) in scales) {
            if (remaining >= size) {
                val chunk = remaining / size
                parts += numberBelowThousand(chunk.toInt()) + " " + name
                remaining %= size
            }
        }
        if (remaining > 0) parts += numberBelowThousand(remaining.toInt())
        return parts.joinToString(" ")
    }

    private fun numberBelowThousand(value: Int): String {
        val ones = arrayOf(
            "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen",
        )
        val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        val parts = mutableListOf<String>()
        val hundreds = value / 100
        val remainder = value % 100
        if (hundreds > 0) parts += ones[hundreds] + " hundred"
        if (remainder in 1..19) parts += ones[remainder]
        else if (remainder >= 20) {
            val tensWord = tens[remainder / 10]
            val one = remainder % 10
            parts += if (one == 0) tensWord else "$tensWord-${ones[one]}"
        }
        return parts.joinToString(" ")
    }

    private fun digitWord(char: Char): String? = when (char) {
        '0' -> "zero"
        '1' -> "one"
        '2' -> "two"
        '3' -> "three"
        '4' -> "four"
        '5' -> "five"
        '6' -> "six"
        '7' -> "seven"
        '8' -> "eight"
        '9' -> "nine"
        else -> null
    }
}
