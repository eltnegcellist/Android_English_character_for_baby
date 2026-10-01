"""Structural native-waveform audit. It does not establish pronunciation or soak success."""
import argparse, hashlib, json
from pathlib import Path
import numpy as np

def audit(root, count):
    rows=[]
    for i in range(count):
        path=root/f'{i:04d}.f32'
        raw=path.read_bytes()
        assert len(raw)%4==0, path
        y=np.frombuffer(raw,dtype='<f4')
        finite=bool(np.isfinite(y).all())
        duration=len(y)/24000
        rms=float(np.sqrt(np.mean(y.astype(np.float64)**2))) if len(y) else 0
        peak=float(np.max(np.abs(y))) if len(y) else 0
        valid=finite and .1<=duration<=60 and rms>1e-5 and peak<=2
        rows.append(dict(index=i,samples=len(y),duration_seconds=duration,rms=rms,peak=peak,finite=finite,valid=valid,sha256=hashlib.sha256(raw).hexdigest()))
    return dict(total=count,failed=sum(not r['valid'] for r in rows),sample_rate=24000,format='little-endian float32 mono',duration_range_seconds=[min(r['duration_seconds'] for r in rows),max(r['duration_seconds'] for r in rows)],records=rows)

def main():
    p=argparse.ArgumentParser();p.add_argument('--waveforms',type=Path,required=True);p.add_argument('--count',type=int,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--resumed',action='store_true');a=p.parse_args()
    result=audit(a.waveforms,a.count)
    result.update(integration_approved=False,uninterrupted_run_approved=not a.resumed,limitations=['Structural validity does not prove pronunciation, name quality or speaker similarity.'])
    if a.resumed:
        assert a.count==780
        report=json.loads((a.output.parent/'native-lite-names-resumed.json').read_text())
        assert report['failed']==0 and [r['index'] for r in report['records']]==list(range(650,780))
        for r in report['records']: assert r['samples']==result['records'][r['index']]['samples']
        result['provenance']={'acknowledged_original_indexes':[0,649],'regenerated_indexes':[650,779],'original_execution_interrupted':True,'cause':'unknown','not_an_uninterrupted_780_case_soak':True}
    a.output.write_text(json.dumps(result,indent=2)+'\n'); print(json.dumps({k:v for k,v in result.items() if k!='records'}));assert result['failed']==0
if __name__=='__main__':main()
