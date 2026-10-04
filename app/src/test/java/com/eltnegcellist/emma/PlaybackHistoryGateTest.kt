package com.eltnegcellist.emma

import org.junit.Assert.*
import org.junit.Test

class PlaybackHistoryGateTest {
    @Test fun firstAudioIsRequiredAndCommitsExactlyOnce() {
        val gate = PlaybackHistoryGate<String>()
        assertNull(gate.started(true))
        gate.offer("original English, including its original name")
        assertEquals("original English, including its original name", gate.started(true))
        assertNull(gate.started(true))
    }
    @Test fun cancelledAndDisabledResponsesAreNotRetained() {
        val gate = PlaybackHistoryGate<String>()
        gate.offer("cancelled before playback"); gate.cancel(); assertNull(gate.started(true))
        gate.offer("OFF at playback"); assertNull(gate.started(false)); assertNull(gate.started(true))
    }
    @Test fun aNewTurnCannotCommitThePreviousCandidate() {
        val gate = PlaybackHistoryGate<String>()
        gate.offer("old");gate.cancel();gate.offer("new")
        assertEquals("new",gate.started(true))
    }
    @Test fun replayOrPlayWithoutCandidateCreatesNoHistory() {
        assertNull(PlaybackHistoryGate<String>().started(true))
    }
    @Test fun playNeverRepeatsImmediately() {
        val phrases=listOf("Hi there!","Hello, hello!","Let's say hello!")
        var previous: String? = null
        val random=kotlin.random.Random(17)
        repeat(100) { val next=nextPlayPhrase(phrases,previous,random);assertNotEquals(previous,next);assertTrue(next in phrases);previous=next }
    }
}
