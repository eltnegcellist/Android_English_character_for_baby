package com.eltnegcellist.emma.ai

import java.util.ArrayDeque

internal data class FullTopicContext(
    val currentExplicitTopic: String?,
    val recentConcreteTopics: List<String>,
    val carriedTopic: String?,
) {
    val hasConcreteContext: Boolean
        get() = currentExplicitTopic != null || carriedTopic != null

    fun toPromptBlock(): String {
        if (!hasConcreteContext) return ""

        val recent = if (recentConcreteTopics.isEmpty()) {
            "(none)"
        } else {
            recentConcreteTopics.joinToString(" -> ")
        }
        val current = currentExplicitTopic ?: "(none)"
        val carried = carriedTopic ?: "(none)"
        return """
            Lightweight topic tracker (secondary context):
            - Current explicit concrete topic: $current
            - Concrete topics detected within the previous 2 parent turns: $recent
            - Suggested carried topic: $carried
            - Generic / no-topic turns are intentionally omitted and must not be treated as a topic.
            - Use this as strong contextual evidence only when a concrete topic is shown.
            - If the current transcript clearly establishes a different topic, the current transcript wins immediately.
            - If the current transcript is vague and a carried topic exists, prefer continuing that concrete topic instead of drifting to a generic reply.
        """.trimIndent()
    }
}

internal class FullTopicTracker(
    private val detector: LiteResponseEngine = LiteResponseEngine(),
) {
    private data class TurnTopic(val concreteTopic: String?)

    // These are PREVIOUS turns only. The current turn is appended after its context
    // has been calculated, matching Full's previous-turn context semantics.
    private val recentTurns = ArrayDeque<TurnTopic>()

    fun observe(transcript: String): FullTopicContext {
        val detection = detector.detectConcreteTopic(transcript)
            ?.takeIf { it.strongEvidence }
        val explicit = detection?.scene

        val previousConcrete = recentTurns.mapNotNull { it.concreteTopic }
        val carried = explicit ?: previousConcrete.lastOrNull()

        val context = FullTopicContext(
            currentExplicitTopic = explicit,
            recentConcreteTopics = previousConcrete,
            carriedTopic = carried,
        )

        // Generic/no-topic turns still age the two-turn window, but are never
        // exposed to Gemma as topics.
        recentTurns.addLast(TurnTopic(explicit))
        while (recentTurns.size > MAX_PREVIOUS_TURNS) {
            recentTurns.removeFirst()
        }

        return context
    }

    fun reset() {
        recentTurns.clear()
    }

    companion object {
        private const val MAX_PREVIOUS_TURNS = 2
    }
}
