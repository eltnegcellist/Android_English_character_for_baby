package com.eltnegcellist.emma

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eltnegcellist.emma.ai.AiCharacterName
import com.eltnegcellist.emma.ai.EnglishLevel
import com.eltnegcellist.emma.ai.LiteEmmaClient
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import com.eltnegcellist.emma.asr.MoonshineModelStore
import com.eltnegcellist.emma.audio.EndpointMode
import com.eltnegcellist.emma.audio.AudioRingRecorder
import com.eltnegcellist.emma.audio.VoiceActivityEvent
import com.eltnegcellist.emma.tts.DiagnosticStore
import com.eltnegcellist.emma.tts.PrerecordedVoiceStore
import com.eltnegcellist.emma.tts.KittenSpeaker

private const val NONVERBAL_RESPONSE_COOLDOWN_MS = 15_000L
private const val BABY_VOCAL_CONTEXT = "赤ちゃんが声を出している"

internal object ConversationRuntime {
    @Volatile private var instance: ConversationController? = null
    fun get(context: Context): ConversationController = instance ?: synchronized(this) {
        instance ?: ConversationController(context.applicationContext).also { instance = it }
    }
}

internal class ConversationController(val context: Context) {
    private val preferences = context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE)
    private val initialAsrModel = if (preferences.getBoolean("asr_model_manual", false)) MoonshineAsrModel.fromSaved(preferences.getString("asr_model", null)) else MoonshineAsrModel.SMALL
    private val onboardingOpen = !preferences.getBoolean("onboarding_completed_v4", false)
    val continueScreenOffState = mutableStateOf(preferences.getBoolean("continue_screen_off", false))
    var continueScreenOff by continueScreenOffState
    val historyEnabledState = mutableStateOf(preferences.getBoolean("history_enabled", true))
    var historyEnabled by historyEnabledState
    val history = ConversationHistory(context)
    private val playbackHistory = PlaybackHistoryGate<HistoryEntry>()
    private var sessionId = ""
    private var hasStartedConversation = false
    private var previousJapaneseGreeting: String? = null
    private var japaneseGreetingTts: TextToSpeech? = null
    private var japaneseGreetingGeneration = 0
    var uiVisible = false
    var onStopped: (() -> Unit)? = null
    var startRequested = false
        private set
    private val audio = context.getSystemService(android.media.AudioManager::class.java)
    private var foregroundFocus: android.media.AudioFocusRequest? = null
    private fun requestAudio(): Boolean {
        if (foregroundFocus != null) return true
        val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ change -> if (change < 0) stopSession() }, mainHandler).build()
        if (audio.requestAudioFocus(request) != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        foregroundFocus = request
        return true
    }
    private fun releaseAudio() { foregroundFocus?.let { audio.abandonAudioFocusRequest(it) }; foregroundFocus=null }
    private val resolvedAiName get() = AiCharacterName.resolve(aiName)
    val englishLevelState = mutableStateOf(EnglishLevel.fromSaved(preferences.getString("level", null)))
    var englishLevel by englishLevelState

    val asrModelState = mutableStateOf(initialAsrModel)
    var asrModel by asrModelState

    val latestTranscriptState = mutableStateOf("")
    var latestTranscript by latestTranscriptState

    val latestTopicState = mutableStateOf("generic")
    var latestTopic by latestTopicState

    val endpointModeState = mutableStateOf(EndpointMode.fromSaved(preferences.getString("endpoint_mode", null)))
    var endpointMode by endpointModeState

    fun updateEndpointMode(mode: EndpointMode) {
        if (recording || generating) return
        endpointMode = mode
        preferences.edit().putString("endpoint_mode", mode.savedValue).apply()
    }

    val autoRespondState = mutableStateOf(preferences.getBoolean("auto_respond", true))
    var autoRespond by autoRespondState

    val aiNameState = mutableStateOf(preferences.getString("ai_character_name", AiCharacterName.DEFAULT).orEmpty())
    var aiName by aiNameState

    val tutorialStepState = mutableStateOf<Int?>(
            if (!onboardingOpen && !preferences.getBoolean("tutorial_completed_v1", false) && !preferences.getBoolean("tutorial_dismissed_v1", false)) 0 else null,
        )
    var tutorialStep by tutorialStepState

    val mouthLevelState = mutableStateOf(0f)
    var mouthLevel by mouthLevelState

    val disposedState = mutableStateOf(false)
    var disposed by disposedState

    val generatingState = mutableStateOf(false)
    var generating by generatingState

    val voiceErrorState = mutableStateOf<String?>(null)
    var voiceError by voiceErrorState

    val statusState = mutableStateOf(ProductionEmmaStatus.IDLE)
    var status by statusState

    val statusMessageState = mutableStateOf("みつことばを準備しています。")
    var statusMessage by statusMessageState

    val latestEmmaTextState = mutableStateOf("")
    var latestEmmaText by latestEmmaTextState

    val recordingState = mutableStateOf(false)
    var recording by recordingState

    val autoStartPendingState = mutableStateOf(!onboardingOpen && tutorialStep == null)
    var autoStartPending by autoStartPendingState

    val pendingStartAfterPermissionState = mutableStateOf(false)
    var pendingStartAfterPermission by pendingStartAfterPermissionState

    val modelPresentState = mutableStateOf(
        (if(AppLanguage.selected(context)=="ja") MoonshineModelStore.isInstalled(context, initialAsrModel) else MultilingualRuntime.isCached(context)) && PrerecordedVoiceStore.isReady(context)
    )
    var modelPresent by modelPresentState

    val modelReadyState = mutableStateOf(false)
    var modelReady by modelReadyState

    val kittenInstalledState = mutableStateOf(PrerecordedVoiceStore.isReady(context))
    var kittenInstalled by kittenInstalledState

    val lastSpeechMillisState = mutableStateOf<Long?>(null)
    var lastSpeechMillis by lastSpeechMillisState

    val lastTtsGenerationMillisState = mutableStateOf<Long?>(null)
    var lastTtsGenerationMillis by lastTtsGenerationMillisState

    val lastTtsTotalMillisState = mutableStateOf<Long?>(null)
    var lastTtsTotalMillis by lastTtsTotalMillisState

    val lastEndpointToTtsRequestMillisState = mutableStateOf<Long?>(null)
    var lastEndpointToTtsRequestMillis by lastEndpointToTtsRequestMillisState

    val lastEndpointToFirstAudioMillisState = mutableStateOf<Long?>(null)
    var lastEndpointToFirstAudioMillis by lastEndpointToFirstAudioMillisState

    val endpointStartedNanosState = mutableStateOf<Long?>(null)
    var endpointStartedNanos by endpointStartedNanosState

    val ttsRequestedAfterEndpointMillisState = mutableStateOf<Long?>(null)
    var ttsRequestedAfterEndpointMillis by ttsRequestedAfterEndpointMillisState

    val lastNonverbalResponseAtMillisState = mutableStateOf(0L)
    var lastNonverbalResponseAtMillis by lastNonverbalResponseAtMillisState

    val screenIntroductionPlayedState = mutableStateOf(false)
    var screenIntroductionPlayed by screenIntroductionPlayedState

    val screenIntroductionPlayingState = mutableStateOf(false)
    var screenIntroductionPlaying by screenIntroductionPlayingState

    val tutorialUserSpokeState = mutableStateOf(false)
    var tutorialUserSpoke by tutorialUserSpokeState

    val mainHandler = Handler(Looper.getMainLooper())

    val session = SessionGate()

    val recorder = AudioRingRecorder(context.assets)

    val lite = LiteEmmaClient(context)

    val kitten = run {
        KittenSpeaker(
            context = context,
            onStarted = {
                val entry = playbackHistory.started(historyEnabled)
                if (historyEnabled && entry != null) history.append(entry)
            },
            onDone = { firstAudioMillis, generationMillis, totalMillis ->
                if (!disposed) {
                    if (!recording) releaseAudio()
                    mouthLevel = 0f
                    lastSpeechMillis = firstAudioMillis
                    lastTtsGenerationMillis = generationMillis
                    lastTtsTotalMillis = totalMillis
                    val queued = ttsRequestedAfterEndpointMillis
                    lastEndpointToTtsRequestMillis = queued
                    lastEndpointToFirstAudioMillis = queued?.plus(firstAudioMillis)
                    if (queued != null) {
                        DiagnosticStore.mark(
                            context,
                            "emma_response_latency",
                            "endpointToTtsRequestMs=$queued kittenToFirstAudioMs=$firstAudioMillis endpointToFirstAudioMs=${queued + firstAudioMillis} kittenTotalMs=$totalMillis",
                        )
                    }
                    endpointStartedNanos = null
                    ttsRequestedAfterEndpointMillis = null
                    if (screenIntroductionPlaying) {
                        screenIntroductionPlaying = false
                        screenIntroductionPlayed = true
                        status = ProductionEmmaStatus.IDLE
                        statusMessage = "自己紹介が終わりました。"
                    } else {
                        if (recording) recorder.resumeBuffering(clearExisting = true)
                        status = if (recording) ProductionEmmaStatus.LISTENING else ProductionEmmaStatus.IDLE
                        statusMessage = if (recording) {
                            if (autoRespond) "普通に話しかけてください。" else "話したところで「今すぐAIが返事する」を押してください。"
                        } else {
                            "試聴を終了しました。"
                        }

                        if (tutorialStep == 2 && tutorialUserSpoke) {
                            preferences.edit().putBoolean("tutorial_completed_v1", true).apply()
                            tutorialStep = null
                            tutorialUserSpoke = false
                            autoStartPending = false
                            statusMessage = if (recording) {
                                "チュートリアル完了。続けて話しかけてください。"
                            } else {
                                "チュートリアルが完了しました。"
                            }
                        }
                    }
                }
            },
            onError = { message ->
                if (!disposed) {
                    mouthLevel = 0f
                    playbackHistory.cancel()
                    if (!recording) releaseAudio()
                    voiceError = message
                    if (screenIntroductionPlaying) {
                        screenIntroductionPlaying = false
                        screenIntroductionPlayed = true
                    }
                    if (recording) recorder.resumeBuffering(clearExisting = true)
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "音声の読み込みまたは再生に失敗しました: $message"
                }
            },
            onAmplitude = { amplitude -> if (!disposed && uiVisible) mouthLevel = amplitude },
        )
    }

    fun speakEmma(text: String): Boolean {
        lastSpeechMillis = null
        lastTtsGenerationMillis = null
        lastTtsTotalMillis = null
        ttsRequestedAfterEndpointMillis = endpointStartedNanos?.let { started ->
            (System.nanoTime() - started) / 1_000_000L
        }
        return kittenInstalled && (recording && continueScreenOff || requestAudio()) && kitten.speak(text)
    }

    fun beginRecording() {
        startRequested = false
        sessionId = java.util.UUID.randomUUID().toString()
        session.start()
        runCatching { recorder.start(endpointMode) }
            .onSuccess {
                recording = true
                recorder.endpointFallbackReason?.let {
                    DiagnosticStore.mark(context, "silero_fallback", it)
                }
                EmmaWorkQueue.execute { lite.resetConversationContext() }
                status = ProductionEmmaStatus.LISTENING
                statusMessage = if (autoRespond) {
                    "普通に話しかけてください。必要なら「今すぐAIが返事する」で区切れます。"
                } else {
                    "話したところで「今すぐAIが返事する」を押してください。"
                }
                if (recorder.endpointFallbackReason != null) {
                    statusMessage += " 声の判定を準備できなかったため、今回は従来方式を使います。"
                }
                val isRestart = hasStartedConversation
                hasStartedConversation = true
                if (isRestart && AppLanguage.selected(context) == "ja" && tutorialStep == null) {
                    speakJapaneseResumeGreeting()
                }
            }
            .onFailure {
                stopSession()
                status = ProductionEmmaStatus.ERROR
                statusMessage = it.message ?: "録音を開始できませんでした。"
            }
    }

    /** The Qwen3 recorded bank has no Japanese clips. This greeting-only
     *  path uses Android's Japanese speech engine; normal English replies stay
     *  on the existing prerecorded playback path.
     */
    private fun speakJapaneseResumeGreeting() {
        val ticket = session.ticket()
        val generation = ++japaneseGreetingGeneration
        val text = JapaneseResumeGreeting.choose(previousJapaneseGreeting)
        previousJapaneseGreeting = text
        recorder.pauseBuffering()
        recorder.clear()
        status = ProductionEmmaStatus.SPEAKING
        statusMessage = "Emmaが挨拶しています。"
        try {
            lateinit var player: TextToSpeech
            player = TextToSpeech(context) { initStatus ->
                mainHandler.post {
                    if (generation != japaneseGreetingGeneration || !session.accepts(ticket) || disposed) {
                        return@post
                    }
                    if (initStatus != TextToSpeech.SUCCESS) {
                        finishJapaneseResumeGreeting(generation, ticket)
                        return@post
                    }
                    val languageStatus = player.setLanguage(Locale.JAPAN)
                    if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                        languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                        finishJapaneseResumeGreeting(generation, ticket)
                        return@post
                    }
                    player.setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    player.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            mainHandler.post { finishJapaneseResumeGreeting(generation, ticket) }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post { finishJapaneseResumeGreeting(generation, ticket) }
                        }
                    })
                    val result = player.speak(text, TextToSpeech.QUEUE_FLUSH, null, "resume-$generation")
                    if (result != TextToSpeech.SUCCESS) finishJapaneseResumeGreeting(generation, ticket)
                }
            }
            japaneseGreetingTts = player
            // A missing Android TTS completion callback must never keep the
            // microphone suspended indefinitely.
            mainHandler.postDelayed({
                finishJapaneseResumeGreeting(generation, ticket)
            }, 8_000L)
        } catch (error: Exception) {
            finishJapaneseResumeGreeting(generation, ticket)
        }
    }

    private fun finishJapaneseResumeGreeting(generation: Int, ticket: Long) {
        if (generation != japaneseGreetingGeneration) return
        cancelJapaneseResumeGreeting()
        if (disposed || !recording || !session.accepts(ticket)) return
        recorder.resumeBuffering(clearExisting = true)
        status = ProductionEmmaStatus.LISTENING
        statusMessage = if (autoRespond) {
            "普通に話しかけてください。"
        } else {
            "話したところで「今すぐAIが返事する」を押してください。"
        }
    }

    private fun cancelJapaneseResumeGreeting() {
        japaneseGreetingGeneration++
        val player = japaneseGreetingTts
        japaneseGreetingTts = null
        player?.runCatching {
            stop()
            shutdown()
        }
    }

    fun stopSession() {
        cancelJapaneseResumeGreeting()
        releaseAudio()
        startRequested = false
        playbackHistory.cancel()
        session.stop()
        pendingStartAfterPermission = false
        kitten.stop()
        recorder.stop()
        recording = false
        generating = false
        mouthLevel = 0f
        endpointStartedNanos = null
        ttsRequestedAfterEndpointMillis = null
        status = ProductionEmmaStatus.IDLE
        statusMessage = "呼びかけを止めました。"
        onStopped?.invoke()
    }

    private fun respondGenericallyToManualRequest() {
        if (!recording || generating || status == ProductionEmmaStatus.SPEAKING) return

        val ticket = session.ticket()
        val requestedLevel = englishLevel
        latestTranscript = ""
        latestEmmaText = ""
        generating = true
        recorder.pauseBuffering()
        recorder.clear()
        status = ProductionEmmaStatus.THINKING
        statusMessage = "返事を考えています…"

        DiagnosticStore.mark(
            context,
            "manual_generic_response_started",
            "mode=Lite reason=no_meaningful_speech",
        )

        EmmaWorkQueue.execute {
            val result = lite.createGenericEnglishIsland(requestedLevel)
            mainHandler.post {
                if (disposed) return@post
                if (!session.accepts(ticket)) return@post
                generating = false

                result.onSuccess { english ->
                    val spokenEnglish = AiCharacterName.stripLeadingSpeakerLabel(english, resolvedAiName)
                    latestEmmaText = spokenEnglish
                    latestTopic = "generic"
                    status = ProductionEmmaStatus.SPEAKING
                    statusMessage = "${resolvedAiName}が話しています。"
                    voiceError = null
                    playbackHistory.offer(
                        HistoryEntry.create(
                            sessionId,
                            "",
                            spokenEnglish,
                            "generic",
                            "lite",
                        )
                    )
                    if (!speakEmma(spokenEnglish)) {
                        playbackHistory.cancel()
                        recorder.resumeBuffering(clearExisting = true)
                        endpointStartedNanos = null
                        ttsRequestedAfterEndpointMillis = null
                        status = ProductionEmmaStatus.ERROR
                        statusMessage = voiceError ?: "音声を再生できませんでした。"
                    }
                }.onFailure { error ->
                    recorder.resumeBuffering(clearExisting = true)
                    endpointStartedNanos = null
                    ttsRequestedAfterEndpointMillis = null
                    status = ProductionEmmaStatus.ERROR
                    statusMessage =
                        "みつことばの生成に失敗しました: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    fun askEmma(automatic: Boolean = false) {
        if (!recording || generating || status == ProductionEmmaStatus.THINKING || status == ProductionEmmaStatus.SPEAKING) return
        val activeReady = lite.isReady()
        if (!modelReady || !activeReady) {
            status = ProductionEmmaStatus.ERROR
            statusMessage = "みつことばがまだ準備できていません。"
            return
        }

        val minimumSeconds = if (automatic) 0.45 else 0.8
        if (recorder.secondsAvailable() < minimumSeconds) {
            if (!automatic) respondGenericallyToManualRequest()
            return
        }

        if (endpointStartedNanos == null) endpointStartedNanos = System.nanoTime()
        val ticket = session.ticket()
        val requestedLevel = englishLevel
        latestTranscript = ""
        latestEmmaText = ""
        generating = true
        recorder.pauseBuffering()
        val wav = recorder.snapshotWav(maxSeconds = 30, consume = true)
        status = ProductionEmmaStatus.THINKING
        statusMessage = "返事を考えています…"

        DiagnosticStore.mark(
            context,
            "emma_turn_processing_started",
            "mode=Lite automatic=$automatic wavBytes=${wav.size}",
        )


        EmmaWorkQueue.execute {
            val result = lite.createEnglishIsland(wav, requestedLevel) { transcript ->
                mainHandler.post {
                    if (!disposed && session.accepts(ticket)) {
                        latestTranscript = transcript
                        statusMessage = "返答を選んでいます…"
                    }
                }
            }

            mainHandler.post {
                if (disposed) return@post
                if (!session.accepts(ticket)) return@post
                generating = false

                result.onSuccess { english ->
                    val nowMillis = System.currentTimeMillis()
                    val infantVocalEvent =
                        latestTranscript.trim().trimEnd('。') == BABY_VOCAL_CONTEXT

                    if (
                        infantVocalEvent &&
                        nowMillis - lastNonverbalResponseAtMillis < NONVERBAL_RESPONSE_COOLDOWN_MS
                    ) {
                        recorder.resumeBuffering(clearExisting = true)
                        endpointStartedNanos = null
                        ttsRequestedAfterEndpointMillis = null
                        latestEmmaText = ""
                        status = ProductionEmmaStatus.LISTENING
                        statusMessage = "赤ちゃんの声を聞いています。"
                        DiagnosticStore.mark(
                            context,
                            "nonverbal_baby_response_suppressed",
                            "cooldownMs=$NONVERBAL_RESPONSE_COOLDOWN_MS",
                        )
                        return@onSuccess
                    }
                    if (infantVocalEvent) lastNonverbalResponseAtMillis = nowMillis

                    val spokenEnglish = AiCharacterName.stripLeadingSpeakerLabel(english, resolvedAiName)
                    latestEmmaText = spokenEnglish
                    latestTopic = lite.lastTopic
                    status = ProductionEmmaStatus.SPEAKING
                    statusMessage =
                        if (infantVocalEvent) "${resolvedAiName}が赤ちゃんに話しかけています。" else "${resolvedAiName}が話しています。"
                    voiceError = null
                    playbackHistory.offer(HistoryEntry.create(sessionId, latestTranscript, spokenEnglish,
                        lite.lastTopic, "lite"))
                    if (!speakEmma(spokenEnglish)) {
                        playbackHistory.cancel()
                        recorder.resumeBuffering(clearExisting = true)
                        endpointStartedNanos = null
                        ttsRequestedAfterEndpointMillis = null
                        status = ProductionEmmaStatus.ERROR
                        statusMessage = voiceError ?: "音声を再生できませんでした。"
                    }
                }.onFailure { error ->
                    endpointStartedNanos = null
                    ttsRequestedAfterEndpointMillis = null
                    val noMeaningfulSpeech =
                        error.message?.contains("聞き取れませんでした") == true

                    if (noMeaningfulSpeech && !automatic) {
                        DiagnosticStore.mark(
                            context,
                            "meaningless_turn_replaced_with_generic",
                            "automatic=false message=${error.message ?: ""}",
                        )
                        respondGenericallyToManualRequest()
                    } else if (noMeaningfulSpeech) {
                        recorder.resumeBuffering(clearExisting = true)
                        latestEmmaText = ""
                        status = ProductionEmmaStatus.LISTENING
                        statusMessage = "意味のあることばを待っています。"
                        DiagnosticStore.mark(
                            context,
                            "meaningless_turn_suppressed",
                            "automatic=true message=${error.message ?: ""}",
                        )
                    } else {
                        recorder.resumeBuffering(clearExisting = true)
                        status = ProductionEmmaStatus.ERROR
                        statusMessage =
                            "みつことばの生成に失敗しました: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
            }
        }
    }
    var onNotificationChanged: (() -> Unit)? = null
    val conversationServiceActiveState = mutableStateOf(false)
    fun dismissInterruptedTutorial() {
        if (tutorialStep == null) return
        tutorialStep = null
        tutorialUserSpoke = false
        autoStartPending = false
        preferences.edit().putBoolean("tutorial_dismissed_v1", true).apply()
        stopSession()
    }
    fun applyContinueScreenOff(enabled: Boolean) {
        preferences.edit().putBoolean("continue_screen_off", enabled).apply()
        continueScreenOff = enabled
        if (enabled && recording && !conversationServiceActiveState.value && !startRequested) {
            startRequested = true
            runCatching { context.startForegroundService(Intent(context, ConversationService::class.java)) }
                .onFailure { stopSession(); status = ProductionEmmaStatus.ERROR; statusMessage = "画面オフ中の呼びかけを開始できません: ${it.message}" }
        } else if (!enabled && (conversationServiceActiveState.value || startRequested)) {
            stopSession()
        }
    }
    fun attachConversationService() {
        startRequested = false
        releaseAudio()
    }
    fun requestStart() {
        if (recording || startRequested || !modelReady) return
        startRequested = true
        if (continueScreenOff) {
            runCatching { context.startForegroundService(Intent(context, ConversationService::class.java)) }
                .onFailure { stopSession(); status = ProductionEmmaStatus.ERROR; statusMessage = "画面オフ中の呼びかけを開始できません: ${it.message}" }
        } else if (requestAudio()) beginRecording() else { stopSession(); status = ProductionEmmaStatus.ERROR; statusMessage="音声を使えません。" }
    }
    fun replay(text: String) {
        stopSession()
        latestEmmaText = text
        status = ProductionEmmaStatus.SPEAKING
        if (!speakEmma(text)) { status = ProductionEmmaStatus.ERROR; statusMessage = "音声を再生できませんでした。" }
    }
    init {
        recorder.onVoiceActivity = { event ->
            val ticket = session.ticket()
            mainHandler.post {
                if (disposed || !recording || !session.accepts(ticket)) return@post
                when (event) {
                    VoiceActivityEvent.SpeechStarted -> {
                        if (tutorialStep == 2) tutorialUserSpoke = true
                        if (!generating && status != ProductionEmmaStatus.SPEAKING) {
                            status = ProductionEmmaStatus.ENDPOINT_WAIT
                            statusMessage = "聞いています…"
                        }
                    }
                    is VoiceActivityEvent.Endpoint -> {
                        DiagnosticStore.mark(
                            context,
                            "auto_endpoint",
                            "speechMs=${event.speechMillis} silenceMs=${event.silenceMillis} autoRespond=$autoRespond",
                        )
                        if ((autoRespond || tutorialStep == 2) && !generating && status != ProductionEmmaStatus.SPEAKING) {
                            endpointStartedNanos = System.nanoTime()
                            status = ProductionEmmaStatus.UNDERSTOOD
                            statusMessage = "聞きました。"
                            mainHandler.postDelayed({
                                if (!disposed && recording && session.accepts(ticket)) askEmma(automatic = true)
                            }, 140L)
                        } else if (!generating && status != ProductionEmmaStatus.SPEAKING) {
                            status = ProductionEmmaStatus.LISTENING
                            statusMessage = "聞き取りました。「今すぐAIが返事する」で返します。"
                        }
                    }
                }
            }
        }

        recorder.onError = { message ->
            val ticket = session.ticket()
            mainHandler.post {
                if (!disposed && session.accepts(ticket)) {
                    stopSession()
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = message
                }
            }
        }
    }
}
