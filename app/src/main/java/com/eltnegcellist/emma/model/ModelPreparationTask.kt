package com.eltnegcellist.emma.model

import android.content.Context
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import com.eltnegcellist.emma.asr.MoonshineModelStore
import com.eltnegcellist.emma.ai.RuriSemanticModelStore
import com.eltnegcellist.emma.tts.PrerecordedVoiceStore

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
        fun recorded() {
            check(PrerecordedVoiceStore.isReady(context)) {
                "事前収録音源がAPKにありません。アプリを更新してください。"
            }
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
                recorded()
                val semanticEnabled = context
                    .getSharedPreferences("emma_speech", Context.MODE_PRIVATE)
                    .getBoolean("semantic_enabled", true)
                if (semanticEnabled) semantic()
            }
            ModelPreparationKind.FULL -> { moonshine(); recorded(); gemma() }
            ModelPreparationKind.ASR -> moonshine()
            ModelPreparationKind.KITTEN -> recorded()
            ModelPreparationKind.GEMMA -> gemma()
            ModelPreparationKind.SEMANTIC -> semantic()
        }
        progress("準備ができました", 100)
    }
}
