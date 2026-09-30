# 固定Lite発話の音素検査と、初めての単体ONNX生成

**固定発話202件の音素検査と単体音声生成を通過した。100回の生成・破棄も完走した。アプリ統合の総合判定は引き続き未合格。**

## G2Pの確認範囲を広げた

前回の「未処理語ゼロ」は発音の正しさを証明していなかった。今回、固定発話に出る短縮形・複合表現を含む149種類、延べ1,260語について、独立に作った母音・子音の期待値と比較した。内容語の主強勢も指定して検査する。close/readは固定文の意味に適した読みを別に確認する。

- 202件すべて通過、期待値未登録ゼロ、音素・指定した強勢の不一致ゼロ。
- 既存の発音・文脈39項目は引き続き通過。
- 新しい境界30項目（数字を実際の英文へ挿入、小数・負数・百分率・序数、短縮形、名前の所有格、大文字、未知語、内部音素構文の拒否、別のbassへの文脈漏れ）も通過。
- 既存61項目と名前入り780ケースも再実行し、退行なし。

初回の照合では6種類の短縮・複合表現が期待値未登録だったため、これらを追加した。here/hearの/iɹ/は固定CMU辞書の元データでも確認できるので、/ɪɹ/とともに許容する。a/amの弱形/ɐ/も明示する。候補出力をそのまま正解として保存したものではない。

CMUのoutsideには主強勢が2つある。今回の候補では、モデル入力の主強勢を1つにする規則を明示した。形容詞・名詞では前、その他では後を主強勢にし、もう一方を副強勢にする。音素の母音・子音はCMUのまま。この入力規則によって、固定7文・8出現が修正された。変更前・後の同一期待値での実測を保存している。

