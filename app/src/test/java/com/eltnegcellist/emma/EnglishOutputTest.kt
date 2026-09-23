package com.eltnegcellist.emma

import com.eltnegcellist.emma.ai.EnglishLevel
import com.eltnegcellist.emma.ai.EnglishOutput
import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishOutputTest {
    @Test fun newOrUnknownSettingUsesNaturalEnglish() {
        assertEquals(EnglishLevel.NATURAL, EnglishLevel.fromSaved(null))
        assertEquals(EnglishLevel.NATURAL, EnglishLevel.fromSaved("unknown"))
        assertEquals(EnglishLevel.EASY, EnglishLevel.fromSaved("EASY"))
    }

    @Test fun shortEnglishAndContractionsArePreserved() {
        assertEquals("It's warm. Yummy!", EnglishOutput.validate("  It's warm. Yummy!  ", EnglishLevel.FIRST_WORDS))
    }

    @Test(expected = IllegalArgumentException::class) fun JapaneseMixIsNotReadAsEnglish() {
        EnglishOutput.validate("Warm milk. ミルクです。", EnglishLevel.FIRST_WORDS)
    }

    @Test(expected = IllegalArgumentException::class) fun LongResponseIsNotSpokenAtSimplestLevel() {
        EnglishOutput.validate(List(19) { "milk" }.joinToString(" "), EnglishLevel.FIRST_WORDS)
    }

    @Test(expected = IllegalArgumentException::class) fun BlankResponseIsNotSpoken() {
        EnglishOutput.validate("...", EnglishLevel.EASY)
    }
}

