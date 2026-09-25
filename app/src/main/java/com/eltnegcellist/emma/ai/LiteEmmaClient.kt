package com.eltnegcellist.emma.ai

import android.content.Context
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import com.eltnegcellist.emma.asr.MoonshineJapaneseAsr
import com.eltnegcellist.emma.tts.DiagnosticStore

class LiteEmmaClient(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("emma_speech", Context.MODE_PRIVATE)
    private val asr = MoonshineJapaneseAsr(appContext)
    private val responses = LiteResponseEngine()

    fun isReady(): Boolean = asr.isReady()

    fun initialize(model: MoonshineAsrModel): Result<Unit> = asr.initialize(model)

    fun createEnglishIsland(
        wavAudio: ByteArray,
        level: EnglishLevel,
        onTranscript: (String) -> Unit,
    ): Result<String> = runCatching {
        val transcript = asr.transcribe(wavAudio).getOrThrow()
        onTranscript(transcript)

        val audience = AudienceMode.fromSaved(preferences.getString("audience_mode", null))
        require(audience == AudienceMode.BABY) {
            "Emma Liteは「赤ちゃんへ」専用です。親との自由会話にはFullを使ってください。"
        }

        val babyName = preferences.getString("baby_name", "").orEmpty()
        val explicitSpokenName = preferences.getString("baby_spoken_name", "").orEmpty()
        val baseSpokenName = BabyNamePronunciation.toSpokenEnglish(babyName, explicitSpokenName)
        val spokenName = BabyNamePronunciation.withChanSuffix(
            baseSpokenName,
            preferences.getBoolean("use_chan_suffix", true),
        )
        val selected = responses.respond(transcript, spokenName)
        val adjusted = fitLevel(selected.english, level)

        DiagnosticStore.mark(
            appContext,
            "lite_response_selected",
            "scene=${selected.scene} score=${selected.score} level=${level.name} transcript=${transcript.take(80)}",
        )
        adjusted
    }

    fun close() = asr.close()

    private fun fitLevel(text: String, level: EnglishLevel): String {
        val maxWords = when (level) {
            EnglishLevel.FIRST_WORDS -> BabySpeechStyle.FIRST_WORDS_MAX_WORDS
            EnglishLevel.EASY -> BabySpeechStyle.EASY_MAX_WORDS
            EnglishLevel.NATURAL -> BabySpeechStyle.MAX_WORDS
        }
        val pieces = Regex("(?<=[.!?])\\s+")
            .split(text.trim())
            .filter { it.isNotBlank() }
            .toMutableList()

        fun words(): Int = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
            .findAll(pieces.joinToString(" "))
            .count()

        while (words() > maxWords && pieces.size > BabySpeechStyle.MIN_SENTENCES) {
            pieces.removeAt(pieces.lastIndex)
        }
        return pieces.joinToString(" ").ifBlank { text }
    }
}
