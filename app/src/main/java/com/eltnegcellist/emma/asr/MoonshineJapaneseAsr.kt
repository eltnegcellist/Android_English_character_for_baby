package com.eltnegcellist.emma.asr

import android.content.Context
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriberOption
import com.eltnegcellist.emma.audio.WavMono16
import com.eltnegcellist.emma.tts.DiagnosticStore
import java.io.File

class MoonshineJapaneseAsr(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var transcriber: Transcriber? = null

    fun isReady(): Boolean = transcriber != null

    fun initialize(model: MoonshineAsrModel): Result<Unit> = runCatching {
        require(MoonshineModelStore.isInstalled(appContext, model)) {
            "Moonshine 日本語${model.shortLabel}がまだ導入されていません。"
        }

        synchronized(lock) {
            transcriber?.close()
            // Silero already supplies a complete utterance. Running the SDK's
            // VAD again can discard speech; Japanese needs a 13 token/s guard.
            val created = Transcriber(listOf(
                TranscriberOption("vad_threshold", "0"),
                TranscriberOption("vad_max_segment_duration", "1000"),
                TranscriberOption("max_tokens_per_second", "13.0"),
            ))
            val root = MoonshineModelStore.directory(appContext, model).absolutePath + File.separator
            created.loadFromFiles(root, model.arch)
            transcriber = created
        }

        DiagnosticStore.mark(
            appContext,
            "lite_asr_initialized",
            "engine=moonshine model=${model.modelName} language=ja " +
                "internalVad=false maxTokensPerSecond=13.0 childcareKeyterms=false",
        )
    }

    fun transcribe(wavAudio: ByteArray): Result<String> = runCatching {
        val pcm = WavMono16.decode(wavAudio)
        val started = System.nanoTime()
        val text = synchronized(lock) {
            val active = transcriber ?: error("Emma Lite ASR is not initialized.")
            active.transcribeWithoutStreaming(pcm.samples, pcm.sampleRate)
                ?.text()
                .orEmpty()
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        DiagnosticStore.mark(
            appContext,
            "lite_asr_transcription",
            "engine=moonshine samples=${pcm.samples.size} sampleRate=${pcm.sampleRate} durationMs=$elapsedMs chars=${text.length}",
        )
        require(text.isNotBlank()) {
            "日本語を聞き取れませんでした。近くで短く話して、もう一度お試しください。"
        }
        text
    }

    fun close() {
        synchronized(lock) {
            transcriber?.close()
            transcriber = null
        }
    }
}
