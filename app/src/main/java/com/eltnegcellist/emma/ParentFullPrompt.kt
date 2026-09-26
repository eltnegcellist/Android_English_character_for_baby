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
        title = { Text("「会話」はFull版で使えます") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "みつことば Liteの「呼びかけ」は、親子の今の場面にAIが短い英語を差し込むことを優先しています。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "「会話」では、あなたの発話や直前の流れを踏まえ、AIも親・赤ちゃんとの3者のやり取りに継続して参加します。",
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
                Text("Fullで「会話」を使う")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("今は使わない")
            }
        },
    )
}
