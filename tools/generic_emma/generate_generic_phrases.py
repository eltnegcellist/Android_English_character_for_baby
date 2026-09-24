#!/usr/bin/env python3
"""Generate scene-neutral Emma Lite generic fallback phrases with Gemma 4 E2B.

Development-time only. The generated strings are reviewed and baked into both
Android and Web Lite. Runtime Lite never invokes Gemma for generic replies.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

PROMPT_VERSION = "emma-lite-generic-v4-human-review-safe"

THEMES = {
    "greeting": "Natural warm greetings only: hello, hi, hello again, Emma is here. Do not ask the baby to speak or move.",
    "presence": "Natural shared-presence language only: Emma is here, here we are, together now, one little moment. Do not claim touch, emotion, safety, ownership, or closeness.",
    "looking": "Only invite visual attention: look around, look with me, take a look, look here, look there. Do not say that anything specific is visible.",
    "listening": "Only invite listening: listen with me, let's listen, pause and listen, listen here, listen there. Do not claim that any sound exists.",
    "curiosity": "Open curiosity only: I wonder, let's wonder, what comes next, let's find out. Do not refer to an unknown thing or discovery.",
    "pace": "Only invite an unhurried pace: take your time, no rush, nice and slow, here we go, one moment. Do not instruct walking, breathing, stopping, or bodily movement.",
    "vocal_play": "Use Emma's own neutral vocal play such as ooh-ooh, ahh-ahh, hey, hi, paired with greetings or neutral invitations. Do not claim an external sound, beat, or movement.",
    "attention": "Use abstract attention language only: here and there, near and far, now and next, look around, listen around. Do not attach these words to a concrete object or event.",
}

SYSTEM_PROMPT = """You write fixed offline baby-directed English lines for Emma Lite.

These are GENERIC FALLBACK replies used when Japanese ASR is incomplete, wrong,
or does not match a supported parenting scene. Emma therefore does NOT know what
the parent actually said. The reply must be pleasant and safe in almost any ordinary
moment.

STRICT RULES:
- English only.
- Every candidate has exactly 3 sentences.
- Each sentence has 2 to 4 spoken words.
- Total length is 6 to 12 spoken words.
- Use very common, easy-to-hear English.
- Speak directly and warmly to a baby.
- At most one question; usually use no question.
- Do not translate or paraphrase the unknown parent speech.
- Do not assume time of day, place, weather, object, color, action, emotion,
  physical state, need, success, danger, cause, or what the baby is doing.
- Do not assume feeding, sleep, bath, diaper, clothes, hugging, crying, smiling,
  body parts, tummy, play, outdoors, rain, sun, food, books, or music.
- Do not give medical, safety, or developmental advice.
- Do not use a baby's name. The app handles names separately.
- Make candidates meaningfully different.
- Prefer greetings, presence, looking invitations, listening invitations, open curiosity,
  slow pacing, vocal play, and abstract attention.
- NEVER say or imply that there is a thing, object, face, view, sky, light, sound, noise,
  beat, movement, color, person, toy, or event present.
- NEVER describe or evaluate the baby (for example sweet, cute, calm, good, happy,
  loved, mine, friend, clever, brave).
- NEVER ask the baby to talk, speak, say something, come somewhere, walk, step,
  breathe, stop, stay still, slow down, or perform any bodily action.
- NEVER use deictic placeholders like "this", "that", or "it" to imply an unknown referent.
- NEVER say "look at this", "look at that", "see this", "see that", "hear that",
  "listen to this", or similar phrases that presuppose something exists.
- Every sentence must be natural, grammatical spoken English. No fragments such as
  "Here is.", "I wonder about.", or "Here we are here.".
- Safe concepts include "Hello, little one.", "Emma is here.", "Look around.",
  "Look with me.", "Listen with me.", "Let's listen.", "I wonder.", "Let's wonder.",
  "Take your time.", "No rush.", "Here we go.", "Near and far.", "Here and there.",
  "Ooh, ooh!", and "Ahh, ahh!". Vary them naturally; do not merely copy examples.

