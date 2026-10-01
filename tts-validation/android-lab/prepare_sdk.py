from pathlib import Path
import urllib.request,zipfile,hashlib,concurrent.futures,xml.etree.ElementTree as E,os
import sys
root=Path(sys.argv[1]);root.mkdir(exist_ok=True)
items=[('build-tools_r35_linux.zip','2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc','https://dl.google.com/android/repository/build-tools_r35_linux.zip'),('platform-tools.zip','477254aa5f903c15cf51001717bdf347fb6b53e0','https://dl.google.com/android/repository/platform-tools_r37.0.1-linux.zip'),('emulator.zip','1b1f78891abf8ec268264356e1365c25519e8379','https://dl.google.com/android/repository/emulator-linux_x64-15917651.zip'),('image.zip','1d93bd994e29c4e9bbe71fd9c278addc5418cbee','https://dl.google.com/android/repository/sys-img/google_apis/x86_64-28_r11.zip')]
def fetch(item):
 name,sha,url=item;p=root/name
 with urllib.request.urlopen(url,timeout=60) as response,p.open('wb') as f:
  while b:=response.read(1024*1024):f.write(b)
 assert hashlib.sha1(p.read_bytes()).hexdigest()==sha,name
 with zipfile.ZipFile(p) as z:
  z.extractall(root)
  for i in z.infolist():
   perm=i.external_attr>>16
   if perm:os.chmod(root/i.filename,perm)
 print('Verified and extracted',name,flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as x:list(x.map(fetch,items))
