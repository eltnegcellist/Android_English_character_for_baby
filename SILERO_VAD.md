# Sileroによる発話開始・終了検出

設定の「話し終わりの判定」で「声で判定（Silero）」と「音量で判定（従来）」を切り替えます。初期値はSileroです。選択は端末内に保存し、次の会話から適用します。Androidでは会話を止めてから切り替え、Webでは切替時に会話を停止します。

Moonshine Japanese Smallのモデル・推論設定を変更せず、VADの開始・終了判定を追加します。Sileroの入力は推論専用のコピーです。録音へノイズ除去・音量補正・AGC/AECを追加せず、既存のASR向け音声変換を維持します。従来RMS VADの閾値・計算は変更していません。

## モデルと終了方針

- 公式Silero ONNX、MIT、2,327,524 bytes（約2.33 MB / 2.22 MiB）。
- 固定リビジョン `1e261b036686cd0017d500ee96acd1c4ba572a9d`。
- SHA-256 `1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3`。
- 16 kHz、512 samples/frame（32 ms）、64 samples context、recurrent stateを継続。
- 開始確率0.5、発話中0.35、開始確認160 ms、最小有声450 ms。
- 終了待ち1,100 ms。32 ms単位なのでモデル上の確定は最短1,120 ms。実際の返答には録音ブロック・推論・既存の約140 msの後続取得時間が加わります。
- 短い暫定発声は700 msで取り消し。音量低下だけで終了させません。
- 約0.55秒の開始前音声を保持します。発話中の確率判定が遅ければ、冒頭を取りこぼす可能性は残ります。

モデル準備時にSHA-256を検証します。準備できなければ従来RMSへ戻して通知します。推論途中でエラーになった場合は会話を停止し、設定から従来方式で再開できます。Sileroは話者識別ではないため、周囲の人の声やアナウンスも音声として検出することがあります。

## 検証範囲

提供された浴室10発話・静音10発話・背景音のみ2区間を、実ONNXモデルで再生しました。Web WASMとAndroid相当のJava ONNX Runtimeの双方で各発話の開始・終了を検出し、背景音2区間では開始しませんでした。Webで切り出したPCMは元波形の対応区間と一致し、Javaでも入力PCMが変更されないことを確認しました。これらは同じ録音に対する実装確認で、会話成功率・CERの改善率を測り直したものではありません。

発話境界は以前のベンチマークでモデルと波形から推定したものです。正確な聴取注釈ではなく、Silero由来の評価バイアスがあります。浴室の一部では推定開始より後から音声を保持するため、開始・終了イベントが出たことを全文認識の保証として扱いません。静音でのASR非劣性や全話者・全環境の安全性は未証明です。

既存ベンチマークでは同一PCMでもMoonshineの結果が変動しました。単回CERの僅差は採用根拠にしません。1.1秒は待ち時間比較から選んだ候補で、アプリ実装でCERが改善したと断定しません。Android実機のマイク・CPU負荷・画面オフ会話と、Safari/iOSなど各ブラウザでの確認は別途必要です。

個人の録音・分割WAV・モデル実行結果に含まれる会話音声はGitへ追加しません。ライセンスと固定モデルの由来はモデル横の `model-provenance.json` に保持しています。

## Android実装と実行

モデルはAPK内の `app/src/main/assets/vad/` に同梱します。既存の完全版ONNX Runtime Android 1.23.2を共有し、追加の推論runtimeをAPKへ入れません。AudioRecord/MIC・生PCM・リングバッファ・WAV生成・Lite/FullのASR経路を維持します。推論は録音workerで行い、TTS/応答処理中は停止・リセットします。設定は `emma_speech` の `endpoint_mode` に保存します。

```sh
python3 scripts/verify-silero-assets.py
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

ユニットテストには160 msの開始、自然な960 msの中休み、静かな声が続く確率系列、短い衝撃音、状態リセット、モデル破損拒否、実モデルでの無音推論を含みます。ホスト上の実推論用 `onnxruntime:1.23.2` は `testImplementation` のみです。

任意の非公開16 kHz mono PCM16 WAVフォルダを用意すると、同じ本番detectorで実録音も再生できます。背景音のみのファイル名は `noise_` で始めます。それ以外は1発話とします。

```sh
SILERO_TEST_WAV_DIR=/absolute/private/wavs gradle :app:testDebugUnitTest --rerun-tasks
```

Android版はv1.9.42（versionCode 125）。既存の公開v1.9.41タグ/APKは変更しません。
