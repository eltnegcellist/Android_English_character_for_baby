package com.eltnegcellist.emma.asr

import android.content.Context
import com.eltnegcellist.emma.audio.WavMono16
import com.eltnegcellist.emma.tts.DiagnosticStore
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File
import kotlin.math.min

class ReazonSpeechJapaneseAsr(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    fun isReady(): Boolean = recognizer != null

    fun initialize(): Result<Unit> = runCatching {
        val directory = ReazonSpeechModelStore.directory(appContext)
        require(ReazonSpeechModelStore.isInstalled(appContext)) {
            "EmmaのReazonSpeech日本語モデルがまだ導入されていません。"
        }

        val encoder = File(directory, "encoder-epoch-99-avg-1.int8.onnx")
        val decoder = File(directory, "decoder-epoch-99-avg-1.onnx")
        val joiner = File(directory, "joiner-epoch-99-avg-1.int8.onnx")
        val tokens = File(directory, "tokens.txt")
        val threads = min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))

        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    joiner = joiner.absolutePath,
                ),
                tokens = tokens.absolutePath,
                numThreads = threads,
                provider = "cpu",
                modelType = "transducer",
            ),
            decodingMethod = "greedy_search",
        )

        synchronized(lock) {
            recognizer?.release()
            recognizer = OfflineRecognizer(config = config)
        }
        DiagnosticStore.mark(
            appContext,
            "lite_asr_initialized",
            "model=${ReazonSpeechModelStore.MODEL_NAME} language=ja threads=$threads",
        )
    }

    fun transcribe(wavAudio: ByteArray): Result<String> = runCatching {
        val pcm = WavMono16.decode(wavAudio)
        val started = System.nanoTime()
        val text = synchronized(lock) {
            val active = recognizer ?: error("Emma ASR is not initialized.")
            val stream = active.createStream()
            try {
                stream.acceptWaveform(pcm.samples, pcm.sampleRate)
                active.decode(stream)
                active.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        DiagnosticStore.mark(
            appContext,
            "lite_asr_transcription",
            "engine=reazonspeech samples=${pcm.samples.size} sampleRate=${pcm.sampleRate} durationMs=$elapsedMs chars=${text.length}",
        )
        require(text.isNotBlank()) {
            "日本語を聞き取れませんでした。近くで短く話して、もう一度お試しください。"
        }
        text
    }

    fun close() {
        synchronized(lock) {
            recognizer?.release()
            recognizer = null
        }
    }
}
