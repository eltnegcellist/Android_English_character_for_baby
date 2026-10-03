# みつことば Android — 開発と過去の実験の扱い

更新日：2026-10-03

## 現在の開発元と配布版

- `main`：現在の開発元。現行仕様はREADMEとソースコードを参照します。
- `mitsukotoba-v*`タグ：各配布版を再現する基準。公開済みのタグとAPKを別の内容で置き換えません。
- 作業ブランチ：個別の修正・検証用。ブランチ名や古いPRの説明を現行仕様とみなしません。

現在の安定配布版は[README](README.md#安定版)に記載します。文書だけの更新ではアプリのバージョンや既存APKを変更しません。

## 過去の実験PR

以下は後続の実装に置き換わった開発記録です。現行mainへマージする候補として扱いません。

| PR | 当時の目的 | 現在の扱い |
| --- | --- | --- |
| [#2](https://github.com/eltnegcellist/Android_English_character_for_baby/pull/2) | Supertonic F3のクラッシュ診断 | 現行TTSはKitten。診断書き出しも現行コードにあるため、旧Supertonic固有の修正は不要。 |
| [#5](https://github.com/eltnegcellist/Android_English_character_for_baby/pull/5) | KittenをONNX Runtimeで直接実行する実験 | v1.9.19以降の正式実装に置き換わった。旧PRのビルド設定は採用しない。 |
| [#6](https://github.com/eltnegcellist/Android_English_character_for_baby/pull/6) | 同実験をv1.9.1へ載せ直す | 同じくv1.9.19以降に置き換わった。古い実機確認待ちの記述を現在の未完了タスクとみなさない。 |

PRの元の説明・差分・作業ブランチは履歴として保持します。後続版に置き換わったPRは、その理由を本文に記録して閉じます。

## 文書の読み分け

- [README.md](README.md)：現在の使い方、構成、配布版、ビルド方法。
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)：現在使う第三者コンポーネントと帰属。
- [LICENSE_AUDIT.md](LICENSE_AUDIT.md)：旧eSpeak/sherpa経路からv1.9.19以降への移行と監査。旧経路の記載は履歴であり、現在の導入手順ではありません。
- 話題判定の説明：アプリ内の設定から開く同梱ガイド。例文は`LiteTopicGuideTest`で実装と照合します。

依存関係や判定例を変更する際は、対応する説明とテストも更新します。実験PRの本文をコピーして現行READMEを上書きしません。

## English

`main` is the current development source. Published `mitsukotoba-v*` tags and APKs remain immutable release references. Documentation-only updates do not change the application version or replace release assets.

PRs #2, #5, and #6 are historical experiments superseded by the current implementation. Keep their original descriptions, diffs, and branches for reference; close them with an explanation rather than merging obsolete code. The README describes current behavior, THIRD_PARTY_NOTICES lists current components, and LICENSE_AUDIT distinguishes the former TTS path from the v1.9.19+ implementation.
