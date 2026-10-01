package com.eltnegcellist.emma.tts

import java.text.Normalizer
import java.util.Locale

/**
 * Non-GPL text preparation for Kitten TTS.
 *
 * The symbol inventory and padding match the KittenTTS 0.8 Python/Web path.
 * This intentionally does not depend on eSpeak NG.
 */
internal object KittenTextProcessor {
    private const val PUNCTUATION = ";:,.!?¡¿—…\"«»\"\" "
    private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val IPA =
        "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘'\u0329'ᵻ"

    private val symbolToId: Map<Char, Long> = buildMap {
        val symbols = "\$" + PUNCTUATION + LETTERS + IPA
        symbols.forEachIndexed { index, symbol -> put(symbol, index.toLong()) }
    }

    private val tokenizer = Regex("""[\p{L}\p{M}\p{N}_]+|[^\p{L}\p{M}\p{N}_\s]""")

    fun normalize(raw: String): String {
        var text = Normalizer.normalize(raw, Normalizer.Form.NFC)
        text = text.replace(Regex("""<[^>]+>"""), " ")
        text = text.replace(Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE), " ")
        text = text.replace(
            Regex("""\b[\w.+-]+@[\w-]+\.[a-z]{2,}\b""", RegexOption.IGNORE_CASE),
            " ",
        )

        val contractions = listOf(
            Regex("""\bcan't\b""", RegexOption.IGNORE_CASE) to "cannot",
            Regex("""\bwon't\b""", RegexOption.IGNORE_CASE) to "will not",
            Regex("""\bshan't\b""", RegexOption.IGNORE_CASE) to "shall not",
            Regex("""\bain't\b""", RegexOption.IGNORE_CASE) to "is not",
            Regex("""\blet's\b""", RegexOption.IGNORE_CASE) to "let us",
            Regex("""\b(\w+)n't\b""", RegexOption.IGNORE_CASE) to "$1 not",
            Regex("""\b(\w+)'re\b""", RegexOption.IGNORE_CASE) to "$1 are",
            Regex("""\b(\w+)'ve\b""", RegexOption.IGNORE_CASE) to "$1 have",
            Regex("""\b(\w+)'ll\b""", RegexOption.IGNORE_CASE) to "$1 will",
            Regex("""\b(\w+)'d\b""", RegexOption.IGNORE_CASE) to "$1 would",
            Regex("""\b(\w+)'m\b""", RegexOption.IGNORE_CASE) to "$1 am",
            Regex("""\bit's\b""", RegexOption.IGNORE_CASE) to "it is",
        )
        for ((pattern, replacement) in contractions) {
            text = text.replace(pattern, replacement)
        }

        text = text.replace(Regex("""\b(\d+)(st|nd|rd|th)\b""", RegexOption.IGNORE_CASE)) { match ->
            ordinalToWords(match.groupValues[1].toLongOrNull() ?: return@replace match.value)
        }
        text = text.replace(Regex("""(?<![A-Za-z])-?[\d,]+(?:\.\d+)?""")) { match ->
            val clean = match.value.replace(",", "")
            if (clean.contains('.')) decimalToWords(clean) else {
                clean.toLongOrNull()?.let(::numberToWords) ?: match.value
            }
        }

        return text
            .lowercase(Locale.US)
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun cleanPhonemes(phonemes: String): LongArray {
        val spaced = tokenizer.findAll(phonemes)
            .map { it.value }
            .joinToString(" ")
        val ids = ArrayList<Long>(spaced.length + 3)
        ids += 0L
        for (ch in spaced) {
            symbolToId[ch]?.let(ids::add)
        }
        ids += 10L
        ids += 0L
        return ids.toLongArray()
    }

    private fun decimalToWords(value: String): String {
        val negative = value.startsWith('-')
        val unsigned = value.removePrefix("-")
        val parts = unsigned.split('.', limit = 2)
        val whole = parts.firstOrNull()?.toLongOrNull() ?: 0L
        val decimal = parts.getOrNull(1).orEmpty()
        val digits = mapOf(
            '0' to "zero", '1' to "one", '2' to "two", '3' to "three", '4' to "four",
            '5' to "five", '6' to "six", '7' to "seven", '8' to "eight", '9' to "nine",
        )
        val spoken = buildString {
            append(numberToWords(whole))
            if (decimal.isNotEmpty()) {
                append(" point ")
                append(decimal.mapNotNull(digits::get).joinToString(" "))
            }
        }
        return if (negative) "negative " + spoken else spoken
    }

    private fun ordinalToWords(value: Long): String {
        if (value <= 0) return numberToWords(value)
        val cardinal = numberToWords(value)
        val irregular = mapOf(
            "one" to "first",
            "two" to "second",
            "three" to "third",
            "five" to "fifth",
            "eight" to "eighth",
            "nine" to "ninth",
            "twelve" to "twelfth",
        )
        val splitAt = maxOf(cardinal.lastIndexOf(' '), cardinal.lastIndexOf('-'))
        val prefix = if (splitAt >= 0) cardinal.substring(0, splitAt + 1) else ""
        val last = if (splitAt >= 0) cardinal.substring(splitAt + 1) else cardinal
        val ordinal = irregular[last] ?: when {
            last.endsWith("y") -> last.dropLast(1) + "ieth"
            last.endsWith("e") -> last.dropLast(1) + "th"
            last.endsWith("t") -> last + "h"
            else -> last + "th"
        }
        return prefix + ordinal
    }

    private fun numberToWords(value: Long): String {
        if (value == 0L) return "zero"
        if (value < 0L) return "negative " + numberToWords(-value)

        val ones = arrayOf(
            "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen",
        )
        val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        val scales = arrayOf("", "thousand", "million", "billion", "trillion")

        fun underThousand(number: Int): String {
            val parts = ArrayList<String>()
            val hundreds = number / 100
            val remainder = number % 100
            if (hundreds > 0) parts += ones[hundreds] + " hundred"
            if (remainder in 1..19) {
                parts += ones[remainder]
            } else if (remainder >= 20) {
                val base = tens[remainder / 10]
                val tail = ones[remainder % 10]
                parts += if (tail.isNotEmpty()) base + "-" + tail else base
            }
            return parts.joinToString(" ")
        }

        var remaining = value
        var scale = 0
        val groups = ArrayList<String>()
        while (remaining > 0 && scale < scales.size) {
            val chunk = (remaining % 1000L).toInt()
            if (chunk != 0) {
                val words = underThousand(chunk)
                groups += if (scales[scale].isEmpty()) words else words + " " + scales[scale]
            }
            remaining /= 1000L
            scale++
        }
        return groups.asReversed().joinToString(" ")
    }
}
