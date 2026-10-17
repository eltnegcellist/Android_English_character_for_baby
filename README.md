
## 採用した事前生成音声（seed1202）

現在の固定英文セットと「押して聞く」の1文・3文モード、自己紹介・通常試聴は、Macで事前生成したQwen3-TTS音声を再生します。参照音声はVoiceDesign seed1202のsurprise_02、Base1.7B8bitの各生成seedも1202に固定しています。現在の英文は変更していません。音源は `assets/voice-1202/`（Androidでは `app/src/main/assets/voice-1202/`）のmanifest.jsonとAAC-LC 96kbps / 24kHz monoです。

Webでは初回準備時に全音源をSHA256検証してCacheStorageへ保存し、以後はオフラインで再生します。AndroidではAPKへ同梱し、Lite初回準備でKittenモデルの取得を省きます。停止・中断・口の動き・履歴再生は既存処理を使用します。

登録済みの固定英文は採用声を優先します。登録外の自由文（任意のFull/Gemma出力や旧履歴等）は、従来のKittenモデルが準備済みの場合の既存生成経路を維持しています。Qwen3のモデル本体はスマートフォンやブラウザには入れません。

生成条件・モデルrevision・参照SHA256・元英文と音源の対応はmanifest.jsonに記録しています。アプリ用音源は-20 LUFSを目標とした音量調整版をAACへ変換したものです。元の無加工FLOAT WAVはMacの実験成果物に保存しています。

音声バンクの整合性・現行英文の収録漏れは `python3 scripts/verify-prerecorded-voice.py` で確認します。AACは `noCompress` でAPKへ格納し、MediaExtractor / MediaCodecから既存のAudioTrack再生へ渡します。英文セットを変更した場合は、同じ採用条件で対応音声を生成して同梱してください。

# みつことば — 親と赤ちゃんとAI、3人でつくる英語の時間

## 話し終わりの判定（Silero・従来方式）

日本語Moonshineは、アプリ側で切り出した発話をそのまま認識するため、SDK内部VADを無効化しています（`vad_threshold=0`, `vad_max_segment_duration=1000`）。育児語のkeyterm誘導は使用せず、繰り返し生成を防ぐ出力上限は日本語向けの `max_tokens_per_second=13.0` です。13は音声1秒あたりの内部トークン数で、13文字・13秒という制限ではありません。Silero／従来の音量方式によるアプリ側の発話検出は引き続き使用します。

Sileroを標準の発話開始・終了判定として追加しました。設定の「話し終わりの判定」から従来の音量方式へ戻せます。選択は保存され、終了待ちは1.1秒です。Moonshine Japanese Smallと既存のASR音声経路を維持します。 Android v1.9.42に含まれます。 実装・固定モデル・検証範囲・実行方法は [SILERO_VAD.md](SILERO_VAD.md) を参照してください。


## 日本語

みつことばは、**赤ちゃんに英語を聞かせるだけではなく、親・赤ちゃん・AIの3人で英語が生まれる時間をつくる**ためのローカル英語アプリです。

親は普段どおり日本語で赤ちゃんに話しかけます。まず相手になるのは **AI**。AIは日本語を直訳するのではなく、「今なにをしているか」の文脈として受け取り、その場に合う英語を返します。AIキャラクターの初期名は **Emma** で、名前は設定から変更できます。

乳児の外国語学習研究では、**ただ音声や映像を与えることと、社会的な相手と関わりながら経験することでは学習が異なる可能性**が示されています。みつことばは、スマホを渡して終わりにするのではなく、親も一緒に参加する3者の相互作用を家庭につくることを目指しています。

### 配布版

**現在のAndroid配布版：v1.9.47**

**versionCode：130**

- リリース: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/tag/mitsukotoba-v1.9.47
- APK直接リンク: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/download/mitsukotoba-v1.9.47/Mitsukotoba-v1.9.47-android-arm-debug.apk
- Web版: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/

v1.9.47では採用したQwen3-TTSの声・生成seed1202で作成した全1,105音源を同梱し、現在の固定英文・1文／3文モード・自己紹介を再生します。Qwen3モデル本体は端末へ入れません。

