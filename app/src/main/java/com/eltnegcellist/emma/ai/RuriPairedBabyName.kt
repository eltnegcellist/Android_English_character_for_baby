package com.eltnegcellist.emma.ai

/** Explicit, reviewed name versions of select paired replies.
 * Default/base English is preserved exactly when the name is unset or on cooldown.
 */
internal object RuriPairedBabyName {
    val templates: Map<String, String> = mapOf(
        "Milk time, sweetheart! You're so lovely. I love you." to "Milk time, {name}! You're so lovely. I love you.",
        "You drank your milk! You did it! Well done, sweetheart." to "You drank your milk! You did it! Well done, {name}.",
        "Diaper change, all done! We did it! Well done, sweetheart." to "Diaper change, all done! We did it! Well done, {name}.",
        "Time for a change. I'll be gentle. Ready, little one?" to "Time for a change. I'll be gentle. Ready, {name}?",
        "Sweet dreams, little one. You're so loved. I'm beside you." to "Sweet dreams, {name}. You're so loved. I'm beside you.",
        "Bath time, sweetheart! You're so lovely. I'm right here." to "Bath time, {name}! You're so lovely. I'm right here.",
        "You've finished your food. Well done, sweetheart. Let's rest now." to "You've finished your food. Well done, {name}. Let's rest now.",
        "Such careful little sips! You worked so well. High five, sweetheart!" to "Such careful little sips! You worked so well. High five, {name}!",
        "Shall we roll together? Here comes your ball. Catch it, sweetheart!" to "Shall we roll together? Here comes your ball. Catch it, {name}!",
        "Good morning, bright eyes! What a lovely hello. Welcome back, sweetheart." to "Good morning, bright eyes! What a lovely hello. Welcome back, {name}.",
        "You're awake, sweetheart! What a lovely surprise. I love you." to "You're awake, {name}! What a lovely surprise. I love you.",
        "I'm close by, darling. The night is quiet. Rest easy, sweetheart." to "I'm close by, darling. The night is quiet. Rest easy, {name}."
    )
    private val word=Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
    private val sentenceSplit=Regex("(?<=[.!?])\\s+")
    private val allowedName=Regex("[A-Za-z'’ -]{1,45}")
    private fun count(value: String) = word.findAll(value).count()
    fun insert(original: String, name: String): String {
        if (name.isBlank() || !allowedName.matches(name)) return original
        val template=templates[original] ?: return original
        val rendered=template.replace("{name}", name)
        val parts=rendered.split(sentenceSplit)
        if (parts.size != 3 || count(rendered) !in 6..12 || parts.any { count(it)>4 }) return original
        return rendered
    }
}
