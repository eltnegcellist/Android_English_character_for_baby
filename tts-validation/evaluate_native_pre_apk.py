"""Fail-closed ledger for the native alternative. It cannot authorize app integration."""
import hashlib,json,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
read=lambda n:json.loads((HERE/'results'/n).read_text())
lex=read('native-lite-word-gate.json')
assert lex['native_sources_sha256']=={n:hashlib.sha256((HERE/'android-isolated'/n).read_bytes()).hexdigest() for n in lex['native_sources_sha256']}
for n in ['native-normal-structure.json','native-length-structure.json','native-lite-names-audio.json']:assert read(n)['failed']==0
controller=read('native-controller.json');assert controller['stop_restart_cycles']==100 and controller['cancelled_terminal_callbacks']==0
changed=subprocess.check_output(['git','diff','62f39abc095dd29a57d86a0efdef5400a2bb4293','--name-only'],cwd=HERE.parent,text=True).splitlines()
untracked=subprocess.check_output(['git','ls-files','--others','--exclude-standard'],cwd=HERE.parent,text=True).splitlines()
assert all(x.startswith('tts-validation/') for x in changed+untracked)
report=dict(integration_approved=False,pre_apk_complete=False,passed_bounded_checks=['Independent native fixed-Lite lexical expectations: 202/1260','Named lexical coverage: 780','Native real ONNX finite 24k audio: 202 normal + 202 length + composite 780 named','Real JVM ONNX stop/restart: 100; cancelled callbacks: 0','Android API + official ORT AAR compile only','Production isolation: all changes under tts-validation'],blockers=['Unrestricted Full/number/OOV/homograph G2P unsupported by this native planner','Named 780 run interrupted; resumed coverage does not prove uninterrupted soak','Acoustic equivalence, name pronunciation and Kiki naturalness not qualified','Specific pitter-patter subset fails ASR diagnostic in length variant; compound repair tested only on five cases','Actual Android AudioTrack, JNI lifecycle and stable KittenSpeaker state-transition parity not exercised'],next_steps=['Retain bounded Lite candidate; do not connect unsupported Full requests','Re-run continuous native soak with durable per-case journal and timeout diagnosis','Broaden independently sourced compound/acoustic checks before adopting a variant','Qualify Android playback/callbacks in an isolated instrumentation harness before application integration'],apk_build_or_inspection_performed=False,production_changed=False)
(HERE/'results/native-pre-apk-readiness.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report));raise SystemExit(1)
