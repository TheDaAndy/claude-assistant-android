"""Local API35 regression; never run against a physical phone or production."""
import json, os, subprocess, time, uuid, urllib.request, urllib.error
from pathlib import Path
f=json.loads(Path(os.environ['ANKAI_QA_FIXTURE']).read_text());base='http://127.0.0.1:'+str(f['port']);out=Path(os.environ['ANKAI_QA_OUTPUT']);out.mkdir(parents=True,exist_ok=True)
adb=[str(Path.home()/'Android/Sdk/platform-tools/adb'),'-s','emulator-5554']
records=[]
def shell(*args):return subprocess.run(adb+['shell',*args],check=True,capture_output=True,text=True).stdout
def api(path,body=None):
 req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(req,timeout=10) as r:return json.load(r)
def state():return api('/qa/state')['availability']
def wait(label,fn,timeout=15):
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  d=state()
  if fn(d):records.append({'check':label,'availability':d});print(label,'PASS',d['state'],flush=True);return d
  time.sleep(.4)
 raise AssertionError((label,state()))
def command(action):
 c=api('/qa/command',{'action':action,'clientRequestId':'presence_'+uuid.uuid4().hex})
 deadline=time.monotonic()+12
 while time.monotonic()<deadline:
  r=api('/qa/command/'+c['id'])
  if r.get('result'):return r['result']
  time.sleep(.25)
 raise AssertionError('Command result timed out')
try:
 wait('screen_on_unlocked',lambda d:d['state']=='ready')
 shell('am','start','-n','org.ankai.uifixture/.MainActivity');time.sleep(1)
 snap=command('ui_snapshot');assert snap['status']=='ok',snap
 node=next(n for n in snap['data']['nodes'] if n.get('text','').lower()=='increment counter');r=node['bounds']
 shell('input','tap',str((r[0]+r[2])//2),str((r[1]+r[3])//2))
 wait('interaction_event',lambda d:d['recentInteraction'] is True)
 wait('interaction_age_decay',lambda d:d['recentInteraction'] is False,40)
 shell('input','keyevent','223')
 wait('display_off',lambda d:d['interactive'] is False and not d['readyForUi'])
 try:command('ui_snapshot');raise AssertionError('UI allowed with display off')
 except urllib.error.HTTPError as e:assert e.code==409
 shell('input','keyevent','224');shell('wm','dismiss-keyguard')
 wait('wake_and_unlock',lambda d:d['state']=='ready')
 # A temporary dummy PIN exists only on the disposable emulator.
 shell('locksettings','set-pin','7391');shell('input','keyevent','223');shell('input','keyevent','224')
 wait('secure_lock',lambda d:d['deviceLocked'] is True and not d['readyForUi'])
 shell('wm','dismiss-keyguard');time.sleep(1)
 shell('input','text','7391');shell('input','keyevent','66')
 wait('local_unlock_restores_ready',lambda d:d['state']=='ready')
 shell('locksettings','clear','--old','7391')
 shell('settings','delete','secure','enabled_accessibility_services')
 wait('permission_revoked',lambda d:d['state']=='ui_unavailable')
 shell('settings','put','secure','enabled_accessibility_services','dev.claude.assistant/dev.claude.assistant.remote.UiControlService')
 shell('settings','put','secure','accessibility_enabled','1')
 wait('permission_restored',lambda d:d['state']=='ready')
 print('Presence native regression PASS',flush=True)
finally:
 # Restore the test emulator even on a failed assertion.
 subprocess.run(adb+['shell','locksettings','clear','--old','7391'],capture_output=True)
 Path(out/'presence-native-report.json').write_text(json.dumps({'records':records},ensure_ascii=False,indent=2))
