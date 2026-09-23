package com.eltnegcellist.emma.tts

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Persistent breadcrumbs, native traces and optional generated-audio captures for Emma diagnostics. */
object DiagnosticStore {
    private const val FILE_NAME = "emma-diagnostics.txt"
    private const val MAX_BYTES = 256 * 1024
    private const val TRACE_DIR = "emma-traces"
    private const val MAX_TRACE_BYTES = 8L * 1024L * 1024L
    private const val MAX_TRACE_FILES = 6
    private const val AUDIO_DIR = "emma-audio"
    private const val AUDIO_MANIFEST = "manifest.txt"
    private const val MAX_AUDIO_FILES = 40
    private val traceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "emma-trace-capture").apply { isDaemon = true }
    }
    @Volatile private var captureFuture: Future<*>? = null

    @Synchronized
    fun mark(context: Context, phase: String, detail: String = "") {
        runCatching {
            val line = "${timestamp()} phase=$phase" +
                detail.takeIf { it.isNotBlank() }?.let { " detail=${it.replace(Regex("[\\r\\n]"), " ").take(240)}" }.orEmpty()
            val file = context.getFileStreamPath(FILE_NAME)
            file.appendText(line + "\n")
            if (file.length() > MAX_BYTES) {
                val tail = file.readLines().takeLast(1200).joinToString("\n") + "\n"
                file.writeText(tail)
            }
        }
    }

    @Synchronized
    fun clearAudioCaptures(context: Context) {
        runCatching {
            val directory = context.getDir(AUDIO_DIR, Context.MODE_PRIVATE)
            directory.listFiles()?.forEach { it.delete() }
            directory.resolve(AUDIO_MANIFEST).writeText(
                "created=${timestamp()} ${deviceInfo(context)}\n",
            )
        }.onFailure { mark(context, "audioCaptureClear", "failed=${it.javaClass.simpleName}") }
    }

    @Synchronized
    fun saveAudioCapture(
        context: Context,
        fileName: String,
        samples: ShortArray,
        sampleRate: Int,
        detail: String,
    ) {
        runCatching {
            require(samples.isNotEmpty()) { "audio capture is empty" }
            require(sampleRate > 0) { "invalid sample rate" }
            val directory = context.getDir(AUDIO_DIR, Context.MODE_PRIVATE)
            val safeStem = fileName
                .removeSuffix(".wav")
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(96)
                .ifBlank { "emma" }
            val file = directory.resolve("$safeStem.wav")
            file.outputStream().buffered().use { output ->
                PcmWav.writeMono16(output, samples, sampleRate)
            }
            val durationMs = samples.size.toLong() * 1000L / sampleRate
            directory.resolve(AUDIO_MANIFEST).appendText(
                "${timestamp()} file=${file.name} sampleRate=$sampleRate samples=${samples.size} durationMs=$durationMs " +
                    "detail=${detail.replace(Regex("[\\r\\n]"), " ").take(480)}\n",
            )
            pruneAudioFiles(directory)
        }.onFailure {
            mark(context, "audioCapture", "failed=${it.javaClass.simpleName} name=${fileName.take(80)}")
        }
    }

    @Synchronized
    fun saveAudioTextReport(context: Context, fileName: String, content: String) {
        runCatching {
            val directory = context.getDir(AUDIO_DIR, Context.MODE_PRIVATE)
            val safeName = fileName
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(96)
                .ifBlank { "report.txt" }
                .let { if (it.endsWith(".txt", ignoreCase = true)) it else "$it.txt" }
            directory.resolve(safeName).writeText(content)
        }.onFailure {
            mark(context, "audioTextReport", "failed=${it.javaClass.simpleName} name=${fileName.take(80)}")
        }
    }

    fun collectOnStartup(context: Context) {
        if (Build.VERSION.SDK_INT < 30) {
            mark(context, "startup", "exitInfo=unavailable_api ${deviceInfo(context)}")
            return
        }
        runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            val exit = manager?.getHistoricalProcessExitReasons(context.packageName, 0, 1)?.firstOrNull()
            if (exit == null) {
                mark(context, "startup", "previousExit=none ${deviceInfo(context)}")
                return
            }
            mark(context, "startup", "previousExit=${reasonName(exit.reason)} reasonCode=${exit.reason} status=${exit.status} importance=${exit.importance} timestamp=${exit.timestamp} pssKb=${exit.pss} rssKb=${exit.rss} ${deviceInfo(context)}")
            // The trace is protobuf/binary data. Copy it byte-for-byte; never decode it
            // as UTF-8. The framework's trace buffer is circular, so this must run now.
            captureFuture = traceExecutor.submit { captureTraces(context.applicationContext) }
        }
    }

    private fun captureTraces(context: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        runCatching {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return
            val directory = context.getDir(TRACE_DIR, Context.MODE_PRIVATE)
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            exits.filter { it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || it.reason == ApplicationExitInfo.REASON_CRASH }
                .forEach { exit ->
                    val stamp = exit.timestamp
                    val stem = "emma-${stamp}-${reasonName(exit.reason)}"
                    val marker = directory.resolve("$stem.done")
                    if (marker.exists()) return@forEach
                    val traceFile = directory.resolve("$stem.trace")
                    val metadata = buildString {
                        append("timestamp=$stamp\nreason=${reasonName(exit.reason)} reasonCode=${exit.reason}\n")
                        append("status=${exit.status} importance=${exit.importance} pssKb=${exit.pss} rssKb=${exit.rss}\n")
                        append("capturedByVersion=").append(deviceInfo(context)).append('\n')
                    }
                    var copied = 0L
                    var truncated = false
                    var copyFailed = false
                    val partialFile = directory.resolve("$stem.partial")
                    runCatching {
                        exit.traceInputStream?.use { input ->
                            partialFile.outputStream().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                while (copied < MAX_TRACE_BYTES) {
                                    val wanted = minOf(buffer.size.toLong(), MAX_TRACE_BYTES - copied).toInt()
                                    val count = input.read(buffer, 0, wanted)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    copied += count
                                }
                                truncated = input.read() >= 0
                            }
                        }
                        if (copied > 0) require(partialFile.renameTo(traceFile)) { "trace rename failed" }
                    }.onFailure { copyFailed = true; partialFile.delete(); traceFile.delete() }
                    val metadataFile = directory.resolve("$stem.txt")
                    metadataFile.writeText(metadata + when {
                        copyFailed -> "trace=copy_failed\n"
                        copied > 0 -> "traceBytes=$copied truncated=$truncated\n"
                        else -> "trace=unavailable_or_overwritten\n"
                    })
                    marker.writeText("captured\n")
                }
            pruneTraceFiles(directory)
        }.onFailure { mark(context, "traceCapture", "failed=${it.javaClass.simpleName}") }
    }

    private fun pruneTraceFiles(directory: java.io.File) {
        val retained = directory.listFiles { file -> file.extension == "trace" || file.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        retained.drop(MAX_TRACE_FILES * 2).forEach { it.delete() }
        directory.listFiles { file -> file.name.endsWith(".done") }
            ?.filter { marker ->
                val stem = marker.name.removeSuffix(".done")
                !directory.resolve("$stem.trace").exists() && !directory.resolve("$stem.txt").exists()
            }
            ?.forEach { it.delete() }
    }

    private fun pruneAudioFiles(directory: java.io.File) {
        directory.listFiles { file -> file.extension.equals("wav", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_AUDIO_FILES)
            ?.forEach { it.delete() }
    }

    /** Writes a ZIP containing logs, native traces and any captured generated-audio WAV/report files. */
    fun writeDetailsZip(context: Context, output: OutputStream) {
        runCatching { captureFuture?.get() }
        ZipOutputStream(output.buffered()).use { zip ->
            val log = read(context).toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry("emma-diagnostics.txt")); zip.write(log); zip.closeEntry()
            val traceDirectory = context.getDir(TRACE_DIR, Context.MODE_PRIVATE)
            traceDirectory.listFiles()?.filter { it.extension == "trace" || it.extension == "txt" }?.sortedBy { it.name }?.forEach { file ->
                zip.putNextEntry(ZipEntry("traces/${file.name}"))
                file.inputStream().use { it.copyTo(zip, 32 * 1024) }
                zip.closeEntry()
            }
            val audioDirectory = context.getDir(AUDIO_DIR, Context.MODE_PRIVATE)
            audioDirectory.listFiles()
                ?.filter { it.extension.equals("wav", ignoreCase = true) || it.extension.equals("txt", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    zip.putNextEntry(ZipEntry("audio/${file.name}"))
                    file.inputStream().use { it.copyTo(zip, 32 * 1024) }
                    zip.closeEntry()
                }
        }
    }

    private fun deviceInfo(context: Context): String {
        val version = runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "versionName=${packageInfo.versionName} versionCode=${packageInfo.longVersionCode}"
        }.getOrDefault("version=unavailable")
        return "$version sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER} model=${Build.MODEL}"
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "unknown"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_change"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user_stopped"
        else -> "other"
    }

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    fun read(context: Context): String {
        val file = context.getFileStreamPath(FILE_NAME)
        return runCatching {
            if (file.isFile) file.readText() else "Emma diagnostics are empty.\n"
        }.getOrDefault("Emma diagnostics could not be read.\n")
    }
}
