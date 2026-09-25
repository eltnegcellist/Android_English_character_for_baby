# Emma — 赤ちゃんのいる家庭向けローカル英語コンパニオン
# Emma — Local English Companion for Families with Babies

Emmaは、親が普段どおり日本語で赤ちゃんに話しかけると、その場面に合った短くやさしい英語で赤ちゃんに語りかける、ローカル処理中心の英語コンパニオンです。  
単純な日本語→英語翻訳ではなく、親の日本語を「いま何が起きているか」を理解するための文脈として扱います。

Emma is a local-first English companion for families with babies. Parents can speak naturally in Japanese, and Emma responds to the baby in short, simple English that fits the current situation. It is not intended to be a literal Japanese-to-English translator.

## 安定版 / Stable Release

**現在のAndroid安定版：v1.5.0**  
**Current stable Android release: v1.5.0**

- versionCode: **75**
- リリース / Release: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/tag/emma-v1.5.0
- APK直接リンク / Direct APK: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/download/emma-v1.5.0/EmmaLocal-v1.5.0-android-arm-debug.apk
- Web版 / Web edition: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/

v1.5.0を現在の安定基準版として固定し、今後の機能変更は別バージョンとして公開します。  
v1.5.0 is treated as the current stable baseline. Future functional changes should be released under a new version instead of replacing this release.

> 現在配布しているAPKはCIによるdebug署名版です。プロジェクト上の安定版ですが、Play Store向けproduction署名版ではありません。  
> The distributed APK is currently CI debug-signed. It is a stable project baseline, but not a Play Store production-signed build.

## Emmaの考え方 / Concept

親は普段どおり日本語で赤ちゃんに話しかけます。Emmaは親の発話をそのまま翻訳するのではなく、その場面を理解するための手がかりとして使い、赤ちゃんへ直接英語で話しかけます。

Parents speak naturally in Japanese. Emma uses the parent's speech as context for understanding the situation, then speaks directly to the baby in English rather than simply translating the parent's sentence.

## エディション / Editions

Android版Emmaには **Lite** と **Full** の2つがあります。  
Android Emma has two editions: **Lite** and **Full**.

| エディション / Edition | 応答方式 / Response engine | 主な特徴 / Main purpose |
| --- | --- | --- |
| **Lite** | LiteResponseEngine | 軽量・予測可能・ローカル中心 / Lightweight, predictable, local-first |
| **Full** | Gemma + 会話履歴 / recent context | より柔軟で文脈に応じた応答 / More flexible, context-aware responses |

LiteとFullは、音声認識と音声合成の基本構成を共有しています。主な違いは「Emmaが何を話すか」の決め方です。  
Lite and Full share the same basic speech input/output stack. The main difference is how Emma decides what to say.

## Emma Lite

```text
マイク / Microphone
  ↓
Moonshine Japanese Tiny / Small Streaming
  ↓
LiteResponseEngine
  ↓
Kitten TTS Nano 0.8 / Kiki
  ↓
Emmaアバター / Emma avatar
```

- 日本語ASR：Moonshine Japanese Tiny / Small Streaming  
  Japanese ASR: Moonshine Japanese Tiny / Small Streaming
- Liteの既定：Tiny  
  Lite default: Tiny
- TTS：Kitten TTS Nano 0.8 / Kiki
- 初期準備後の推論は端末内で実行  
  Inference runs on-device after setup
- Small ASRも設定から選択可能  
  Small ASR can also be selected manually

Liteは、毎回生成AIに文章を作らせるのではなく、軽量な応答エンジンから場面に合う英語を選びます。  
Lite stays compact and predictable by using a curated lightweight response engine instead of generating every utterance with a large model.

## Emma Full

FullはLiteと同じ音声入出力の仕組みに、応答生成用のGemmaを追加します。  
Full uses the same speech stack as Lite and adds Gemma for response generation.

```text
マイク / Microphone
  ├─→ Moonshine Japanese Tiny / Small Streaming
  │      ↓
  │   日本語文字起こし / Japanese transcript
  │
  └─→ 元音声 / Original audio
             ↓
        Gemma 4 E2B
        + 直近の会話履歴 / recent conversation history
             ↓
        英語応答 / English response
             ↓
        Kitten TTS Nano / Kiki
             ↓
        Emmaアバター / Emma avatar
```

言語内容の主な情報源はMoonshineの文字起こしです。元音声は、抑揚、笑い声、喃語、声の調子、泣き声など、文字だけでは失われる情報を補助的に渡すために使います。  
The Moonshine transcript is the primary source for linguistic meaning. Original audio is secondary context for information that text may lose, such as intonation, laughter, cooing, babbling, squealing, or crying.

Fullでは追加で2GBを超えるGemmaモデルデータが必要です。  
Full requires more than 2 GB of additional local Gemma model data.

## 初回起動 / First Launch

初回起動時に、次のどちらかを選びます。  
On first launch, the family selects one of the following:

- **Lite** — 既定はMoonshine Tiny + LiteResponseEngine + Kitten TTS  
  Default: Moonshine Tiny + LiteResponseEngine + Kitten TTS
- **Full** — 既定はMoonshine Small + Gemma + Kitten TTS  
  Default: Moonshine Small + Gemma + Kitten TTS

