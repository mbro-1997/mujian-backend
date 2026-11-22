#!/usr/bin/env python3
"""Test-only UI lost-response probe; upstream must be protected isolated visual fixture."""
import http.client, http.server, json, re, os, threading, sys
from pathlib import Path
from urllib.parse import urlsplit
root=Path(__file__).resolve().parents[1]
config=Path(os.environ.get('MJ_UI_FIXTURE',str(root/'.local/test/visual-runtime.json')))
fixture=json.loads(config.read_text())
if fixture.get('boundary')!='isolated MySQL/Redis; fake WeChat/COS':raise SystemExit('refuse non-isolated upstream')
upstream=urlsplit(fixture['baseUrl'])
if upstream.hostname!='127.0.0.1' or upstream.port==18080:raise SystemExit('requires random isolated port')
control=root/'.local/test/ui-fault.json'; trace=root/'.local/test/ui-fault-trace.jsonl'
lock=threading.Lock()
class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def handle_request(self):
  body=self.rfile.read(int(self.headers.get('Content-Length','0')))
  mode=None
  if self.command=='POST' and (re.fullmatch(r'/api/admin/(realname|student)-verifications/[0-9a-f]{32}/(approve|reject)',self.path) or re.fullmatch(r'/api/me/(realname|student)-verifications',self.path)):
   with lock:
    settings=json.loads(control.read_text()) if control.exists() else {}
    mode=settings.get('mode')
    if mode in ('before','after'):
     control.write_text('{}');os.chmod(control,0o600)
    with open(trace,'a') as out:out.write(json.dumps({'path':self.path,'idempotencyKey':self.headers.get('Idempotency-Key'),'mode':mode})+'\n')
    os.chmod(trace,0o600)
  if mode!='before':
   conn=http.client.HTTPConnection(upstream.hostname,upstream.port,timeout=20)
   headers={k:v for k,v in self.headers.items() if k.lower() not in ('host','connection','content-length')}
   conn.request(self.command,self.path,body,headers);response=conn.getresponse();data=response.read();status=response.status;result_headers=response.getheaders();conn.close()
  if mode in ('before','after'):
   data=json.dumps({'code':'SERVICE_UNAVAILABLE','reasonCode':'DATABASE_UNAVAILABLE','message':'controlled isolated response loss','traceId':'c1400000000040008000000000000001','data':None}).encode();status=503;result_headers=[('Content-Type','application/json'),('Cache-Control','no-store')]
  self.send_response(status)
  for k,v in result_headers:
   if k.lower() not in ('transfer-encoding','content-length','connection'):self.send_header(k,v)
  self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
 do_GET=handle_request
 do_POST=handle_request
port=18080 if sys.argv[1:]==['--native-port'] else 0
if sys.argv[1:] and port==0:raise SystemExit('only --native-port allowed')
server=http.server.ThreadingHTTPServer(('127.0.0.1',port),Handler)
p=root/('.local/test/native-proxy.json' if port else '.local/test/ui-proxy.json');p.write_text(json.dumps({'baseUrl':f'http://127.0.0.1:{server.server_port}','pid':os.getpid()}));os.chmod(p,0o600)
print('isolated fault proxy ready',flush=True);server.serve_forever()
