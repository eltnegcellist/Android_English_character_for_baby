package com.eltnegcellist.emma

import com.eltnegcellist.emma.audio.EndpointMode
import androidx.activity.compose.BackHandler
import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.eltnegcellist.emma.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.eltnegcellist.emma.ai.AiCharacterName
import com.eltnegcellist.emma.ai.EnglishLevel
import com.eltnegcellist.emma.tts.KittenSpeaker
import com.eltnegcellist.emma.asr.MoonshineAsrModel
import com.eltnegcellist.emma.ui.CompactEmmaAvatar
import com.eltnegcellist.emma.ui.EmmaColorMode
import com.eltnegcellist.emma.ui.migrateLegacyEmmaColors
import com.eltnegcellist.emma.ui.EmmaSoftPalette
import com.eltnegcellist.emma.ui.EmmaVividPalette
import com.eltnegcellist.emma.ui.EmmaVisualState

internal fun tutorialStartButtonEnabled(
    modelReady: Boolean,
    busy: Boolean,
    tutorialStep: Int?,
): Boolean = modelReady && (!busy || tutorialStep == 1)

internal fun tutorialHighlightsAction(tutorialStep: Int?): Boolean =
    tutorialStep != null && tutorialStep != 0

internal fun tutorialStepAfterIntroduction(tutorialStep: Int?): Int? =
    if (tutorialStep == 0) 1 else tutorialStep