v1.9.43では公開機能をLiteの呼びかけへ一本化し、Fullと会話を開発者設定へ移動しました。旧Fullの設定は更新後に一度だけLiteへ切り替わります（モデル・履歴は保持）。v1.9.42で導入したSileroの発話開始・終了判定も維持します。設定から従来RMSへ戻せ、選択は保存されます。終了待ちは1.1秒、Moonshine Japanese Smallと既存の録音音声処理を維持します。v1.9.41のSemantic実推論パリティと隠し診断機能も維持します。Android実機のマイク・CPU負荷・画面オフ会話は未確認で、静音の認識精度が全環境で維持される保証はまだありません。

> 現在配布しているAPKはCIによるdebug署名版です。Play Store向けproduction署名版ではありません。

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

### 設計研究メモ

0〜2歳、とくに0歳児向けの機能設計について、自由会話・AIと乳児のインタラクション・月齢別最適化・親の英語復唱・Repetition Guardまで含めた検討記録を [RESEARCH_INFANT_ENGLISH_DESIGN_2026-10.md](RESEARCH_INFANT_ENGLISH_DESIGN_2026-10.md) にまとめています。これは実装仕様ではなく、将来の機能判断で再利用するための研究メモです。

### 呼びかけ（公開版）

公開するみつことばの基本体験は **Liteの「呼びかけ」だけ** です。親子の今の場面を手がかりに、AIが短い英語を差し込みます。親と赤ちゃんのやり取りが主役です。

v1.9.48からFull（Gemma）と3者会話は隠し設定も含め完全削除し、Liteの呼びかけだけに統一しました。

### 会話を自分で区切る

通常は発話終了を自動検出して返答します。終了検出がうまくいかない場合や、ここまでで返してほしい場合は、会話中の **「ここで返事して」** を押してください。その時点までの録音を確定し、発話終了を待たずにMoonshineへ渡して返答します。自動返答をONにしたままでも使えます。

### エディション

Android版は **Liteのみ** です（v1.9.48でFull/Gemmaを完全廃止）。

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
- Liteの既定：Small
- TTS：Kitten TTS Nano 0.8 / Kiki
- 初期準備後の推論は端末内で実行
- Tiny ASRも軽量オプションとして設定から選択可能

Liteは、毎回生成AIに文章を作らせるのではなく、軽量な応答エンジンから場面に合う英語を選びます。

### 初回起動

初回起動では自動的に **Lite**（既定はMoonshine Small + LiteResponseEngine + Kitten TTS）を選びます。Fullの案内や選択画面は表示しません。

旧バージョンのFull選択はLiteへ移行し、ダウンロード済みのGemmaモデルのみ削除します。Moonshine・Ruriモデルと会話履歴は保持します。

### Lite版の機能

- Moonshine Japanese ASR / Ruriによる話題・Interaction判定
- 事前収録英語音声、画面オフの呼びかけ、履歴、開発者向け診断
- Full/Gemmaによる自由生成や3者会話は廃止

### Web版

APKをインストールせずブラウザで使える **みつことば Web** もあります。

- Web版を開く: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/
- Web版リポジトリ: https://github.com/eltnegcellist/Web_EmmaLocal_English_for_babies

Web版もブラウザで完結する呼びかけアプリとして提供しています。

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
- Moonshine Voice 0.1.5
- Microsoft ONNX Runtime Android 1.23.2
- OkHttp 4.12.0
- AndroidX WorkManager 2.12.0

Moonshine 0.1.5のAndroid AARには独自に縮小した `libonnxruntime.so` が含まれます。みつことばではGradleのArtifact Transformでそのファイルだけを除去し、Kitten TTSにも必要なMicrosoft公式ONNX Runtime Android 1.23.2完全版をMoonshineとKittenで共有します。

追加のsherpa AARを手動取得する必要はありません。

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

### モデルと主要依存関係

| コンポーネント | 用途 | 主なライセンス |
| --- | --- | --- |
| Moonshine Voice / Japanese Streaming | 日本語ASR | MIT |
| Kitten TTS Nano 0.8 FP32 | 英語TTSモデル | Apache-2.0 |
| CMU Pronouncing Dictionary | 英語phonemizer辞書 | BSD-style |
| ONNX Runtime Android 1.23.2 | Kitten / Moonshine推論 | MIT |
| Eigen（ORT等の第三者依存） | 数値計算 | MPL-2.0 |
| OkHttp 4.12.0 | モデルダウンロード | Apache-2.0 |
| AndroidX WorkManager 2.12.0 | バックグラウンドモデル準備 | Apache-2.0 |

