#!/usr/bin/env python3
"""Independent segment expectations for every lexical token in the fixed Lite bank.

A coverage pass is narrower than pronunciation/voice approval: stress, timing,
expressive length and contextual senses have separate gates.
"""
import argparse
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'candidates'))
from validate_g2p import BASE, NoEspeak, extract, git
sys.meta_path.insert(0, NoEspeak())
from clean_frontend import Frontend, kitten_ipa
from frontend_fingerprint import frontend_fingerprint


def segments(phonemes):
    return re.sub('[ˈˌː]', '', kitten_ipa(phonemes)).replace('ɾ', 't')


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--dictionary', required=True, type=Path)
    p.add_argument('--ref', default=BASE)
    p.add_argument('--frontend-path',type=Path,default=HERE/'candidates/clean_frontend.py')
    p.add_argument('--summary-output',type=Path,default=HERE/'results/lite-word-summary.json')
    p.add_argument('--output', type=Path, default=HERE / 'results/lite-word-check.json.gz')
    a = p.parse_args()
    spec=importlib.util.spec_from_file_location('lite_frontend_under_test',a.frontend_path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    f = module.Frontend(a.dictionary)
    fixture = json.loads((HERE / 'fixtures/lite-word-segments.json').read_text())
    expected = fixture['words']
    banks, hashes = extract(a.ref)
    rows, vocabulary, missing = [], set(), set()
    for index, bank in enumerate(banks):
        text = bank['template'].replace('{name}, ', '').replace('{name}', 'little one')
        result = f(text)
        words = []
        for token in result['tokens']:
            word = token['text'].lower()
            if not re.search('[a-z]', word):
                continue
            vocabulary.add(word)
            if word not in expected:
                missing.add(word)
            actual = segments(token['phonemes']) if token['phonemes'] else None
            words.append({'word': word, 'tag': token['tag'], 'actual_segments': actual,
                          'expected_segments': expected.get(word),
                          'match': word in expected and actual in expected[word]})
        # Content-word primary stress is checked independently of segment matching.
        vowels = r'aɪ|aʊ|eɪ|oʊ|ɔɪ|[ɑæʌɔəɜɛeɪiuʊɐo]'
        for original, token in zip([t for t in result['tokens'] if re.search('[a-z]', t['text'].lower())], words):
            accepted = fixture.get('primary_stress_syllable', {}).get(token['word'])
            if accepted is not None and original['phonemes']:
                raw = kitten_ipa(original['phonemes'])
                primary = [len(re.findall(vowels, raw[:m.start()])) for m in re.finditer('ˈ', raw)]
                token['primary_stress_syllables'] = primary
                token['expected_primary_syllables'] = accepted
                token['match'] = token['match'] and len(primary) == 1 and primary[0] in accepted
        # Semantics of close/read in the actual bank must also be correct.
        for token in words:
            if token['word'] == 'close':
                target = 'kloʊz' if re.search(r'\bOpen(?:,| and) close\.', text) else 'kloʊs'
                token['context_expected'] = target
                token['match'] = token['match'] and token['actual_segments'] == target
            elif token['word'] == 'read':
                token['context_expected'] = 'ɹid' # All fixed bank occurrences are Let's read!
                token['match'] = token['match'] and token['actual_segments'] == 'ɹid'
        rows.append({'index': index, 'group': bank['group'], 'text': text,
                     'match': bool(result['ipa']) and all(w['match'] for w in words), 'words': words})
    failures = [{'index': r['index'], 'text': r['text'], 'words': [w for w in r['words'] if not w['match']]} for r in rows if not r['match']]
    summary = {'source_commit': git('rev-parse', a.ref).strip(), 'source_hashes': hashes,
               'frontend_dependencies_sha256': frontend_fingerprint(),
               'fixture_sha256': hashlib.sha256((HERE/'fixtures/lite-word-segments.json').read_bytes()).hexdigest(),
               'frontend_sha256': hashlib.sha256(a.frontend_path.read_bytes()).hexdigest(),
               'utterances': len(rows), 'lexical_occurrences': sum(len(r['words']) for r in rows),
               'unique_lexical_tokens': len(vocabulary), 'fixture_words': len(expected),
               'missing_expectations': sorted(missing), 'failed_utterances': len(failures),
               'failed_occurrences': sum(len(r['words']) for r in failures),
               'scope': fixture['policy'], 'integration_approved': False}
    a.output.write_bytes(gzip.compress(json.dumps({'summary': summary, 'records': rows}, ensure_ascii=False, indent=2).encode(), mtime=0))
    a.summary_output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(summary, ensure_ascii=False))
    print(json.dumps(failures[:20], ensure_ascii=False, indent=2))
    return bool(failures or missing)

if __name__ == '__main__':
    raise SystemExit(main())
