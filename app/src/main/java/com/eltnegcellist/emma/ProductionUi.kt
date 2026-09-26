package com.eltnegcellist.emma

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.eltnegcellist.emma.ai.AudienceMode
import com.eltnegcellist.emma.ai.ConversationEngineMode
import com.eltnegcellist.emma.ai.EnglishLevel
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import com.eltnegcellist.emma.ui.CompactEmmaAvatar
import com.eltnegcellist.emma.ui.EmmaColorMode
import com.eltnegcellist.emma.ui.EmmaVividPalette
import com.eltnegcellist.emma.ui.EmmaVisualState

@Composable
internal fun EmmaHomeScreen(
    visualState: EmmaVisualState,
    mouthLevel: Float,
    statusTitle: String,
    statusMessage: String,
    voiceError: String?,
    showBusyIndicator: Boolean,
    recording: Boolean,
    modelReady: Boolean,
    busy: Boolean,
    autoRespond: Boolean,
    latestTranscript: String,
    latestEmmaText: String,
    aiName: String,
    engineMode: ConversationEngineMode,
    onRequestParentFull: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onToggleAutoRespond: (Boolean) -> Unit,
    onManualRespond: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(tonalElevation = 2.dp, shadowElevation = 6.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (recording) {
                        OutlinedButton(
                            onClick = onManualRespond,
                            enabled = modelReady && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("ここで返事して") }
                    }
                    if (!recording) {
                        Button(
                            onClick = onStartSession,
                            enabled = modelReady && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("3人で話す") }
                    } else {
                        OutlinedButton(
                            onClick = onStopSession,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("セッションを終了") }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("みつことば", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text("親と赤ちゃんとAI、3人でつくる英語の時間", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onOpenAbout) { Text("みつことばとは？") }
                    TextButton(onClick = onOpenSettings) { Text("設定") }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("端末内で処理", style = MaterialTheme.typography.labelLarge)
                        Text("会話は外部へ送信しません", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("●", color = MaterialTheme.colorScheme.primary)
                }
            }

            AudienceSelector(
                enabled = !busy && !recording,
                engineMode = engineMode,
                onRequestParentFull = onRequestParentFull,
            )

            CompactEmmaAvatar(
                state = visualState,
                mouthLevel = mouthLevel,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = statusColor(visualState),
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            statusTitle,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = statusTextColor(visualState),
                        )
                    }
                    if (showBusyIndicator) CircularProgressIndicator(strokeWidth = 2.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text(statusMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                voiceError?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (latestEmmaText.isNotBlank() || latestTranscript.isNotBlank()) {
                ConversationExchange(
                    transcript = latestTranscript,
                    emmaText = latestEmmaText,
                    aiName = aiName,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自動で返事", style = MaterialTheme.typography.titleSmall)
                        Text("話し終わりを検出してAIが返します", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = autoRespond, onCheckedChange = onToggleAutoRespond, enabled = visualState != EmmaVisualState.SPEAKING)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AudienceSelector(
    enabled: Boolean,
    engineMode: ConversationEngineMode,
    onRequestParentFull: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    var mode by remember(engineMode) {
        mutableStateOf(
            if (engineMode != ConversationEngineMode.FULL) {
                AudienceMode.BABY
            } else {
                AudienceMode.fromSaved(preferences.getString("audience_mode", null))
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudienceMode.entries.forEach { option ->
                    val selected = option == mode
                    val onClick = {
                        if (engineMode != ConversationEngineMode.FULL && option == AudienceMode.PARENT) {
                            onRequestParentFull()
                        } else {
                            mode = option
                            preferences.edit().putString("audience_mode", option.name).apply()
                        }
                    }
                    if (selected) {
                        Button(
                            onClick = onClick,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { Text(option.label) }
                    } else {
                        OutlinedButton(
                            onClick = onClick,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { Text(option.label) }
                    }
                }
            }
            Text(
                if (engineMode != ConversationEngineMode.FULL) {
                    "みつことば ${engineMode.label}は「赤ちゃんへ」専用です。親との自由会話や、より柔軟な応答はFullで利用できます。"
                } else {
                    mode.description
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConversationExchange(transcript: String, emmaText: String, aiName: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (transcript.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(0.88f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("あなた", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(transcript, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (emmaText.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(0.88f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(aiName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(emmaText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmmaSettingsScreen(
    level: EnglishLevel,
    enabled: Boolean,
    previewing: Boolean,
    modelReady: Boolean,
    modelPresent: Boolean,
    asrInstalled: Boolean,
    kittenInstalled: Boolean,
    gemmaInstalled: Boolean,
    lastSpeechMillis: Long?,
    engineMode: ConversationEngineMode,
    asrModel: MoonshineAsrModel,
    keepScreenOn: Boolean,
    aiName: String,
    onAiNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onEngineMode: (ConversationEngineMode) -> Unit,
    onAsrModel: (MoonshineAsrModel) -> Unit,
    onLevel: (EnglishLevel) -> Unit,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onDownloadGemma: () -> Unit,
    onSelectGemma: () -> Unit,
    onPrepareLite: () -> Unit,
    onLoadGemma: () -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onExportDiagnostics: () -> Unit,
    onExportCrashDetails: () -> Unit,
) {
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("← 戻る") }
                    Column(Modifier.padding(start = 4.dp)) {
                        Text("設定", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text("家族とみつことばの設定", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onOpenAbout) { Text("みつことばとは？") }
            }

            ProductionFamilySettings(
                enabled = enabled,
                aiName = aiName,
                onAiNameChange = onAiNameChange,
            )
            AppearanceSettings(enabled = enabled)

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Android版の動作", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "みつことば利用中は画面をスリープさせない",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = onKeepScreenOn,
                        enabled = enabled,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Lite / Full", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Lite / Full から選べます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ConversationEngineMode.entries.forEach { option ->
                        if (engineMode == option) {
                            Button(
                                onClick = {},
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("みつことば ${option.label}（選択中）") }
                        } else {
                            OutlinedButton(
                                onClick = { onEngineMode(option) },
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("みつことば ${option.label}に切り替える") }
                        }
                        Text(
                            option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("音声認識", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Moonshine Tiny / Small を選べます。Liteの初期値はTiny、Fullの初期値はSmallです。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoonshineAsrModel.entries.forEach { option ->
                        if (asrModel == option) {
                            Button(
                                onClick = {},
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("${option.label}（選択中）") }
                        } else {
                            OutlinedButton(
                                onClick = { onAsrModel(option) },
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("${option.label}に切り替える") }
                        }
                    }
                    Text(
                        if (asrModel == MoonshineAsrModel.SMALL) {
                            "Smallは追加データが必要です。Tinyより高精度な場合がありますが、常に正確とは限りません。"
                        } else {
                            "Tinyは軽量で、みつことばをすばやく始める標準設定です。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("みつことば ${engineMode.label}", style = MaterialTheme.typography.titleMedium)

                    val editionReady = modelReady && kittenInstalled

                    if (editionReady) {
                        Text("準備完了", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            when (engineMode) {
                                ConversationEngineMode.LITE ->
                                    "Moonshine 日本語${asrModel.shortLabel}で聞き取り、LiteResponseEngineで返答を選び、Kitten TTS Nano / Kikiで話します。"
                                ConversationEngineMode.FULL ->
                                    "Moonshine 日本語${asrModel.shortLabel}の文字起こしと元音声をGemmaが会話履歴と一緒に受け取り、Kitten TTS Nano / Kikiで話します。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text("初回だけ、このエディションに必要なデータを準備します。", style = MaterialTheme.typography.bodyMedium)

                        when (engineMode) {
                            ConversationEngineMode.LITE -> {
                                Text(
                                    if (asrModel == MoonshineAsrModel.TINY) {
                                        "Moonshine 日本語Tiny 約32MB + Kitten TTS Nano 約31MB。合計約64MBです。"
                                    } else {
                                        "Moonshine 日本語Smallの追加データ + Kitten TTS Nanoを準備します。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Button(
                                    onClick = onPrepareLite,
                                    enabled = enabled,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("みつことば Liteを準備") }
                            }

                            ConversationEngineMode.FULL -> {
                                if (!gemmaInstalled) {
                                    Text("Gemmaは2GB超の追加データが必要です。", style = MaterialTheme.typography.bodySmall)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = onDownloadGemma,
                                            enabled = enabled,
                                            modifier = Modifier.weight(1f),
                                        ) { Text("Gemmaを入手") }
                                        Button(
                                            onClick = onSelectGemma,
                                            enabled = enabled,
                                            modifier = Modifier.weight(1f),
                                        ) { Text("Gemmaを選択") }
                                    }
                                }
                                if (!asrInstalled || !kittenInstalled) {
                                    Button(
                                        onClick = onPrepareLite,
                                        enabled = enabled,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Moonshine ${asrModel.shortLabel} + Kittenを準備") }
                                }
                                if (gemmaInstalled && asrInstalled && kittenInstalled) {
                                    Button(
                                        onClick = onLoadGemma,
                                        enabled = enabled,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("みつことば Fullを起動") }
                                }
                            }
                        }
                    }

                    Text("AIの声：Kitten TTS Nano / Kiki", style = MaterialTheme.typography.titleSmall)
                    if (kittenInstalled) {
                        OutlinedButton(
                            onClick = if (previewing) onStopPreview else onPreview,
                            enabled = enabled || previewing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (previewing) "試聴を停止" else "Kikiの声を試聴")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onPrepareLite,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Moonshine + Kittenを準備") }
                    }

                    lastSpeechMillis?.let {
                        Text(
                            "直近の音声開始：${it}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                when (engineMode) {
                    ConversationEngineMode.LITE ->
                        "LiteはMoonshine + LiteResponseEngine + Kitten / Kikiの軽量構成です。"
                    ConversationEngineMode.FULL ->
                        "Fullは同じMoonshineとKittenを使い、Gemmaだけを追加します。文字内容はMoonshineを優先し、元音声は非言語情報の補助として使います。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun LiteModelInstallDialog(
    phase: String,
    progressPercent: Int?,
) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = {
            Text("みつことばを準備しています")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                progressPercent?.let {
                    Text("$it%", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    phase.ifBlank { "日本語聞き取りモデルを準備しています…" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "インストール中のためお待ちください。完了するまでアプリを閉じたり、他の操作をしたりしないでください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun AppearanceSettings(enabled: Boolean) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    var colorMode by remember { mutableStateOf(EmmaColorMode.fromSaved(preferences.getString("emma_color_mode", null))) }
    var vividPalette by remember { mutableStateOf(EmmaVividPalette.fromSaved(preferences.getString("emma_vivid_palette", null))) }
    var colorExpanded by remember { mutableStateOf(false) }
    var vividExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("見た目", style = MaterialTheme.typography.titleMedium)
            Text("キャラクターの色", style = MaterialTheme.typography.titleSmall)
            Box {
                OutlinedButton(onClick = { colorExpanded = true }, enabled = enabled) { Text(colorMode.label) }
                DropdownMenu(expanded = colorExpanded, onDismissRequest = { colorExpanded = false }) {
                    EmmaColorMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                colorMode = option
                                preferences.edit().putString("emma_color_mode", option.savedValue).apply()
                                colorExpanded = false
                            },
                        )
                    }
                }
            }
            if (colorMode == EmmaColorMode.VIVID) {
                Box {
                    OutlinedButton(onClick = { vividExpanded = true }, enabled = enabled) { Text(vividPalette.label) }
                    DropdownMenu(expanded = vividExpanded, onDismissRequest = { vividExpanded = false }) {
                        EmmaVividPalette.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    vividPalette = option
                                    preferences.edit().putString("emma_vivid_palette", option.savedValue).apply()
                                    vividExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            Text(colorMode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun statusColor(state: EmmaVisualState): Color = when (state) {
    EmmaVisualState.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    EmmaVisualState.SPEAKING -> MaterialTheme.colorScheme.secondaryContainer
    EmmaVisualState.THINKING, EmmaVisualState.UNDERSTOOD -> MaterialTheme.colorScheme.primaryContainer
    EmmaVisualState.LISTENING, EmmaVisualState.ENDPOINT_WAIT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    EmmaVisualState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun statusTextColor(state: EmmaVisualState): Color = when (state) {
    EmmaVisualState.ERROR -> MaterialTheme.colorScheme.error
    EmmaVisualState.SPEAKING -> MaterialTheme.colorScheme.onSecondaryContainer
    EmmaVisualState.THINKING, EmmaVisualState.UNDERSTOOD -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
