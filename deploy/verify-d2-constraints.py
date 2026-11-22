#!/usr/bin/env python3
"""D2 schema probes, only on the explicitly named local integration database.
All fixture writes and probe mutations are rolled back. No clean/drop/disable-checks.
This supplements, rather than replaces, verify-local.py and TestDatabase tests.
"""
import datetime as dt
import json, os, re, sys, uuid
from pathlib import Path
import pymysql
config=Path(os.environ['MJ_TEST_CONFIG'])
p=dict(line.strip().split('=',1) for line in config.read_text().splitlines() if '=' in line and not line.startswith('#'))
m=re.fullmatch(r'jdbc:mysql://127\.0\.0\.1:3306/(darkmujian_d1_test_[a-zA-Z0-9_]+)(?:\?.*)?',p['spring.datasource.url'])
if not m:sys.exit('REFUSED: only the dedicated local integration database is allowed')
c=pymysql.connect(host='127.0.0.1',port=3306,user=p['spring.datasource.username'],password=p['spring.datasource.password'],database=m[1],autocommit=False,cursorclass=pymysql.cursors.DictCursor)
q=c.cursor();uid=lambda:uuid.uuid4().hex
now=dt.datetime.now().replace(microsecond=0)
report={'database':m[1],'transaction':'rollback only','checkProbes':[],'notNullProbes':[],'foreignKeyProbes':[],'uniqueProbes':[],'gaps':[]}
def rows(sql,args=()):q.execute(sql,args);return q.fetchall()
def insert(table,data):q.execute('INSERT INTO `'+table+'` ('+','.join('`'+k+'`' for k in data)+') VALUES ('+','.join(['%s']*len(data))+')',list(data.values()))
def mutation(table,row,patch,expected):
 q.execute('SAVEPOINT probe')
 try:
  q.execute('UPDATE `'+table+'` SET '+','.join('`'+k+'`=%s' for k in patch)+' WHERE id=%s',list(patch.values())+[row['id']]);code=0;error=''
 except pymysql.MySQLError as e:code=e.args[0];error=str(e)
 finally:q.execute('ROLLBACK TO SAVEPOINT probe')
 return code,error
