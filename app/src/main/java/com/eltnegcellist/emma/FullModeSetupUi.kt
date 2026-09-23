package com.eltnegcellist.emma

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
internal fun FullModeSetupScreen(
    busy: Boolean,
    phase: String,
    progressPercent: Int?,
    errorMessage: String?,
    gemmaNeeded: Boolean,
    supertonicNeeded: Boolean,
    onPrepare: () -> Unit,
    onCancel: () -> Unit,
    onManualSetup: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Emma Fullを準備",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "FullではGemma 4 E2Bが会話の内容を理解し、その場で英語を生成します。",
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(Modifier.height(20.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("初回のみ必要なデータを取得します", style = MaterialTheme.typography.titleMedium)
                    if (gemmaNeeded) {
                        Text(
                            "Gemmaの会話モデルは2GBを超えます（約2.6GB）。Wi-Fiでの準備をおすすめします。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            "Gemmaは導入済みです。Gemmaの大容量ダウンロードは行いません。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (supertonicNeeded) {
                        Text(
                            "Supertonic 3音声も未導入のため、約129MBの音声データもあわせて取得します。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "音声は現在の設定を使います。Supertonic 3の追加ダウンロードが不要な場合は、Gemmaだけを取得します。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "ダウンロード後の会話処理は端末内で行います。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            if (busy) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    phase.ifBlank { "Fullを準備しています…" },
                    style = MaterialTheme.typography.titleSmall,
                )
                progressPercent?.let {
                    Text(
                        "$it%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "ダウンロード中はこの画面を開いたままにしてください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Button(
                    onClick = onPrepare,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (errorMessage != null) {
                            "もう一度ダウンロードする"
                        } else if (gemmaNeeded) {
                            "2GB超をダウンロードしてFullを準備"
                        } else {
                            "必要なデータを取得してFullを準備"
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("今はFullにしない")
                }
                errorMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onManualSetup,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("手動で設定する")
                    }
                }
            }
        }
    }
}
