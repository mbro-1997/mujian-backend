#!/usr/bin/env python3
"""Actual HTTP boundary probes against the protected visual fixture; no successful writes intended."""
import json,os,re,uuid,urllib.request,urllib.error,http.cookiejar
from pathlib import Path
root=Path(__file__).resolve().parents[1];cfg=json.loads((root/'.local/test/visual-runtime.json').read_text())
assert cfg['boundary']=='isolated MySQL/Redis; fake WeChat/COS' and cfg['baseUrl'].startswith('http://127.0.0.1:')
contract=json.loads((root/'src/main/resources/contracts/d2a.openapi.json').read_text());evidence=Path(os.environ['MJ_EVIDENCE_DIR'])
opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def authcall(path,body=None,headers={}):
 r=opener.open(urllib.request.Request(cfg['baseUrl']+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json',**headers}));return json.load(r)['data']
csrf=authcall('/api/admin/auth/csrf')['csrfToken'];authcall('/api/admin/auth/login',{'username':cfg['username'],'password':cfg['password']},{'X-CSRF-Token':csrf});csrf=authcall('/api/admin/auth/csrf')['csrfToken']
admin_cookie=next(h for h in opener.handlers if isinstance(h,urllib.request.HTTPCookieProcessor)).cookiejar
cookie='; '.join(c.name+'='+c.value for c in admin_cookie)
def sample(schema,name=''):
 if '$ref' in schema:return sample(contract['components']['schemas'][schema['$ref'].split('/')[-1]],schema['$ref'].split('/')[-1])
 if 'const' in schema:return schema['const']
 if 'enum' in schema:return schema['enum'][0]
 typ=schema.get('type');typ=next((x for x in typ if x!='null'),None) if isinstance(typ,list) else typ
 if typ=='object':return {k:sample(schema['properties'][k],k) for k in schema.get('required',[])}
 if typ=='array':return [sample(schema['items']) for _ in range(schema.get('minItems',1))]
 if typ=='integer':return schema.get('minimum',0)
 if typ=='boolean':return True
 if name in ('Id','RequestId'):return uuid.uuid4().hex
 if schema.get('pattern')=='^[0-9a-f]{32}$':return uuid.uuid4().hex
 if name=='idCard':return '11010519491231002X'
 return 'x'*max(1,schema.get('minLength',1))
results=[]
for template,methods in contract['paths'].items():
 for method,op in methods.items():
  if method not in ('get','post'):continue
  path=re.sub(r'\{[^}]+\}',lambda _:uuid.uuid4().hex,template)
  if path=='/api/public/rules':path+='?scene=register'
  content=op.get('requestBody',{}).get('content',{});upload='multipart/form-data' in content
  body=None if method=='get' else json.dumps(sample(content.get('application/json',{}).get('schema',{}))).encode()
  headers={'Content-Type':'application/json'}
  if upload:body=b'--C14\r\nContent-Disposition: form-data; name="consentAccepted"\r\n\r\ntrue\r\n--C14--\r\n';headers={'Content-Type':'multipart/form-data; boundary=C14'}
  variants=[('invalid_activity',{'X-Session-Activity':'invalid'},body,400,'REQUEST_INVALID')]
  if method=='post':variants += [('unsupported_content_type',{'Content-Type':'text/plain'},b'x',415,'CONTENT_TYPE_UNSUPPORTED'),('oversize',{},b'x'*(6*1024*1024+1 if upload else 8193),413,'PAYLOAD_TOO_LARGE')]
  if not path.startswith('/api/public/'):
   variants += [('anonymous',{},body,401,'LOGIN_REQUIRED'),('wrong_client',{'Cookie':'mj_admin_dev='+cfg['miniToken']} if path.startswith('/api/admin/') else {'Authorization':'Bearer '+next(c.value for c in admin_cookie if c.name=='mj_admin_dev')},body,403,'CLIENT_TYPE_MISMATCH')]
   if path.startswith('/api/admin/') and method=='post':variants += [('invalid_csrf',{'Cookie':cookie},body,403,'CSRF_INVALID')]
  for scenario,extra,data,expected,reason in variants:
   request=urllib.request.Request(cfg['baseUrl']+path,data=data,method=method.upper(),headers={**headers,"Idempotency-Key":uuid.uuid4().hex,"X-CSRF-Token":"invalid-csrf",**extra})
   if upload and scenario=='oversize':
    from urllib.parse import urlsplit
    endpoint=urlsplit(cfg['baseUrl']);con=__import__('http.client',fromlist=['HTTPConnection']).HTTPConnection(endpoint.hostname,endpoint.port,timeout=20)
    con.putrequest(method.upper(),path);con.putheader('Content-Type',headers['Content-Type']);con.putheader('Content-Length',str(len(data)));con.endheaders();r=con.getresponse()
   else:
    try:r=urllib.request.urlopen(request,timeout=20)
    except urllib.error.HTTPError as e:r=e
   response=json.load(r);actual=response.get('reasonCode')
   results.append({'operationId':op['operationId'],'scenario':scenario,'http':r.status,'reason':actual,'expectedHttp':expected,'expectedReason':reason,'matched':r.status==expected and actual==reason})
   with (evidence/'d2-http.jsonl').open('a') as out:out.write(json.dumps({'method':method.upper(),'path':path,'status':r.status,'body':response})+'\n')
(evidence/'boundary-matrix-probes.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'probes':len(results),'matched':sum(x['matched'] for x in results),'mismatches':[x for x in results if not x['matched']]},ensure_ascii=False))
