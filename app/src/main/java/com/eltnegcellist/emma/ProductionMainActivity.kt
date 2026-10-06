package com.eltnegcellist.emma

import android.app.ActivityManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eltnegcellist.emma.ai.AiCharacterName
import com.eltnegcellist.emma.ai.ConversationEngineMode
import com.eltnegcellist.emma.ai.EnglishLevel
import com.eltnegcellist.emma.ai.GemmaEmmaClient
import com.eltnegcellist.emma.ai.LiteEmmaClient
import com.eltnegcellist.emma.ai.RuriSemanticModelStore
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import com.eltnegcellist.emma.asr.MoonshineModelStore
import com.eltnegcellist.emma.audio.AudioRingRecorder
import com.eltnegcellist.emma.audio.VoiceActivityEvent
import com.eltnegcellist.emma.model.GemmaModelStore
import com.eltnegcellist.emma.model.ModelPreparationKind
import com.eltnegcellist.emma.model.ModelPreparationManager
import com.eltnegcellist.emma.model.ModelPreparationStatus
import com.eltnegcellist.emma.tts.DiagnosticStore
import com.eltnegcellist.emma.tts.KittenModelStore
import com.eltnegcellist.emma.tts.KittenSpeaker
import com.eltnegcellist.emma.ui.EmmaTheme
import com.eltnegcellist.emma.ui.EmmaVisualState
import kotlinx.coroutines.delay

private const val NONVERBAL_RESPONSE_COOLDOWN_MS = 15_000L
private const val BABY_VOCAL_CONTEXT = "赤ちゃんが声を出している"

internal fun shouldRequestConversationNotificationPermission(
    sdkInt: Int,
    continueScreenOff: Boolean,
    permissionGranted: Boolean,
    startPromptHandled: Boolean,
): Boolean =
    continueScreenOff &&
        sdkInt >= 33 &&
        !permissionGranted &&
        !startPromptHandled

class ProductionMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Emma's speech is ordinary media playback, not call/VoIP audio.
        // Keep hardware volume keys attached to the media stream while the app
        // is in the foreground, even though the microphone is also active.
        volumeControlStream = AudioManager.STREAM_MUSIC

        DiagnosticStore.collectOnStartup(this)
        setContent {
            EmmaTheme {
                ProductionEmmaApp()
            }
        }
    }
}

internal enum class ProductionEmmaStatus {
    IDLE,
    MODEL_IMPORTING,
    MODEL_LOADING,
    LISTENING,
    ENDPOINT_WAIT,
    UNDERSTOOD,
    THINKING,
    SPEAKING,
    ERROR,
}

