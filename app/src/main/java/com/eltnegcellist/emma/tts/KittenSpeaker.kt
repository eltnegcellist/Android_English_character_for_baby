package com.eltnegcellist.emma.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

class KittenSpeaker(
    private val context: Context,
    private val onDone: (Long, Long) -> Unit,
    private val onError: (String) -> Unit,
    private val onAmplitude: (Float) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var requestId: String? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var closed = false
    private var engine: Engine? = null

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
                        "voice=Kiki sid=$KIKI_SPEAKER_ID threads=$THREADS speed=$KITTEN_SPEED",
                    )
                }

                val generationStarted = System.nanoTime()
                val generated = active.tts.generateWithConfig(
                    text.trim(),
                    GenerationConfig(
                        speed = KITTEN_SPEED,
                        sid = KIKI_SPEAKER_ID,
                    ),
                )
                val generationMs = (System.nanoTime() - generationStarted) / 1_000_000L
                require(generated.samples.isNotEmpty() && generated.samples.all { it.isFinite() }) {
                    "Kitten TTS Nano returned invalid audio."
                }

                val pcm = toPcm16(generated.samples)
                val firstAudioMs = play(id, pcm, generated.sampleRate, started)
                val totalMs = (System.nanoTime() - started) / 1_000_000L

                DiagnosticStore.mark(
                    context,
                    "kitten_generation",
                    "voice=Kiki sid=$KIKI_SPEAKER_ID speed=$KITTEN_SPEED chars=${text.length} " +
                        "samples=${pcm.size} generationMs=$generationMs firstAudioMs=$firstAudioMs totalMs=$totalMs",
                )
                firstAudioMs to totalMs
            }.onSuccess { value ->
                main.post {
                    if (!closed && requestId == id) {
                        requestId = null
                        onAmplitude(0f)
                        onDone(value.first, value.second)
                    }
                }
            }.onFailure { error ->
                main.post {
                    if (!closed && requestId == id) {
                        requestId = null
                        onAmplitude(0f)
                        onError("Kitten TTS Nano生成に失敗しました: ${error.message ?: error.javaClass.simpleName}")
                    }
                }
            }
        }
        return true
    }

    fun stop() {
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
        }
    }

    fun shutdown() {
        if (closed) return
        closed = true
        stop()
        worker.execute {
            engine?.close()
            engine = null
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
                check(written > 0) { "音声出力エラー: $written" }
                if (firstAudioMs == 0L) {
                    firstAudioMs = (System.nanoTime() - started) / 1_000_000L
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
        val tts = OfflineTts(
            assetManager = null,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kitten = OfflineTtsKittenModelConfig(
                        model = File(dir, "model.int8.onnx").path,
                        voices = File(dir, "voices.bin").path,
                        tokens = File(dir, "tokens.txt").path,
                        dataDir = File(dir, "espeak-ng-data").path,
                    ),
                    numThreads = THREADS,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            ),
        )

        override fun close() {
            tts.release()
        }
    }

    private companion object {
        const val KIKI_SPEAKER_ID = 7
        const val KITTEN_SPEED = 1.0f
        const val THREADS = 2
        const val PLAYBACK_CHUNK_SAMPLES = 2048
        const val PLAYBACK_TIMEOUT_MS = 60_000L

        fun toPcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) {
            (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }

        fun chunkAmplitude(samples: ShortArray, offset: Int, count: Int): Float {
            if (count <= 0) return 0f
            var peak = 0
            val end = minOf(samples.size, offset + count)
            for (i in offset until end) peak = max(peak, abs(samples[i].toInt()))
            return (peak / 12000f).coerceIn(0f, 1f)
        }
    }
}
