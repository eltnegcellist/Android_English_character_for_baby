package com.eltnegcellist.emma.model

import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Shared HTTP downloader for large on-device models.
 *
 * Incomplete transfers are deliberately preserved as `.download.part`.
 * A later attempt resumes with HTTP Range when the server supports it.
 *
 * OkHttp is used instead of HttpURLConnection because Hugging Face large-file
 * downloads redirect to signed Xet/CDN URLs. OkHttp handles cross-host redirects
 * and transient reconnects more reliably while keeping Range checkpoints.
 */
internal object InAppModelDownloader {
    private const val BUFFER_BYTES = 4 * 1024 * 1024
    private const val HTTP_RANGE_NOT_SATISFIABLE = 416

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

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
        var response: Response? = null

        try {
            response = execute(url, resumeFrom, ifRange)

            if (resumeFrom > 0L && response.code == HTTP_RANGE_NOT_SATISFIABLE) {
                val remoteTotal = parseUnsatisfiedTotal(response.header("Content-Range"))
                if (remoteTotal != null && remoteTotal == resumeFrom && resumeFrom >= minimumBytes) {
                    installPartial(partial, destination)
                    metadata.delete()
                    onProgress(Progress(destination.length(), destination.length()))
                    return@runCatching destination
                }

                response.close()
                response = null
                partial.delete()
                metadata.delete()
                resumeFrom = 0L
                ifRange = null
                response = execute(url, 0L, null)
            }

            val code = response.code
            val resumed = resumeFrom > 0L && code == 206

            if (resumeFrom > 0L && !resumed) {
                // The server ignored Range, or If-Range detected a changed file.
                // Reuse this full response but overwrite the old checkpoint.
                partial.delete()
                metadata.delete()
                resumeFrom = 0L
            }

            val body = response.body ?: throw IOException("ダウンロード応答にデータがありません。")
            val responseBytes = body.contentLength().takeIf { it > 0L }
            val total = when {
                resumed -> parseContentRangeTotal(response.header("Content-Range"))
                    ?: responseBytes?.let { resumeFrom + it }
                else -> responseBytes
            }

            val remainingBytes = total?.let { (it - resumeFrom).coerceAtLeast(0L) }
            remainingBytes?.let { remaining ->
                val available = StatFs(
                    destination.parentFile?.absolutePath ?: destination.absolutePath,
                ).availableBytes
                check(available > remaining + freeSpaceMarginBytes) {
                    "空き容量が不足しています。端末の空き容量を増やしてからもう一度お試しください。"
                }
            }

            val newIfRange = response.header("ETag")
                ?: response.header("Last-Modified")
                ?: ifRange
            writeIfRange(metadata, newIfRange)

            var downloaded = resumeFrom
            onProgress(Progress(downloaded, total))

            body.byteStream().use { input ->
                FileOutputStream(partial, resumed).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var lastPercent = Progress(downloaded, total).percent ?: -1

                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedIOException("モデルのダウンロードが中断されました。")
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

            if (partial.length() < minimumBytes) {
                throw IOException("ダウンロードしたデータが不完全です。通信環境を確認してもう一度お試しください。")
            }
            total?.let { expected ->
                if (partial.length() != expected) {
                    throw IOException(
                        "ダウンロードしたデータのサイズが一致しません（" +
                            partial.length() + " / " + expected + " bytes）。",
                    )
                }
            }

            installPartial(partial, destination)
            metadata.delete()
            onProgress(Progress(destination.length(), total ?: destination.length()))
            destination
        } finally {
            response?.close()
            // Keep partial + metadata on failure so the next run can resume.
        }
    }

    private fun execute(
        url: String,
        rangeStart: Long,
        ifRange: String?,
    ): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mitsukotoba-Android/1.9.19")
            .header("Accept", "*/*")
            .apply {
                if (rangeStart > 0L) {
                    header("Range", "bytes=$rangeStart-")
                    if (!ifRange.isNullOrBlank()) header("If-Range", ifRange)
                }
            }
            .build()

        val response = client.newCall(request).execute()
        val code = response.code
        if (code in 200..299 || code == HTTP_RANGE_NOT_SATISFIABLE) {
            return response
        }

        response.close()
        val message = "ダウンロードに失敗しました（HTTP $code）。"
        if (code == 408 || code == 425 || code == 429 || code in 500..599) {
            throw IOException(message)
        }
        throw IllegalStateException(message)
    }

    private fun installPartial(partial: File, destination: File) {
        if (destination.exists() && !destination.delete()) {
            error("古いデータを置き換えられませんでした。")
        }
        if (!partial.renameTo(destination)) {
            error("ダウンロードしたデータを保存できませんでした。")
        }
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
