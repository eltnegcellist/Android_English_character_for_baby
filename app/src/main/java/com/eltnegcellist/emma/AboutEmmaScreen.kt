package com.eltnegcellist.emma

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun AboutEmmaScreen(
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text("← 戻る") }
            }

            Text(
                "Emmaとは？",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "親子のいつもの会話の中に、英語の話し手をもう一人加えることを目指したアプリです。",
                style = MaterialTheme.typography.titleMedium,
            )

            AboutSection(
                title = "赤ちゃんの耳は、まだ一つの言語だけに決まっていない",
                body = "乳児期の早い段階では、赤ちゃんは母語にはない外国語の音の違いにも高い感度を持っています。研究では、生後6〜12か月ごろにかけて、普段聞く言語の音へ知覚が徐々に最適化され、非母語の音声対立を聞き分ける能力が低下していくことが示されています。これは単純に能力を失うというより、脳が身の回りの言語へ効率よく適応していく発達の一部と考えられています。",
            )

            AboutSection(
                title = "ただ英語を流すだけとは違う",
                body = "Kuhl、Tsao、Liuらの2003年の研究では、9か月の英語環境の乳児が、中国語の母語話者と12回の対面セッションを経験しました。その後、乳児は中国語特有の音の違いをよりよく識別しました。一方、同じ外国語刺激を映像や音声を通して経験した条件では、同じような音韻学習は確認されませんでした。少なくともこの研究は、外国語の音を聞く量だけでなく、社会的で相互作用のある経験が重要である可能性を示しています。",
            )

            AboutSection(
                title = "Emmaの設計思想",
                body = "理想を言えば、英語話者が毎日の親子の時間に入り、赤ちゃんや親の様子に合わせて、その瞬間に合う英語を話してくれる環境です。しかし、それを家庭でいつも実現するのは簡単ではありません。そこでEmmaは、親が普段どおり日本語で赤ちゃんに話しかけ、その内容を手がかりに、Emmaが赤ちゃんへ英語で反応する仕組みにしました。",
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("たとえば", style = MaterialTheme.typography.titleMedium)
                    Text("親：「お風呂入ろうね」", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Emma： “Bath time!” “Splash, splash!” “Here we go!”",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "“Let’s take a bath.” と翻訳することが目的ではありません。今が「お風呂の時間」だと受け取り、その場にいる英語話者のように赤ちゃんへ直接話しかけます。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AboutSection(
                title = "もっと自由に話すFull版もあります",
                body = "通常のEmmaは、赤ちゃんへ短く分かりやすい英語をすばやく返すことを大切にしています。さらに、より自由に、あなたの話や直前の会話の流れに合わせてAIがその場で言葉を考えて話すFull版も用意しています。Full版は追加の大きなAIデータが必要なので、まずは通常のEmmaから始め、必要になったら設定から切り替えられます。",
            )

            AboutSection(
                title = "なぜEmmaには顔があるの？",
                body = "新生児が、スクランブルされた配置や空白の刺激よりも、顔らしく配置された刺激をより長く追視することを示した研究があります。Emmaは単なる音声プレーヤーではなく、「誰かがこちらに話しかけている」感覚へ少しでも近づけるため、顔・口の動き・まばたき・表情を持つキャラクターとして設計しています。長時間画面を見せること自体を目的としているわけではありません。",
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "研究が証明していることと、Emmaが目指していること",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Kuhlらの研究が調べたのは、生身の人間との社会的な外国語経験です。AIキャラクターのEmmaが同じ学習効果を生むことは、現時点で直接証明されていません。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Emmaは、その研究から得られた「ただ音を流すだけでなく、相互作用のある言語経験が重要かもしれない」という示唆を、家庭でできる形へ近づけようとする試みです。",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                "参考研究",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            ResearchReference(
                title = "Kuhl, Tsao & Liu (2003)",
                description = "Foreign-language experience in infancy: Effects of short-term exposure and social interaction on phonetic learning. PNAS 100(15), 9096–9101.",
                onOpen = { uriHandler.openUri("https://doi.org/10.1073/pnas.1532872100") },
            )
            ResearchReference(
                title = "Werker & Tees (1984)",
                description = "Cross-language speech perception: Evidence for perceptual reorganization during the first year of life. Infant Behavior and Development 7(1), 49–63.",
                onOpen = { uriHandler.openUri("https://doi.org/10.1016/S0163-6383(84)80022-3") },
            )
            ResearchReference(
                title = "Kuhl (2007)",
                description = "Is speech learning ‘gated’ by the social brain? Developmental Science 10(1), 110–120.",
                onOpen = { uriHandler.openUri("https://doi.org/10.1111/j.1467-7687.2007.00572.x") },
            )
            ResearchReference(
                title = "Johnson et al. (1991)",
                description = "Newborns' preferential tracking of face-like stimuli and its subsequent decline. Cognition 40(1–2), 1–19.",
                onOpen = { uriHandler.openUri("https://doi.org/10.1016/0010-0277(91)90045-6") },
            )
        }
    }
}

@Composable
private fun AboutSection(
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResearchReference(
    title: String,
    description: String,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("論文を開く")
            }
        }
    }
}
