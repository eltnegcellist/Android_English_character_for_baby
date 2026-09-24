#!/usr/bin/env python3
"""Generate scene-neutral Emma Lite generic fallback phrases with Gemma 4 E2B.

This runs only during development. The resulting strings are reviewed and then baked
into both Android and Web Lite. Runtime Lite never invokes Gemma for generic replies.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

PROMPT_VERSION = "emma-lite-generic-v1-scene-neutral-compact"

THEMES = {
    "greeting": "A warm, varied greeting to the baby without assuming time of day, mood, activity, or location.",
    "together": "A calm sense that Emma and the baby share this moment, without claiming touch, safety, feelings, or needs.",
    "looking": "Invite the baby to look around or notice something visually, without naming any object, color, person, or place.",
    "listening": "Invite the baby to listen or notice sounds, without claiming what sound exists or naming a source.",
    "curiosity": "Express gentle curiosity and wonder about the present moment without inventing facts or asking for a verbal answer.",
    "encouragement": "Gentle, non-evaluative encouragement to notice, take time, or stay curious; do not praise achievements that may not have happened.",
    "rhythm": "Use simple neutral sound-play or rhythm such as tap/pause/soft syllables, without claiming the baby is moving or an object is making sound.",
    "attention": "Gently vary attention between here/there, near/far, now/next, without assuming a concrete scene.",
}

SYSTEM_PROMPT = """You write fixed offline baby-directed English lines for Emma Lite.

These are GENERIC FALLBACK replies. They are used when Japanese ASR is incomplete,
wrong, or does not match a supported parenting scene. Therefore the line must still
feel pleasant even when Emma does not actually know what the parent said.

STRICT STYLE:
- English only.
- Exactly 3 very short complete sentences.
- 6 to 12 spoken words total.
- No sentence may exceed 4 spoken words.
- At most one question; usually no question.
- Speak directly and warmly to a baby.
- Use very common, easy-to-hear words.
- Keep Emma lively, warm, and varied.
- Rhythm, repetition, small sound-play, looking, listening, and curiosity are welcome.
- Do NOT translate or paraphrase the parent's unknown speech.
- Do NOT assume time of day, place, weather, object, color, action, emotion, physical state, need, success, danger, or cause.
- Do NOT assume feeding, sleep, bathing, diapering, clothes, hugging, crying, smiling, hands, feet, tummy, play, outdoors, rain, sun, food, books, or music.
- Do NOT give medical, safety, or developmental advice.
- Do NOT use the baby's name; the app inserts a configured name separately.
- Avoid boilerplate repetition. Make each candidate meaningfully different.
- Avoid repeatedly beginning with the same 2-3 words.

