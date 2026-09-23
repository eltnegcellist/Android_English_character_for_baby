package com.eltnegcellist.emma

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.eltnegcellist.emma.ai.ConversationEngineMode
import com.eltnegcellist.emma.ai.EnglishLevel
import com.eltnegcellist.emma.ai.GemmaEmmaClient
import com.eltnegcellist.emma.ai.LiteEmmaClient
import com.eltnegcellist.emma.asr.LiteAsrModelStore
import com.eltnegcellist.emma.audio.AudioRingRecorder
import com.eltnegcellist.emma.audio.VoiceActivityEvent
import com.eltnegcellist.emma.model.GemmaModelStore
import com.eltnegcellist.emma.tts.DiagnosticStore
import com.eltnegcellist.emma.tts.EmmaSpeaker
import com.eltnegcellist.emma.tts.KokoroModelStore
import com.eltnegcellist.emma.tts.KokoroSpeaker
import com.eltnegcellist.emma.tts.VoiceBackend
import com.eltnegcellist.emma.ui.EmmaTheme
import com.eltnegcellist.emma.ui.EmmaVisualState
import kotlin.math.abs

private const val NONVERBAL_RESPONSE_COOLDOWN_MS = 15_000L
private const val BABY_VOCAL_CONTEXT = "赤ちゃんが声を出している"

class ProductionMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticStore.collectOnStartup(this)
        setContent {
            EmmaTheme {
                ProductionEmmaApp()
            }
        }
    }
}

private enum class ProductionEmmaStatus {
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
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    var englishLevel by remember { mutableStateOf(EnglishLevel.fromSaved(preferences.getString("level", null))) }
    val initialEngineMode = remember {
        val saved = preferences.getString("conversation_engine_mode", null)
        when {
            saved != null -> ConversationEngineMode.fromSaved(saved)
            GemmaModelStore.hasUsableModel(context) -> ConversationEngineMode.FULL
            else -> ConversationEngineMode.LITE
        }.also { resolved ->
            preferences.edit()
                .putString("conversation_engine_mode", resolved.name)
                .apply()
        }
    }
    var engineMode by remember { mutableStateOf(initialEngineMode) }
    val initialRate = remember {
        val stored = preferences.getFloat("rate", 1.00f).coerceIn(0.70f, 1.10f)
        if (!preferences.getBoolean("rate_migrated_v121", false)) {
            val wasOldDefault = abs(stored - 0.72f) < 0.005f || abs(stored - 0.80f) < 0.005f
            val migrated = if (wasOldDefault) 1.00f else stored
            preferences.edit()
                .putFloat("rate", migrated)
                .putBoolean("rate_migrated_v12", true)
                .putBoolean("rate_migrated_v121", true)
                .apply()
            migrated
        } else {
            stored
        }
    }
    var speechRate by remember { mutableStateOf(initialRate) }
    var latestTranscript by remember { mutableStateOf("") }
    var autoRespond by remember { mutableStateOf(preferences.getBoolean("auto_respond", true)) }
    var settingsOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var parentFullPromptOpen by remember { mutableStateOf(false) }
    var onboardingOpen by remember {
        mutableStateOf(!preferences.getBoolean("onboarding_completed_v2", false))
    }
    var firstRunBusy by remember { mutableStateOf(false) }
    var firstRunReady by remember { mutableStateOf(false) }
    var firstRunPhase by remember { mutableStateOf("") }
    var firstRunProgressPercent by remember { mutableStateOf<Int?>(null) }
    var firstRunError by remember { mutableStateOf<String?>(null) }
    var voiceBackend by remember {
        mutableStateOf(VoiceBackend.fromSaved(preferences.getString("voice_backend", null)))
    }
    var mouthLevel by remember { mutableStateOf(0f) }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val session = remember { SessionGate() }
    var disposed by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val recorder = remember { AudioRingRecorder() }
    val gemma = remember { GemmaEmmaClient(context) }
    val lite = remember { LiteEmmaClient(context) }
    val modelFile = remember { GemmaModelStore.modelFile(context) }

