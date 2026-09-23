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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eltnegcellist.emma.ai.EnglishLevel
import com.eltnegcellist.emma.ai.GemmaEmmaClient
import com.eltnegcellist.emma.audio.AudioRingRecorder
import com.eltnegcellist.emma.audio.VoiceActivityEvent
import com.eltnegcellist.emma.model.GemmaModelStore
import com.eltnegcellist.emma.tts.DiagnosticStore
import com.eltnegcellist.emma.tts.EmmaSpeaker
import com.eltnegcellist.emma.tts.KokoroModelStore
import com.eltnegcellist.emma.tts.KokoroSpeaker
import com.eltnegcellist.emma.ui.EmmaAvatar
import com.eltnegcellist.emma.ui.EmmaVisualState
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must run before Compose creates Gemma or any speaker instance.
        DiagnosticStore.collectOnStartup(this)
        setContent {
            MaterialTheme {
                EmmaApp()
            }
        }
    }
}

private enum class EmmaStatus {
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
private fun EmmaApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    var englishLevel by remember { mutableStateOf(EnglishLevel.fromSaved(preferences.getString("level", null))) }
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
    var voiceName by remember { mutableStateOf(preferences.getString("voice", "").orEmpty()) }
    var availableVoices by remember { mutableStateOf(emptyList<String>()) }
    var latestTranscript by remember { mutableStateOf("") }
    var autoRespond by remember { mutableStateOf(preferences.getBoolean("auto_respond", true)) }
    var showAdvanced by remember { mutableStateOf(false) }
    var mouthLevel by remember { mutableStateOf(0f) }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val session = remember { SessionGate() }
    var disposed by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val recorder = remember { AudioRingRecorder() }
    val gemma = remember { GemmaEmmaClient(context) }
    val modelFile = remember { GemmaModelStore.modelFile(context) }

    var status by remember { mutableStateOf(EmmaStatus.IDLE) }
    var statusMessage by remember {
        mutableStateOf("Gemma 4 E2Bを読み込むと、会話を端末内だけで処理します。")
    }
    var latestEmmaText by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var autoStartPending by remember { mutableStateOf(true) }
    var pendingStartAfterPermission by remember { mutableStateOf(false) }
    var modelPresent by remember { mutableStateOf(GemmaModelStore.hasUsableModel(context)) }
    var modelReady by remember { mutableStateOf(false) }
    var kokoroInstalled by remember { mutableStateOf(KokoroModelStore.isInstalled(context)) }
    var kokoroOnly by remember { mutableStateOf(preferences.getBoolean("kokoro_only", false)) }
    var kokoroImporting by remember { mutableStateOf(false) }
    var lastSpeechMillis by remember { mutableStateOf<Long?>(null) }
    var endpointStartedNanos by remember { mutableStateOf<Long?>(null) }
    var ttsRequestedAfterEndpointMillis by remember { mutableStateOf<Long?>(null) }

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