@Composable
internal fun EmmaHomeScreen(
    languagePicker: @Composable () -> Unit = {},
    visualState: EmmaVisualState,
    mouthLevel: Float,
    statusTitle: String,
    statusMessage: String,
    voiceError: String?,
    showBusyIndicator: Boolean,
    recording: Boolean,
    modelReady: Boolean,
    busy: Boolean,
    latestTranscript: String,
    latestEmmaText: String,
    latestTopic: String,
    aiName: String,
    backgroundPreparationActive: Boolean,
    backgroundPreparationPhase: String,
    backgroundPreparationPercent: Int?,
    onOpenSettings: () -> Unit,
    onOpenPlay: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onManualRespond: () -> Unit,
    tutorialStep: Int?,
    tutorialIntroReady: Boolean,
    onTutorialNext: () -> Unit,
    onTutorialStartSession: () -> Unit,
    onTutorialFinish: () -> Unit,
) {
    var tutorialAvatarBounds by remember { mutableStateOf<Rect?>(null) }
    var tutorialActionBounds by remember { mutableStateOf<Rect?>(null) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val homeScrollState = rememberScrollState()

    // Show the actual tutorial voice line even before the first conversation.
    // Retain the most recently spoken reply while the next ASR turn is pending.
    var lastVisibleEnglish by remember { mutableStateOf(AiCharacterName.introduction(aiName)) }
    var lastVisibleRoute by remember { mutableStateOf("intro") }
    LaunchedEffect(latestEmmaText, latestTopic) {
        if (latestEmmaText.isNotBlank()) {
            lastVisibleEnglish = latestEmmaText
            lastVisibleRoute = latestTopic
        }
    }
    val displayEnglish = latestEmmaText.ifBlank { lastVisibleEnglish }
    val displayRoute = if (latestEmmaText.isNotBlank()) latestTopic else lastVisibleRoute

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.boundsInRoot().topLeft }) {
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
                        AppLanguage.productName,
                        style = if(AppLanguage.language=="ja") MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        languagePicker()
                        TextButton(onClick = onOpenPlay, enabled = modelReady && tutorialStep == null,
                            modifier=Modifier.semantics { contentDescription=AppLanguage.translate("押して聞く") }) {
                            Text(if(AppLanguage.language=="ja") "☝ 押して聞く" else "☝", style = MaterialTheme.typography.labelLarge)
                        }
                        TextButton(onClick = onOpenSettings,modifier=Modifier.semantics { contentDescription=AppLanguage.translate("設定") }) { Text(if(AppLanguage.language=="ja") "設定" else "⚙") }
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp, shadowElevation = 6.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!recording) {
                        // A single large invitation when Emma is asleep.
                        Button(
                            onClick = if (tutorialStep == 1) onTutorialStartSession else onStartSession,
                            enabled = tutorialStartButtonEnabled(modelReady, busy, tutorialStep),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 96.dp)
                                .onGloballyPositioned { tutorialActionBounds = it.boundsInRoot() },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("呼びかけを始める",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "ここを押してEmmaとおしゃべり",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    } else {
                        // In a running conversation, responding matters more than stopping.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = onManualRespond,
                                enabled = modelReady && !busy,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 66.dp)
                                    .onGloballyPositioned { tutorialActionBounds = it.boundsInRoot() },
                            ) {
                                Text(
                                    "今すぐAIが返事する",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            OutlinedButton(
                                onClick = onStopSession,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 9.dp),
                                modifier = Modifier.weight(0.35f).heightIn(min = 60.dp),
                            ) {
                                Text("呼びかけを止める",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(homeScrollState)
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (backgroundPreparationActive) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "モデルをバックグラウンドで準備中",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            backgroundPreparationPhase.ifBlank { "ダウンロードを続けています…" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            backgroundPreparationPercent?.let { "$it%" } ?: "進捗を確認中…",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "この間も、準備済みのLite機能はそのまま使えます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                CompactEmmaAvatar(
                    state = visualState,
                    mouthLevel = mouthLevel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { tutorialAvatarBounds = it.boundsInRoot() },
                )
                if (displayEnglish.isNotBlank()) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        color = Color(0xFFFFF6FF),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color(0xFFDDBCE9)),
                        shadowElevation = 3.dp,
                    ) {
                        Text(
                            ReplyDisplayHints.fromScene(displayRoute).topicLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF7945A1),
                        )
                    }
                }
            }
            // The status replaces the old always-visible Emma name badge.
            // Preserve voice and model state, but don't repeat spoken English.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = statusColor(visualState),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            statusTitle,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = statusTextColor(visualState),
                        )
                    }
                    if (showBusyIndicator) CircularProgressIndicator(strokeWidth = 2.dp)
                }
                voiceError?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            ConversationExchange(
                emmaText = displayEnglish,
                route = displayRoute,
            )

            Spacer(Modifier.height(8.dp))
        }
        }

        if (tutorialStep != null) {
            EmmaCoachMarkOverlay(
                step = tutorialStep,
                aiName = aiName,
                introReady = tutorialIntroReady,
                targetBounds = (
                    if (tutorialHighlightsAction(tutorialStep)) tutorialActionBounds else tutorialAvatarBounds
                )?.translate(-overlayOrigin),
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
            val target = targetBounds?.takeIf { it.width > 0f && it.height > 0f && it.top < size.height && it.bottom > 0f }?.let {
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
                        1 -> "2 / 3　ここから呼びかけを始めます"
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
                            "${aiName}が自己紹介しています。声が終わると自動で次のステップへ進みます。"
                        }
                        1 -> "画面下で光っている「呼びかけを始める」を押してください。マイクが始まり、親子の場面に合わせた英語の呼びかけを開始します。"
                        else -> "赤ちゃんへ普段どおり日本語で話しかけてみてください。話し終わったら、必要に応じて明るく表示されている「今すぐAIが返事する」を押すと、その時点までの言葉をもとに${aiName}が返事します。「自動で返事」がオンの場合は、押さなくても話し終わりを検出して自動で返事します。"
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
                            "↓ 必要なら「今すぐAIが返事する」を押す",
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
private fun ConversationExchange(emmaText: String, route: String) {
    val hint = ReplyDisplayHints.fromScene(route)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFFE1C8EA)),
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFFFEFD), Color(0xFFFFF7FC), Color(0xFFF8F2FF))
                    )
                )
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // All three English sentences stay together. Emma remains the visual focus.
            Text(
                emmaText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF44304F),
            )
            if(AppLanguage.language=="ja") Text(
                hint.japaneseNote,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF826D91),
            )
        }
    }
}

