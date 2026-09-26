# みつことば — 親と赤ちゃんとAI、3人でつくる英語の時間

## 日本語

みつことばは、**赤ちゃんに英語を聞かせるだけではなく、親・赤ちゃん・AIの3人で英語が生まれる時間をつくる**ためのローカル英語アプリです。

親は普段どおり日本語で赤ちゃんに話しかけます。AIキャラクターの初期名は **Emma**。Emmaは日本語を直訳するのではなく、「今なにをしているか」の文脈として受け取り、その場に合う短い英語を赤ちゃんへ返します。名前は設定で変更でき、ぬいぐるみなど別のキャラクターとして使うこともできます。

乳児の外国語学習研究では、**ただ音声や映像を与えることと、社会的な相手と関わりながら経験することでは学習が異なる可能性**が示されています。みつことばは、スマホを渡して終わりにするのではなく、親も一緒に参加する3者の相互作用を家庭につくることを目指しています。

### 安定版

**現在のAndroid安定版：v1.7.1**  
**versionCode：79**

- リリース: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/tag/mitsukotoba-v1.7.1
- APK直接リンク: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/download/mitsukotoba-v1.7.1/Mitsukotoba-v1.7.1-android-arm-debug.apk
- Web版: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/

v1.7.1は、親・赤ちゃん・AIの3者を表す正式ロゴをランチャーアイコンとアプリ内ブランド表示へ採用した安定版です。v1.7.0で追加したEmmaの名前変更・自己紹介・「ここで返事して」も引き続き利用できます。

> 現在配布しているAPKはCIによるdebug署名版です。プロジェクト上の安定版ですが、Play Store向けproduction署名版ではありません。

### なぜみつことばを使うのか

#### 1. 乳児期は、ことばの音への感度が大きく変わる時期

乳児期の早い段階では、赤ちゃんは母語にはない外国語の音の違いにも高い感度を持っています。研究では、生後6〜12か月ごろにかけて、普段聞く言語の音へ知覚が徐々に最適化され、いくつかの非母語の音声対立への感度が低下していくことが示されています。

これは能力を単純に失うというより、脳が身の回りの言語へ効率よく適応していく発達の一部です。みつことばは、この時期に家庭の中で英語の音・リズム・イントネーションへ触れる機会を増やすことを目指しています。

#### 2. ただ英語を聞かせるだけでは、同じではない

Kuhl、Tsao、Liuらの2003年の研究では、9か月児が中国語の母語話者と12回の対面セッションを経験すると、中国語の音の違いを識別する学習が確認されました。

一方、同じ話者・同じ内容を映像や音声で提示した条件では、同じような学習は確認されませんでした。この結果は、外国語の音をどれだけ聞いたかだけでなく、**社会的な相互作用**が重要である可能性を示しています。

#### 3. スクリーンでも、相互作用があると学習のあり方は変わり得る

Lytle、Garcia-Sierra、Kuhlらの2018年の研究では、9か月児自身のタッチに反応して外国語動画が再生される環境を使い、1人で体験する条件と、別の乳児と一緒に体験する条件を比較しました。

社会的な相手と一緒に体験した乳児では、外国語音韻に対するより成熟した脳反応が見られました。これは「スクリーンなら学べない」「画面を見せれば学べる」という単純な二択ではなく、**能動的な参加や社会的な相手の存在によって、スクリーンからの学習のあり方も変わり得る**ことを示唆しています。

この研究の社会的な相手はAIではなく別の乳児です。したがって、みつことばの効果を直接証明するものではありません。しかし、「受動的に見るだけ」と「誰かと一緒に反応しながら経験すること」は同じではない、という点は、みつことばの設計思想に重要な示唆を与えています。

#### 4. だから、親・赤ちゃん・AIの3人にした

理想を言えば、英語話者が毎日の親子の時間に入り、その瞬間に合う英語を赤ちゃんへ話してくれる環境です。しかし、それを家庭でいつも実現するのは簡単ではありません。

そこでみつことばでは、

```text
親が日本語で話す
       ↕
     赤ちゃん
       ↕
AIが英語で応える
```

という3者のやり取りをつくります。

親の日本語は翻訳対象ではなく、今の場面を理解するための文脈です。赤ちゃんの声や喃語も、次のやり取りのきっかけになります。

たとえば、

```text
親：「お風呂入ろうね」

みつことば AI:
“Bath time!”
“Splash, splash!”
“Here we go!”
```

