#!/usr/bin/env python3
"""Independent pronunciation contrasts; unknown or wrong readings are failures.

Expected American-English segments are authored fixtures, not captured candidate
output. Passing these examples does not qualify arbitrary English or audio.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'candidates'))
from validate_g2p import NoEspeak
sys.meta_path.insert(0, NoEspeak())
from clean_frontend import kitten_ipa

# Exact word IPA after semantic symbol expansion. This engine convention puts
# stress immediately before the stressed vowel, rather than the syllable onset.
CASES = [
 ('Please close the door.', 'close', 'klˈoʊz'),
 ('Stay close to me.', 'close', 'klˈoʊs'),
 ('We live here.', 'live', 'lˈɪv'),
 ('This is live music.', 'live', 'lˈaɪv'),
 ('I will present the gift.', 'present', 'pɹizˈɛnt'),
 ('This present is for you.', 'present', 'pɹˈɛzənt'),
 ('Please record your voice.', 'record', 'ɹəkˈɔɹd'),
 ('This record sounds lovely.', 'record', 'ɹˈɛkəɹd'),
 ('Please lead the way.', 'lead', 'lˈid'),
 ('The pipe contains lead.', 'lead', 'lˈɛd'),
 ('I object to that.', 'object', 'əbdʒˈɛkt'),
 ('Look at this object.', 'object', 'ˈɑbdʒɛkt'),
 ('I am content.', 'content', 'kəntˈɛnt'),
 ('The content is helpful.', 'content', 'kˈɑntɛnt'),
 ('She does the work.', 'does', 'dˈʌz'),
 ('The does graze in the field.', 'does', 'dˈoʊz'),
 ('I use a spoon.', 'use', 'jˈuz'),
 ('This spoon has a use.', 'use', 'jˈus'),
 ('I read every night.', 'read', 'ɹˈid'),
 ('Yesterday I read the book.', 'read', 'ɹˈɛd'),
 ('The wind blows.', 'wind', 'wˈɪnd'),
 ('Please wind the toy.', 'wind', 'wˈaɪnd'),
 ('A tear is falling.', 'tear', 'tˈɪɹ'),
 ('Do not tear the page.', 'tear', 'tˈɛɹ'),
 ('She took the lead.', 'lead', 'lˈid'),
 ('Little hands! Open, close. Wiggle, wiggle!', 'close', 'klˈoʊz'),
 ('Hands, hands! Open and close. Wiggle, wiggle!', 'close', 'klˈoʊz'),
 ('Nice and close.', 'close', 'klˈoʊs'),
 ('They live together.', 'live', 'lˈɪv'),
 ('We took the lead.', 'lead', 'lˈid'),
 ('These does roam in the forest.', 'does', 'dˈoʊz'),
 ('He does like music.', 'does', 'dˈʌz'),
 ('The lead is heavy.', 'lead', 'lˈɛd'),
]
# A clear context must not resolve a different, unqualified occurrence.
BASS_CASES = [
 ('I play the bass guitar. The bass swims.', ['music', 'fish'], True),
 ('I play the bass guitar. Please bring the bass.', ['music', None], False),
 ('The bass swims. Please bring the bass.', ['fish', None], False),
 ('The bass guitar and the bass fish are here.', [None, None], False),
 ('I play the bass guitar; the bass swims.', ['music', 'fish'], True),
 ('The bass is low.', [None], False),
]


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--dictionary', type=Path, required=True)
    p.add_argument('--frontend-path', type=Path, default=HERE / 'candidates/clean_frontend.py')
    p.add_argument('--output', type=Path, default=HERE / 'results/pronunciation-expanded.json')
    a = p.parse_args()
    spec = importlib.util.spec_from_file_location('frontend_under_test', a.frontend_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    f = module.Frontend(a.dictionary)
    rows = []
    for text, word, expected in CASES:
        result = f(text)
        phones = [kitten_ipa(t['phonemes']) for t in result['tokens'] if t['text'].lower() == word and t['phonemes']]
        rows.append({'group': 'word_pronunciation', 'text': text, 'word': word,
                     'expected': expected, 'actual': phones,
                     'tags': [t['tag'] for t in result['tokens'] if t['text'].lower() == word],
                     'match': phones == [expected] and result['ipa'] is not None})
    for text, policies, resolved in BASS_CASES:
        result = f(text)
        actual_policies = result.get('bass_occurrence_policies', [result.get('bass_context_policy')] * len(policies))
        bass_phones = [kitten_ipa(t['phonemes']) for t in result['tokens'] if t['text'].lower() == 'bass' and t['phonemes']]
        expected_phones = [{'music': 'bˈeɪs', 'fish': 'bˈæs'}.get(policy) for policy in policies]
        rows.append({'group': 'bass_occurrence_scope', 'text': text,
                     'expected_policies': policies, 'actual_policies': actual_policies,
                     'actual_phonemes': bass_phones, 'expected_phonemes_when_resolved': expected_phones if resolved else None,
                     'expected_resolved': resolved, 'actual_resolved': result['ipa'] is not None,
                     'match': actual_policies == policies and (result['ipa'] is not None) == resolved and (not resolved or bass_phones == expected_phones)})
    failures = [r for r in rows if not r['match']]
    output = {'total': len(rows), 'passed': len(rows) - len(failures), 'failed': len(failures),
              'decision': 'BLOCKED: remaining pronunciation failures; no ONNX/app integration' if failures else 'Fixture pass only; other G2P/audio/Android gates remain unqualified',
              'records': rows}
    a.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k:v for k,v in output.items() if k != 'records'}, ensure_ascii=False))
    print(json.dumps(failures, ensure_ascii=False, indent=2))
    return bool(failures)

if __name__ == '__main__':
    raise SystemExit(main())