@Composable
internal fun EmmaSettingsScreen(
    languagePicker: @Composable () -> Unit = {},
    endpointMode: EndpointMode,
    onEndpointMode: (EndpointMode) -> Unit,
    autoRespond: Boolean,
    onToggleAutoRespond: (Boolean) -> Unit,
    featureSettings: @Composable () -> Unit,
    level: EnglishLevel,
    enabled: Boolean,
    previewing: Boolean,
    modelReady: Boolean,
    modelPresent: Boolean,
    asrInstalled: Boolean,
    kittenInstalled: Boolean,
    lastSpeechMillis: Long?,
    lastTtsGenerationMillis: Long?,
    lastTtsTotalMillis: Long?,
    lastEndpointToTtsRequestMillis: Long?,
    lastEndpointToFirstAudioMillis: Long?,
    asrModel: MoonshineAsrModel,
    semanticEnabled: Boolean,
    semanticInstalled: Boolean,
    semanticDebugBusy: Boolean,
    semanticDebugResult: String?,
    semanticDebugEnglish: String?,
    keepScreenOn: Boolean,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onAsrModel: (MoonshineAsrModel) -> Unit,
    onSemanticEnabled: (Boolean) -> Unit,
    onRunSemanticDiagnostic: (String) -> Unit,
    onSpeakSemanticDiagnostic: (String) -> Unit,
    onResetSemanticDiagnosticContext: () -> Unit,
    onLevel: (EnglishLevel) -> Unit,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onPrepareLite: () -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onExportDiagnostics: () -> Unit,
    onExportCrashDetails: () -> Unit,
    onOpenTutorial: () -> Unit,
    onResetAllData: () -> Unit,
) {
    var topicGuideOpen by remember { mutableStateOf(false) }
    var developerTapCount by remember { mutableStateOf(0) }
    var developerToolsVisible by remember { mutableStateOf(false) }
    var resetConfirmOpen by remember { mutableStateOf(false) }
    var semanticDiagnosticInput by remember { mutableStateOf("お風呂入ろうね") }

    BackHandler {
        when {
            resetConfirmOpen -> resetConfirmOpen = false
            topicGuideOpen -> topicGuideOpen = false
            else -> onBack()
        }
    }

    if (topicGuideOpen) {
        LiteTopicGuideDialog(onDismiss = { topicGuideOpen = false })
    }

    if (resetConfirmOpen) {
        AlertDialog(
            onDismissRequest = { resetConfirmOpen = false },
            title = { Text("アプリデータをすべて削除") },
            text = {
                Text(
                    "赤ちゃん・AIの設定、初回設定情報、Moonshine / Ruri / 収録音声を含む、みつことばの端末内データをすべて削除します。次回起動時は初期設定とモデル取得が必要です。"
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

            languagePicker()
            AppearanceSettings(enabled = enabled)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("呼びかけの設定", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("自動で返事", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "話し終わりを検出してEmmaが返事をします",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = autoRespond,
                            onCheckedChange = onToggleAutoRespond,
                            enabled = enabled,
                        )
                    }
                    Text(
                        "オフの場合は、会話画面の「今すぐAIが返事する」を使えます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("画面表示", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "みつことばを使っている間の画面の消灯を設定します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("画面をスリープさせない", Modifier.weight(1f))
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = onKeepScreenOn,
                            enabled = enabled,
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("話し終わりの判定", style = MaterialTheme.typography.titleMedium)
                    EndpointMode.entries.forEach { option ->
                        if (endpointMode == option) {
                            Button(onClick = {}, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(option.label) }
                        } else {
                            OutlinedButton(onClick = { onEndpointMode(option) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(option.label) }
                        }
                    }
                    Text("声で判定すると、背景音がある場所でも話の区切りを捉えやすくなります。動作が気になる場合は従来方式へ戻せます。会話を止めてから変更してください。", style = MaterialTheme.typography.bodySmall)
                }
            }

            featureSettings()

            if (AppLanguage.language == "ja") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("音声認識", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "日本語の聞き取り設定です。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                            "Tiny（軽量）を使用中。精度を優先したい場合はSmallを選べます。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("返答と話題", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("意味で話題を判定する（標準）")
                            Text(
                                if (semanticEnabled) {
                                    if (semanticInstalled) {
                                        "Ruri Semanticで日本語の意味から話題を選びます。"
                                    } else {
                                        "Ruri Semanticを有効にしています。初回のみモデル準備が必要です。"
                                    }
                                } else {
                                    "軽量な従来Lite判定を使います。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = semanticEnabled,
                            onCheckedChange = onSemanticEnabled,
                            enabled = enabled,
                        )
                    }
                    Text(
                        "話題が曖昧な場合は直前の話題を最大6回使い、明確な別の話題で切り替えます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { topicGuideOpen = true }) {
                        Text("話題の判定方法・話題一覧を見る")
                    }
                }
            }

            } else {
                Text("Whisper Small · E5", style = MaterialTheme.typography.titleMedium)
                Text("この端末で音声を処理します。モデルは必要なときだけ取得し、保存済みのものは再利用します。")
            }

            // Model repair for ordinary users is always Lite-only.
            if (!modelReady || !kittenInstalled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("音声モデルの再準備", style = MaterialTheme.typography.titleMedium)
                        Text("必要なモデルが不足している場合は、ここから準備できます。", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onPrepareLite, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                            Text("音声モデルを準備")
                        }
                    }
                }
            }

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
                "${AppLanguage.productName} Android",
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

                        Text("モデル・実行状態", style = MaterialTheme.typography.titleSmall)
                        Text(
                            buildString {
                                append("ASR: Moonshine ").append(asrModel.shortLabel)
                                append(if (asrInstalled) " / 準備済み" else " / 未準備")
                                append("\nSemantic: ")
                                append(if (semanticInstalled) "Ruri 70M INT8 / 準備済み" else "Ruri 70M INT8 / 未準備")
                                append("\n音声: ").append(if (kittenInstalled) "準備済み" else "未準備")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text("Semantic 診断・比較", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "同じ日本語を従来Lite・Ruri Semantic・Guard（既存ルール優先）で判定し、話題・分類スコア・英語を比較します。診断用の話題履歴は通常会話とは分離されています。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = semanticDiagnosticInput,
                            onValueChange = { semanticDiagnosticInput = it.take(200) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled && !semanticDebugBusy,
                            label = { Text("日本語テキスト") },
                            placeholder = { Text("例：お風呂入ろうね") },
                            minLines = 2,
                            maxLines = 4,
                        )
                        OutlinedButton(
                            onClick = { onRunSemanticDiagnostic(semanticDiagnosticInput) },
                            enabled = enabled && semanticInstalled && !semanticDebugBusy && semanticDiagnosticInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (semanticDebugBusy) "判定中…" else "Lite / Semantic / Guardを比較")
                        }
                        TextButton(
                            onClick = onResetSemanticDiagnosticContext,
                            enabled = enabled && !semanticDebugBusy,
                        ) {
                            Text("診断用の直前話題をクリア")
                        }
                        if (!semanticInstalled) {
                            Text(
                                "Ruri Semanticが未準備です。通常設定でSemanticを有効にすると準備できます。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        semanticDebugResult?.let { result ->
                            Text(
                                result,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!semanticDebugEnglish.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { onSpeakSemanticDiagnostic(semanticDebugEnglish) },
                                enabled = enabled && !semanticDebugBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Semantic英語を採用音声で試聴")
                            }
                        }

                        Text("AI音声の試聴・診断", style = MaterialTheme.typography.titleSmall)
                        if (kittenInstalled) {
                            OutlinedButton(
                                onClick = if (previewing) onStopPreview else onPreview,
                                enabled = enabled || previewing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (previewing) "試聴を停止" else "採用した声を試聴")
                            }
                        } else {
                            Text(
                                "音声が未準備のため試聴できません。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            "固定パラメータ",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            "Voice: 採用音声（参照元seed1202）\n" +
                                "Generation: Qwen3-TTS 1.7B Base 8bit / seed1202固定\n" +
                                "Playback: 事前生成したAAC / 24,000Hz\n" +
                                "Listening loudness: -20 LUFS\n" +
                                "登録外の自由文: Kitten / Kiki（モデル準備時）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            "直近の実測",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            buildString {
                                append("音声準備: ")
                                append(lastTtsGenerationMillis?.let { "${it}ms" } ?: "未計測")
                                append("\n音声要求→初音: ")
                                append(lastSpeechMillis?.let { "${it}ms" } ?: "未計測")
                                append("\nTTS全体: ")
                                append(lastTtsTotalMillis?.let { "${it}ms" } ?: "未計測")
                                append("\n発話終了→TTS要求: ")
                                append(lastEndpointToTtsRequestMillis?.let { "${it}ms" } ?: "試聴では未計測")
                                append("\n発話終了→初音: ")
                                append(lastEndpointToFirstAudioMillis?.let { "${it}ms" } ?: "試聴では未計測")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            "試聴では音声準備・初音・TTS全体を確認できます。発話終了からの2項目は、通常会話を1回行うと更新されます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text("診断情報の書き出し", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(
                            onClick = onExportDiagnostics,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("診断ログをTXTで保存")
                        }
                        OutlinedButton(
                            onClick = onExportCrashDetails,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("クラッシュ詳細・音声診断をZIPで保存")
                        }
                        Text(
                            "ZIPには利用可能なクラッシュトレースや診断用に保存された音声が含まれる場合があります。共有前に内容を確認してください。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

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
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("画面を閉じる")
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
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
                    "他の画面に移動したり、アプリを閉じても準備は続きます。",
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
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE).also(::migrateLegacyEmmaColors) }
    var colorMode by remember { mutableStateOf(EmmaColorMode.fromSaved(preferences.getString("emma_color_mode", null))) }
    var filledPalette by remember {
        mutableStateOf(EmmaVividPalette.fromSaved(preferences.getString("emma_filled_palette", null)))
    }
    var softPalette by remember { mutableStateOf(EmmaSoftPalette.fromSaved(preferences.getString("emma_soft_palette", null))) }
    var softExpanded by remember { mutableStateOf(false) }
    var vividPalette by remember { mutableStateOf(EmmaVividPalette.fromSaved(preferences.getString("emma_vivid_palette", null))) }
    var colorExpanded by remember { mutableStateOf(false) }
    var vividExpanded by remember { mutableStateOf(false) }
    var filledExpanded by remember { mutableStateOf(false) }

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
            if (colorMode == EmmaColorMode.SOFT) {
                Text("やさしい色の配色", style = MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(onClick = { softExpanded = true }, enabled = enabled) { Text(softPalette.label) }
                    DropdownMenu(expanded = softExpanded, onDismissRequest = { softExpanded = false }) {
                        EmmaSoftPalette.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                softPalette = option
                                preferences.edit().putString("emma_soft_palette", option.savedValue).apply()
                                softExpanded = false
                            })
                        }
                    }
                }
            }
            if (colorMode == EmmaColorMode.VIVID) {
                Text("はっきり色の配色", style = MaterialTheme.typography.titleSmall)
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
            if (colorMode == EmmaColorMode.FILLED) {
                Text("塗りつぶしの配色", style = MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(onClick = { filledExpanded = true }, enabled = enabled) { Text(filledPalette.label) }
                    DropdownMenu(expanded = filledExpanded, onDismissRequest = { filledExpanded = false }) {
                        EmmaVividPalette.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    filledPalette = option
                                    preferences.edit().putString("emma_filled_palette", option.savedValue).apply()
                                    filledExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            Text(colorMode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(colorMode.gradientDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
