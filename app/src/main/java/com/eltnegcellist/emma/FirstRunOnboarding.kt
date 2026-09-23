package com.eltnegcellist.emma

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun FirstRunOnboardingScreen(
    busy: Boolean,
    ready: Boolean,
    phase: String,
    progressPercent: Int?,
    errorMessage: String?,
    usingAndroidVoice: Boolean,
    onPrepareRecommended: () -> Unit,
    onUseAndroidVoice: () -> Unit,
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
                "単なる日本語→英語の翻訳ではありません。お風呂、ミルク、ねんね、遊びなど、今この瞬間に合う短くリズミカルな英語を返します。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("なぜこの設計？ Emmaとは？")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text("Emmaのしくみ", style = MaterialTheme.typography.titleMedium)
                    Text("① 親の日本語を聞き取る", style = MaterialTheme.typography.bodyMedium)
                    Text("② その場面に合う赤ちゃん向け英語を選ぶ", style = MaterialTheme.typography.bodyMedium)
                    Text("③ Emmaの温かい声で話す", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "準備後の聞き取り・返答選択・音声生成は端末内で行います。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    Text("初回だけ準備します", style = MaterialTheme.typography.titleMedium)
                    Text("日本語聞き取りデータ：約169MB", style = MaterialTheme.typography.bodyMedium)
                    Text("Emmaの音声データ：約129MB", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "合計約298MBです。Wi-Fiでの準備をおすすめします。日本語の聞き取りデータはEmmaに必要です。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Supertonic 3 F3の自然な声をおすすめします。使わない場合はAndroid標準の英語音声でも始められます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        phase.ifBlank { "Emmaを準備しています…" },
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
                                if (usingAndroidVoice) {
                                    "日本語の聞き取り準備が完了しました。音声はAndroid標準を使います。Supertonic 3は後から設定できます。"
                                } else {
                                    "準備が完了しました。Emmaを始められます。"
                                },
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
                        onClick = onPrepareRecommended,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (errorMessage == null) "Emmaを準備する" else "もう一度準備する")
                    }
                    OutlinedButton(
                        onClick = onUseAndroidVoice,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("標準音声で始める")
                    }
                    Text(
                        "標準音声を選んでも、日本語の聞き取りに必要なデータは自動で準備します。Supertonic 3だけを省略します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    errorMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