@Composable
private fun ProductionEmmaApp() {
    val context = LocalContext.current
    val runtime = remember { ConversationRuntime.get(context) }
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    remember {
        preferences.edit()
            .remove("voice_backend")
            .apply()
        true
    }
    var englishLevel by runtime.englishLevelState
    val initialEngineMode = remember {
        val saved = preferences.getString("conversation_engine_mode", null)
        when {
            saved != null -> ConversationEngineMode.fromSaved(saved)
            GemmaModelStore.hasUsableModel(context) -> ConversationEngineMode.FULL
            else -> ConversationEngineMode.LITE
        }.also { resolved ->
            preferences.edit()
                .putString("conversation_engine_mode", resolved.savedValue)
                .apply()
        }
    }
    var engineMode by runtime.engineModeState
    var onboardingMode by remember { mutableStateOf(initialEngineMode) }
    var highPerformance by remember {
        mutableStateOf(
            if (preferences.contains("first_run_high_performance")) {
                preferences.getBoolean("first_run_high_performance", true)
            } else {
                !preferences.getBoolean("first_run_start_tiny", false)
            },
        )
    }
    var semanticEnabled by remember {
        mutableStateOf(preferences.getBoolean("semantic_enabled", true))
    }
    var asrModelManuallySelected by remember {
        mutableStateOf(preferences.getBoolean("asr_model_manual", false))
    }
    val initialAsrModel = remember {
        if (preferences.getBoolean("asr_model_manual", false)) {
            MoonshineAsrModel.fromSaved(preferences.getString("asr_model", null))
        } else {
            MoonshineAsrModel.SMALL
        }
    }
    var asrModel by runtime.asrModelState
    var latestTranscript by runtime.latestTranscriptState
    var autoRespond by runtime.autoRespondState
    var keepScreenOn by remember { mutableStateOf(preferences.getBoolean("keep_screen_on", true)) }
    var aiName by runtime.aiNameState
    val resolvedAiName = AiCharacterName.resolve(aiName)
    var featureScreen by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var parentFullPromptOpen by remember { mutableStateOf(false) }
    var onboardingOpen by remember {
        mutableStateOf(!preferences.getBoolean("onboarding_completed_v4", false))
    }
    var tutorialStep by runtime.tutorialStepState
    var firstRunBusy by remember { mutableStateOf(false) }
    var firstRunReady by remember { mutableStateOf(false) }
    var firstRunPhase by remember { mutableStateOf("") }
    var firstRunProgressPercent by remember { mutableStateOf<Int?>(null) }
    var firstRunError by remember { mutableStateOf<String?>(null) }
    var mouthLevel by runtime.mouthLevelState

    val mainHandler = runtime.mainHandler
    val session = runtime.session
    var disposed by remember { mutableStateOf(false) }
    var generating by runtime.generatingState
    var voiceError by runtime.voiceErrorState
    val recorder = runtime.recorder
    val gemma = runtime.gemma
    val lite = runtime.lite
    val modelFile = runtime.modelFile

    var status by runtime.statusState
    var statusMessage by runtime.statusMessageState
    var latestEmmaText by runtime.latestEmmaTextState
    var recording by runtime.recordingState
    var autoStartPending by runtime.autoStartPendingState
    var pendingStartAfterPermission by runtime.pendingStartAfterPermissionState
    var pendingStartAfterNotificationPermission by remember { mutableStateOf(false) }
    var modelPresent by runtime.modelPresentState
    var modelReady by runtime.modelReadyState
    var kittenInstalled by runtime.kittenInstalledState
    var fullSetupOpen by remember {
        mutableStateOf(
            initialEngineMode == ConversationEngineMode.FULL &&
                (
                    !GemmaModelStore.hasUsableModel(context) ||
                        !MoonshineModelStore.isInstalled(context, initialAsrModel) ||
                        !KittenModelStore.isInstalled(context)
                ),
        )
    }
    var fullSetupBusy by remember { mutableStateOf(false) }
    var fullSetupPhase by remember { mutableStateOf("") }
    var fullSetupProgressPercent by remember { mutableStateOf<Int?>(null) }
    var fullSetupError by remember { mutableStateOf<String?>(null) }
    var liteSetupBusy by remember { mutableStateOf(false) }
    var liteSetupDialogOpen by remember { mutableStateOf(false) }
    var liteSetupPhase by remember { mutableStateOf("") }
    var liteSetupProgressPercent by remember { mutableStateOf<Int?>(null) }
    var lastSpeechMillis by runtime.lastSpeechMillisState
    var lastTtsGenerationMillis by runtime.lastTtsGenerationMillisState
    var lastTtsTotalMillis by runtime.lastTtsTotalMillisState
    var lastEndpointToTtsRequestMillis by runtime.lastEndpointToTtsRequestMillisState
    var lastEndpointToFirstAudioMillis by runtime.lastEndpointToFirstAudioMillisState
    var endpointStartedNanos by runtime.endpointStartedNanosState
    var ttsRequestedAfterEndpointMillis by runtime.ttsRequestedAfterEndpointMillisState
    var lastNonverbalResponseAtMillis by runtime.lastNonverbalResponseAtMillisState
    var screenIntroductionPlayed by runtime.screenIntroductionPlayedState
    var screenIntroductionPlaying by runtime.screenIntroductionPlayingState
    var tutorialUserSpoke by runtime.tutorialUserSpokeState
    var lastHandledPreparationAtMillis by remember { mutableStateOf(0L) }

    DisposableEffect(recording, keepScreenOn) {
        val window = (context as? ComponentActivity)?.window
        if (recording && keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val diagnosticsExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri).use { output ->
                requireNotNull(output) { "診断ファイルを開けませんでした。" }
                output.write(DiagnosticStore.read(context).toByteArray())
            }
            statusMessage = "診断情報を書き出しました。"
        }.onFailure { statusMessage = "診断情報の書き出しに失敗しました。" }
    }
    val crashDetailsExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            statusMessage = "クラッシュ詳細を保存しています…"
            EmmaWorkQueue.execute {
                runCatching {
                    context.contentResolver.openOutputStream(uri).use { output ->
                        requireNotNull(output) { "クラッシュ詳細を開けませんでした。" }
                        DiagnosticStore.writeDetailsZip(context, output)
                    }
                }.onSuccess { mainHandler.post { statusMessage = "クラッシュ詳細を保存しました。" } }
                    .onFailure { mainHandler.post { statusMessage = "クラッシュ詳細の保存に失敗しました。" } }
            }
        }
    }

    val kitten = runtime.kitten

    fun speakEmma(text: String) = runtime.speakEmma(text)

    fun modeModelsPresent(
        mode: ConversationEngineMode,
        selectedAsr: MoonshineAsrModel = asrModel,
    ): Boolean = when (mode) {
        ConversationEngineMode.LITE ->
            MoonshineModelStore.isInstalled(context, selectedAsr) && KittenModelStore.isInstalled(context)
        ConversationEngineMode.FULL ->
            MoonshineModelStore.isInstalled(context, selectedAsr) &&
                KittenModelStore.isInstalled(context) &&
                GemmaModelStore.hasUsableModel(context)
    }

    fun loadModel() {
        if (disposed) return
        val requiredModelPresent = modeModelsPresent(engineMode)
        if (!requiredModelPresent) {
            modelPresent = false
            modelReady = false
            status = ProductionEmmaStatus.ERROR
            statusMessage = when (engineMode) {
                ConversationEngineMode.LITE -> "みつことば LiteのMoonshineとKitten TTSを先に準備してください。"
                ConversationEngineMode.FULL -> "みつことば FullのMoonshine・Kitten・Gemmaを先に準備してください。"
            }
            settingsOpen = true
            return
        }

        modelPresent = true
        modelReady = false
        status = ProductionEmmaStatus.MODEL_LOADING
        statusMessage = "みつことば ${engineMode.label}を起動しています…"
        val requestedMode = engineMode
        val requestedAsr = asrModel

        EmmaWorkQueue.execute {
            val result = when (requestedMode) {
                ConversationEngineMode.LITE -> {
                    gemma.close()
                    lite.initialize(requestedAsr)
                }
                ConversationEngineMode.FULL -> {
                    lite.close()
                    gemma.initialize(modelFile.absolutePath, requestedAsr)
                }
            }
            mainHandler.post {
                if (disposed || engineMode != requestedMode || asrModel != requestedAsr) return@post
                result.onSuccess {
                    modelReady = true
                    kittenInstalled = KittenModelStore.isInstalled(context)
                    status = ProductionEmmaStatus.IDLE
                    statusMessage = "みつことば ${requestedMode.label}の準備ができました。"
                }.onFailure { error ->
                    modelReady = false
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "みつことばの起動に失敗しました: ${error.message ?: error.javaClass.simpleName}"
                    settingsOpen = true
                }
            }
        }
    }

    fun activateEngineMode(selected: ConversationEngineMode) {
        engineMode = selected
        if (!asrModelManuallySelected) {
            asrModel = MoonshineAsrModel.SMALL
        }
        preferences.edit()
            .putString("conversation_engine_mode", selected.savedValue)
            .apply()
        if (selected != ConversationEngineMode.FULL) {
            preferences.edit().putString("audience_mode", "BABY").apply()
        }
        modelReady = false
        kittenInstalled = KittenModelStore.isInstalled(context)
        modelPresent = modeModelsPresent(selected)
        status = ProductionEmmaStatus.IDLE
        statusMessage = when (selected) {
            ConversationEngineMode.LITE ->
                if (modelPresent) "Lite / ${asrModel.shortLabel}を選びました。起動します…" else "Liteの音声モデルを準備してください。"
            ConversationEngineMode.FULL ->
                if (modelPresent) "Full / ${asrModel.shortLabel}を選びました。起動します…" else "Fullを選びました。Moonshine・Kitten・Gemmaを準備してください。"
        }

        EmmaWorkQueue.execute {
            when (selected) {
                ConversationEngineMode.LITE -> gemma.close()
                ConversationEngineMode.FULL -> lite.close()
            }
        }
        if (modelPresent) {
            mainHandler.post { if (!disposed) loadModel() }
        }
    }

    fun activateAsrModel(selected: MoonshineAsrModel) {
        if (selected == asrModel) return
        asrModel = selected
        asrModelManuallySelected = true
        preferences.edit()
            .putString("asr_model", selected.savedValue)
            .putBoolean("asr_model_manual", true)
            .apply()

        modelReady = false
        modelPresent = modeModelsPresent(engineMode, selected)
        if (MoonshineModelStore.isInstalled(context, selected)) {
            status = ProductionEmmaStatus.MODEL_LOADING
            statusMessage = "音声認識を${selected.shortLabel}に切り替えています…"
            EmmaWorkQueue.execute {
                lite.close()
                gemma.close()
                mainHandler.post {
                    if (!disposed && asrModel == selected) loadModel()
                }
            }
        } else {
            status = ProductionEmmaStatus.MODEL_IMPORTING
            statusMessage = "Moonshine 日本語${selected.shortLabel}をバックグラウンドで準備します…"
        }
    }

    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && !disposed) {
            modelReady = false
            status = ProductionEmmaStatus.MODEL_IMPORTING
            statusMessage = "会話モデルを取り込んでいます…"

            EmmaWorkQueue.execute {
                gemma.close()
                val result = GemmaModelStore.importModel(context, uri) { percent ->
                    mainHandler.post {
                        if (disposed) return@post
                        statusMessage = if (percent != null) "会話モデルを取り込んでいます… $percent%" else "会話モデルを取り込んでいます…"
                    }
                }

                mainHandler.post {
                    if (disposed) return@post
                    result.onSuccess {
                        modelPresent = true
                        loadModel()
                    }.onFailure { error ->
                        modelPresent = modeModelsPresent(ConversationEngineMode.FULL)
                        status = ProductionEmmaStatus.ERROR
                        statusMessage = "モデルの取り込みに失敗しました: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
            }
        }
    }

    fun monitorModelPreparation(
        kind: ModelPreparationKind,
        requestedAsr: MoonshineAsrModel,
        onProgress: (String, Int?) -> Unit,
        onFinished: (Result<Unit>) -> Unit,
    ) {
        Thread({
            while (!disposed) {
                val snapshot = ModelPreparationManager.snapshot(context)
                if (snapshot.kind == kind && snapshot.asrModel == requestedAsr) {
                    mainHandler.post {
                        if (!disposed) onProgress(snapshot.phase, snapshot.percent)
                    }
                    if (!snapshot.active) {
                        val result = if (snapshot.status == ModelPreparationStatus.SUCCEEDED) {
                            Result.success(Unit)
                        } else {
                            Result.failure(IllegalStateException(snapshot.error ?: "モデルの準備を完了できませんでした。"))
                        }
                        ModelPreparationManager.markHandled(context, snapshot)
                        mainHandler.post {
                            if (!disposed) onFinished(result)
                        }
                        break
                    }
                }
                Thread.sleep(500L)
            }
        }, "ModelPreparationMonitor").apply {
            isDaemon = true
        }.start()
    }

    fun startLiteAutomaticSetup() {
        if (disposed || liteSetupBusy) return

        val requestedAsr = asrModel
        liteSetupBusy = true
        liteSetupDialogOpen = true
        liteSetupProgressPercent = 0
        liteSetupPhase = "Moonshine 日本語${requestedAsr.shortLabel}を準備しています…"
        modelReady = false
        status = ProductionEmmaStatus.MODEL_IMPORTING
        statusMessage = "音声モデルをバックグラウンドで準備しています…"

        if (!ModelPreparationManager.start(context, ModelPreparationKind.LITE, requestedAsr)) {
            liteSetupBusy = false
            liteSetupDialogOpen = false
            status = ProductionEmmaStatus.ERROR
            statusMessage = "別のモデル準備が進行中です。"
            return
        }

        monitorModelPreparation(
            ModelPreparationKind.LITE,
            requestedAsr,
            onProgress = { phase, percent ->
                liteSetupPhase = phase
                liteSetupProgressPercent = percent
                statusMessage = phase
            },
            onFinished = { result ->
                liteSetupBusy = false
                liteSetupDialogOpen = false
                kittenInstalled = KittenModelStore.isInstalled(context)
                modelPresent = modeModelsPresent(engineMode)
                if (result.isSuccess) {
                    if (kittenInstalled) kitten.resetModel()
                    liteSetupProgressPercent = 100
                    status = ProductionEmmaStatus.IDLE
                    statusMessage = "音声モデルの準備ができました。"
                    if (modelPresent) loadModel()
                } else {
                    liteSetupProgressPercent = null
                    status = ProductionEmmaStatus.ERROR
                    val detail = result.exceptionOrNull()?.message ?: "原因を確認できませんでした。"
                    statusMessage = "音声モデルの準備に失敗しました: $detail"
                    settingsOpen = true
                }
            },
        )
    }

    fun startSemanticAutomaticSetup() {
        if (
            disposed ||
            liteSetupBusy ||
            RuriSemanticModelStore.isInstalled(context)
        ) {
            return
        }

        val requestedAsr = asrModel
        liteSetupBusy = true
        liteSetupDialogOpen = true
        liteSetupProgressPercent = 0
        liteSetupPhase = "意味で話題を理解するモデルを準備しています…"
        status = ProductionEmmaStatus.MODEL_IMPORTING
        statusMessage = liteSetupPhase

        if (!ModelPreparationManager.start(context, ModelPreparationKind.SEMANTIC, requestedAsr)) {
            liteSetupBusy = false
            liteSetupDialogOpen = false
            status = ProductionEmmaStatus.ERROR
            statusMessage = "別のモデル準備が進行中です。"
            return
        }

        monitorModelPreparation(
            ModelPreparationKind.SEMANTIC,
            requestedAsr,
            onProgress = { phase, percent ->
                liteSetupPhase = phase
                liteSetupProgressPercent = percent
                statusMessage = phase
            },
            onFinished = { result ->
                liteSetupBusy = false
                liteSetupDialogOpen = false
                if (result.isSuccess) {
                    liteSetupProgressPercent = 100
                    status = if (modelReady) ProductionEmmaStatus.IDLE else status
                    statusMessage = "意味で話題を理解するモデルの準備ができました。"
                } else {
                    liteSetupProgressPercent = null
                    status = ProductionEmmaStatus.ERROR
                    val detail = result.exceptionOrNull()?.message ?: "原因を確認できませんでした。"
                    statusMessage = "話題理解モデルの準備に失敗しました: $detail"
                    settingsOpen = true
                }
            },
        )
    }

    fun startFirstRunSetup(selectedMode: ConversationEngineMode) {
        if (disposed || firstRunBusy) return

        val requestedAsr = if (highPerformance) MoonshineAsrModel.SMALL else MoonshineAsrModel.TINY
        asrModel = requestedAsr
        asrModelManuallySelected = !highPerformance
        semanticEnabled = highPerformance
        preferences.edit()
            .putString("asr_model", requestedAsr.savedValue)
            .putBoolean("asr_model_manual", !highPerformance)
            .putBoolean("first_run_start_tiny", !highPerformance)
            .putBoolean("first_run_high_performance", highPerformance)
            .putBoolean("semantic_enabled", highPerformance)
            .putString("conversation_engine_mode", selectedMode.savedValue)
            .apply()
        if (selectedMode != ConversationEngineMode.FULL) {
            preferences.edit().putString("audience_mode", "BABY").apply()
        }

        firstRunBusy = true
        firstRunReady = false
        firstRunError = null
        firstRunProgressPercent = 0
        firstRunPhase = "みつことば ${selectedMode.label}を準備しています…"
        status = ProductionEmmaStatus.MODEL_IMPORTING
        statusMessage = "必要なデータをバックグラウンドで準備しています…"
        engineMode = selectedMode

        val kind = if (selectedMode == ConversationEngineMode.FULL) {
            ModelPreparationKind.FULL
        } else {
            ModelPreparationKind.LITE
        }
        if (!ModelPreparationManager.start(context, kind, requestedAsr)) {
            firstRunBusy = false
            firstRunError = "別のモデル準備が進行中です。"
            status = ProductionEmmaStatus.ERROR
            return
        }

        monitorModelPreparation(
            kind,
            requestedAsr,
            onProgress = { phase, percent ->
                firstRunPhase = phase
                firstRunProgressPercent = percent
                statusMessage = phase
            },
            onFinished = { result ->
                kittenInstalled = KittenModelStore.isInstalled(context)
                modelPresent = modeModelsPresent(selectedMode, requestedAsr)
                firstRunBusy = false
                if (result.isSuccess) {
                    if (kittenInstalled) kitten.resetModel()
                    firstRunReady = true
                    firstRunProgressPercent = 100
                    firstRunPhase = "準備できました"
                    preferences.edit()
                        .putBoolean("onboarding_completed_v4", true)
                        .putString("conversation_engine_mode", selectedMode.savedValue)
                        .remove("voice_backend")
                        .apply()
                    onboardingOpen = false
                    tutorialStep = 0
                    screenIntroductionPlayed = false
                    autoStartPending = false
                    status = ProductionEmmaStatus.IDLE
                    statusMessage = "モデルを起動しています…"
                    if (modelPresent) loadModel()
                } else {
                    firstRunReady = false
                    firstRunProgressPercent = null
                    val detail = result.exceptionOrNull()?.message ?: "原因を確認できませんでした。"
                    firstRunError = "初期設定を完了できませんでした。\n$detail"
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "初期設定に失敗しました: $detail"
                }
            },
        )
    }

    fun startFullAutomaticSetup() {
        if (disposed || fullSetupBusy) return

        val requestedAsr = if (asrModelManuallySelected) asrModel else MoonshineAsrModel.SMALL
        asrModel = requestedAsr
        fullSetupBusy = true
        fullSetupError = null
        fullSetupProgressPercent = 0
        fullSetupPhase = "Fullの準備を始めています…"
        status = if (modelReady) ProductionEmmaStatus.IDLE else ProductionEmmaStatus.MODEL_IMPORTING
        statusMessage = if (modelReady) {
            "Fullをバックグラウンドで準備中です。Liteはそのまま使えます。"
        } else {
            "みつことば Fullをバックグラウンドで準備しています…"
        }

        if (!ModelPreparationManager.start(context, ModelPreparationKind.FULL, requestedAsr)) {
            fullSetupBusy = false
            fullSetupError = "別のモデル準備が進行中です。"
            status = ProductionEmmaStatus.ERROR
            return
        }

        monitorModelPreparation(
            ModelPreparationKind.FULL,
            requestedAsr,
            onProgress = { phase, percent ->
                fullSetupPhase = phase
                fullSetupProgressPercent = percent
                statusMessage = phase
            },
            onFinished = { result ->
                fullSetupBusy = false
                kittenInstalled = KittenModelStore.isInstalled(context)
                modelPresent = modeModelsPresent(ConversationEngineMode.FULL, requestedAsr)
                if (result.isSuccess) {
                    if (kittenInstalled) kitten.resetModel()
                    fullSetupProgressPercent = 100
                    fullSetupPhase = "Fullの準備ができました"
                    fullSetupOpen = false
                    fullSetupError = null
                    activateEngineMode(ConversationEngineMode.FULL)
                } else {
                    fullSetupProgressPercent = null
                    val detail = result.exceptionOrNull()?.message ?: "原因を確認できませんでした。"
                    fullSetupError = "Fullの準備を完了できませんでした。\n$detail"
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "Fullの準備に失敗しました: $detail"
                }
            },
        )
    }

    // Notification permission controls visibility, not the ability to run an FGS.
    // Keep microphone permission mandatory, but ask for notification visibility at
    // the first actual screen-off conversation start. A denial never blocks audio.
    fun beginRecording() = runtime.requestStart()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (disposed || !pendingStartAfterNotificationPermission) return@rememberLauncherForActivityResult
        pendingStartAfterNotificationPermission = false
        preferences.edit()
            .putBoolean("conversation_notification_permission_requested", true)
            .putBoolean("conversation_notification_start_prompt_v2", true)
            .putBoolean("conversation_notification_controls", granted)
            .apply()
        runtime.onNotificationChanged?.invoke()
        beginRecording()
    }

    fun continueStartAfterMicPermission() {
        val shouldAskForNotification = shouldRequestConversationNotificationPermission(
            sdkInt = Build.VERSION.SDK_INT,
            continueScreenOff = runtime.continueScreenOff,
            permissionGranted =
                Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            startPromptHandled = preferences.getBoolean("conversation_notification_start_prompt_v2", false),
        )
        if (shouldAskForNotification) {
            pendingStartAfterNotificationPermission = true
            preferences.edit()
                .putBoolean("conversation_notification_permission_requested", true)
                .putBoolean("conversation_notification_start_prompt_v2", true)
                .apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            beginRecording()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (disposed) return@rememberLauncherForActivityResult
        if (granted && pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            continueStartAfterMicPermission()
        } else if (!granted) {
            pendingStartAfterPermission = false
            status = ProductionEmmaStatus.ERROR
            statusMessage = "マイク権限が必要です。"
        }
    }

    fun startSession() {
        if (!kittenInstalled) {
            status = ProductionEmmaStatus.ERROR
            statusMessage = "Kitten TTS Nano / Kikiを準備してください。"
            settingsOpen = true
            return
        }
        if (!modelReady) {
            status = ProductionEmmaStatus.ERROR
            statusMessage = "みつことばの準備完了後にセッションを開始してください。"
            settingsOpen = true
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingStartAfterPermission = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        continueStartAfterMicPermission()
    }

    fun stopSession() = runtime.stopSession()

    fun askEmma(automatic: Boolean = false) = runtime.askEmma(automatic)

    LaunchedEffect(Unit) {
        if (onboardingOpen) {
            settingsOpen = false
            fullSetupOpen = false
        } else if (
            initialEngineMode == ConversationEngineMode.FULL &&
            (
                !GemmaModelStore.hasUsableModel(context) ||
                        !MoonshineModelStore.isInstalled(context, initialAsrModel) ||
                        !KittenModelStore.isInstalled(context)
            )
        ) {
            fullSetupOpen = true
        } else if (modelPresent && !modelReady) {
            loadModel()
        } else if (!modelPresent) {
            settingsOpen = true
        }

        // Existing users do not revisit first-run setup after an update.
        // Keep Web's default-on Semantic behavior by preparing Ruri quietly;
        // Lite continues with rule matching until the verified assets are ready.
        if (
            !onboardingOpen &&
            semanticEnabled &&
            !RuriSemanticModelStore.isInstalled(context)
        ) {
            ModelPreparationManager.start(
                context,
                ModelPreparationKind.SEMANTIC,
                initialAsrModel,
            )
        }
    }

    LaunchedEffect(
        modelReady,
        onboardingOpen,
        tutorialStep,
        screenIntroductionPlayed,
        screenIntroductionPlaying,
    ) {
        if (
            modelReady &&
            !onboardingOpen &&
            tutorialStep == 0 &&
            !screenIntroductionPlayed &&
            !screenIntroductionPlaying
        ) {
            screenIntroductionPlaying = true
            status = ProductionEmmaStatus.SPEAKING
            statusMessage = "${resolvedAiName}が自己紹介しています。"
            voiceError = null
            if (!speakEmma(AiCharacterName.introduction(resolvedAiName))) {
                screenIntroductionPlaying = false
                screenIntroductionPlayed = true
            }
        }
    }

    LaunchedEffect(modelReady, onboardingOpen, tutorialStep) {
        if (
            modelReady &&
            !onboardingOpen &&
            tutorialStep == null &&
            autoStartPending &&
            !recording
        ) {
            autoStartPending = false
            startSession()
        }
    }

    DisposableEffect(Unit) {
        val lifecycle = (context as ComponentActivity).lifecycle
        runtime.uiVisible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_START) runtime.uiVisible = true
            if(event == Lifecycle.Event.ON_STOP) { runtime.uiVisible = false; mouthLevel = 0f; runtime.dismissInterruptedTutorial() }
            if (event == Lifecycle.Event.ON_STOP && (!runtime.continueScreenOff || !recording) && (recording || generating || pendingStartAfterPermission || status == ProductionEmmaStatus.SPEAKING || runtime.startRequested)) {
                stopSession()
            }
        }
        lifecycle.addObserver(observer)

        onDispose {
            disposed = true
            runtime.uiVisible = false
            lifecycle.removeObserver(observer)
        }
    }

    if (featureScreen != null) {
        if (featureScreen == "history") HistoryScreen(runtime) { featureScreen = null }
        else PlayScreen(runtime) { featureScreen = null }
        return
    }

    val blockingModelPreparation = firstRunBusy || liteSetupBusy
    val busy = blockingModelPreparation || generating ||
        (status == ProductionEmmaStatus.MODEL_IMPORTING && !fullSetupBusy) ||
        status == ProductionEmmaStatus.MODEL_LOADING ||
        status == ProductionEmmaStatus.THINKING ||
        status == ProductionEmmaStatus.SPEAKING

    val backgroundPreparationActive =
        (fullSetupBusy && !fullSetupOpen) || (liteSetupBusy && !liteSetupDialogOpen)
    val backgroundPreparationPhase = when {
        fullSetupBusy -> fullSetupPhase
        liteSetupBusy -> liteSetupPhase
        else -> ""
    }
    val backgroundPreparationPercent = when {
        fullSetupBusy -> fullSetupProgressPercent
        liteSetupBusy -> liteSetupProgressPercent
        else -> null
    }

    val visualState = when (status) {
        ProductionEmmaStatus.IDLE, ProductionEmmaStatus.MODEL_IMPORTING, ProductionEmmaStatus.MODEL_LOADING -> EmmaVisualState.IDLE
        ProductionEmmaStatus.LISTENING -> EmmaVisualState.LISTENING
        ProductionEmmaStatus.ENDPOINT_WAIT -> EmmaVisualState.ENDPOINT_WAIT
        ProductionEmmaStatus.UNDERSTOOD -> EmmaVisualState.UNDERSTOOD
        ProductionEmmaStatus.THINKING -> EmmaVisualState.THINKING
        ProductionEmmaStatus.SPEAKING -> EmmaVisualState.SPEAKING
        ProductionEmmaStatus.ERROR -> EmmaVisualState.ERROR
    }
    val statusTitle = when (status) {
        ProductionEmmaStatus.IDLE -> if (modelReady) "準備完了" else "待機中"
        ProductionEmmaStatus.MODEL_IMPORTING -> "準備中"
        ProductionEmmaStatus.MODEL_LOADING -> "起動中"
        ProductionEmmaStatus.LISTENING, ProductionEmmaStatus.ENDPOINT_WAIT -> "聞いています"
        ProductionEmmaStatus.UNDERSTOOD -> "聞きました"
        ProductionEmmaStatus.THINKING -> "考えています"
        ProductionEmmaStatus.SPEAKING -> "話しています"
        ProductionEmmaStatus.ERROR -> "確認してください"
    }

    if (aboutOpen) {
        AboutEmmaScreen(
            onBack = { aboutOpen = false },
        )
    } else if (onboardingOpen) {
        FirstRunOnboardingScreen(
            selectedMode = onboardingMode,
            busy = firstRunBusy,
            ready = firstRunReady,
            phase = firstRunPhase,
            progressPercent = firstRunProgressPercent,
            errorMessage = firstRunError,
            aiName = aiName,
            onAiNameChange = { value ->
                aiName = value
                preferences.edit().putString("ai_character_name", value).apply()
                screenIntroductionPlayed = false
            },
            highPerformance = highPerformance,
            onHighPerformanceChange = { enabled ->
                highPerformance = enabled
                preferences.edit()
                    .putBoolean("first_run_high_performance", enabled)
                    .putBoolean("first_run_start_tiny", !enabled)
                    .apply()
                firstRunReady = false
            },
            onModeSelected = { selected ->
                if (!firstRunBusy) {
                    onboardingMode = selected
                    firstRunReady = false
                    firstRunError = null
                    firstRunProgressPercent = null
                    firstRunPhase = ""
                }
            },
            onPrepare = { startFirstRunSetup(onboardingMode) },
            onOpenAbout = { aboutOpen = true },
        )
    } else if (liteSetupBusy && liteSetupDialogOpen) {
        LiteModelInstallDialog(
            phase = liteSetupPhase,
            progressPercent = liteSetupProgressPercent,
            onDismiss = { liteSetupDialogOpen = false },
        )
    } else if (fullSetupOpen) {
        FullModeSetupScreen(
            busy = fullSetupBusy,
            phase = fullSetupPhase,
            progressPercent = fullSetupProgressPercent,
            errorMessage = fullSetupError,
            gemmaNeeded = !GemmaModelStore.hasUsableModel(context),
            moonshineNeeded = !MoonshineModelStore.isInstalled(context, asrModel),
            asrModel = asrModel,
            kittenNeeded = !kittenInstalled,
            onPrepare = ::startFullAutomaticSetup,
            onCancel = {
                fullSetupOpen = false
                if (!fullSetupBusy) {
                    fullSetupError = null
                    if (
                        engineMode == ConversationEngineMode.FULL &&
                        (
                !GemmaModelStore.hasUsableModel(context) ||
                        !MoonshineModelStore.isInstalled(context, asrModel) ||
                        !KittenModelStore.isInstalled(context)
            )
                    ) {
                        settingsOpen = true
                    }
                }
            },
            onManualSetup = {
                if (!fullSetupBusy) {
                    fullSetupOpen = false
                    activateEngineMode(ConversationEngineMode.FULL)
                    settingsOpen = true
                }
            },
        )
    } else if (settingsOpen) {
        EmmaSettingsScreen(
            featureSettings = { FeatureSettings(runtime) { stopSession(); autoStartPending=false; featureScreen="history" } },
            level = englishLevel,
            enabled = !busy && !recording,
            previewing = !recording && status == ProductionEmmaStatus.SPEAKING,
            modelReady = modelReady,
            modelPresent = modelPresent,
            asrInstalled = MoonshineModelStore.isInstalled(context, asrModel),
            kittenInstalled = kittenInstalled,
            gemmaInstalled = GemmaModelStore.hasUsableModel(context),
            lastSpeechMillis = lastSpeechMillis,
            lastTtsGenerationMillis = lastTtsGenerationMillis,
            lastTtsTotalMillis = lastTtsTotalMillis,
            lastEndpointToTtsRequestMillis = lastEndpointToTtsRequestMillis,
            lastEndpointToFirstAudioMillis = lastEndpointToFirstAudioMillis,
            engineMode = engineMode,
            asrModel = asrModel,
            semanticEnabled = semanticEnabled,
            semanticInstalled = RuriSemanticModelStore.isInstalled(context),
            keepScreenOn = keepScreenOn,
            aiName = aiName,
            onAiNameChange = { value ->
                aiName = value
                preferences.edit().putString("ai_character_name", value).apply()
                screenIntroductionPlayed = false
            },
            onBack = { settingsOpen = false },
            onOpenAbout = { aboutOpen = true },
            onEngineMode = { selected ->
                if (!recording && !busy && selected != engineMode) {
                    val targetAsr = if (asrModelManuallySelected) {
                        asrModel
                    } else {
                        MoonshineAsrModel.SMALL
                    }
                    if (!asrModelManuallySelected) asrModel = targetAsr
                    if (
                        selected == ConversationEngineMode.FULL &&
                        (
                            !GemmaModelStore.hasUsableModel(context) ||
                                !MoonshineModelStore.isInstalled(context, targetAsr) ||
                                !KittenModelStore.isInstalled(context)
                        )
                    ) {
                        fullSetupError = null
                        fullSetupProgressPercent = null
                        fullSetupPhase = ""
                        fullSetupOpen = true
                    } else {
                        activateEngineMode(selected)
                    }
                }
            },
            onAsrModel = { selected ->
                if (!recording && !busy) {
                    val needsDownload = !MoonshineModelStore.isInstalled(context, selected)
                    activateAsrModel(selected)
                    if (needsDownload) startLiteAutomaticSetup()
                }
            },
            onSemanticEnabled = { enabled ->
                if (!recording && !busy) {
                    semanticEnabled = enabled
                    preferences.edit().putBoolean("semantic_enabled", enabled).apply()
                    lite.resetConversationContext()
                    if (enabled && !RuriSemanticModelStore.isInstalled(context)) {
                        startSemanticAutomaticSetup()
                    }
                }
            },
            onLevel = { englishLevel = it; preferences.edit().putString("level", it.name).apply() },
            onPreview = {
                status = ProductionEmmaStatus.SPEAKING
                voiceError = null
                statusMessage = "声を試聴しています。"
                latestEmmaText = "Hi, I'm $resolvedAiName. Hello, little one!"
                if (!speakEmma(latestEmmaText)) status = ProductionEmmaStatus.ERROR
            },
            onStopPreview = ::stopSession,
            onDownloadGemma = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GemmaModelStore.MODEL_DOWNLOAD_URL))) }
                    .onFailure { statusMessage = "ブラウザを開けませんでした。" }
            },
            onSelectGemma = { modelPicker.launch(arrayOf("*/*")) },
            onPrepareLite = ::startLiteAutomaticSetup,
            onLoadGemma = ::loadModel,
            onKeepScreenOn = {
                keepScreenOn = it
                preferences.edit().putBoolean("keep_screen_on", it).apply()
            },
            onExportDiagnostics = { diagnosticsExporter.launch("emma-beta13-diagnostics.txt") },
            onExportCrashDetails = { crashDetailsExporter.launch("emma-beta13-crash-details.zip") },
            onOpenTutorial = {
                stopSession()
                settingsOpen = false
                screenIntroductionPlayed = false
                screenIntroductionPlaying = false
                tutorialStep = 0
                tutorialUserSpoke = false
                autoStartPending = false
                status = ProductionEmmaStatus.IDLE
                statusMessage = "使い方を3ステップで確認しましょう。"
            },
            onResetAllData = {
                stopSession()
                runCatching {
                    gemma.close()
                    lite.close()
                    kitten.shutdown()
                    check(context.getSystemService(ActivityManager::class.java).clearApplicationUserData()) { "clearApplicationUserData failed" }
                }.onFailure {
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "アプリデータを削除できませんでした。"
                }
            },
        )
    } else {
        EmmaHomeScreen(
            visualState = visualState,
            mouthLevel = mouthLevel,
            statusTitle = statusTitle,
            statusMessage = statusMessage,
            voiceError = voiceError,
            showBusyIndicator = status == ProductionEmmaStatus.MODEL_IMPORTING || status == ProductionEmmaStatus.MODEL_LOADING || status == ProductionEmmaStatus.THINKING,
            recording = recording,
            modelReady = modelReady,
            busy = busy,
            autoRespond = autoRespond,
            latestTranscript = latestTranscript,
            latestEmmaText = latestEmmaText,
            aiName = resolvedAiName,
            engineMode = engineMode,
            backgroundPreparationActive = backgroundPreparationActive,
            backgroundPreparationPhase = backgroundPreparationPhase,
            backgroundPreparationPercent = backgroundPreparationPercent,
            onRequestParentFull = { parentFullPromptOpen = true },
            onOpenAbout = { aboutOpen = true },
            onOpenSettings = {
                if (recording || generating || pendingStartAfterPermission || status == ProductionEmmaStatus.SPEAKING) {
                    stopSession()
                }
                settingsOpen = true
            },
            onOpenPlay = { stopSession(); autoStartPending=false; featureScreen="play" },
            onStartSession = ::startSession,
            onStopSession = ::stopSession,
            onToggleAutoRespond = {
                autoRespond = it
                preferences.edit().putBoolean("auto_respond", it).apply()
                if (recording && !busy) {
                    status = ProductionEmmaStatus.LISTENING
                    statusMessage = if (it) "普通に話しかけてください。" else "手動モードです。「今すぐAIが返事する」で返します。"
                }
            },
            onManualRespond = { askEmma(automatic = false) },
            tutorialStep = tutorialStep,
            tutorialIntroReady = screenIntroductionPlayed,
            onTutorialNext = {
                if (tutorialStep == 0) tutorialStep = 1
            },
            onTutorialStartSession = {
                // Step 2 must remain actionable even if the self-introduction TTS
                // leaves a stale SPEAKING/busy state for one frame. Stop any
                // leftover tutorial speech before starting the microphone session.
                kitten.stop()
                screenIntroductionPlaying = false
                screenIntroductionPlayed = true
                if (!recording && status == ProductionEmmaStatus.SPEAKING) {
                    status = ProductionEmmaStatus.IDLE
                    statusMessage = "自己紹介が終わりました。"
                }
                tutorialUserSpoke = false
                tutorialStep = 2
                startSession()
            },
            onTutorialFinish = {
                preferences.edit().putBoolean("tutorial_completed_v1", true).apply()
                tutorialStep = null
                tutorialUserSpoke = false
                autoStartPending = false
            },
        )

        if (parentFullPromptOpen) {
            ParentFullPrompt(
                onUseFull = {
                    parentFullPromptOpen = false
                    preferences.edit()
                        .putString("audience_mode", "PARENT")
                        .apply()

                    val targetAsr = if (asrModelManuallySelected) asrModel else MoonshineAsrModel.SMALL
                    if (!asrModelManuallySelected) asrModel = targetAsr
                    val fullNeedsSetup =
                        !GemmaModelStore.hasUsableModel(context) ||
                        !MoonshineModelStore.isInstalled(context, targetAsr) ||
                        !KittenModelStore.isInstalled(context)

                    if (fullNeedsSetup) {
                        fullSetupError = null
                        fullSetupProgressPercent = null
                        fullSetupPhase = ""
                        fullSetupOpen = true
                    } else {
                        activateEngineMode(ConversationEngineMode.FULL)
                    }
                },
                onDismiss = { parentFullPromptOpen = false },
            )
        }
    }
}
