package com.eltnegcellist.emma.asr

import android.content.Context
import com.eltnegcellist.emma.model.InAppModelDownloader
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ReazonSpeechModelStore {
    const val MODEL_NAME = "reazonspeech-k2-v2-int8"
    private const val REVISION = "a454b3fe1e63f4189ae3994248aeb3d31b6682f4"
    private const val BASE_URL =
        "https://huggingface.co/reazon-research/reazonspeech-k2-v2/resolve/$REVISION"

    private data class Asset(
        val name: String,
        val minimumBytes: Long,
        val expectedBytes: Long,
        val sha256: String?,
    )

    private val assets = listOf(
        Asset(
            "encoder-epoch-99-avg-1.int8.onnx",
            150_000_000L,
            154_670_139L,
            "2c7bd08a8a99f9ddd0d9e458456577b1f6279214e51426f114f9eced44c54e1d",
        ),
        Asset(
            "decoder-epoch-99-avg-1.onnx",
            11_000_000L,
            11_767_836L,
            "58b18211ae06265466bfa17172dab574df94f76c8bcb61a3640c28ba860e4124",
        ),
        Asset(
            "joiner-epoch-99-avg-1.int8.onnx",
            2_500_000L,
            2_696_970L,
            "49cc7ea1d3d35a40a27442db5e89996da64bf0e683a903dce76e99e57a12e4de",
        ),
        Asset("tokens.txt", 40_000L, 46_000L, null),
    )

    fun directory(context: Context): File = File(context.filesDir, "asr/$MODEL_NAME")

    fun isInstalled(context: Context): Boolean = isInstalledAt(directory(context))

    fun downloadAndInstall(
        context: Context,
        progress: (Int?) -> Unit,
    ): Result<Unit> = runCatching {
        val target = directory(context)
        val staging = File(context.filesDir, "asr/$MODEL_NAME.part")
        staging.deleteRecursively()
        require(staging.mkdirs()) { "ASRの一時フォルダを作成できませんでした。" }

        val expectedTotal = assets.sumOf { it.expectedBytes }
        var completedBytes = 0L

        try {
            for (asset in assets) {
                val output = File(staging, asset.name)
                val url = "$BASE_URL/${asset.name}?download=true"
                InAppModelDownloader.download(
                    url = url,
                    destination = output,
                    minimumBytes = asset.minimumBytes,
                    freeSpaceMarginBytes = 384L * 1024L * 1024L,
                ) { state ->
                    val current = completedBytes + state.downloadedBytes
                    progress(((current * 100L) / expectedTotal).toInt().coerceIn(0, 99))
                }.getOrThrow()

                asset.sha256?.let { expected ->
                    verifySha256(output, expected, asset.name)
                }
                completedBytes += output.length()
            }

            require(isInstalledAt(staging)) {
                "ReazonSpeechに必要なファイルが不足しています。"
            }

            val backup = File(context.filesDir, "asr/$MODEL_NAME.old")
            backup.deleteRecursively()
            target.parentFile?.mkdirs()
            if (target.exists() && !target.renameTo(backup)) {
                error("旧ASRモデルを退避できませんでした。")
            }
            if (!staging.renameTo(target)) {
                backup.renameTo(target)
                error("ASRモデルを配置できませんでした。")
            }
            backup.deleteRecursively()

            File(context.filesDir, "asr/sherpa-onnx-whisper-tiny").deleteRecursively()
            progress(100)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun isInstalledAt(dir: File): Boolean =
        assets.all { asset ->
            File(dir, asset.name).let { it.isFile && it.length() >= asset.minimumBytes }
        }

    private fun verifySha256(file: File, expected: String, label: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == expected) {
            "$label のSHA-256が一致しません。もう一度ダウンロードしてください。"
        }
    }
}
