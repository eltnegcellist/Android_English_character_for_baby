#!/usr/bin/env python3
"""Check that public Android copy matches the Lite-only callout UI.

This checks public Lite-only labels and enforces removal of hidden Full/Gemma options.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/eltnegcellist/emma"

CHECKS = {
    "AboutEmmaScreen.kt": {
        "required": ['title = "呼びかけ（公開版）"'],
        "forbidden": ['title = "「呼びかけ」と「会話」"', 'Liteでは「呼びかけ」、Fullでは'],
    },
    "ProductionUi.kt": {
        "required": [
            'Text("呼びかけを始める"',
            'Text("呼びかけを止める"',
            '2 / 3　ここから呼びかけを始めます',
            '「呼びかけを始める」を押してください。',
        ],
        "forbidden": ['「会話を始める」', 'ここから会話を始めます', '実験用エンジン（Lite / Full）', 'Gemmaを入手', 'Full専用：発話先'],
    },
    "FirstRunOnboarding.kt": {
        "required": ['準備が終わると、そのまま呼びかけを始められます。'],
        "forbidden": ['準備が終わると、そのまま会話を始められます。'],
    },
    "FeatureScreens.kt": {
        "required": [
            '画面オフ中の呼びかけ',
            '画面を閉じても呼びかけを続ける',
            '通知から呼びかけを止める',
        ],
        "forbidden": ['画面オフ中の会話', '通知から会話', '「会話を止める」'],
    },
    "ConversationService.kt": {
        "required": ['みつことばの呼びかけ中', '通知から呼びかけを止められます。'],
        "forbidden": ['みつことばの会話中', '「会話を止める」', '"会話を止める", stop'],
    },
    "ConversationController.kt": {
        "required": ['statusMessage = "呼びかけを止めました。"'],
        "forbidden": ['statusMessage = "会話を止めました。"', '画面オフ会話を開始できません'],
    },
}

errors = []
for filename, checks in CHECKS.items():
    source = (SOURCE / filename).read_text(encoding="utf-8")
    for phrase in checks["required"]:
        if phrase not in source:
            errors.append(f"{filename}: expected public label missing: {phrase}")
    for phrase in checks["forbidden"]:
        if phrase in source:
            errors.append(f"{filename}: obsolete public label found: {phrase}")

if errors:
    raise SystemExit("\n".join(errors))

print("Android public callout copy: OK")
