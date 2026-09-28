package com.eltnegcellist.emma

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
    tutorialStep: Int?,
    tutorialIntroReady: Boolean,
    onTutorialNext: () -> Unit,
    onTutorialStartSession: () -> Unit,
    onTutorialFinish: () -> Unit,
) {
    var tutorialAvatarBounds by remember { mutableStateOf<Rect?>(null) }
    var tutorialActionBounds by remember { mutableStateOf<Rect?>(null) }
    var tutorialStatusBounds by remember { mutableStateOf<Rect?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(tonalElevation = 2.dp, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "みつことば",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = onOpenAbout) { Text("英語学習") }
                        TextButton(onClick = onOpenSettings) { Text("設定") }
                    }
                }
            }
        },
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { tutorialActionBounds = it.boundsInRoot() },
                        ) { Text("ここで返事して") }
                    }
                    if (!recording) {
                        Button(
                            onClick = if (tutorialStep == 1) onTutorialStartSession else onStartSession,
                            enabled = modelReady && !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { tutorialActionBounds = it.boundsInRoot() },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { tutorialAvatarBounds = it.boundsInRoot() },
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    aiName,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Column(
                modifier = Modifier.onGloballyPositioned { tutorialStatusBounds = it.boundsInRoot() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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

        if (tutorialStep != null) {
            EmmaCoachMarkOverlay(
                step = tutorialStep,
                aiName = aiName,
                introReady = tutorialIntroReady,
                targetBounds = when (tutorialStep) {
                    0 -> tutorialAvatarBounds
                    1 -> tutorialActionBounds
                    else -> tutorialStatusBounds
                },
                onNext = onTutorialNext,
                onFinish = onTutorialFinish,
            )
        }
    }
}

@Composable
private fun EmmaCoachMarkOverlay(
    step: Int,
    aiName: String,
    introReady: Boolean,
    targetBounds: Rect?,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "tutorial-spotlight")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tutorial-spotlight-pulse",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dim = Color.Black.copy(alpha = 0.68f)
            val pad = 10.dp.toPx()
            val target = targetBounds?.let {
                Rect(
                    left = (it.left - pad).coerceAtLeast(0f),
                    top = (it.top - pad).coerceAtLeast(0f),
                    right = (it.right + pad).coerceAtMost(size.width),
                    bottom = (it.bottom + pad).coerceAtMost(size.height),
                )
            }

            if (target == null) {
                drawRect(dim)
            } else {
                if (target.top > 0f) {
                    drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, target.top))
                }
                if (target.bottom < size.height) {
                    drawRect(
                        dim,
                        topLeft = Offset(0f, target.bottom),
                        size = Size(size.width, size.height - target.bottom),
                    )
                }
                if (target.left > 0f) {
                    drawRect(
                        dim,
                        topLeft = Offset(0f, target.top),
                        size = Size(target.left, target.height),
                    )
                }
                if (target.right < size.width) {
                    drawRect(
                        dim,
                        topLeft = Offset(target.right, target.top),
                        size = Size(size.width - target.right, target.height),
                    )
                }

                drawRoundRect(
                    color = Color(0xFFB67CFF).copy(alpha = 0.55f + pulse * 0.45f),
                    topLeft = Offset(target.left, target.top),
                    size = Size(target.width, target.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                    style = Stroke(width = (3.dp + 3.dp * pulse).toPx()),
                )
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    top = if (step == 0) 36.dp else 86.dp,
                ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    when (step) {
                        0 -> "1 / 3　${aiName}と会おう"
                        1 -> "2 / 3　ここから会話を始めます"
                        else -> "3 / 3　話しかけてみよう"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when (step) {
                        0 -> if (introReady) {
                            "${aiName}の自己紹介が終わりました。明るく表示されている顔が、赤ちゃんへ英語で話しかけます。"
                        } else {
                            "${aiName}が自己紹介しています。声が終わるまでそのまま聞いてください。"
                        }
                        1 -> "画面下で光っている「3人で話す」を実際に押してください。押すとマイクが始まり、会話を開始します。"
                        else -> "明るく表示されている「聞いています」を確認して、実際に赤ちゃんへ普段どおり日本語で話しかけてみてください。声を検知して$aiNameが返事を最後まで話し終えると、チュートリアルは自動で完了します。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onFinish) { Text("スキップ") }
                    if (step == 0) {
                        Button(onClick = onNext, enabled = introReady) { Text("次へ") }
                    } else if (step == 2) {
                        Text(
                            "話しかけてみてください…",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            "↓ 光っているボタンを押す",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
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
                    "みつことば Liteでは「呼びかけ」を使えます。AIも3者のやり取りへ継続参加する「会話」はFullで利用できます。"
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
    onOpenTutorial: () -> Unit,
    onResetAllData: () -> Unit,
) {
    var developerTapCount by remember { mutableStateOf(0) }
    var developerToolsVisible by remember { mutableStateOf(false) }
    var resetConfirmOpen by remember { mutableStateOf(false) }
    var familySettingsOpen by remember { mutableStateOf(false) }

    if (resetConfirmOpen) {
        AlertDialog(
            onDismissRequest = { resetConfirmOpen = false },
            title = { Text("アプリデータをすべて削除") },
            text = {
                Text(
                    "赤ちゃん・AIの設定、初回設定情報、Moonshine / Kitten / Gemmaのモデルを含む、みつことばの端末内データをすべて削除します。次回起動時は初期設定とモデル取得が必要です。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetConfirmOpen = false
                        onResetAllData()
                    },
                ) { Text("すべて削除") }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirmOpen = false }) { Text("キャンセル") }
            },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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
                TextButton(onClick = onOpenAbout) { Text("英語学習に使える理由") }
            }

            OutlinedButton(
                onClick = { familySettingsOpen = !familySettingsOpen },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (familySettingsOpen) "赤ちゃん・AIの名前設定を閉じる ▲" else "赤ちゃん・AIの名前を設定 ▼")
            }
            if (familySettingsOpen) {
                ProductionFamilySettings(
                    enabled = enabled,
                    aiName = aiName,
                    onAiNameChange = onAiNameChange,
                )
            }
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
                        if (option == ConversationEngineMode.LITE) {
                            Text("音声認識", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MoonshineAsrModel.entries.forEach { asrOption ->
                                    if (asrModel == asrOption) {
                                        Button(
                                            onClick = {},
                                            enabled = enabled,
                                            modifier = Modifier.weight(1f),
                                        ) { Text(asrOption.label) }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onAsrModel(asrOption) },
                                            enabled = enabled,
                                            modifier = Modifier.weight(1f),
                                        ) { Text(asrOption.label) }
                                    }
                                }
                            }
                            Text(
                                if (asrModel == MoonshineAsrModel.SMALL) {
                                    "Small推奨。Tinyは軽さを優先したい場合に選べます。"
                                } else {
                                    "Tiny（軽量）を使用中。Fullでも同じ音声認識設定を使います。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
            OutlinedButton(
                onClick = onOpenTutorial,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("チュートリアルを見る")
            }

            Text(
                "最初の使い方ガイドをもう一度表示します。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "隠し設定を開くには、下の「みつことば Android」を5回タップしてください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "みつことば Android",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        developerTapCount += 1
                        if (developerTapCount >= 5) {
                            developerTapCount = 0
                            developerToolsVisible = true
                        }
                    }
                    .padding(12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )

            if (developerToolsVisible) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("開発者設定", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "診断・音声試聴・完全リセットなど、通常利用では不要な項目です。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text("AI音声の試聴", style = MaterialTheme.typography.titleSmall)
                        if (kittenInstalled) {
                            OutlinedButton(
                                onClick = if (previewing) onStopPreview else onPreview,
                                enabled = enabled || previewing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (previewing) "試聴を停止" else "Kikiの声を試聴")
                            }
                        } else {
                            Text(
                                "Kitten TTSが未準備のため試聴できません。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        lastSpeechMillis?.let {
                            Text(
                                "直近の音声開始：${it}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        OutlinedButton(
                            onClick = { resetConfirmOpen = true },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("アプリデータをすべて削除")
                        }
                    }
                }
            }

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