そこで親がお湯をぱしゃぱしゃしたり、赤ちゃんに「Splash splashだね」と返したりする。AIの英語を画面の中だけで終わらせず、**目の前の親子の時間へつなげる**ことを想定しています。

#### 5. スクリーンタイムをどう考えるか

乳児のスクリーン利用については、専門機関のガイダンスにも違いがあります。

WHOの2019年ガイドラインは、1歳未満の乳児についてスクリーンタイムを推奨していません。これは身体活動・座位行動・睡眠を24時間全体で捉える公衆衛生ガイドラインです。

一方、米国小児科学会（AAP）は2026年のPolicy Statementで、子どものデジタル体験を**スクリーン時間だけで評価するのではなく**、内容、発達段階、親との共同利用、何を置き換えているか、デザインが子どもの発達を支えるものか、といった文脈も含めて考えるべきだとしています。AAPは、乳児が画面から現実世界へ学習を移しにくいことにも注意を促しつつ、親子で一緒に関わる joint media engagement が学習や関係形成に関わり得るとしています。

みつことばは、どの家庭にも「赤ちゃんに画面を見せるべき」「見せるべきではない」と決めるものではありません。赤ちゃんがAIの顔を見るか、親の顔を見るか、目の前のおもちゃやお風呂を見るかは、それぞれの家庭と、その瞬間に委ねます。

大切にしているのは、**スマホを赤ちゃんに渡して終わりにしないこと**です。親も同じ場に参加し、AIの英語をきっかけに赤ちゃんへ話しかけ、赤ちゃんの反応にまた応える。画面の有無より、親・赤ちゃん・AIの3人が同じ時間を共有することを重視しています。

画面を見せたくない場合は、端末をぬいぐるみの後ろなど、赤ちゃんの手が届かず放熱できる場所に置き、Emmaの声だけを聞かせる使い方もできます。AIの名前はぬいぐるみに合わせて変更できます。端末を布で覆ったり、充電中の端末をぬいぐるみの下や赤ちゃんの寝床へ置いたりしないでください。

#### 6. なぜAIには顔があるのか

新生児が、スクランブルされた配置や空白の刺激よりも、顔らしく配置された刺激をより長く追視することを示した研究があります。

みつことばのAIには、顔・口の動き・まばたき・表情があります。これは長時間画面へ注意を引きつけるためではなく、短いやり取りの中で「誰かがこちらに話しかけている」ことを示す視覚的な手がかりとして設計しています。

#### 7. 研究が示していることと、まだ分からないこと

重要な点として、2003年の研究は**生身の話者との社会的な外国語経験**を、2018年の研究は**乳児同士の社会的な相手がいるタッチスクリーン環境**を調べたものです。AIとの3者交流そのものを検証した研究ではありません。

したがって、みつことばに同じ言語学習効果があるとは現時点では言えません。

みつことばは、これらの研究から得られる、

**「ただ聞く・ただ見るだけでなく、能動性や社会的な相互作用が重要かもしれない」**

という示唆を、親・赤ちゃん・AIの3人で家庭の日常へ近づけようとする試みです。

#### 参考研究・ガイドライン

