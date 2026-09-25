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

    fun initialize(
        model: MoonshineAsrModel,
        useChildcareKeyterms: Boolean,
    ): Result<Unit> = runCatching {
        require(MoonshineModelStore.isInstalled(appContext, model)) {
            "Moonshine 日本語${model.shortLabel}がまだ導入されていません。"
        }

        synchronized(lock) {
            transcriber?.close()
            val created = Transcriber()
            val root = MoonshineModelStore.directory(appContext, model).absolutePath + File.separator
            created.loadFromFiles(root, model.arch)
            if (useChildcareKeyterms) {
                runCatching { created.setKeyterms(CHILDCARE_ASR_KEYTERMS) }
                    .onFailure { error ->
                        DiagnosticStore.mark(
                            appContext,
                            "moonshine_keyterms_skipped",
                            "model=${model.shortLabel} error=${error.message ?: error.javaClass.simpleName}",
                        )
                    }
            }
            transcriber = created
        }

        DiagnosticStore.mark(
            appContext,
            "lite_asr_initialized",
            "engine=moonshine model=${model.modelName} language=ja childcareKeyterms=$useChildcareKeyterms",
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

    private companion object {
        val CHILDCARE_ASR_KEYTERMS = listOf(
            "沐浴", "お風呂", "ミルク", "母乳", "おっぱい", "哺乳瓶", "授乳",
            "ねんね", "おやすみ", "昼寝", "おむつ", "うんち", "おしっこ",
            "着替え", "抱っこ", "おてて", "あんよ", "にこにこ", "泣く",
            "ぐずぐず", "喃語", "クーイング", "げっぷ", "吐き戻し", "おもちゃ",
            "お散歩", "ベビーカー", "離乳食", "絵本", "音楽",
        )
    }
}
