package com.eltnegcellist.emma

import com.eltnegcellist.emma.audio.PcmRingBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmRingBufferTest {
    @Test fun wraparoundReturnsMostRecentSamplesInOrder() {
        val buffer = PcmRingBuffer(4)
        buffer.append(shortArrayOf(1, 2, 3, 4, 5, 6), 6, buffer.epoch())
        assertArrayEquals(shortArrayOf(3, 4, 5, 6), buffer.snapshot(4, false))
        assertArrayEquals(shortArrayOf(5, 6), buffer.snapshot(2, false))
    }

    @Test fun consumingRequestKeepsOnlyConversationRecordedAfterRequest() {
        val buffer = PcmRingBuffer(8)
        buffer.append(shortArrayOf(1, 2), 2, buffer.epoch())
        assertArrayEquals(shortArrayOf(1, 2), buffer.snapshot(8, true))
        assertEquals(0, buffer.size())
        buffer.append(shortArrayOf(3, 4), 2, buffer.epoch())
        buffer.pause()
        buffer.append(shortArrayOf(99), 1, buffer.epoch())
        buffer.resume()
        buffer.append(shortArrayOf(5), 1, buffer.epoch())
        assertArrayEquals(shortArrayOf(3, 4, 5), buffer.snapshot(8, true))
    }

    @Test fun readsSpanningPlaybackAndRestartAreDiscarded() {
        val buffer = PcmRingBuffer(8)
        val beforePlayback = buffer.epoch()
        buffer.pause()
        val duringPlayback = buffer.epoch()
        buffer.resume()
        buffer.append(shortArrayOf(98), 1, beforePlayback)
        buffer.append(shortArrayOf(99), 1, duringPlayback)
        assertEquals(0, buffer.size())
        val beforeStop = buffer.epoch()
        buffer.clear()
        buffer.append(shortArrayOf(97), 1, beforeStop)
        buffer.append(shortArrayOf(1), 1, buffer.epoch())
        assertArrayEquals(shortArrayOf(1), buffer.snapshot(8, false))
    }

    @Test fun sessionClearErasesPreviousConversation() {
        val buffer = PcmRingBuffer(8)
        buffer.append(shortArrayOf(1, 2, 3), 3, buffer.epoch())
        buffer.clear()
        assertArrayEquals(shortArrayOf(), buffer.snapshot(8, false))
    }
}

