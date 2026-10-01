package com.eltnegcellist.emma.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KittenPhonemizerTest {
    private val dictionary = """
        HELLO  HH AH0 L OW1
        I  AY1
        HAVE  HH AE1 V
        READ  R IY1 D
        READ(2)  R EH1 D
        THIS  DH IH1 S
        BOOK  B UH1 K
    """.trimIndent()

    @Test
    fun convertsCmuArpabetToIpa() {
        val phonemizer = KittenPhonemizer.fromDictionaryText(dictionary)
        assertEquals("həlˈoʊ", phonemizer.phonemize("hello"))
    }

    @Test
    fun selectsPastReadAfterPerfectAuxiliary() {
        val phonemizer = KittenPhonemizer.fromDictionaryText(dictionary)
        val value = phonemizer.phonemize("I have read this book.")
        assertTrue(value.contains("ɹˈɛd"))
    }

    @Test
    fun keepsInfantDirectedExceptionWithoutDictionaryEntry() {
        val phonemizer = KittenPhonemizer.fromDictionaryText(dictionary)
        assertEquals("pikəbˈu", phonemizer.phonemize("peekaboo"))
    }
}
