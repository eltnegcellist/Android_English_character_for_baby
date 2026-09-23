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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun EmmaOnboardingScreen(
    onContinue: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "はじめまして、Emmaです",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "親は、いつもどおり日本語で赤ちゃんに話しかけてください。\n" +
                    "Emmaはその日本語を「いま何が起きているか」の文脈として受け取り、" +
                    "赤ちゃんへ英語で話しかけます。",
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(Modifier.height(18.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "翻訳アプリではありません",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "日本語をそのまま英訳するのではなく、その場に合う短い英語でEmmaが会話に加わります。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "親は日本語のままで大丈夫",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "英語育児のために、親が無理に英語へ言い換える必要はありません。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "標準EmmaとEmma Full",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "標準Emmaは、日常の育児場面に合わせた短い英語を軽快に話します。" +
                            "親との自由な会話をしたいときは「親へ」を押すと、Gemmaを使うEmma Fullをご案内します。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "必要な音声・AIモデルの準備後、会話の主要処理は端末内で行います。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Emmaをはじめる")
            }
        }
    }
}
