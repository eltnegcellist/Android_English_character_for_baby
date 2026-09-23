package com.eltnegcellist.emma.model

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object GemmaModelStore {
    const val MODEL_DOWNLOAD_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
    const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
    private const val MIN_EXPECTED_SIZE_BYTES = 2_000_000_000L
    private const val COPY_BUFFER_BYTES = 4 * 1024 * 1024
    private const val FREE_SPACE_MARGIN_BYTES = 512L * 1024L * 1024L

    fun modelFile(context: Context): File {
        val directory = File(context.filesDir, "models")
        return File(directory, MODEL_FILE_NAME)
    }

    fun hasUsableModel(context: Context): Boolean {
        val file = modelFile(context)
        return file.isFile && file.length() > MIN_EXPECTED_SIZE_BYTES
    }

    @Synchronized
    fun downloadModel(
        context: Context,
        onProgress: (Int?) -> Unit,
    ): Result<File> {
        val target = modelFile(context)
        return InAppModelDownloader.download(
            url = MODEL_DOWNLOAD_URL,
            destination = target,
            minimumBytes = MIN_EXPECTED_SIZE_BYTES,
            freeSpaceMarginBytes = FREE_SPACE_MARGIN_BYTES,
        ) { progress ->
            onProgress(progress.percent)
        }.mapCatching { file ->
            require(file.length() > MIN_EXPECTED_SIZE_BYTES) {
                "会話データのダウンロードが不完全です。"
            }
            onProgress(100)
            file
        }
    }

    @Synchronized
    fun importModel(
        context: Context,
        sourceUri: Uri,
        onProgress: (Int?) -> Unit,
    ): Result<File> = runCatching {
        val resolver = context.contentResolver
        val sourceInfo = querySourceInfo(context, sourceUri)

        sourceInfo.displayName?.let { name ->
            require(name.endsWith(".litertlm", ignoreCase = true)) {
                "Please select a .litertlm model file."
            }
        }

        sourceInfo.sizeBytes?.let { size ->
            require(size > MIN_EXPECTED_SIZE_BYTES) {
                "The selected file is too small to be Gemma 4 E2B."
            }
            val available = StatFs(context.filesDir.absolutePath).availableBytes
            require(available > size + FREE_SPACE_MARGIN_BYTES) {
                "Not enough free storage to copy the Gemma model into Emma."
            }
        }

        val target = modelFile(context)
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "$MODEL_FILE_NAME.part")
        partial.delete()

        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var copied = 0L
                    var lastReportedPercent = -1

                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        copied += read

                        val total = sourceInfo.sizeBytes
                        if (total != null && total > 0L) {
                            val percent = ((copied * 100L) / total).toInt().coerceIn(0, 100)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        } else {
                            onProgress(null)
                        }
                    }
                    output.fd.sync()
                }
            } ?: error("Could not open the selected model file.")

            require(partial.length() > MIN_EXPECTED_SIZE_BYTES) {
                "The copied model file is incomplete."
            }
            sourceInfo.sizeBytes?.takeIf { it >= 0 }?.let { expected ->
                require(partial.length() == expected) { "Model copy size does not match the source." }
            }
            // Same-directory POSIX rename replaces atomically; a failed rename preserves the old model.
            android.system.Os.rename(partial.absolutePath, target.absolutePath)
        } finally {
            partial.delete()
        }

        onProgress(100)
        target
    }

    private data class SourceInfo(
        val displayName: String?,
        val sizeBytes: Long?,
    )

    private fun querySourceInfo(context: Context, uri: Uri): SourceInfo {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex).takeIf { it > 0L } else null
                SourceInfo(name, size)
            } else {
                SourceInfo(null, null)
            }
        } finally {
            cursor?.close()
        }
    }
}


