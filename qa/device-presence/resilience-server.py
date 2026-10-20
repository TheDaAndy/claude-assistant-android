"""Local-only fault fixture. No real device credentials; drives native channel regression."""
import json,time,threading
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
lock=threading.Lock();state={'commands':[],'results':[],'pollFaults':[],'resultFaults':[],'polls':0}
class H(BaseHTTPRequestHandler):
 def reply(self,value,status=200):
  self.send_response(status);self.send_header('Content-Type','application/json');self.end_headers()
  try:self.wfile.write(json.dumps(value).encode())
  except BrokenPipeError:pass
 def do_GET(self):
  if self.path=='/qa/state':
   with lock:self.reply(state)
   return
  if self.path.endswith('/commands'):
   time.sleep(.5)
   with lock:
    state['polls']+=1
    if state['pollFaults']:
     status=state['pollFaults'].pop(0);self.reply({'error':'fixture failure'},status);return
    self.reply({'command':state['commands'].pop(0) if state['commands'] else None})
  else:self.reply({},404)
 def do_POST(self):
  data=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or '{}')
  with lock:
   if self.path=='/qa/queue':
    for k in ['commands','pollFaults','resultFaults']:state[k].extend(data.get(k,[]))
    self.reply({});return
   if self.path.endswith('/result'):
    state['results'].append({'path':self.path,'body':data})
    status=state['resultFaults'].pop(0) if state['resultFaults'] else 200
    self.reply({},status);return
   self.reply({})
 def log_message(self,*a):pass
if __name__=='__main__':ThreadingHTTPServer(('127.0.0.1',18770),H).serve_forever()
