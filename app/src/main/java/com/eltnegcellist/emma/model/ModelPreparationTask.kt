package com.eltnegcellist.emma.model

import android.content.Context
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import com.eltnegcellist.emma.asr.MoonshineModelStore
import com.eltnegcellist.emma.ai.RuriSemanticModelStore
import com.eltnegcellist.emma.tts.KittenModelStore

internal object ModelPreparationTask {
    fun run(
        context: Context,
        kind: ModelPreparationKind,
        asr: MoonshineAsrModel,
        progress: (String, Int?) -> Unit,
    ): Result<Unit> = runCatching {
        fun moonshine() {
            if (MoonshineModelStore.isInstalled(context, asr)) return
            MoonshineModelStore.downloadAndInstall(context, asr) { p ->
                progress("Moonshine 日本語${asr.shortLabel}を準備しています…", p)
            }.getOrThrow()
        }
        fun kitten() {
            if (KittenModelStore.isInstalled(context)) return
            KittenModelStore.downloadAndInstall(context) { p ->
                progress("Kitten TTS Nano / Kikiを準備しています…", p)
            }.getOrThrow()
        }
        fun gemma() {
            if (GemmaModelStore.hasUsableModel(context)) return
            GemmaModelStore.downloadModel(context) { p ->
                progress("Gemmaをダウンロードしています（2GB超）", p)
            }.getOrThrow()
        }
        fun semantic() {
            if (RuriSemanticModelStore.isInstalled(context)) return
            RuriSemanticModelStore.downloadAndInstall(context) { p ->
                progress("意味で話題を理解するモデルを準備しています…", p)
            }.getOrThrow()
        }

        when (kind) {
            ModelPreparationKind.LITE -> {
                moonshine()
                kitten()
                val semanticEnabled = context
                    .getSharedPreferences("emma_speech", Context.MODE_PRIVATE)
                    .getBoolean("semantic_enabled", true)
                if (semanticEnabled) semantic()
            }
            ModelPreparationKind.FULL -> { moonshine(); kitten(); gemma() }
            ModelPreparationKind.ASR -> moonshine()
            ModelPreparationKind.KITTEN -> kitten()
            ModelPreparationKind.GEMMA -> gemma()
            ModelPreparationKind.SEMANTIC -> semantic()
        }
        progress("準備ができました", 100)
    }
}
