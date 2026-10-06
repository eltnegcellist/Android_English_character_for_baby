package com.eltnegcellist.emma.ai

import android.content.Context
import com.eltnegcellist.emma.model.InAppModelDownloader
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Android copy of the Semantic Lite assets used by the Web app.
 *
 * The asset directory name includes the model hash, and every file is verified
 * again after download before it becomes the active install.
 */
internal object RuriSemanticModelStore {
    const val MODEL_NAME = "ruri70-int8-bd500193"
    const val APPROX_DOWNLOAD_MB = 78

    private const val BASE_URL =
        "https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/" +
            "semantic-assets/ruri70-int8-bd500193/"

    private const val MODEL_BYTES = 71_248_404L
    private const val TOKENIZER_BYTES = 6_724_873L
    private const val TOPIC_HEAD_BYTES = 33_880L

    private const val MODEL_SHA256 =
        "bd500193003fdeaba8c5422a47b974b40b49a5e0c91285c76f6bd949d2205264"
    private const val TOKENIZER_SHA256 =
        "0a94ac9a0a02c067bdef25b72ae9f4ee33f48f552e55988d444f6d25eeb1d062"
    private const val TOPIC_HEAD_SHA256 =
        "1391e9595ec2b932c0f915647c7d184112dadcce281bab59e6de37cc45e2bb29"

    private const val TOTAL_BYTES = MODEL_BYTES + TOKENIZER_BYTES + TOPIC_HEAD_BYTES

    fun directory(context: Context): File =
        File(context.filesDir, "semantic/" + MODEL_NAME)

    fun isInstalled(context: Context): Boolean = isInstalledAt(directory(context))

    fun downloadAndInstall(
        context: Context,
        progress: (Int?) -> Unit,
    ): Result<Unit> = runCatching {
        val downloadDir = File(context.filesDir, "model-downloads/" + MODEL_NAME)
        downloadDir.mkdirs()

        val model = File(downloadDir, "model.onnx")
        val tokenizer = File(downloadDir, "tokenizer.json")
        val topicHead = File(downloadDir, "head-topic.f32")

        var completed = 0L
        fun weightedProgress(fileDone: Long, fileTotal: Long) {
            val totalDone = completed + fileDone.coerceIn(0L, fileTotal)
            progress(((totalDone * 100L) / TOTAL_BYTES).toInt().coerceIn(0, 100))
        }

        InAppModelDownloader.download(
            url = BASE_URL + "model.onnx",
            destination = model,
            minimumBytes = MODEL_BYTES,
            freeSpaceMarginBytes = 192L * 1024L * 1024L,
        ) { state ->
            weightedProgress(state.downloadedBytes, MODEL_BYTES)
        }.getOrThrow()
        verifySha256(model, MODEL_SHA256, "Ruri Semanticモデル")
        completed += MODEL_BYTES

        InAppModelDownloader.download(
            url = BASE_URL + "tokenizer.json",
            destination = tokenizer,
            minimumBytes = TOKENIZER_BYTES,
            freeSpaceMarginBytes = 96L * 1024L * 1024L,
        ) { state ->
            weightedProgress(state.downloadedBytes, TOKENIZER_BYTES)
        }.getOrThrow()
        verifySha256(tokenizer, TOKENIZER_SHA256, "Ruri tokenizer")
        completed += TOKENIZER_BYTES

        InAppModelDownloader.download(
            url = BASE_URL + "head-topic.f32",
            destination = topicHead,
            minimumBytes = TOPIC_HEAD_BYTES,
            freeSpaceMarginBytes = 64L * 1024L * 1024L,
        ) { state ->
            weightedProgress(state.downloadedBytes, TOPIC_HEAD_BYTES)
        }.getOrThrow()
        verifySha256(topicHead, TOPIC_HEAD_SHA256, "Ruri話題分類ヘッド")

        installVerifiedFiles(context, model, tokenizer, topicHead)
        progress(100)
    }

    private fun installVerifiedFiles(
        context: Context,
        model: File,
        tokenizer: File,
        topicHead: File,
    ) {
        val target = directory(context)
        val staging = File(context.filesDir, "semantic/" + MODEL_NAME + ".part")
        val backup = File(context.filesDir, "semantic/" + MODEL_NAME + ".old")

        staging.deleteRecursively()
        require(staging.mkdirs()) { "Semanticモデルの一時フォルダを作成できませんでした。" }

        try {
            model.copyTo(File(staging, "model.onnx"), overwrite = true)
            tokenizer.copyTo(File(staging, "tokenizer.json"), overwrite = true)
            topicHead.copyTo(File(staging, "head-topic.f32"), overwrite = true)

            require(isInstalledAt(staging)) {
                "Semanticモデルに必要なファイルが不足しています。"
            }

            backup.deleteRecursively()
            target.parentFile?.mkdirs()
            if (target.exists() && !target.renameTo(backup)) {
                error("旧Semanticモデルを退避できませんでした。")
            }
            if (!staging.renameTo(target)) {
                backup.renameTo(target)
                error("Semanticモデルを配置できませんでした。")
            }
            backup.deleteRecursively()

            model.delete()
            tokenizer.delete()
            topicHead.delete()
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun isInstalledAt(dir: File): Boolean =
        File(dir, "model.onnx").let { it.isFile && it.length() == MODEL_BYTES } &&
            File(dir, "tokenizer.json").let { it.isFile && it.length() == TOKENIZER_BYTES } &&
            File(dir, "head-topic.f32").let { it.isFile && it.length() == TOPIC_HEAD_BYTES }

    private fun verifySha256(file: File, expected: String, label: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { source ->
            DigestInputStream(BufferedInputStream(source), digest).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (input.read(buffer) >= 0) Unit
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == expected) { "$label のSHA-256が一致しません。" }
    }
}
