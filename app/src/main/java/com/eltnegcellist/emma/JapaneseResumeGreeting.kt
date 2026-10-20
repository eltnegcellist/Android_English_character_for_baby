package com.eltnegcellist.emma

import kotlin.random.Random

/** Fixed, short Japanese greetings for a resumed conversation, not English reply content. */
internal object JapaneseResumeGreeting {
    val options = listOf(
        "おかえり！",
        "また会えたね！",
        "やっほー、また一緒だね！",
    )

    fun choose(previous: String? = null, nextIndex: (Int) -> Int = { Random.nextInt(it) }): String {
        val choices = options.filter { it != previous }
        return choices[nextIndex(choices.size).coerceIn(0, choices.lastIndex)]
    }
}