- [Kuhl, Tsao & Liu (2003)](https://doi.org/10.1073/pnas.1532872100) — *Foreign-language experience in infancy: Effects of short-term exposure and social interaction on phonetic learning*. PNAS 100(15), 9096–9101.
- [Lytle, Garcia-Sierra & Kuhl (2018)](https://doi.org/10.1073/pnas.1611621115) — *Two are better than one: Infant language learning from video improves in the presence of peers*. PNAS 115(40), 9859–9866.
- [Werker & Tees (1984)](https://doi.org/10.1016/S0163-6383(84)80022-3) — *Cross-language speech perception: Evidence for perceptual reorganization during the first year of life*. Infant Behavior and Development 7(1), 49–63.
- [Kuhl (2007)](https://doi.org/10.1111/j.1467-7687.2007.00572.x) — *Is speech learning ‘gated’ by the social brain?* Developmental Science 10(1), 110–120.
- [American Academy of Pediatrics (2026)](https://doi.org/10.1542/peds.2025-075320) — *Digital Ecosystems, Children, and Adolescents: Policy Statement*. Pediatrics 157(2), e2025075320.
- [World Health Organization (2019)](https://www.who.int/publications/i/item/9789241550536) — *Guidelines on physical activity, sedentary behaviour and sleep for children under 5 years of age*.
- [Johnson et al. (1991)](https://doi.org/10.1016/0010-0277(91)90045-6) — *Newborns' preferential tracking of face-like stimuli and its subsequent decline*. Cognition 40(1–2), 1–19.

### 会話を自分で区切る

通常は発話終了を自動検出して返答します。終了検出がうまくいかない場合や、ここまでで返してほしい場合は、会話中の **「ここで返事して」** を押してください。その時点までの録音を確定し、発話終了を待たずにMoonshineへ渡して返答します。自動返答をONにしたままでも使えます。

### エディション

Android版みつことばには **Lite** と **Full** の2つがあります。

| エディション | 応答方式 | 主な特徴 |
| --- | --- | --- |
| **Lite** | LiteResponseEngine | 軽量・予測可能・ローカル中心 |
| **Full** | Gemma + 会話履歴 | より柔軟で文脈に応じた応答 |

LiteとFullは、音声認識と音声合成の基本構成を共有しています。主な違いは「みつことばが何を話すか」の決め方です。

### みつことば Lite

```text
マイク
  ↓
Moonshine Japanese Tiny / Small Streaming
  ↓
LiteResponseEngine
  ↓
Kitten TTS Nano 0.8 / Kiki
  ↓
みつことばアバター
```

- 日本語ASR：Moonshine Japanese Tiny / Small Streaming
- Liteの既定：Tiny
- TTS：Kitten TTS Nano 0.8 / Kiki
- 初期準備後の推論は端末内で実行
- Small ASRも設定から選択可能

Liteは、毎回生成AIに文章を作らせるのではなく、軽量な応答エンジンから場面に合う英語を選びます。

### みつことば Full

FullはLiteと同じ音声入出力の仕組みに、応答生成用のGemmaを追加します。

```text
マイク
  ├─→ Moonshine Japanese Tiny / Small Streaming
  │      ↓
  │   日本語文字起こし
  │
  └─→ 元音声
             ↓
        Gemma 4 E2B
        + 直近の会話履歴
             ↓
        英語応答
             ↓
        Kitten TTS Nano / Kiki
             ↓
        みつことばアバター
```

言語内容の主な情報源はMoonshineの文字起こしです。元音声は、抑揚、笑い声、喃語、声の調子、泣き声など、文字だけでは失われる情報を補助的に渡すために使います。

Fullでは追加で2GBを超えるGemmaモデルデータが必要です。

### 初回起動

初回起動時に、次のどちらかを選びます。

- **Lite** — 既定はMoonshine Tiny + LiteResponseEngine + Kitten TTS
- **Full** — 既定はMoonshine Small + Gemma + Kitten TTS

選択したエディションは保存され、後から設定画面で変更できます。旧バージョンの保存値は、現在のLite / Full構成へ移行されます。

### 共通機能

LiteとFullでは、次の機能を共通で利用します。

- Moonshine Japanese Tiny / Small Streaming ASR
- Kitten TTS Nano 0.8 / Kiki
- 自動発話終了判定と自動応答
- 赤ちゃんの名前・呼び方設定
- 任意の `-chan` 付与
- 家族・性別関連設定
- 外観設定
- 赤ちゃんの非言語音への反応
- 画面スリープ防止設定

エディションの違いはシンプルです。

- **Lite** = 軽量な固定応答知識
- **Full** = 文字起こし + 元音声の補助情報 + 会話履歴を使った生成応答

### Web版

APKをインストールせずブラウザで使える **みつことば Web Lite** もあります。

- Web版を開く: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/
- Web版リポジトリ: https://github.com/eltnegcellist/Web_EmmaLocal_English_for_babies

Web版はLiteのみです。FullはAndroid版で提供します。

### プライバシーとローカル処理

みつことばは、できるだけ端末内で処理する設計です。

- 音声認識はモデル取得後、端末内で実行
- 応答選択・生成は端末内で実行
- 音声合成は端末内で実行
- 通常の会話処理をクラウドAI APIへ送信することを前提としていません
- ネットワーク通信は主に必要なモデルファイルの取得に使用

この公開リポジトリには、署名鍵、keystore、パスワード、APIトークン、個人・家族固有の非公開情報を意図的に含めません。

### 動作要件

- Android 9（API 28）以降
- マイク権限
- 初回モデル取得時のインターネット接続
- 選択したエディションに応じた空き容量
- ARM Android端末（`arm64-v8a` または `armeabi-v7a`）

### ビルド環境

現在の主な構成は以下です。

- Java 17
- Android Gradle Plugin 9.3.0
- Kotlin 2.3.21
- compileSdk 37.1
- targetSdk 36
- sherpa-onnx 1.13.8
- Moonshine Voice 0.1.5

sherpa-onnxのAndroidランタイムはリポジトリへ直接含めず、ビルド前に取得します。

```bash
mkdir -p app/libs
curl -fL \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar \
  -o app/libs/sherpa-onnx-static-1.13.8.aar
```

その後、次のコマンドでテストとビルドを行います。

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

### モデルと主要依存関係

- Moonshine Voice / Moonshine Japanese Tiny / Small Streaming — MIT
- Kitten TTS Nano 0.8 — Apache-2.0
- sherpa-onnx — Apache-2.0
- LiteRT-LM / Gemma — Fullモードで使用

モデルファイルは別途取得され、リポジトリへ直接含めません。

詳細は [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) を参照してください。

### プロジェクト状況

**v1.6.1を現在の安定基準版とします。**

みつことばは独立開発中のプロジェクトです。言語習得効果を保証するものではなく、子どもの発達に関する専門的助言の代替を目的としていません。

今後の機能変更は新しいバージョンとして公開し、v1.6.1を再現可能な「みつことば」安定基準版として保持します。

### ライセンス

みつことば本体のソースコードについては、現時点で独自の利用ライセンスを設定していません。第三者コンポーネントには、それぞれのライセンスが適用されます。

---

# Mitsukotoba — English Time Made Together by Parent, Baby, and AI

## English

Mitsukotoba is a local-first English app designed not simply to play English to a baby, but to create **a three-way interaction in which parent, baby, and AI share the same moment**.

The parent speaks naturally in Japanese. The default AI character is **Emma**. Emma uses the speech as context rather than translating it literally, and responds directly to the baby in short English. The character name can be changed in Settings, including for use with a favorite toy or plush character.

Research on infant foreign-language learning suggests that passive audio or video exposure may not be equivalent to socially interactive experience. Mitsukotoba is designed around that distinction: not handing a phone to a baby and walking away, but bringing an English-speaking AI into an interaction in which the caregiver remains involved.

### Stable Release

**Current stable Android release: v1.7.1**  
**versionCode: 79**

- Release: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/tag/mitsukotoba-v1.7.1
- Direct APK: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/download/mitsukotoba-v1.7.1/Mitsukotoba-v1.7.1-android-arm-debug.apk
- Web edition: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/

v1.7.0 restores Emma as the default configurable AI character, adds first-turn self-introduction, and adds a manual turn cutoff control. v1.6.1 remains available as the previous research-guidance baseline.

> The distributed APK is currently CI debug-signed. It is a stable project baseline, but not a Play Store production-signed build.

### Why Use Mitsukotoba?

#### 1. Infancy is a period of rapid change in speech perception

Early in infancy, babies are sensitive to many speech-sound contrasts that are not present in the language they hear every day. Research suggests that between roughly 6 and 12 months, perception gradually becomes optimized for the languages in the baby's environment, while sensitivity to some non-native contrasts declines.

This is not simply a loss of ability; it is part of adapting efficiently to the surrounding language environment. Mitsukotoba aims to create more opportunities at home to hear English sounds, rhythm, and intonation during this period.

#### 2. Simply playing English is not the same as social experience

In Kuhl, Tsao, and Liu's 2003 study, 9-month-old infants exposed to native Mandarin speakers in 12 live face-to-face sessions showed learning of Mandarin phonetic contrasts.

Comparable learning was not observed when the same speakers and material were presented by video or audio alone. The result suggests that the amount of foreign-language sound may not be the whole story; **social interaction may matter**.

#### 3. Screen-based learning can also change when interaction changes

Lytle, Garcia-Sierra, and Kuhl (2018) used a touchscreen environment in which 9-month-old infants could trigger foreign-language video clips themselves. Infants who experienced the touchscreen material in the presence of another infant showed more mature neural responses to the foreign-language phonetic contrasts than infants who experienced it alone.

This does not mean that screens automatically teach language, or that any interactive screen experience is beneficial. It suggests something more specific: **active participation and the presence of a social partner can change how infants learn from screen-based material**.

The social partner in this study was another infant, not an AI. The study therefore does not validate Mitsukotoba directly. It does, however, strengthen the distinction between passive viewing and socially situated, responsive experience.

#### 4. Why parent + baby + AI?

Ideally, an English speaker could join everyday parent-and-baby moments and say something appropriate to the baby in English based on what is happening right then. That is difficult to provide continuously at home.

Mitsukotoba tries to approximate part of that situation:

```text
Parent speaks Japanese
        ↕
       Baby
        ↕
AI responds in English
```

The parent's Japanese is context, not a translation target. Baby vocalizations can also become prompts for another response.

For example:

```text
Parent: 「お風呂入ろうね」

Mitsukotoba AI:
“Bath time!”
“Splash, splash!”
“Here we go!”
```

The intended next step is not more screen content. A parent might splash the bathwater, smile at the baby, or echo “Splash, splash.” The AI's English is meant to flow back into the real parent-and-baby moment.

#### 5. How we think about screen time

Guidance on infant screen use is not identical across organizations.

The World Health Organization's 2019 guideline does not recommend screen time for infants under 1 year of age, in the context of 24-hour guidance on physical activity, sedentary behavior, and sleep.

The American Academy of Pediatrics' 2026 Policy Statement takes a broader digital-ecosystem approach. It argues that children's media experiences should not be evaluated only through screen-time limits, but also by content, developmental stage, caregiver involvement, what media displaces, and whether design supports children's needs. The AAP also notes that infants have difficulty transferring learning from screens to the real world, while joint media engagement with caregivers can support learning and relationships.

Mitsukotoba does not prescribe whether a family should or should not let a baby look at the screen. A baby may look at the AI face, the caregiver's face, a toy, bathwater, or something else in the room.

What matters to the design is that the caregiver does not simply hand over the phone and disengage. Mitsukotoba is intended to be used as a shared interaction among **parent, baby, and AI**. Families who prefer not to show the display can place the phone behind a plush toy or other character, out of the baby's reach and without covering the device, and use the AI voice as that character. Do not place a charging phone under fabric, inside bedding, or in the baby's sleep space.

#### 6. Why does the AI have a face?

Research has found that newborns may preferentially track face-like configurations compared with scrambled or blank stimuli.

Mitsukotoba therefore uses a face, mouth movement, blinking, and expression as visual cues that someone is speaking. The purpose is not to maximize attention or prolong viewing, but to support a short, socially legible exchange.

#### 7. What the research shows — and what it does not

The 2003 study investigated interaction with live human speakers. The 2018 study investigated touchscreen foreign-language exposure in the presence or absence of another infant. Neither study tested a three-way interaction involving an AI.

Mitsukotoba therefore does **not** claim that the same language-learning effects have been demonstrated for this app.

It is an attempt to translate one research theme into a practical home design:

**language experience may be more meaningful when it is active, responsive, and socially situated rather than purely passive.**

#### References and Guidance

- [Kuhl, Tsao & Liu (2003)](https://doi.org/10.1073/pnas.1532872100) — *Foreign-language experience in infancy: Effects of short-term exposure and social interaction on phonetic learning*. PNAS 100(15), 9096–9101.
- [Lytle, Garcia-Sierra & Kuhl (2018)](https://doi.org/10.1073/pnas.1611621115) — *Two are better than one: Infant language learning from video improves in the presence of peers*. PNAS 115(40), 9859–9866.
- [Werker & Tees (1984)](https://doi.org/10.1016/S0163-6383(84)80022-3) — *Cross-language speech perception: Evidence for perceptual reorganization during the first year of life*. Infant Behavior and Development 7(1), 49–63.
- [Kuhl (2007)](https://doi.org/10.1111/j.1467-7687.2007.00572.x) — *Is speech learning 'gated' by the social brain?* Developmental Science 10(1), 110–120.
- [American Academy of Pediatrics (2026)](https://doi.org/10.1542/peds.2025-075320) — *Digital Ecosystems, Children, and Adolescents: Policy Statement*. Pediatrics 157(2), e2025075320.
- [World Health Organization (2019)](https://www.who.int/publications/i/item/9789241550536) — *Guidelines on physical activity, sedentary behaviour and sleep for children under 5 years of age*.
- [Johnson et al. (1991)](https://doi.org/10.1016/0010-0277(91)90045-6) — *Newborns' preferential tracking of face-like stimuli and its subsequent decline*. Cognition 40(1–2), 1–19.

### Manually End a Turn

Mitsukotoba normally detects the end of speech automatically. If endpoint detection is slow or you want a reply immediately, tap **“Reply now”** while speaking. The app stops the current listening turn at that point and processes the audio collected so far. This works even when automatic response is enabled.

### Editions

Android Mitsukotoba has two editions: **Lite** and **Full**.

| Edition | Response engine | Main purpose |
| --- | --- | --- |
| **Lite** | LiteResponseEngine | Lightweight, predictable, local-first |
| **Full** | Gemma + recent conversation context | More flexible, context-aware responses |

Lite and Full share the same basic speech input/output stack. The main difference is how Mitsukotoba decides what to say.

### Mitsukotoba Lite

```text
Microphone
  ↓
Moonshine Japanese Tiny / Small Streaming
  ↓
LiteResponseEngine
  ↓
Kitten TTS Nano 0.8 / Kiki
  ↓
Mitsukotoba avatar
```

- Japanese ASR: Moonshine Japanese Tiny / Small Streaming
- Lite default: Tiny
- TTS: Kitten TTS Nano 0.8 / Kiki
- Inference runs on-device after setup
- Small ASR can also be selected manually

Lite stays compact and predictable by using a curated lightweight response engine instead of generating every utterance with a large model.

### Mitsukotoba Full

Full uses the same speech stack as Lite and adds Gemma for response generation.

```text
Microphone
  ├─→ Moonshine Japanese Tiny / Small Streaming
  │      ↓
  │   Japanese transcript
  │
  └─→ Original audio
             ↓
        Gemma 4 E2B
        + recent conversation history
             ↓
        English response
             ↓
        Kitten TTS Nano / Kiki
             ↓
        Mitsukotoba avatar
```

The Moonshine transcript is the primary source for linguistic meaning. Original audio is secondary context for information that text may lose, such as intonation, laughter, cooing, babbling, squealing, or crying.

Full requires more than 2 GB of additional local Gemma model data.

### First Launch

On first launch, the family selects one of the following:

- **Lite** — default: Moonshine Tiny + LiteResponseEngine + Kitten TTS
- **Full** — default: Moonshine Small + Gemma + Kitten TTS

The selected edition is saved and can later be changed from Settings. Legacy saved edition values are migrated to the current Lite / Full structure.

### Shared Features

Lite and Full share the following features:

- Moonshine Japanese Tiny / Small Streaming ASR
- Kitten TTS Nano 0.8 / Kiki
- Automatic speech endpoint detection and reply flow
- Baby-name pronunciation settings
- Optional `-chan` suffix
- Family / gender settings
- Appearance settings
- Non-verbal baby response behavior
- Screen-awake preference

The edition boundary is intentionally simple:

- **Lite** = lightweight fixed response knowledge
- **Full** = generative response using transcript + secondary audio context + recent conversation history

### Web Edition

Mitsukotoba also has a browser-based **Web Lite** edition that does not require APK installation.

- Open Mitsukotoba Web: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/
- Web repository: https://github.com/eltnegcellist/Web_EmmaLocal_English_for_babies

The Web edition is Lite-only. Full is provided by the Android application.

### Privacy and Local Processing

Mitsukotoba is designed around local processing.

- Speech recognition runs locally after required model files are downloaded
- Response selection/generation runs locally
- Speech synthesis runs locally
- Recorded speech and generated conversation are not intended to be sent to a cloud AI API
- Network access is primarily used for downloading required model files

This public repository does not intentionally contain signing keys, keystores, passwords, API tokens, private runner configuration, or family-specific private data.

### Requirements

- Android 9 (API 28) or later
- Microphone permission
- Internet access for initial model downloads
- Sufficient free storage for the selected edition
- ARM Android device (`arm64-v8a` or `armeabi-v7a`)

### Build Environment

The current project uses:

- Java 17
- Android Gradle Plugin 9.3.0
- Kotlin 2.3.21
- compileSdk 37.1
- targetSdk 36
- sherpa-onnx 1.13.8
- Moonshine Voice 0.1.5

The sherpa-onnx Android runtime is intentionally not committed to the repository and is downloaded before building.

```bash
mkdir -p app/libs
curl -fL \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar \
  -o app/libs/sherpa-onnx-static-1.13.8.aar
```

Then run:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

### Models and Major Dependencies

- Moonshine Voice / Moonshine Japanese Tiny / Small Streaming — MIT
- Kitten TTS Nano 0.8 — Apache-2.0
- sherpa-onnx — Apache-2.0
- LiteRT-LM / Gemma — used for Full mode

Model files are downloaded separately and are not committed to this repository.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for additional information.

### Project Status

**v1.6.1 is the current stable baseline.**

Mitsukotoba is an independently developed project. It does not claim to guarantee language-learning outcomes or replace professional guidance about child development.

Future functional changes should be released under a new version so that v1.6.1 remains reproducible as the stable Mitsukotoba reference point.

### License

No license for the Mitsukotoba application source code has been granted yet. Third-party components remain subject to their respective licenses.
