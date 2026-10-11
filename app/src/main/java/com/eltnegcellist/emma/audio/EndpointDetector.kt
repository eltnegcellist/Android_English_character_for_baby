package com.eltnegcellist.emma.audio

internal interface EndpointDetector : AutoCloseable {
    fun process(samples: ShortArray, count: Int): List<VoiceActivityEvent>
    fun reset()
    override fun close() {}
}

internal class LegacyEndpointDetector(sampleRate: Int) : EndpointDetector {
    private val delegate = AdaptiveEndpointDetector(sampleRate)
    override fun process(samples: ShortArray, count: Int) = listOfNotNull(delegate.process(samples, count))
    override fun reset() = delegate.reset()
}

enum class EndpointMode(val savedValue: String, val label: String) {
    SILERO("silero", "声で判定（Silero）"),
    LEGACY("legacy", "音量で判定（従来）");
    companion object {
        fun fromSaved(value: String?) = if (value == LEGACY.savedValue) LEGACY else SILERO
    }
}
