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
                "みつことばとは？",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "英語を聞かせるだけではなく、親・赤ちゃん・AIの3人で、英語が生まれる時間をつくるためのアプリです。",
                style = MaterialTheme.typography.titleMedium,
            )

            AboutSection(
                title = "なぜ乳児期に英語の音？",
                body = "乳児期の早い段階では、赤ちゃんは母語にはない外国語の音の違いにも高い感度を持っています。研究では、生後6〜12か月ごろにかけて、普段聞く言語の音へ知覚が徐々に最適化され、いくつかの非母語の音声対立への感度が低下していくことが示されています。これは能力を単純に失うというより、脳が身の回りの言語へ効率よく適応していく発達の一部と考えられています。",
            )

            AboutSection(
                title = "ただ聞かせるだけでは足りないかもしれない",
                body = "Kuhl、Tsao、Liuらの2003年の研究では、9か月児が中国語の母語話者と12回の対面セッションを経験すると、中国語の音の違いを識別する学習が確認されました。一方、同じ話者・同じ内容を映像や音声で提示した条件では、同じような学習は確認されませんでした。この結果は、外国語の音の量だけでなく、社会的な相互作用が重要である可能性を示しています。",
            )

            AboutSection(
                title = "スクリーンでも、相互作用があると違う",
                body = "Lytle、Garcia-Sierra、Kuhlらの2018年の研究では、9か月児自身のタッチに反応して外国語動画が再生される環境を使い、1人で体験する条件と、別の乳児と一緒に体験する条件を比較しました。社会的な相手と一緒に体験した乳児では、外国語音韻に対するより成熟した脳反応が見られました。これは「スクリーンなら学べない／見せれば学べる」という二択ではなく、能動的な参加や社会的な相手の存在によって、スクリーンからの学習のあり方も変わり得ることを示唆しています。",
            )

            AboutSection(
                title = "スクリーンタイムをどう考える？",
                body = "WHOの2019年ガイドラインは、1歳未満の乳児についてスクリーンタイムを推奨していません。一方、米国小児科学会（AAP）は2026年の声明で、子どものデジタル体験をスクリーン時間だけで評価するのではなく、内容、発達段階、親との共同利用、何を置き換えているかなども含めて考えるべきだとしています。AAPは乳児が画面から現実世界へ学習を移しにくいことにも注意を促しつつ、短時間の高品質なコンテンツや、親子で一緒に関わる joint media engagement を単純な時間だけでは評価できないとしています。",
            )

            AboutSection(
                title = "画面を見るかより、3人でどう使うか",
                body = "みつことばは、赤ちゃんに画面を見せ続けるためのアプリではありません。一方で、AIの顔を見ることを避けるよう求めるものでもありません。赤ちゃんがAIの顔を見るか、親の顔を見るか、目の前のおもちゃやお風呂を見るかは、それぞれの家庭と、その瞬間に委ねます。大切にしているのは、スマホを赤ちゃんに渡して終わりにせず、親も同じ場に参加することです。",
            )

            AboutSection(
                title = "なぜ「みつことば」？",
                body = "名前の由来は、親・赤ちゃん・AIの3人です。親が普段どおり日本語で話し、AIが今の場面を受け取って赤ちゃんへ短い英語で返す。赤ちゃんが声や表情で反応し、それを親やAIがまた受け取る。目指しているのは、親 → AI → 赤ちゃんという一方向の再生ではなく、親 ↔ 赤ちゃん ↔ AIの三角形です。AIキャラクターの初期名はEmmaですが、設定で好きな名前へ変更できます。",
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("たとえば、お風呂なら", style = MaterialTheme.typography.titleMedium)
                    Text("親：「お風呂入ろうね」", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "みつことば AI： “Bath time!” “Splash, splash!” “Here we go!”",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "そこで親がお湯をぱしゃぱしゃしたり、赤ちゃんへ「Splash splashだね」と返したりする。AIの英語を画面の中だけで終わらせず、目の前の親子の時間へつなげることを想定しています。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AboutSection(
                title = "画面を見せたくない家庭では",
                body = "みつことばは画面を見ることを必須にしていません。たとえばスマートフォンをぬいぐるみの後ろなどに置き、Emmaの声だけがそこから聞こえるようにして、親・赤ちゃん・AIの3者のやり取りを作る使い方もできます。AIの名前もぬいぐるみに合わせて変更できます。安全のため、端末は布やぬいぐるみで覆わず放熱できる場所に置き、充電中の端末をぬいぐるみの下へ入れたり、赤ちゃんの寝床や手の届く場所へ置いたりしないでください。",
            )

            AboutSection(
                title = "なぜAIには顔があるの？",
                body = "新生児が、スクランブルされた配置や空白の刺激よりも、顔らしく配置された刺激をより長く追視することを示した研究があります。みつことばのAIには、顔・口の動き・まばたき・表情があります。これは長時間画面へ注意を引きつけるためではなく、短いやり取りの中で「誰かがこちらに話しかけている」ことを示す視覚的な手がかりとして設計しています。",
            )

            AboutSection(
                title = "Lite・Full",
                body = "みつことば LiteとFullは、どちらも親の日本語を手がかりに赤ちゃんへ英語で話しかけます。Liteは軽量なLiteResponseEngineから短い返答を選び、FullはGemmaを使って文字起こし、元音声の非言語情報、直前の会話も参考にしながら、その場に合わせた英語を生成します。",
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "研究が示していることと、まだ分からないこと",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "2003年の研究は生身の話者との交流を、2018年の研究は乳児同士の社会的な相手がいるタッチスクリーン環境を調べたものです。どちらも、AIとの3者交流そのものを検証した研究ではありません。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "したがって、みつことばに同じ言語学習効果があるとは現時点では言えません。みつことばは、これらの研究から得られる「受動的に聞く・見るだけでなく、能動性や社会的な相互作用が重要かもしれない」という示唆を、親・赤ちゃん・AIの3人で家庭の日常へ近づけようとする試みです。",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                "参考研究・ガイドライン",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            ResearchReference(
                title = "Kuhl, Tsao & Liu (2003)",
                description = "Foreign-language experience in infancy: Effects of short-term exposure and social interaction on phonetic learning. PNAS 100(15), 9096–9101.",
                onOpen = { uriHandler.openUri("https://doi.org/10.1073/pnas.1532872100") },
            )
            ResearchReference(
                title = "Lytle, Garcia-Sierra & Kuhl (2018)",
                description = "Two are better than one: Infant language learning from video improves in the presence of peers. PNAS 115(40), 9859–9866.",
                onOpen = { uriHandler.openUri("https://doi.org/10.1073/pnas.1611621115") },
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
                title = "American Academy of Pediatrics (2026)",
                description = "Digital Ecosystems, Children, and Adolescents: Policy Statement. Pediatrics 157(2): e2025075320.",
                onOpen = { uriHandler.openUri("https://doi.org/10.1542/peds.2025-075320") },
            )
            ResearchReference(
                title = "World Health Organization (2019)",
                description = "Guidelines on physical activity, sedentary behaviour and sleep for children under 5 years of age.",
                onOpen = { uriHandler.openUri("https://www.who.int/publications/i/item/9789241550536") },
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
                Text("資料を開く")
            }
        }
    }
}
