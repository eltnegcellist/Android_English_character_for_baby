#!/usr/bin/env python3
"""Bounded Lite diagnostics only: valid PCM does not qualify integration or voice parity."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import resource
import os
import sys
import time
import numpy as np

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE/'candidates'))
from validate_g2p import BASE, NoEspeak, extract, inventory
sys.meta_path.insert(0,NoEspeak())
from clean_frontend import Frontend
from isolated_onnx import Engine, ASSET_HASHES
from frontend_fingerprint import frontend_fingerprint


def main():
    p=argparse.ArgumentParser();p.add_argument('--dictionary',type=Path,required=True);p.add_argument('--assets',type=Path,required=True)
    p.add_argument('--check-only',action='store_true')
    p.add_argument('--limit',type=int,default=0);p.add_argument('--soak',type=int,default=0)
    a=p.parse_args()
    # Refuse to start synthesis unless the independent fixed-bank gate passed.
    gate=json.loads((HERE/'results/lite-word-summary.json').read_text())
    fixture_hash=hashlib.sha256((HERE/'fixtures/lite-word-segments.json').read_bytes()).hexdigest()
    frontend_hash=hashlib.sha256((HERE/'candidates/clean_frontend.py').read_bytes()).hexdigest()
    if gate.get('frontend_dependencies_sha256')!=frontend_fingerprint():
        raise ValueError('Frontend dependency sources changed after the passed gate')
    if gate['failed_utterances'] or gate['missing_expectations'] or gate.get('fixture_sha256')!=fixture_hash or gate.get('frontend_sha256')!=frontend_hash:
        raise ValueError('The word gate is missing, failing, or stale; run it before ONNX diagnostics')
    for name, total in [('pronunciation-expanded.json',39),('frontend-boundaries.json',30)]:
        passed=json.loads((HERE/'results'/name).read_text())
        if passed['total']!=total or passed['failed']:raise ValueError('Focused G2P gate failed: '+name)
    prior=json.loads((HERE/'results/cmu-candidate-summary.json').read_text())
    if any(v['failed'] for v in prior['assertions'].values()) or any(prior['counts'][g]['unresolved_or_error'] for g in ['fixed_bank','name_stress']) or prior['gpl_family_metadata_hits'] or prior['forbidden_installed_packages']:
        raise ValueError('Existing coverage/dependency gate failed')
    banks,hashes=extract(BASE)
    if gate['source_hashes']!=hashes or gate['utterances']!=len(banks):raise ValueError('Corpus gate mismatch')
    if a.check_only:
        print('Passed fixed-input G2P preflight; no ONNX synthesis requested',flush=True)
        return 0
    f=Frontend(a.dictionary);e=Engine(a.assets);rows=[]
    selected=banks[:a.limit] if a.limit else banks
    started=time.monotonic()
    for i,b in enumerate(selected):
        text=b['template'].replace('{name}, ','').replace('{name}','little one')
        r=f(text);t=time.monotonic(); row={'index':i,'text':text}
        try:
            if r['unresolved'] or r['unresolved_meaning']:raise ValueError('Unresolved input')
            y=e.generate(r['ipa']);duration=y.size/24000
            bound=max(10, len(text.split())*3)
            peak=float(np.max(np.abs(y)));rms=float(np.sqrt(np.mean(y.astype(np.float64)**2)))
            # Loose structural bounds, not measurements of naturalness.
            valid=0.05<=duration<=bound and 0<rms and peak<=2.0
            pcm=np.rint(np.clip(y,-1,1)*32767).astype('<i2')
            row.update(samples=int(y.size),sample_rate=24000,duration_seconds=duration,peak=peak,rms=rms,
                       over_unit_fraction=float(np.mean(np.abs(y)>1)),pcm_sha256=hashlib.sha256(pcm.tobytes()).hexdigest(),valid=valid)
        except Exception as exc:row.update(valid=False,error=repr(exc))
        row['generation_seconds']=time.monotonic()-t;rows.append(row)
        if (i+1)%20==0: print(json.dumps({'completed':i+1,'failed':sum(not r['valid'] for r in rows)}),flush=True)
    soak=[]
    # Repeated generate/discard only; this is not AudioTrack/cancel/restart testing.
    if a.soak:
        ipa=f(selected[0]['template'].replace('{name}, ','').replace('{name}','little one'))['ipa']
        for i in range(a.soak):
            y=e.generate(ipa);soak.append({'index':i,'samples':int(y.size),'finite':bool(np.isfinite(y).all()),'max_rss_kib':resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,'current_rss_kib':int(Path('/proc/self/statm').read_text().split()[1])*os.sysconf('SC_PAGE_SIZE')//1024,'float_sha256':hashlib.sha256(y.tobytes()).hexdigest()})
    packages=inventory();hits=[r for r in packages if re.search(r'\b(?:A?GPL|LGPL)\b|GNU (?:Library|Lesser|General)',r['license_metadata'],re.I)]
    forbidden=[r['name'] for r in packages if r['name'].lower().replace('_','-') in {'misaki','num2words','phonemizer','espeakng-loader','espeakng','sherpa-onnx'}]
    summary={'utterances':len(rows),'failed':sum(not r['valid'] for r in rows),'sample_rate':24000,'speaker_id':7,'speaker':'expr-voice-5-f',
             'requested_speed':0.8,'model_speed_prior':0.8,'tensor_speed':float(e.speed[0]),'elapsed_seconds':time.monotonic()-started,
             'soak_generations':len(soak),'soak_failed':sum(not r['finite'] or not r['samples'] for r in soak),
             'max_rss_kib':resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,'soak_unique_waveforms':len({r['float_sha256'] for r in soak}),'soak_rss_range_kib':[min(r['current_rss_kib'] for r in soak),max(r['current_rss_kib'] for r in soak)] if soak else None,'gpl_family_metadata_hits':hits,'forbidden_packages':forbidden,
             'integration_approved':False,'limitations':['no acoustic comparison with stable TTS','no names synthesized in this run','no Android playback/cancel/callback testing','no final APK audit','no unrestricted Full qualification']}
    out={'summary':summary,'asset_sha256':ASSET_HASHES,'metadata':e.meta,'source_hashes':hashes,'gate_fixture_sha256':fixture_hash,'gate_frontend_sha256':frontend_hash,'records':rows,'generate_discard_soak':soak,'packages':packages}
    (HERE/'results/onnx-isolated.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(summary,ensure_ascii=False),flush=True)
    return bool(summary['failed'] or summary['soak_failed'] or hits or forbidden)

if __name__=='__main__':raise SystemExit(main())
