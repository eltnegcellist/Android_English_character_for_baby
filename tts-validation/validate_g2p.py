#!/usr/bin/env python3
"""Read-only, fail-closed Misaki discovery probe. Never synthesizes or changes app code."""
import argparse
import hashlib
import importlib.abc
import importlib.metadata as metadata
import json
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
AI = 'app/src/main/java/com/eltnegcellist/emma/ai/'
BASE = '62f39abc095dd29a57d86a0efdef5400a2bb4293'
NAMES = ['', 'Hana', 'Hana-chan', 'Aoi-chan', 'Haruto-chan', 'Tsumugi-chan']


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True)


def source(ref, name):
    return git('show', f'{ref}:{AI}{name}')


def sha(text):
    return hashlib.sha256(text.encode()).hexdigest()


def literals(text):
    # Only fixed spoken bank sections; fail on interpolation/escaped strings.
    values = re.findall(r'"((?:\\.|[^"\\])*)"', text)
    if any('\\' in v or '$' in v for v in values):
        raise ValueError('Unsupported bank syntax: extraction must be reviewed')
    return [v for v in values if re.search(r'[.!?]', v)]


def extract(ref):
    engine = source(ref, 'LiteResponseEngine.kt')
    style = source(ref, 'LiteSpeechStyle.kt')
    boundaries = [
        ('generic', 'private val genericReplies = listOf(', 'private val sceneFillers ='),
        ('fillers', 'private val sceneFillers = mapOf(', 'private val scenes ='),
        ('scenes', 'private val scenes = listOf(', 'internal fun allTemplatesForValidation'),
    ]
    rows = []
    for group, start, end in boundaries:
        section = engine.split(start, 1)[1].split(end, 1)[0]
        rows.extend({'group': group, 'template': s} for s in literals(section))
    rows.extend({'group': 'neutral_closers', 'template': s}
                for s in literals(style.split('val neutralClosers = listOf(', 1)[1]))
    return rows, {'LiteResponseEngine.kt': sha(engine), 'LiteSpeechStyle.kt': sha(style)}


class NoEspeak(importlib.abc.MetaPathFinder):
    def find_spec(self, fullname, path=None, target=None):
        if fullname.split('.')[0] in {'phonemizer', 'espeakng_loader', 'espeakng'} or fullname == 'misaki.espeak':
            raise ImportError(f'Forbidden fallback import: {fullname}')


def inventory():
    rows = []
    for dist in metadata.distributions():
        m = dist.metadata
        license_text = '\n'.join([m.get('License', ''), m.get('License-Expression', ''),
                                 *[v for v in m.get_all('Classifier', []) if v.startswith('License')]])
        rows.append({'name': m['Name'], 'version': dist.version, 'license_metadata': license_text})
    return sorted(rows, key=lambda row: row['name'].lower())