OUTPUT FORMAT IS STRICT:
Return ONLY one JSON array.
Each candidate MUST itself be an array of exactly 3 strings.
Each string MUST be one short sentence with 2 to 4 spoken words.
Example shape only:
[["Hello, little one!","Look over here.","Emma is here."]]
Do not add markdown or commentary."""

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
    "thing", "things", "object", "objects", "face", "world", "sky", "view", "light",
    "sound", "sounds", "noise", "beat", "rhythm", "move", "moves", "moving",
    "bright", "dark", "big", "small", "soft", "loud", "quiet", "pretty", "nice",
    "good", "great", "fun", "sweet", "cute", "love", "loved", "friend", "friends",
    "mine", "close", "calm", "clever", "brave", "stay close", "look at this", "look at that",
    "see this", "see that", "hear that", "hear it", "listen to this", "you are", "it is",
    "there is", "talk", "speak", "say", "come", "walk", "step", "steps", "breathe",
    "breath", "stop", "still", "slow down", "keep going", "try", "hurry", "want",
    "hear", "see", "this", "that", "it", "they", "start",
}

def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("--base-url", default="http://127.0.0.1:8080/v1")
    p.add_argument("--model", default="gemma")
    p.add_argument("--per-theme", type=int, default=8)
    p.add_argument("--batch-size", type=int, default=8)
    p.add_argument("--temperature", type=float, default=0.92)
    p.add_argument("--output", type=Path, required=True)
    return p.parse_args()

def _ensure_sentence_punctuation(text: str) -> str:
    text = " ".join(text.strip().split())
    if text and text[-1] not in ".!?":
        text += "."
    return text

def _decode_first_json_array(raw: str):
    raw = raw.strip()
    if raw.startswith("```"):
        raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw, flags=re.I | re.S)
    left = raw.find("[")
    if left < 0:
        raise json.JSONDecodeError("No JSON array found", raw, 0)
    decoder = json.JSONDecoder()
    parsed, _ = decoder.raw_decode(raw[left:])
    return parsed

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
                f"Create {count} different candidates now. "
                "Remember: JSON array of candidate arrays; each candidate has exactly 3 short sentence strings."
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
    parsed = _decode_first_json_array(raw)
    if not isinstance(parsed, list):
        raise ValueError("Model output must be a JSON array")

    out: list[str] = []
    for item in parsed:
        if isinstance(item, list) and len(item) == 3 and all(isinstance(x, str) for x in item):
            sentences = [_ensure_sentence_punctuation(x) for x in item]
            out.append(" ".join(sentences))
        elif isinstance(item, str):
            # Tolerate a flat fallback and let validate() decide.
            out.append(" ".join(item.strip().split()))
    if not out:
        raise ValueError("Model output contained no usable candidates")
    return out

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
        if not 2 <= n <= 4:
            reasons.append(f"sentence_words={n}")
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

        while len(accepted) < args.per_theme and attempts < 40:
            attempts += 1
            remaining = args.per_theme - len(accepted)
            batch = max(args.batch_size, remaining)
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
                # Exact responses must never repeat. Openers may repeat a little,
                # but not enough to make generic fallback feel monotonous.
                if opener_counts.get(opener, 0) >= 4:
                    reasons.append("overused_opener")

                if reasons:
                    rejected.append({"text": text, "reasons": reasons})
                    continue

                global_seen.add(key)
                opener_counts[opener] = opener_counts.get(opener, 0) + 1
                accepted.append(text)
                if len(accepted) >= args.per_theme:
                    break

        output["themes"][theme_id] = {
            "direction": theme,
            "accepted": accepted,
            "rejected": rejected,
        }
        print(
            f"{theme_id}: accepted={len(accepted)} rejected={len(rejected)} attempts={attempts}",
            file=sys.stderr,
        )

        # Persist partial output so a failed workflow remains diagnosable.
        output["accepted_total"] = sum(
            len(item["accepted"]) for item in output["themes"].values()
        )
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(output, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

        if len(accepted) < args.per_theme:
            raise RuntimeError(
                f"{theme_id}: only {len(accepted)}/{args.per_theme} valid candidates"
            )

    output["accepted_total"] = sum(
        len(theme["accepted"]) for theme in output["themes"].values()
    )
    args.output.write_text(
        json.dumps(output, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(args.output)
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
