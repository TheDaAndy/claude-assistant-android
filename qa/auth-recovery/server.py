"""Loopback-only expired-session fixture. Uses dummy credentials, never production accounts."""
import json
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
records=[]
class H(BaseHTTPRequestHandler):
 def do_GET(self):
  if self.path=='/qa/requests':self.reply(200,records);return
  cookie=self.headers.get('Cookie','');basic=self.headers.get('Authorization','').startswith('Basic ')
  records.append({'path':self.path,'auth':'basic' if basic else 'fresh-cookie' if cookie=='ankai_session=qa_fresh' else 'rejected-cookie'})
  if not basic and cookie!='ankai_session=qa_fresh':self.reply(401,{'error':'expired session'});return
  if self.path=='/api/auth/session':self.reply(200,{'user':{'username':'qa'}},basic)
  elif self.path=='/api/projects':self.reply(200,{'projects':[{'id':'p1','name':'QA Project'}]},basic)
  else:self.reply(404,{})
 def reply(self,status,data,new_cookie=False):
  self.send_response(status);self.send_header('Content-Type','application/json')
  if new_cookie:self.send_header('Set-Cookie','ankai_session=qa_fresh; Path=/; HttpOnly')
  self.end_headers();self.wfile.write(json.dumps(data).encode())
 def log_message(self,*a):pass
ThreadingHTTPServer(('127.0.0.1',18771),H).serve_forever()
