package com.eltnegcellist.emma.ai

enum class EnglishLevel(val label: String, val example: String, val maxWords: Int, val instruction: String) {
    FIRST_WORDS("とてもやさしい", "Warm milk. Yummy!", 15,
        "Use only very common concrete words like milk, warm, hand, look, nice, happy, sleepy. Keep grammar extremely simple. No idioms, abstract words, or complex grammar. Gentle repetition is welcome."),
    EASY("やさしい", "Your milk is warm. Yummy!", 24,
        "Use everyday beginner English. Prefer simple present tense and short clauses. No idioms or abstract vocabulary."),
    NATURAL("ふつう", "Your milk is nice and warm. Enjoy it!", 32,
        "Use natural everyday English. Prefer clear clauses and concrete words. Avoid technical words and obscure idioms.");

    companion object {
        fun fromSaved(value: String?): EnglishLevel = entries.firstOrNull { it.name == value } ?: NATURAL
    }
}

internal object EnglishOutput {
    fun validate(text: String, level: EnglishLevel, maxWords: Int = level.maxWords): String {
        val clean = text.trim().trim('"', '“', '”')
        val words = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?").findAll(clean).count()
        require(words in 1..maxWords) { "英語が長すぎるか、生成できませんでした。もう一度お試しください。" }
        require(!Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]").containsMatchIn(clean)) {
            "英語以外の文章が生成されたため、読み上げを見送りました。"
        }
        return clean
    }
}
