package com.eltnegcellist.emma

import android.content.Context
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal enum class RepetitionLabKind(val savedValue: String, val label: String) {
    ENGLISH_REPEAT("english_repeat", "Emmaの英語をまねする"),
    JAPANESE_CONTROL("japanese_control", "通常の日本語"),
}

internal data class RepetitionLabPrompt(
    val id: String,
    val kind: RepetitionLabKind,
    val text: String,
)

internal object RepetitionLabCorpus {
    val prompts = listOf(
        RepetitionLabPrompt("E01", RepetitionLabKind.ENGLISH_REPEAT, "Milk time!"),
        RepetitionLabPrompt("E02", RepetitionLabKind.ENGLISH_REPEAT, "Let's have some milk."),
        RepetitionLabPrompt("E03", RepetitionLabKind.ENGLISH_REPEAT, "Bath time!"),
        RepetitionLabPrompt("E04", RepetitionLabKind.ENGLISH_REPEAT, "Let's take a bath."),
        RepetitionLabPrompt("E05", RepetitionLabKind.ENGLISH_REPEAT, "Good morning!"),
        RepetitionLabPrompt("E06", RepetitionLabKind.ENGLISH_REPEAT, "Night-night."),
        RepetitionLabPrompt("E07", RepetitionLabKind.ENGLISH_REPEAT, "Time to sleep."),
        RepetitionLabPrompt("E08", RepetitionLabKind.ENGLISH_REPEAT, "Little hands!"),
        RepetitionLabPrompt("E09", RepetitionLabKind.ENGLISH_REPEAT, "Kick, kick!"),
        RepetitionLabPrompt("E10", RepetitionLabKind.ENGLISH_REPEAT, "Big hug!"),
        RepetitionLabPrompt("E11", RepetitionLabKind.ENGLISH_REPEAT, "Look at the book!"),
        RepetitionLabPrompt("E12", RepetitionLabKind.ENGLISH_REPEAT, "Let's read!"),
        RepetitionLabPrompt("E13", RepetitionLabKind.ENGLISH_REPEAT, "Outside time!"),
        RepetitionLabPrompt("E14", RepetitionLabKind.ENGLISH_REPEAT, "Rain, rain!"),
        RepetitionLabPrompt("E15", RepetitionLabKind.ENGLISH_REPEAT, "Let's eat!"),
        RepetitionLabPrompt("E16", RepetitionLabKind.ENGLISH_REPEAT, "I hear you!"),
        RepetitionLabPrompt("J01", RepetitionLabKind.JAPANESE_CONTROL, "ミルク飲もうね"),
        RepetitionLabPrompt("J02", RepetitionLabKind.JAPANESE_CONTROL, "お風呂入ろうね"),
        RepetitionLabPrompt("J03", RepetitionLabKind.JAPANESE_CONTROL, "おはよう"),
        RepetitionLabPrompt("J04", RepetitionLabKind.JAPANESE_CONTROL, "ねんねしようね"),
        RepetitionLabPrompt("J05", RepetitionLabKind.JAPANESE_CONTROL, "おててかわいいね"),
        RepetitionLabPrompt("J06", RepetitionLabKind.JAPANESE_CONTROL, "足をばたばたしてるね"),
        RepetitionLabPrompt("J07", RepetitionLabKind.JAPANESE_CONTROL, "ぎゅーしようね"),
        RepetitionLabPrompt("J08", RepetitionLabKind.JAPANESE_CONTROL, "絵本読もうか"),
        RepetitionLabPrompt("J09", RepetitionLabKind.JAPANESE_CONTROL, "お散歩行こうか"),
        RepetitionLabPrompt("J10", RepetitionLabKind.JAPANESE_CONTROL, "雨だね"),
        RepetitionLabPrompt("J11", RepetitionLabKind.JAPANESE_CONTROL, "ごはん食べようね"),
        RepetitionLabPrompt("J12", RepetitionLabKind.JAPANESE_CONTROL, "聞こえたよ"),
        RepetitionLabPrompt("J13", RepetitionLabKind.JAPANESE_CONTROL, "もう少し飲む？"),
        RepetitionLabPrompt("J14", RepetitionLabKind.JAPANESE_CONTROL, "気持ちいいね"),
        RepetitionLabPrompt("J15", RepetitionLabKind.JAPANESE_CONTROL, "かわいいね"),
        RepetitionLabPrompt("J16", RepetitionLabKind.JAPANESE_CONTROL, "ゆっくりでいいよ"),
    )
}

internal object RepetitionLabStore {
    private const val DIRECTORY = "repetition-lab"
    private const val RESULTS_FILE = "results.tsv"
    private const val SESSION_FILE = "session.txt"

    private fun directory(context: Context) = context.getDir(DIRECTORY, Context.MODE_PRIVATE)

    fun reset(context: Context, asrModel: String) {
        val dir = directory(context)
        dir.listFiles()?.forEach { it.deleteRecursively() }
        dir.mkdirs()
        dir.resolve(SESSION_FILE).writeText(
            buildString {
                append("created=").append(timestamp()).append('\n')
                append("asrModel=").append(clean(asrModel)).append('\n')
                append("purpose=Phase 0 repetition-guard ASR observation only; no production filtering\n")
            },
        )
        dir.resolve(RESULTS_FILE).writeText(
            "timestamp\tpromptId\tkind\tprompt\tdurationMs\tasrTranscript\taudioFile\n",
        )
    }

    fun resultCount(context: Context): Int {
        val file = directory(context).resolve(RESULTS_FILE)
        if (!file.isFile) return 0
        return file.readLines().drop(1).count { it.isNotBlank() }
    }

    fun save(
        context: Context,
        prompt: RepetitionLabPrompt,
        wavAudio: ByteArray,
        transcript: String,
        durationMs: Long,
    ) {
        val dir = directory(context)
        if (!dir.resolve(RESULTS_FILE).isFile) reset(context, "unknown")
        require(wavAudio.size >= 44) { "録音データが短すぎます。" }
        require(String(wavAudio.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF") {
            "録音データがWAVではありません。"
        }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val audioName = "${prompt.id}-$stamp.wav"
        dir.resolve(audioName).writeBytes(wavAudio)
        dir.resolve(RESULTS_FILE).appendText(
            listOf(
                timestamp(),
                prompt.id,
                prompt.kind.savedValue,
                clean(prompt.text),
                durationMs.toString(),
                clean(transcript),
                audioName,
            ).joinToString("\t") + "\n",
        )
    }

    fun writeZip(context: Context, output: OutputStream) {
        val dir = directory(context)
        ZipOutputStream(output.buffered()).use { zip ->
            dir.listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip, 32 * 1024) }
                    zip.closeEntry()
                }
        }
    }

    private fun clean(value: String): String =
        value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim()

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