def diagnostic_ipa(ps):
    # Upstream EN_PHONES.md expansion, NOT a qualified Kitten adapter.
    # Kitten accepts alphabetic symbols too; token membership alone misses wrong semantics.
    replacements = {'ʤ': 'dʒ', 'ʧ': 'tʃ', 'A': 'eɪ', 'I': 'aɪ', 'Y': 'ɔɪ',
                    'O': 'oʊ', 'Q': 'əʊ', 'W': 'aʊ', 'ᵊ': 'ə'}
    return ''.join(replacements.get(c, c) for c in ps)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ref', default=BASE)
    parser.add_argument('--compare-ref', default='origin/main')
    parser.add_argument('--output', type=Path, default=ROOT / 'tts-validation/results/g2p-discovery.json')
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if metadata.version('misaki') != '0.9.4' or metadata.version('en-core-web-sm') != '3.8.0':
        raise RuntimeError('Reference versions do not match')
    sys.meta_path.insert(0, NoEspeak())
    from misaki import en
    g2p = en.G2P(trf=False, british=False, fallback=None)
    assert g2p.fallback is None
    banks, hashes = extract(args.ref)
    compare_banks, compare_hashes = extract(args.compare_ref)
    token_path = ROOT / 'tts-validation/fixtures/kitten-v0.8-tokens.txt'
    token_text = token_path.read_text()
    symbols = {line.rsplit(' ', 1)[0] for line in token_text.splitlines()}
    records = []

    def probe(text, group, **extra):
        started = time.perf_counter()
        try:
            ps, tokens = g2p(text)
            unresolved = [{'text': t.text, 'tag': t.tag, 'phonemes': t.phonemes}
                          for t in tokens if t.phonemes is None or '❓' in t.phonemes]
            lexical_empty = [t.text for t in tokens if re.search('[A-Za-z0-9]', t.text) and t.phonemes == '']
            row = {'group': group, 'text': text, 'phonemes': ps,
                   'tokens': [{'text': t.text, 'tag': t.tag, 'phonemes': t.phonemes} for t in tokens],
                   'unresolved': unresolved, 'empty_lexical_tokens': lexical_empty,
                   'raw_unsupported_symbols': sorted(set(ps) - symbols),
                   'expanded_unsupported_symbols': sorted(set(diagnostic_ipa(ps)) - symbols),
                   'resolved': bool(ps.strip()) and not unresolved and not lexical_empty and '❓' not in ps}
        except Exception as exc:
            row = {'group': group, 'text': text, 'resolved': False, 'exception': repr(exc)}
        row.update(extra)
        row['elapsed_ms'] = round(1000 * (time.perf_counter() - started), 3)
        records.append(row)
        return row

    for bank in banks:
        probe(bank['template'].replace('{name}, ', '').replace('{name}', 'little one'),
              'fixed_bank', bank_group=bank['group'], template=bank['template'])
    templates = [b for b in banks if b['group'] in {'generic', 'scenes'}]
    for name in NAMES[1:]:
        for bank in templates:
            text = bank['template'].replace('{name}', name) if '{name}' in bank['template'] else f"{name}! {bank['template']}"
            probe(text, 'name_stress', name=name, bank_group=bank['group'])
    fixtures = ROOT / 'tts-validation/fixtures/core-sentences.txt'
    for line in fixtures.read_text().splitlines():
        if line and not line.startswith('#'):
            text, purpose = line.split('|', 1)
            probe(text.strip(), 'core_fixture', purpose=purpose.strip())
    for text in ["Hello, qzxvplm.", "Hello, Tsumugi.", "It's 3 o'clock.", 'You have 2 little hands.',
                 'The bass is low.', 'We caught a bass.', 'A tear fell.', 'Please tear the paper.']:
        probe(text, 'extra_stress')

    # Independently specified contrast checks; successful token generation is not pronunciation correctness.
    contrasts = [('I read a book yesterday.', 'read', 'ɹˈɛd'),
                 ('Please read the book.', 'read', 'ɹˈid'),
                 ('The wind is gentle.', 'wind', 'wˈɪnd'),
                 ('Wind up the toy.', 'Wind', 'wˈInd'),
                 ('The bass is low.', 'bass', 'bˈAs'),
                 ('We caught a bass.', 'bass', 'bˈæs'),
                 ('A tear fell.', 'tear', 'tˈɪɹ'),
                 ('Please tear the paper.', 'tear', 'tˈɛɹ')]
    for text, word, expected in contrasts:
        row = probe(text, 'pronunciation_contrast', expected_word=word, expected_phonemes=expected)
        actual = next((t['phonemes'] for t in row.get('tokens', []) if t['text'] == word), None)
        row['reference_match'] = actual == expected
    packages = inventory()
    family_hits = [p for p in packages if re.search(r'\b(?:A?GPL|LGPL)\b|GNU (?:Library|Lesser|General)', p['license_metadata'], re.I)]
    forbidden_packages = [p['name'] for p in packages if p['name'].lower().replace('_', '-') in
                          {'phonemizer', 'phonemizer-fork', 'espeakng-loader', 'espeakng'}]
    counts = {g: {'total': len(rs := [r for r in records if r['group'] == g]),
                  'unresolved_or_error': sum(not r['resolved'] for r in rs)}
              for g in sorted({r['group'] for r in records})}
    unresolved_words = sorted({t['text'] for r in records for t in r.get('unresolved', [])})
    summary = {'counts': counts, 'bank_counts': {g: sum(b['group'] == g for b in banks) for g in sorted({b['group'] for b in banks})},
               'unresolved_words': unresolved_words, 'gpl_family_metadata_hits': family_hits,
               'forbidden_packages': forbidden_packages,
               'contrast_matches': sum(r.get('reference_match', False) for r in records),
               'contrast_total': len(contrasts),
               'contrast_failures': [{'text': r['text'], 'expected': r['expected_phonemes'],
                                      'actual': next((t['phonemes'] for t in r.get('tokens', []) if t['text'] == r['expected_word']), None)}
                                     for r in records if r['group'] == 'pronunciation_contrast' and not r['reference_match']],
               'fixed_bank_raw_symbol_failures': sum(bool(r.get('raw_unsupported_symbols')) for r in records if r['group'] == 'fixed_bank'),
               'fixed_bank_semantic_alias_symbols': sorted({c for r in records if r['group'] == 'fixed_bank' for c in r.get('phonemes', '') if c in 'AIOQWYTᵊ'}),
               'raw_unsupported_symbols': sorted({c for r in records for c in r.get('raw_unsupported_symbols', [])}),
               'expanded_unsupported_symbols': sorted({c for r in records for c in r.get('expanded_unsupported_symbols', [])}),
               'corpus_same_as_compare_ref': banks == compare_banks and hashes == compare_hashes,
               'decision': 'BLOCKED: no app integration; license/data provenance and Kitten adapter remain unqualified'}
    import misaki
    data_dir = Path(misaki.__file__).parent / 'data'
    result = {'source_commit': git('rev-parse', args.ref).strip(),
              'compare_commit': git('rev-parse', args.compare_ref).strip(), 'source_hashes': hashes,
              'misaki_version': metadata.version('misaki'), 'g2p_configuration': {'trf': False, 'british': False, 'fallback': None, 'version': None},
              'dictionary_hashes': {name: hashlib.sha256((data_dir / name).read_bytes()).hexdigest() for name in ['us_gold.json', 'us_silver.json']},
              'kitten_tokens_sha256': sha(token_text), 'summary': summary, 'packages': packages,
              'banks': banks, 'records': records,
              'not_tested': ['Full generated corpus', 'arbitrary user names', 'qualified Kitten phoneme mapping',
                             'audio synthesis/quality', 'Android lifecycle/soak', 'final APK', 'complete dictionary provenance']}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    # Discovery report cannot confer integration approval, even if lexical coverage were perfect.
    return 1


if __name__ == '__main__':
    raise SystemExit(main())
