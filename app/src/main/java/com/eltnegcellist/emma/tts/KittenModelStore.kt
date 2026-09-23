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

object KittenModelStore {
    const val MODEL_NAME = "kitten-nano-en-v0_8-int8"
    const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$MODEL_NAME.tar.bz2"
    const val ARCHIVE_SHA256 =
        "6fa5be852612ce761094ba74ee6123b4fc4acfefa79bf64dc63acae4a83af2fd"
    const val APPROX_DOWNLOAD_MB = 31

    fun directory(context: Context): File =
        File(context.filesDir, "tts/$MODEL_NAME")

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
                minimumBytes = 29_000_000L,
                freeSpaceMarginBytes = 192L * 1024L * 1024L,
            ) { state ->
                progress(state.percent?.let { (it * 70) / 100 })
            }.getOrThrow()

            verifyArchive(archive)
            installArchive(context, archive) { installPercent ->
                progress(70 + (installPercent * 30) / 100)
            }.getOrThrow()
            progress(100)
        } finally {
            archive.delete()
        }
    }

    private fun installArchive(
        context: Context,
        archive: File,
        progress: (Int) -> Unit,
    ): Result<Unit> = runCatching {
        val target = directory(context)
        val staging = File(context.filesDir, "tts/$MODEL_NAME.part")
        staging.deleteRecursively()
        require(staging.mkdirs()) { "Kitten TTSの一時フォルダを作成できませんでした。" }

        var extractedFiles = 0
        try {
            FileInputStream(archive).use { source ->
                TarArchiveInputStream(
                    BZip2CompressorInputStream(BufferedInputStream(source)),
                ).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        require(!entry.isSymbolicLink && !entry.isLink) {
                            "リンクを含むKitten TTS書庫は利用できません。"
                        }
                        val relative = relativeModelPath(entry.name)
                        if (relative != null) {
                            val output = File(staging, relative)
                            require(
                                output.canonicalPath.startsWith(staging.canonicalPath + File.separator) ||
                                    output.canonicalPath == staging.canonicalPath,
                            ) { "危険なKitten TTS書庫パスです。" }

                            if (entry.isDirectory) {
                                output.mkdirs()
                            } else {
                                output.parentFile?.mkdirs()
                                FileOutputStream(output).use { tar.copyTo(it, 1024 * 1024) }
                                extractedFiles++
                                progress((extractedFiles * 8).coerceAtMost(95))
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }

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
            progress(100)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun relativeModelPath(entryName: String): String? {
        val cleaned = entryName.replace('\\', '/').trimStart('/')
        if (cleaned.isBlank() || cleaned.contains("../")) return null
        val prefix = "$MODEL_NAME/"
        return when {
            cleaned == MODEL_NAME -> ""
            cleaned.startsWith(prefix) -> cleaned.removePrefix(prefix)
            else -> cleaned
        }
    }

    private fun isInstalledAt(dir: File): Boolean {
        val model = File(dir, "model.int8.onnx")
        val voices = File(dir, "voices.bin")
        val tokens = File(dir, "tokens.txt")
        val espeak = File(dir, "espeak-ng-data")
        return model.isFile && model.length() > 20_000_000L &&
            voices.isFile && voices.length() > 0L &&
            tokens.isFile && tokens.length() > 0L &&
            espeak.isDirectory && (espeak.list()?.isNotEmpty() == true)
    }

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
            "Kitten TTS公式アーカイブのSHA-256が一致しません。"
        }
    }
}
