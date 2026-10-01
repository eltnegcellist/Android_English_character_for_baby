package com.eltnegcellist.emma.tts

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class KittenVoiceLoaderTest {
    @Test
    fun parsesLittleEndianFloat32Npy() {
        val headerText = "{'descr': '<f4', 'fortran_order': False, 'shape': (2, 3), }\n"
        val header = headerText.toByteArray(Charsets.US_ASCII)
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x93.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte(), 'Y'.code.toByte()))
        out.write(byteArrayOf(1, 0))
        out.write(byteArrayOf((header.size and 0xff).toByte(), ((header.size ushr 8) and 0xff).toByte()))
        out.write(header)
        val values = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f)
        val data = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(data::putFloat)
        out.write(data.array())

        val parsed = KittenVoiceLoader.parseNpyFloat32(out.toByteArray())
        assertEquals(2, parsed.rows)
        assertEquals(3, parsed.columns)
        assertArrayEquals(floatArrayOf(4f, 5f, 6f), parsed.styleFor(1), 0f)
    }
}
