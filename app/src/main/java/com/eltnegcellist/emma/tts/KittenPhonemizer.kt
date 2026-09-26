package com.eltnegcellist.emma.tts

import java.io.File

internal class KittenPhonemizer(
    cmuFile: File,
) {
    private val cmu: Map<String, List<List<String>>> = loadCmu(cmuFile)

    fun phonemize(text: String, japaneseNameHint: String = ""): String {
        val processed = text.lowercase().replace('’', '\'')
        val parts = TOKEN_REGEX.findAll(processed).map { it.value }.toList()
        val nameHints = normalizeNameHints(japaneseNameHint)
        val out = ArrayList<String>(parts.size)

        for (i in parts.indices) {
            val part = parts[i]
            when {
                part.firstOrNull()?.isLetter() == true -> out += lookup(part, parts, i, nameHints)
                part.length == 1 && PUNCTUATION.contains(part[0]) -> out += part
            }
        }
        return out.joinToString(" ")
    }

    private fun lookup(
        word: String,
        parts: List<String>,
        index: Int,
        nameHints: Set<String>,
    ): String {
        val normalized = word.lowercase().replace('’', '\'')
        val basePossessive = normalized.removeSuffix("'s")

        if (basePossessive in nameHints) {
            japaneseNameToIpa(basePossessive)?.let { base ->
                return if (normalized.endsWith("'s")) base + "z" else base
            }
        }

        CUSTOM_IPA[normalized]?.let { return it }

        val tries = listOf(normalized, normalized.replace("'", ""), basePossessive).distinct()
        for (candidate in tries) {
            val variants = cmu[candidate] ?: continue
            val selected = chooseHeteronym(candidate, variants, parts, index)
            return arpabetToIpa(selected)
        }

        return simpleG2p(normalized)
    }

    private fun chooseHeteronym(
        word: String,
        candidates: List<List<String>>,
        parts: List<String>,
        index: Int,
    ): List<String> {
        if (candidates.size < 2) return candidates.first()

        val prev = previousLexical(parts, index)
        val prev2 = previousLexical(parts, index, 2)
        val next = nextLexical(parts, index)
        val nearby = nearbyLexical(parts, index).toSet()

        return when (word) {
            "wind" -> {
                val nounish = prev in DETERMINERS ||
                    prev in setOf("strong", "cold", "warm", "gentle", "north", "south", "east", "west")
                val letUs = prev == "us" && prev2 == "let"
                val objectAhead = next in DETERMINERS
                val verbish = prev in AUX_BASE_FORM || letUs || objectAhead ||
                    next in setOf("up", "down", "back", "around")
                chooseByPhone(candidates, if (nounish && !verbish) "IH" else if (verbish) "AY" else "IH")
            }
            "read" -> {
                val past = prev in PERFECT_AUX || PAST_CUES.any { it in nearby }
                val base = prev in AUX_BASE_FORM
                chooseByPhone(candidates, if (past && !base) "EH" else "IY")
            }
            in STRESS_HETERONYMS -> {
                val verbish = prev in AUX_BASE_FORM ||
                    prev in setOf("i", "you", "we", "they", "he", "she", "it") ||
                    (prev == "not" && prev2 in AUX_BASE_FORM)
                val nounish = prev in DETERMINERS
                chooseStress(candidates, if (verbish && !nounish) StressMode.LATE else StressMode.EARLY)
            }
            "live" -> {
                val adjective = next in LIVE_ADJ_NOUNS || prev in DETERMINERS
                val verb = prev in AUX_BASE_FORM || prev in setOf("i", "you", "we", "they", "he", "she")
                chooseByPhone(candidates, if (adjective && !verb) "AY" else "IH")
            }
            "lead" -> {
                val metalPrev = setOf("contain", "contains", "contained", "containing", "has", "have", "had", "with", "of", "from", "made")
                val metal = next in LEAD_METAL_NOUNS || prev in setOf("metal", "poisoning") || prev in metalPrev
                val verb = prev in AUX_BASE_FORM || prev in setOf("i", "you", "we", "they", "he", "she")
                chooseByPhone(candidates, if (metal && !verb) "EH" else "IY")
            }
            "close" -> {
                val niceAndClose = prev == "and" && prev2 == "nice"
                val adjective = next in CLOSE_ADJ_NOUNS || prev in setOf("very", "too", "so") ||
                    next == "to" || niceAndClose
                val verb = prev in AUX_BASE_FORM ||
                    prev in setOf("i", "you", "we", "they", "he", "she") ||
                    prev == "please" || prev == "open"
                chooseByPhone(candidates, if (adjective && !verb) "S" else "Z")
            }
            "use" -> {
                val nounish = prev in DETERMINERS || prev in setOf("in", "for", "of")
                val verbish = prev in AUX_BASE_FORM || prev in setOf("i", "you", "we", "they", "he", "she")
                chooseByPhone(candidates, if (nounish && !verbish) "S" else "Z")
            }
            else -> candidates.first()
        }
    }

    private fun arpabetToIpa(phones: List<String>): String = buildString {
        for (raw in phones) {
            val match = PHONE_REGEX.matchEntire(raw) ?: continue
            val base = match.groupValues[1]
            val stress = match.groupValues[2]
            var phoneme = PHONE[base] ?: continue
            if (base == "AH" && stress == "0") phoneme = "ə"
            if (base == "ER" && stress == "0") phoneme = "ɚ"
            if (base in VOWELS) {
                phoneme = when (stress) {
                    "1" -> "ˈ$phoneme"
                    "2" -> "ˌ$phoneme"
                    else -> phoneme
                }
            }
            append(phoneme)
        }
    }

    private fun chooseByPhone(candidates: List<List<String>>, wanted: String): List<String> =
        candidates.firstOrNull { phones -> phones.any { it.replace(Regex("[012]$"), "") == wanted } }
            ?: candidates.first()

    private fun chooseStress(candidates: List<List<String>>, mode: StressMode): List<String> {
        val sorted = candidates.sortedBy { phones -> phones.indexOfFirst { it.endsWith("1") }.let { if (it < 0) Int.MAX_VALUE else it } }
        return if (mode == StressMode.LATE) sorted.last() else sorted.first()
    }

    private fun previousLexical(parts: List<String>, index: Int, n: Int = 1): String {
        var count = 0
        for (i in index - 1 downTo 0) {
            val part = parts[i]
            if (part.firstOrNull()?.isLetter() == true) {
                count++
                if (count == n) return part.lowercase()
            }
        }
        return ""
    }

    private fun nextLexical(parts: List<String>, index: Int, n: Int = 1): String {
        var count = 0
        for (i in index + 1 until parts.size) {
            val part = parts[i]
            if (part.firstOrNull()?.isLetter() == true) {
                count++
                if (count == n) return part.lowercase()
            }
        }
        return ""
    }

    private fun nearbyLexical(parts: List<String>, index: Int, radius: Int = 5): List<String> =
        (maxOf(0, index - radius)..minOf(parts.lastIndex, index + radius))
            .filter { it != index }
            .mapNotNull { parts[it].takeIf { p -> p.firstOrNull()?.isLetter() == true }?.lowercase() }

    private fun simpleG2p(word: String): String {
        val rules = listOf(
            "tion" to "ʃən", "sh" to "ʃ", "ch" to "ʧ", "th" to "θ", "ph" to "f",
            "ng" to "ŋ", "oo" to "u", "ee" to "i", "ea" to "i",
        )
        var i = 0
        return buildString {
            while (i < word.length) {
                var matched = false
                for ((source, target) in rules) {
                    if (word.startsWith(source, i)) {
                        append(target)
                        i += source.length
                        matched = true
                        break
                    }
                }
                if (matched) continue
                SIMPLE_LETTER[word[i]]?.let(::append)
                i++
            }
        }
    }

    private fun japaneseNameToIpa(raw: String): String? {
        val word = raw.lowercase().removeSuffix("'s")
        val digraphs = listOf(
            "kyo" to "kjo", "kyu" to "kju", "kya" to "kja",
            "sho" to "ʃo", "shu" to "ʃu", "sha" to "ʃa",
            "cho" to "ʧo", "chu" to "ʧu", "cha" to "ʧa",
            "nyo" to "njo", "nyu" to "nju", "nya" to "nja",
            "hyo" to "hjo", "hyu" to "hju", "hya" to "hja",
            "myo" to "mjo", "myu" to "mju", "mya" to "mja",
            "ryo" to "ɹjo", "ryu" to "ɹju", "rya" to "ɹja",
            "gyo" to "ɡjo", "gyu" to "ɡju", "gya" to "ɡja",
            "jo" to "ʤo", "ju" to "ʤu", "ja" to "ʤa",
            "shi" to "ʃi", "chi" to "ʧi", "tsu" to "tsu", "fu" to "fu",
        )
        val consonants = mapOf(
            'k' to "k", 's' to "s", 't' to "t", 'n' to "n", 'h' to "h", 'm' to "m",
            'y' to "j", 'r' to "ɹ", 'w' to "w", 'g' to "ɡ", 'z' to "z", 'd' to "d",
            'b' to "b", 'p' to "p", 'j' to "ʤ", 'f' to "f", 'v' to "v",
        )
        val vowels = mapOf('a' to "ɑ", 'i' to "i", 'u' to "u", 'e' to "e", 'o' to "o")

        var i = 0
        val result = StringBuilder()
        while (i < word.length) {
            val digraph = digraphs.firstOrNull { word.startsWith(it.first, i) }
            if (digraph != null) {
                result.append(digraph.second)
                i += digraph.first.length
                continue
            }

            val char = word[i]
            val next = word.getOrNull(i + 1)
            when {
                vowels.containsKey(char) -> result.append(vowels.getValue(char))
                char == 'n' && (next == null || (!vowels.containsKey(next) && next != 'y')) -> result.append("n")
                consonants.containsKey(char) -> result.append(consonants.getValue(char))
                char == '-' || char == '\'' -> Unit
                else -> return null
            }
            i++
        }
        return result.takeIf { it.isNotEmpty() }?.let { "ˈ$it" }
    }

    private fun normalizeNameHints(nameHint: String): Set<String> {
        if (nameHint.isBlank()) return emptySet()
        val base = nameHint.trim().lowercase().replace(Regex("(?:-|\\s)?chan$"), "")
        return base.split(Regex("[\\s-]+"))
            .filter { it.matches(Regex("[a-z]+(?:'[a-z]+)?")) }
            .toSet()
    }

    private fun loadCmu(file: File): Map<String, List<List<String>>> {
        require(file.isFile) { "CMUDictが見つかりません。" }
        val map = HashMap<String, MutableList<List<String>>>(140_000)
        file.bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.isBlank() || line.startsWith(";;;")) return@forEach
                val split = line.indexOf(' ')
                if (split <= 0) return@forEach
                val rawKey = line.substring(0, split).lowercase()
                val word = rawKey.replace(Regex("\\(\\d+\\)$"), "")
                val phones = line.substring(split).trim().split(Regex("\\s+"))
                map.getOrPut(word) { mutableListOf() }.add(phones)
            }
        }
        require(map.size > 100_000) { "CMUDictの読み込み結果が不完全です。" }
        return map
    }

    private enum class StressMode { EARLY, LATE }

    private companion object {
        val TOKEN_REGEX = Regex("[a-z]+(?:'[a-z]+)?|[;:,.!?¡¿—…\\\"«»]|\\S")
        val PHONE_REGEX = Regex("^([A-Z]+)([012])?$")
        val PUNCTUATION = setOf(';', ':', ',', '.', '!', '?', '¡', '¿', '—', '…', '"', '«', '»')

        val PHONE = mapOf(
            "AA" to "ɑ", "AE" to "æ", "AH" to "ʌ", "AO" to "ɔ", "AW" to "aʊ", "AY" to "aɪ",
            "B" to "b", "CH" to "ʧ", "D" to "d", "DH" to "ð", "EH" to "ɛ", "ER" to "ɝ", "EY" to "eɪ",
            "F" to "f", "G" to "ɡ", "HH" to "h", "IH" to "ɪ", "IY" to "i", "JH" to "ʤ", "K" to "k",
            "L" to "l", "M" to "m", "N" to "n", "NG" to "ŋ", "OW" to "oʊ", "OY" to "ɔɪ", "P" to "p",
            "R" to "ɹ", "S" to "s", "SH" to "ʃ", "T" to "t", "TH" to "θ", "UH" to "ʊ", "UW" to "u",
            "V" to "v", "W" to "w", "Y" to "j", "Z" to "z", "ZH" to "ʒ",
            "AX" to "ə", "AXR" to "ɚ", "IX" to "ᵻ", "UX" to "ʊ", "DX" to "ɾ",
            "EL" to "l", "EM" to "m", "EN" to "n", "NX" to "ŋ",
        )
        val VOWELS = setOf("AA","AE","AH","AO","AW","AY","EH","ER","EY","IH","IY","OW","OY","UH","UW","AX","AXR","IX","UX")
        val SIMPLE_LETTER = mapOf(
            'a' to "æ",'b' to "b",'c' to "k",'d' to "d",'e' to "ɛ",'f' to "f",'g' to "ɡ",
            'h' to "h",'i' to "ɪ",'j' to "ʤ",'k' to "k",'l' to "l",'m' to "m",'n' to "n",
            'o' to "ɑ",'p' to "p",'q' to "k",'r' to "ɹ",'s' to "s",'t' to "t",'u' to "ʌ",
            'v' to "v",'w' to "w",'x' to "ks",'y' to "j",'z' to "z",
        )

        val AUX_BASE_FORM = setOf("will","shall","can","could","would","should","may","might","must","do","does","did","to","please")
        val PERFECT_AUX = setOf("have","has","had")
        val DETERMINERS = setOf("a","an","the","this","that","these","those","my","your","his","her","our","their","another","each","every","one","new")
        val PAST_CUES = setOf("yesterday","ago","earlier","previously","last")
        val LIVE_ADJ_NOUNS = setOf("music","show","broadcast","stream","concert","performance","event","television","tv","coverage","audience","camera","video","feed","bird")
        val LEAD_METAL_NOUNS = setOf("pipe","pipes","paint","metal","poisoning","ore","battery","batteries","shot","weight","weights")
        val CLOSE_ADJ_NOUNS = setOf("friend","friends","relationship","relationships","call","calls","race","races","match","matches","look","contact")
        val STRESS_HETERONYMS = setOf(
            "record","present","object","project","permit","produce","progress","rebel","refuse","subject","suspect",
            "conduct","contract","contrast","convert","digest","discount","escort","export","extract","import",
            "increase","insult","invalid","perfect","protest","reject","survey","transfer","transport",
        )
        val CUSTOM_IPA = mapOf(
            "peekaboo" to "pikəbˈu",
            "oo" to "ˈu",
            "pitter" to "pˈɪtɚ",
            "mmm" to "m",
            "bassinet" to "bˌæsɪnˈɛt",
            "breastmilk" to "bɹˈɛstmˌɪlk",
            "playtime" to "plˈeɪtˌaɪm",
            "tummytime" to "tˈʌmitˌaɪm",
        )
    }
}
