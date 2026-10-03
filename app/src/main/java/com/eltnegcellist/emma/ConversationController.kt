package com.eltnegcellist.emma

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eltnegcellist.emma.ai.AiCharacterName
import com.eltnegcellist.emma.ai.ConversationEngineMode
import com.eltnegcellist.emma.ai.EnglishLevel
import com.eltnegcellist.emma.ai.GemmaEmmaClient
import com.eltnegcellist.emma.ai.LiteEmmaClient
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import com.eltnegcellist.emma.asr.MoonshineModelStore
import com.eltnegcellist.emma.audio.AudioRingRecorder
import com.eltnegcellist.emma.audio.VoiceActivityEvent
import com.eltnegcellist.emma.model.GemmaModelStore
import com.eltnegcellist.emma.tts.DiagnosticStore
import com.eltnegcellist.emma.tts.KittenModelStore
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
    private val initialEngineMode = preferences.getString("conversation_engine_mode", null)?.let { ConversationEngineMode.fromSaved(it) } ?: if (GemmaModelStore.hasUsableModel(context)) ConversationEngineMode.FULL else ConversationEngineMode.LITE
    private val initialAsrModel = if (preferences.getBoolean("asr_model_manual", false)) MoonshineAsrModel.fromSaved(preferences.getString("asr_model", null)) else MoonshineAsrModel.SMALL
    private val onboardingOpen = !preferences.getBoolean("onboarding_completed_v4", false)
    val continueScreenOffState = mutableStateOf(preferences.getBoolean("continue_screen_off", true))
    var continueScreenOff by continueScreenOffState
    val historyEnabledState = mutableStateOf(preferences.getBoolean("history_enabled", true))
    var historyEnabled by historyEnabledState
    val history = ConversationHistory(context)
    private val playbackHistory = PlaybackHistoryGate<HistoryEntry>()
    private var sessionId = ""
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

    val engineModeState = mutableStateOf(initialEngineMode)
    var engineMode by engineModeState

    val asrModelState = mutableStateOf(initialAsrModel)
    var asrModel by asrModelState

    val latestTranscriptState = mutableStateOf("")
    var latestTranscript by latestTranscriptState

    val autoRespondState = mutableStateOf(preferences.getBoolean("auto_respond", true))
    var autoRespond by autoRespondState

    val aiNameState = mutableStateOf(preferences.getString("ai_character_name", AiCharacterName.DEFAULT).orEmpty())
    var aiName by aiNameState

    val tutorialStepState = mutableStateOf<Int?>(
            if (!onboardingOpen && !preferences.getBoolean("tutorial_completed_v1", false)) 0 else null,
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
            when (initialEngineMode) {
                ConversationEngineMode.LITE ->
                    MoonshineModelStore.isInstalled(context, initialAsrModel) && KittenModelStore.isInstalled(context)
                ConversationEngineMode.FULL ->
                    MoonshineModelStore.isInstalled(context, initialAsrModel) &&
                        KittenModelStore.isInstalled(context) &&
                        GemmaModelStore.hasUsableModel(context)
            },
        )
    var modelPresent by modelPresentState

    val modelReadyState = mutableStateOf(false)
    var modelReady by modelReadyState

    val kittenInstalledState = mutableStateOf(KittenModelStore.isInstalled(context))
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

    val recorder = AudioRingRecorder()

    val gemma = GemmaEmmaClient(context)

    val lite = LiteEmmaClient(context)

    val modelFile = GemmaModelStore.modelFile(context)

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
                            if (autoRespond) "普通に話しかけてください。" else "話したところで「ここで返事して」を押してください。"
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
                    statusMessage = "Kitten TTS Nanoの生成または再生に失敗しました: $message"
                }
            },
            onAmplitude = { amplitude -> if (!disposed) mouthLevel = amplitude },
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
        runCatching { recorder.start() }
            .onSuccess {
                recording = true
                EmmaWorkQueue.execute { lite.resetConversationContext() }
                status = ProductionEmmaStatus.LISTENING
                statusMessage = if (autoRespond) {
                    "普通に話しかけてください。必要なら「ここで返事して」で区切れます。"
                } else {
                    "話したところで「ここで返事して」を押してください。"
                }
            }
            .onFailure {
                stopSession()
                status = ProductionEmmaStatus.ERROR
                statusMessage = it.message ?: "録音を開始できませんでした。"
            }
    }

    fun stopSession() {
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
        statusMessage = "会話を止めました。"
        onStopped?.invoke()
    }

    fun askEmma(automatic: Boolean = false) {
        if (!recording || generating || status == ProductionEmmaStatus.THINKING || status == ProductionEmmaStatus.SPEAKING) return
        val activeReady = when (engineMode) {
            ConversationEngineMode.LITE -> lite.isReady()
            ConversationEngineMode.FULL -> gemma.isReady()
        }
        if (!modelReady || !activeReady) {
            status = ProductionEmmaStatus.ERROR
            statusMessage = "みつことばがまだ準備できていません。"
            return
        }

        val minimumSeconds = if (automatic) 0.45 else 0.8
        if (recorder.secondsAvailable() < minimumSeconds) {
            if (!automatic) {
                status = ProductionEmmaStatus.LISTENING
                statusMessage = "もう少し話してください。"
            }
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
            "mode=${engineMode.label} automatic=$automatic wavBytes=${wav.size}",
        )

        val requestedMode = engineMode
        EmmaWorkQueue.execute {
            val result = when (requestedMode) {
                ConversationEngineMode.LITE ->
                    lite.createEnglishIsland(wav, requestedLevel) { transcript ->
                        mainHandler.post {
                            if (!disposed && session.accepts(ticket)) {
                                latestTranscript = transcript
                                statusMessage = "返答を選んでいます…"
                            }
                        }
                    }
                ConversationEngineMode.FULL ->
                    gemma.createEnglishIsland(wav, requestedLevel) { transcript ->
                        mainHandler.post {
                            if (!disposed && session.accepts(ticket)) {
                                val clearSpeech = transcript.replace("[不明]", "").trim()
                                latestTranscript = if (clearSpeech.isEmpty()) {
                                    "ことばではない声を聞きました"
                                } else {
                                    transcript.replace("[不明]", "…")
                                }
                                statusMessage = "返事を考えています…"
                            }
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
                    status = ProductionEmmaStatus.SPEAKING
                    statusMessage =
                        if (infantVocalEvent) "${resolvedAiName}が赤ちゃんに話しかけています。" else "${resolvedAiName}が話しています。"
                    voiceError = null
                    playbackHistory.offer(HistoryEntry.create(sessionId, latestTranscript, spokenEnglish,
                        if (requestedMode == ConversationEngineMode.LITE) lite.lastTopic else gemma.lastTopic, requestedMode.savedValue))
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

                    recorder.resumeBuffering(clearExisting = true)
                    if (noMeaningfulSpeech) {
                        latestEmmaText = ""
                        status = ProductionEmmaStatus.LISTENING
                        statusMessage = "意味のあることばを待っています。"
                        DiagnosticStore.mark(
                            context,
                            "meaningless_turn_suppressed",
                            "automatic=$automatic message=${error.message ?: ""}",
                        )
                    } else {
                        status = ProductionEmmaStatus.ERROR
                        statusMessage =
                            "みつことばの生成に失敗しました: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
            }
        }
    }
    fun requestStart() {
        if (recording || startRequested || !modelReady) return
        startRequested = true
        if (continueScreenOff) {
            runCatching { context.startForegroundService(Intent(context, ConversationService::class.java)) }
                .onFailure { stopSession(); status = ProductionEmmaStatus.ERROR; statusMessage = "画面オフ会話を開始できません: ${it.message}" }
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
                            statusMessage = "聞き取りました。「ここで返事して」で返します。"
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
