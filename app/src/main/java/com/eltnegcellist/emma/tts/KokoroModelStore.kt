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

object KokoroModelStore {
    const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"
    const val MODEL_PAGE = "https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html"
    const val ARCHIVE_SHA256 = "c5f7e2d2caf082bc1d20fb70334a61d99d20b484500aad32e7cf84c128ea3298"
    private const val ROOT = "kokoro-multi-lang-v1_0"
    private val requiredFiles = KokoroArchiveValidation.requiredFiles

    fun directory(context: Context) = File(context.filesDir, ROOT)
    fun isInstalled(context: Context): Boolean = isInstalledAt(directory(context))
    private fun isInstalledAt(dir: File): Boolean = KokoroArchiveValidation.isInstalledAt(dir)

    fun downloadAndInstall(
        context: Context,
        progress: (Int?) -> Unit,
    ): Result<Unit> = runCatching {
        val archive = File(context.cacheDir, "$ROOT.auto-download.tar.bz2")
        try {
            InAppModelDownloader.download(
                url = MODEL_URL,
                destination = archive,
                minimumBytes = 1_000_000L,
                freeSpaceMarginBytes = 768L * 1024L * 1024L,
            ) { state ->
                progress(state.percent?.let { (it * 70) / 100 })
            }.getOrThrow()

            installArchiveFile(context, archive) { installPercent ->
                progress(installPercent?.let { 70 + (it * 30) / 100 })
            }.getOrThrow()
            progress(100)
        } finally {
            archive.delete()
        }
    }

    private fun installArchiveFile(
        context: Context,
        archive: File,
        progress: (Int?) -> Unit,
    ): Result<Unit> = runCatching {
        require(archive.isFile && archive.length() > 0L) { "音声データを確認できません。" }

        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(archive).use { source ->
            DigestInputStream(BufferedInputStream(source), digest).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (input.read(buffer) >= 0) {
                    // DigestInputStream updates the digest while reading.
                }
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == ARCHIVE_SHA256) {
            "公式KokoroアーカイブのSHA-256が一致しません。"
        }

        val target = directory(context)
        val staging = File(context.filesDir, "$ROOT.part")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "一時フォルダを作れません。" }

        try {
            val found = mutableSetOf<String>()
            FileInputStream(archive).use { source ->
                TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(source))).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val relative = KokoroArchiveValidation.relativePath(entry.name)
                        if (relative.isEmpty() && entry.isDirectory) {
                            entry = tar.nextEntry
                            continue
                        }
                        require(!entry.isSymbolicLink && !entry.isLink) { "リンクを含む書庫は拒否します。" }
                        if (!entry.isDirectory) {
                            val out = File(staging, relative)
                            require(out.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                                "危険な書庫パスです。"
                            }
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { tar.copyTo(it, 1024 * 1024) }
                            if (relative in requiredFiles) {
                                found += relative
                                progress(found.size * 100 / requiredFiles.size)
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }

            require(requiredFiles.all { it in found } && isInstalledAt(staging)) {
                "Kokoroに必要なmodel.onnx、voices.bin、tokens.txt、US lexicon、espeak-ng-dataを確認できません。"
            }

            val backup = File(context.filesDir, "$ROOT.old")
            backup.deleteRecursively()
            if (target.exists() && !target.renameTo(backup)) error("旧Kokoroモデルを退避できません。")
            if (!staging.renameTo(target)) {
                backup.renameTo(target)
                error("Kokoroモデルを配置できません。")
            }
            backup.deleteRecursively()
        } finally {
            staging.deleteRecursively()
        }
    }
}
