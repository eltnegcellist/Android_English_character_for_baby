#!/usr/bin/env python3
"""Synthetic-only regression tests for the offline Issue #101 auditor."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

path = Path(__file__).with_name("audit-asr-replay.py")
spec = importlib.util.spec_from_file_location("asr_audit", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def sample():
    rows = []
    for variant in module.VARIANTS:
        for name, text, transcript, interaction in (
            ("a.wav", "泣いてはいないよ",
             "泣いているよ" if variant == "jp13_boost3" else "泣いてはいないよ", "reassurance"),
            ("b.wav", "今日は晴れだね",
             "ミルクだね" if variant == "jp13_boost3" else "今日は晴れだね", None),
        ):
            requested = int(variant != module.BASELINE)
            rows.append({
                "file": name, "variant": variant, "expected": text, "transcript": transcript,
                "reference_chars": len(text), "char_errors": int(text != transcript),
                "elapsed_ms": 120, "keyterms_requested": requested, "keyterms_applied": requested,
                "keyterms_skipped": [], "ruri_topic": None, "ruri_interaction": interaction,
                "both_generic": interaction is None,
            })
    return {"status": "RESEARCH_ONLY_ANDROID_DEVICE_AUDIO_REPLAY",
            "model": "Moonshine Small", "ruri_available": True,
            "variants": list(module.VARIANTS), "results": rows}


class ReplayAuditTests(unittest.TestCase):
    def test_six_variants_semantics_and_privacy(self):
        gold = {
            "a.wav": {"human_verified": True, "expected_topic": None,
                      "expected_interaction": "reassurance", "forbidden_terms": ["泣いている"]},
            "b.wav": {"human_verified": True, "expected_topic": None,
                      "expected_interaction": None, "forbidden_terms": ["ミルク"]},
        }
        report = module.audit(sample(), gold)
        self.assertTrue(report["human_semantic_review_complete"])
        self.assertEqual(len(report["variants"]), 6)
        affected = next(x for x in report["variants"] if x["variant"] == "jp13_boost3")
        self.assertEqual(affected["potential_injection_clips"], 2)
        self.assertEqual(affected["paired_cer_worse"], 2)
        serialized = json.dumps(report, ensure_ascii=False)
        for secret in ("a.wav", "b.wav", "泣いているよ", "ミルクだね"):
            self.assertNotIn(secret, serialized)

    def test_duplicate_and_missing_variant_fail(self):
        doc = sample()
        doc["results"].append(doc["results"][0])
        with self.assertRaises(AssertionError):
            module.audit(doc)
        doc = sample()
        doc["results"] = [x for x in doc["results"] if x["variant"] != "jp13_boost2"]
        with self.assertRaises(AssertionError):
            module.audit(doc)

    def test_unverified_labels_are_not_scored(self):
        report = module.audit(sample(), {"a.wav": {"human_verified": False}})
        self.assertEqual(report["gold_verified_clips"], 0)
        self.assertFalse(report["human_semantic_review_complete"])
        self.assertIsNone(report["variants"][0]["ruri_joint_accuracy_pct"])

    def test_invalid_gold_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "annotations.json"
            path.write_text(json.dumps({"cases": [
                {"file": "unknown.wav", "human_verified": True,
                 "expected_topic": None, "expected_interaction": None}]}))
            with self.assertRaises(AssertionError):
                module.load_gold(path, {"a.wav"})


if __name__ == "__main__":
    unittest.main()
