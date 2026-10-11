package com.eltnegcellist.emma.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SileroEndpointDetectorTest {
    @Test fun actualModelRunsPartialChunksAndDoesNotMutatePcm() {
        val bytes = File("src/main/assets/vad/silero_vad.onnx").readBytes()
        SileroEndpointDetector(bytes, 16_000).use { detector ->
            val silence = ShortArray(128)
            repeat(100) { assertTrue(detector.process(silence, silence.size).isEmpty()) }
            assertArrayEquals(ShortArray(128), silence)
            detector.reset()
            assertTrue(detector.process(silence, 1).isEmpty())
            assertTrue(detector.process(ShortArray(2_048), 2_048).isEmpty())
        }
    }
    @Test fun rejectsCorruptModelBeforeNativeInference() {
        try {
            SileroEndpointDetector(byteArrayOf(1, 2, 3), 16_000)
            fail("A corrupt model must never become the active detector")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("checksum"))
        }
    }

    /** Optional private fixtures: do not put personal recordings in the repository. */
    @Test fun replayLocalRecordingsWhenProvided() {
        val directory = System.getenv("SILERO_TEST_WAV_DIR")
        assumeTrue("Private WAV fixtures were not supplied", directory != null)
        val fixtures = File(directory!!).listFiles { f -> f.extension == "wav" }!!.sortedBy { it.name }
        assertTrue("Missing WAV fixtures", fixtures.isNotEmpty())
        val model = File("src/main/assets/vad/silero_vad.onnx").readBytes()
        SileroEndpointDetector(model, 16_000).use { detector ->
            for (file in fixtures) {
                detector.reset()
                val bytes = file.readBytes()
                val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                var offset = 12
                var pcm: ShortArray? = null
                while (offset + 8 <= bytes.size) {
                    val tag = String(bytes, offset, 4, Charsets.US_ASCII)
                    val length = view.getInt(offset + 4)
                    require(length >= 0 && offset + 8L + length <= bytes.size)
                    if (tag == "fmt ") {
                        require(view.getShort(offset + 8).toInt() == 1)
                        require(view.getShort(offset + 10).toInt() == 1)
                        require(view.getInt(offset + 12) == 16_000)
                        require(view.getShort(offset + 22).toInt() == 16)
                    }
                    if (tag == "data") pcm = ShortArray(length / 2) { view.getShort(offset + 8 + it * 2) }
                    offset += 8 + length + length % 2
                }
                val samples = requireNotNull(pcm)
                val original = samples.copyOf()
                val events = samples.asList().chunked(2_048).flatMap { chunk ->
                    val input = chunk.toShortArray()
                    val copy = input.copyOf()
                    detector.process(input, input.size).also { assertArrayEquals(copy, input) }
                }
                assertArrayEquals(original, samples)
                if (file.name.startsWith("noise_")) assertTrue(file.name, events.isEmpty())
                else {
                    assertEquals(file.name, 1, events.count { it is VoiceActivityEvent.SpeechStarted })
                    assertEquals(file.name, 1, events.count { it is VoiceActivityEvent.Endpoint })
                }
                println("${file.name}: starts=${events.count { it is VoiceActivityEvent.SpeechStarted }} endpoints=${events.count { it is VoiceActivityEvent.Endpoint }} PCM unchanged")
            }
        }
    }
}
