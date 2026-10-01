"""Reproduce bounded native fixtures from the pinned stable source, not G2P output."""
import base64,json
from pathlib import Path
from validate_g2p import BASE,NAMES,extract
HERE=Path(__file__).resolve().parent
b=lambda x:base64.b64encode(x.encode()).decode()
banks,_=extract(BASE)
fixed=[x['template'].replace('{name}, ','').replace('{name}','little one') for x in banks]
assert len(fixed)==202
names=[]
for name in NAMES[1:]:
 for row in banks:
  if row['group'] in {'generic','scenes'}:
   template=row['template'];text=template.replace('{name}',name) if '{name}' in template else f'{name}! {template}'
   names.append((text,name))
assert len(names)==780
files={'native-lite-catalogue.txt':'\n'.join(map(b,fixed))+'\n','native-lite-fixed.tsv':''.join(b(x)+'\t-\n' for x in fixed),'native-lite-names.tsv':''.join(b(t)+'\t'+b(n)+'\n' for t,n in names),'native-lite-names-remaining.tsv':''.join(b(t)+'\t'+b(n)+'\t'+str(i)+'\n' for i,(t,n) in enumerate(names) if i>=650),'native-lite-compound.tsv':''.join(b(x)+'\t-\t'+str(i)+'\n' for i,x in enumerate(fixed) if 'pitter-patter' in x.lower())}
for name,content in files.items():
 path=HERE/'fixtures'/name
 if path.exists(): assert path.read_text()==content,name
 else:path.write_text(content)
print('Native fixtures reproduce the pinned stable corpus: 202 fixed + 780 named cases')