Android v1.9.42はv1.9.19で導入した非GPL TTS経路を維持しており、Kitten TTS実行経路では既知の **eSpeak NG / sherpa TTS依存を使用しません**。ただし「GPLフリー」と「permissive-only」は同義ではなく、MPL-2.0等の別ライセンスは残ります。

モデルファイルは別途取得され、リポジトリへ直接含めません。

詳細：
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — 第三者依存と帰属
- [LICENSE_AUDIT.md](LICENSE_AUDIT.md) — GPL / eSpeak依存監査

### プロジェクト状況

**Silero導入前の比較基準としてv1.9.41を保持します。**

みつことばは独立開発中のプロジェクトです。言語習得効果を保証するものではなく、子どもの発達に関する専門的助言の代替を目的としていません。

今後の機能変更は新しいバージョンとして公開し、v1.9.41を再現可能な安定基準版として保持します。

Lite版では一度判定した育児の話題を、曖昧な発話に限り次の最大1ターンまで引き継ぎます。明確な新しい話題が出た場合はすぐ切り替え、保持が切れた後はジェネラルに戻ります。従来Lite・Ruri Semantic・Guardで共通です。

CIではSemantic実推論のAndroid/Web一致も検証します。固定14ケースをWeb WASMとAndroid相当のJava ONNX Runtimeで実際に推論し、token ID・raw topic・最終topicを完全一致、probability差0.025以下、margin差0.035以下、embedding cosine 0.995以上で検証します。これは分類精度のベンチマークではなく、両実装の回帰差を検出するためのテストです。

### 隠し開発者設定

設定画面下部の「みつことば Android」を5回タップすると開発者設定を表示します。Lite / FullとFull専用の呼びかけ / 会話の切り替えはここに移動しました。通常のKiki音声試聴とTTS実測に加え、Ruri Semantic / Guard / 従来Liteを同じ日本語で比較する診断、Semanticで選ばれた英語のKiki試聴、モデル準備状態、診断ログTXT、クラッシュトレースや診断用音声を含む詳細ZIP、完全リセットを利用できます。Semantic診断の会話文脈は通常会話とは分離しています。

### 開発資料

- [shared/README.md](shared/README.md) — Web・Android共通の判定例とガイドの更新手順

- [BRANCH_POLICY.md](BRANCH_POLICY.md) — 開発元・安定版タグ・過去の実験PRの扱い
- [LICENSE_AUDIT.md](LICENSE_AUDIT.md) — 旧TTSから現行TTSへの移行と監査結果
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — 現行の第三者コンポーネント一覧

### ライセンス

みつことば本体のソースコードについては、現時点で独自の利用ライセンスを設定していません。第三者コンポーネントには、それぞれのライセンスが適用されます。

第三者ライセンスの一覧と監査結果は、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) と [LICENSE_AUDIT.md](LICENSE_AUDIT.md) を参照してください。

---

# Mitsukotoba — English Time Made Together by Parent, Baby, and AI

## English

Mitsukotoba is a local-first English app designed not simply to play English to a baby, but to create **a three-way interaction in which parent, baby, and AI share the same moment**.

The parent speaks naturally in Japanese. The default AI character is **Emma**. Emma uses the speech as context rather than translating it literally, and responds directly to the baby in short English. The character name can be changed in Settings, including for use with a favorite toy or plush character.

Research on infant foreign-language learning suggests that passive audio or video exposure may not be equivalent to socially interactive experience. Mitsukotoba is designed around that distinction: not handing a phone to a baby and walking away, but bringing an English-speaking AI into an interaction in which the caregiver remains involved.

### Current Release

**Current distributed Android release: v1.9.47**

**versionCode: 130**

- Release: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/tag/mitsukotoba-v1.9.47
- Direct APK: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/download/mitsukotoba-v1.9.47/Mitsukotoba-v1.9.47-android-arm-debug.apk
- Web edition: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/

v1.9.47 bundles all 1,105 clips generated with the adopted Qwen3 voice and per-utterance seed1202 for the fixed English bank, one/three-sentence play mode, and introduction. The Qwen3 model is not included on the device.

