# Androidの共通話題データ

編集元は[Webの共通データ](https://github.com/eltnegcellist/Web_EmmaLocal_English_for_babies/tree/main/shared)です。このフォルダのJSONと生成スクリプトをAndroid側だけで変更しません。

`lite-topic-source.json`に、取り込んだWebのコミットSHAとファイルのSHA-256を記録します。通常のAndroid CIは固定版との一致を確認するため、Webの後続変更によって過去版の再ビルドが壊れません。日次の`Shared topic contract drift`は最新Webとのずれを検出します。

## 取り込み・検証

```bash
# 検証済みWebコミットを指定（完全な40文字SHA）
python3 scripts/sync-lite-topic-contract.py --ref <SHA>
# 固定した原本との一致と生成物を確認
python3 scripts/sync-lite-topic-contract.py --check
python3 scripts/generate-lite-topic-contract.py --target android --check
python3 scripts/test-lite-topic-contract.py
gradle :app:testDebugUnitTest
# 最新Webとのずれだけを確認（ファイルは変更しない）
python3 scripts/sync-lite-topic-contract.py --check-latest
```

`--ref`なしの取り込みは実行時のWeb mainを解決し、そのコミットに固定します。生成ガイドはアプリ内に同梱され、実行時にWebへアクセスしません。判定テスト用Kotlinも同じJSONから生成し、新しいJSON依存をアプリに追加しません。

`LiteWebTopicParityTest`は共通の単発発話と会話列を検証し、`LiteTopicGuideTest`は表示例と判定を照合します。失敗した場合は、期待値を上書きする前に仕様と実装を確認してください。

## English

Edit the canonical Web contract, then adopt its full commit SHA using the sync command. Android stores source hashes, generates native offline help and Kotlin test fixtures, and verifies the pinned copy in normal CI. Daily CI checks the latest upstream data without modifying files. Runtime help remains bundled and requires no network or new JSON runtime dependency.
