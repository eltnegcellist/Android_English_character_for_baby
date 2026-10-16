#!/usr/bin/env python3
"""Offline, aggregate-only Issue #101 audit. NEVER upload inputs or gold to public CI."""
import argparse
import json
import math
import statistics
import unicodedata
from collections import defaultdict
from pathlib import Path

VARIANTS = ("as_is_6p5_boost2", "jp13_no_keyterms", "jp13_boost1",
            "jp13_boost2", "jp13_boost3", "jp13_boost2_new_terms")
BASELINE = "jp13_no_keyterms"


def norm(s):
    return "".join(unicodedata.normalize("NFKC", s).split())


def percentile95(values):
    return sorted(values)[math.ceil(len(values) * .95) - 1]


def load_gold(path, files):
    if path is None:
        return {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    assert isinstance(raw["cases"], list)
    gold = {}
    for entry in raw["cases"]:
        name = entry["file"]
        assert name in files and name not in gold, "Unknown or duplicate gold WAV"
        assert type(entry.get("human_verified")) is bool, "Explicit human_verified required"
        forbidden = entry.get("forbidden_terms", [])
        assert isinstance(forbidden, list) and all(isinstance(t, str) and t.strip() for t in forbidden)
        if entry["human_verified"]:
            assert "expected_topic" in entry and "expected_interaction" in entry
        gold[name] = entry
    return gold


def audit(doc, gold=None):
    assert doc.get("status") == "RESEARCH_ONLY_ANDROID_DEVICE_AUDIO_REPLAY"
    assert doc.get("model"), "Model name missing"
    assert len(doc["variants"]) == 6 and set(doc["variants"]) == set(VARIANTS), "Six variants missing"
    grouped = defaultdict(dict)
    for row in doc["results"]:
        name, v = row["file"], row["variant"]
        assert v in VARIANTS and name not in grouped[v], "Unexpected variant or duplicate WAV"
        assert row["reference_chars"] > 0 and row["char_errors"] >= 0 and row["elapsed_ms"] >= 0
        assert 0 <= row["keyterms_applied"] <= row["keyterms_requested"]
        assert len(row["keyterms_skipped"]) == row["keyterms_requested"] - row["keyterms_applied"]
        grouped[v][name] = row
    files = set(grouped[BASELINE])
    assert files and all(set(grouped[v]) == files for v in VARIANTS), "Inconsistent WAV coverage"
    for name in files:
        assert len({grouped[v][name]["expected"] for v in VARIANTS}) == 1, "Inconsistent references"
        assert len({grouped[v][name]["reference_chars"] for v in VARIANTS}) == 1
    gold = gold or {}
    variants = []
    for v in VARIANTS:
        entries = [grouped[v][name] for name in sorted(files)]
        baseline = grouped[BASELINE]
        chars = sum(x["reference_chars"] for x in entries)
        ms = [x["elapsed_ms"] for x in entries]
        g = [x.get("both_generic") for x in entries]
        verified = [x for x in entries if gold.get(x["file"], {}).get("human_verified")]
        def accuracy(label, expected):
            if not verified or not doc.get("ruri_available"):
                return None
            return round(100 * sum(x.get(label) == gold[x["file"]][expected] for x in verified) / len(verified), 2)
        possible = 0
        for x in verified:
            record = gold[x["file"]]
            source, observed = norm(x["expected"]), norm(x["transcript"])
            if any(norm(term) in observed and norm(term) not in source for term in record.get("forbidden_terms", [])):
                possible += 1
        counts = {(x["keyterms_requested"], x["keyterms_applied"], len(x["keyterms_skipped"])) for x in entries}
        assert len(counts) == 1, "Keyterm activation changed within variant"
        requested, applied, skipped = counts.pop()
        variants.append({
            "variant": v, "clips": len(entries), "keyterms_requested": requested,
            "keyterms_applied": applied, "keyterms_skipped_count": skipped,
            "cer_pct": round(100 * sum(x["char_errors"] for x in entries) / chars, 2),
            "paired_cer_better": sum(x["char_errors"] < baseline[x["file"]]["char_errors"] for x in entries),
            "paired_cer_worse": sum(x["char_errors"] > baseline[x["file"]]["char_errors"] for x in entries),
            "median_asr_ms": round(statistics.median(ms), 1),
            "p95_asr_ms": round(percentile95(ms), 1),
            "both_generic_pct": round(100 * sum(g) / len(g), 2) if all(type(x) is bool for x in g) else None,
            "gold_cases": len(verified),
            "ruri_topic_accuracy_pct": accuracy("ruri_topic", "expected_topic"),
            "ruri_interaction_accuracy_pct": accuracy("ruri_interaction", "expected_interaction"),
            "ruri_joint_accuracy_pct": (
                round(100 * sum(x.get("ruri_topic") == gold[x["file"]]["expected_topic"] and
                                x.get("ruri_interaction") == gold[x["file"]]["expected_interaction"]
                                for x in verified) / len(verified), 2)
                if verified and doc.get("ruri_available") else None),
            "potential_injection_clips": possible,
        })
    return {
        "status": "RESEARCH_ONLY_NOT_A_RELEASE_APPROVAL",
        "privacy": "AGGREGATE_ONLY_NO_WAV_NAMES_TRANSCRIPTS_OR_GOLD",
        "model": doc["model"], "clips": len(files),
        "gold_verified_clips": sum(bool(gold.get(f, {}).get("human_verified")) for f in files),
        "human_semantic_review_complete": len(gold) == len(files) and all(gold[f].get("human_verified") for f in files),
        "ruri_available": bool(doc.get("ruri_available")),
        "warning": "Possible lexical intrusions require human judgment. Fixed WAV excludes microphone, VAD and audible playback. No automatic release approval.",
        "variants": variants,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path, help="PRIVATE Android results.json")
    parser.add_argument("--gold", type=Path, help="PRIVATE human verified labels JSON")
    parser.add_argument("--output", type=Path, help="aggregates only; review for privacy before publishing")
    args = parser.parse_args()
    doc = json.loads(args.report.read_text(encoding="utf-8"))
    gold = load_gold(args.gold, {x["file"] for x in doc["results"]})
    result = audit(doc, gold)
    print("RESEARCH ONLY; fixed audio without microphone, VAD or audible latency.")
    print("variant | applied/requested | CER% | paired better/worse | generic% | median/p95 ASR ms | potential injections")
    for x in result["variants"]:
        print(f'{x["variant"]} | {x["keyterms_applied"]}/{x["keyterms_requested"]} | {x["cer_pct"]} | '
              f'{x["paired_cer_better"]}/{x["paired_cer_worse"]} | {x["both_generic_pct"]} | '
              f'{x["median_asr_ms"]}/{x["p95_asr_ms"]} | {x["potential_injection_clips"]}')
    if args.output:
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
