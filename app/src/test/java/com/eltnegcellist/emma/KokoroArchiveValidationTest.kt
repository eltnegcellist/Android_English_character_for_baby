package com.eltnegcellist.emma

import com.eltnegcellist.emma.tts.KokoroArchiveValidation
import java.io.File
import java.io.RandomAccessFile
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KokoroArchiveValidationTest {
    @Test fun acceptsRootDirectoryAndNormalizesEntries() {
        assertEquals("", KokoroArchiveValidation.relativePath("kokoro-multi-lang-v1_0/"))
        assertEquals("model.onnx", KokoroArchiveValidation.relativePath("kokoro-multi-lang-v1_0/model.onnx"))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsTraversal() { KokoroArchiveValidation.relativePath("kokoro-multi-lang-v1_0/../outside") }

    @Test fun incompleteTreeIsNotReady() {
        val dir = createTempDirectory("kokoro-test-").toFile()
        try {
            assertFalse(KokoroArchiveValidation.isInstalledAt(dir))
            File(dir, "model.onnx").writeBytes(ByteArray(1))
            assertFalse(KokoroArchiveValidation.isInstalledAt(dir))
        } finally { dir.deleteRecursively() }
    }

    @Test fun completeTreeIsReady() {
        val dir = createTempDirectory("kokoro-test-").toFile()
        try {
            KokoroArchiveValidation.requiredFiles.forEach { name ->
                val f = File(dir, name)
                f.parentFile?.mkdirs()
                RandomAccessFile(f, "rw").use {
                    it.setLength(when (name) { "model.onnx" -> 300_000_000L; "voices.bin" -> 20_000_000L; else -> 1L })
                }
            }
            listOf("phondata", "phontab", "phonindex", "en_dict").forEach { File(dir, "espeak-ng-data/$it").apply { parentFile?.mkdirs(); writeText("ok") } }
            File(dir, "espeak-ng-data/lang/gmw/en").apply { parentFile?.mkdirs(); writeText("ok") }
            assertTrue(KokoroArchiveValidation.isInstalledAt(dir))
            File(dir, "espeak-ng-data/phontab").delete()
            assertFalse(KokoroArchiveValidation.isInstalledAt(dir))
        } finally { dir.deleteRecursively() }
    }
}
