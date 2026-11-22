#!/usr/bin/env python3
"""Review the explicitly isolated visual fixture as a second administrator for browser recovery."""
import json,urllib.request,http.cookiejar,uuid,sys,os
from pathlib import Path
root=Path(__file__).resolve().parents[1]
c=json.loads((root/'.local/test/visual-runtime.json').read_text());other=json.loads((root/'.local/test/visual-other-admin.json').read_text())
assert c['baseUrl'].startswith('http://127.0.0.1:') and c['boundary']=='isolated MySQL/Redis; fake WeChat/COS'
domain=sys.argv[1];assert domain in ('student','realname')
o=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def call(path,body=None,headers={}):
 r=o.open(urllib.request.Request(c['baseUrl']+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json',**headers}));v=json.load(r);return v['data']
csrf=call('/api/admin/auth/csrf')['csrfToken'];call('/api/admin/auth/login',other,{'X-CSRF-Token':csrf});csrf=call('/api/admin/auth/csrf')['csrfToken']
path='/api/admin/'+domain+'-verifications/'+c['applications'][domain];detail=call(path);assert detail['application']['status']=='pending'
b={k:detail[v] for k,v in [('caseId','caseId'),('expectedVersion','version'),('expectedCaseVersion','caseVersion')]};b['remark']='C14隔离测试：由另一管理员先行审核'
result=call(path+'/reject',b,{'X-CSRF-Token':csrf,'Idempotency-Key':uuid.uuid4().hex});detail=call(path)
proof={'domain':domain,'applicationId':c['applications'][domain],'state':detail['application']['status'],'reviewedBy':detail['reviewedByAdminUserId'],'action':result,'boundary':c['boundary']}
Path(os.environ['MJ_EVIDENCE_DIR'],'other-review-'+domain+'.json').write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'domain':domain,'state':proof['state'],'secondAdminApplied':True}))
