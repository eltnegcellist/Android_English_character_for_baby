package com.eltnegcellist.emma.asr

import android.content.Context
import ai.moonshine.voice.AssetDownloader
import ai.moonshine.voice.JNI
import ai.moonshine.voice.ModelSpec
import java.io.File

object MoonshineModelStore {
    const val MODEL_NAME = "moonshine-tiny-streaming-ja"
    const val APPROX_DOWNLOAD_MB = 32

    private val spec: ModelSpec
        get() = ModelSpec.stt(
            "ja",
            JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING,
            false,
        )

    fun directory(context: Context): File =
        File(context.filesDir, "asr/$MODEL_NAME")

    fun isInstalled(context: Context): Boolean = runCatching {
        val dir = directory(context)
        dir.isDirectory && AssetDownloader().isModelPresent(dir, spec)
    }.getOrDefault(false)

    fun downloadAndInstall(
        context: Context,
        progress: (Int?) -> Unit,
    ): Result<Unit> = runCatching {
        val dir = directory(context)
        require(dir.exists() || dir.mkdirs()) {
            "Moonshineのモデルフォルダを作成できませんでした。"
        }

        val downloader = AssetDownloader()
        downloader.ensureModelPresent(dir, spec) { _, fileIndex, totalFiles, downloaded, total ->
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

        require(downloader.isModelPresent(dir, spec)) {
            "Moonshine 日本語Tinyに必要なファイルが不足しています。"
        }
        progress(100)
    }
}
