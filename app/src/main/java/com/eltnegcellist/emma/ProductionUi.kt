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
import com.eltnegcellist.emma.ai.AudienceMode
import com.eltnegcellist.emma.ai.ConversationEngineMode
import com.eltnegcellist.emma.ai.EnglishLevel
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
    kokoroOnly: Boolean,
    busy: Boolean,
    autoRespond: Boolean,
    latestTranscript: String,
    latestEmmaText: String,
    engineMode: ConversationEngineMode,
    onParentRequested: () -> Unit,
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
                    if (recording && !autoRespond) {
                        OutlinedButton(
                            onClick = onManualRespond,
                            enabled = modelReady && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("今返事して") }
                    }
                    if (!recording) {
                        Button(
                            onClick = onStartSession,
                            enabled = !kokoroOnly && modelReady && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Emmaと話す") }
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
                    Text("Emma", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text("おうちの英語パートナー", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onOpenSettings) { Text("設定") }
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
                enabled = !busy,
                engineMode = engineMode,
                onParentRequested = onParentRequested,
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
                        Text("話し終わりを検出してEmmaが返します", style = MaterialTheme.typography.bodySmall)
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
    onParentRequested: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    var mode by remember(engineMode) {
        mutableStateOf(
            if (engineMode == ConversationEngineMode.LITE) {
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
                    if (option == mode) {
                        Button(
                            onClick = {},
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { Text(option.label) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (engineMode == ConversationEngineMode.LITE && option == AudienceMode.PARENT) {
                                    onParentRequested()
                                } else {
                                    mode = option
                                    preferences.edit().putString("audience_mode", option.name).apply()
                                }
                            },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { Text(option.label) }
                    }
                }
            }
            Text(
                if (engineMode == ConversationEngineMode.LITE) {
                    "「親へ」ではEmma Fullを使います。タップすると初回のみFullの準備をご案内します。"
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
private fun ConversationExchange(transcript: String, emmaText: String) {
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
                        Text("Emma", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
    rate: Float,
    enabled: Boolean,
    previewing: Boolean,
    modelReady: Boolean,
    modelPresent: Boolean,
    kokoroInstalled: Boolean,
    lastSpeechMillis: Long?,
    engineMode: ConversationEngineMode,
    onBack: () -> Unit,
    onEngineMode: (ConversationEngineMode) -> Unit,
    onLevel: (EnglishLevel) -> Unit,
    onRate: (Float) -> Unit,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onDownloadGemma: () -> Unit,
    onSelectGemma: () -> Unit,
    onDownloadLiteAsr: () -> Unit,
    onSelectLiteAsr: () -> Unit,
    onLoadGemma: () -> Unit,
    onDownloadKokoro: () -> Unit,
    onSelectKokoro: () -> Unit,
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← 戻る") }
                Column(Modifier.padding(start = 4.dp)) {
                    Text("設定", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("家族とEmmaの設定", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            ProductionFamilySettings(enabled = enabled)

            AppearanceSettings(enabled = enabled)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("動作モード", style = MaterialTheme.typography.titleMedium)
                    ConversationEngineMode.entries.forEach { option ->
                        if (engineMode == option) {
                            Button(
                                onClick = {},
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(option.label) }
                        } else {
                            OutlinedButton(
                                onClick = { onEngineMode(option) },
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(option.label) }
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
                    Text(
                        if (engineMode == ConversationEngineMode.LITE) "Emma" else "Emma Full",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (modelReady && kokoroInstalled) {
                        Text("準備完了", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            if (engineMode == ConversationEngineMode.LITE) {
                                "日常の育児場面に合わせて短い英語を選び、Kokoroの温かい声で話します。"
                            } else {
                                "会話・聞き取り・EmmaのKokoro音声はこの端末の中で処理されます。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            "初回だけ、会話と音声に必要なデータを準備します。",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        if (!modelReady) {
                            Text(
                                if (engineMode == ConversationEngineMode.LITE) "日本語聞き取り" else "会話AI",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                when {
                                    modelPresent -> "導入済み。読み込みが必要です。"
                                    engineMode == ConversationEngineMode.LITE -> "未導入です。標準Emmaの日本語聞き取りに必要なデータを準備します。"
                                    else -> "未導入です。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!modelPresent) {
                                    OutlinedButton(
                                        onClick = if (engineMode == ConversationEngineMode.LITE) onDownloadLiteAsr else onDownloadGemma,
                                        enabled = enabled,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("入手する") }
                                    Button(
                                        onClick = if (engineMode == ConversationEngineMode.LITE) onSelectLiteAsr else onSelectGemma,
                                        enabled = enabled,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("選択する") }
                                } else {
                                    Button(onClick = onLoadGemma, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (engineMode == ConversationEngineMode.LITE) "Emmaを準備" else "会話AIを準備")
                                    }
                                }
                            }
                        }

                        if (!kokoroInstalled) {
                            Text("Emmaの声（Kokoro）", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Emmaでは声の温かみを重視するため、Kokoroは必須です。Android標準TTSには切り替えません。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onDownloadKokoro, enabled = enabled, modifier = Modifier.weight(1f)) { Text("入手する") }
                                Button(onClick = onSelectKokoro, enabled = enabled, modifier = Modifier.weight(1f)) { Text("選択する") }
                            }
                        }
                    }
                }
            }

            Text(
                if (engineMode == ConversationEngineMode.LITE) {
                    "標準Emmaは日本語を端末内で聞き取り、日常の育児場面に合う短い返答を選んでKokoroで話します。"
                } else {
                    "Emma Fullは直前の会話も含めて理解し、その場で英語を生成します。処理は端末内で行います。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
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
            Text("Emmaの色", style = MaterialTheme.typography.titleSmall)
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
