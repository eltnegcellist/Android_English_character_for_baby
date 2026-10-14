package com.eltnegcellist.emma.ai

import android.content.Context
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import com.eltnegcellist.emma.asr.MoonshineJapaneseAsr
import com.eltnegcellist.emma.tts.DiagnosticStore
import java.util.Locale

internal data class LiteDiagnosticComparison(
    val ruleScene: String,
    val ruleEnglish: String,
    val semanticScene: String,
    val semanticEnglish: String,
    val guardScene: String,
    val guardEnglish: String,
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
    private val responses = LiteResponseEngine().also {
        it.restoreRuriReplyDiversity(preferences.getString("ruri_reply_diversity_v1", null))
    }
    private val debugRuleResponses = LiteResponseEngine()
    private val debugSemanticResponses = LiteResponseEngine()
    private val debugGuardResponses = LiteResponseEngine()

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

        // Topic × Interaction is enabled by default. Respect any explicit saved OFF.
        val interactionEnabled = true // Topic and Interaction are inseparable in public Lite.
        // Live Lite never falls back to Japanese rule-based topic detection.
        // When Ruri is unavailable/disabled, select generic English instead.
        val selected = responses.respondRuri(transcript, "", semanticPrediction, interactionEnabled)
        // Save the rotated template IDs between sessions, not the parent's Japanese speech.
        preferences.edit().putString("ruri_reply_diversity_v1", responses.exportRuriReplyDiversity()).apply()
        lastTopic = selected.scene
        // Privacy-safe per-turn audit: no Japanese transcript or baby name.
        val rawTopic = semanticPrediction?.topic ?: "none"
        val rawInteraction = semanticPrediction?.interaction ?: "none"
        val score = { value: Double? -> "%.3f".format(Locale.US, value ?: 0.0) }
        val topicAccepted = selected.scene == rawTopic || selected.scene.startsWith("combined:${rawTopic}:")
        val interactionAccepted = selected.scene == "interaction:${rawInteraction}" ||
            (selected.scene.startsWith("combined:") && selected.scene.endsWith(":${rawInteraction}"))
        DiagnosticStore.mark(
            appContext,
            "ruri_route",
            "route=${selected.scene} reason=${selected.routingReason} " +
                "topic=${rawTopic} tP=${score(semanticPrediction?.probability)} " +
                "tM=${score(semanticPrediction?.margin)} tOK=${topicAccepted} " +
                "interaction=${rawInteraction} iP=${score(semanticPrediction?.interactionProbability)} " +
                "iM=${score(semanticPrediction?.interactionMargin)} iOK=${interactionAccepted} " +
                "iOn=${interactionEnabled}",
        )
        val adjusted = fitLevel(selected.english, level)

        DiagnosticStore.mark(
            appContext,
            "lite_response_selected",
            "scene=${selected.scene} semanticUsed=${selected.semanticUsed} " +
                "topic=${semanticPrediction?.topic ?: "none"} " +
                "topicProbability=${semanticPrediction?.probability ?: 0.0} " +
                "topicMargin=${semanticPrediction?.margin ?: 0.0} " +
                "interaction=${semanticPrediction?.interaction ?: "none"} " +
                "interactionProbability=${semanticPrediction?.interactionProbability ?: 0.0} " +
                "interactionMargin=${semanticPrediction?.interactionMargin ?: 0.0} " +
                "interactionEnabled=${interactionEnabled} level=${level.name}",
        )
        adjusted
    }

    internal fun debugCompareText(text: String): Result<LiteDiagnosticComparison> = runCatching {
        val transcript = text.trim()
        require(transcript.isNotBlank()) { "日本語を入力してください。" }

        check(RuriSemanticModelStore.isInstalled(appContext)) {
            "Ruri Semanticモデルが未準備です。設定からSemanticを有効にしてモデルを準備してください。"
        }
        val prediction = requireNotNull(semantic.predict(transcript).getOrThrow()) {
            "Semantic診断では長すぎる入力を判定できません。短い日本語でお試しください。"
        }

        val rule = debugRuleResponses.respond(transcript, "")
        val semanticResponse = debugSemanticResponses.respond(
            transcript,
            "",
            prediction.copy(mode = "semantic"),
        )
        val guardResponse = debugGuardResponses.respond(
            transcript,
            "",
            prediction.copy(mode = "guard"),
        )

        LiteDiagnosticComparison(
            ruleScene = rule.scene,
            ruleEnglish = rule.english,
            semanticScene = semanticResponse.scene,
            semanticEnglish = semanticResponse.english,
            guardScene = guardResponse.scene,
            guardEnglish = guardResponse.english,
            semanticTopic = prediction.topic,
            semanticProbability = prediction.probability,
            semanticMargin = prediction.margin,
            semanticContextUsed = semanticResponse.contextUsed,
        )
    }

    internal fun resetDebugConversationContext() {
        debugRuleResponses.resetConversationContext()
        debugSemanticResponses.resetConversationContext()
        debugGuardResponses.resetConversationContext()
    }

    fun createGenericEnglishIsland(
        level: EnglishLevel,
    ): Result<String> = runCatching {
        val selected = responses.respondGeneric()
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
