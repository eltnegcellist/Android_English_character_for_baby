package com.eltnegcellist.emma

import android.content.Context
import com.eltnegcellist.emma.tts.PrerecordedVoiceStore
import org.json.JSONObject
import kotlin.random.Random

/**
 * Pick exactly one prerecorded English sentence from the existing "greeting"
 * category in the shared Play topics. No platform speech synthesis is used.
 */
internal object EnglishResumeGreeting {
    val recordedCandidates = listOf(
        "Hi there!",
        "Hello again!",
        "Hello, little one!",
        "Hello, hello!",
    )

    fun candidates(context: Context): List<String> = runCatching {
        val source = context.assets.open("play-topics.json").bufferedReader().use { it.readText() }
        val topics = JSONObject(source).getJSONArray("topics")
        val category = (0 until topics.length())
            .map { topics.getJSONObject(it) }
            .firstOrNull { it.getString("id") == "greeting" }
            ?: return@runCatching emptyList()
        val phrases = category.getJSONArray("phrases")
        val sentences = (0 until phrases.length()).flatMap { i ->
            phrases.getString(i).split(Regex("(?<=[.!?])\\s+"))
                .map { it.trim() }.filter { it.isNotEmpty() }
        }.toSet()
        recordedCandidates.filter { it in sentences && PrerecordedVoiceStore.hasText(context, it) }
    }.getOrDefault(emptyList())

    fun pick(
        options: List<String>,
        previous: String? = null,
        nextIndex: (Int) -> Int = { Random.nextInt(it) },
    ): String? {
        if (options.isEmpty()) return null
        val alternatives = options.filter { it != previous }
        val pool = alternatives.ifEmpty { options }
        return pool[nextIndex(pool.size).coerceIn(0, pool.lastIndex)]
    }

    fun choose(context: Context, previous: String? = null): String? =
        pick(candidates(context), previous)
}
