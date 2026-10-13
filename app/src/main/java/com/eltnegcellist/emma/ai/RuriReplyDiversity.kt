package com.eltnegcellist.emma.ai

import java.util.ArrayDeque

/**
 * Stateful Ruri reply variety selector. Persists only template IDs and hashed
 * English trigrams, never transcripts, baby names or spoken output.
 * The Ruri classifier and its thresholds remain authoritative and unchanged.
 */
internal class RuriReplyDiversity {
    internal data class Pick(val text: String, val reason: String, val poolKey: String, val index: Int)
    private data class Cycle(val used: MutableSet<Int> = linkedSetOf(), var fallbackStep: Int = 0)
    private val history = ArrayDeque<String>()
    private val phraseHistory = ArrayDeque<List<String>>()
    private val cycles = linkedMapOf<String, Cycle>()

    private fun grams(text: String): List<String> {
        val words = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?").findAll(text.lowercase()).map { it.value }.toList()
        return (0 until (words.size - 2).coerceAtLeast(0))
            .map { Integer.toHexString(words.subList(it, it + 3).joinToString(" ").hashCode()) }
            .distinct()
    }

    fun restore(value: String?) {
        if (value.isNullOrBlank()) return
        val parts = value.split("\n")
        if (parts.size != 4 || parts[0] != "v1") return
        history.clear(); phraseHistory.clear(); cycles.clear()
        val idPattern = Regex("[a-z0-9_:|#-]+")
        parts[1].split(',').filter { it.matches(idPattern) }.takeLast(60).forEach(history::addLast)
        parts[2].split(';').filter { it.isNotBlank() }.takeLast(24).forEach { row ->
            phraseHistory.addLast(row.split('.').filter { it.matches(Regex("[0-9a-f]+")) }.take(32))
        }
        parts[3].split(';').filter { it.contains('=') }.takeLast(135).forEach { item ->
            val key = item.substringBefore('=')
            val fields = item.substringAfter('=').split('~')
            if (!key.matches(idPattern) || fields.size != 2) return@forEach
            val used = fields[0].split('.').mapNotNull(String::toIntOrNull).filter { it in 0..99 }.toMutableSet()
            val step = fields[1].toIntOrNull()?.coerceIn(0, 6) ?: 0
            cycles[key] = Cycle(used, step)
        }
    }
    fun snapshot(): String = listOf(
        "v1",
        history.joinToString(","),
        phraseHistory.joinToString(";") { it.joinToString(".") },
        cycles.entries.toList().takeLast(135).joinToString(";") { (key, cycle) ->
            key + "=" + cycle.used.joinToString(".") + "~" + cycle.fallbackStep
        }
    ).joinToString("\n")

    private fun pick(pool: String?, replies: List<String>?, excluded: Set<Int> = emptySet()): Pick? {
        if (pool == null || replies.isNullOrEmpty()) return null
        val candidates = replies.indices.filterNot { it in excluded }.ifEmpty { replies.indices.toList() }
        val recentGroups = phraseHistory.toList().takeLast(18)
        val picked = candidates.minWithOrNull(compareBy<Int> { i ->
            val id = "$pool#$i"
            val last = history.indexOfLast { it == id }
            val occurrences = history.count { it == id }
            val grams = grams(replies[i])
            val overlap = recentGroups.sumOf { row -> grams.count { it in row } }
            val openerCount = grams.firstOrNull()?.let { opener -> recentGroups.count { opener in it } } ?: 0
            (if (last < 0) 0 else 1200 + last * 4) + occurrences * 240 + overlap * 7 + openerCount * 35
        }.thenBy { it }) ?: return null
        val id = "$pool#$picked"
        history.addLast(id); while (history.size > 60) history.removeFirst()
        phraseHistory.addLast(grams(replies[picked])); while (phraseHistory.size > 24) phraseHistory.removeFirst()
        return Pick(replies[picked], "", pool, picked)
    }

    fun select(
        topic: String?, interaction: String?, pair: List<String>?,
        topicReplies: List<String>?, interactionReplies: List<String>?,
    ): Pick? {
        val topicKey = topic?.let { "topic:$it" }
        val interactionKey = interaction?.let { "interaction:$it" }
        if (topic != null && interaction != null && !pair.isNullOrEmpty()) {
            val key = "$topic|$interaction"
            val cycle = cycles.getOrPut(key) { Cycle() }
            cycle.used.removeAll { it !in pair.indices }
            if (cycle.used.size >= pair.size && cycle.fallbackStep < 6) {
                val step = cycle.fallbackStep++
                val primary = if (step % 2 == 0) pick(interactionKey, interactionReplies) else pick(topicKey, topicReplies)
                val secondary = if (primary == null) {
                    if (step % 2 == 0) pick(topicKey, topicReplies) else pick(interactionKey, interactionReplies)
                } else null
                val picked = primary ?: secondary
                if (picked != null) {
                    val viaInteraction = picked.poolKey.startsWith("interaction:")
                    return picked.copy(reason = if (viaInteraction) "diversity_interaction" else "diversity_topic")
                }
            }
            if (cycle.fallbackStep >= 6) { cycle.used.clear(); cycle.fallbackStep = 0 }
            val picked = pick("combined:$key", pair, cycle.used)
            if (picked != null) { cycle.used.add(picked.index); return picked.copy(reason="combined") }
        }
        if (topic != null) {
            val picked = pick(topicKey, topicReplies)
            if (picked != null) return picked.copy(reason=if (interaction == null) "topic_only" else "pair_missing")
        }
        if (interaction != null) {
            val picked = pick(interactionKey, interactionReplies)
            if (picked != null) return picked.copy(reason="interaction_only")
        }
        return null
    }
}
