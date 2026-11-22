#!/usr/bin/env python3
"""Validate real C HTTP samples from explicitly selected local execution logs, not design examples."""
import json,re,sys,collections,warnings,os
from pathlib import Path
warnings.filterwarnings('ignore',category=DeprecationWarning)
from jsonschema import Draft202012Validator,FormatChecker,RefResolver
root=Path(__file__).resolve().parents[1]; evidence=Path(os.environ['MJ_EVIDENCE_DIR'])
contract=json.loads((root/'src/main/resources/contracts/d2c.openapi.json').read_text())
traces=set()
for name in sys.argv[1:]:traces.update(re.findall(r'traceId=([0-9a-f]{32})',Path(name).read_text()))
excluded=json.loads((evidence/"superseded-fixture-traces.json").read_text())
seen=collections.Counter(); errors=[]; n=0
paths=sorted(contract['paths'],key=lambda p:(p.count('{'),-len(p)))
for line,raw in enumerate((evidence/'d2-http.jsonl').read_text().splitlines(),1):
 s=json.loads(raw)
 if s['body'].get('traceId') not in traces or s['body'].get('traceId') in excluded:continue
 path=s['path'].split('?')[0]; match=next((p for p in paths if re.fullmatch(re.sub(r'\{[^}]+\}','[^/]+',p),path)),None)
 if not match:continue
 op=contract['paths'][match].get(s['method'].lower())
 if not op:continue
 n+=1;response=op['responses'].get(str(s['status']))
 if response is None:errors.append({'line':line,'operation':op['operationId'],'error':'undeclared HTTP status'});continue
 v=Draft202012Validator(response['content']['application/json']['schema'],resolver=RefResolver.from_schema(contract),format_checker=FormatChecker())
 for err in v.iter_errors(s['body']):errors.append({'line':line,'operation':op['operationId'],'path':err.json_path,'validator':err.validator})
 seen[(op['operationId'],str(s['status']),s['body'].get('reasonCode','OK'))]+=1
ops=[]
for path,methods in contract['paths'].items():
 for m,op in methods.items():
  observed=[{'http':k[1],'reason':k[2],'count':v} for k,v in seen.items() if k[0]==op['operationId']]
  ops.append({'operationId':op['operationId'],'method':m,'path':path,'observed':observed,'state':'有真实HTTP样本' if observed else '未采集真实HTTP样本'})
report={'contractVersion':contract['info']['version'],'boundary':'Only selected run trace IDs; real HTTP/MySQL/Redis, fake WeChat/COS. Old pre-revision samples retained in raw file but excluded. No real-channel or native proof inferred.','supersededFixtureTraces':excluded,'runLogs':[Path(p).name for p in sys.argv[1:]],'samples':n,'operationsObserved':sum(bool(o['observed']) for o in ops),'errors':errors,'operations':ops}
(evidence/os.environ.get('MJ_C_SCHEMA_REPORT','c-http-schema-validation.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k in ('contractVersion','samples','operationsObserved','errors')},ensure_ascii=False));sys.exit(bool(errors))
