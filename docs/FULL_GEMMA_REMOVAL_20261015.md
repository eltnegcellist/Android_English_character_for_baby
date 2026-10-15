# Android v1.9.48: Full（Gemma）廃止

Lite「呼びかけ」のみを維持。隠し開発者メニューも含めFull/会話を撤去し、Gemma推論、追加モデル準備、手動取り込み、LiteRT-LM依存を削除。

過去のFULL/PARENT保存設定はLite/BABYへ移行する。ファイルはアプリ私有領域の `models/gemma-4-E2B-it.litertlm` と .part / .download.part / .download.meta のみ削除し、履歴、Moonshine、Ruri、収録音声は保存する。旧FULL/GEMMA準備状態を解消する。旧WorkManagerジョブは不明な種別として失敗終了し、Gemmaを再取得しない。

## リリース前確認
- Android CI `gradle :app:testDebugUnitTest :app:assembleDebug`
- 旧Full端末からのアップデート、Gemma保存ファイル除去、Lite設定、ASR/Ruri/録音音声/履歴保持
- 呼びかけ、画面オフ再生、履歴再生、Semantic診断を実機で回帰検証
