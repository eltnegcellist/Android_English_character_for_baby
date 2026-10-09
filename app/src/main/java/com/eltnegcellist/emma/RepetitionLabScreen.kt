package com.eltnegcellist.emma

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eltnegcellist.emma.ai.ConversationEngineMode
import com.eltnegcellist.emma.audio.AudioRingRecorder

@Composable
internal fun RepetitionLabScreen(
    runtime: ConversationController,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prompts = RepetitionLabCorpus.prompts
    var promptIndex by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("英語復唱と通常の日本語を、同じMoonshine日本語ASRで測定します。") }
    var resultCount by remember { mutableIntStateOf(RepetitionLabStore.resultCount(context)) }
    val recorder = remember { AudioRingRecorder(capacitySeconds = 10) }
    val prompt = prompts[promptIndex]
    val ready =
        runtime.engineMode == ConversationEngineMode.LITE &&
            runtime.modelReady &&
            runtime.lite.isReady()

    fun stopRecorderOnly() {
        if (recording) {
            recorder.stop()
            recording = false
        }
    }

    fun startRecordingInternal() {
        if (!ready || busy || recording) return
        if (resultCount == 0) {
            RepetitionLabStore.reset(context, runtime.asrModel.modelName)
        }
        runCatching { recorder.start() }
            .onSuccess {
                recording = true
                statusMessage = "録音中です。画面の文を自然に1回だけ話してください。"
            }
            .onFailure {
                statusMessage = "録音を開始できませんでした: ${it.message ?: it.javaClass.simpleName}"
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecordingInternal()
        else statusMessage = "測定にはマイク権限が必要です。"
    }

    fun startRecording() {
        if (!ready) {
            statusMessage = "Liteを起動し、Moonshine日本語ASRの準備が完了してから測定してください。"
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startRecordingInternal()
        }
    }

    fun stopAndTranscribe() {
        if (!recording || busy) return
        val durationMs = (recorder.secondsAvailable() * 1000.0).toLong()
        recorder.pauseBuffering()
        val wav = recorder.snapshotWav(maxSeconds = 10, consume = true)
        recorder.stop()
        recording = false
        busy = true
        statusMessage = "Moonshineで文字起こししています…"
        val capturedPrompt = prompt

        EmmaWorkQueue.execute {
            val result = runtime.lite.debugTranscribe(wav)
            val transcript = result.getOrElse { error ->
                "[ASR_ERROR] ${error.message ?: error.javaClass.simpleName}"
            }
            val saveResult = runCatching {
                RepetitionLabStore.save(
                    context = context,
                    prompt = capturedPrompt,
                    wavAudio = wav,
                    transcript = transcript,
                    durationMs = durationMs,
                )
            }
            runtime.mainHandler.post {
                busy = false
                if (saveResult.isFailure) {
                    statusMessage = "測定結果を保存できませんでした: ${saveResult.exceptionOrNull()?.message}"
                    return@post
                }
                resultCount = RepetitionLabStore.resultCount(context)
                statusMessage = buildString {
                    append("ASR結果: ").append(transcript)
                    append("\n保存しました（").append(resultCount).append("件）。")
                }
                if (promptIndex < prompts.lastIndex) promptIndex += 1
            }
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            busy = true
            statusMessage = "測定データを書き出しています…"
            EmmaWorkQueue.execute {
                val result = runCatching {
                    context.contentResolver.openOutputStream(uri).use { output ->
                        requireNotNull(output) { "出力先を開けませんでした。" }
                        RepetitionLabStore.writeZip(context, output)
                    }
                }
                runtime.mainHandler.post {
                    busy = false
                    statusMessage = if (result.isSuccess) {
                        "測定データを書き出しました。"
                    } else {
                        "書き出しに失敗しました: ${result.exceptionOrNull()?.message}"
                    }
                }
            }
        }
    }

    fun closeLab() {
        stopRecorderOnly()
        runtime.stopSession()
        onBack()
    }

    BackHandler { closeLab() }

    DisposableEffect(Unit) {
        recorder.onError = { message ->
            runtime.mainHandler.post {
                recording = false
                busy = false
                statusMessage = message
            }
        }
        onDispose {
            recorder.stop()
            recorder.onError = null
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = ::closeLab) { Text("← 戻る") }
                Text("Phase 0", style = MaterialTheme.typography.labelLarge)
            }

            Text(
                "英語復唱 ASR測定",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "本番の会話判定は変更しません。親がEmmaの英語をまねした音声と、通常の日本語を同じ日本語ASRへ通し、誤認識の実データを集めるための開発用測定です。",
                style = MaterialTheme.typography.bodyMedium,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "${promptIndex + 1} / ${prompts.size}　${prompt.kind.label}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        prompt.text,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    if (prompt.kind == RepetitionLabKind.ENGLISH_REPEAT) {
                        OutlinedButton(
                            onClick = { runtime.replay(prompt.text) },
                            enabled = ready && !recording && !busy && runtime.status != ProductionEmmaStatus.SPEAKING,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("① Emmaの声で聞く")
                        }
                    }

                    Button(
                        onClick = if (recording) ::stopAndTranscribe else ::startRecording,
                        enabled = !busy && runtime.status != ProductionEmmaStatus.SPEAKING,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (recording) "録音を止めて文字起こし" else if (prompt.kind == RepetitionLabKind.ENGLISH_REPEAT) "② 録音開始" else "録音開始")
                    }

                    Text(statusMessage, style = MaterialTheme.typography.bodySmall)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { if (promptIndex > 0) promptIndex -= 1 },
                            enabled = promptIndex > 0 && !recording && !busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("前へ") }
                        OutlinedButton(
                            onClick = { if (promptIndex < prompts.lastIndex) promptIndex += 1 },
                            enabled = promptIndex < prompts.lastIndex && !recording && !busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("次へ") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("測定データ", style = MaterialTheme.typography.titleMedium)
                    Text("保存済み: $resultCount 件 / 推奨 ${prompts.size} 件", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "ZIPには results.tsv と各録音WAVを入れます。英語復唱を無視するロジックは、このデータを確認してから別段階で設計します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { exporter.launch("mitsukotoba-repetition-phase0.zip") },
                        enabled = resultCount > 0 && !recording && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("測定データを書き出す") }
                    TextButton(
                        onClick = {
                            stopRecorderOnly()
                            RepetitionLabStore.reset(context, runtime.asrModel.modelName)
                            resultCount = 0
                            promptIndex = 0
                            statusMessage = "測定データをリセットしました。"
                        },
                        enabled = !busy,
                    ) { Text("新しい測定を始める") }
                }
            }

            if (!ready) {
                Text(
                    "この測定はLite専用です。設定でLiteを選び、Moonshineの準備を完了してください。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
