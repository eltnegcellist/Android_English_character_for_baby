package com.eltnegcellist.emma.audio

import org.junit.Assert.*
import org.junit.Test

class SileroEndpointPolicyTest {
    @Test fun requiresContinuousOnsetAndKeepsQuietVoice() {
        val policy = SileroEndpointPolicy()
        repeat(4) { assertNull(policy.process(0.9f)) }
        assertEquals(VoiceActivityEvent.SpeechStarted, policy.process(0.9f))
        repeat(20) { assertNull(policy.process(0.36f)) }
        repeat(34) { assertNull(policy.process(0f)) }
        val endpoint = policy.process(0f) as VoiceActivityEvent.Endpoint
        assertEquals(1_120L, endpoint.silenceMillis)
    }
    @Test fun aNaturalPauseDoesNotEndAndVoiceResetsWaiting() {
        val policy = SileroEndpointPolicy()
        repeat(16) { policy.process(0.9f) }
        repeat(30) { assertNull(policy.process(0f)) }
        repeat(10) { assertNull(policy.process(0.4f)) }
        repeat(34) { assertNull(policy.process(0f)) }
        assertTrue(policy.process(0f) is VoiceActivityEvent.Endpoint)
    }
    @Test fun transientIsNotAnUtterance() {
        val policy = SileroEndpointPolicy()
        repeat(5) { policy.process(0.9f) }
        repeat(30) { assertNull(policy.process(0f)) }
        repeat(4) { assertNull(policy.process(0.9f)) }
        assertEquals(VoiceActivityEvent.SpeechStarted, policy.process(0.9f))
    }
    @Test fun resettingDropsThePreviousTurn() {
        val policy = SileroEndpointPolicy()
        repeat(16) { policy.process(0.9f) }
        policy.reset()
        repeat(80) { assertNull(policy.process(0f)) }
    }
    @Test fun rollbackSelectionIsIndependentOfAsrModel() {
        assertEquals(EndpointMode.LEGACY, EndpointMode.fromSaved("legacy"))
        assertEquals(EndpointMode.SILERO, EndpointMode.fromSaved(null))
    }
}
