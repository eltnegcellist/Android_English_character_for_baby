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
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

class SupertonicSpeaker(
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

    fun speak(text: String, speed: Float = 1.0f): Boolean {
        if (closed || text.isBlank() || !speed.isFinite() || !SupertonicModelStore.isInstalled(context)) {
            return false
        }

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
                        "supertonic_runtime_initialized",
                        "voice=F3 sid=$F3_SPEAKER_ID speakers=${it.numSpeakers} " +
                            "engineRate=${it.sampleRate} threads=$THREADS steps=$NUM_STEPS",
                    )
                }

                val effectiveSpeed = if (isBabyAudienceMode()) {
                    (speed * BABY_SPEED_FACTOR).coerceIn(0.85f, 1.05f)
                } else {
                    speed.coerceIn(0.75f, 1.15f)
                }

                val config = GenerationConfig(
                    speed = effectiveSpeed,
                    sid = F3_SPEAKER_ID,
                    numSteps = NUM_STEPS,
                    extra = mapOf("lang" to "en"),
                )

                val generationStarted = System.nanoTime()
                val generated = active.tts.generateWithConfigAndCallback(
                    text.trim(),
                    config,
                ) { 1 }
                val generationMs = (System.nanoTime() - generationStarted) / 1_000_000L

                require(generated.samples.isNotEmpty()) {
                    "Supertonic 3 returned no audio samples."
                }

                val engineSampleRate = active.tts.sampleRate()
                val playbackSampleRate = when {
                    generated.sampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE -> generated.sampleRate
                    engineSampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE -> engineSampleRate
                    else -> DEFAULT_SUPERTONIC_SAMPLE_RATE
                }
                var nonFiniteSamples = 0
                val safeSamples = FloatArray(generated.samples.size) { index ->
                    val value = generated.samples[index]
                    if (value.isFinite()) {
                        value
                    } else {
                        nonFiniteSamples++
                        0f
                    }
                }
                val peak = safeSamples.maxOf { kotlin.math.abs(it) }

                val pcm = toPcm16(safeSamples)
                val firstAudioMs = play(id, pcm, playbackSampleRate, started)
                val totalMs = (System.nanoTime() - started) / 1_000_000L

                DiagnosticStore.mark(
                    context,
                    "supertonic_generation",
                    "voice=F3 sid=$F3_SPEAKER_ID steps=$NUM_STEPS speed=$effectiveSpeed " +
                        "chars=${text.length} generatedRate=${generated.sampleRate} engineRate=$engineSampleRate " +
                        "playbackRate=$playbackSampleRate peak=$peak nonFinite=$nonFiniteSamples " +
                        "samples=${pcm.size} generationMs=$generationMs " +
                        "firstAudioMs=$firstAudioMs totalMs=$totalMs",
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
                        onError("Supertonic 3生成に失敗しました: ${error.message ?: error.javaClass.simpleName}")
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

    private fun isBabyAudienceMode(): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(AUDIENCE_MODE_KEY, BABY_AUDIENCE_VALUE) != "PARENT"

    private class Engine(context: Context) : AutoCloseable {
        private val dir = SupertonicModelStore.directory(context)
        val tts = OfflineTts(
            assetManager = null,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    supertonic = OfflineTtsSupertonicModelConfig(
                        durationPredictor = File(dir, "duration_predictor.int8.onnx").path,
                        textEncoder = File(dir, "text_encoder.int8.onnx").path,
                        vectorEstimator = File(dir, "vector_estimator.int8.onnx").path,
                        vocoder = File(dir, "vocoder.int8.onnx").path,
                        ttsJson = File(dir, "tts.json").path,
                        unicodeIndexer = File(dir, "unicode_indexer.bin").path,
                        voiceStyle = File(dir, "voice.bin").path,
                    ),
                    numThreads = THREADS,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            ),
        )
        val numSpeakers: Int = tts.numSpeakers()
        val sampleRate: Int = tts.sampleRate()

        init {
            require(numSpeakers > F3_SPEAKER_ID) {
                "Supertonic 3 F3を利用できません（speakers=$numSpeakers, sid=$F3_SPEAKER_ID）。"
            }
        }

        override fun close() {
            tts.release()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "emma_speech"
        const val AUDIENCE_MODE_KEY = "audience_mode"
        const val BABY_AUDIENCE_VALUE = "BABY"

        const val F3_SPEAKER_ID = 2
        const val NUM_STEPS = 8
        const val THREADS = 2
        const val BABY_SPEED_FACTOR = 0.94f
        const val MIN_SAMPLE_RATE = 8_000
        const val MAX_SAMPLE_RATE = 96_000
        const val DEFAULT_SUPERTONIC_SAMPLE_RATE = 24_000
        const val PLAYBACK_CHUNK_SAMPLES = 2048
        const val PLAYBACK_TIMEOUT_MS = 60_000L
        const val TTS_TARGET_PEAK = 0.92f
        const val TTS_MAX_VOLUME_BOOST = 1.8f

        fun toPcm16(samples: FloatArray): ShortArray {
            var peak = 0f
            for (sample in samples) {
                peak = max(peak, abs(sample))
            }
            val gain = if (peak > 0f) {
                (TTS_TARGET_PEAK / peak).coerceIn(1f, TTS_MAX_VOLUME_BOOST)
            } else {
                1f
            }
            return ShortArray(samples.size) {
                (samples[it].times(gain).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
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