v1.9.43 makes Lite callouts the sole public Android experience while keeping Full/dialogue behind developer settings. Existing Full preferences migrate once to Lite without deleting model data or history. The Silero speech detection added in v1.9.42 remains available with a saved Settings rollback to RMS. Endpoint waiting is 1.1 seconds; Moonshine Japanese Small and the existing captured-audio processing remain unchanged. Semantic runtime parity checks and diagnostics are retained. Physical-device microphone, CPU load and screen-off conversation checks remain outstanding; unchanged quiet-room ASR accuracy across environments has not been established.

> The distributed APK is currently CI debug-signed. It is not a Play Store production-signed build.

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

Android **Lite only**. Full/Gemma was removed in v1.9.48.

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
- Lite default: Small
- TTS: Kitten TTS Nano 0.8 / Kiki
- Inference runs on-device after setup
- Tiny ASR remains available as a lightweight option

Lite stays compact and predictable by using a curated lightweight response engine instead of generating every utterance with a large model.

### First Launch

The first-run flow uses **Lite** by default (Moonshine Small + LiteResponseEngine + Kitten TTS), with no edition selector or Full promotion.

Existing Full selections migrate to Lite. Downloaded Gemma model files are removed while Moonshine, Ruri, and conversation history are preserved.

### Lite Features

- Moonshine Japanese ASR, Ruri semantic Topic/Interaction routing
- Prerecorded English speech, screen-off callouts, local history and diagnostics
- No Full/Gemma engine or developer dialogue toggle

### Web Edition

Mitsukotoba also has a browser-based **Web edition** that does not require APK installation.

- Open Mitsukotoba Web: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/
- Web repository: https://github.com/eltnegcellist/Web_EmmaLocal_English_for_babies

The Web edition is a standalone callout app that runs directly in the browser.

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
- Moonshine Voice 0.1.5
- Microsoft ONNX Runtime Android 1.23.2
- OkHttp 4.12.0
- AndroidX WorkManager 2.12.0

Moonshine 0.1.5's Android AAR contains a reduced `libonnxruntime.so`. Mitsukotoba removes only that native library through a Gradle Artifact Transform and lets Moonshine and Kitten share the official full Microsoft ONNX Runtime Android 1.23.2 runtime.

No manual sherpa AAR download is required.

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

### Models and Major Dependencies

| Component | Purpose | Main license |
| --- | --- | --- |
| Moonshine Voice / Japanese Streaming | Japanese ASR | MIT |
| Kitten TTS Nano 0.8 FP32 | English TTS model | Apache-2.0 |
| CMU Pronouncing Dictionary | English phonemizer lexicon | BSD-style |
| ONNX Runtime Android 1.23.2 | Kitten / Moonshine inference | MIT |
| Eigen (third-party dependency provenance) | Numerical routines | MPL-2.0 |
| OkHttp 4.12.0 | Resumable model downloads | Apache-2.0 |
| AndroidX WorkManager 2.12.0 | Background model preparation | Apache-2.0 |

Android v1.9.42 retains the non-GPL TTS path introduced in v1.9.19. The Kitten TTS execution path does **not use known eSpeak NG or sherpa TTS dependencies**. "GPL-free" does not mean "permissive-only"; separately licensed material such as MPL-2.0 components remains.

Model files are downloaded separately and are not committed to this repository.

See:
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — third-party licenses and attribution
- [LICENSE_AUDIT.md](LICENSE_AUDIT.md) — GPL / eSpeak dependency audit

### Project Status

**v1.9.41 remains the pre-Silero comparison baseline.**

Mitsukotoba is an independently developed project. It does not claim to guarantee language-learning outcomes or replace professional guidance about child development.

Future functional changes should be released under a new version so that v1.9.41 remains reproducible as the stable Mitsukotoba reference point.

### Development Documents

- [shared/README.md](shared/README.md) — shared Web/Android topic tests and guide update procedure

- [BRANCH_POLICY.md](BRANCH_POLICY.md) — source of truth, release tags, and historical experimental PRs
- [LICENSE_AUDIT.md](LICENSE_AUDIT.md) — TTS migration and dependency audit
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — current third-party components

### License

No license for the Mitsukotoba application source code has been granted yet. Third-party components remain subject to their respective licenses.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [LICENSE_AUDIT.md](LICENSE_AUDIT.md) for the third-party license inventory and audit status.



追加機能の作業ブランチ仕様・新しい検証範囲は[FEATURES.md](FEATURES.md)に記載します。
