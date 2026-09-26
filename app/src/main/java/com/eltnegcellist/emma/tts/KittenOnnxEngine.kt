package com.eltnegcellist.emma.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import kotlin.math.min

internal class KittenOnnxEngine(
    modelDirectory: File,
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(THREADS)
    }
    private val session = environment.createSession(
        File(modelDirectory, KittenModelStore.MODEL_FILE).absolutePath,
        sessionOptions,
    )
    private val phonemizer = KittenPhonemizer(File(modelDirectory, KittenModelStore.CMUDICT_FILE))
    private val voice = loadVoice(
        File(modelDirectory, KittenModelStore.VOICES_FILE),
        KIKI_STYLE_KEY,
    )

    fun generate(
        rawText: String,
        japaneseNameHint: String = "",
    ): FloatArray {
        val chunks = chunkText(rawText)
        require(chunks.isNotEmpty()) { "読み上げる英語がありません。" }

        val generated = ArrayList<FloatArray>(chunks.size)
        var total = 0
        for (chunk in chunks) {
            val audio = synthesizeChunk(chunk, japaneseNameHint)
            require(audio.isNotEmpty()) { "Kitten TTS Nano returned empty audio." }
            generated += audio
            total += audio.size
        }

        val combined = FloatArray(total)
        var offset = 0
        for (audio in generated) {
            audio.copyInto(combined, destinationOffset = offset)
            offset += audio.size
        }
        return combined
    }

    private fun synthesizeChunk(
        chunk: String,
        japaneseNameHint: String,
    ): FloatArray {
        val processed = KittenTextFrontend.preprocess(chunk)
        val phonemes = phonemizer.phonemize(processed, japaneseNameHint)
        val tokenIds = KittenTextFrontend.cleanPhonemes(phonemes)
        require(tokenIds.size > 3) { "Kitten TTSの発音トークンを作成できませんでした。" }

        val row = min(tokenIds.size, voice.rows - 1)
        val style = voice.row(row)
        val effectiveSpeed = REQUESTED_SPEED * KIKI_SPEED_PRIOR

        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(tokenIds),
            longArrayOf(1L, tokenIds.size.toLong()),
        ).use { inputIds ->
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(style),
                longArrayOf(1L, style.size.toLong()),
            ).use { styleTensor ->
                OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(floatArrayOf(effectiveSpeed)),
                    longArrayOf(1L),
                ).use { speedTensor ->
                    val inputs = mapOf(
                        "input_ids" to inputIds,
                        "style" to styleTensor,
                        "speed" to speedTensor,
                    )
                    session.run(inputs).use { result ->
                        val output = result[0] as? OnnxTensor
                            ?: error("Kitten TTSのONNX出力形式が不正です。")
                        val buffer = output.floatBuffer
                            ?: error("Kitten TTSのONNX出力をFloat32として読めません。")
                        val raw = FloatArray(buffer.remaining())
                        buffer.get(raw)
                        val end = (raw.size - AUDIO_TRIM_SAMPLES).coerceAtLeast(0)
                        return raw.copyOf(end)
                    }
                }
            }
        }
    }

    private fun chunkText(raw: String): List<String> {
        val sentences = raw.split(Regex("[.!?]+"))
        val chunks = mutableListOf<String>()

        for (source in sentences) {
            val sentence = source.trim()
            if (sentence.isBlank()) continue

            if (sentence.length <= MAX_CHUNK_CHARS) {
                chunks += ensurePunctuation(sentence)
                continue
            }

            val words = sentence.split(Regex("\\s+"))
            var current = StringBuilder()
            for (word in words) {
                if (current.isEmpty()) {
                    current.append(word)
                } else if (current.length + word.length + 1 <= MAX_CHUNK_CHARS) {
                    current.append(' ').append(word)
                } else {
                    chunks += ensurePunctuation(current.toString())
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) chunks += ensurePunctuation(current.toString())
        }
        return chunks
    }

    private fun ensurePunctuation(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return trimmed
        return if (trimmed.last() in setOf('.', '!', '?', ',', ';', ':')) trimmed else "$trimmed,"
    }

    override fun close() {
        session.close()
        sessionOptions.close()
    }

    private data class VoiceStyle(
        val rows: Int,
        val dimensions: Int,
        val values: FloatArray,
    ) {
        fun row(index: Int): FloatArray {
            require(index in 0 until rows)
            val start = index * dimensions
            return values.copyOfRange(start, start + dimensions)
        }
    }

    private fun loadVoice(file: File, key: String): VoiceStyle {
        require(file.isFile) { "Kitten TTSのvoices.npzが見つかりません。" }
        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && (entry.name == "$key.npy" || entry.name.endsWith("/$key.npy"))) {
                    return parseNpyFloat32(zip.readBytes())
                }
                zip.closeEntry()
            }
        }
        error("Kitten TTSのKiki音声データをvoices.npzから読み出せませんでした。")
    }

    private fun parseNpyFloat32(bytes: ByteArray): VoiceStyle {
        require(bytes.size > 16) { "voices.npz内のNPYデータが短すぎます。" }
        require(
            bytes[0] == 0x93.toByte() &&
                bytes.copyOfRange(1, 6).toString(StandardCharsets.US_ASCII) == "NUMPY",
        ) { "voices.npz内のNPYヘッダーが不正です。" }

        val major = bytes[6].toInt() and 0xff
        val headerLength: Int
        val headerStart: Int
        if (major == 1) {
            headerLength = (bytes[8].toInt() and 0xff) or ((bytes[9].toInt() and 0xff) shl 8)
            headerStart = 10
        } else {
            headerLength = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
            headerStart = 12
        }

        require(headerStart + headerLength <= bytes.size) { "voices.npz内のNPYヘッダー長が不正です。" }
        val header = String(bytes, headerStart, headerLength, StandardCharsets.US_ASCII)
        require(header.contains("<f4") || header.contains("|f4")) {
            "Kiki音声データがFloat32ではありません。"
        }
        require(!header.contains("fortran_order': True") && !header.contains("fortran_order\": True")) {
            "Fortran-orderのKiki音声データには対応していません。"
        }

        val shapeText = Regex("""['"]shape['"]\\s*:\\s*\\(([^)]*)\\)""")
            .find(header)
            ?.groupValues
            ?.get(1)
            ?: error("Kiki音声データのshapeを読めません。")
        val shape = shapeText.split(',')
            .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toIntOrNull() }
        require(shape.size == 2 && shape[0] > 0 && shape[1] > 0) {
            "Kiki音声データのshapeが想定外です: $shape"
        }

        val count = shape[0] * shape[1]
        val dataStart = headerStart + headerLength
        require(dataStart + count * Float.SIZE_BYTES <= bytes.size) {
            "Kiki音声データが途中で切れています。"
        }
        val floatBuffer = ByteBuffer.wrap(bytes, dataStart, count * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
        val values = FloatArray(count)
        floatBuffer.get(values)
        return VoiceStyle(shape[0], shape[1], values)
    }

    private companion object {
        const val THREADS = 2
        const val REQUESTED_SPEED = 0.8f
        const val KIKI_SPEED_PRIOR = 0.8f
        const val KIKI_STYLE_KEY = "expr-voice-5-f"
        const val SAMPLE_RATE = 24_000
        const val AUDIO_TRIM_SAMPLES = 5_000
        const val MAX_CHUNK_CHARS = 400
    }

    companion object {
        const val OUTPUT_SAMPLE_RATE = SAMPLE_RATE
    }
}
