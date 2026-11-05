"""Local-only deterministic inbox fixture, not a production server. No real credentials."""
import json
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
state=Path('/tmp/ankai-notification-fixture-state.json')
if not state.exists(): state.write_text(json.dumps({'notifications':[]}))
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*a): pass
 def do_GET(self):
  if self.path=='/api/auth/session': data={'user':{'username':'qa'}}
  elif self.path=='/api/projects': data={'projects':[]}
  elif self.path=='/api/personal': data={'decisions':[],'agents':[],'goals':[]}
  elif self.path=='/api/notifications': data=json.loads(state.read_text())
  else: self.send_error(404);return
  self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(json.dumps(data).encode())
ThreadingHTTPServer(('127.0.0.1',18764),Handler).serve_forever()
