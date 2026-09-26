package com.eltnegcellist.emma.tts

import android.content.Context
import com.eltnegcellist.emma.model.InAppModelDownloader
import java.io.File

object KittenModelStore {
    const val MODEL_NAME = "kitten-nano-en-v0_8-fp32"
    const val MODEL_FILE = "kitten_tts_nano_v0_8.onnx"
    const val VOICES_FILE = "voices.npz"
    const val CMUDICT_FILE = "cmudict.dict"

    const val MODEL_URL =
        "https://huggingface.co/KittenML/kitten-tts-nano-0.8-fp32/resolve/main/$MODEL_FILE"
    const val VOICES_URL =
        "https://huggingface.co/KittenML/kitten-tts-nano-0.8-fp32/resolve/main/$VOICES_FILE"
    const val CMUDICT_URL =
        "https://cdn.jsdelivr.net/gh/cmusphinx/cmudict@74790861f652b15e4ac49015a90074ad62a27690/cmudict.dict"

    const val APPROX_DOWNLOAD_MB = 64

    fun directory(context: Context): File =
        File(context.filesDir, "tts/$MODEL_NAME")

    fun isInstalled(context: Context): Boolean = isInstalledAt(directory(context))

    fun downloadAndInstall(
        context: Context,
        progress: (Int?) -> Unit,
    ): Result<Unit> = runCatching {
        val target = directory(context)
        val staging = File(context.filesDir, "tts/$MODEL_NAME.part")
        staging.deleteRecursively()
        require(staging.mkdirs()) { "Kitten TTSの一時フォルダを作成できませんでした。" }

        try {
            progress(0)
            downloadPart(
                url = MODEL_URL,
                destination = File(staging, MODEL_FILE),
                minimumBytes = 50_000_000L,
                startPercent = 0,
                spanPercent = 84,
                progress = progress,
            )
            downloadPart(
                url = VOICES_URL,
                destination = File(staging, VOICES_FILE),
                minimumBytes = 3_000_000L,
                startPercent = 84,
                spanPercent = 7,
                progress = progress,
            )
            downloadPart(
                url = CMUDICT_URL,
                destination = File(staging, CMUDICT_FILE),
                minimumBytes = 2_000_000L,
                startPercent = 91,
                spanPercent = 9,
                progress = progress,
            )

            require(isInstalledAt(staging)) {
                "Kitten TTS Nanoに必要なファイルが不足しています。"
            }

            val backup = File(context.filesDir, "tts/$MODEL_NAME.old")
            backup.deleteRecursively()
            target.parentFile?.mkdirs()
            if (target.exists() && !target.renameTo(backup)) {
                error("旧Kitten TTSモデルを退避できませんでした。")
            }
            if (!staging.renameTo(target)) {
                backup.renameTo(target)
                error("Kitten TTSモデルを配置できませんでした。")
            }
            backup.deleteRecursively()

            // Remove obsolete model layouts after the direct ONNX install is complete.
            File(context.filesDir, "tts/kitten-nano-en-v0_8-int8").deleteRecursively()
            File(target, "espeak-ng-data").deleteRecursively()
            File(target, "model.fp32.onnx").delete()
            File(target, "voices.bin").delete()
            File(target, "tokens.txt").delete()

            progress(100)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun downloadPart(
        url: String,
        destination: File,
        minimumBytes: Long,
        startPercent: Int,
        spanPercent: Int,
        progress: (Int?) -> Unit,
    ) {
        InAppModelDownloader.download(
            url = url,
            destination = destination,
            minimumBytes = minimumBytes,
            freeSpaceMarginBytes = 192L * 1024L * 1024L,
        ) { state ->
            val mapped = state.percent?.let { percent ->
                (startPercent + (percent * spanPercent) / 100).coerceIn(0, 100)
            }
            progress(mapped ?: startPercent)
        }.getOrThrow()
    }

    private fun isInstalledAt(dir: File): Boolean {
        val model = File(dir, MODEL_FILE)
        val voices = File(dir, VOICES_FILE)
        val cmu = File(dir, CMUDICT_FILE)
        return model.isFile && model.length() > 50_000_000L &&
            voices.isFile && voices.length() > 3_000_000L &&
            cmu.isFile && cmu.length() > 2_000_000L
    }
}
