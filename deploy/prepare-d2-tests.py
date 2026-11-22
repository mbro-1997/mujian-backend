#!/usr/bin/env python3
"""Create fresh, explicitly named local test databases. Admin password is read locally, never logged.
Usage: python3 deploy/prepare-d2-tests.py --admin-user root
Does not read/write darkmujian, change existing accounts, or replace existing databases.
"""
import argparse, getpass, json, os, secrets, base64
from datetime import datetime
from pathlib import Path
import pymysql
p=argparse.ArgumentParser();p.add_argument('--admin-user',default='root');a=p.parse_args()
root=Path(__file__).resolve().parents[1];dest=root/'.local/test';dest.mkdir(parents=True,exist_ok=True,mode=0o700);dest.chmod(0o700)
stamp=datetime.now().strftime('%Y%m%d%H%M%S')+'_'+secrets.token_hex(3)
names={'integration':'darkmujian_d1_test_d2a_'+stamp,'compatibility':'darkmujian_d1_compat_d2a_'+stamp}
user='mj_d2test_'+secrets.token_hex(5);password=secrets.token_urlsafe(32)
conn=pymysql.connect(host='127.0.0.1',port=3306,user=a.admin_user,password=getpass.getpass('本机MySQL管理密码（隐藏输入）: '),ssl={'check_hostname':False},autocommit=True)
with conn.cursor() as cur:
 cur.execute('SELECT VERSION()');assert cur.fetchone()[0].startswith('8.4.'),'Requires MySQL 8.4'
 for name in names.values():
  cur.execute('SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name=%s',(name,));assert cur.fetchone()[0]==0,'Refuse existing database'
 cur.execute("SELECT COUNT(*) FROM mysql.user WHERE User=%s",(user,));assert cur.fetchone()[0]==0,'Refuse existing user'
 for name in names.values():cur.execute('CREATE DATABASE `'+name+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci')
 cur.execute("CREATE USER %s@'127.0.0.1' IDENTIFIED BY %s",(user,password))
 for name in names.values():cur.execute('GRANT ALL PRIVILEGES ON `'+name+'`.* TO %s@\'127.0.0.1\'',(user,))
conn.close()
for kind,name in names.items():
 path=dest/(kind+'-'+stamp+'.properties')
 fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'w') as f:f.write('spring.datasource.url=jdbc:mysql://127.0.0.1:3306/'+name+'?connectionTimeZone=Asia/Shanghai\nspring.datasource.username='+user+'\nspring.datasource.password='+password+'\nmj.environment=test\nspring.data.redis.host=127.0.0.1\nspring.data.redis.port=6379\nmj.verification.crypto.active-version=test-v1\nmj.verification.crypto.keys.test-v1='+base64.b64encode(secrets.token_bytes(32)).decode()+'\n')
 print(('MJ_TEST_CONFIG' if kind=='integration' else 'MJ_COMPAT_CONFIG')+'='+str(path))
print('Created two fresh isolated databases and one account scoped only to them. No development database changes.')