try:
 dictionary=json.loads((Path(__file__).resolve().parents[1]/'src/test/resources/contracts/d2a.dictionary.json').read_text())
 tables=[t['table'] for t in dictionary['tables']]
 actor=rows("SELECT user_id FROM admin_account LIMIT 1")[0]['user_id']
 fixture_user=uid()
 insert('sys_user',dict(id=fixture_user,nickname='D2约束夹具',phone_verified=0,user_status='active',register_status='new',created_at=now,updated_at=now))
 tid,rid=uid(),uid()
 insert('coupon_template',dict(id=tid,template_code='D2_CONSTRAINT_'+uid(),template_name='虚构约束夹具',template_version=1,coupon_type='fixed_amount',face_amount_fen=100,threshold_rent_amount_fen=0,applicable_scope_type='all',cost_bearer='shared_by_settlement_ratio',validity_type='relative_days',valid_days=1,refund_return_policy='return_with_extension',return_extension_days=7,issued_count=0,template_status='draft',created_by_admin_user_id=actor,created_at=now,updated_at=now))
 insert('coupon_reward_rule',dict(id=rid,event_type='first_realname_verified',rule_version=2147483000,template_id=tid,status='active',activated_at=now,created_by_admin_user_id=actor,created_at=now))
 parent=rows('SELECT * FROM admin_case_action LIMIT 1')[0]
 child={k:v for k,v in parent.items()};child.update(id=uid(),action_no='ACTION'+uid(),idempotency_key='fixture:'+uid(),parent_action_id=parent['id'],step_no=1,action_type='fixture_child');insert('admin_case_action',child)
 for table in tables:
  cols=rows('SELECT COLUMN_NAME,DATA_TYPE,IS_NULLABLE,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s ORDER BY ORDINAL_POSITION',(table,))
  column={x['COLUMN_NAME']:x for x in cols};names=list(column)
  samples=rows('SELECT * FROM `'+table+'` LIMIT 100')
  if not samples:report['gaps'].append(table+': missing valid fixture');continue
  # Keep representatives of each state, including nullable combinations.
  chosen=[];seen=set()
  for r in samples:
   signature=tuple((k,str(v)) for k,v in r.items() if k.endswith('status') or k in ('identity_type','content_type','is_primary','ineligible_reason'))
   if signature not in seen:chosen.append(r);seen.add(signature)
  checks=rows('SELECT tc.CONSTRAINT_NAME,cc.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME=%s AND tc.CONSTRAINT_TYPE=\'CHECK\'',(table,))
  for ck in checks:
   clause=ck['CHECK_CLAUSE'].replace("\\'", "'");found=None
   mentioned=[k for k in names if re.search(r'\b'+re.escape(k)+r'\b',clause)]
   for r in chosen:
    if found:break
    for col in mentioned:
     if found:break
     kind=column[col]['DATA_TYPE']
     values=[None]
     if kind in ('datetime','timestamp'):values += [now-dt.timedelta(days=3660),now+dt.timedelta(days=3660)]
     elif kind in ('int','bigint','tinyint'):values += [-1,0,1,2,6]
     elif kind in ('varbinary','binary'):values += [b'',b'fictional']
     elif kind=='json':values += ['[]','{}','["a"]','["a","a","a","a"]']
     else:values += [r.get('id_card_front_object_key'),r.get('id_card_back_object_key'),' ', '','invalid',uid(),tid,rid,'active','rejected','verified','approved','granted','pending']
     for value in values:
      select=','.join('%s AS `'+k+'`' if k==col else '`'+k+'`' for k in names)
      try:
       result=rows('SELECT ('+clause+') AS valid FROM (SELECT '+select+' FROM `'+table+'` WHERE id=%s) candidate',(value,r['id']))[0]['valid']
      except pymysql.MySQLError:continue
      if result != 0:continue
      code,error=mutation(table,r,{col:value},3819)
      if code==3819:
       actual=re.search(r"Check constraint '([^']+)'",error)
       found={'table':table,'constraint':ck['CONSTRAINT_NAME'],'changedColumn':col,'isNull':value is None,'mysqlError':code,'reportedConstraint':actual.group(1) if actual else None};break
   if found:report['checkProbes'].append(found)
   else:report['gaps'].append(ck['CONSTRAINT_NAME']+': no isolated CHECK rejection found')
  for col in cols:
   name=col['COLUMN_NAME']
   if col['IS_NULLABLE']=='NO' and 'GENERATED' not in col['EXTRA']:
    code,error=mutation(table,samples[0],{name:None},1048)
    assert code==1048,(table,name,code)
    report['notNullProbes'].append({'table':table,'column':name,'mysqlError':code})
  fks=rows('SELECT CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND REFERENCED_TABLE_NAME IS NOT NULL',(table,))
  for fk in fks:
   name=fk['COLUMN_NAME'];hit=None
   for r in samples:
    patch={name:uid()}
    if table=='admin_case' and name=='created_by_admin_user_id':patch['created_by_type']='admin'
    if table=='admin_case_action' and name=='parent_action_id':patch.update(action_type='fixture_child',step_no=1)
    if table=='coupon_reward_record' and name=='rule_id':patch['ineligible_reason']='ROLE_NOT_ELIGIBLE'
    code,error=mutation(table,r,patch,1452)
    if code==1452:hit={'table':table,'constraint':fk['CONSTRAINT_NAME'],'mysqlError':code};break
   if hit:report['foreignKeyProbes'].append(hit)
   else:report['gaps'].append(fk['CONSTRAINT_NAME']+': orphan fixture not reached')
  indexes=rows('SELECT INDEX_NAME,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_csv FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND NON_UNIQUE=0 AND INDEX_NAME<>\'PRIMARY\' GROUP BY INDEX_NAME',(table,))
  for index in indexes:
   target=index['INDEX_NAME'];parts=index['columns_csv'].split(',');hit=False
   for r in samples:
    if any(r[k] is None for k in parts):continue
    clone={k:v for k,v in r.items() if 'GENERATED' not in column[k]['EXTRA']};clone['id']=uid()
    if table=='user_login_identity':
     if target=='a1_phone':clone['user_id']=fixture_user
     if target=='a1_user_phone':clone['phone']=clone['identifier']='+8619900000000'
    elif table=='platform_rule_document':
     if target=='a2_version':clone['rule_status']='draft'
     if target=='a2_active':clone['rule_version']='fixture-'+uid()[:16]
    elif table=='user_realname_verification':
     if target=='a4_request':clone['user_id']=fixture_user
     if target=='a4_active':clone['submit_request_id']=uid()
    elif table=='user_student_verification':
     if target!='a5_number':clone['application_no']='SV'+uid()
     if target!='a5_request':clone['submit_request_id']=uid()
     if target!='a5_pending':clone['user_id']=fixture_user
    elif table=='admin_case':
     if target!='a6_number':clone['case_no']='CASE'+uid()
     if target!='a6_active' and clone['active_case_key']:clone['active_case_key']='fixture:'+uid()
    elif table=='admin_case_relation':
     if target=='a7_relation':clone['is_primary']=0
     if target=='a7_primary':clone['relation_id']=uid()
    elif table=='admin_case_action':
     if target!='a8_number':clone['action_no']='ACTION'+uid()
     if target!='a8_request':clone['idempotency_key']='fixture:'+uid()
     if target!='a8_step':clone['parent_action_id']=clone['step_no']=None
    elif table=='coupon_reward_rule':
     if target=='a11_version':clone.update(status='draft',activated_at=None,ended_at=None)
     if target=='a11_active':clone['rule_version']=2147483001
    q.execute('SAVEPOINT probe')
    try:insert(table,clone)
    except pymysql.MySQLError as e:
     if e.args[0]==1062:
      idx=re.search(r"for key '([^']+)'",str(e))
      if idx and idx.group(1).split('.')[-1]==target:report['uniqueProbes'].append({'table':table,'index':target,'mysqlError':1062});hit=True
    finally:q.execute('ROLLBACK TO SAVEPOINT probe')
    if hit:break
   if not hit:report['gaps'].append(target+': unique index not individually hit')
 report['summary']={k:len(report[k]) for k in ('checkProbes','notNullProbes','foreignKeyProbes','uniqueProbes','gaps')}
 report['note']='CHECK expression was evaluated as FALSE and actual UPDATE rejected with 3819. Redundant constraints may be reported by MySQL under an earlier constraint name. Unique coverage lists only the actual rejected index; it is not an all-index claim.'
finally:c.rollback();c.close()
output=Path(os.environ['MJ_EVIDENCE_DIR'])/'d2-constraint-probes.json';output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report['summary'],ensure_ascii=False));print('All fixture/probe writes rolled back.')
if report['gaps']:sys.exit(2)
