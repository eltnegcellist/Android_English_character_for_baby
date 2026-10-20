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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import com.eltnegcellist.emma.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun FirstRunOnboardingScreen(
    languagePicker: @Composable () -> Unit = {},
    busy: Boolean,
    ready: Boolean,
    phase: String,
    progressPercent: Int?,
    errorMessage: String?,
    highPerformance: Boolean,
    onHighPerformanceChange: (Boolean) -> Unit,
    onPrepare: () -> Unit,
    onOpenAbout: () -> Unit,
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
            MitsukotobaBrandMark()
            languagePicker()
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



            Text("初期設定では会話履歴を端末内に残します。録音は保存しません。画面を閉じると会話は停止します。画面を閉じても続けたい場合は設定でオンにできます。履歴の保存も設定で変更できます。", style = MaterialTheme.typography.bodySmall)

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

            Text(if (AppLanguage.language == "ja") "Moonshine Small · Ruri" else "Whisper Small · E5 · ~387 MB", style = MaterialTheme.typography.bodySmall)

            when {
                busy -> {
                    CircularProgressIndicator()
                    progressPercent?.let {
                        Text("$it%", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        phase.ifBlank { "みつことばのモデルを準備しています…" },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "準備が終わると、そのまま呼びかけを始められます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ready -> {
                    Text(
                        "準備が完了しました。チュートリアルへ移動します…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    Button(
                        onClick = onPrepare,
                        enabled = !AppLanguage.choiceRequired.value,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (errorMessage == null) "みつことばを準備する" else "もう一度準備する")
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