    val speaker = remember {
        EmmaSpeaker(
            context = context,
            onVoicesReady = { voices -> if (!disposed) availableVoices = voices },
            onDone = {
                if (!disposed) {
                    mouthLevel = 0f
                    if (recording) recorder.resumeBuffering(clearExisting = true)
                    status = if (recording) EmmaStatus.LISTENING else EmmaStatus.IDLE
                    statusMessage = if (recording) {
                        if (autoRespond) "聞いています。普通に話しかけてください。" else "聞いています。話し終えたら「今返事して」を押してください。"
                    } else {
                        "試聴を終了しました。"
                    }
                }
            },
            onError = { message ->
                if (!disposed) {
                    mouthLevel = 0f
                    voiceError = message
                    if (status == EmmaStatus.SPEAKING) {
                        if (recording) recorder.resumeBuffering(clearExisting = true)
                        status = EmmaStatus.ERROR
                        statusMessage = message
                    }
                }
            },
        )
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
                    status = if (recording) EmmaStatus.LISTENING else EmmaStatus.IDLE
                    statusMessage = if (recording) {
                        if (autoRespond) "聞いています。普通に話しかけてください。" else "聞いています。話し終えたら「今返事して」を押してください。"
                    } else {
                        "Kokoro試聴を終了しました。"
                    }
                }
            },
            onError = { message ->
                if (!disposed) {
                    mouthLevel = 0f
                    voiceError = "$message Android音声へ切り替えます。"
                    if (!speaker.speak(latestEmmaText.ifBlank { "Hello, little one. Look at you!" }, speechRate, voiceName)) {
                        if (recording) recorder.resumeBuffering(clearExisting = true)
                        status = EmmaStatus.ERROR
                        statusMessage = message
                    }
                }
            },
            onAmplitude = { amplitude -> if (!disposed) mouthLevel = amplitude },
        )
    }

    fun speakEmma(text: String): Boolean {
        lastSpeechMillis = null
        ttsRequestedAfterEndpointMillis = endpointStartedNanos?.let { started ->
            (System.nanoTime() - started) / 1_000_000L
        }
        return if (kokoroInstalled) kokoro.speak(text, speechRate) else speaker.speak(text, speechRate, voiceName)
    }

    fun loadModel() {
        if (disposed) return
        if (kokoroOnly) {
            statusMessage = "Kokoroだけの診断モードではGemmaを起動しません。"
            return
        }
        if (!GemmaModelStore.hasUsableModel(context)) {
            modelPresent = false
            modelReady = false
            status = EmmaStatus.ERROR
            statusMessage = "Gemma 4 E2Bの.litertlmモデルを先に選択してください。"
            showAdvanced = true
            return
        }

        modelPresent = true
        modelReady = false
        status = EmmaStatus.MODEL_LOADING
        statusMessage = "Gemma 4 E2BをGPUへ読み込んでいます…"

        EmmaWorkQueue.execute {
            val result = gemma.initialize(modelFile.absolutePath)
            mainHandler.post {
                if (disposed) return@post
                result.onSuccess {
                    modelReady = true
                    status = EmmaStatus.IDLE
                    statusMessage = "Emmaの準備ができました。"
                }.onFailure { error ->
                    modelReady = false
                    status = EmmaStatus.ERROR
                    statusMessage = "Gemmaの読み込みに失敗しました: ${error.message ?: error.javaClass.simpleName}"
                    showAdvanced = true
                }
            }
        }
    }

    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && !disposed) {
            modelReady = false
            status = EmmaStatus.MODEL_IMPORTING
            statusMessage = "Gemma 4 E2BモデルをEmmaへコピーしています…"

            EmmaWorkQueue.execute {
                gemma.close()
                val result = GemmaModelStore.importModel(context, uri) { percent ->
                    mainHandler.post {
                        if (disposed) return@post
                        statusMessage = if (percent != null) {
                            "Gemma 4 E2BモデルをEmmaへコピーしています… $percent%"
                        } else {
                            "Gemma 4 E2BモデルをEmmaへコピーしています…"
                        }
                    }
                }

                mainHandler.post {
                    if (disposed) return@post
                    result.onSuccess {
                        modelPresent = true
                        loadModel()
                    }.onFailure { error ->
                        modelPresent = GemmaModelStore.hasUsableModel(context)
                        status = EmmaStatus.ERROR
                        statusMessage = "モデルの取り込みに失敗しました: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
            }
        }
    }

    fun startKokoroAutomaticSetup() {
        if (disposed || kokoroImporting) return
        kokoroImporting = true
        status = EmmaStatus.MODEL_IMPORTING
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
                    kokoro.resetModel()
                    kokoroInstalled = true
                    status = EmmaStatus.IDLE
                    statusMessage = "Kokoro準備完了。af_heart女性音声で話します。"
                }
            }.onFailure { error ->
                mainHandler.post {
                    if (disposed) return@post
                    kokoroImporting = false
                    kokoroInstalled = KokoroModelStore.isInstalled(context)
                    status = EmmaStatus.ERROR
                    statusMessage = "Kokoroの準備に失敗しました: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    fun beginRecording() {
        session.start()
        runCatching { recorder.start() }
            .onSuccess {
                recording = true
                status = EmmaStatus.LISTENING
                statusMessage = if (autoRespond) {
                    "聞いています。普通に話しかけてください。"
                } else {
                    "聞いています。話し終えたら「今返事して」を押してください。"
                }
            }
            .onFailure {
                session.stop()
                status = EmmaStatus.ERROR
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
            status = EmmaStatus.ERROR
            statusMessage = "マイク権限が必要です。"
        }
    }

    fun startSession() {
        if (!modelReady) {
            status = EmmaStatus.ERROR
            statusMessage = "Gemma 4 E2Bの読み込み完了後にセッションを開始してください。"
            showAdvanced = true
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
        speaker.stop()
        kokoro.stop()
        recorder.stop()
        recording = false
        generating = false
        mouthLevel = 0f
        endpointStartedNanos = null
        ttsRequestedAfterEndpointMillis = null
        status = EmmaStatus.IDLE
        statusMessage = "セッション停止中"
    }

    fun askEmma(automatic: Boolean = false) {
        if (!recording || generating || status == EmmaStatus.THINKING || status == EmmaStatus.SPEAKING) return
        if (!modelReady || !gemma.isReady()) {
            status = EmmaStatus.ERROR
            statusMessage = "Gemma 4 E2Bがまだ準備できていません。"
            return
        }
        val minimumSeconds = if (automatic) 0.45 else 0.8
        if (recorder.secondsAvailable() < minimumSeconds) {
            if (!automatic) {
                status = EmmaStatus.LISTENING
                statusMessage = "まだ会話が短すぎます。もう少し話してください。"
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
        status = EmmaStatus.THINKING
        statusMessage = "Emmaが考えています…"

        DiagnosticStore.mark(
            context,
            "emma_turn_processing_started",
            "automatic=$automatic wavBytes=${wav.size}",
        )

        EmmaWorkQueue.execute {
            val result = gemma.createEnglishIsland(wav, requestedLevel) { transcript ->
                mainHandler.post {
                    if (!disposed && session.accepts(ticket)) {
                        latestTranscript = transcript.ifBlank { "[不明]" }
                        statusMessage = "Emmaが返事を考えています…"
                    }
                }
            }
            mainHandler.post {
                if (disposed) return@post
                generating = false
                if (!session.accepts(ticket)) return@post
                result.onSuccess { english ->
                    latestEmmaText = english
                    status = EmmaStatus.SPEAKING
                    statusMessage = "Emmaが話しています。"
                    voiceError = null
                    if (!speakEmma(english)) {
                        recorder.resumeBuffering(clearExisting = true)
                        endpointStartedNanos = null
                        ttsRequestedAfterEndpointMillis = null
                        status = EmmaStatus.ERROR
                        statusMessage = voiceError ?: "音声を再生できませんでした。"
                    }
                }.onFailure { error ->
                    endpointStartedNanos = null
                    ttsRequestedAfterEndpointMillis = null
                    recorder.resumeBuffering(clearExisting = true)
                    val noSpeech = error.message?.contains("聞き取れませんでした") == true
                    if (automatic && noSpeech) {
                        status = EmmaStatus.LISTENING
                        statusMessage = "聞いています。普通に話しかけてください。"
                    } else {
                        status = EmmaStatus.ERROR
                        statusMessage = "Emmaの生成に失敗しました: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!kokoroOnly && modelPresent) loadModel()
        if (!modelPresent) showAdvanced = true
    }

    LaunchedEffect(modelReady) {
        if (modelReady && autoStartPending && !recording && !kokoroOnly) {
            autoStartPending = false
            startSession()
        }
    }

    DisposableEffect(Unit) {
        val lifecycle = (context as ComponentActivity).lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && (recording || generating || pendingStartAfterPermission || status == EmmaStatus.SPEAKING)) {
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
                        if (!generating && status != EmmaStatus.SPEAKING) {
                            status = EmmaStatus.ENDPOINT_WAIT
                            statusMessage = "聞いています…"
                        }
                    }
                    is VoiceActivityEvent.Endpoint -> {
                        DiagnosticStore.mark(
                            context,
                            "auto_endpoint",
                            "speechMs=${event.speechMillis} silenceMs=${event.silenceMillis} autoRespond=$autoRespond",
                        )
                        if (autoRespond && !generating && status != EmmaStatus.SPEAKING) {
                            endpointStartedNanos = System.nanoTime()
                            status = EmmaStatus.UNDERSTOOD
                            statusMessage = "聞きました。"
                            // Give the acknowledgement animation a moment to register before THINKING.
                            mainHandler.postDelayed({
                                if (!disposed && recording && session.accepts(ticket)) askEmma(automatic = true)
                            }, 140L)
                        } else if (!generating && status != EmmaStatus.SPEAKING) {
                            status = EmmaStatus.LISTENING
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
                    status = EmmaStatus.ERROR
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
            speaker.shutdown()
            kokoro.shutdown()
            mainHandler.removeCallbacksAndMessages(null)
            EmmaWorkQueue.execute { gemma.close() }
        }
    }

    val busy = generating || kokoroImporting || status == EmmaStatus.MODEL_IMPORTING ||
        status == EmmaStatus.MODEL_LOADING || status == EmmaStatus.THINKING || status == EmmaStatus.SPEAKING

    val visualState = when (status) {
        EmmaStatus.IDLE, EmmaStatus.MODEL_IMPORTING, EmmaStatus.MODEL_LOADING -> EmmaVisualState.IDLE
        EmmaStatus.LISTENING -> EmmaVisualState.LISTENING
        EmmaStatus.ENDPOINT_WAIT -> EmmaVisualState.ENDPOINT_WAIT
        EmmaStatus.UNDERSTOOD -> EmmaVisualState.UNDERSTOOD
        EmmaStatus.THINKING -> EmmaVisualState.THINKING
        EmmaStatus.SPEAKING -> EmmaVisualState.SPEAKING
        EmmaStatus.ERROR -> EmmaVisualState.ERROR
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Emma", style = MaterialTheme.typography.headlineLarge)
            Text("Gemma 4 E2B + Kokoro · 完全ローカル", style = MaterialTheme.typography.bodyMedium)

            EmmaAvatar(
                state = visualState,
                mouthLevel = mouthLevel,
                modifier = Modifier.fillMaxWidth(),
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        when (status) {
                            EmmaStatus.IDLE -> "待機中"
                            EmmaStatus.MODEL_IMPORTING -> "モデル取り込み中"
                            EmmaStatus.MODEL_LOADING -> "Emmaを起動中"
                            EmmaStatus.LISTENING -> "聞いています"
                            EmmaStatus.ENDPOINT_WAIT -> "聞いています"
                            EmmaStatus.UNDERSTOOD -> "聞きました"
                            EmmaStatus.THINKING -> "考えています"
                            EmmaStatus.SPEAKING -> "話しています"
                            EmmaStatus.ERROR -> "確認してください"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(statusMessage)
                    voiceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (status == EmmaStatus.MODEL_IMPORTING || status == EmmaStatus.MODEL_LOADING || status == EmmaStatus.THINKING) {
                        CircularProgressIndicator()
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!recording && status != EmmaStatus.SPEAKING) {
                    Button(
                        onClick = ::startSession,
                        enabled = !kokoroOnly && modelReady && !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("セッション開始")
                    }
                } else {
                    OutlinedButton(onClick = ::stopSession, modifier = Modifier.weight(1f)) {
                        Text("停止")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自動で返事", style = MaterialTheme.typography.titleSmall)
                    Text("話し終わりを検出してEmmaが返します", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = autoRespond,
                    onCheckedChange = {
                        autoRespond = it
                        preferences.edit().putBoolean("auto_respond", it).apply()
                        if (recording && !busy) {
                            status = EmmaStatus.LISTENING
                            statusMessage = if (it) "聞いています。普通に話しかけてください。" else "手動モードです。「今返事して」で返します。"
                        }
                    },
                    enabled = status != EmmaStatus.SPEAKING,
                )
            }

            if (recording) {
                OutlinedButton(
                    onClick = { askEmma(automatic = false) },
                    enabled = modelReady && !generating && status != EmmaStatus.THINKING && status != EmmaStatus.SPEAKING,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("今返事して")
                }
            }

            if (latestEmmaText.isNotBlank() || latestTranscript.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (latestEmmaText.isNotBlank()) {
                            Text("Emma", style = MaterialTheme.typography.labelLarge)
                            Text(latestEmmaText, style = MaterialTheme.typography.bodyLarge)
                        }
                        if (latestTranscript.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("聞き取り: $latestTranscript", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "設定を閉じる" else "音声・モデル・診断設定")
            }

            if (showAdvanced) {
                SpeechSettings(
                    level = englishLevel,
                    rate = speechRate,
                    voiceName = voiceName,
                    voices = availableVoices,
                    enabled = !busy && !recording,
                    previewing = !recording && status == EmmaStatus.SPEAKING,
                    onStopPreview = ::stopSession,
                    onLevel = { englishLevel = it; preferences.edit().putString("level", it.name).apply() },
                    onRate = { speechRate = it; preferences.edit().putFloat("rate", it).apply() },
                    onVoice = { voiceName = it; preferences.edit().putString("voice", it).apply() },
                    onPreview = {
                        status = EmmaStatus.SPEAKING
                        voiceError = null
                        statusMessage = "声を試聴しています。"
                        latestEmmaText = "Hello, little one. Look at you!"
                        if (!speakEmma(latestEmmaText)) status = EmmaStatus.ERROR
                    },
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("モデル管理", style = MaterialTheme.typography.titleMedium)

                        Text("Gemma 4 E2B", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                modelReady -> "読み込み済み。会話できます。"
                                modelPresent -> "モデル導入済み。読み込み待ちです。"
                                else -> "未導入。初回だけモデルをダウンロードして選択してください。"
                            },
                        )
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GemmaModelStore.MODEL_DOWNLOAD_URL)))
                                }.onFailure { statusMessage = "ブラウザを開けませんでした。" }
                            },
                            enabled = !busy && !recording,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Gemmaモデルをダウンロード") }
                        if (!modelPresent) {
                            Button(
                                onClick = { modelPicker.launch(arrayOf("*/*")) },
                                enabled = !busy && !recording,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("ダウンロードしたGemmaモデルを選択") }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (!modelReady) {
                                    Button(
                                        onClick = ::loadModel,
                                        enabled = !busy && !recording,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("読み込む") }
                                }
                                OutlinedButton(
                                    onClick = { modelPicker.launch(arrayOf("*/*")) },
                                    enabled = !busy && !recording,
                                    modifier = Modifier.weight(1f),
                                ) { Text("選び直す") }
                            }
                        }

                        Text("Kokoro · af_heart女性音声", style = MaterialTheme.typography.titleSmall)
                        Text(if (kokoroInstalled) "導入済み。EmmaはKokoroで話します。" else "未導入。必要ならアプリ内で自動準備します。")
                        if (!kokoroInstalled) {
                            Button(
                                onClick = ::startKokoroAutomaticSetup,
                                enabled = !busy && !recording,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Kokoroを自動で準備") }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("診断", style = MaterialTheme.typography.titleMedium)
                        Text("発話検出・Gemma・Kokoroの処理時間を端末内ログに記録します。音声そのものは保存しません。", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = { diagnosticsExporter.launch("emma-v1.2-diagnostics.txt") },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("診断情報を書き出す") }
                        OutlinedButton(
                            onClick = { crashDetailsExporter.launch("emma-v1.2-crash-details.zip") },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("クラッシュ詳細を書き出す") }
                        lastSpeechMillis?.let { Text("Kokoro最初の音: ${it}ms", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            Text(
                "処理: マイク → 自動発話検出 → 日本語聞き取り → 会話として英語生成（Gemma 4 E2B）→ Kokoro。すべて端末内で処理します。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
