package com.eltnegcellist.emma.model

import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared HTTP downloader for large on-device models.
 *
 * Incomplete transfers are deliberately preserved as `.download.part`.
 * A later attempt resumes with HTTP Range when the server supports it.
 */
internal object InAppModelDownloader {
    private const val BUFFER_BYTES = 4 * 1024 * 1024
    private const val MAX_REDIRECTS = 8
    private const val HTTP_RANGE_NOT_SATISFIABLE = 416

    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) {
        val percent: Int?
            get() = totalBytes?.takeIf { it > 0L }?.let {
                ((downloadedBytes * 100L) / it).toInt().coerceIn(0, 100)
            }
    }

    fun download(
        url: String,
        destination: File,
        minimumBytes: Long = 1L,
        freeSpaceMarginBytes: Long = 256L * 1024L * 1024L,
        onProgress: (Progress) -> Unit,
    ): Result<File> = runCatching {
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, destination.name + ".download.part")
        val metadata = File(destination.parentFile, destination.name + ".download.meta")

        var resumeFrom = partial.takeIf { it.isFile }?.length()?.coerceAtLeast(0L) ?: 0L
        var ifRange = readIfRange(metadata)

        var connection: HttpURLConnection? = null
        try {
            connection = openFollowingRedirects(url, resumeFrom, ifRange)

            if (resumeFrom > 0L && connection.responseCode == HTTP_RANGE_NOT_SATISFIABLE) {
                val remoteTotal = parseUnsatisfiedTotal(connection.getHeaderField("Content-Range"))
                if (remoteTotal != null && remoteTotal == resumeFrom && resumeFrom >= minimumBytes) {
                    installPartial(partial, destination)
                    metadata.delete()
                    onProgress(Progress(destination.length(), destination.length()))
                    return@runCatching destination
                }

                partial.delete()
                metadata.delete()
                resumeFrom = 0L
                ifRange = null
                connection.disconnect()
                connection = openFollowingRedirects(url, 0L, null)
            }

            val resumed =
                resumeFrom > 0L && connection.responseCode == HttpURLConnection.HTTP_PARTIAL

            if (resumeFrom > 0L && !resumed) {
                // The server ignored Range, or If-Range detected a changed file.
                // Reuse this full response but overwrite the old checkpoint.
                partial.delete()
                metadata.delete()
                resumeFrom = 0L
            }

            val responseBytes = connection.contentLengthLong.takeIf { it > 0L }
            val total = when {
                resumed -> parseContentRangeTotal(connection.getHeaderField("Content-Range"))
                    ?: responseBytes?.let { resumeFrom + it }
                else -> responseBytes
            }

            val remainingBytes = total?.let { (it - resumeFrom).coerceAtLeast(0L) }
            remainingBytes?.let { remaining ->
                val available = StatFs(
                    destination.parentFile?.absolutePath ?: destination.absolutePath,
                ).availableBytes
                require(available > remaining + freeSpaceMarginBytes) {
                    "空き容量が不足しています。端末の空き容量を増やしてからもう一度お試しください。"
                }
            }

            val newIfRange = connection.getHeaderField("ETag")
                ?: connection.getHeaderField("Last-Modified")
                ?: ifRange
            writeIfRange(metadata, newIfRange)

            var downloaded = resumeFrom
            onProgress(Progress(downloaded, total))

            connection.inputStream.use { input ->
                FileOutputStream(partial, resumed).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var lastPercent = Progress(downloaded, total).percent ?: -1

                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("モデルのダウンロードが中断されました。")
                        }

                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue

                        output.write(buffer, 0, read)
                        downloaded += read

                        val progress = Progress(downloaded, total)
                        val percent = progress.percent
                        if (percent == null || percent != lastPercent) {
                            if (percent != null) lastPercent = percent
                            onProgress(progress)
                        }
                    }
                    output.fd.sync()
                }
            }

            require(partial.length() >= minimumBytes) {
                "ダウンロードしたデータが不完全です。通信環境を確認してもう一度お試しください。"
            }
            total?.let { expected ->
                require(partial.length() == expected) {
                    "ダウンロードしたデータのサイズが一致しません。もう一度お試しください。"
                }
            }

            installPartial(partial, destination)
            metadata.delete()
            onProgress(Progress(destination.length(), total ?: destination.length()))
            destination
        } finally {
            connection?.disconnect()
            // Keep partial + metadata on failure so the next run can resume.
        }
    }

    private fun installPartial(partial: File, destination: File) {
        if (destination.exists() && !destination.delete()) {
            error("古いデータを置き換えられませんでした。")
        }
        if (!partial.renameTo(destination)) {
            error("ダウンロードしたデータを保存できませんでした。")
        }
    }

    private fun openFollowingRedirects(
        sourceUrl: String,
        rangeStart: Long,
        ifRange: String?,
    ): HttpURLConnection {
        var current = sourceUrl
        repeat(MAX_REDIRECTS + 1) { attempt ->
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mitsukotoba-Android")
                if (rangeStart > 0L) {
                    setRequestProperty("Range", "bytes=$rangeStart-")
                    if (!ifRange.isNullOrBlank()) {
                        setRequestProperty("If-Range", ifRange)
                    }
                }
            }

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: error("ダウンロード先を確認できませんでした。")
                connection.disconnect()
                current = URL(URL(current), location).toString()
            } else {
                require(code in 200..299 || code == HTTP_RANGE_NOT_SATISFIABLE) {
                    "ダウンロードに失敗しました（HTTP $code）。"
                }
                return connection
            }

            if (attempt == MAX_REDIRECTS) {
                error("ダウンロードの転送回数が多すぎます。")
            }
        }
        error("ダウンロードを開始できませんでした。")
    }

    private fun parseContentRangeTotal(value: String?): Long? {
        val slash = value?.lastIndexOf('/') ?: return null
        return value.substring(slash + 1)
            .trim()
            .takeIf { it != "*" }
            ?.toLongOrNull()
    }

    private fun parseUnsatisfiedTotal(value: String?): Long? {
        val prefix = "bytes */"
        return value
            ?.takeIf { it.startsWith(prefix, ignoreCase = true) }
            ?.substring(prefix.length)
            ?.trim()
            ?.toLongOrNull()
    }

    private fun readIfRange(file: File): String? =
        runCatching {
            file.takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }.getOrNull()

    private fun writeIfRange(file: File, value: String?) {
        if (value.isNullOrBlank()) return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(value)
        }
    }
}
