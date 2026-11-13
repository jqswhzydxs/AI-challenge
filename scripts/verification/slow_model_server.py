# Local protocol fixture: no external model, no real credentials, no business logging.
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import json,time
calls=0
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_GET(self):
  body=json.dumps({'calls':calls}).encode();self.send_response(200);self.end_headers();self.wfile.write(body)
 def do_POST(self):
  global calls
  self.rfile.read(int(self.headers.get('Content-Length','0')));calls+=1
  time.sleep(35)
  try:
   self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers()
   self.wfile.write(json.dumps({'id':'fixture','object':'chat.completion','created':0,'model':'fixture','choices':[{'index':0,'message':{'role':'assistant','content':'{}'},'finish_reason':'stop'}]}).encode())
  except (BrokenPipeError,ConnectionResetError):pass
ThreadingHTTPServer(('0.0.0.0',8000),Handler).serve_forever()
