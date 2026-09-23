package com.eltnegcellist.emma.audio

import com.eltnegcellist.emma.tts.PcmWav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavMono16Test {
    @Test
    fun decodesRecorderCompatibleMono16Wav() {
        val source = shortArrayOf(0, 16384, -16384, 32767, -32768)
        val wav = PcmWav.encodeMono16(source, 16_000)

        val decoded = WavMono16.decode(wav)

        assertEquals(16_000, decoded.sampleRate)
        assertEquals(source.size, decoded.samples.size)
        assertEquals(0f, decoded.samples[0], 0.0001f)
        assertEquals(0.5f, decoded.samples[1], 0.0001f)
        assertEquals(-0.5f, decoded.samples[2], 0.0001f)
        assertTrue(decoded.samples[3] > 0.99f)
        assertEquals(-1f, decoded.samples[4], 0.0001f)
    }
}
