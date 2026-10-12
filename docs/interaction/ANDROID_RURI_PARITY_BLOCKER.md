# Android Ruri Native ORT ↔ Web WASM 埋め込み不一致 — リリース阻止項目

記録: 2026-10-13 JST。対象：Web [PR #73](https://github.com/eltnegcellist/Web_EmmaLocal_English_for_babies/pull/73) と Android [PR #38](https://github.com/eltnegcellist/Android_English_character_for_baby/pull/38)。

## 結論

- **Android CIのビルド成功と、Ruri Web・Androidで同じ英文を選ぶことは別問題。**
- Webの実Ruri ONNX + Interaction推論は通っているが、Java Native ORT（CIのLinux x86_64）とWeb WASMの`sentence_embedding`が大きく異なる。
- AndroidのTopic headとInteraction headの両方は埋め込みを入力とするため、この不一致を解消またはAndroid実機での再現有無と精度を確認するまで、**「AndroidでもWebと同等に分類する」「実機検証済み」「一般向けにON」等と宣言しない**。
- キーワード・ルールベースへのフォールバックはユーザー方針に反するので設けない。推論が失敗した場合は既存の汎用英文への退避を維持。

## 再現方法

GitHub Actions `.github/workflows/semantic-runtime-parity.yml` が Android Java Native ONNX Runtime と Webの同じ固定モデルの同じ日本語入力を比較する。

1. Android PR #38のCI `Semantic Runtime Parity` を実行。
2. 同PRのWeb counterpart `feature/interaction-prd-web-integration-20261012` から、ONNX/model/tokenizer/head とWeb WASMハーネスを取得。
3. Web `node scripts/semantic-parity-web.mjs --output .semantic-parity/web.json` の各ケースと、Java `semantic-parity-host` の同じケースを比較。
4. **トークンIDは一致**（Tokenizer mismatch の失敗なし）。Ruri `model.onnx` の実際のSHA-256は `bd500193003fdeaba8c5422a47b974b40b49a5e0c91285c76f6bd949d2205264`、サイズ71,248,404 bytes。
5. ケース「お風呂に入って温まろう」: token IDs `[1,8313,9324,9544,1894,7843,2]`。
   - Native Java: `[-0.00622698,0.006268648,0.018591568,-0.019465618,-0.007331575,0.10198786,-0.003009723,-0.019678691]`
   - Web WASM: `[-0.051732227,-0.0068537095,0.016817071,-0.014050257,0.026352253,0.02758273,0.00039346,0.015506712]`
   - 両者のノルムは約1.0だが、**コサイン類似度は約-0.1786**。以前の閾値0.995を大きく下回る。他のmilk,sleep,diaper等でも約-0.17〜-0.24の異常。
6. 同一のWeb ONNX/辞書/Topic head/parity script/WASMのGit blob SHAは、**過去に成功したWebコミット** `674d2ee964b3b5ecac3cd643b78b3e13d862b2fb` と実験中のWeb PR #73で一致。成功実績 [Actions #38218072919](https://github.com/eltnegcellist/Android_English_character_for_baby/actions/runs/38218072919) では最小cosine約0.9967。
7. 最近の失敗例: [#38250390148](https://github.com/eltnegcellist/Android_English_character_for_baby/actions/runs/38250390148)、[#38311137166](https://github.com/eltnegcellist/Android_English_character_for_baby/actions/runs/38311137166)。
8. 変えた条件：Webの成功コミット固定→**改善なし**、Javaセッション内/外スレッドを1に設定→**改善なし**、Javaグラフ最適化OFF + deterministicCompute true→**改善なし**、Java ORT 1.23.2から1.30.0に変更→**改善なし**（最初のケースcosine約-0.1785）。
9. 実験的なWeb ref固定・Java ORT 1.30.0・NO_OPTは**元の設定に戻した**。`PARITY_DEBUG`（ハッシュと8次元ログ出力）は再現調査のため残した。

**まだ原因は特定できていない。** 数値カーネル／SIMD／量子化演算／CPUホスト差等は候補であり、確定ではない。Android ARM64実機が同じ結果かどうかは未確認。

## Codex / Mac へ引き継ぐ次の検証

1. CIの失敗した時と成功した時で物理CPU種類・AVX/SIMDフラグ・ORTの演算カーネル選択の違いを採取。Android実機ARM64とMacの両方で同じテキストを推論し、Web WASMを同時測定。
2. Native ORTのモデル入力全ID・Attention Mask・出力形状・出力名（`sentence_embedding`）をdumpし、Webとbyte-level比較。誤ったtensor buffer解釈・メモリ再利用を排除する。
3. 量子化演算による不一致を、非量子化小規模モデル/テスト用サブグラフやORTプロファイリングで分離。Native 1.23.2/1.30.0双方で再現した事実を利用。
4. 実機で問題が再現する場合、**Androidだけ別の埋め込みに既存Web学習ヘッドを流用しない**。同一Ruri入力ベクトルに修復するのが優先。修復不可の場合はAndroid向けTopicとInteractionの両ヘッドを端末側埋め込みから再学習・検証する別設計を検討し、Webと英語出力の意味一致テストを用意。
5. 実行環境別の小規模実測だけで一般公開を判断せず、リアル音声（Moonshine ASR）→Ruri2軸→英語選択→Kiki再生まで連続で確認。

## 実装済み・成功している部分

- Web: 111組×3セット＝333個の完成済みTopic×Interaction短英文を保持、14分類112セット・従来Topic197セットを維持。Web実ONNXの17基準発話をCIでassert。Web CI成功。
- Android: 同じ333 +112+197の英文を同期し、Android CIでビルド・ユニットテスト通過済み。**アプリのCI成功は実機推論の精度を意味しない**。
- 日本語のキーワード・regexに基づくトピック／Interactionの判定は実行時に使用せず、Ruriの確率閾値（Topic 0.5 + margin0.05、Interaction 0.5）だけを使用。
- 両PRはドラフトのまま。リリースはPR-Fの実機評価まで保留。
