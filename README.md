# Emma — 赤ちゃんのいる家庭向けローカル英語コンパニオン

## 日本語

Emmaは、親が普段どおり日本語で赤ちゃんに話しかけると、その場面に合った短くやさしい英語で赤ちゃんに語りかける、ローカル処理中心の英語コンパニオンです。

単純な日本語→英語翻訳ではありません。親の日本語を「いま何が起きているか」を理解するための文脈として扱い、Emmaが赤ちゃんへ直接英語で話しかけます。

### 安定版

**現在のAndroid安定版：v1.5.0**  
**versionCode：75**

- リリース: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/tag/emma-v1.5.0
- APK直接リンク: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/download/emma-v1.5.0/EmmaLocal-v1.5.0-android-arm-debug.apk
- Web版: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/

v1.5.0を現在の安定基準版として固定し、今後の機能変更は別バージョンとして公開します。

> 現在配布しているAPKはCIによるdebug署名版です。プロジェクト上の安定版ですが、Play Store向けproduction署名版ではありません。

### Emmaの考え方

親は普段どおり日本語で赤ちゃんに話しかけます。Emmaは親の発話をそのまま翻訳するのではなく、その場面を理解するための手がかりとして使い、赤ちゃんへ直接英語で話しかけます。

### なぜEmmaを作ったのか

#### 赤ちゃんの耳は、まだ一つの言語だけに決まっていない

乳児期の早い段階では、赤ちゃんは母語にはない外国語の音の違いにも高い感度を持っています。研究では、生後6〜12か月ごろにかけて、普段聞く言語の音へ知覚が徐々に最適化され、非母語の音声対立を聞き分ける能力が低下していくことが示されています。

これは単純に能力を失うというより、脳が身の回りの言語へ効率よく適応していく発達の一部と考えられています。Emmaは、この時期に家庭の中で英語の音・リズム・イントネーションへ自然に触れる機会を増やすことを目指しています。

#### ただ英語を流すだけとは違う

Kuhl、Tsao、Liuらの2003年の研究では、9か月の英語環境の乳児が、中国語の母語話者と12回の対面セッションを経験しました。その後、乳児は中国語特有の音の違いをよりよく識別しました。

一方、同じ外国語刺激を映像や音声を通して経験した条件では、同じような音韻学習は確認されませんでした。この研究は、外国語の音を聞く「量」だけでなく、社会的で相互作用のある経験が重要である可能性を示しています。

#### Emmaが目指していること

理想を言えば、英語話者が毎日の親子の時間に入り、赤ちゃんや親の様子に合わせて、その瞬間に合う英語を話してくれる環境です。しかし、それを家庭でいつも実現するのは簡単ではありません。

そこでEmmaは、親が普段どおり日本語で赤ちゃんに話しかけ、その内容を手がかりに、Emmaが赤ちゃんへ英語で反応する仕組みにしました。

たとえば、

```text
親：「お風呂入ろうね」

Emma:
“Bath time!”
“Splash, splash!”
“Here we go!”
```

Emmaの目的は「お風呂入ろうね」を単純に “Let’s take a bath.” と翻訳することではありません。今が「お風呂の時間」だと受け取り、その場にいる英語話者のように赤ちゃんへ直接話しかけることを目指しています。

#### なぜEmmaには顔があるのか

新生児が、スクランブルされた配置や空白の刺激よりも、顔らしく配置された刺激をより長く追視することを示した研究があります。

Emmaは単なる音声プレーヤーではなく、「誰かがこちらに話しかけている」感覚へ少しでも近づけるため、顔・口の動き・まばたき・表情を持つキャラクターとして設計しています。長時間画面を見せること自体を目的としているわけではありません。

#### 研究が証明していることと、Emmaが目指していること

重要な点として、Kuhlらの研究が調べたのは**生身の人間との社会的な外国語経験**です。AIキャラクターであるEmmaが同じ学習効果を生むことは、現時点で直接証明されていません。

Emmaは、「ただ外国語音声を流すだけではなく、相互作用のある言語経験が重要かもしれない」という研究上の示唆を、家庭で日常的に使える形へ近づけようとする試みです。

#### 参考研究

