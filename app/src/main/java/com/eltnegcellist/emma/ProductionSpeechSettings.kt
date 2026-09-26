package com.eltnegcellist.emma

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eltnegcellist.emma.ai.BabyNamePronunciation
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
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    var babyName by remember { mutableStateOf(preferences.getString("baby_name", "").orEmpty()) }
    var spokenBabyName by remember { mutableStateOf(preferences.getString("baby_spoken_name", "").orEmpty()) }
    val resolvedSpokenName = BabyNamePronunciation.toSpokenEnglish(babyName, spokenBabyName)

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("会話と声", style = MaterialTheme.typography.titleMedium)

            Text("赤ちゃんの名前", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = babyName,
                onValueChange = { value ->
                    val sanitized = value.filterNot { it == '\n' || it == '\r' || it == '\t' }.take(30)
                    babyName = sanitized
                    preferences.edit().putString("baby_name", sanitized).apply()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = { Text("名前（任意）") },
                placeholder = { Text("例：はな / Hana") },
            )
            OutlinedTextField(
                value = spokenBabyName,
                onValueChange = { value ->
                    val sanitized = value
                        .filterNot { it == '\n' || it == '\r' || it == '\t' }
                        .filter { it.isLetter() || it == '\'' || it == '’' || it == '-' || it == ' ' }
                        .take(40)
                    spokenBabyName = sanitized
                    preferences.edit().putString("baby_spoken_name", sanitized).apply()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = { Text("英語で呼ぶ名前（必要な場合だけ）") },
                placeholder = { Text("例：Hana") },
            )
            Text(
                when {
                    babyName.isBlank() -> "名前は未設定です。"
                    resolvedSpokenName.isNotBlank() -> "みつことばが呼ぶ名前：$resolvedSpokenName"
                    else -> "読み方が必要な場合は英字で指定してください。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
