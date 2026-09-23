package com.eltnegcellist.emma

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eltnegcellist.emma.ai.ConversationEngineMode

@Composable
internal fun FirstRunOnboardingScreen(
    selectedMode: ConversationEngineMode,
    busy: Boolean,
    ready: Boolean,
    phase: String,
    progressPercent: Int?,
    errorMessage: String?,
    onModeSelected: (ConversationEngineMode) -> Unit,
    onPrepare: () -> Unit,
    onOpenAbout: () -> Unit,
    onStartEmma: () -> Unit,
) {
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
            Text(
                "はじめまして、Emmaです",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "親がいつもの日本語で話しかけると、Emmaがその場面を受け取り、赤ちゃんへやさしい英語で直接話しかけます。",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "単なる日本語→英語の翻訳ではありません。使い方に合わせてLite・Standard・Fullから選べます。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("なぜこの設計？ Emmaとは？")
            }

            Text(
                "Emmaを選ぶ",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            ConversationEngineMode.entries.forEach { option ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Emma ${option.label}", style = MaterialTheme.typography.titleMedium)
                        Text(option.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when (option) {
                                ConversationEngineMode.LITE ->
                                    "Moonshine Tiny + Kitten TTS Nano / 約64MB / 完全ローカル"
                                ConversationEngineMode.STANDARD ->
                                    "ReazonSpeech + Supertonic 3 / 約298MB / 完全ローカル"
                                ConversationEngineMode.FULL ->
                                    "Gemma + Supertonic 3 / 2GB超 / 完全ローカル"
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
                "赤ちゃんの設定（任意）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            ProductionFamilySettings(enabled = !busy)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("選択中：Emma ${selectedMode.label}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (selectedMode) {
                            ConversationEngineMode.LITE ->
                                "Web版Liteと同じMoonshine日本語TinyとKitten TTS Nano / KikiをAndroid上で動かします。"
                            ConversationEngineMode.STANDARD ->
                                "Android向けにReazonSpeechとSupertonic 3 F3を使います。通常はこちらがおすすめです。"
                            ConversationEngineMode.FULL ->
                                "Gemmaが応答を生成します。ダウンロード量が大きいためWi-Fiでの準備をおすすめします。"
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
                        phase.ifBlank { "Emma ${selectedMode.label}を準備しています…" },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "準備中です。完了するまでアプリを閉じず、そのままお待ちください。",
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
                                "Emma ${selectedMode.label}を始められます。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Button(
                        onClick = onStartEmma,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Emmaを始める")
                    }
                }

                else -> {
                    Button(
                        onClick = onPrepare,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (errorMessage == null) "Emma ${selectedMode.label}を準備する" else "もう一度準備する")
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
