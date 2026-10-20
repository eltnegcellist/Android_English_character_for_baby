package com.eltnegcellist.emma

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageBrandTest {
    @Test
    fun productNameIsJapaneseOnlyForJapaneseLocale() {
        val previous = AppLanguage.languageState.value
        try {
            for (language in AppLanguage.supported) {
                AppLanguage.languageState.value = language
                assertEquals(if (language == "ja") "みつことば" else "CooTrio", AppLanguage.productName)
            }
        } finally {
            AppLanguage.languageState.value = previous
        }
    }
}