選択したエディションは保存され、後から設定画面で変更できます。  
The selected edition is saved and can later be changed from Settings.

旧バージョンの保存値は、現在のLite / Full構成へ移行されます。  
Legacy saved edition values are migrated to the current Lite / Full structure.

## 共通機能 / Shared Features

LiteとFullでは、次の機能を共通で利用します。  
Lite and Full share the following features:

- Moonshine Japanese Tiny / Small Streaming ASR
- Kitten TTS Nano 0.8 / Kiki
- 自動発話終了判定と自動応答  
  Automatic speech endpoint detection and reply flow
- 赤ちゃんの名前・呼び方設定  
  Baby-name pronunciation settings
- 任意の `-chan` 付与  
  Optional `-chan` suffix
- 家族・性別関連設定  
  Family / gender settings
- 外観設定  
  Appearance settings
- 赤ちゃんの非言語音への反応  
  Non-verbal baby response behavior
- 画面スリープ防止設定  
  Screen-awake preference

エディションの違いはシンプルです。  
The edition boundary is intentionally simple:

- **Lite** = 軽量な固定応答知識  
  Lightweight fixed response knowledge
- **Full** = 文字起こし + 元音声の補助情報 + 会話履歴を使った生成応答  
  Generative response using transcript + secondary audio context + recent conversation history

## Web版 / Web Edition

APKをインストールせずブラウザで使える **Emma Web Lite** もあります。  
Emma also has a browser-based **Web Lite** edition that does not require APK installation.

**Web版を開く / Open Emma Web**  
https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/

**Web版リポジトリ / Web repository**  
https://github.com/eltnegcellist/Web_EmmaLocal_English_for_babies

Web版はLiteのみです。FullはAndroid版で提供します。  
The Web edition is Lite-only. Full is provided by the Android application.

## プライバシーとローカル処理 / Privacy and Local Processing

Emmaは、できるだけ端末内で処理する設計です。  
Emma is designed around local processing.

- 音声認識はモデル取得後、端末内で実行  
  Speech recognition runs locally after required model files are downloaded
- 応答選択・生成は端末内で実行  
  Response selection/generation runs locally
- 音声合成は端末内で実行  
  Speech synthesis runs locally
- 通常の会話処理をクラウドAI APIへ送信することを前提としていません  
  Recorded speech and generated conversation are not intended to be sent to a cloud AI API
- ネットワーク通信は主に必要なモデルファイルの取得に使用  
  Network access is primarily used for downloading required model files

この公開リポジトリには、署名鍵、keystore、パスワード、APIトークン、個人・家族固有の非公開情報を意図的に含めません。  
This public repository does not intentionally contain signing keys, keystores, passwords, API tokens, private runner configuration, or family-specific private data.

## 動作要件 / Requirements

- Android 9（API 28）以降  
  Android 9 (API 28) or later
- マイク権限  
  Microphone permission
- 初回モデル取得時のインターネット接続  
  Internet access for initial model downloads
- 選択したエディションに応じた空き容量  
  Sufficient free storage for the selected edition
- ARM Android端末（`arm64-v8a` または `armeabi-v7a`）  
  ARM Android device (`arm64-v8a` or `armeabi-v7a`)

## ビルド環境 / Build Environment

現在の主な構成は以下です。  
The current project uses:

- Java 17
- Android Gradle Plugin 9.3.0
- Kotlin 2.3.21
- compileSdk 37.1
- targetSdk 36
- sherpa-onnx 1.13.8
- Moonshine Voice 0.1.5

sherpa-onnxのAndroidランタイムはリポジトリへ直接含めず、ビルド前に取得します。  
The sherpa-onnx Android runtime is intentionally not committed to the repository and is downloaded before building.

```bash
mkdir -p app/libs
curl -fL \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar \
  -o app/libs/sherpa-onnx-static-1.13.8.aar
```

その後、次のコマンドでテストとビルドを行います。  
Then run:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

## モデルと主要依存関係 / Models and Major Dependencies

- Moonshine Voice / Moonshine Japanese Tiny / Small Streaming — MIT
- Kitten TTS Nano 0.8 — Apache-2.0
- sherpa-onnx — Apache-2.0
- LiteRT-LM / Gemma — Fullモードで使用 / used for optional Full mode

モデルファイルは別途取得され、リポジトリへ直接含めません。  
Model files are downloaded separately and are not committed to this repository.

詳細は [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) を参照してください。  
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for additional information.

## プロジェクト状況 / Project Status

**v1.5.0を現在の安定基準版とします。**  
**v1.5.0 is the current stable baseline.**

Emmaは独立開発中のプロジェクトです。言語習得効果を保証するものではなく、子どもの発達に関する専門的助言の代替を目的としていません。  
Emma is an independently developed project. It does not claim to guarantee language-learning outcomes or replace professional guidance about child development.

今後の機能変更は新しいバージョンとして公開し、v1.5.0を再現可能な安定基準版として保持します。  
Future functional changes should be released under a new version so that v1.5.0 remains reproducible as the stable reference point.

## ライセンス / License

Emma本体のソースコードについては、現時点で独自の利用ライセンスを設定していません。第三者コンポーネントには、それぞれのライセンスが適用されます。  
No license for the Emma application source code has been granted yet. Third-party components remain subject to their respective licenses.