    var status by remember { mutableStateOf(ProductionEmmaStatus.IDLE) }
    var statusMessage by remember {
        mutableStateOf("Emmaを準備しています。")
    }
    var latestEmmaText by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var autoStartPending by remember { mutableStateOf(!onboardingOpen) }
    var pendingStartAfterPermission by remember { mutableStateOf(false) }
    var modelPresent by remember {
        mutableStateOf(
            if (initialEngineMode == ConversationEngineMode.LITE) {
                LiteAsrModelStore.isInstalled(context)
            } else {
                GemmaModelStore.hasUsableModel(context)
            },
        )
    }
    var modelReady by remember { mutableStateOf(false) }
    var kokoroInstalled by remember { mutableStateOf(KokoroModelStore.isInstalled(context)) }
    var fullSetupOpen by remember {
        mutableStateOf(
            initialEngineMode == ConversationEngineMode.FULL &&
                (
                    !GemmaModelStore.hasUsableModel(context) ||
                        (voiceBackend == VoiceBackend.KOKORO && !KokoroModelStore.isInstalled(context))
                ),
        )
    }
    var fullSetupBusy by remember { mutableStateOf(false) }
    var fullSetupPhase by remember { mutableStateOf("") }
    var fullSetupProgressPercent by remember { mutableStateOf<Int?>(null) }
    var fullSetupError by remember { mutableStateOf<String?>(null) }
    var liteSetupBusy by remember { mutableStateOf(false) }
    var liteSetupPhase by remember { mutableStateOf("") }
    var liteSetupProgressPercent by remember { mutableStateOf<Int?>(null) }
    var kokoroOnly by remember { mutableStateOf(preferences.getBoolean("kokoro_only", false)) }
    var kokoroImporting by remember { mutableStateOf(false) }
    var lastSpeechMillis by remember { mutableStateOf<Long?>(null) }
    var endpointStartedNanos by remember { mutableStateOf<Long?>(null) }
    var ttsRequestedAfterEndpointMillis by remember { mutableStateOf<Long?>(null) }
    var lastNonverbalResponseAtMillis by remember { mutableStateOf(0L) }

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

    val kokoro = remember {
        KokoroSpeaker(
            context = context,
            onDone = { firstAudioMillis, totalMillis ->
                if (!disposed) {
                    mouthLevel = 0f
                    lastSpeechMillis = firstAudioMillis
                    val queued = ttsRequestedAfterEndpointMillis
                    if (queued != null) {
                        DiagnosticStore.mark(
                            context,
                            "emma_response_latency",
                            "endpointToTtsRequestMs=$queued kokoroToFirstAudioMs=$firstAudioMillis endpointToFirstAudioMs=${queued + firstAudioMillis} kokoroTotalMs=$totalMillis",
                        )
                    }
                    endpointStartedNanos = null
                    ttsRequestedAfterEndpointMillis = null
                    if (recording) recorder.resumeBuffering(clearExisting = true)
                    status = if (recording) ProductionEmmaStatus.LISTENING else ProductionEmmaStatus.IDLE
                    statusMessage = if (recording) {
                        if (autoRespond) "普通に話しかけてください。" else "話し終えたら「今返事して」を押してください。"
                    } else {
                        "試聴を終了しました。"
                    }
                }
            },
            onError = { message ->
                if (!disposed) {
                    mouthLevel = 0f
                    voiceError = message
                    if (recording) recorder.resumeBuffering(clearExisting = true)
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "Kokoro音声の生成または再生に失敗しました: $message"
                }
            },
            onAmplitude = { amplitude -> if (!disposed) mouthLevel = amplitude },
        )
    }

    val androidSpeaker = remember {
        EmmaSpeaker(
            context = context,
            onDone = {
                if (!disposed) {
                    mouthLevel = 0f
                    endpointStartedNanos = null
                    ttsRequestedAfterEndpointMillis = null
                    if (recording) recorder.resumeBuffering(clearExisting = true)
                    status = if (recording) ProductionEmmaStatus.LISTENING else ProductionEmmaStatus.IDLE
                    statusMessage = if (recording) {
                        if (autoRespond) "普通に話しかけてください。" else "話し終えたら「今返事して」を押してください。"
                    } else {
                        "試聴を終了しました。"
                    }
                }
            },
            onError = { message ->
                if (!disposed) {
                    voiceError = message
                    if (recording) recorder.resumeBuffering(clearExisting = true)
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "Android標準音声の再生に失敗しました: $message"
                }
            },
        )
    }

    fun speakEmma(text: String): Boolean {
        lastSpeechMillis = null
        ttsRequestedAfterEndpointMillis = endpointStartedNanos?.let { started ->
            (System.nanoTime() - started) / 1_000_000L
        }
        return when (voiceBackend) {
            VoiceBackend.KOKORO -> kokoroInstalled && kokoro.speak(text, speechRate)
            VoiceBackend.ANDROID -> androidSpeaker.speak(
                text,
                rate = (speechRate * 0.84f).coerceIn(0.65f, 0.95f),
            )
        }
    }

