"""Disposable API35 emulator only. Uses real upload/command/channel routes."""
import json,os,uuid,time,urllib.request,subprocess,xml.etree.ElementTree as ET,re
from pathlib import Path
f=json.loads(Path(os.environ['ANKAI_QA_FIXTURE']).read_text());base='http://127.0.0.1:'+str(f['port']);out=Path(os.environ['ANKAI_QA_OUTPUT']);out.mkdir(parents=True,exist_ok=True)
adb=[str(Path.home()/'Android/Sdk/platform-tools/adb'),'-s','emulator-5554'];device=f['deviceId']
def shell(*args):return subprocess.check_output(adb+['shell',*args],text=True)
def api(path,body=None,headers=None):
 req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json',**(headers or {})})
 with urllib.request.urlopen(req,timeout=15) as r:return json.load(r)
def dump():
 shell('uiautomator','dump','/sdcard/ankai-install-qa.xml')
 return ET.fromstring(shell('cat','/sdcard/ankai-install-qa.xml'))
def tap(text):
 deadline=time.monotonic()+20
 while time.monotonic()<deadline:
  nodes=list(dump().iter('node'));found=next((n for n in nodes if n.get('text','').lower()==text.lower()),None)
  if found is None:found=next((n for n in nodes if text.lower() in (n.get('text','')+' '+n.get('content-desc','')).lower()),None)
  if found is not None:
   r=list(map(int,re.findall(r'\d+',found.get('bounds'))));shell('input','tap',str((r[0]+r[2])//2),str((r[1]+r[3])//2));return
  time.sleep(.5)
 raise AssertionError('UI element not found: '+text)
def capture(name):
 time.sleep(1.5);(out/name).write_bytes(subprocess.check_output(adb+['exec-out','screencap','-p']))
def queue():
 boundary='qa'+uuid.uuid4().hex;p=Path(os.environ['ANKAI_QA_APK']);body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="qa-game.apk"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n'.encode()+p.read_bytes()+f'\r\n--{boundary}--\r\n'.encode())
 req=urllib.request.Request(base+f'/api/run/devices/{device}/packages',data=body,headers={'Content-Type':'multipart/form-data; boundary='+boundary,'X-Client-Request-Id':'upload_'+uuid.uuid4().hex})
 with urllib.request.urlopen(req,timeout=15) as r:package=json.load(r)['package']
 c=api(f'/api/run/devices/{device}/commands',{'action':'install_apk','params':{'packageId':package['packageId'],'sha256':package['sha256']},'clientRequestId':'install_'+uuid.uuid4().hex})['command']
 deadline=time.monotonic()+30
 while time.monotonic()<deadline:
  current=api(f'/api/run/devices/{device}/commands/'+c['id'])['command']
  if current.get('result'):raise AssertionError(current['result'])
  if current.get('progress'):break
  time.sleep(.5)
 else:raise AssertionError('No install progress')
 print('Real APK transfer completed; awaiting local Android confirmation',flush=True)
 return c['id']
if __name__=='__main__':
 mode=os.environ.get('ANKAI_QA_MODE','prepare')
 if mode in ('prepare','pending'):
  devices=api('/api/run/devices')['devices'];d=next(x for x in devices if x['id']==device);assert 'install_apk' in d['actions'];assert 'ui_tap' not in d['actions']
  tap('Apps über Ankaï installieren');capture('install-setup-1.6.0.png');command=queue();(out/'install-qa-current.json').write_text(json.dumps({'commandId':command,'base':base,'deviceId':device}))
  if mode=='pending':print('Pending session prepared for local grant-revocation test',flush=True);raise SystemExit(0)
  shell('cmd','statusbar','expand-notifications')
  tree=dump()
  for group in tree.iter('node'):
   if group.get('resource-id','').endswith('expandableNotificationRow') and sum('APK installieren:' in n.get('text','') for n in group.iter('node'))>1:
    expand=next((n for n in group.iter('node') if n.get('resource-id','').endswith('expand_button')),None)
    if expand is not None:
     r=list(map(int,re.findall(r'\d+',expand.get('bounds'))));shell('input','tap',str((r[0]+r[2])//2),str((r[1]+r[3])//2));break
  tap('APK installieren: org.ankai.uifixture');time.sleep(1);capture('install-android-confirmation.png')
  print('Android confirmation ready for inspection; no remote approval performed',flush=True)
 elif mode in ('confirm','cancel'):
  current=json.loads((out/'install-qa-current.json').read_text());tap('Install' if mode=='confirm' else 'Cancel')
  deadline=time.monotonic()+25
  while time.monotonic()<deadline:
   c=api(f'/api/run/devices/{device}/commands/'+current['commandId'])['command']
   if c.get('result'):break
   time.sleep(.5)
  r=c.get('result');assert r,r
  assert r['status']==('ok' if mode=='confirm' else 'error'),r
  if mode=='confirm':assert r['data']['installed'] is True and r['data']['androidStatus']==0;assert 'package:' in shell('pm','path','org.ankai.uifixture')
  (out/('install-native-'+mode+'.json')).write_text(json.dumps(r,ensure_ascii=False,indent=2));print(mode,'PASS',r['status'],flush=True)

 elif mode=='revoke':
  current=json.loads((out/'install-qa-current.json').read_text());tap('Apps über Ankaï installieren');tap('APK-Installationsanfragen für Ankaï erlauben')
  deadline=time.monotonic()+15
  while time.monotonic()<deadline:
   cmd=api(f'/api/run/devices/{device}/commands/'+current['commandId'])['command'];d=next(x for x in api('/api/run/devices')['devices'] if x['id']==device)
   if cmd.get('result') and 'install_apk' not in d['actions']:break
   time.sleep(.5)
  assert cmd.get('result',{}).get('status')=='error';assert 'install_apk' not in d['actions']
  (out/'install-native-revoked.json').write_text(json.dumps(cmd['result'],ensure_ascii=False,indent=2));print('Local install grant revocation PASS',flush=True)
