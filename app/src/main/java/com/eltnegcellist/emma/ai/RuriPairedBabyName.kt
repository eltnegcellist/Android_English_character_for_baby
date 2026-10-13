package com.eltnegcellist.emma.ai

/**
 * Insert the already-configured spoken baby's name into a three-sentence
 * Ruri combined reply only when grammar and all length caps stay valid.
 * Never modifies the shared English template IDs or the Ruri prediction.
 */
internal object RuriPairedBabyName {
    const val MIN_GAP = 8
    private val vocative = Regex(",\\s*(?:sweetheart|baby|darling|little one)(?=[.!?])", RegexOption.IGNORE_CASE)
    private val split = Regex("(?<=[.!?])\\s+")
    private val word = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
    private val allowedName = Regex("[A-Za-z'’ -]{1,45}")
    private fun count(value: String): Int = word.findAll(value).count()
    private fun valid(value: String): Boolean {
        val parts = value.split(split)
        return parts.size == 3 && count(value) in 6..12 && parts.all { count(it) <= 4 }
    }

    fun insert(english: String, name: String): String {
        if (name.isBlank() || !allowedName.matches(name)) return english
        val parts = english.trim().split(split)
        if (parts.size != 3) return english
        for (index in 0..2) {
            if (!vocative.containsMatchIn(parts[index])) continue
            val revised = parts.toMutableList()
            revised[index] = parts[index].replaceFirst(vocative, ", $name")
            val candidate = revised.joinToString(" ")
            if (valid(candidate)) return candidate
        }
        if (count(english) + count(name) > 12) return english
        for (index in listOf(2, 0, 1)) {
            val part = parts[index]
            if (!part.endsWith(".") && !part.endsWith("!")) continue
            if (count(part) + count(name) > 4) continue
            val revised = parts.toMutableList()
            revised[index] = part.dropLast(1) + ", " + name + part.last()
            val candidate = revised.joinToString(" ")
            if (valid(candidate)) return candidate
        }
        return english
    }
}
