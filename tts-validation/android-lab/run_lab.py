"""Run emulator, adb and instrumentation in one execution/network namespace."""
import argparse,os,subprocess,time,json
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--sdk',type=Path,required=True);p.add_argument('--apk',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
adb=str(a.sdk/'platform-tools/adb');env=dict(os.environ,ANDROID_AVD_HOME=str(a.sdk/'avd'),ANDROID_EMULATOR_HOME=str(a.sdk/'emulator-home'))
for executable in (a.sdk/"emulator").rglob("*"):
 if executable.is_file():
  with executable.open("rb") as f:magic=f.read(4)
  if magic==b"\x7fELF":executable.chmod(executable.stat().st_mode|0o111)
(a.sdk/"platform-tools/adb").chmod(0o755)
log=a.output.with_suffix('.emulator.log').open('w')
process=subprocess.Popen([str(a.sdk/'emulator/emulator'),'-avd','tts28lab','-read-only','-accel','off','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader_indirect','-camera-back','none','-camera-front','none','-cores','2'],env=env,stdout=log,stderr=log)
started=time.monotonic();last=0
try:
 while True:
  if process.poll() is not None:raise RuntimeError('Emulator exited before boot; see log')
  try:r=subprocess.run([adb,'shell','getprop','sys.boot_completed'],capture_output=True,text=True,timeout=10)
  except subprocess.TimeoutExpired:r=subprocess.CompletedProcess([],1,stdout='')
  if r.stdout.strip()=='1':break
  elapsed=time.monotonic()-started
  if elapsed>600:raise TimeoutError('Emulator boot exceeded 600 seconds')
  if elapsed-last>30:print(f'Android boot pending: {int(elapsed)}s',flush=True);last=elapsed
  time.sleep(2)
 print('Android boot complete',flush=True)
 while not a.apk.exists():
  if time.monotonic()-started>660:raise TimeoutError('Lab APK unavailable')
  time.sleep(1)
 subprocess.run([adb,'install','-r',str(a.apk)],check=True,timeout=120)
 out=a.output.with_suffix('.instrumentation.txt').open('w')
 job=subprocess.Popen([adb,'shell','am','instrument','-w','validation.tts.lab/validation.tts.LabInstrumentation'],stdout=out,stderr=out)
 began=time.monotonic();last=0
 while job.poll() is None:
  elapsed=time.monotonic()-began
  if elapsed>1200:job.kill();raise TimeoutError('Instrumentation exceeded 1200 seconds')
  if elapsed-last>30:
   logs=subprocess.run([adb,'logcat','-d','-s','TTS_LAB:I','*:S'],capture_output=True,text=True,timeout=20).stdout
   a.output.with_suffix('.logcat.txt').write_text(logs);print(f'Android probe {int(elapsed)}s; '+logs[-300:],flush=True);last=elapsed
  time.sleep(2)
 out.close()
 result=subprocess.run([adb,'shell','run-as','validation.tts.lab','cat','files/result.json'],capture_output=True,text=True,timeout=20)
 if result.returncode:raise RuntimeError(a.output.with_suffix('.instrumentation.txt').read_text())
 d=json.loads(result.stdout);a.output.write_text(json.dumps(d,indent=2)+'\n');assert d['passed'];print(json.dumps(d),flush=True)
except Exception as error:
 a.output.write_text(json.dumps({'passed':False,'error':str(error),'integration_approved':False},indent=2)+'\n');raise
finally:
 process.terminate()
 try:process.wait(timeout=20)
 except subprocess.TimeoutExpired:process.kill()
 log.close()
