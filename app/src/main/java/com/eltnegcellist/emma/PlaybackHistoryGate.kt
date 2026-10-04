package com.eltnegcellist.emma

/** Only the first accepted audio start can commit a conversation candidate. */
internal class PlaybackHistoryGate<T> {
    private var candidate: T? = null
    fun offer(value: T) { candidate = value }
    fun cancel() { candidate = null }
    fun started(enabled: Boolean): T? {
        val result = candidate
        candidate = null
        return if (enabled) result else null
    }
}
internal fun nextPlayPhrase(phrases: List<String>, previous: String?, random: kotlin.random.Random = kotlin.random.Random.Default): String =
    phrases.filter { it != previous }.random(random)
