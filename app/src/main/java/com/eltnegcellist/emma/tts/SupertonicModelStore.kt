package com.eltnegcellist.emma.tts

import android.content.Context
import com.eltnegcellist.emma.model.InAppModelDownloader
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest

object SupertonicModelStore {
    const val MODEL_NAME = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
    const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$MODEL_NAME.tar.bz2"
    const val ARCHIVE_SHA256 =
        "82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427"

    private val requiredFiles = setOf(
        "duration_predictor.int8.onnx",
        "text_encoder.int8.onnx",
        "vector_estimator.int8.onnx",
        "vocoder.int8.onnx",
        "tts.json",
        "unicode_indexer.bin",
        "voice.bin",
    )

    fun directory(context: Context): File = File(context.filesDir, "tts/$MODEL_NAME")

    fun isInstalled(context: Context): Boolean = isInstalledAt(directory(context))

    fun downloadAndInstall(
        context: Context,
        progress: (Int?) -> Unit,
    ): Result<Unit> = runCatching {
        val archive = File(context.cacheDir, "$MODEL_NAME.auto-download.tar.bz2")
        try {
            InAppModelDownloader.download(
                url = MODEL_URL,
                destination = archive,
                minimumBytes = 120_000_000L,
                freeSpaceMarginBytes = 512L * 1024L * 1024L,
            ) { state ->
                progress(state.percent?.let { (it * 70) / 100 })
            }.getOrThrow()

            verifyArchive(archive)
            installArchiveFile(context, archive) { installPercent ->
                progress(70 + (installPercent * 30) / 100)
            }.getOrThrow()
            progress(100)
        } finally {
            archive.delete()
        }
    }

    private fun installArchiveFile(
        context: Context,
        archive: File,
        progress: (Int) -> Unit,
    ): Result<Unit> = runCatching {
        val target = directory(context)
        val staging = File(context.filesDir, "tts/$MODEL_NAME.part")
        staging.deleteRecursively()
        require(staging.mkdirs()) { "音声モデルの一時フォルダを作成できませんでした。" }

        try {
            val found = mutableSetOf<String>()
            FileInputStream(archive).use { source ->
                TarArchiveInputStream(
                    BZip2CompressorInputStream(BufferedInputStream(source)),
                ).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        require(!entry.isSymbolicLink && !entry.isLink) {
                            "リンクを含む音声モデル書庫は利用できません。"
                        }
                        if (!entry.isDirectory) {
                            val relative = relativeModelPath(entry.name)
                            if (relative != null && relative in requiredFiles) {
                                val output = File(staging, relative)
                                require(
                                    output.canonicalPath.startsWith(staging.canonicalPath + File.separator),
                                ) { "危険な音声モデル書庫パスです。" }
                                output.parentFile?.mkdirs()
                                FileOutputStream(output).use { tar.copyTo(it, 1024 * 1024) }
                                found += relative
                                progress(found.size * 100 / requiredFiles.size)
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }

            require(found.containsAll(requiredFiles) && isInstalledAt(staging)) {
                "Supertonic 3に必要なファイルが不足しています。"
            }

            val backup = File(context.filesDir, "tts/$MODEL_NAME.old")
            backup.deleteRecursively()
            target.parentFile?.mkdirs()
            if (target.exists() && !target.renameTo(backup)) {
                error("旧音声モデルを退避できませんでした。")
            }
            if (!staging.renameTo(target)) {
                backup.renameTo(target)
                error("音声モデルを配置できませんでした。")
            }
            backup.deleteRecursively()

            File(context.filesDir, "kokoro-multi-lang-v1_0").deleteRecursively()
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun relativeModelPath(entryName: String): String? {
        val cleaned = entryName.replace('\\', '/').trimStart('/')
        if (cleaned.contains("../")) return null
        val prefix = "$MODEL_NAME/"
        return when {
            cleaned.startsWith(prefix) -> cleaned.removePrefix(prefix)
            cleaned in requiredFiles -> cleaned
            else -> null
        }
    }

    private fun isInstalledAt(dir: File): Boolean =
        requiredFiles.all { name -> File(dir, name).let { it.isFile && it.length() > 0L } }

    private fun verifyArchive(file: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { source ->
            DigestInputStream(BufferedInputStream(source), digest).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (input.read(buffer) >= 0) Unit
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == ARCHIVE_SHA256) {
            "Supertonic 3公式アーカイブのSHA-256が一致しません。"
        }
    }
}
