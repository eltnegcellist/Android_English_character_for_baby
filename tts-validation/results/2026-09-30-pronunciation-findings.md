# 発音と文脈の追加検証

**字句カバレッジが通っていても、誤読があった。修正後、拡張した39項目は通過した。統合の総合判定は引き続き未合格。**

## 検出した問題と修正

CMU辞書の最初の発音候補を一律に使うと、close / live / present / record / lead / object / content / useで、品詞に適した読みを選べない例があった。今回、監査したCMUの番号付き発音候補を品詞へ対応付けた。全ての同綴異音語に対応したという意味ではない。

実際のLite固定発話にも、次の2件の誤読を発見した。

- `Little hands! Open, close. Wiggle, wiggle!`
- `Hands, hands! Open and close. Wiggle, wiggle!`

小さい品詞モデルはここでcloseを形容詞JJに分類する。手を閉じる動作の/z/ではなく、近いという意味の/s/になっていた。`Open, close.` / `Open and close.`という短い動作表現に限り、CMUの動詞の発音を使う。`Nice and close.`の/s/は維持する。固定発話202件で音素が変わったのはこの2件、名前入り780件では対応する10件だった。

品詞だけで区別できない名詞のleadについては、take/hold/in the lead等の限定した表現を扱う。doesの鹿の複数形は、明確な限定詞・数と動物の動作を伴う場合だけCMUのdoe + /z/を使う。一般的な語義分類器ではなく、未対応の文脈が残る。

bassは以前、一つの明確な音楽文脈が別の文のbassにも適用されていた。今回は句単位で判定し、別の出現へ意味を引き継がない。一区切りに複数のbassがある場合は保守的に未解決とする。

| 入力 | 修正後の扱い |
| --- | --- |
| `I play the bass guitar. The bass swims.` | 音楽と魚をそれぞれ選ぶ |
| `I play the bass guitar. Please bring the bass.` | 後半が未解決、読み上げ用IPAを返さない |
| `The bass swims. Please bring the bass.` | 後半が未解決、読み上げ用IPAを返さない |
| `The bass guitar and the bass fish are here.` | 同じ句に複数出現、未解決として扱う |
| `The bass is low.` | 引き続き未解決 |

## 結果

`validate_pronunciation.py`は、派生文を含む単語の期待音素33件と、bassの出現ごとの判定・読み上げ可否6件の**計39項目**を試験する。期待値は候補の出力を保存して作ったものではない。アメリカ英語の読み分けを独立に定義し、Kitten向け記号へ展開した単語音素と比較する。このエンジンの記号慣例では強勢記号を強勢母音の直前へ置く。

| 検査 | 修正前 | 修正後 |
| --- | ---: | ---: |
| 拡張発音・文脈39項目 | 20通過、19失敗 | 39通過、0失敗 |
| 固定発話202件の未処理語・非対応記号 | 0 | 0 |
| 名前入り780件の未処理語・非対応記号 | 0 | 0 |
| 既存の期待値・拒否検査61項目 | 61通過 | 61通過 |

修正前は前回保存した候補と同一内容の`0ede33b`、GitHub上の`df30c2a7cd82341e99746efb15013a291d93f95f`に対応する。修正前のbassには出現ごとの判定欄がなかったため、従来の文全体の判定を各出現に適用して比較する。新しい欄の有無だけで失敗にしていない。

実測は`pronunciation-before.json`と`pronunciation-expanded.json`に保存。既存コーパスの再実行結果は`cmu-candidate-summary.json`と`cmu-candidate.json.gz`に保存。従来の曖昧なbass期待値は削除せず、未解決として検出する。

クリーン環境でpip check成功、パッケージメタデータのGPL/LGPL表記検出ゼロ、禁止パッケージの検出ゼロ。この結果を最終配布物のライセンス合格とは扱わない。

基準はv1.9.12 / `62f39abc095dd29a57d86a0efdef5400a2bb4293`。作業中にmainが更新されたため、最新取得時点の`6224d9745b1d1c009d55ad766b38330f1c06d038`とも発話データ・対象2ファイルの一致を確認した。検証ブランチのapp / .github / tools / gradleには基準からの変更なし。

## 判定と次の条件

**この39項目が通ったことで、G2P全体を合格にはしない。** 名前の近似発音は音声で未確認。全固定文の発音期待値、未対応の語義・同綴異音語、任意の名前、自由文の網羅性は残る。今回の限定規則に合わない既知語が、誤読を検出できず変換される可能性も残る。

次は固定文に出る語の期待値と、文脈規則の境界・否定例を増やす。音声生成、停止・再生成、Kiki / speed 0.8の維持、Androidのコールバック、APKの検査はまだ未実施。アプリ統合も実機確認用APK作成も行わない。

```bash
python tts-validation/validate_pronunciation.py --dictionary /path/to/pinned/cmudict.dict
python tts-validation/validate_clean_candidate.py --dictionary /path/to/pinned/cmudict.dict
```

前者は39項目通過でexit code 0、後者は総合的な統合未承認を示してexit code 1となる。前者だけで統合を承認してはならない。
