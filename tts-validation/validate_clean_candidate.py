#!/usr/bin/env python3
"""Second independent discovery run: selective Misaki code + pinned CMU + own rules."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'candidates'))
from validate_g2p import BASE, NAMES, ROOT, extract, git, inventory, NoEspeak
sys.meta_path.insert(0, NoEspeak())
from clean_frontend import Frontend, kitten_ipa, romaji_name
from number_words import num2words

NUMBER_EXPECTED = [
    (0, 'cardinal', 'zero'), (19, 'cardinal', 'nineteen'), (20, 'cardinal', 'twenty'),
    (21, 'cardinal', 'twenty one'), (100, 'cardinal', 'one hundred'),
    (101, 'cardinal', 'one hundred one'), (1000, 'cardinal', 'one thousand'),
    (1_000_001, 'cardinal', 'one million one'), (-12, 'cardinal', 'minus twelve'),
    ('1.50', 'cardinal', 'one point five zero'), ('-0.5', 'cardinal', 'minus zero point five'),
    (1, 'ordinal', 'first'), (2, 'ordinal', 'second'), (3, 'ordinal', 'third'),
    (5, 'ordinal', 'fifth'), (8, 'ordinal', 'eighth'), (9, 'ordinal', 'ninth'),
    (12, 'ordinal', 'twelfth'), (20, 'ordinal', 'twentieth'), (21, 'ordinal', 'twenty first'),
    (100, 'ordinal', 'one hundredth'), (1900, 'year', 'nineteen hundred'),
    (1905, 'year', 'nineteen oh five'), (1999, 'year', 'nineteen ninety nine'),
    (2005, 'year', 'two thousand five'), (2026, 'year', 'twenty twenty six'),
]
NAME_EXPECTED = {'Hana': 'hˈɑnɑ', 'Hana-chan': 'hˈɑnɑ tʃɑn', 'Aoi-chan': 'ˈɑoi tʃɑn',
                 'Haruto-chan': 'hˈɑɹuto tʃɑn', 'Tsumugi-chan': 'tsˈumuɡi tʃɑn',
                 'Sakura-chan': 'sˈɑkuɹɑ tʃɑn', 'Riku-chan': 'ɹˈiku tʃɑn'}
ADAPTER_EXPECTED = {'həlˈO': 'həlˈoʊ', 'tˈIm': 'tˈaɪm', 'dˈA': 'dˈeɪ', 'nˈW': 'nˈaʊ',
                    'tˈY': 'tˈɔɪ', 'ʤˈɛntəl': 'dʒˈɛntəl', 'ʧˈæn': 'tʃˈæn',
                    'lˈɪTᵊl': 'lˈɪɾəl'}
CONTRASTS = [('I read a book yesterday.', 'read', 'ɹˈɛd'),
             ('Please read the book.', 'read', 'ɹˈid'),
             ('The wind is gentle.', 'wind', 'wˈɪnd'),
             ('Wind up the toy.', 'Wind', 'wˈInd'),
             ('The bass is low.', 'bass', 'bˈAs'),
             ('We caught a bass.', 'bass', 'bˈæs'),
             ('A tear fell.', 'tear', 'tˈɪɹ'),
             ('Please tear the paper.', 'tear', 'tˈɛɹ')]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--dictionary', type=Path, required=True)
    parser.add_argument('--ref', default=BASE)
    parser.add_argument('--compare-ref', default='origin/main')
    parser.add_argument('--output', type=Path, default=HERE / 'results/cmu-candidate.json.gz')
    args = parser.parse_args()
    x = Frontend(args.dictionary)
    symbols = {line.rsplit(' ', 1)[0] for line in (HERE / 'fixtures/kitten-v0.8-tokens.txt').read_text().splitlines()}
    banks, hashes = extract(args.ref)
    newer, newer_hashes = extract(args.compare_ref)
    records, assertions = [], []

    def probe(text, group, name=None):
        try:
            output = x(text, japanese_name=name)
            unsupported = sorted(set(output['ipa'] or '') - symbols)
            row = {'text': text, 'group': group, 'name_metadata': name, **output,
                   'unsupported_kitten_symbols': unsupported,
                   'resolved': bool(output['ipa']) and not output['unresolved'] and not output['unresolved_meaning'] and not unsupported}
        except Exception as exc:
            row = {'text': text, 'group': group, 'name_metadata': name, 'resolved': False, 'exception': repr(exc)}
        records.append(row)
        return row

    for bank in banks:
        row = probe(bank['template'].replace('{name}, ', '').replace('{name}', 'little one'), 'fixed_bank')
        row['bank_group'] = bank['group']
    for name in NAMES[1:]:
        for bank in [b for b in banks if b['group'] in {'generic', 'scenes'}]:
            text = bank['template'].replace('{name}', name) if '{name}' in bank['template'] else f"{name}! {bank['template']}"
            probe(text, 'name_stress', name)
    for line in (HERE / 'fixtures/core-sentences.txt').read_text().splitlines():
        if line and not line.startswith('#'):
            text = line.split('|', 1)[0].strip()
            probe(text, 'core_fixture', 'Hana' if 'Hana' in text else None)
    for text, word, expected in CONTRASTS:
        row = probe(text, 'pronunciation_contrast')
        actual = next((t['phonemes'] for t in row.get('tokens', []) if t['text'] == word), None)
        row.update(expected_phonemes=expected, actual_phonemes=actual, reference_match=actual == expected)
    clear_bass = [('The bass guitar sounds deep.', 'bˈAs'), ('The bass clef is on this page.', 'bˈAs'),
                  ('I play the double bass.', 'bˈAs'), ('The bass swims in the lake.', 'bˈæs')]
    for text, expected in clear_bass:
        row = probe(text, 'clear_bass_context')
        actual = next((t['phonemes'] for t in row.get('tokens', []) if t['text'].lower() == 'bass'), None)
        assertions.append({'group': 'bass_clear_context', 'input': text, 'expected': expected,
                           'actual': actual, 'match': actual == expected and row['resolved']})
    ambiguous = probe('The bass is low.', 'ambiguous_meaning')
    assertions.append({'group': 'ambiguous_meaning_detection', 'input': ambiguous['text'],
                       'match': ambiguous.get('unresolved_meaning') == ['bass'] and not ambiguous['resolved']})
    for text in ["Let's open the window and listen to the birds.",
                 'Your little hands are warm and soft.', 'We can turn the page together.',
                 'I can hear you making a happy sound.', 'After your bath, we will dry your feet.',
                 'The rain is tapping on the window.', "It's time for a clean diaper.",
                 'Your teddy bear is right beside you.', 'That was a wonderful little smile.',
                 'Take your time. I am here with you.', 'You are safe in my arms.',
                 'Can you see the bright red flower?', 'We are going outside for a short walk.',
                 'We used to sing this song.', 'The leaves move in the gentle wind.',
                 'I read this book last night.', 'Please read this little story with me.',
                 "I'm listening to your voice.", 'You have ten tiny fingers and ten tiny toes.',
                 'A little butterfly is flying over the garden.']:
        probe(text, 'unseen_english_stress')
    for value, mode, expected in NUMBER_EXPECTED:
        actual = num2words(value, to=mode)
        assertions.append({'group': 'number_spelling', 'input': str(value), 'mode': mode,
                           'expected': expected, 'actual': actual, 'match': actual == expected})
        # Check every resulting word is representable by the candidate/Kitten.
        probe(actual + '.', 'number_output_g2p')
    for name, expected in NAME_EXPECTED.items():
        actual = romaji_name(name)
        assertions.append({'group': 'name_policy', 'input': name, 'expected': expected,
                           'actual': actual, 'match': actual == expected})
    for ps, expected in ADAPTER_EXPECTED.items():
        actual = kitten_ipa(ps)
        assertions.append({'group': 'phoneme_adapter', 'input': ps, 'expected': expected,
                           'actual': actual, 'match': actual == expected and not (set(actual) - symbols)})
    for text in ['Hello, qzxvplm.', 'Good morning, Tsumugi.']:
        row = probe(text, 'negative_unknown')
        assertions.append({'group': 'unknown_detection', 'input': text, 'match': not row['resolved']})
    for value, mode in [('NaN', 'cardinal'), ('Infinity', 'cardinal'), (1_000_000_000, 'cardinal'),
                        (-1, 'ordinal'), ('2.5', 'ordinal'), (3, 'currency')]:
        try:
            num2words(value, to=mode)
            match = False
        except ValueError:
            match = True
        assertions.append({'group': 'unsupported_number_rejection', 'input': str(value), 'mode': mode, 'match': match})
    for name in ["O'Connor", 'Qzxvplm', 'Hana[xx]', 'John/xx', '']:
        try:
            romaji_name(name)
            match = False
        except ValueError:
            match = True
        assertions.append({'group': 'unsupported_name_rejection', 'input': name, 'match': match})
    for ps in ['❓', 'həlˈB']:
        try:
            kitten_ipa(ps)
            match = False
        except ValueError:
            match = True
        assertions.append({'group': 'invalid_adapter_rejection', 'input': ps, 'match': match})

    packages = inventory()
    hits = [p for p in packages if re.search(r'\b(?:A?GPL|LGPL)\b|GNU (?:Library|Lesser|General)', p['license_metadata'], re.I)]
    forbidden = [p['name'] for p in packages if p['name'].lower().replace('_', '-') in
                 {'num2words', 'misaki', 'phonemizer', 'phonemizer-fork', 'espeakng-loader', 'espeakng'}]
    summary = {'counts': {g: {'total': len(rs := [r for r in records if r['group'] == g]),
                             'unresolved_or_error': sum(not r['resolved'] for r in rs)} for g in sorted({r['group'] for r in records})},
               'assertions': {g: {'total': len(aa := [a for a in assertions if a['group'] == g]),
                                   'failed': sum(not a['match'] for a in aa)} for g in sorted({a['group'] for a in assertions})},
               'contrast_matches': sum(r.get('reference_match', False) for r in records),
               'contrast_total': len(CONTRASTS),
               'contrast_failures': [r for r in records if r['group'] == 'pronunciation_contrast' and not r['reference_match']],
               'unresolved_bank_words': sorted({t for r in records if r['group'] in {'fixed_bank', 'name_stress'} for t in r.get('unresolved', [])}),
               'gpl_family_metadata_hits': hits, 'forbidden_installed_packages': forbidden,
               'same_corpus_as_compare_ref': banks == newer and hashes == newer_hashes,
               'decision': 'BLOCKED: G2P correctness/Full coverage and audio parity are not established; no app integration'}
    output = {'source_commit': git('rev-parse', args.ref).strip(),
              'compare_commit': git('rev-parse', args.compare_ref).strip(), 'source_hashes': hashes,
              'candidate': 'selective Misaki code + CMU data + independent number/name rules',
              'provenance': x.provenance, 'configuration': {'version': '2.0', 'fallback': None, 'trf': False, 'british': False},
              'summary': summary, 'records': records, 'assertions': assertions, 'packages': packages,
              'limitations': ['Japanese name pronunciation policy not acoustically reviewed', 'arbitrary foreign names unsupported',
                              'no English OOV fallback', 'bass meaning unresolved', 'Full unrestricted text not qualified',
                              'no acoustic comparison', 'no Android engine/lifecycle/APK']}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(gzip.compress(json.dumps(output, ensure_ascii=False, indent=2).encode(), mtime=0))
    (args.output.parent / 'cmu-candidate-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 1


if __name__ == '__main__':
    raise SystemExit(main())
