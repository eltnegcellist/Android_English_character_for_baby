package com.eltnegcellist.emma.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveEndpointDetectorTest {
    private val sampleRate = 16_000
    private val frameSamples = 1_600 // 100 ms

    @Test
    fun shortSpeechEndsAfterAdaptiveSilence() {
        val detector = AdaptiveEndpointDetector(sampleRate)
        repeat(5) { detector.process(frame(0), frameSamples) }

        var started = false
        repeat(8) {
            if (detector.process(frame(5_000), frameSamples) is VoiceActivityEvent.SpeechStarted) started = true
        }
        assertTrue(started)

        var endpoint = false
        repeat(10) {
            if (detector.process(frame(0), frameSamples) is VoiceActivityEvent.Endpoint) endpoint = true
        }
        assertTrue(endpoint)
    }

    @Test
    fun longSpeechDoesNotEndOnBriefPause() {
        val detector = AdaptiveEndpointDetector(sampleRate)
        repeat(3) { detector.process(frame(0), frameSamples) }
        repeat(40) { detector.process(frame(5_000), frameSamples) }

        var endpoint = false
        repeat(8) {
            if (detector.process(frame(0), frameSamples) is VoiceActivityEvent.Endpoint) endpoint = true
        }
        assertFalse(endpoint)

        repeat(8) {
            if (detector.process(frame(0), frameSamples) is VoiceActivityEvent.Endpoint) endpoint = true
        }
        assertTrue(endpoint)
    }

    @Test
    fun transientNoiseDoesNotLeakIntoNextTurn() {
        val detector = AdaptiveEndpointDetector(sampleRate)

        var endpoint = false
        repeat(2) { detector.process(frame(5_000), frameSamples) }
        repeat(10) {
            if (detector.process(frame(0), frameSamples) is VoiceActivityEvent.Endpoint) endpoint = true
        }
        assertFalse(endpoint)

        var startedAgain = false
        repeat(6) {
            if (detector.process(frame(5_000), frameSamples) is VoiceActivityEvent.SpeechStarted) startedAgain = true
        }
        assertTrue(startedAgain)
    }

    @Test
    fun softerSpeechAfterLoudSpeechDoesNotEndMidSentence() {
        val detector = AdaptiveEndpointDetector(sampleRate)
        repeat(5) { detector.process(frame(0), frameSamples) }

        var endpoint = false
        repeat(15) {
            if (detector.process(frame(8_000), frameSamples) is VoiceActivityEvent.Endpoint) endpoint = true
        }

        // This level is much quieter than the opening phrase but still clearly
        // above the absolute speech threshold. It must remain speech even when
        // sustained for several seconds.
        repeat(30) {
            if (detector.process(frame(2_600), frameSamples) is VoiceActivityEvent.Endpoint) endpoint = true
        }
        assertFalse(endpoint)

        repeat(15) {
            if (detector.process(frame(8_000), frameSamples) is VoiceActivityEvent.Endpoint) endpoint = true
        }
        assertFalse(endpoint)
    }

    @Test
    fun continuousSpeechLongerThanTwentySecondsIsNeverForceCut() {
        val detector = AdaptiveEndpointDetector(sampleRate)
        repeat(5) { detector.process(frame(0), frameSamples) }

        var endpoint = false
        repeat(220) {
            if (detector.process(frame(5_000), frameSamples) is VoiceActivityEvent.Endpoint) endpoint = true
        }
        assertFalse(endpoint)
    }

    private fun frame(value: Int): ShortArray = ShortArray(frameSamples) { value.toShort() }
}
