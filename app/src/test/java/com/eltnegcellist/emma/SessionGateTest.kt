package com.eltnegcellist.emma

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGateTest {
    @Test fun completionAfterStopCannotSpeak() {
        val gate = SessionGate()
        val pending = gate.start()
        gate.stop()
        assertFalse(gate.accepts(pending))
    }

    @Test fun restartRejectsPreviousCompletionButAcceptsNewOne() {
        val gate = SessionGate()
        val old = gate.start()
        gate.stop()
        val current = gate.start()
        assertFalse(gate.accepts(old))
        assertTrue(gate.accepts(current))
    }

    @Test fun repeatedStopAndInactiveTicketsCannotResumeSession() {
        val gate = SessionGate()
        assertFalse(gate.accepts(gate.ticket()))
        gate.start()
        gate.stop()
        gate.stop()
        assertFalse(gate.accepts(gate.ticket()))
    }
}

