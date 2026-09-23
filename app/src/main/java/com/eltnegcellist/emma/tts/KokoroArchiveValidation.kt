package com.eltnegcellist.emma.tts

import java.io.File

/** Pure archive and extracted-tree checks shared by the installer and JVM tests. */
internal object KokoroArchiveValidation {
    private const val ROOT = "kokoro-multi-lang-v1_0"
    private const val MIN_MODEL_BYTES = 300_000_000L
    private const val MIN_VOICES_BYTES = 20_000_000L
    val requiredFiles = listOf("model.onnx", "voices.bin", "tokens.txt", "lexicon-us-en.txt")

    fun relativePath(rawName: String): String {
        val raw = rawName.replace('\\', '/')
        val relative = raw.removePrefix("$ROOT/").removePrefix("./")
        if (relative.isNotEmpty() && (relative.startsWith('/') || relative.split('/').any { it == ".." })) {
            error("危険な書庫パスです。")
        }
        return relative
    }

    fun isInstalledAt(dir: File): Boolean =
        requiredFiles.all { File(dir, it).isFile && File(dir, it).length() > 0 } &&
            File(dir, "model.onnx").length() >= MIN_MODEL_BYTES &&
            File(dir, "voices.bin").length() >= MIN_VOICES_BYTES &&
            listOf("phondata", "phontab", "phonindex", "en_dict").all {
                File(dir, "espeak-ng-data/$it").isFile && File(dir, "espeak-ng-data/$it").length() > 0
            } && File(dir, "espeak-ng-data/lang/gmw/en").isFile && File(dir, "espeak-ng-data/lang/gmw/en").length() > 0
}
