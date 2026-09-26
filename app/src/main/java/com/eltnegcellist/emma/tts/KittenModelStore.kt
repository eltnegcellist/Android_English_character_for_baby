package com.eltnegcellist.emma.tts

import android.content.Context
import com.eltnegcellist.emma.model.InAppModelDownloader
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest

object KittenModelStore {
    const val MODEL_NAME = "kitten-nano-en-v0_8-fp32"
    const val MODEL_FILE = "kitten_tts_nano_v0_8.onnx"
    const val VOICES_FILE = "voices.npz"
    const val CMUDICT_FILE = "cmudict.dict"

    private const val KITTEN_REVISION = "87b12ff7859cdebd9c055c987a586101fad5b650"
    const val MODEL_URL =
        "https://huggingface.co/KittenML/kitten-tts-nano-0.8-fp32/resolve/$KITTEN_REVISION/$MODEL_FILE"
    const val VOICES_URL =
        "https://huggingface.co/KittenML/kitten-tts-nano-0.8-fp32/resolve/$KITTEN_REVISION/$VOICES_FILE"
    private const val MODEL_SHA256 =
        "320564d2615f235de972ca27a7f39551c94185cfa24ca85b07a29084135f1e5e"
    private const val VOICES_SHA256 =
        "8aa7cee235abb0739cb51e6559685f65a4dacd95568833d05699b1633f519b3f"
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
            val modelFile = File(staging, MODEL_FILE)
            downloadPart(
                url = MODEL_URL,
                destination = modelFile,
                minimumBytes = 50_000_000L,
                startPercent = 0,
                spanPercent = 84,
                progress = progress,
            )
            verifySha256(modelFile, MODEL_SHA256)

            val voicesFile = File(staging, VOICES_FILE)
            downloadPart(
                url = VOICES_URL,
                destination = voicesFile,
                minimumBytes = 3_000_000L,
                startPercent = 84,
                spanPercent = 7,
                progress = progress,
            )
            verifySha256(voicesFile, VOICES_SHA256)

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

    private fun verifySha256(file: File, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { source ->
            DigestInputStream(BufferedInputStream(source), digest).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (input.read(buffer) >= 0) Unit
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == expected) {
            "Kitten TTS公式ファイルのSHA-256が一致しません: ${file.name}"
        }
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