outsideの前後に主強勢を置く発音表記は、[Cambridgeの発音ページ](https://dictionary.cambridge.org/pronunciation/english/outside)でも確認した。辞書元データを一般的に「誤り」と断定する変更ではない。独立期待値の音素・強勢・弱形許容は`fixtures/lite-word-segments.json`に明示している。弱い機能語や感嘆・反復表現の強勢、発話の抑揚・自然さはこの検査から除外し、音響段階の課題として残す。

## 単体ONNXへ進めた条件

上記で検査済みの**固定Lite文だけ**を使う診断エンジンを作った。任意の文章・任意の名前・Full自由文を承認したわけではない。

生成スクリプトは、固定文検査の成功、コーパス・期待値・候補コードのSHA-256一致、既存の発音・境界・依存検査を確認してから起動する。失敗または古い固定文検査のままでは生成しない。生成の実測後には、検査済みソースの指紋を全候補Pythonコード・source-manifestまで広げた。一時的に数値処理ファイルへ変更を加えると事前検査で拒否され、復元すると通過することを確認した（音声を再生成しないcheck-only検査）。結果はpreflight-rejection.jsonに保存した。アプリにも既存KittenSpeakerにも接続していない。

取得元は安定版のモデルアーカイブと同じ。アーカイブSHA-256 `16092117bfe591ddcd58d078e1454603b8e1caea46f85653b2c2efae76bd883e`を確認し、モデル・voices.bin・tokens.txt・LICENSE・READMEだけを選択した。eSpeakデータは展開していない。取得用の一時アーカイブも削除した。

- model.fp32.onnx: `2174dbf67b58b7b50d7b65294f89c2c53c172834533519b853c579879a04cc22`
- voices.bin: `d520519c4a3519d44fcfcd943ed0b1e3c5da5cee0eea501d922fac1a93cd24dc`
- ONNX Runtime 1.22.0、CPU、推論2スレッド。
- Kikiはモデル内speaker ID 7 / expr-voice-5-f。声データのSHA-256も照合する。
- 要求速度は安定版と同じ0.8。モデル内のKiki speed prior 0.8を掛けるため、ONNXのspeedテンソルは0.64（float32の実値は約0.639999986）。直接0.8を渡す方式では、この補正を再現できない。

推論はONNX Runtimeへ直接行う。Sherpaランタイム・eSpeak・元のMisaki配布物・num2wordsは使用しない。モデルには由来を示す`has_espeak=1`というメタデータが残っている。この文字列はeSpeak実行やデータの同梱を意味するものとして判定していない。実際に含める資材・インストール依存・fallbackを別に検査する。モデル資材のLICENSE本文も保存した。

## 音声生成の実測

| 検査 | 結果 |
| --- | --- |
| 固定202文 | 全件生成成功 |
| サンプルレート | 全件24kHz |
| 空音声・NaN/Inf | 0件 |
| 定義した長さ・振幅・RMSの構造的検査 | 全件通過 |
| 音声の長さ | 0.675〜6.475秒 |
| 最大絶対振幅 | 約0.976 |
| 絶対振幅1超のサンプル | 0 |
| 1文の生成時間中央値 | 約0.435秒（このクラウドCPU） |
| 202文＋100回生成の所要時間 | 約124秒 |
| 同一入力の追加100回生成・破棄 | 100回完走、空/非有限音声なし |
| 追加100回の実RSS | 373,412〜373,440KiB、幅28KiB |

100回の波形は長さが同じでもハッシュが全て異なった。モデルには乱数演算名も確認できるが、出力差の原因をこの試験だけで確定していない。波形の完全一致を合格条件にしていない。

これは**生成・破棄**の試験であり、再生・停止・再開の100回耐久試験ではない。RSSの幅が小さかったことから、長期のメモリリークがないと証明したわけでもない。音声の自然さ・発話内容・Kikiの聞こえ方を、波形の数値正常性から推定しない。

ONNX用環境でもpip check成功、パッケージメタデータのGPL/LGPL表記検出ゼロ、禁止TTSパッケージ検出ゼロ。最終APKの合格判定ではない。

## 本体との分離と残り

基準はv1.9.12 / `62f39abc095dd29a57d86a0efdef5400a2bb4293`。今回取得したmain `0b8928ecda5c25f3d1fbe22894b3e3e4293d7e96`とも、固定発話と対象2ファイルが一致した。検証ブランチのapp / .github / tools / gradleには基準からの変更なし。

まだ未合格なのは、安定版と比較した発音・音質、名前を含む音声、句分割・間の再現、停止・再生成とコールバック、Androidでのメモリ・再生・モデル管理、最終APK内容、自由文や任意の名前の対応である。今回の診断エンジンは一つの入力をまとめて生成し、現在のSherpaの句分割・間の処理の同等性をまだ検証していない。

APKは作成していない。次は単体エンジン側の句分割・名前入り生成、キャンセル時の契約と比較基準を詰める。VAD・ASR・会話処理は変更しない。

## 再実行

```bash
python -m pip install -r tts-validation/candidates/requirements-onnx.lock
python tts-validation/candidates/fetch_cmudict.py /tmp/mitsukotoba-cmu
python tts-validation/candidates/fetch_kitten_assets.py /tmp/mitsukotoba-kittten-assets
python tts-validation/validate_clean_candidate.py --dictionary /tmp/mitsukotoba-cmu/cmudict.dict
python tts-validation/validate_pronunciation.py --dictionary /tmp/mitsukotoba-cmu/cmudict.dict
python tts-validation/validate_frontend_boundaries.py --dictionary /tmp/mitsukotoba-cmu/cmudict.dict
python tts-validation/validate_lite_words.py --dictionary /tmp/mitsukotoba-cmu/cmudict.dict
python tts-validation/validate_onnx_isolated.py --dictionary /tmp/mitsukotoba-cmu/cmudict.dict --assets /tmp/mitsukotoba-kittten-assets --soak 100
```

最初の検証スクリプトのexit code 1は総合統合未承認を表す。後続の各検査が通過しても総合判定を自動で承認しない。音声・モデルの大きなバイナリはgitへ入れず、取得元とSHA-256、各波形のPCMハッシュと測定値を保存する。
