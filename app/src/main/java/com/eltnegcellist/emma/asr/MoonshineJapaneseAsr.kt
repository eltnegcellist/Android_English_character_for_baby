package com.eltnegcellist.emma.asr

import android.content.Context
import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
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

    fun initialize(): Result<Unit> = runCatching {
        require(MoonshineModelStore.isInstalled(appContext)) {
            "Emma LiteのMoonshine日本語モデルがまだ導入されていません。"
        }

        synchronized(lock) {
            transcriber?.close()
            val created = Transcriber()
            val root = MoonshineModelStore.directory(appContext).absolutePath + File.separator
            created.loadFromFiles(root, JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING)
            transcriber = created
        }

        DiagnosticStore.mark(
            appContext,
            "lite_asr_initialized",
            "engine=moonshine model=${MoonshineModelStore.MODEL_NAME} language=ja",
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
