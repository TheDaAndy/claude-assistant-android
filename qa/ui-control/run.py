import os,time,json,uuid,base64,urllib.request
from pathlib import Path
base=os.environ['ANKAI_UI_TEST_BASE'].rstrip('/')
if not base.startswith('http://127.0.0.1:'):raise SystemExit('This fixture test is restricted to an isolated localhost test server.')
h={'Authorization':'Bearer '+os.environ['ANKAI_UI_TEST_RUN_TOKEN'],'x-chattool-session-id':os.environ['ANKAI_UI_TEST_SESSION_ID'],'x-chattool-run-id':os.environ['ANKAI_UI_TEST_RUN_ID']}
def api(path,method='GET',body=None,headers=None):
 headers={**h,'Content-Type':'application/json',**(headers or {})}
 return json.load(urllib.request.urlopen(urllib.request.Request(base+path,method=method,headers=headers,data=json.dumps(body).encode() if body is not None else None),timeout=20))
root='/api/run/devices';devices=api(root)['devices'];d=next(d for d in devices if d['id']==os.environ['ANKAI_UI_TEST_DEVICE_ID']);id=d['id'];records=[];pkg='org.ankai.uifixture'
output=Path(os.environ['ANKAI_UI_TEST_OUTPUT']);output.mkdir(parents=True,exist_ok=True)
assert d['control']['uiEnabled'] and d['control']['uiReady'] and d['control']['uid'] is None and not d['control']['enabled'];assert 'remote_shell' not in d['actions']
def do(action,params=None,success=True):
 c=api(f'{root}/{id}/commands','POST',{'action':action,'params':params or {},'clientRequestId':'a11y_'+uuid.uuid4().hex},h)['command'];start=time.monotonic()
 for _ in range(100):
  r=api(f'{root}/{id}/commands/'+c['id'],headers=h)['command']
  if r.get('result'):break
  time.sleep(.2)
 result=r.get('result');assert result,r
 data=result.get('data');records.append({'action':action,'params':params,'seconds':round(time.monotonic()-start,2),'status':result['status'],'data':{k:v for k,v in data.items() if k!='base64'} if isinstance(data,dict) else data})
 (output/'rootless-native-e2e.json').write_text(json.dumps({'app':'1.4.0','api':35,'root':False,'shizuku':False,'projectlessRun':True,'scope':'user','commands':records},ensure_ascii=False,indent=2))
 assert result['status']==('ok' if success else 'error'),result if not isinstance(data,dict) or 'base64' not in data else result['status'];return data

def snap():return do('ui_snapshot')
def wait_package():
 for _ in range(10):
  s=snap()
  if s['packageName']==pkg:return s
  time.sleep(.3)
 raise Exception('Wrong foreground app')
def tap_node(node):
 b=node['bounds'];do('ui_tap',{'packageName':pkg,'x':(b[0]+b[2])//2,'y':(b[1]+b[3])//2})
def textnode(s,text):return next(n for n in s['nodes'] if n.get('text','').casefold()==text.casefold())
do('ui_launch',{'packageName':pkg});s=wait_package();assert 'fixture-secret' not in json.dumps(s);assert any(n['password'] for n in s['nodes']);print('Projectless run -> launch and redacted native snapshot: PASS',flush=True)
tap_node(textnode(s,'Increment counter'));s=snap();assert any(n.get('text')=='Counter 1' for n in s['nodes']);print('Remote tap changed native counter: PASS',flush=True)
plain=next(n for n in s['nodes'] if n.get('description')=='Plain test field');tap_node(plain);do('ui_text',{'packageName':pkg,'text':'Ankai rootless input'});s=snap();assert any(n.get('text')=='Ankai rootless input' for n in s['nodes']);print('Remote text entry: PASS',flush=True)
do('ui_global',{'target':'back'});s=snap();password=next(n for n in s['nodes'] if n['password']);tap_node(password);do('ui_text',{'packageName':pkg,'text':'FORBIDDEN'},False);do('ui_global',{'target':'back'});print('Password write denied: PASS',flush=True)
do('ui_tap',{'packageName':'com.wrong.foreground','x':50,'y':50},False);print('Wrong foreground denied: PASS',flush=True)
image=do('ui_screenshot');(output/'rootless-test-screen.jpg').write_bytes(base64.b64decode(image['base64']));assert max(image['width'],image['height'])<=720;print('Rootless JPEG capture: PASS',flush=True)
s=snap();tap_node(textnode(s,'Protect screenshot'));do('ui_screenshot',success=False);print('Secure window screenshot denied: PASS',flush=True)
do('ui_swipe',{'packageName':pkg,'x':230,'y':800,'endX':230,'endY':250,'durationMs':400});s=snap();assert not any(n.get('text')=='Ankaï UI test fixture' for n in s['nodes']);print('Remote swipe scrolled native view: PASS',flush=True)
do('ui_global',{'target':'home'});time.sleep(.5);s=snap();assert s['packageName']!=pkg;print('Remote Home: PASS',flush=True)
