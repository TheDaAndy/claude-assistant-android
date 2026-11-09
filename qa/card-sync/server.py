"""Isolated card fixture: the dashboard refresh deliberately takes 8s after reply."""
import json,time
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
answered=False
class H(BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def send(self,data):
  self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(json.dumps(data).encode())
 def do_GET(self):
  if self.path=='/api/auth/session':return self.send({'user':{'username':'qa'}})
  if self.path=='/api/projects':return self.send({'projects':[]})
  if self.path=='/api/personal':
   if answered:time.sleep(8)
   return self.send({'decisions':[{'id':'qa-card','revision':2 if answered else 1,'kind':'question','status':'answered' if answered else 'proposed','title':'Soll ich vormittags planen?','body':'Testfrage für die sofortige Aktualisierung.','risk':0,'confidence':90}],'agents':[],'goals':[]})
  return self.send({})
 def do_POST(self):
  global answered
  self.rfile.read(int(self.headers.get('Content-Length',0)))
  if self.path.endswith('/respond'):
   answered=True;return self.send({'decision':{'id':'qa-card','revision':2,'kind':'question','status':'answered','title':'Soll ich vormittags planen?','risk':0,'confidence':90}})
  return self.send({})
ThreadingHTTPServer(('127.0.0.1',18765),H).serve_forever()
