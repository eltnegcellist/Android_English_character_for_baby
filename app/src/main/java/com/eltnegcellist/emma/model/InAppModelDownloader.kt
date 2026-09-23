package com.eltnegcellist.emma.model

import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal object InAppModelDownloader {
    private const val BUFFER_BYTES = 4 * 1024 * 1024
    private const val MAX_REDIRECTS = 8

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
        partial.delete()

        var connection: HttpURLConnection? = null
        try {
            connection = openFollowingRedirects(url)
            val total = connection.contentLengthLong.takeIf { it > 0L }
            total?.let { expected ->
                val available = StatFs(destination.parentFile?.absolutePath ?: destination.absolutePath).availableBytes
                require(available > expected + freeSpaceMarginBytes) {
                    "空き容量が不足しています。端末の空き容量を増やしてからもう一度お試しください。"
                }
            }

            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var downloaded = 0L
                    var lastPercent = -1
                    while (true) {
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

            if (destination.exists() && !destination.delete()) {
                error("古いデータを置き換えられませんでした。")
            }
            if (!partial.renameTo(destination)) {
                error("ダウンロードしたデータを保存できませんでした。")
            }
            onProgress(Progress(destination.length(), total ?: destination.length()))
            destination
        } finally {
            connection?.disconnect()
            partial.delete()
        }
    }

    private fun openFollowingRedirects(sourceUrl: String): HttpURLConnection {
        var current = sourceUrl
        repeat(MAX_REDIRECTS + 1) { attempt ->
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Emma-Android")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: error("ダウンロード先を確認できませんでした。")
                connection.disconnect()
                current = URL(URL(current), location).toString()
            } else {
                require(code in 200..299) {
                    "ダウンロードに失敗しました（HTTP $code）。"
                }
                return connection
            }
            if (attempt == MAX_REDIRECTS) error("ダウンロードの転送回数が多すぎます。")
        }
        error("ダウンロードを開始できませんでした。")
    }
}
