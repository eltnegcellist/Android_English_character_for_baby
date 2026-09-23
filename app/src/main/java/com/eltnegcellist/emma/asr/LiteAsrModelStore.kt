package com.eltnegcellist.emma.asr

import android.content.Context
import android.net.Uri
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

object LiteAsrModelStore {
    const val MODEL_NAME = "sherpa-onnx-whisper-tiny"
    const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "$MODEL_NAME.tar.bz2"

    private const val ENCODER_SHA256 =
        "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"
    private const val DECODER_SHA256 =
        "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"

    private val requiredFiles = mapOf(
        "tiny-encoder.int8.onnx" to 12_000_000L,
        "tiny-decoder.int8.onnx" to 85_000_000L,
        "tiny-tokens.txt" to 700_000L,
    )

    fun directory(context: Context): File = File(context.filesDir, "asr/$MODEL_NAME")

    fun isInstalled(context: Context): Boolean = isInstalledAt(directory(context))

    fun importArchive(
        context: Context,
        uri: Uri,
        progress: (Int?) -> Unit,
    ): Result<Unit> = runCatching {
        val target = directory(context)
        val staging = File(context.filesDir, "asr/$MODEL_NAME.part")
        staging.deleteRecursively()
        require(staging.mkdirs()) { "ASRの一時フォルダを作成できませんでした。" }

        try {
            val found = mutableSetOf<String>()
            context.contentResolver.openInputStream(uri).use { source ->
                requireNotNull(source) { "ASRモデル書庫を開けませんでした。" }
                TarArchiveInputStream(
                    BZip2CompressorInputStream(BufferedInputStream(source)),
                ).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        require(!entry.isSymbolicLink && !entry.isLink) {
                            "リンクを含むASR書庫は利用できません。"
                        }
                        if (!entry.isDirectory) {
                            val relative = relativeModelPath(entry.name)
                            if (relative != null && relative in requiredFiles) {
                                val output = File(staging, relative)
                                require(
                                    output.canonicalPath.startsWith(staging.canonicalPath + File.separator),
                                ) { "危険なASR書庫パスです。" }
                                output.parentFile?.mkdirs()
                                FileOutputStream(output).use { tar.copyTo(it, 1024 * 1024) }
                                found += relative
                                progress(found.size * 80 / requiredFiles.size)
                            }
                        }
                        entry = tar.nextEntry
                    }
                }
            }

            require(requiredFiles.keys.all { it in found } && isInstalledAt(staging)) {
                "Whisper tinyに必要なファイルが不足しています。"
            }

            progress(85)
            verifySha256(
                File(staging, "tiny-encoder.int8.onnx"),
                ENCODER_SHA256,
                "Whisper tiny encoder",
            )
            progress(92)
            verifySha256(
                File(staging, "tiny-decoder.int8.onnx"),
                DECODER_SHA256,
                "Whisper tiny decoder",
            )

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
            progress(100)
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

    private fun isInstalledAt(directory: File): Boolean =
        requiredFiles.all { (name, minimumBytes) ->
            File(directory, name).let { it.isFile && it.length() >= minimumBytes }
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
            "$label のSHA-256が一致しません。公式sherpa-onnxモデルを選択してください。"
        }
    }
}
