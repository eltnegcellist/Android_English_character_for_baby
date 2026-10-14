# Moonshine育児語認識 × Ruri generic率・実機比較（研究専用）

2026-10-14。通常の公開APK・Ruri分類閾値・Topic/Interaction分類数には変更を加えない。

## 実験の内容
Androidの別androidTest APKにある MoonshineAsrBiasReplayTest は、同じ実音声を6条件で繰り返し認識する。
(1) 現状相当 6.5 tokens/s, boost2, 従来の30語
(2) 13.0 tokens/s, boost2, 30語
(3) 13.0 tokens/s, keytermsなし
(4) 13.0 tokens/s, boost1, 30語
(5) 13.0 tokens/s, boost3, 30語
(6) 13.0 tokens/s, boost2, 30語＋しゃっくり・ご機嫌・くしゃみ・あくび

参照テキストを人が書き起こす。同じ16kHz/モノラル/PCM16音声をすべての条件で再生し、文字誤り、全文一致、Ruriの採用TopicとInteraction、両方genericの割合、ASR時間を測る。Ruri未導入ならASRのみ測る。

## Galaxy S25 / Macの実行
研究ブランチ research/moonshine-ja-bias-replay-20261014 をMac上でチェックアウトし、Galaxy S25をUSB接続する。先にアプリでMoonshine SmallとRuriのモデルを準備する（Ruriは任意）。端末内の育児に関する録音と対照例を、1ファイルずつ実際の発話どおりに書き起こす。

音声を16kHz PCM16 WAVに変換する例:

    ffmpeg -i raw.m4a -ac 1 -ar 16000 -c:a pcm_s16le clip01.wav
    printf '%s\n' 'ミルクを飲もうね' > clip01.txt

コマンド例:

    gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    adb shell run-as com.eltnegcellist.emma mkdir -p files/asr-bias-lab/inputs
    adb shell -T "run-as com.eltnegcellist.emma sh -c 'cat > files/asr-bias-lab/inputs/clip01.wav'" < clip01.wav
    adb shell -T "run-as com.eltnegcellist.emma sh -c 'cat > files/asr-bias-lab/inputs/clip01.txt'" < clip01.txt
    adb shell am instrument -w -e class com.eltnegcellist.emma.asr.MoonshineAsrBiasReplayTest com.eltnegcellist.emma.test/androidx.test.runner.AndroidJUnitRunner
    adb shell run-as com.eltnegcellist.emma cat files/asr-bias-lab/results.json > asr-results.json
    python3 scripts/summarize-asr-replay.py asr-results.json --output asr-summary.json

端末でのadb操作・実測はまだ未実施。APKの署名が違う場合は既存アプリを無理に上書きせず、バックアップ後に実施すること。音声と文字起こしはプライベートな内容なのでGitHubやCIにアップロードしない。

## Ruri候補の再評価
Web研究ブランチ research/ruri-generic-rescue-sweep-20261014 では、実際のONNX Ruriを既存の67件の探索的QA＋23件の追加日常表現に通す。既存の両軸genericケースだけ採用基準を変える条件比較を行う。合格前に一般公開の閾値は変更しない。
探索的な文章は人手審査済みの独立goldではなく、実音声の誤りや実用品質の断定には使えない。

## 判定
両軸generic率の改善だけでは採用しない。ミルク誤挿入、しゃっくり誤分類、否定文の意味反転、ASR誤り、Kiki読み上げ内容などの悪化を先に確認し、同じ録音と参照テキストで比較する。