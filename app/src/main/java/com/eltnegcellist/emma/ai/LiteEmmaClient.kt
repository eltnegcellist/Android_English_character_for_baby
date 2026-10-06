package com.eltnegcellist.emma.ai

import android.content.Context
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import com.eltnegcellist.emma.asr.MoonshineJapaneseAsr
import com.eltnegcellist.emma.tts.DiagnosticStore

internal data class LiteDiagnosticComparison(
    val ruleScene: String,
    val ruleEnglish: String,
    val semanticScene: String,
    val semanticEnglish: String,
    val semanticTopic: String?,
    val semanticProbability: Double?,
    val semanticMargin: Double?,
    val semanticContextUsed: Boolean,
)

class LiteEmmaClient(
    context: Context,
) {
    var lastTopic: String = "general"
        private set
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("emma_speech", Context.MODE_PRIVATE)
    private val asr = MoonshineJapaneseAsr(appContext)
    private val semantic = RuriSemanticClassifier(appContext)
    private val responses = LiteResponseEngine()
    private val debugRuleResponses = LiteResponseEngine()
    private val debugSemanticResponses = LiteResponseEngine()

    fun isReady(): Boolean = asr.isReady()

    fun resetConversationContext() = responses.resetConversationContext()

    fun initialize(model: MoonshineAsrModel): Result<Unit> =
        asr.initialize(model, useChildcareKeyterms = true)

    fun createEnglishIsland(
        wavAudio: ByteArray,
        level: EnglishLevel,
        onTranscript: (String) -> Unit,
    ): Result<String> = runCatching {
        val transcript = asr.transcribe(wavAudio).getOrThrow().trim()
        require(MeaningfulJapaneseUtterance.isMeaningful(transcript)) {
            "意味のある発話を聞き取れませんでした。"
        }
        onTranscript(transcript)

        val audience = AudienceMode.fromSaved(preferences.getString("audience_mode", null))
        require(audience == AudienceMode.BABY) {
            "みつことば Liteでは「呼びかけ」を使えます。AIも3者のやり取りへ継続参加する「会話」はFullを使ってください。"
        }

        val babyName = preferences.getString("baby_name", "").orEmpty()
        val explicitSpokenName = preferences.getString("baby_spoken_name", "").orEmpty()
        val baseSpokenName = BabyNamePronunciation.toSpokenEnglish(babyName, explicitSpokenName)
        val spokenName = BabyNamePronunciation.withChanSuffix(
            baseSpokenName,
            preferences.getBoolean("use_chan_suffix", true),
        )
        val semanticRequested = preferences.getBoolean("semantic_enabled", true)
        val semanticPrediction = if (
            semanticRequested &&
            RuriSemanticModelStore.isInstalled(appContext)
        ) {
            semantic.predict(transcript)
                .onFailure { error ->
                    DiagnosticStore.mark(
                        appContext,
                        "semantic_topic_fallback",
                        "reason=${error.message ?: error.javaClass.simpleName}",
                    )
                }
                .getOrNull()
        } else {
            null
        }

        val selected = responses.respond(transcript, spokenName, semanticPrediction)
        lastTopic = selected.scene
        val adjusted = fitLevel(selected.english, level)

        DiagnosticStore.mark(
            appContext,
            "lite_response_selected",
            "scene=${selected.scene} ruleScene=${selected.ruleScene} semanticUsed=${selected.semanticUsed} " +
                "semanticTopic=${semanticPrediction?.topic ?: "none"} score=${selected.score} " +
                "level=${level.name} transcript=${transcript.take(80)}",
        )
        adjusted
    }

    fun debugCompareText(text: String): Result<LiteDiagnosticComparison> = runCatching {
        val transcript = text.trim()
        require(transcript.isNotBlank()) { "日本語を入力してください。" }

        val babyName = preferences.getString("baby_name", "").orEmpty()
        val explicitSpokenName = preferences.getString("baby_spoken_name", "").orEmpty()
        val baseSpokenName = BabyNamePronunciation.toSpokenEnglish(babyName, explicitSpokenName)
        val spokenName = BabyNamePronunciation.withChanSuffix(
            baseSpokenName,
            preferences.getBoolean("use_chan_suffix", true),
        )
        check(RuriSemanticModelStore.isInstalled(appContext)) {
            "Ruri Semanticモデルが未準備です。設定からSemanticを有効にしてモデルを準備してください。"
        }
        val prediction = semantic.predict(transcript).getOrThrow()

        val rule = debugRuleResponses.respond(transcript, spokenName)
        val semanticResponse = debugSemanticResponses.respond(transcript, spokenName, prediction)

        LiteDiagnosticComparison(
            ruleScene = rule.scene,
            ruleEnglish = rule.english,
            semanticScene = semanticResponse.scene,
            semanticEnglish = semanticResponse.english,
            semanticTopic = prediction?.topic,
            semanticProbability = prediction?.probability,
            semanticMargin = prediction?.margin,
            semanticContextUsed = semanticResponse.contextUsed,
        )
    }

    fun resetDebugConversationContext() {
        debugRuleResponses.resetConversationContext()
        debugSemanticResponses.resetConversationContext()
    }

    fun createGenericEnglishIsland(
        level: EnglishLevel,
    ): Result<String> = runCatching {
        val babyName = preferences.getString("baby_name", "").orEmpty()
        val explicitSpokenName = preferences.getString("baby_spoken_name", "").orEmpty()
        val baseSpokenName = BabyNamePronunciation.toSpokenEnglish(babyName, explicitSpokenName)
        val spokenName = BabyNamePronunciation.withChanSuffix(
            baseSpokenName,
            preferences.getBoolean("use_chan_suffix", true),
        )
        val selected = responses.respondGeneric(spokenName)
        lastTopic = selected.scene
        val adjusted = fitLevel(selected.english, level)

        DiagnosticStore.mark(
            appContext,
            "lite_generic_response_selected",
            "scene=${selected.scene} level=${level.name} reason=manual_without_meaningful_speech",
        )
        adjusted
    }

    fun close() {
        asr.close()
        semantic.close()
    }

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
