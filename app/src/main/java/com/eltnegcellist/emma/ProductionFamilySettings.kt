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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eltnegcellist.emma.ai.BabyGender

@Composable
internal fun ProductionFamilySettings(enabled: Boolean) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    var babyGender by remember {
        mutableStateOf(BabyGender.fromSaved(preferences.getString("baby_gender", null)))
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("赤ちゃんの性別", style = MaterialTheme.typography.titleMedium)
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
                    "未指定の場合、みつことばは性別を推測しません。"
                } else {
                    "この設定に合わせて呼び方を選びます。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
