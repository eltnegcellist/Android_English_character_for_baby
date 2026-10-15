package com.eltnegcellist.emma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFullRemovalTest {
    @Test fun cleanupTargetsOnlyNamedGemmaFiles() {
        val name = "gemma-4-E2B-it.litertlm"
        assertEquals(
            setOf(name, "$name.part", "$name.download.part", "$name.download.meta"),
            LEGACY_GEMMA_FILE_NAMES.toSet()
        )
        assertTrue(LEGACY_GEMMA_FILE_NAMES.all { it.startsWith(name) && '/' !in it })
    }
}
