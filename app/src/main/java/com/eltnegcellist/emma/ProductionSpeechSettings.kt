package com.eltnegcellist.emma

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eltnegcellist.emma.ai.EnglishLevel
import java.util.Locale

@Composable
internal fun ProductionSpeechSettings(
    level: EnglishLevel,
    rate: Float,
    enabled: Boolean,
    previewing: Boolean,
    onLevel: (EnglishLevel) -> Unit,
    onRate: (Float) -> Unit,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("会話と声", style = MaterialTheme.typography.titleMedium)

            Text("英語のむずかしさ", style = MaterialTheme.typography.titleSmall)
            EnglishLevel.entries.forEach { option ->
                if (level == option) {
                    Button(onClick = {}, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text(option.label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onLevel(option) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(option.label)
                    }
                }
                Text(option.example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("親へ話すときの速さ：${String.format(Locale.US, "%.2f", rate)}×", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = rate,
                onValueChange = onRate,
                valueRange = 0.70f..1.10f,
                steps = 7,
                enabled = enabled,
            )
            Text(
                "赤ちゃんへ話すときは自然な発音を保つ設定を使います。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = if (previewing) onStopPreview else onPreview,
                    enabled = previewing || enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (previewing) "試聴を停止" else "みつことばの声を試す")
                }
            }
            Text("会話中は設定を変更できません。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
