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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.mitsukotoba_icon),
                    contentDescription = "みつことば ロゴ",
                    modifier = Modifier.size(132.dp).padding(vertical = 4.dp),
                )
            }

            Text(
                "みつことばが英語学習に使える理由",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "みつことばは、英語を一方的に聞かせるのではなく、親・赤ちゃん・AIの3人で反応し合う時間をつくることを目指しています。ここでは、その設計の背景にある研究とガイドラインを分けて紹介します。",
                style = MaterialTheme.typography.titleMedium,
            )

            AboutSection(
                title = "1. 乳児期は、ことばの音への感度が大きく変わる",
                body = "乳児は早い時期には母語にない外国語の音の違いにも感度を持ちますが、生後6〜12か月ごろにかけて、普段聞く言語の音へ知覚が最適化され、一部の非母語の音声対立への感度が低下していくことが報告されています。これは単純な「能力の消失」というより、周囲の言語環境への知覚の再編成と考えられます。[1]",
            )

            AboutSection(
                title = "2. ただ聞かせるだけでは、同じではない",
                body = "Kuhl、Tsao、Liuらの2003年の研究では、9か月児が中国語の母語話者と12回の対面セッションを経験すると、中国語の音の違いを識別する学習が確認されました。一方、同じ話者・同じ内容を映像や音声で提示した条件では、同じような学習は確認されませんでした。外国語の音の量だけでなく、社会的な相互作用が重要である可能性を示した研究です。[2]",
            )

            AboutSection(
                title = "3. スクリーンでも、相互作用があると学習は変わり得る",
                body = "Lytle、Garcia-Sierra、Kuhlらの2018年の研究では、9か月児自身のタッチに反応して外国語動画が再生される環境を使い、1人で体験する条件と、別の乳児と一緒に体験する条件を比較しました。社会的な相手と一緒に体験した乳児では、外国語音韻に対するより成熟した脳反応が見られました。[3]\n\nこれは「スクリーンなら学べない」「画面を見せれば学べる」という二択を意味しません。能動的な参加や社会的な相手の存在によって、スクリーンを介した外国語経験のあり方も変わり得る、という示唆です。なお、この研究の社会的な相手はAIではなく別の乳児であり、みつことば自体の効果を直接証明したものではありません。",
            )

            AboutSection(
                title = "4. なぜAIには顔があるの？",
                body = "新生児が、スクランブルされた配置や空白の刺激よりも、顔らしく配置された刺激をより長く追視したとする研究があります。[4] みつことばのAIには、顔・口の動き・まばたき・表情があります。これは長時間画面へ注意を引きつけるためではなく、短いやり取りの中で「誰かがこちらに話しかけている」ことを示す視覚的な手がかりとして設計しています。",
            )

            AboutSection(
                title = "5. スクリーンタイムをどう考える？",
                body = "WHOの2019年ガイドラインは、1歳未満の乳児についてスクリーンタイムを推奨していません。これは身体活動・座位行動・睡眠を24時間全体で捉える公衆衛生ガイドラインです。[5]\n\n一方、米国小児科学会（AAP）は2026年のPolicy Statementで、子どものデジタル体験をスクリーン時間だけで評価するのではなく、内容、発達段階、親との共同利用、何を置き換えているか、デザインが子どもの発達を支えるものか、といった文脈も含めて考えるべきだとしています。乳児が画面から現実世界へ学習を移しにくいことにも注意を促す一方、共同利用などの文脈を重視しています。[6]",
            )

            AboutSection(
                title = "6. だから、画面を見ることより「3人でどう使うか」",
                body = "みつことばは、赤ちゃんに画面を見せ続けるためのアプリではありません。一方で、AIの顔を見ることを避けるよう求めるものでもありません。赤ちゃんがAIの顔を見るか、親の顔を見るか、目の前のおもちゃやお風呂を見るかは、それぞれの家庭と、その瞬間に委ねます。\n\n大切にしているのは、スマホを赤ちゃんに渡して終わりにしないことです。親も同じ場に参加し、AIの英語をきっかけに赤ちゃんへ話しかけ、赤ちゃんの反応にまた応える。親・赤ちゃん・AIの3人が同じ時間を共有する使い方を想定しています。",
            )

            AboutSection(
                title = "7. 画面を見せたくない家庭では",
                body = "画面を見ることは必須ではありません。たとえばスマートフォンをぬいぐるみの後ろなどに置き、AIキャラクターの声だけがそこから聞こえるようにして、親・赤ちゃん・AIの3者のやり取りを作る使い方もできます。AIの初期名はEmmaですが、ぬいぐるみに合わせて設定から変更できます。\n\n安全のため、端末は布やぬいぐるみで覆わず放熱できる場所に置き、充電中の端末をぬいぐるみの下へ入れたり、赤ちゃんの寝床や手の届く場所へ置いたりしないでください。",
            )

            AboutSection(
                title = "8. なぜ「みつことば」？",
                body = "名前の由来は、親・赤ちゃん・AIの3人です。親が普段どおり日本語で話し、AIが今の場面を受け取って赤ちゃんへ短い英語で返す。赤ちゃんが声や表情で反応し、それを親やAIがまた受け取る。目指しているのは、親 → AI → 赤ちゃんという一方向の再生ではなく、親 ↔ 赤ちゃん ↔ AIの三角形です。",
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("たとえば、お風呂なら", style = MaterialTheme.typography.titleMedium)
                    Text("親：「お風呂入ろうね」", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "AI： “Bath time!” “Splash, splash!” “Here we go!”",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "そこで親がお湯をぱしゃぱしゃしたり、赤ちゃんへ「Splash splashだね」と返したりする。AIの英語を画面の中だけで終わらせず、目の前の親子の時間へつなげることを想定しています。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AboutSection(
                title = "「呼びかけ」と「会話」",
                body = "「呼びかけ」は、親子の今の場面にAIが短い英語を差し込み、親と赤ちゃんのやり取りを支えるモードです。「会話」は、親の発話や直前の流れを踏まえ、AIも3者のやり取りへ継続して参加します。Liteでは「呼びかけ」、Fullでは「呼びかけ」と「会話」を利用できます。",
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
                        "対面での外国語学習を扱った研究[2]や、社会的な相手がいるタッチスクリーン環境を扱った研究[3]は、AIとの3者交流そのものを検証したものではありません。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "したがって、みつことばに同じ言語学習効果があるとは現時点では言えません。みつことばは、「ただ聞く・ただ見るだけでなく、能動性や社会的な相互作用が重要かもしれない」という研究上の示唆を、親・赤ちゃん・AIの3人で家庭の日常へ近づけようとする試みです。",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                "引用した論文",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            ResearchReference(
                number = 1,
                title = "Werker & Tees (1984)",
                description = "Cross-language speech perception: Evidence for perceptual reorganization during the first year of life. Infant Behavior and Development 7(1), 49–63.",
                buttonLabel = "論文を開く",
                onOpen = { uriHandler.openUri("https://doi.org/10.1016/S0163-6383(84)80022-3") },
            )
            ResearchReference(
                number = 2,
                title = "Kuhl, Tsao & Liu (2003)",
                description = "Foreign-language experience in infancy: Effects of short-term exposure and social interaction on phonetic learning. PNAS 100(15), 9096–9101.",
                buttonLabel = "論文を開く",
                onOpen = { uriHandler.openUri("https://doi.org/10.1073/pnas.1532872100") },
            )
            ResearchReference(
                number = 3,
                title = "Lytle, Garcia-Sierra & Kuhl (2018)",
                description = "Two are better than one: Infant language learning from video improves in the presence of peers. PNAS 115(40), 9859–9866.",
                buttonLabel = "論文を開く",
                onOpen = { uriHandler.openUri("https://doi.org/10.1073/pnas.1611621115") },
            )
            ResearchReference(
                number = 4,
                title = "Johnson et al. (1991)",
                description = "Newborns' preferential tracking of face-like stimuli and its subsequent decline. Cognition 40(1–2), 1–19.",
                buttonLabel = "論文を開く",
                onOpen = { uriHandler.openUri("https://doi.org/10.1016/0010-0277(91)90045-6") },
            )

            Text(
                "引用したガイドライン／政策声明",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            ResearchReference(
                number = 5,
                title = "World Health Organization (2019)",
                description = "Guidelines on physical activity, sedentary behaviour and sleep for children under 5 years of age.",
                buttonLabel = "ガイドラインを開く",
                onOpen = { uriHandler.openUri("https://www.who.int/publications/i/item/9789241550536") },
            )
            ResearchReference(
                number = 6,
                title = "American Academy of Pediatrics (2026)",
                description = "Digital Ecosystems, Children, and Adolescents: Policy Statement. Pediatrics 157(2), e2025075320.",
                buttonLabel = "政策声明を開く",
                onOpen = { uriHandler.openUri("https://doi.org/10.1542/peds.2025-075320") },
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
    number: Int,
    title: String,
    description: String,
    buttonLabel: String,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "[$number] $title",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(buttonLabel)
            }
        }
    }
}