Return ONLY a JSON array of strings. No markdown and no commentary."""

WORD_RE = re.compile(r"[A-Za-z]+(?:['’][A-Za-z]+)?")
SENTENCE_RE = re.compile(r"(?<=[.!?])\s+")
JAPANESE_RE = re.compile(r"[\u3040-\u30ff\u3400-\u9fff]")

BANNED = {
    "bath", "shower", "water", "milk", "bottle", "feed", "feeding", "drink",
    "sleep", "sleepy", "bed", "night", "morning", "diaper", "nappy", "pee", "poop",
    "clothes", "dress", "sock", "hat", "hug", "cuddle", "snuggle", "cry", "crying",
    "tear", "smile", "smiling", "hand", "hands", "finger", "fingers", "foot", "feet",
    "toe", "toes", "tummy", "belly", "burp", "play", "toy", "outside", "walk",
    "rain", "sun", "sunshine", "food", "eat", "meal", "bite", "book", "page",
    "story", "music", "song", "sing", "hungry", "tired", "cold", "warm", "hot",
    "happy", "sad", "safe", "okay", "fine", "good job", "well done",
}

def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("--base-url", default="http://127.0.0.1:8080/v1")
    p.add_argument("--model", default="gemma")
    p.add_argument("--per-theme", type=int, default=10)
    p.add_argument("--batch-size", type=int, default=10)
    p.add_argument("--temperature", type=float, default=0.95)
    p.add_argument("--output", type=Path, required=True)
    return p.parse_args()

def chat_completion(base_url: str, model: str, theme_id: str, theme: str,
                    count: int, temperature: float) -> list[str]:
    body = {
        "model": model,
        "temperature": temperature,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": (
                f"Generic subtheme: {theme_id}\n"
                f"Direction: {theme}\n"
                f"Create {count} meaningfully different generic Emma Lite candidates."
            )},
        ],
    }
    req = urllib.request.Request(
        base_url.rstrip("/") + "/chat/completions",
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Gemma endpoint request failed: {exc}") from exc

    raw = payload["choices"][0]["message"]["content"].strip()
    if raw.startswith("```"):
        raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw, flags=re.I | re.S)
    left, right = raw.find("["), raw.rfind("]")
    if left >= 0 and right > left:
        raw = raw[left:right + 1]
    parsed = json.loads(raw)
    if not isinstance(parsed, list) or not all(isinstance(x, str) for x in parsed):
        raise ValueError("Model output must be a JSON array of strings")
    return parsed

def validate(text: str) -> list[str]:
    reasons: list[str] = []
    cleaned = " ".join(text.strip().split())
    sentences = [x.strip() for x in SENTENCE_RE.split(cleaned) if x.strip()]
    words = WORD_RE.findall(cleaned)

    if JAPANESE_RE.search(cleaned):
        reasons.append("contains_japanese")
    if len(sentences) != 3:
        reasons.append(f"sentence_count={len(sentences)}")
    if not 6 <= len(words) <= 12:
        reasons.append(f"word_count={len(words)}")
    for sentence in sentences:
        n = len(WORD_RE.findall(sentence))
        if n > 4:
            reasons.append(f"sentence_too_long={n}")
            break
    if cleaned.count("?") > 1:
        reasons.append("too_many_questions")

    lower = cleaned.casefold()
    for banned in BANNED:
        if " " in banned:
            if banned in lower:
                reasons.append(f"banned={banned}")
                break
        elif re.search(rf"\b{re.escape(banned)}\b", lower):
            reasons.append(f"banned={banned}")
            break
    return reasons

def opener_key(text: str) -> str:
    first = SENTENCE_RE.split(text.strip())[0] if text.strip() else ""
    return " ".join(WORD_RE.findall(first.casefold())[:3])

def main() -> int:
    args = parse_args()
    output = {
        "prompt_version": PROMPT_VERSION,
        "generator_model": args.model,
        "review_note": "Gemma-generated candidates; review before product inclusion.",
        "themes": {},
    }
    global_seen: set[str] = set()
    opener_counts: dict[str, int] = {}

    for theme_id, theme in THEMES.items():
        accepted: list[str] = []
        rejected: list[dict[str, object]] = []
        attempts = 0
        while len(accepted) < args.per_theme and attempts < 16:
            attempts += 1
            batch = max(args.batch_size, args.per_theme - len(accepted))
            try:
                candidates = chat_completion(
                    args.base_url, args.model, theme_id, theme, batch, args.temperature
                )
            except (ValueError, KeyError, json.JSONDecodeError, RuntimeError) as exc:
                print(f"{theme_id}: attempt {attempts} failed: {exc}", file=sys.stderr)
                continue

            for candidate in candidates:
                text = " ".join(candidate.strip().split())
                key = text.casefold()
                if key in global_seen:
                    continue
                reasons = validate(text)
                opener = opener_key(text)
                if opener_counts.get(opener, 0) >= 2:
                    reasons.append("overused_opener")
                if reasons:
                    rejected.append({"text": text, "reasons": reasons})
                    continue
                global_seen.add(key)
                opener_counts[opener] = opener_counts.get(opener, 0) + 1
                accepted.append(text)
                if len(accepted) >= args.per_theme:
                    break

        if len(accepted) < args.per_theme:
            raise RuntimeError(
                f"{theme_id}: only {len(accepted)}/{args.per_theme} valid candidates"
            )
        output["themes"][theme_id] = {
            "direction": theme,
            "accepted": accepted,
            "rejected": rejected,
        }
        print(f"{theme_id}: accepted={len(accepted)} rejected={len(rejected)}", file=sys.stderr)

    output["accepted_total"] = sum(
        len(theme["accepted"]) for theme in output["themes"].values()
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(output, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(args.output)
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
