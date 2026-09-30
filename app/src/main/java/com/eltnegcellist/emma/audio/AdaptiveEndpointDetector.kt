package com.eltnegcellist.emma.audio

import kotlin.math.max
import kotlin.math.sqrt

sealed interface VoiceActivityEvent {
    data object SpeechStarted : VoiceActivityEvent
    data class Endpoint(val speechMillis: Long, val silenceMillis: Long) : VoiceActivityEvent
}

/**
 * Small on-device VAD/endpoint detector tuned for parent-to-baby speech.
 *
 * The detector learns the room noise before speech and, once speech has started,
 * also treats a sustained large drop from the recent speech level as a likely
 * return to background noise. This prevents steady café/TV/car noise from
 * keeping a turn open forever while preserving the existing adaptive endpoint
 * timings for natural pauses.
 */
internal class AdaptiveEndpointDetector(
    private val sampleRate: Int,
) {
    private var noiseFloor = 0.0045f
    private var inSpeech = false
    private var voicedMillis = 0L
    private var silenceMillis = 0L
    private var onsetMillis = 0L
    private var onsetPeakRms = 0f
    private var recentSpeechLevel = 0f

    fun reset() {
        inSpeech = false
        voicedMillis = 0L
        silenceMillis = 0L
        onsetMillis = 0L
        onsetPeakRms = 0f
        recentSpeechLevel = 0f
    }

    fun process(samples: ShortArray, count: Int): VoiceActivityEvent? {
        if (count <= 0) return null
        val frameMillis = max(1L, count.toLong() * 1000L / sampleRate)
        val rms = rms(samples, count)
        val threshold = max(MIN_SPEECH_RMS, noiseFloor * NOISE_MULTIPLIER)
        val absoluteVoiced = rms >= threshold

        if (!inSpeech) {
            if (!absoluteVoiced) {
                adaptNoiseFloor(rms, IDLE_NOISE_ALPHA)
                onsetMillis = 0L
                onsetPeakRms = 0f
                return null
            }

            onsetMillis += frameMillis
            onsetPeakRms = max(onsetPeakRms, rms)
            if (onsetMillis >= MIN_ONSET_MS) {
                inSpeech = true
                voicedMillis = onsetMillis
                silenceMillis = 0L
                recentSpeechLevel = max(onsetPeakRms, rms)
                onsetMillis = 0L
                onsetPeakRms = 0f
                return VoiceActivityEvent.SpeechStarted
            }
            return null
        }

        val relativeQuiet =
            absoluteVoiced &&
                recentSpeechLevel >= threshold * MIN_RELATIVE_REFERENCE_MULTIPLIER &&
                rms <= recentSpeechLevel * RELATIVE_QUIET_RATIO

        if (absoluteVoiced && !relativeQuiet) {
            voicedMillis += frameMillis
            silenceMillis = 0L
            recentSpeechLevel = max(rms, recentSpeechLevel * RECENT_SPEECH_DECAY)
            return null
        }

        // Either the frame is below the absolute speech threshold, or it is
        // still loud in absolute terms but much quieter than the parent's
        // recent speech. The latter is common in cafés, cars, or near a TV:
        // adapt the room floor upward while counting the normal endpoint pause.
        if (relativeQuiet) {
            adaptNoiseFloor(rms, IN_SPEECH_NOISE_ALPHA)
        } else if (!absoluteVoiced) {
            adaptNoiseFloor(rms, QUIET_NOISE_ALPHA)
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

    private fun adaptNoiseFloor(rms: Float, alpha: Float) {
        noiseFloor =
            (noiseFloor * (1f - alpha) + rms * alpha)
                .coerceIn(MIN_NOISE_FLOOR, MAX_NOISE_FLOOR)
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

        const val IDLE_NOISE_ALPHA = 0.04f
        const val QUIET_NOISE_ALPHA = 0.04f
        const val IN_SPEECH_NOISE_ALPHA = 0.08f
        const val MIN_NOISE_FLOOR = 0.0015f
        const val MAX_NOISE_FLOOR = 0.030f

        // A frame that is still above the absolute threshold can nevertheless
        // be background noise if it drops sharply from the established speech
        // level. 0.38 is deliberately conservative so normal softer syllables
        // do not immediately look like silence.
        const val RELATIVE_QUIET_RATIO = 0.38f
        const val MIN_RELATIVE_REFERENCE_MULTIPLIER = 1.6f
        const val RECENT_SPEECH_DECAY = 0.995f
    }
}
