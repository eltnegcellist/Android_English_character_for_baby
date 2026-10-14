"""Verify bundled AAC integrity and current Android fixed-English coverage."""
import hashlib,json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
assets=root/'app/src/main/assets'
bank=assets/'voice-1202'
manifest=json.loads((bank/'manifest.json').read_text())
assert manifest['schemaVersion']==1 and manifest['generationSeed']==1202 and manifest['referenceSourceSeed']==1202
assert manifest['referenceSha256']=='49e2939ab96482595d300d52135bc57234df4a019aca773f739d2f6f8be3a8d0'
assert manifest['modelRevision']=='e7dd0585652209fa0d7783659aad4e8a324de11c'
normalize=lambda text: re.sub(r'\s+',' ',text).strip()
catalog={};total=0
for entry in manifest['entries']:
 text=normalize(entry['text']);key=hashlib.sha256(text.encode()).hexdigest()[:24]
 assert entry['id']==key and entry['file']==f'clips/{key}.m4a' and text not in catalog
 assert entry['seed']==1202 and entry['reference_sha256']==manifest['referenceSha256']
 assert entry['sample_rate']==24000 and entry['codec']=='AAC-LC' and '{' not in text and '}' not in text
 data=(bank/entry['file']).read_bytes()
 assert len(data)==entry['bytes'] and hashlib.sha256(data).hexdigest()==entry['sha256'],text
 catalog[text]=entry;total+=len(data)
assert total==manifest['totalBytes']
required={"Hi, I'm Emma.","Hi, I'm Emma. Hello, little one!"}
for name in ['LiteResponseEngine.kt','InteractionReplyBank.kt','RuriCompositeReplies.kt','RuriSupplementalReplies.kt','RuriFrequentReplyVariants.kt']:
 source=root/'app/src/main/java/com/eltnegcellist/emma/ai'/name
 for literal in re.findall(r'"(?:\\.|[^"\\])*"',source.read_text()):
  try: text=json.loads(literal)
  except json.JSONDecodeError: continue
  if re.fullmatch(r"[A-Za-z0-9 ,.?!'’\-:]+",text) and re.search(r'[A-Za-z]',text) and re.search(r'[.!?]',text): required.add(normalize(text))
for topic in json.loads((assets/'play-topics.json').read_text())['topics']:
 for text in topic['phrases']:
  required.add(normalize(text))
  required.update(normalize(sentence) for sentence in re.findall(r'[^.!?]+[.!?]+|[^.!?]+$',text))
missing=required-catalog.keys()
assert not missing,'Regenerate adopted audio for: '+repr(sorted(missing))
print(json.dumps({'clips':len(catalog),'coveredAndroidUtterances':len(required),'totalBytes':total,'generationSeed':1202}))
