#!/usr/bin/env python3
"""Probe parent deletion RESTRICT in the named integration DB; always rollback.
Only count the constraint MySQL actually reports, never infer pass from another FK.
"""
import json, os, re, sys, uuid, datetime as dt
from pathlib import Path
import pymysql
p=dict(x.split('=',1) for x in Path(os.environ['MJ_TEST_CONFIG']).read_text().splitlines() if '=' in x and not x.startswith('#'))
m=re.fullmatch(r'jdbc:mysql://127\.0\.0\.1:3306/(darkmujian_d1_test_[a-zA-Z0-9_]+)(?:\?.*)?',p['spring.datasource.url'])
if not m: sys.exit('REFUSED: dedicated local integration database required')
c=pymysql.connect(host='127.0.0.1',port=3306,user=p['spring.datasource.username'],password=p['spring.datasource.password'],database=m[1],autocommit=False,cursorclass=pymysql.cursors.DictCursor)
q=c.cursor()
def rows(sql,args=()): q.execute(sql,args); return q.fetchall()
report={'database':m[1],'transaction':'rollback only','probes':[],'gaps':[]}
try:
 tables=[t['table'] for t in json.loads((Path(__file__).resolve().parents[1]/'src/test/resources/contracts/d2a.dictionary.json').read_text())['tables']]
 # Missing nullable references get explicit rollback-only fixtures.
 uid=lambda:uuid.uuid4().hex
 now=dt.datetime.now().replace(microsecond=0)
 def insert(table,data):q.execute('INSERT INTO `'+table+'` ('+','.join('`'+k+'`' for k in data)+') VALUES ('+','.join(['%s']*len(data))+')',list(data.values()))
 actor=rows('SELECT user_id FROM admin_account LIMIT 1')[0]['user_id']
 tid,rid=uid(),uid()
 insert('coupon_template',dict(id=tid,template_code='D2_PARENT_'+uid(),template_name='虚构父外键夹具',template_version=1,coupon_type='fixed_amount',face_amount_fen=100,threshold_rent_amount_fen=0,applicable_scope_type='all',cost_bearer='shared_by_settlement_ratio',validity_type='relative_days',valid_days=1,refund_return_policy='return_with_extension',return_extension_days=7,issued_count=0,template_status='draft',created_by_admin_user_id=actor,created_at=now,updated_at=now))
 insert('coupon_reward_rule',dict(id=rid,event_type='first_realname_verified',rule_version=2147483000,template_id=tid,status='draft',created_by_admin_user_id=actor,created_at=now))
 reward=rows('SELECT id FROM coupon_reward_record LIMIT 1')[0]
 q.execute("UPDATE coupon_reward_record SET rule_id=%s,ineligible_reason='ROLE_NOT_ELIGIBLE' WHERE id=%s",(rid,reward['id']))
 parent=rows('SELECT * FROM admin_case_action LIMIT 1')[0]
 child=dict(parent);child.update(id=uid(),action_no='ACTION'+uid(),idempotency_key='parent-fixture:'+uid(),parent_action_id=parent['id'],step_no=1,action_type='fixture_child');insert('admin_case_action',child)
 for table in tables:
  fks=rows('SELECT CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND REFERENCED_TABLE_NAME IS NOT NULL',(table,))
  for fk in fks:
   constraint=fk['CONSTRAINT_NAME'];parent_table=fk['REFERENCED_TABLE_NAME'];col=fk['COLUMN_NAME']
   samples=rows('SELECT id,`'+col+'` AS ref FROM `'+table+'` WHERE `'+col+'` IS NOT NULL LIMIT 1')
   if not samples:report['gaps'].append({'constraint':constraint,'reason':'no child fixture'});continue
   sample=samples[0]
   q.execute('SAVEPOINT parent_probe')
   try:
    original=rows('SELECT * FROM `'+parent_table+'` WHERE `'+fk['REFERENCED_COLUMN_NAME']+'`=%s',(sample['ref'],))[0]
    generated={x['COLUMN_NAME'] for x in rows("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND EXTRA LIKE '%%GENERATED%%'",(parent_table,))}
    clone={k:v for k,v in original.items() if k not in generated};clone['id']=uid()
    if parent_table=='sys_user':clone.update(phone=None,phone_verified=0,phone_source=None,phone_verified_at=None)
    elif parent_table=='platform_rule_document':clone.update(rule_version='parent-'+uid()[:16],rule_status='draft')
    elif parent_table=='admin_case':clone.update(case_no='CASE'+uid(),active_case_key='parent:'+uid() if clone['active_case_key'] else None)
    elif parent_table=='admin_case_action':clone.update(action_no='ACTION'+uid(),idempotency_key='parent:'+uid(),parent_action_id=None,step_no=None)
    elif parent_table=='coupon_template':clone.update(template_code='D2_PARENT_'+uid())
    elif parent_table=='coupon_reward_rule':clone.update(rule_version=2147483001,status='draft',activated_at=None,ended_at=None)
    else:raise AssertionError('Unreviewed parent '+parent_table)
    insert(parent_table,clone)
    if table=='admin_case' and col=='created_by_admin_user_id':q.execute("UPDATE admin_case SET created_by_type='admin',created_by_admin_user_id=%s WHERE id=%s",(clone['id'],sample['id']))
    else:q.execute('UPDATE `'+table+'` SET `'+col+'`=%s WHERE id=%s',(clone['id'],sample['id']))
    try:
     q.execute('DELETE FROM `'+parent_table+'` WHERE id=%s',(clone['id'],))
     report['gaps'].append({'constraint':constraint,'reason':'parent deletion unexpectedly succeeded'})
    except pymysql.MySQLError as e:
     if e.args[0]!=1451:raise
     reported=re.search(r'CONSTRAINT `([^`]+)`',str(e));actual=reported.group(1) if reported else None
     if actual==constraint:report['probes'].append({'table':table,'constraint':constraint,'parent':parent_table,'mysqlError':1451,'isolatedParent':True})
     else:report['gaps'].append({'constraint':constraint,'reportedConstraint':actual})
   finally:q.execute('ROLLBACK TO SAVEPOINT parent_probe')
finally:c.rollback();c.close()
report['summary']={'verifiedParentDeleteRestrictions':len(report['probes']),'notIndividuallyVerified':len(report['gaps'])}
Path(os.environ['MJ_EVIDENCE_DIR'],'d2-parent-fk-probes.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report['summary']));print('All DELETE probes rolled back; no constraints disabled.')
if report['gaps']:sys.exit(2)
