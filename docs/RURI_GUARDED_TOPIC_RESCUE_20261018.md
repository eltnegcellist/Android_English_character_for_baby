# Ruri Topic限定救済の採用実装

ユーザーが2026年10月18日に採用を決定した条件を、Live Liteの返答選択へ反映する。

1. 通常はTopic確率50%以上・候補差5ポイント以上、Interaction確率50%以上。
2. この判定で両軸とも不採用になった場合だけ、Topic確率30%以上・候補差10ポイント以上でTopicを採用。
3. Interactionを救済しない。既に採用されたTopic/Interactionを変更しない。
4. Interactionが欠落・無効化されている場合、最上位Topicがgenericの場合、不正スコアの場合は救済しない。

`LiteResponseEngine.respondRuri`だけで採否を選び、モデル・22/15分類・ヘッド・生確率は維持。新しい採用理由は `topic_rescued_both_generic`。日本語の文字列を分類条件には使わず、英文/音源/ASRは変更しない。

Webの対になるPRは https://github.com/eltnegcellist/Web_EmmaLocal_English_for_babies/pull/109 。Web研究ブランチに、日本語報告と実ONNX414文のWeb/Mac Native検証結果を保存する。

## 検証と限界

- 境界30%・候補差10ptの包含、閾値直下の棄却、通常50%・差5ptの維持、Interaction50%の維持、欠落/無効/NaN、実際の睡眠言い換えスコアと診断理由を5件のJVMテストで検証。
- Mac上でAndroid由来の既存44件＋新規5件が合格。実ONNX414文について、実返答エンジンが承認された研究規則に全件一致することを確認。
- Webとの差はモデルの実行環境によって残る。新規202文ではTopic全件一致。全414文ではTopic4件、初回英文6件の差を記録。救済規則そのものの相違ではなく、生スコアの数値差によるもの。
- 「ママが寝不足だね」からcryを拾う、笑顔の否定からsmileを拾う等の既知の意味誤りを修正したとは扱わない。ユーザーによる方式採用と、各合成文の人手意味審査は別。期待ラベルと意味レビューは未承認のまま。
- Galaxy S25、実録音、Moonshine認識後の文字列、連続応答の全バリエーションは未測定。

mainへのマージと公開はこの変更には含めない。
