package com.eltnegcellist.emma

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eltnegcellist.emma.ai.AiCharacterName
import com.eltnegcellist.emma.ai.ConversationEngineMode

@Composable
internal fun FirstRunOnboardingScreen(
    selectedMode: ConversationEngineMode,
    busy: Boolean,
    ready: Boolean,
    phase: String,
    progressPercent: Int?,
    errorMessage: String?,
    aiName: String,
    onAiNameChange: (String) -> Unit,
    startWithTiny: Boolean,
    onStartWithTinyChange: (Boolean) -> Unit,
    onModeSelected: (ConversationEngineMode) -> Unit,
    onPrepare: () -> Unit,
    onOpenAbout: () -> Unit,
    onStartEmma: () -> Unit,
) {
    val resolvedAiName = AiCharacterName.resolve(aiName)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MitsukotobaBrandMark()
            Text(
                "いつもの日本語から、赤ちゃんへの英語が生まれる。",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                "親が話すと、AIがその場に合う短い英語を返します。翻訳ではなく、親・赤ちゃん・AIの3人で同じ時間を共有するためのアプリです。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InteractionStep(
                    modifier = Modifier.weight(1f),
                    imageRes = R.drawable.mitsukotoba_ai,
                    title = "親",
                    caption = "日本語で話す",
                )
                InteractionStep(
                    modifier = Modifier.weight(1f),
                    imageRes = R.drawable.mitsukotoba_parent,
                    title = "AI",
                    caption = "場面を理解",
                )
                InteractionStep(
                    modifier = Modifier.weight(1f),
                    imageRes = R.drawable.mitsukotoba_baby,
                    title = "赤ちゃん",
                    caption = "短い英語を聞く",
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "AIキャラクター",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "名前は $resolvedAiName。初回チュートリアルの中で声で自己紹介します。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "初期名はEmma。設定から好きな名前へ変更できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Button(
                onClick = onOpenAbout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text(
                        "研究から知る",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "みつことばが英語学習に使える理由 →",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                "Lite / Fullを選ぶ",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            ConversationEngineMode.entries.forEach { option ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("みつことば ${option.label}", style = MaterialTheme.typography.titleMedium)
                        Text(option.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when (option) {
                                ConversationEngineMode.LITE ->
                                    "Moonshine ${if (startWithTiny) "Tiny" else "Small"} + LiteResponseEngine + Kitten TTS Nano / 完全ローカル"
                                ConversationEngineMode.FULL ->
                                    "Moonshine ${if (startWithTiny) "Tiny" else "Small"} + Gemma + Kitten TTS Nano / 2GB超 / 完全ローカル"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (selectedMode == option) {
                            Button(
                                onClick = {},
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("選択中") }
                        } else {
                            OutlinedButton(
                                onClick = { onModeSelected(option) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("このモードを選ぶ") }
                        }
                    }
                }
            }

            Text(
                "家族とAIの設定（任意）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            ProductionFamilySettings(
                enabled = !busy,
                aiName = aiName,
                onAiNameChange = onAiNameChange,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        "軽量設定：Moonshine Tiny",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "必要ならTinyで開始（通常はSmall推奨）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = startWithTiny,
                    onCheckedChange = onStartWithTinyChange,
                    enabled = !busy,
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("選択中：みつことば ${selectedMode.label}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (selectedMode) {
                            ConversationEngineMode.LITE ->
                                "Moonshine日本語${if (startWithTiny) "Tiny" else "Small"}で聞き取り、LiteResponseEngineで返答を選び、Kitten TTS Nano / Kikiで話します。"
                            ConversationEngineMode.FULL ->
                                "Moonshine日本語${if (startWithTiny) "Tiny" else "Small"}で聞き取り、KittenはLiteと共通です。Gemmaが文字起こし・元音声・直前の会話から返答を生成します。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            when {
                busy -> {
                    CircularProgressIndicator()
                    progressPercent?.let {
                        Text("$it%", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        phase.ifBlank { "みつことば ${selectedMode.label}を準備しています…" },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "準備が終わると、そのまま会話を始められます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ready -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("準備できました", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "みつことば ${selectedMode.label}を始められます。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Button(
                        onClick = onStartEmma,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("3人で話しはじめる")
                    }
                }

                else -> {
                    Button(
                        onClick = onPrepare,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (errorMessage == null) "みつことば ${selectedMode.label}を準備する" else "もう一度準備する")
                    }
                    errorMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractionStep(
    modifier: Modifier,
    imageRes: Int,
    title: String,
    caption: String,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = title,
                modifier = Modifier.size(72.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
