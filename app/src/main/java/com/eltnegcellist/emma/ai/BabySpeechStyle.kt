package com.eltnegcellist.emma.ai

internal object BabySpeechStyle {
    // Standard Emma should feel quick and rhythmic. 10-20 words is a target,
    // not a minimum quota: a natural reply under 10 words is allowed.
    const val TARGET_MIN_WORDS = 10
    const val MAX_WORDS = 20
    const val MIN_SENTENCES = 3
    const val MAX_SENTENCES = 5

    const val FIRST_WORDS_MAX_WORDS = 14
    const val EASY_MAX_WORDS = 18

    // Match Full mode: use the configured spoken name when it has not appeared
    // in either of the two most recent baby-directed replies.
    const val NAME_REPEAT_WINDOW = 2
}
