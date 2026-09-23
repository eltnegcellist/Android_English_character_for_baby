package com.eltnegcellist.emma

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ParentFullPrompt(
    onUseFull: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("親と話すにはFull版") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Emma LiteとStandardは、赤ちゃんへ短く分かりやすい英語で話しかけることを優先しています。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "「親へ」では、あなたの話や直前の会話に合わせてAIがその場で英語を考えるFull版を使います。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Full版は初回のみ2GB超の追加AIデータが必要です。準備後の会話処理は端末内で行います。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onUseFull) {
                Text("Emma Fullを使う")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("今は使わない")
            }
        },
    )
}
