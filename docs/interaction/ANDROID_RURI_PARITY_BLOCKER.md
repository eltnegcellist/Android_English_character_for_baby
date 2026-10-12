# Android Ruri Native ORT ↔ Web WASM 一致検証（解決済み）

更新: 2026-10-13 JST

**ステータス：CI上の推論一致問題は解消。** 過去の失敗と診断手順を残すため旧ファイル名 `ANDROID_RURI_PARITY_BLOCKER.md` を維持する。これにより自然発話への分類精度、Galaxy S25実機性能やKiki音声品質まで保証されるわけではない。

## 原因と修正

AndroidとJavaの`semantic-parity-host`で、ONNX Runtime `OrtSession.SessionOptions()` に `setIntraOpNumThreads(1)` / `setInterOpNumThreads(1)` を追加していたことが原因。

- Javaの比較ホストとAndroidアプリ本体の両方で、以前に一致テストが通過していた**標準のセッションスレッド設定**へ戻した。
- Web WASMの設定は変更せず、Ruri ONNX本体・tokenizer・Topic head・Interaction headも変更していない。
- 2026-10-13付 [Semantic Runtime Parity CI #38312365175](https://github.com/eltnegcellist/Android_English_character_for_baby/actions/runs/38312365175) **success**。
- CI実測：ケース最小embedding cosine **0.9967217882242312**（要求0.995以上）、分類確率の最大差 **0.01673420657010366**、margin最大差 **0.02573292865598789**、embedding絶対差最大 **0.01332462765276432**。
- 最初の「お風呂に入って温まろう」ケースではNative/Webのcosineが**0.9999999999999868**。Nativeベクトル冒頭`-0.051732212,-0.006853708,0.016817078`、Web冒頭`-0.051732227,-0.006853709,0.016817071`と一致。

## なぜ長く失敗していたか

スレッド設定を1に固定した場合、入力token IDs、ONNX SHA、attention maskが一致していても、ネイティブとWASMの出力の類似度が約**-0.1786**になることがあった。最適化OFFやONNX Runtimeバージョン合わせ（Java 1.30）では改善しなかった。過去に成功していたWebコミットを固定しても失敗が続いた。実際には、Java/Android側で追加したスレッド数変更が決定的だった。

参照: [失敗CI #38311137166](https://github.com/eltnegcellist/Android_English_character_for_baby/actions/runs/38311137166)、[失敗CI #38311393189](https://github.com/eltnegcellist/Android_English_character_for_baby/actions/runs/38311393189)、[成功CI #38312365175](https://github.com/eltnegcellist/Android_English_character_for_baby/actions/runs/38312365175)。

## なお残る精度確認

1. CIの対照はLinux版NativeとWeb WASM。**Galaxy S25のARM64実機**での実際のembedding、推論時間、メモリ・発熱・電池消費は未確認。標準スレッド設定へ戻した結果、実機負荷の再測定が必要。
2. 実モデルの二軸分類と英語選択について、Web実ONNXの17固定ケースは成功。追加で研究用の**67件の未審査・学習発話と完全一致しない探索的プローブ**を別途CIで実行する。既存477件の人工データによるInteraction test 64/76=84.21%は実利用の正解率を意味しない。
3. 端末内Moonshine日本語ASR、Ruri Topic/Interaction、333セットの英語、Kitten Kikiの音声出力まで連続確認は未実施。
4. 非同期会話の途中遮り、推論失敗時の汎用英文、Web/Androidの判定一致を実機で試す。
5. 日本語のキーワードや正規表現による話題・Interactionの判定へは戻さない。Ruriのみの判定を維持。

**公開判断：** CIでの推論一致問題は解消したが、自然発話・端末実機・英文の第三者レビューが残るためPR #38/#73は引き続きドラフト。デフォルトONや一般リリースは未承認。
