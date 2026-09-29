package com.eltnegcellist.emma.audio

import kotlin.math.max
import kotlin.math.sqrt

sealed interface VoiceActivityEvent {
    data object SpeechStarted : VoiceActivityEvent
    data class Endpoint(val speechMillis: Long, val silenceMillis: Long) : VoiceActivityEvent
}

/**
 * Small on-device VAD/endpoint detector tuned for parent-to-baby speech.
 * It adapts to room noise and uses a longer endpoint after longer utterances.
 */
internal class AdaptiveEndpointDetector(
    private val sampleRate: Int,
) {
    private var noiseFloor = 0.0045f
    private var inSpeech = false
    private var voicedMillis = 0L
    private var silenceMillis = 0L
    private var onsetMillis = 0L

    fun reset() {
        inSpeech = false
        voicedMillis = 0L
        silenceMillis = 0L
        onsetMillis = 0L
    }

    fun process(samples: ShortArray, count: Int): VoiceActivityEvent? {
        if (count <= 0) return null
        val frameMillis = max(1L, count.toLong() * 1000L / sampleRate)
        val rms = rms(samples, count)
        val threshold = max(MIN_SPEECH_RMS, noiseFloor * NOISE_MULTIPLIER)
        val voiced = rms >= threshold

        if (!inSpeech) {
            if (!voiced) {
                noiseFloor = (noiseFloor * 0.96f + rms * 0.04f).coerceIn(MIN_NOISE_FLOOR, MAX_NOISE_FLOOR)
                onsetMillis = 0L
                return null
            }
            onsetMillis += frameMillis
            if (onsetMillis >= MIN_ONSET_MS) {
                inSpeech = true
                voicedMillis = onsetMillis
                silenceMillis = 0L
                onsetMillis = 0L
                return VoiceActivityEvent.SpeechStarted
            }
            return null
        }

        if (voiced) {
            voicedMillis += frameMillis
            silenceMillis = 0L
            return null
        }

        silenceMillis += frameMillis

        // A cough, tap, or other short transient may pass the onset threshold but is not a turn.
        // Release the provisional speech state instead of carrying it into the next real utterance.
        if (voicedMillis < MIN_UTTERANCE_MS && silenceMillis >= FALSE_START_SILENCE_MS) {
            reset()
            return null
        }

        val endpointMillis = when {
            voicedMillis < 1_000L -> 850L
            voicedMillis < 3_000L -> 1_100L
            voicedMillis < 7_000L -> 1_400L
            else -> 1_750L
        }

        if (voicedMillis >= MIN_UTTERANCE_MS && silenceMillis >= endpointMillis) {
            val event = VoiceActivityEvent.Endpoint(voicedMillis, silenceMillis)
            reset()
            return event
        }
        return null
    }

    private fun rms(samples: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) {
            val normalized = samples[i].toDouble() / Short.MAX_VALUE.toDouble()
            sum += normalized * normalized
        }
        return sqrt(sum / count).toFloat()
    }

    private companion object {
        const val MIN_ONSET_MS = 160L
        const val MIN_UTTERANCE_MS = 450L
        const val FALSE_START_SILENCE_MS = 700L
        const val MIN_SPEECH_RMS = 0.010f
        const val NOISE_MULTIPLIER = 2.8f
        const val MIN_NOISE_FLOOR = 0.0015f
        const val MAX_NOISE_FLOOR = 0.025f
    }
}
