#!/usr/bin/env python3
"""Summarize local Android fixed-WAV Moonshine / Ruri replay, never upload private data."""
import argparse, json, math, statistics
from collections import defaultdict
from pathlib import Path

def main():
    p = argparse.ArgumentParser()
    p.add_argument("report", type=Path)
    p.add_argument("--output", type=Path)
    a = p.parse_args()
    document = json.loads(a.report.read_text())
    rows = document["results"]
    baseline = next((v for v in document["variants"] if v.startswith("as_is_")), None)
    assert baseline, "Baseline not found"
    reference = {row["file"] for row in rows if row["variant"] == baseline}
    groups = defaultdict(list)
    for row in rows:
        groups[row["variant"]].append(row)
    assert all({x["file"] for x in group} == reference for group in groups.values()), "Missing or duplicate WAV rows"
    assert all(len(group) == len(reference) for group in groups.values()), "Duplicate audio files in a group"
    summary = []
    for name, group in groups.items():
        chars = sum(x["reference_chars"] for x in group)
        errors = sum(x["char_errors"] for x in group)
        latencies = sorted(x["elapsed_ms"] for x in group)
        generic = [x["both_generic"] for x in group if isinstance(x.get("both_generic"), bool)]
        summary.append({
            "variant": name,
            "clips": len(group),
            "exact_pct": round(100 * sum(x["exact"] for x in group) / len(group), 2),
            "char_error_rate_pct": round(100 * errors / max(1, chars), 2),
            "both_generic_pct": round(100 * sum(generic) / len(generic), 2) if generic else None,
            "median_asr_ms": round(statistics.median(latencies), 1),
            "p95_asr_ms": latencies[max(0, math.ceil(0.95 * len(latencies)) - 1)]
        })
    summary.sort(key=lambda x: document["variants"].index(x["variant"]))
    print("Fixed-audio research only; requires adjudicated reference text, excludes microphone/VAD and Kiki.")
    print("variant | clips | exact% | CER% | both-generic% | median ASR ms | p95 ASR ms")
    for x in summary:
        print(" | ".join(str(x[key]) for key in (
            "variant", "clips", "exact_pct", "char_error_rate_pct",
            "both_generic_pct", "median_asr_ms", "p95_asr_ms"
        )))
    if a.output:
        a.output.write_text(json.dumps({
            "status": "RESEARCH_ONLY_FIXED_AUDIO",
            "source": str(a.report), "variants": summary
        }, ensure_ascii=False, indent=2) + "\n")

if __name__ == "__main__":
    main()