    fun loadModel() {
        if (disposed) return
        if (kokoroOnly) {
            statusMessage = "Kokoroだけの診断モードでは会話エンジンを起動しません。"
            return
        }

        val requiredModelPresent = if (engineMode == ConversationEngineMode.LITE) {
            LiteAsrModelStore.isInstalled(context)
        } else {
            GemmaModelStore.hasUsableModel(context)
        }
        if (!requiredModelPresent) {
            modelPresent = false
            modelReady = false
            status = ProductionEmmaStatus.ERROR
            statusMessage = if (engineMode == ConversationEngineMode.LITE) {
                "日本語の聞き取りデータを先に準備してください。"
            } else {
                "会話モデルを先に設定してください。"
            }
            settingsOpen = true
            return
        }

        modelPresent = true
        modelReady = false
        status = ProductionEmmaStatus.MODEL_LOADING
        statusMessage = if (engineMode == ConversationEngineMode.LITE) {
            "Emmaを起動しています…"
        } else {
            "Emma Fullを起動しています…"
        }
        val requestedMode = engineMode

        EmmaWorkQueue.execute {
            val result = if (requestedMode == ConversationEngineMode.LITE) {
                gemma.close()
                lite.initialize()
            } else {
                lite.close()
                gemma.initialize(modelFile.absolutePath)
            }
            mainHandler.post {
                if (disposed || engineMode != requestedMode) return@post
                result.onSuccess {
                    modelReady = true
                    status = ProductionEmmaStatus.IDLE
                    statusMessage = if (requestedMode == ConversationEngineMode.LITE) {
                        "Emmaの準備ができました。"
                    } else {
                        "Fullの準備ができました。"
                    }
                }.onFailure { error ->
                    modelReady = false
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "Emmaの起動に失敗しました: ${error.message ?: error.javaClass.simpleName}"
                    settingsOpen = true
                }
            }
        }
    }

