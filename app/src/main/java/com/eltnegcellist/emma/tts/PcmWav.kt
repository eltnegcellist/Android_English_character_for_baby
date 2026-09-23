package com.eltnegcellist.emma.tts

import java.io.ByteArrayOutputStream
import java.io.OutputStream

internal object PcmWav {
    fun encodeMono16(samples: ShortArray, sampleRate: Int): ByteArray =
        ByteArrayOutputStream(44 + samples.size * 2).use { output ->
            writeMono16(output, samples, sampleRate)
            output.toByteArray()
        }

    fun writeMono16(output: OutputStream, samples: ShortArray, sampleRate: Int) {
        require(sampleRate > 0) { "sampleRate must be positive" }
        val dataBytes = samples.size.toLong() * 2L
        require(dataBytes <= Int.MAX_VALUE.toLong()) { "WAV data is too large" }
        val riffSize = 36L + dataBytes
        require(riffSize <= Int.MAX_VALUE.toLong()) { "WAV RIFF is too large" }

        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeLe32(output, riffSize.toInt())
        output.write("WAVE".toByteArray(Charsets.US_ASCII))
        output.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeLe32(output, 16)
        writeLe16(output, 1) // PCM
        writeLe16(output, 1) // mono
        writeLe32(output, sampleRate)
        writeLe32(output, sampleRate * 2) // 16-bit mono byte rate
        writeLe16(output, 2) // block align
        writeLe16(output, 16) // bits per sample
        output.write("data".toByteArray(Charsets.US_ASCII))
        writeLe32(output, dataBytes.toInt())
        for (sample in samples) writeLe16(output, sample.toInt() and 0xffff)
    }

    private fun writeLe16(output: OutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }

    private fun writeLe32(output: OutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
        output.write((value ushr 16) and 0xff)
        output.write((value ushr 24) and 0xff)
    }
}
