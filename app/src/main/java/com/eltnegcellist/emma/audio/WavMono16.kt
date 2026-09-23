package com.eltnegcellist.emma.audio

internal data class MonoPcm16(
    val samples: FloatArray,
    val sampleRate: Int,
)

internal object WavMono16 {
    fun decode(bytes: ByteArray): MonoPcm16 {
        require(bytes.size >= 44) { "WAV is too small." }
        require(ascii(bytes, 0, 4) == "RIFF" && ascii(bytes, 8, 4) == "WAVE") {
            "Unsupported WAV container."
        }

        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var audioFormat = 0
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val id = ascii(bytes, offset, 4)
            val chunkSize = le32(bytes, offset + 4)
            require(chunkSize >= 0) { "Invalid WAV chunk." }
            val payload = offset + 8
            require(payload + chunkSize <= bytes.size) { "Truncated WAV chunk." }

            when (id) {
                "fmt " -> {
                    require(chunkSize >= 16) { "Invalid WAV fmt chunk." }
                    audioFormat = le16(bytes, payload)
                    channels = le16(bytes, payload + 2)
                    sampleRate = le32(bytes, payload + 4)
                    bitsPerSample = le16(bytes, payload + 14)
                }
                "data" -> {
                    dataOffset = payload
                    dataSize = chunkSize
                    break
                }
            }
            offset = payload + chunkSize + (chunkSize and 1)
        }

        require(audioFormat == 1) { "Only PCM WAV is supported." }
        require(channels == 1) { "Only mono WAV is supported." }
        require(bitsPerSample == 16) { "Only 16-bit WAV is supported." }
        require(sampleRate > 0) { "Invalid WAV sample rate." }
        require(dataOffset >= 0 && dataSize >= 2) { "WAV has no audio data." }

        val count = dataSize / 2
        val samples = FloatArray(count)
        var p = dataOffset
        for (i in 0 until count) {
            val lo = bytes[p].toInt() and 0xff
            val hi = bytes[p + 1].toInt()
            val value = (hi shl 8) or lo
            samples[i] = value.toShort().toFloat() / 32768f
            p += 2
        }
        return MonoPcm16(samples, sampleRate)
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
