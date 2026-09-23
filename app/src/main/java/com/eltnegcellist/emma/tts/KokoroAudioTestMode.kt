package com.eltnegcellist.emma.tts

internal const val KOKORO_AUDIO_TEST_ONCE_KEY = "kokoro_audio_test_once"
internal const val KOKORO_AUDIO_TEST_TEXT = "Hello, little one! Look at your tiny hands! Wiggle, wiggle! What a happy day!"

enum class KokoroAudioTestMode(
    val savedValue: String,
    val label: String,
    val description: String,
) {
    PIPELINE_058(
        savedValue = "PIPELINE_058",
        label = "A：0.58×・逐次再生",
        description = "1短句目ができたら再生を始める逐次再生モードです。",
    ),
    FULL_BUFFER_058(
        savedValue = "FULL_BUFFER_058",
        label = "B：0.58×・全生成後に再生",
        description = "同じ0.58×ですが、全短句の生成完了後にMODE_STREAMで再生します。",
    ),
    FULL_BUFFER_070(
        savedValue = "FULL_BUFFER_070",
        label = "C：0.70×・分割生成・全生成後",
        description = "0.70×で短句ごとに生成し、全部そろってからMODE_STREAMで再生します。",
    ),
    WHOLE_070_STREAM(
        savedValue = "WHOLE_070_STREAM",
        label = "D：0.70×・全文1回生成",
        description = "同じ固定英文全体をKokoroへ1回で渡し、0.70×でMODE_STREAM再生します。",
    ),
    WHOLE_100_STREAM(
        savedValue = "WHOLE_100_STREAM",
        label = "E：1.00×・全文1回生成",
        description = "Dと同じ固定英文を全文1回で生成し、速度だけ1.00×へ戻します。",
    ),
    WHOLE_070_STATIC(
        savedValue = "WHOLE_070_STATIC",
        label = "F：0.70×・全文1回・静的再生",
        description = "Dと同じ固定英文・同じ0.70×全文生成ですが、PCMを全部AudioTrackへ書いてからMODE_STATICで再生します。",
    ),
    WHOLE_058_STATIC(
        savedValue = "WHOLE_058_STATIC",
        label = "G：0.58×・全文1回・静的再生",
        description = "Fと同じ固定英文・同じMODE_STATIC再生で、速度だけ0.58×にします。全文1回生成で発音内容が崩れるかを見る条件です。",
    ),
    SPLIT_058_STATIC(
        savedValue = "SPLIT_058_STATIC",
        label = "H：0.58×・短句別生成・静的再生",
        description = "固定英文を短句ごとに生成し、間を入れて結合したPCMを静的再生します。",
    );

    companion object {
        fun fromSaved(value: String?): KokoroAudioTestMode? =
            entries.firstOrNull { it.savedValue == value }
    }
}
