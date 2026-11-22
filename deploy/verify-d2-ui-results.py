#!/usr/bin/env python3
"""Verify actual isolated browser requests against DB, and all domain list HTTP filters."""
import sys,os,json,re,urllib.request,urllib.error,http.cookiejar
from pathlib import Path
import pymysql
root=Path(__file__).resolve().parents[1]
cfg=json.loads((root/'.local/test/visual-runtime.json').read_text())
if cfg['boundary']!='isolated MySQL/Redis; fake WeChat/COS':raise SystemExit('isolated fixture required')
p=dict(line.split('=',1) for line in Path(os.environ['MJ_TEST_CONFIG']).read_text().splitlines() if '=' in line and not line.startswith('#'))
m=re.fullmatch(r'jdbc:mysql://127\.0\.0\.1:3306/(darkmujian_d1_test_[a-zA-Z0-9_]+)(?:\?.*)?',p['spring.datasource.url'])
if not m:raise SystemExit('protected test database required')
o=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def call(path,body=None,headers={}):
 req=urllib.request.Request(cfg['baseUrl']+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json',**headers})
 try:r=o.open(req)
 except urllib.error.HTTPError as e:r=e
 return r.status,json.load(r)
_,csrf=call('/api/admin/auth/csrf');status,login=call('/api/admin/auth/login',{'username':cfg['username'],'password':cfg['password']},{'X-CSRF-Token':csrf['data']['csrfToken']});assert status==200
results=[]
for domain,statuses in ([] if '--recovery-only' in sys.argv else [('realname',['pending','verified','rejected']),('student',['','pending','approved','rejected','expired'])]):
 for value in statuses+['invalid',''] if domain=='realname' else statuses+['invalid']:
  query='page=1&pageSize=20'+('&status='+value if value else '')
  path='/api/admin/'+domain+'-verifications?'+query
  status,body=call(path);expected=400 if value=='invalid' else 200
  assert status==expected,(domain,value,status,body.get('reasonCode'))
  results.append({'domain':domain,'path':path,'http':status,'reason':body.get('reasonCode'),'returnedRows':len(body.get('data',{}).get('items',[])) if body.get('data') else None})
  if status==200 and value:assert all(r['status']==value for r in body['data']['items'])
 for suffix in ['status=','status=expired'] if domain=='realname' else ['status=']:
  path='/api/admin/'+domain+'-verifications?'+suffix
  status,body=call(path);assert status==400
  results.append({'domain':domain,'path':path,'http':status,'reason':body.get('reasonCode')})
con=pymysql.connect(host='127.0.0.1',user=p['spring.datasource.username'],password=p['spring.datasource.password'],database=m[1],cursorclass=pymysql.cursors.DictCursor)
traces=[json.loads(x) for x in (root/'.local/test/ui-fault-trace.jsonl').read_text().splitlines()]
verification=[]
with con.cursor() as q:
 for domain,appid in ([] if '--filters-only' in sys.argv else cfg['applications'].items()):
  t=[x for x in traces if '/'+appid+'/' in x['path']];assert len(t)==2;assert t[0]['idempotencyKey']==t[1]['idempotencyKey'];assert [x['mode'] for x in t]==['before','after']
  q.execute('SELECT COUNT(*) AS n FROM admin_case_action WHERE target_id=%s AND action_status=\'succeeded\'',(appid,));n=q.fetchone()['n'];assert n==1
  q.execute('SELECT verification_status FROM user_'+domain+'_verification WHERE id=%s',(appid,));state=q.fetchone()['verification_status'];assert state=='rejected'
  verification.append({'domain':domain,'applicationId':appid,'requests':t,'successfulActions':n,'finalStatus':state})
con.close()
out={'boundary':cfg['boundary'],'database':m[1],'filters':results,'browserRecovery':verification}
Path(os.environ['MJ_EVIDENCE_DIR'],'admin-filters-http.json' if '--filters-only' in sys.argv else 'admin-browser-http-db.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'listHttpChecks':len(results),'browserDomainRecoveries':len(verification),'sameKeyConfirmed':True if verification else None,'successfulActionsPerApplication':[1 for _ in verification]}))