    fun activateEngineMode(selected: ConversationEngineMode) {
        engineMode = selected
        preferences.edit()
            .putString("conversation_engine_mode", selected.name)
            .apply()
        if (selected == ConversationEngineMode.LITE) {
            preferences.edit().putString("audience_mode", "BABY").apply()
        }
        modelReady = false
        modelPresent = if (selected == ConversationEngineMode.LITE) {
            LiteAsrModelStore.isInstalled(context)
        } else {
            GemmaModelStore.hasUsableModel(context)
        }
        status = ProductionEmmaStatus.IDLE
        statusMessage = if (selected == ConversationEngineMode.LITE) {
            if (modelPresent) "標準のEmmaを選びました。起動します…" else "日本語の聞き取りを準備してください。"
        } else {
            if (modelPresent) "Fullを選びました。起動します…" else "Fullを選びました。Gemmaを準備してください。"
        }

        EmmaWorkQueue.execute {
            if (selected == ConversationEngineMode.LITE) {
                gemma.close()
            } else {
                lite.close()
            }
        }
        if (modelPresent) {
            mainHandler.post { if (!disposed) loadModel() }
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
                        modelPresent = GemmaModelStore.hasUsableModel(context)
                        status = ProductionEmmaStatus.ERROR
                        statusMessage = "モデルの取り込みに失敗しました: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
            }
        }
    }

    fun startLiteAutomaticSetup() {
        if (disposed || liteSetupBusy) return

        liteSetupBusy = true
        liteSetupProgressPercent = 0
        liteSetupPhase = "Whisperをダウンロードしています…"
        modelReady = false
        status = ProductionEmmaStatus.MODEL_IMPORTING
        statusMessage = "Whisperを準備しています…"

        EmmaWorkQueue.execute {
            lite.close()
            LiteAsrModelStore.downloadAndInstall(context) { percent ->
                mainHandler.post {
                    if (!disposed) {
                        liteSetupProgressPercent = percent
                        liteSetupPhase = when {
                            percent == null -> "Whisperをダウンロードしています…"
                            percent < 70 -> "Whisperをダウンロードしています…"
                            percent < 93 -> "Whisperを展開・検証しています…"
                            else -> "Whisperを配置しています…"
                        }
                        statusMessage = "Whisperを準備しています… ${percent?.let { "$it%" } ?: ""}"
                    }
                }
            }.onSuccess {
                mainHandler.post {
                    if (disposed) return@post
                    liteSetupBusy = false
                    liteSetupProgressPercent = 100
                    modelPresent = true
                    statusMessage = "日本語の聞き取り準備ができました。Emmaを起動します…"
                    if (engineMode == ConversationEngineMode.LITE) {
                        loadModel()
                    } else {
                        status = ProductionEmmaStatus.IDLE
                    }
                }
            }.onFailure { error ->
                mainHandler.post {
                    if (disposed) return@post
                    liteSetupBusy = false
                    liteSetupProgressPercent = null
                    modelPresent = LiteAsrModelStore.isInstalled(context)
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "Whisperの準備に失敗しました: ${error.message ?: error.javaClass.simpleName}"
                    settingsOpen = true
                }
            }
        }
    }

    fun startFirstRunSetup(useKokoro: Boolean) {
        if (disposed || firstRunBusy) return

        firstRunBusy = true
        firstRunReady = false
        firstRunError = null
        firstRunProgressPercent = 0
        firstRunPhase = "Whisperを準備しています…"
        status = ProductionEmmaStatus.MODEL_IMPORTING
        statusMessage = "Emmaの初期設定をしています…"
        engineMode = ConversationEngineMode.LITE
        preferences.edit()
            .putString("conversation_engine_mode", ConversationEngineMode.LITE.name)
            .putString("audience_mode", "BABY")
            .apply()

        EmmaWorkQueue.execute {
            runCatching {
                gemma.close()
                lite.close()

                if (!LiteAsrModelStore.isInstalled(context)) {
                    LiteAsrModelStore.downloadAndInstall(context) { percent ->
                        mainHandler.post {
                            if (!disposed) {
                                firstRunPhase = when {
                                    percent == null -> "Whisperをダウンロードしています…"
                                    percent < 70 -> "Whisperをダウンロードしています…"
                                    percent < 93 -> "Whisperを展開・検証しています…"
                                    else -> "Whisperを配置しています…"
                                }
                                firstRunProgressPercent = percent
                            }
                        }
                    }.getOrThrow()
                }

                if (useKokoro && !KokoroModelStore.isInstalled(context)) {
                    mainHandler.post {
                        if (!disposed) {
                            firstRunPhase = "Kokoroの温かい声を準備しています…"
                            firstRunProgressPercent = 0
                        }
                    }
                    KokoroModelStore.downloadAndInstall(context) { percent ->
                        mainHandler.post {
                            if (!disposed) {
                                firstRunPhase = "Kokoroの温かい声を準備しています…"
                                firstRunProgressPercent = percent
                            }
                        }
                    }.getOrThrow()
                }

                mainHandler.post {
                    if (!disposed) {
                        firstRunPhase = "Emmaを起動しています…"
                        firstRunProgressPercent = null
                    }
                }
                lite.initialize().getOrThrow()
            }.onSuccess {
                mainHandler.post {
                    if (disposed) return@post
                    modelPresent = true
                    modelReady = true
                    kokoroInstalled = KokoroModelStore.isInstalled(context)
                    if (useKokoro && kokoroInstalled) {
                        voiceBackend = VoiceBackend.KOKORO
                        kokoro.resetModel()
                    } else {
                        voiceBackend = VoiceBackend.ANDROID
                    }
                    preferences.edit()
                        .putString("voice_backend", voiceBackend.savedValue)
                        .apply()
                    firstRunBusy = false
                    firstRunReady = true
                    firstRunProgressPercent = 100
                    firstRunPhase = "準備できました"
                    status = ProductionEmmaStatus.IDLE
                    statusMessage = "初期設定が完了しました。"
                }
            }.onFailure { error ->
                mainHandler.post {
                    if (disposed) return@post
                    modelPresent = LiteAsrModelStore.isInstalled(context)
                    modelReady = false
                    kokoroInstalled = KokoroModelStore.isInstalled(context)
                    firstRunBusy = false
                    firstRunReady = false
                    firstRunProgressPercent = null
                    firstRunError = error.message
                        ?: "初期設定を完了できませんでした。通信環境と空き容量を確認してください。"
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "Emmaの初期設定を完了できませんでした。"
                }
            }
        }
    }

    fun startKokoroAutomaticSetup() {
        if (disposed || kokoroImporting) return
        kokoroImporting = true
        status = ProductionEmmaStatus.MODEL_IMPORTING
        statusMessage = "Kokoroの温かい声を準備しています…"

        EmmaWorkQueue.execute {
            KokoroModelStore.downloadAndInstall(context) { percent ->
                mainHandler.post {
                    if (!disposed) {
                        statusMessage = "Kokoroの温かい声を準備しています… ${percent?.let { "$it%" } ?: ""}"
                    }
                }
            }.onSuccess {
                mainHandler.post {
                    if (disposed) return@post
                    kokoroImporting = false
                    kokoroInstalled = true
                    voiceBackend = VoiceBackend.KOKORO
                    preferences.edit().putString("voice_backend", VoiceBackend.KOKORO.savedValue).apply()
                    kokoro.resetModel()
                    status = ProductionEmmaStatus.IDLE
                    statusMessage = "Kokoroの準備ができました。"
                }
            }.onFailure { error ->
                mainHandler.post {
                    if (disposed) return@post
                    kokoroImporting = false
                    kokoroInstalled = KokoroModelStore.isInstalled(context)
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "Kokoroの準備に失敗しました: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    fun startFullAutomaticSetup() {
        if (disposed || fullSetupBusy) return

        fullSetupBusy = true
        fullSetupError = null
        fullSetupProgressPercent = null
        fullSetupPhase = "Fullの準備を始めています…"
        status = ProductionEmmaStatus.MODEL_IMPORTING
        statusMessage = "Emma Fullを準備しています…"

        EmmaWorkQueue.execute {
            runCatching {
                lite.close()
                gemma.close()

                if (!GemmaModelStore.hasUsableModel(context)) {
                    mainHandler.post {
                        if (!disposed) {
                            fullSetupPhase = "Gemmaをダウンロードしています（2GB超）"
                            fullSetupProgressPercent = 0
                        }
                    }
                    GemmaModelStore.downloadModel(context) { percent ->
                        mainHandler.post {
                            if (!disposed) {
                                fullSetupPhase = "Gemmaをダウンロードしています（2GB超）"
                                fullSetupProgressPercent = percent
                            }
                        }
                    }.getOrThrow()
                }

                if (voiceBackend == VoiceBackend.KOKORO && !KokoroModelStore.isInstalled(context)) {
                    mainHandler.post {
                        if (!disposed) {
                            fullSetupPhase = "Emmaの声（Kokoro）を準備しています"
                            fullSetupProgressPercent = 0
                        }
                    }
                    KokoroModelStore.downloadAndInstall(context) { percent ->
                        mainHandler.post {
                            if (!disposed) {
                                fullSetupPhase = "Emmaの声（Kokoro）を準備しています"
                                fullSetupProgressPercent = percent
                            }
                        }
                    }.getOrThrow()
                }
            }.onSuccess {
                mainHandler.post {
                    if (disposed) return@post
                    modelPresent = GemmaModelStore.hasUsableModel(context)
                    kokoroInstalled = KokoroModelStore.isInstalled(context)
                    if (voiceBackend == VoiceBackend.KOKORO && kokoroInstalled) {
                        kokoro.resetModel()
                    }
                    fullSetupBusy = false
                    fullSetupProgressPercent = 100
                    fullSetupPhase = "Fullの準備ができました"
                    fullSetupOpen = false
                    fullSetupError = null
                    activateEngineMode(ConversationEngineMode.FULL)
                }
            }.onFailure { error ->
                mainHandler.post {
                    if (disposed) return@post
                    modelPresent = if (engineMode == ConversationEngineMode.FULL) {
                        GemmaModelStore.hasUsableModel(context)
                    } else {
                        LiteAsrModelStore.isInstalled(context)
                    }
                    kokoroInstalled = KokoroModelStore.isInstalled(context)
                    fullSetupBusy = false
                    fullSetupProgressPercent = null
                    fullSetupError = error.message
                        ?: "Fullの準備を完了できませんでした。通信環境と空き容量を確認してください。"
                    status = ProductionEmmaStatus.ERROR
                    statusMessage = "Emma Fullの準備を完了できませんでした。"
                }
            }
        }
    }

    fun beginRecording() {
        session.start()
        runCatching { recorder.start() }
            .onSuccess {
                recording = true
                status = ProductionEmmaStatus.LISTENING
                statusMessage = if (autoRespond) "普通に話しかけてください。" else "話し終えたら「今返事して」を押してください。"
            }
            .onFailure {
                session.stop()
                status = ProductionEmmaStatus.ERROR
                statusMessage = it.message ?: "録音を開始できませんでした。"
            }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (disposed) return@rememberLauncherForActivityResult
        if (granted && pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            beginRecording()
        } else if (!granted) {
            pendingStartAfterPermission = false
            status = ProductionEmmaStatus.ERROR
            statusMessage = "マイク権限が必要です。"
        }
    }

    fun startSession() {
        if (voiceBackend == VoiceBackend.KOKORO && !kokoroInstalled) {
            status = ProductionEmmaStatus.ERROR
            statusMessage = "Kokoroを準備するか、Android標準音声を選んでください。"
            settingsOpen = true
            return
        }
        if (voiceBackend == VoiceBackend.ANDROID && !androidSpeaker.isReady()) {
            status = ProductionEmmaStatus.ERROR
            statusMessage = "Androidの英語音声を準備しています。少ししてからもう一度お試しください。"
            return
        }
        if (!modelReady) {
            status = ProductionEmmaStatus.ERROR
            statusMessage = "Emmaの準備完了後にセッションを開始してください。"
            settingsOpen = true
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingStartAfterPermission = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        beginRecording()
    }

    fun stopSession() {
        session.stop()
        pendingStartAfterPermission = false
        kokoro.stop()
        androidSpeaker.stop()
        recorder.stop()
        recording = false
        generating = false
        mouthLevel = 0f
        endpointStartedNanos = null
        ttsRequestedAfterEndpointMillis = null
        status = ProductionEmmaStatus.IDLE
        statusMessage = "セッションを終了しました。"
    }

    fun askEmma(automatic: Boolean = false) {
        if (!recording || generating || status == ProductionEmmaStatus.THINKING || status == ProductionEmmaStatus.SPEAKING) return
        val activeReady = if (engineMode == ConversationEngineMode.LITE) lite.isReady() else gemma.isReady()
        if (!modelReady || !activeReady) {
            status = ProductionEmmaStatus.ERROR
            statusMessage = "Emmaがまだ準備できていません。"
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

        DiagnosticStore.mark(context, "emma_turn_processing_started", "automatic=$automatic wavBytes=${wav.size}")

        val requestedMode = engineMode
        EmmaWorkQueue.execute {
            val result = if (requestedMode == ConversationEngineMode.LITE) {
                lite.createEnglishIsland(wav, requestedLevel) { transcript ->
                    mainHandler.post {
                        if (!disposed && session.accepts(ticket)) {
                            latestTranscript = transcript
                            statusMessage = "返答を選んでいます…"
                        }
                    }
                }
            } else {
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
                generating = false
                if (!session.accepts(ticket)) return@post
                result.onSuccess { english ->
                    val nowMillis = System.currentTimeMillis()
                    val infantVocalEvent = latestTranscript.trim().trimEnd('。') == BABY_VOCAL_CONTEXT
                    if (infantVocalEvent && nowMillis - lastNonverbalResponseAtMillis < NONVERBAL_RESPONSE_COOLDOWN_MS) {
                        recorder.resumeBuffering(clearExisting = true)
                        endpointStartedNanos = null
                        ttsRequestedAfterEndpointMillis = null
                        latestEmmaText = ""
                        status = ProductionEmmaStatus.LISTENING
                        statusMessage = "赤ちゃんの声を聞いています。"
                        DiagnosticStore.mark(context, "nonverbal_baby_response_suppressed", "cooldownMs=$NONVERBAL_RESPONSE_COOLDOWN_MS")
                        return@onSuccess
                    }
                    if (infantVocalEvent) lastNonverbalResponseAtMillis = nowMillis

                    latestEmmaText = english
                    status = ProductionEmmaStatus.SPEAKING
                    statusMessage = if (infantVocalEvent) "Emmaが赤ちゃんに話しかけています。" else "Emmaが話しています。"
                    voiceError = null
                    if (!speakEmma(english)) {
                        recorder.resumeBuffering(clearExisting = true)
                        endpointStartedNanos = null
                        ttsRequestedAfterEndpointMillis = null
                        status = ProductionEmmaStatus.ERROR
                        statusMessage = voiceError ?: "音声を再生できませんでした。"
                    }
                }.onFailure { error ->
                    endpointStartedNanos = null
                    ttsRequestedAfterEndpointMillis = null
                    val noSpeech = error.message?.contains("聞き取れませんでした") == true
                    val babyMode = preferences.getString("audience_mode", "BABY") != "PARENT"
                    val nowMillis = System.currentTimeMillis()
                    val canReactToNonverbal = automatic && noSpeech && babyMode &&
                        nowMillis - lastNonverbalResponseAtMillis >= NONVERBAL_RESPONSE_COOLDOWN_MS

                    if (canReactToNonverbal) {
                        val response = NonverbalBabyResponse.next(nowMillis)
                        lastNonverbalResponseAtMillis = nowMillis
                        latestTranscript = "ことばではない声を聞きました"
                        latestEmmaText = response
                        status = ProductionEmmaStatus.SPEAKING
                        statusMessage = "Emmaが赤ちゃんに話しかけています。"
                        voiceError = null
                        DiagnosticStore.mark(context, "nonverbal_baby_response", "cooldownMs=$NONVERBAL_RESPONSE_COOLDOWN_MS")
                        if (!speakEmma(response)) {
                            recorder.resumeBuffering(clearExisting = true)
                            status = ProductionEmmaStatus.ERROR
                            statusMessage = voiceError ?: "音声を再生できませんでした。"
                        }
                    } else {
                        recorder.resumeBuffering(clearExisting = true)
                        if (automatic && noSpeech) {
                            status = ProductionEmmaStatus.LISTENING
                            statusMessage = if (babyMode) {
                                "声を聞いています。"
                            } else {
                                "普通に話しかけてください。"
                            }
                        } else {
                            status = ProductionEmmaStatus.ERROR
                            statusMessage = "Emmaの生成に失敗しました: ${error.message ?: error.javaClass.simpleName}"
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (onboardingOpen) {
            settingsOpen = false
            fullSetupOpen = false
        } else if (
            initialEngineMode == ConversationEngineMode.FULL &&
            (
                !GemmaModelStore.hasUsableModel(context) ||
                    (voiceBackend == VoiceBackend.KOKORO && !KokoroModelStore.isInstalled(context))
            )
        ) {
            fullSetupOpen = true
        } else if (!kokoroOnly && modelPresent) {
            loadModel()
        } else if (!modelPresent) {
            settingsOpen = true
        }
    }

    LaunchedEffect(modelReady, onboardingOpen) {
        if (modelReady && !onboardingOpen && autoStartPending && !recording && !kokoroOnly) {
            autoStartPending = false
            startSession()
        }
    }

    DisposableEffect(Unit) {
        val lifecycle = (context as ComponentActivity).lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && (recording || generating || pendingStartAfterPermission || status == ProductionEmmaStatus.SPEAKING)) {
                stopSession()
            }
        }
        lifecycle.addObserver(observer)

        recorder.onVoiceActivity = { event ->
            val ticket = session.ticket()
            mainHandler.post {
                if (disposed || !recording || !session.accepts(ticket)) return@post
                when (event) {
                    VoiceActivityEvent.SpeechStarted -> {
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
                        if (autoRespond && !generating && status != ProductionEmmaStatus.SPEAKING) {
                            endpointStartedNanos = System.nanoTime()
                            status = ProductionEmmaStatus.UNDERSTOOD
                            statusMessage = "聞きました。"
                            mainHandler.postDelayed({
                                if (!disposed && recording && session.accepts(ticket)) askEmma(automatic = true)
                            }, 140L)
                        } else if (!generating && status != ProductionEmmaStatus.SPEAKING) {
                            status = ProductionEmmaStatus.LISTENING
                            statusMessage = "聞き取りました。「今返事して」でEmmaが返します。"
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
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            session.stop()
            recorder.onVoiceActivity = null
            recorder.onError = null
            recorder.stop()
            kokoro.shutdown()
            androidSpeaker.shutdown()
            mainHandler.removeCallbacksAndMessages(null)
            EmmaWorkQueue.execute {
                gemma.close()
                lite.close()
            }
        }
    }

    val busy = firstRunBusy || fullSetupBusy || liteSetupBusy || generating || kokoroImporting || status == ProductionEmmaStatus.MODEL_IMPORTING ||
        status == ProductionEmmaStatus.MODEL_LOADING || status == ProductionEmmaStatus.THINKING || status == ProductionEmmaStatus.SPEAKING

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
            busy = firstRunBusy,
            ready = firstRunReady,
            phase = firstRunPhase,
            progressPercent = firstRunProgressPercent,
            errorMessage = firstRunError,
            usingAndroidVoice = voiceBackend == VoiceBackend.ANDROID,
            onPrepareRecommended = { startFirstRunSetup(useKokoro = true) },
            onUseAndroidVoice = { startFirstRunSetup(useKokoro = false) },
            onOpenAbout = { aboutOpen = true },
            onStartEmma = {
                if (!firstRunBusy && firstRunReady) {
                    preferences.edit()
                        .putBoolean("onboarding_completed_v2", true)
                        .putString("voice_backend", voiceBackend.savedValue)
                        .apply()
                    onboardingOpen = false
                    autoStartPending = true
                    status = ProductionEmmaStatus.IDLE
                    statusMessage = "Emmaを始めます。"
                }
            },
        )
    } else if (liteSetupBusy) {
        LiteModelInstallDialog(
            phase = liteSetupPhase,
            progressPercent = liteSetupProgressPercent,
        )
    } else if (fullSetupOpen) {
        FullModeSetupScreen(
            busy = fullSetupBusy,
            phase = fullSetupPhase,
            progressPercent = fullSetupProgressPercent,
            errorMessage = fullSetupError,
            gemmaNeeded = !GemmaModelStore.hasUsableModel(context),
            kokoroNeeded = voiceBackend == VoiceBackend.KOKORO && !kokoroInstalled,
            onPrepare = ::startFullAutomaticSetup,
            onCancel = {
                if (!fullSetupBusy) {
                    fullSetupOpen = false
                    fullSetupError = null
                    if (
                        engineMode == ConversationEngineMode.FULL &&
                        (
                !GemmaModelStore.hasUsableModel(context) ||
                    (voiceBackend == VoiceBackend.KOKORO && !KokoroModelStore.isInstalled(context))
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
            level = englishLevel,
            rate = speechRate,
            enabled = !busy && !recording,
            previewing = !recording && status == ProductionEmmaStatus.SPEAKING,
            modelReady = modelReady,
            modelPresent = modelPresent,
            kokoroInstalled = kokoroInstalled,
            voiceBackend = voiceBackend,
            lastSpeechMillis = lastSpeechMillis,
            engineMode = engineMode,
            onBack = { settingsOpen = false },
            onOpenAbout = { aboutOpen = true },
            onEngineMode = { selected ->
                if (!recording && !busy && selected != engineMode) {
                    if (
                        selected == ConversationEngineMode.FULL &&
                        (
                !GemmaModelStore.hasUsableModel(context) ||
                    (voiceBackend == VoiceBackend.KOKORO && !KokoroModelStore.isInstalled(context))
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
            onLevel = { englishLevel = it; preferences.edit().putString("level", it.name).apply() },
            onRate = { speechRate = it; preferences.edit().putFloat("rate", it).apply() },
            onPreview = {
                status = ProductionEmmaStatus.SPEAKING
                voiceError = null
                statusMessage = "声を試聴しています。"
                latestEmmaText = "Hello, little one. Look at you!"
                if (!speakEmma(latestEmmaText)) status = ProductionEmmaStatus.ERROR
            },
            onStopPreview = ::stopSession,
            onDownloadGemma = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GemmaModelStore.MODEL_DOWNLOAD_URL))) }
                    .onFailure { statusMessage = "ブラウザを開けませんでした。" }
            },
            onSelectGemma = { modelPicker.launch(arrayOf("*/*")) },
            onDownloadLiteAsr = ::startLiteAutomaticSetup,
            onLoadGemma = ::loadModel,
            onDownloadKokoro = ::startKokoroAutomaticSetup,
            onUseAndroidVoice = {
                voiceBackend = VoiceBackend.ANDROID
                preferences.edit().putString("voice_backend", VoiceBackend.ANDROID.savedValue).apply()
                status = ProductionEmmaStatus.IDLE
                statusMessage = "Android標準音声を使用します。Kokoroは後から追加できます。"
            },
            onExportDiagnostics = { diagnosticsExporter.launch("emma-v1.2-diagnostics.txt") },
            onExportCrashDetails = { crashDetailsExporter.launch("emma-v1.2-crash-details.zip") },
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
            kokoroOnly = kokoroOnly,
            busy = busy,
            autoRespond = autoRespond,
            latestTranscript = latestTranscript,
            latestEmmaText = latestEmmaText,
            engineMode = engineMode,
            onRequestParentFull = { parentFullPromptOpen = true },
            onOpenAbout = { aboutOpen = true },
            onOpenSettings = { settingsOpen = true },
            onStartSession = ::startSession,
            onStopSession = ::stopSession,
            onToggleAutoRespond = {
                autoRespond = it
                preferences.edit().putBoolean("auto_respond", it).apply()
                if (recording && !busy) {
                    status = ProductionEmmaStatus.LISTENING
                    statusMessage = if (it) "普通に話しかけてください。" else "手動モードです。「今返事して」で返します。"
                }
            },
            onManualRespond = { askEmma(automatic = false) },
        )

        if (parentFullPromptOpen) {
            ParentFullPrompt(
                onUseFull = {
                    parentFullPromptOpen = false
                    preferences.edit()
                        .putString("audience_mode", "PARENT")
                        .apply()

                    val fullNeedsSetup =
                        !GemmaModelStore.hasUsableModel(context) ||
                            (voiceBackend == VoiceBackend.KOKORO && !KokoroModelStore.isInstalled(context))

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
