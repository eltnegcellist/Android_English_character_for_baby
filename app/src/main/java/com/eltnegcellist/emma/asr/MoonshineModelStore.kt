package com.eltnegcellist.emma.asr

import android.content.Context
import ai.moonshine.voice.AssetDownloader
import ai.moonshine.voice.ModelSpec
import java.io.File

object MoonshineModelStore {
    private fun spec(model: MoonshineAsrModel): ModelSpec =
        ModelSpec.stt(
            "ja",
            model.arch,
            false,
        )

    fun directory(context: Context, model: MoonshineAsrModel): File =
        File(context.filesDir, "asr/${model.modelName}")

    fun isInstalled(context: Context, model: MoonshineAsrModel): Boolean = runCatching {
        val dir = directory(context, model)
        dir.isDirectory && AssetDownloader().isModelPresent(dir, spec(model))
    }.getOrDefault(false)

    fun downloadAndInstall(
        context: Context,
        model: MoonshineAsrModel,
        progress: (Int?) -> Unit,
    ): Result<Unit> = runCatching {
        val dir = directory(context, model)
        require(dir.exists() || dir.mkdirs()) {
            "Moonshineのモデルフォルダを作成できませんでした。"
        }

        val modelSpec = spec(model)
        val downloader = AssetDownloader()
        downloader.ensureModelPresent(dir, modelSpec) { _, fileIndex, totalFiles, downloaded, total ->
            val fileFraction = if (total > 0L) {
                (downloaded.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val overall = if (totalFiles > 0) {
                (((fileIndex - 1).toDouble() + fileFraction) / totalFiles.toDouble() * 100.0)
                    .toInt()
                    .coerceIn(0, 99)
            } else {
                null
            }
            progress(overall)
        }

        require(downloader.isModelPresent(dir, modelSpec)) {
            "Moonshine 日本語${model.shortLabel}に必要なファイルが不足しています。"
        }
        progress(100)
    }
}
