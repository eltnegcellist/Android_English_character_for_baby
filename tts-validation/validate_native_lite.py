#!/usr/bin/env python3
"""Independent lexical expectations for a bounded Kotlin CMU alternative."""
import argparse,base64,hashlib,json,re
from pathlib import Path
HERE=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--input',type=Path,default=HERE/'results/native-lite-lexical.json');p.add_argument('--output',type=Path,default=HERE/'results/native-lite-word-gate.json')
a=p.parse_args();r=json.loads(a.input.read_text());f=json.loads((HERE/'fixtures/lite-word-segments.json').read_text())
fixed=json.loads((HERE/'fixtures/comparison-corpus.json').read_text())
assert [x['text'] for x in r['records']]==[x['text'] for x in fixed]
catalogue=[base64.b64decode(x).decode() for x in (HERE/'fixtures/native-lite-catalogue.txt').read_text().splitlines()]
assert catalogue==[x['text'] for x in fixed]
failures=[];count=0;seen=set();vowels=r'aɪ|aʊ|eɪ|oʊ|ɔɪ|[ɑæʌɔəɜɛeɪiuʊɐo]'
for row in r['records']:
 for t in row.get('words',[]):
  word=t['text'].lower();raw=t['ipa'];seg=re.sub('[ˈˌː]','',raw);seen.add(word);count+=1
  expected=f['words'].get(word,[])
  primary=[len(re.findall(vowels,raw[:m.start()])) for m in re.finditer('ˈ',raw)]
  stress=f['primary_stress_syllable'].get(word)
  valid=seg in expected and (not stress or (len(primary)==1 and primary[0] in stress))
  if word=='close':valid=valid and seg==('kloʊz' if re.search(r'\bOpen(?:,| and) close\.',row['text']) else 'kloʊs')
  if word=='read':valid=valid and seg=='ɹid'
  if not valid:failures.append({'text':row['text'],'word':word,'ipa':raw,'segments':seg,'expected':expected,'primary':primary,'expected_primary':stress})
tokens={line.rsplit(' ',1)[0] for line in (HERE/'fixtures/kitten-v0.8-tokens.txt').read_text().splitlines()}
unsupported=sorted({c for row in r['records'] if row.get('valid') for c in row['ipa'] if c not in tokens})
result={'utterances':len(r['records']),'lexical_occurrences':count,'unique_lexical_tokens':len(seen),'failed_occurrences':len(failures),'unresolved_utterances':r['failed'],'unsupported_tokens':unsupported,'fixture_sha256':hashlib.sha256((HERE/'fixtures/lite-word-segments.json').read_bytes()).hexdigest(),'native_sources_sha256':{name:hashlib.sha256((HERE/'android-isolated'/name).read_bytes()).hexdigest() for name in ['NativeLitePlanner.kt','NativeLiteProbe.kt']},'integration_approved':False,'scope':'Independent fixed-Lite expectations only; no Full/OOV/acoustic approval','failures':failures}
a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps(result,ensure_ascii=False));raise SystemExit(bool(failures or unsupported or r['failed']))
