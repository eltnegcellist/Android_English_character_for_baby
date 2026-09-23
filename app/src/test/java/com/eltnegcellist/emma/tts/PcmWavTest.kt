package com.eltnegcellist.emma.tts

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmWavTest {
    @Test
    fun writesValidMono16HeaderAndSamples() {
        val output = ByteArrayOutputStream()
        PcmWav.writeMono16(output, shortArrayOf(0, 32767, -32768), 24000)
        val bytes = output.toByteArray()

        assertEquals(50, bytes.size)
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(42, le32(bytes, 4))
        assertEquals("WAVE", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals("fmt ", bytes.copyOfRange(12, 16).toString(Charsets.US_ASCII))
        assertEquals(1, le16(bytes, 20))
        assertEquals(1, le16(bytes, 22))
        assertEquals(24000, le32(bytes, 24))
        assertEquals(48000, le32(bytes, 28))
        assertEquals(16, le16(bytes, 34))
        assertEquals("data", bytes.copyOfRange(36, 40).toString(Charsets.US_ASCII))
        assertEquals(6, le32(bytes, 40))
        assertEquals(0, le16(bytes, 44))
        assertEquals(32767, le16(bytes, 46))
        assertEquals(32768, le16(bytes, 48))
    }

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
