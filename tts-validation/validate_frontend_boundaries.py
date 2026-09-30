#!/usr/bin/env python3
"""Meaningful boundary tests: real numeric inputs, context, names and rejection."""
import argparse
import json
from pathlib import Path
import sys
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'candidates'))
from validate_g2p import NoEspeak
sys.meta_path.insert(0, NoEspeak())
from clean_frontend import Frontend, kitten_ipa
from validate_lite_words import segments

WORD_CASES = [
 ('We are open and close to the park.', 'close', ['kloʊs']),
 ('Open and close your hands.', 'close', ['kloʊz']),
 ('Please open and close.', 'close', ['kloʊz']),
 ('Keep your eyes close to the book.', 'close', ['kloʊs']),
 ('Please close your eyes.', 'close', ['kloʊz']),
 ('She does not sing.', 'does', ['dʌz']),
 ('Hold the lead.', 'lead', ['lid']),
 ('The pipe is made of lead.', 'lead', ['lɛd']),
 ('Outside time!', 'Outside', ['aʊtsaɪd']),
 ('Rain outside!', 'outside', ['aʊtsaɪd']),
 ('You have 0 toys.', '0', ['zɪɹoʊ', 'ziɹoʊ']),
 ('You have 0.5 ounces.', '0.5', ['zɪɹoʊ pɔɪnt faɪv', 'ziɹoʊ pɔɪnt faɪv']),
 ('You have -0.5 ounces.', '-0.5', ['maɪnəs zɪɹoʊ pɔɪnt faɪv', 'maɪnəs ziɹoʊ pɔɪnt faɪv']),
 ('You have 50% milk.', '50%', ['fɪfti pəɹsɛnt']),
 ('You have 2nd place.', '2nd', ['sɛkənd']),
 ('You have 1.5 ounces.', '1.5', ['wʌn pɔɪnt faɪv']),
 ("I'm listening.", "I'm", ['aɪm']),
 ("You're warm.", "You're", ['jʊɹ', 'jɔɹ', 'jəɹ']),
]


def main():
    p=argparse.ArgumentParser(); p.add_argument('--dictionary',required=True,type=Path)
    a=p.parse_args(); f=Frontend(a.dictionary); rows=[]
    for text, word, expected in WORD_CASES:
        r=f(text); actual=[segments(t['phonemes']) for t in r['tokens'] if t['text'].lower()==word.lower() and t['phonemes']]
        rows.append({'group':'word_or_numeric_context','text':text,'word':word,'expected':expected,'actual':actual,
                     'match':bool(r['ipa']) and len(actual)==1 and actual[0] in expected})
    for text in ['Hello, Hana-chan!', "Hana-chan's hands are warm.", 'HANA-CHAN, hello!']:
        r=f(text,japanese_name='Hana-chan'); actual=[kitten_ipa(t['phonemes']) for t in r['tokens'] if t['text'].lower()=='hana-chan']
        rows.append({'group':'explicit_name_metadata','text':text,'actual':actual,'match':bool(r['ipa']) and actual==['hˈɑnɑ tʃɑn']})
    for text in ['Hello, qzxvplm.', 'Hello, Tsumugi.', 'Hana-chanx, hello!']:
        r=f(text,japanese_name='Hana-chan' if 'Hana-chanx' in text else None)
        rows.append({'group':'no_guessed_name_or_oov','text':text,'unresolved':r['unresolved'],'match':r['ipa'] is None and bool(r['unresolved'])})
    for text in ['[Hana](/hɑnɑ/)', 'Hello, Hana\n[xx]', 'Hello ] world']:
        rejected=False
        try: f(text)
        except ValueError: rejected=True
        rows.append({'group':'embedded_alias_rejection','text':text,'match':rejected})
    for text in ['The bass guitar is here. Please bring the bass.', 'The bass swims; please bring the bass.', 'The bass guitar is here: a bass swims.']:
        r=f(text)
        rows.append({'group':'bass_scope_rejection','text':text,'unresolved_meaning':r['unresolved_meaning'],'match':r['ipa'] is None and bool(r['unresolved_meaning'])})
    failed=[r for r in rows if not r['match']]
    result={'total':len(rows),'passed':len(rows)-len(failed),'failed':len(failed),'integration_approved':False,'records':rows}
    (HERE/'results/frontend-boundaries.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='records'},ensure_ascii=False)); print(json.dumps(failed,ensure_ascii=False,indent=2))
    return bool(failed)

if __name__=='__main__': raise SystemExit(main())
