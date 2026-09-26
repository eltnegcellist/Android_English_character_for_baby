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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eltnegcellist.emma.ai.BabyGender
import com.eltnegcellist.emma.ai.BabyNamePronunciation

@Composable
internal fun ProductionFamilySettings(
    enabled: Boolean,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    var babyName by remember { mutableStateOf(preferences.getString("baby_name", "").orEmpty()) }
    var spokenBabyName by remember { mutableStateOf(preferences.getString("baby_spoken_name", "").orEmpty()) }
    var useChanSuffix by remember { mutableStateOf(preferences.getBoolean("use_chan_suffix", true)) }
    var babyGender by remember {
        mutableStateOf(BabyGender.fromSaved(preferences.getString("baby_gender", null)))
    }
    var pronunciationOpen by remember { mutableStateOf(false) }
    val resolvedSpokenName = BabyNamePronunciation.withChanSuffix(
        BabyNamePronunciation.toSpokenEnglish(babyName, spokenBabyName),
        useChanSuffix,
    )

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("赤ちゃん", style = MaterialTheme.typography.titleMedium)

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
                label = { Text("名前（任意・日本語）") },
                placeholder = { Text("例：はな") },
            )

            Text("性別", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BabyGender.entries.forEach { option ->
                    if (babyGender == option) {
                        Button(
                            onClick = {},
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { Text(option.label) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                babyGender = option
                                preferences.edit().putString("baby_gender", option.name).apply()
                            },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) { Text(option.label) }
                    }
                }
            }
            Text(
                if (babyGender == BabyGender.UNSPECIFIED) {
                    "未指定の場合、みつことばは名前などから性別を推測しません。"
                } else {
                    "親へ話すときも、この設定に合わせて呼び方を選びます。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(
                onClick = { pronunciationOpen = !pronunciationOpen },
                enabled = enabled,
            ) {
                Text(if (pronunciationOpen) "名前の読み方設定を閉じる" else "名前の読み方を調整")
            }

            if (pronunciationOpen) {
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
                    label = { Text("英語で呼ぶ名前") },
                    placeholder = { Text("例：Hana") },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("名前に「-chan」を付ける", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = useChanSuffix,
                        onCheckedChange = {
                            useChanSuffix = it
                            preferences.edit().putBoolean("use_chan_suffix", it).apply()
                        },
                        enabled = enabled,
                    )
                }
                Text(
                    when {
                        babyName.isBlank() -> "名前は未設定です。"
                        resolvedSpokenName.isNotBlank() -> "みつことばが呼ぶ名前：$resolvedSpokenName"
                        else -> "必要な場合だけ、英字で読み方を指定してください。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!enabled) {
                Text(
                    "会話中は設定を変更できません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
