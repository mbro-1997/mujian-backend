#!/usr/bin/env python3
"""Validate captured actual HTTP bodies; never count contract examples as runtime coverage."""
import collections,csv,json,os,re,sys,warnings
from pathlib import Path
warnings.filterwarnings('ignore',category=DeprecationWarning)
from jsonschema import Draft202012Validator,FormatChecker,RefResolver
root=Path(__file__).resolve().parents[1]
contract=json.loads((root/'src/main/resources/contracts/d2a.openapi.json').read_text())
evidence=Path(os.environ['MJ_EVIDENCE_DIR']);samples=[json.loads(x) for x in (evidence/'d2-http.jsonl').read_text().splitlines() if x]
seen=collections.Counter();errors=[];count=0
for i,sample in enumerate(samples,1):
 path=sample['path'].split('?')[0];match=next(((p,v.get(sample['method'].lower())) for p,v in contract['paths'].items() if re.fullmatch(re.sub(r'\{[^}]+\}',r'[^/]+',p),path)),None)
 if not match or not match[1]:continue
 p,op=match;response=op['responses'].get(str(sample['status']))
 if not response:errors.append({'line':i,'operation':op['operationId'],'error':'undeclared HTTP status'});continue
 schema=response['content']['application/json']['schema'];validator=Draft202012Validator(schema,resolver=RefResolver.from_schema(contract),format_checker=FormatChecker())
 failures=list(validator.iter_errors(sample['body']))
 if failures:errors.append({'line':i,'operation':op['operationId'],'errors':[{'path':str(e.json_path),'validator':e.validator} for e in failures]})
 count+=1;seen[(op['operationId'],str(sample['status']),sample['body'].get('reasonCode') or 'OK')]+=1
matrix=[];summary=[]
for path,methods in contract['paths'].items():
 for method,op in methods.items():
  if method not in ('get','post'):continue
  actual=[]
  for status,response in op['responses'].items():
   reasons=['OK'] if status=='200' else response['description'].split(' / ')
   for reason in reasons:
    n=seen[(op['operationId'],status,reason)];matrix.append(dict(operationId=op['operationId'],method=method.upper(),path=path,http=status,reasonCode=reason,observations=n,state='已命中' if n else '尚未命中'))
    if n:actual.append(status+'/'+reason)
  summary.append({'operationId':op['operationId'],'method':method.upper(),'path':path,'observed':actual})
with (evidence/'operation-http-reason-matrix.csv').open('w') as f:
 w=csv.DictWriter(f,fieldnames=list(matrix[0]));w.writeheader();w.writerows(matrix)
report={'boundary':'Actual HTTP samples. Isolated tests use real MySQL/Redis; WeChat/COS substitutes, controlled loopback real HTTP adapter tests and official-channel probes are separately labelled. C16 historical boundary supplement: c16-upstream-sample-boundaries.json. A response hit does not prove successful real phone authorization.','samplesValidated':count,'operationsObserved':len({x[0] for x in seen}),'declaredCombinations':len(matrix),'observedCombinations':sum(x['observations']>0 for x in matrix),'errors':errors,'operations':summary}
(evidence/'http-schema-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
(evidence/'23操作实际响应覆盖.md').write_text('# 23操作实际HTTP采样\n\n完整组合见 `operation-http-reason-matrix.csv`。所有尚未命中组合保留未执行，不默认视为不可达。只计 `d2-http.jsonl`，不计规格样例。外部渠道按样本及C16边界补充区分替身、受控HTTP与官方探针；HTTP、MySQL、Redis为真实本机服务。修复前命中与修复后适用性见operation-conclusions.csv。\n\n|操作|方法与路径|实际命中HTTP/reason|\n|---|---|---|\n'+''.join('|'+x['operationId']+'|'+x['method']+' `'+x['path']+'`|'+', '.join(x['observed'])+'|\n' for x in summary))
print(json.dumps({k:v for k,v in report.items() if k not in ('operations','boundary')},ensure_ascii=False));sys.exit(bool(errors))
