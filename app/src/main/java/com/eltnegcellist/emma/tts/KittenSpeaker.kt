package com.eltnegcellist.emma.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

class KittenSpeaker(
    private val context: Context,
    private val onDone: (Long, Long, Long) -> Unit,
    private val onError: (String) -> Unit,
    private val onStarted: () -> Unit = {},
    private val onAmplitude: (Float) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var requestId: String? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var closed = false
    @Volatile private var warmGeneration = 0L
    private var engine: Engine? = null
    private val cache = object : LinkedHashMap<String, ShortArray>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShortArray>?) = size > 24
    }
    fun prepare(texts: List<String>, onReady: (Boolean) -> Unit = {}) {
        if (closed || !KittenModelStore.isInstalled(context)) { main.post { onReady(false) }; return }
        val generation = ++warmGeneration
        worker.execute {
            var success = true
            texts.forEach { text ->
                if (!closed && generation == warmGeneration && !cache.containsKey(text.trim())) runCatching {
                    val active = engine ?: Engine(context).also { engine = it }
                    val generated = active.generate(text.trim())
                    require(generated.isNotEmpty() && generated.all(Float::isFinite))
                    cache[text.trim()] = toPcm16(generated)
                }.onFailure { success = false }
            }
            main.post { if (!closed && generation == warmGeneration) onReady(success) }
        }
    }

    fun speak(text: String): Boolean {
        if (closed || text.isBlank() || !KittenModelStore.isInstalled(context)) return false

        stop()
        val id = UUID.randomUUID().toString()
        requestId = id

        worker.execute {
            if (closed || requestId != id) return@execute
            val started = System.nanoTime()

            runCatching {
                val active = engine ?: Engine(context).also {
                    engine = it
                    DiagnosticStore.mark(
                        context,
                        "kitten_runtime_initialized",
                        "runtime=onnxruntime-android phonemizer=cmudict model=Nano-FP32 voice=Kiki speed=" +
                            KITTEN_SPEED + " effectiveSpeed=" + EFFECTIVE_SPEED,
                    )
                }

                val generationStarted = System.nanoTime()
                val pcm = cache[text.trim()] ?: active.generate(text.trim()).let { generated ->
                    require(generated.isNotEmpty() && generated.all(Float::isFinite)) { "Kitten TTS Nano returned invalid audio." }
                    toPcm16(generated).also { cache[text.trim()] = it }
                }
                val generationMs = (System.nanoTime() - generationStarted) / 1_000_000L
                val firstAudioMs = play(id, pcm, SAMPLE_RATE, started)
                val totalMs = (System.nanoTime() - started) / 1_000_000L

                DiagnosticStore.mark(
                    context,
                    "kitten_generation",
                    "runtime=onnxruntime voice=Kiki speed=" + KITTEN_SPEED +
                        " effectiveSpeed=" + EFFECTIVE_SPEED +
                        " chars=" + text.length +
                        " samples=" + pcm.size +
                        " generationMs=" + generationMs +
                        " firstAudioMs=" + firstAudioMs +
                        " totalMs=" + totalMs,
                )
                Triple(firstAudioMs, generationMs, totalMs)
            }.onSuccess { value ->
                main.post {
                    if (!closed && requestId == id) {
                        requestId = null
                        onAmplitude(0f)
                        onDone(value.first, value.second, value.third)
                    }
                }
            }.onFailure { error ->
                main.post {
                    if (!closed && requestId == id) {
                        requestId = null
                        onAmplitude(0f)
                        onError(
                            "Kitten TTS Nano生成に失敗しました: " +
                                (error.message ?: error.javaClass.simpleName),
                        )
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        warmGeneration++
        requestId = null
        main.post { onAmplitude(0f) }
        track?.runCatching {
            pause()
            flush()
            release()
        }
        track = null
    }

    fun resetModel() {
        if (closed) return
        stop()
        worker.execute {
            engine?.close()
            engine = null
            cache.clear()
        }
    }

    fun shutdown() {
        if (closed) return
        closed = true
        stop()
        worker.execute {
            engine?.close()
            engine = null
            cache.clear()
        }
        worker.shutdown()
        main.removeCallbacksAndMessages(null)
    }

    private fun play(
        id: String,
        pcm: ShortArray,
        sampleRate: Int,
        started: Long,
    ): Long {
        val player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(
                max(
                    AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    ),
                    sampleRate * 2,
                ),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = player
        check(player.state == AudioTrack.STATE_INITIALIZED) { "音声出力を初期化できません。" }

        var offset = 0
        var firstAudioMs = 0L
        try {
            player.play()
            while (offset < pcm.size && requestId == id && !closed) {
                val count = minOf(PLAYBACK_CHUNK_SAMPLES, pcm.size - offset)
                val written = player.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
                check(written > 0) { "音声出力エラー: " + written }
                if (firstAudioMs == 0L) {
                    firstAudioMs = max(1L, (System.nanoTime() - started) / 1_000_000L)
                    main.post { if (!closed && requestId == id) onStarted() }
                }
                val amplitude = chunkAmplitude(pcm, offset, written)
                main.post {
                    if (!closed && requestId == id) onAmplitude(amplitude)
                }
                offset += written
            }

            val deadline = System.nanoTime() + PLAYBACK_TIMEOUT_MS * 1_000_000L
            while (requestId == id && !closed) {
                val played = player.playbackHeadPosition.toLong() and 0xffffffffL
                if (played >= pcm.size) break
                check(System.nanoTime() < deadline) { "音声再生がタイムアウトしました。" }
                Thread.sleep(20L)
            }
        } finally {
            player.runCatching { stop() }
            player.runCatching { release() }
            if (track === player) track = null
        }
        return firstAudioMs
    }

    private class Engine(context: Context) : AutoCloseable {
        private val dir = KittenModelStore.directory(context)
        private val environment = OrtEnvironment.getEnvironment()
        private val sessionOptions = OrtSession.SessionOptions()
        private val session = environment.createSession(
            File(dir, "model.onnx").absolutePath,
            sessionOptions,
        )
        private val voiceTable = KittenVoiceLoader.loadKiki(File(dir, "voices.npz"))
        private val phonemizer = KittenPhonemizer.fromFile(File(dir, "cmudict.dict"))

        fun generate(text: String): FloatArray {
            val chunks = chunkText(text)
            require(chunks.isNotEmpty()) { "読み上げる英文がありません。" }
            val generated = ArrayList<FloatArray>(chunks.size)
            var total = 0
            for (chunk in chunks) {
                val audio = generateChunk(chunk)
                generated += audio
                total += audio.size
            }
            val result = FloatArray(total)
            var offset = 0
            for (chunk in generated) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            return result
        }

        private fun generateChunk(chunk: String): FloatArray {
            val normalized = KittenTextProcessor.normalize(chunk)
            val phonemes = phonemizer.phonemize(normalized)
            require(phonemes.isNotBlank()) { "英文を発音記号へ変換できませんでした。" }

            val tokenIds = KittenTextProcessor.cleanPhonemes(phonemes)
            require(tokenIds.size > 3) { "Kitten TTSのトークンが不足しています。" }

            val referenceIndex = minOf(tokenIds.size, voiceTable.rows - 1)
            val style = voiceTable.styleFor(referenceIndex)

            val inputIdsTensor = OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(tokenIds),
                longArrayOf(1L, tokenIds.size.toLong()),
            )
            val styleTensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(style),
                longArrayOf(1L, style.size.toLong()),
            )
            val speedTensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(floatArrayOf(EFFECTIVE_SPEED)),
                longArrayOf(1L),
            )

            try {
                val inputs = mapOf(
                    "input_ids" to inputIdsTensor,
                    "style" to styleTensor,
                    "speed" to speedTensor,
                )
                val output = session.run(inputs)
                try {
                    val waveform = output[0] as? OnnxTensor
                        ?: error("Kitten TTSのwaveform出力がありません。")
                    val buffer = waveform.floatBuffer
                        ?: error("Kitten TTSのwaveformがfloat32ではありません。")
                    val raw = FloatArray(buffer.remaining())
                    buffer.get(raw)
                    val keep = (raw.size - AUDIO_TRIM_SAMPLES).coerceAtLeast(0)
                    require(keep > 0) { "Kitten TTSの音声出力が短すぎます。" }
                    return raw.copyOf(keep)
                } finally {
                    output.close()
                }
            } finally {
                inputIdsTensor.close()
                styleTensor.close()
                speedTensor.close()
            }
        }

        override fun close() {
            session.close()
            sessionOptions.close()
        }

        private fun chunkText(text: String): List<String> {
            val sentences = text.split(Regex("""[.!?]+"""))
            val chunks = ArrayList<String>()
            for (raw in sentences) {
                val sentence = raw.trim()
                if (sentence.isEmpty()) continue
                if (sentence.length <= MAX_CHUNK_CHARS) {
                    chunks += ensurePunctuation(sentence)
                } else {
                    var current = StringBuilder()
                    for (word in sentence.split(Regex("""\s+"""))) {
                        if (current.isEmpty() || current.length + 1 + word.length <= MAX_CHUNK_CHARS) {
                            if (current.isNotEmpty()) current.append(' ')
                            current.append(word)
                        } else {
                            chunks += ensurePunctuation(current.toString())
                            current = StringBuilder(word)
                        }
                    }
                    if (current.isNotEmpty()) chunks += ensurePunctuation(current.toString())
                }
            }
            return chunks
        }

        private fun ensurePunctuation(text: String): String {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return trimmed
            return if (trimmed.last() in charArrayOf('.', '!', '?', ',', ';', ':')) trimmed else trimmed + ","
        }
    }

    companion object {
        const val KITTEN_SPEED = 0.8f
        private const val KIKI_SPEED_PRIOR = 0.8f
        const val EFFECTIVE_SPEED = KITTEN_SPEED * KIKI_SPEED_PRIOR
        const val SAMPLE_RATE = 24_000
        const val AUDIO_TRIM_SAMPLES = 5_000
        const val MAX_CHUNK_CHARS = 400
        const val PLAYBACK_CHUNK_SAMPLES = 2048
        const val PLAYBACK_TIMEOUT_MS = 60_000L
        const val TTS_TARGET_PEAK = 0.92f
        const val TTS_MAX_VOLUME_BOOST = 1.8f

        private fun toPcm16(samples: FloatArray): ShortArray {
            var peak = 0f
            for (sample in samples) peak = max(peak, abs(sample))
            val gain = if (peak > 0f) {
                (TTS_TARGET_PEAK / peak).coerceIn(1f, TTS_MAX_VOLUME_BOOST)
            } else {
                1f
            }
            return ShortArray(samples.size) {
                (samples[it].times(gain).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
        }

        private fun chunkAmplitude(samples: ShortArray, offset: Int, count: Int): Float {
            if (count <= 0) return 0f
            var peak = 0
            val end = minOf(samples.size, offset + count)
            for (index in offset until end) peak = max(peak, abs(samples[index].toInt()))
            return (peak / 12000f).coerceIn(0f, 1f)
        }
    }
}