- [Kuhl, Tsao & Liu (2003)](https://doi.org/10.1073/pnas.1532872100) — *Foreign-language experience in infancy: Effects of short-term exposure and social interaction on phonetic learning*. PNAS 100(15), 9096–9101.
- [Werker & Tees (1984)](https://doi.org/10.1016/S0163-6383(84)80022-3) — *Cross-language speech perception: Evidence for perceptual reorganization during the first year of life*. Infant Behavior and Development 7(1), 49–63.
- [Kuhl (2007)](https://doi.org/10.1111/j.1467-7687.2007.00572.x) — *Is speech learning ‘gated’ by the social brain?* Developmental Science 10(1), 110–120.
- [Johnson et al. (1991)](https://doi.org/10.1016/0010-0277(91)90045-6) — *Newborns' preferential tracking of face-like stimuli and its subsequent decline*. Cognition 40(1–2), 1–19.

### エディション

Android版Emmaには **Lite** と **Full** の2つがあります。

| エディション | 応答方式 | 主な特徴 |
| --- | --- | --- |
| **Lite** | LiteResponseEngine | 軽量・予測可能・ローカル中心 |
| **Full** | Gemma + 会話履歴 | より柔軟で文脈に応じた応答 |

LiteとFullは、音声認識と音声合成の基本構成を共有しています。主な違いは「Emmaが何を話すか」の決め方です。

### Emma Lite

```text
マイク
  ↓
Moonshine Japanese Tiny / Small Streaming
  ↓
LiteResponseEngine
  ↓
Kitten TTS Nano 0.8 / Kiki
  ↓
Emmaアバター
```

- 日本語ASR：Moonshine Japanese Tiny / Small Streaming
- Liteの既定：Tiny
- TTS：Kitten TTS Nano 0.8 / Kiki
- 初期準備後の推論は端末内で実行
- Small ASRも設定から選択可能

Liteは、毎回生成AIに文章を作らせるのではなく、軽量な応答エンジンから場面に合う英語を選びます。

### Emma Full

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
        Emmaアバター
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

APKをインストールせずブラウザで使える **Emma Web Lite** もあります。

- Web版を開く: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/
- Web版リポジトリ: https://github.com/eltnegcellist/Web_EmmaLocal_English_for_babies

Web版はLiteのみです。FullはAndroid版で提供します。

### プライバシーとローカル処理

Emmaは、できるだけ端末内で処理する設計です。

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

**v1.5.0を現在の安定基準版とします。**

Emmaは独立開発中のプロジェクトです。言語習得効果を保証するものではなく、子どもの発達に関する専門的助言の代替を目的としていません。

今後の機能変更は新しいバージョンとして公開し、v1.5.0を再現可能な安定基準版として保持します。

### ライセンス

Emma本体のソースコードについては、現時点で独自の利用ライセンスを設定していません。第三者コンポーネントには、それぞれのライセンスが適用されます。

---

# Emma — Local English Companion for Families with Babies

## English

Emma is a local-first English companion for families with babies. Parents can speak naturally in Japanese, and Emma responds to the baby in short, simple English that fits the current situation.

Emma is not intended to be a literal Japanese-to-English translator. The parent's Japanese speech is treated as context for understanding what is happening now, and Emma speaks directly to the baby in English.

### Stable Release

**Current stable Android release: v1.5.0**  
**versionCode: 75**

- Release: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/tag/emma-v1.5.0
- Direct APK: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/download/emma-v1.5.0/EmmaLocal-v1.5.0-android-arm-debug.apk
- Web edition: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/

v1.5.0 is treated as the current stable baseline. Future functional changes should be released under a new version instead of replacing this release.

> The distributed APK is currently CI debug-signed. It is a stable project baseline, but not a Play Store production-signed build.

### Concept

Parents speak naturally in Japanese. Emma uses the parent's speech as context for understanding the situation, then speaks directly to the baby in English rather than simply translating the parent's sentence.

### Why Emma Was Created

#### A baby's ear is not yet tuned to only one language

Early in infancy, babies are highly sensitive to speech-sound contrasts that may not exist in the language they hear every day. Research suggests that between roughly 6 and 12 months of age, speech perception gradually becomes optimized for the languages in the baby's environment, while sensitivity to some non-native phonetic contrasts declines.

This is not simply a loss of ability. It is generally understood as part of the brain's adaptation to the language environment around the child. Emma aims to increase natural opportunities at home for babies to hear English sounds, rhythm, and intonation during this period.

#### More than simply playing English audio

In a 2003 study by Kuhl, Tsao, and Liu, 9-month-old infants from English-speaking environments took part in 12 face-to-face sessions with native Mandarin speakers. Afterwards, the infants showed improved discrimination of Mandarin phonetic contrasts.

Comparable phonetic learning was not observed when the same foreign-language material was presented through audiovisual or audio-only exposure. The study suggests that the amount of foreign-language sound alone may not be the whole story, and that social, interactive experience may matter.

#### What Emma is trying to provide

In an ideal setting, an English speaker could join everyday parent-and-baby moments and say something appropriate in English based on what the baby and parent are doing at that exact moment. That is difficult to provide continuously in most homes.

Emma is an attempt to approximate part of that experience. The parent continues speaking naturally in Japanese, Emma uses the parent's speech as context, and then responds directly to the baby in English.

For example:

```text
Parent: 「お風呂入ろうね」

Emma:
“Bath time!”
“Splash, splash!”
“Here we go!”
```

The goal is not to translate 「お風呂入ろうね」 into “Let’s take a bath.” Emma instead recognizes that this is a bath-time moment and speaks to the baby as an English-speaking person present in that situation might.

#### Why does Emma have a face?

Research has found that newborns may preferentially track face-like configurations compared with scrambled or blank stimuli.

Emma is therefore designed not merely as an audio player, but as a character with a face, mouth movement, blinking, and expression, in an effort to make the experience feel more like someone is speaking to the baby. The goal is not to encourage prolonged screen viewing.

#### What the research shows — and what Emma does not yet prove

An important limitation is that the Kuhl studies examined **social foreign-language experience with real human speakers**. There is currently no direct evidence that an AI character such as Emma produces the same language-learning effect.

Emma is an attempt to bring one implication of this research into an everyday home setting: foreign-language experience may be more meaningful when it is connected to interaction and context, rather than being only passive audio exposure.

#### References

- [Kuhl, Tsao & Liu (2003)](https://doi.org/10.1073/pnas.1532872100) — *Foreign-language experience in infancy: Effects of short-term exposure and social interaction on phonetic learning*. PNAS 100(15), 9096–9101.
- [Werker & Tees (1984)](https://doi.org/10.1016/S0163-6383(84)80022-3) — *Cross-language speech perception: Evidence for perceptual reorganization during the first year of life*. Infant Behavior and Development 7(1), 49–63.
- [Kuhl (2007)](https://doi.org/10.1111/j.1467-7687.2007.00572.x) — *Is speech learning ‘gated’ by the social brain?* Developmental Science 10(1), 110–120.
- [Johnson et al. (1991)](https://doi.org/10.1016/0010-0277(91)90045-6) — *Newborns' preferential tracking of face-like stimuli and its subsequent decline*. Cognition 40(1–2), 1–19.

### Editions

Android Emma has two editions: **Lite** and **Full**.

| Edition | Response engine | Main purpose |
| --- | --- | --- |
| **Lite** | LiteResponseEngine | Lightweight, predictable, local-first |
| **Full** | Gemma + recent conversation context | More flexible, context-aware responses |

Lite and Full share the same basic speech input/output stack. The main difference is how Emma decides what to say.

### Emma Lite

```text
Microphone
  ↓
Moonshine Japanese Tiny / Small Streaming
  ↓
LiteResponseEngine
  ↓
Kitten TTS Nano 0.8 / Kiki
  ↓
Emma avatar
```

- Japanese ASR: Moonshine Japanese Tiny / Small Streaming
- Lite default: Tiny
- TTS: Kitten TTS Nano 0.8 / Kiki
- Inference runs on-device after setup
- Small ASR can also be selected manually

Lite stays compact and predictable by using a curated lightweight response engine instead of generating every utterance with a large model.

### Emma Full

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
        Emma avatar
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

Emma also has a browser-based **Web Lite** edition that does not require APK installation.

- Open Emma Web: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/
- Web repository: https://github.com/eltnegcellist/Web_EmmaLocal_English_for_babies

The Web edition is Lite-only. Full is provided by the Android application.

### Privacy and Local Processing

Emma is designed around local processing.

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

**v1.5.0 is the current stable baseline.**

Emma is an independently developed project. It does not claim to guarantee language-learning outcomes or replace professional guidance about child development.

Future functional changes should be released under a new version so that v1.5.0 remains reproducible as the stable reference point.

### License

No license for the Emma application source code has been granted yet. Third-party components remain subject to their respective licenses.
