#!/usr/bin/env python3
"""C-27 §11.6: manual, offline, single-account LOCAL maintenance; never a web feature.

Production entry accepts neither a database nor a user/SQL argument. The private
configuration and enrollment proof are fixed under .local/identity-maintenance.
Only the two identity tables can be updated. No schema/identity creation/deletion.
"""
import argparse
import datetime as dt
import fcntl
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import stat
import subprocess
import sys
import uuid
from urllib.parse import urlparse, parse_qs

import pymysql

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / '.local/identity-maintenance'
ID = re.compile(r'[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}\Z')
PREFIX = 'mujian:development:session:'
PERSONAL = {'sys_user', 'sys_user_role', 'user_login_identity',
            'user_realname_verification', 'user_student_verification',
            'user_rule_acceptance', 'coupon_reward_record'}
FACTORY = {'factory_profile', 'factory_onboarding_audit', 'factory_invite_code',
           'factory_profile_change_audit'}
ASSETS = {'prop_sku', 'prop_image', 'prop_sku_style', 'prop_sku_audit',
          'prop_sku_stock', 'prop_sn_instance', 'prop_sn_adjustment_audit',
          'prop_sn_adjustment_item', 'prop_instance_status_log',
          'prop_stock_adjustment_audit', 'prop_stock_movement', 'prop_qr_code',
          'prop_qr_exception_record', 'prop_operation_record'}
AUDIT = {'admin_operation_audit', 'admin_case', 'admin_case_action', 'admin_case_relation'}


class Refused(RuntimeError):
    pass


def require(ok, message):
    if not ok:
        raise Refused(message)


def now():
    return dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat()


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, default=str).encode()


def secure(path, directory=False):
    st = path.lstat()
    require(not path.is_symlink() and st.st_uid == os.getuid(), 'Unsafe owner/symlink: ' + path.name)
    require(stat.S_IMODE(st.st_mode) == (0o700 if directory else 0o600),
            'Expected 700 directory / 600 file: ' + path.name)
    require(stat.S_ISDIR(st.st_mode) if directory else stat.S_ISREG(st.st_mode), 'Wrong file type')


def save_new(path, value):
    """Durable exclusive audit/backup; no overwrite of prior operation records."""
    secure(path.parent, True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as f:
        f.write(encoded(value) + b'\n')
        f.flush()
        os.fsync(f.fileno())
    fd = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def properties(path):
    secure(path)
    result = {}
    for line in path.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith(('#', '!')):
            continue
        require('=' in line and not line.endswith('\\'), 'Unsupported property syntax')
        k, v = line.split('=', 1)
        k, v = k.strip(), v.strip()
        require(k not in result, 'Duplicate configuration key')
        result[k] = v
    return result


def gates(config, proof, runtime, profile):
    require(config.get('enabled') is True, 'Switch disabled')
    require(profile == 'local' and config.get('environment') == 'local', 'Local environment required')
    require(runtime.get('mj.environment', 'development') == 'development', 'Runtime environment is not development')
    user = config.get('allowedUserId', '')
    require(ID.fullmatch(user) and user == proof.get('userId'), 'Exact enrolled account required')
    require(proof.get('userConfirmedPaused') is True and proof.get('contextHttp') == 200
            and proof.get('loginTraceId'), 'Missing real-login enrollment evidence')
    require(config.get('serverUuid') == proof.get('serverUuid') and config.get('serverUuid'), 'Server enrollment mismatch')
    url = urlparse(runtime.get('spring.datasource.url', '')[5:])
    require(runtime.get('spring.datasource.url', '').startswith('jdbc:mysql://'), 'MySQL JDBC required')
    require(url.hostname == '127.0.0.1' and url.port == 3306 and url.path == '/darkmujian'
            and not url.username and not url.password and not url.fragment, 'Only local darkmujian:3306 allowed')
    require(set(parse_qs(url.query)) <= {'connectionTimeZone', 'serverTimezone', 'useUnicode', 'characterEncoding'},
            'Unreviewed JDBC options')
    require(runtime.get('spring.data.redis.host', '127.0.0.1') == '127.0.0.1'
            and runtime.get('spring.data.redis.port', '6379') == '6379'
            and runtime.get('spring.data.redis.database', '0') == '0'
            and not runtime.get('spring.data.redis.url'), 'Only local Redis database 0 allowed')
    return user


def rows(conn, sql, args=()):
    with conn.cursor() as c:
        c.execute(sql, args)
        return c.fetchall()


def schema(conn):
    cols = rows(conn, 'SELECT TABLE_NAME t,COLUMN_NAME c,DATA_TYPE kind,EXTRA extra,GENERATION_EXPRESSION gen '
                'FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION')
    fks = rows(conn, 'SELECT TABLE_NAME t,COLUMN_NAME c,REFERENCED_TABLE_NAME parent,REFERENCED_COLUMN_NAME pc '
               'FROM information_schema.key_column_usage WHERE table_schema=DATABASE() '
               'AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,COLUMN_NAME')
    indexes = rows(conn, 'SELECT INDEX_NAME n,NON_UNIQUE nu,COLUMN_NAME c FROM information_schema.statistics '
                   "WHERE table_schema=DATABASE() AND table_name='sys_user_role' ORDER BY INDEX_NAME,SEQ_IN_INDEX")
    return {'columns': cols, 'foreignKeys': fks, 'roleIndexes': indexes}


def verify_schema(actual, expected):
    require(actual == expected, 'Schema changed; review new dependencies before maintenance')
    require({'n': 'uk_user_enabled_role', 'nu': 0, 'c': 'enabled_user_guard'} in actual['roleIndexes'],
            'Enabled-role unique index missing')
    guard = [x for x in actual['columns'] if x['t'] == 'sys_user_role' and x['c'] == 'enabled_user_guard']
    require(len(guard) == 1 and 'GENERATED' in guard[0]['extra'] and guard[0]['gen'], 'Generated unique guard missing')


def q(name):
    require(re.fullmatch('[a-zA-Z0-9_]+', name), 'Invalid metadata identifier')
    return '`' + name + '`'


def inventory(conn, user, meta):
    """Forward dependency closure: FKs plus logical ID/JSON references.

    Fetch IDs/status and server-side row hashes only; no plaintext identity,
    certification, phone, image or payload fields leave MySQL here.
    Unknown associated records fail closed; new tables/columns fail schema pin.
    """
    tables = {}
    for col in meta['columns']:
        tables.setdefault(col['t'], []).append(col)
    found = {'sys_user': {user}}
    result = {}
    for _ in range(len(tables) + 1):
        before = sum(map(len, found.values()))
        all_ids = sorted(set().union(*found.values()))
        for table, cols in tables.items():
            names = {x['c'] for x in cols}
            if 'id' not in names:
                continue
            conditions, args = [], []
            if table == 'sys_user':
                conditions.append('id=%s'); args.append(user)
            else:
                for col in cols:
                    name, kind = col['c'], col['kind']
                    # Include logical source/target references and actor fields with no FK.
                    if name != 'id' and kind in {'varchar', 'char'} and (
                            name.endswith('_id') or name.endswith('_by') or name.endswith('_guard')):
                        conditions.append(q(name) + ' IN (' + ','.join(['%s'] * len(all_ids)) + ')')
                        args.extend(all_ids)
                    if kind == 'json':
                        for i in all_ids:
                            conditions.append('CAST(' + q(name) + ' AS CHAR) LIKE %s')
                            args.append('%' + i + '%')
                for fk in meta['foreignKeys']:
                    if fk['t'] == table and fk['parent'] in found and fk['pc'] == 'id':
                        ids = sorted(found[fk['parent']])
                        conditions.append(q(fk['c']) + ' IN (' + ','.join(['%s'] * len(ids)) + ')')
                        args.extend(ids)
            if not conditions:
                result[table] = {'count': 0, 'records': []}
                continue
            safe = sorted(n for n in names if n.endswith('_status') or n in {
                'status', 'role_code', 'enabled', 'owner_user_id', 'factory_id', 'sku_id',
                'current_order_id', 'related_order_id', 'rental_order_id', 'checkout_batch_id',
                'approved_audit_id', 'created_factory_id', 'applicant_user_id',
                'image_generation_status', 'locked_quantity', 'renting_quantity'})
            # Identity/stage/time are the only allowed mutations; compare all other data bytewise by hash.
            digest_cols = [n['c'] for n in cols if not (table == 'sys_user' and n['c'] in {'register_status', 'updated_at'})
                           and not (table == 'sys_user_role' and n['c'] in {'enabled', 'enabled_user_guard', 'updated_at'})]
            obj = ','.join("'" + n + "'," + q(n) for n in digest_cols)
            sql = 'SELECT id,' + (','.join(map(q, safe)) + ',' if safe else '') + \
                'SHA2(CAST(JSON_OBJECT(' + obj + ') AS CHAR),256) row_hash FROM ' + q(table) + \
                ' WHERE ' + ' OR '.join('(' + x + ')' for x in conditions) + ' ORDER BY id'
            selected = rows(conn, sql, tuple(args))
            require(len(selected) <= 10000, 'Too many dependencies for manual maintenance')
            result[table] = {'count': len(selected), 'records': selected}
            if selected:
                found.setdefault(table, set()).update(x['id'] for x in selected)
        if sum(map(len, found.values())) == before:
            return result
    raise Refused('Dependency closure did not converge')


def conflicts(inv, bootstrap):
    issues = []
    for table, data in inv.items():
        rs = data['records']
        if not rs:
            continue
        if table not in PERSONAL | FACTORY | ASSETS | AUDIT:
            issues.append(table + ': related records require review')
        if bootstrap and table not in PERSONAL | AUDIT:
            issues.append(table + ': existing factory/other dependency blocks first enrollment')
        for r in rs:
            if table in {'factory_onboarding_audit', 'factory_profile_change_audit', 'prop_sku_audit',
                         'prop_sn_adjustment_audit', 'prop_stock_adjustment_audit'} and r.get('audit_status') == 'pending':
                issues.append(table + ': pending review')
            if table == 'factory_invite_code' and r.get('invite_status') == 'locked':
                issues.append(table + ': locked invitation')
            if table == 'admin_case' and r.get('case_status') != 'closed':
                issues.append(table + ': unclosed work item')
            if table == 'prop_qr_exception_record' and r.get('exception_status') == 'reported':
                issues.append(table + ': unresolved exception')
            if table == 'prop_qr_code' and r.get('image_generation_status') in {'pending', 'generating'}:
                issues.append(table + ': QR generation in progress')
            if any(r.get(k) for k in ('current_order_id', 'related_order_id', 'rental_order_id', 'checkout_batch_id')):
                issues.append(table + ': transaction dependency')
            if table == 'prop_sku_stock' and (r.get('locked_quantity', 0) or r.get('renting_quantity', 0)):
                issues.append(table + ': inventory occupied')
            if table == 'prop_sn_instance' and r.get('instance_status') in {'locked', 'renting'}:
                issues.append(table + ': instance occupied')
    return sorted(set(issues))


def plan(user, roles, inv, action, target=None):
    require(user and user['user_status'] == 'active', 'Target account must be active')
    require(user['id'] != '3e8c4a5e468449709003045cd6dfca76', 'Fixed system identity prohibited')
    require(all(r['role_code'] in {'demander', 'supplier'} for r in roles), 'Admin/unknown identity prohibited')
    active = [r for r in roles if r['enabled'] == 1]
    bootstrap = action in {'bootstrap', 'rollback-bootstrap'}
    require(not conflicts(inv, bootstrap), '; '.join(conflicts(inv, bootstrap)))
    if action == 'bootstrap':
        require(len(roles) == len(active) == 1 and active[0]['role_code'] == 'demander'
                and user['register_status'] == 'demander_bound', 'First enrollment requires sole active demander')
        return {'old': active[0]['id'], 'new': None, 'stage': 'new', 'noop': False}
    if action == 'rollback-bootstrap':
        require(len(roles) == 1 and not active and roles[0]['role_code'] == 'demander'
                and user['register_status'] == 'new', 'Bootstrap rollback no longer safe')
        return {'old': None, 'new': roles[0]['id'], 'stage': 'demander_bound', 'noop': False}
    require(action == 'switch' and target in {'demander', 'supplier'}, 'Invalid operation')
    require(len(roles) == 2 and {r['role_code'] for r in roles} == {'demander', 'supplier'}
            and len(active) == 1, 'Both genuine identities and exactly one enabled role required')
    expected = 'factory_bound' if active[0]['role_code'] == 'supplier' else 'demander_bound'
    require(user['register_status'] == expected, 'Role/stage mismatch')
    factories = inv.get('factory_profile', {}).get('records', [])
    require(len(factories) == 1 and factories[0]['owner_user_id'] == user['id']
            and factories[0]['factory_status'] == 'active', 'Owned active approved factory required')
    approvals = inv.get('factory_onboarding_audit', {}).get('records', [])
    require(any(r['id'] == factories[0]['approved_audit_id'] and r['audit_status'] == 'approved'
                and r['created_factory_id'] == factories[0]['id'] and r['applicant_user_id'] == user['id']
                for r in approvals), 'Normal approved onboarding provenance required')
    target_row = next(r for r in roles if r['role_code'] == target)
    return {'old': active[0]['id'], 'new': target_row['id'],
            'stage': 'factory_bound' if target == 'supplier' else 'demander_bound',
            'noop': active[0]['role_code'] == target}


def preserved(inv):
    return {t: [(r['id'], r['row_hash']) for r in d['records']] for t, d in inv.items()}


def apply_plan(conn, user, change):
    if change['noop']:
        return
    with conn.cursor() as c:
        if change['old']:
            c.execute('UPDATE sys_user_role SET enabled=0,updated_at=CURRENT_TIMESTAMP(3) '
                      'WHERE id=%s AND user_id=%s AND enabled=1', (change['old'], user['id']))
            require(c.rowcount == 1, 'Old role compare-and-set failed')
        if change['new']:
            c.execute('UPDATE sys_user_role SET enabled=1,updated_at=CURRENT_TIMESTAMP(3) '
                      'WHERE id=%s AND user_id=%s AND enabled=0', (change['new'], user['id']))
            require(c.rowcount == 1, 'Target role compare-and-set failed')
        c.execute('UPDATE sys_user SET register_status=%s,updated_at=CURRENT_TIMESTAMP(3) '
                  'WHERE id=%s AND register_status=%s AND user_status=%s',
                  (change['stage'], user['id'], user['register_status'], 'active'))
        require(c.rowcount == 1, 'Stage compare-and-set failed')


class Redis:
    """Small bounded RESP2 transport; no arbitrary Java deserialization."""
    def __init__(self, password=None):
        self.sock = socket.create_connection(('127.0.0.1', 6379), 5)
        self.file = self.sock.makefile('rb')
        if password:
            self.command('AUTH', password)

    def close(self):
        self.file.close(); self.sock.close()

    def read(self):
        tag = self.file.read(1)
        line = self.file.readline(8192)
        require(line.endswith(b'\r\n'), 'Invalid Redis response')
        value = line[:-2]
        if tag == b'+': return value
        if tag == b'-': raise Refused('Redis command refused')
        if tag == b':': return int(value)
        if tag == b'$':
            n = int(value)
            if n == -1: return None
            require(0 <= n <= 2**20, 'Redis bulk bound exceeded')
            data = self.file.read(n)
            require(len(data) == n and self.file.read(2) == b'\r\n', 'Truncated Redis reply')
            return data
        if tag == b'*':
            n = int(value)
            require(0 <= n <= 100000, 'Redis array bound exceeded')
            return [self.read() for _ in range(n)]
        raise Refused('Unknown Redis type')

    def command(self, *args):
        data = [a if isinstance(a, bytes) else str(a).encode() for a in args]
        self.sock.sendall(b'*' + str(len(data)).encode() + b'\r\n' + b''.join(
            b'$' + str(len(a)).encode() + b'\r\n' + a + b'\r\n' for a in data))
        return self.read()

    def session_keys(self):
        cursor = 0
        while True:
            cursor, keys = self.command('SCAN', cursor, 'MATCH', PREFIX + 'sessions:*', 'COUNT', 100)
            for key in keys:
                if re.fullmatch(re.escape(PREFIX).encode() + rb'sessions:[0-9a-f-]{36}', key):
                    yield key
            cursor = int(cursor)
            if not cursor: break


def java_string(value):
    b = value.encode('ascii')
    return b'\xac\xed\x00\x05t' + len(b).to_bytes(2, 'big') + b


def revoke_user(redis, user):
    count = 0
    # Exact serialized user/client comparison inside Lua prevents touching others.
    script = """
if redis.call('HGET',KEYS[1],'sessionAttr:userId') ~= ARGV[1] or
   redis.call('HGET',KEYS[1],'sessionAttr:clientType') ~= ARGV[2] then return 0 end
redis.call('DEL',KEYS[1],KEYS[2])
redis.call('SREM',KEYS[3],ARGV[3])
return 1
"""
    for key in redis.session_keys():
        sid = key.decode().rsplit(':', 1)[1]
        count += redis.command('EVAL', script, 3, key, PREFIX + 'sessions:expires:' + sid,
                               PREFIX + 'index:org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME:' + user,
                               java_string(user), java_string('miniapp'), sid)
    for key in redis.session_keys():
        values = redis.command('HMGET', key, 'sessionAttr:userId', 'sessionAttr:clientType')
        require(values != [java_string(user), java_string('miniapp')], 'Target session still exists')
    return count


def offline():
    # Keep detecting the old package while its shared JVM may still be running.
    # Entire backend is stopped briefly; other accounts' persisted sessions survive.
    for host in ('127.0.0.1', '::1'):
        try:
            s = socket.create_connection((host, 18080), .3)
        except OSError:
            continue
        s.close()
        raise Refused('Stop local backend and drain requests before maintenance')
    ps = subprocess.check_output(['ps', '-axo', 'command='], text=True)
    require(not any(('java ' in line or '/java ' in line) and
                    ('mujian-backend' in line or 'com.mujian.backend' in line
                     or 'com.muhou.backend' in line) for line in ps.splitlines()),
            'Backend JVM still running (including alternate ports)')


def run(action, target, profile, reason, paused, operation=None, device_reset=False):
    secure(ROOT / '.local', True); secure(HOME, True)
    require(subprocess.run(['git', 'check-ignore', '-q', '.local/identity-maintenance/config.json'], cwd=ROOT).returncode == 0,
            'Private maintenance directory must be Git-ignored')
    for name in ('config.json', 'enrollment.json'):
        secure(HOME / name)
    config = json.loads((HOME / 'config.json').read_text())
    proof = json.loads((HOME / 'enrollment.json').read_text())
    runtime = properties(ROOT / '.local/runtime.properties')
    user_id = gates(config, proof, runtime, profile)
    require(paused and reason.strip(), 'Explicit paused-account declaration and reason required')
    require(action == 'inspect' or device_reset,
            'Confirm old mini-program pages, login and pending recovery data cleared on all target-account devices')
    require((ROOT / 'src/main/resources/application-local.yml').is_file(), 'Missing local profile')
    fd = os.open(HOME / 'lock', os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'a') as lock:
        secure(HOME / 'lock')
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if action != 'inspect': offline()
        conn = pymysql.connect(host='127.0.0.1', port=3306, database='darkmujian',
                               user=runtime['spring.datasource.username'], password=runtime['spring.datasource.password'],
                               cursorclass=pymysql.cursors.DictCursor, autocommit=False, connect_timeout=5)
        redis = None
        operation_id = uuid.uuid4().hex
        committing = False
        prepared = False
        audit = {'operationId': operation_id, 'at': now(), 'actorOsUser': getpass.getuser(),
                 'actorUid': os.getuid(), 'agent': 'manual local maintenance (not product admin approval)',
                 'reason': reason, 'userId': user_id, 'action': action, 'target': target}
        try:
            server = rows(conn, 'SELECT DATABASE() db,@@server_uuid serverUuid,@@hostname hostname,@@port port')[0]
            require(server['db'] == 'darkmujian' and server['port'] == 3306
                    and server['serverUuid'] == config['serverUuid']
                    and server['hostname'] in {socket.gethostname(), socket.gethostname().split('.')[0]}, 'Local database fingerprint mismatch')
            meta = schema(conn)
            verify_schema(meta, json.loads((ROOT / 'deploy/identity-maintenance-schema.json').read_text()))
            require(rows(conn, 'SELECT GET_LOCK(%s,0) locked', ('local-identity-maintenance:' + user_id,))[0]['locked'] == 1,
                    'Another maintenance operation is active')
            conn.begin()
            users = rows(conn, 'SELECT * FROM sys_user WHERE id=%s FOR UPDATE', (user_id,))
            require(len(users) == 1, 'Enrolled user missing')
            user = users[0]
            roles = rows(conn, 'SELECT * FROM sys_user_role WHERE user_id=%s ORDER BY id FOR UPDATE', (user_id,))
            inv = inventory(conn, user_id, meta)
            if action == 'inspect':
                conn.rollback()
                safe = {'operationId': operation_id, 'userId': user_id, 'stage': user['register_status'],
                        'roles': [{k: r[k] for k in ('id', 'role_code', 'enabled')} for r in roles],
                        'inventory': inv, 'conflicts': conflicts(inv, len(roles) == 1), 'server': server}
                save_new(HOME / (operation_id + '-inspection.json'), safe)
                return {'outcome': 'inspected', 'operationId': operation_id, 'conflicts': safe['conflicts']}
            # A paused offline backend prevents both already-authenticated writes and new sessions.
            offline()
            change = plan(user, roles, inv, action, target)
            if action == 'rollback-bootstrap':
                require(operation and ID.fullmatch(operation), 'Original bootstrap operation required')
                source = json.loads((HOME / (operation + '-result.json')).read_text())
                require(source['userId'] == user_id and source['action'] == 'bootstrap'
                        and source['outcome'] == 'committed', 'Not a committed bootstrap for this user')
                backup = json.loads((HOME / (operation + '-prepared.json')).read_text())
                require([r['id'] for r in roles] == [r['id'] for r in backup['beforeRoles']], 'Original role changed')
            audit.update(beforeUser=user, beforeRoles=roles, inventory=inv, planned=change, server=server)
            save_new(HOME / (operation_id + '-prepared.json'), audit)
            prepared = True
            if change['noop']:
                conn.rollback()
                audit = {k: audit[k] for k in ('operationId', 'userId', 'action', 'target', 'actorOsUser', 'actorUid', 'reason')}
                audit.update(outcome='no_change', at=now(), sessionsRevoked=0)
                save_new(HOME / (operation_id + '-result.json'), audit)
                return {'outcome': 'no_change', 'operationId': operation_id}
            redis = Redis(runtime.get('spring.data.redis.password'))
            count = revoke_user(redis, user_id)
            # Re-read switch immediately before DB writes; disabling it is fail-closed.
            latest = json.loads((HOME / 'config.json').read_text())
            require(latest == config and latest.get('enabled') is True, 'Configuration changed during maintenance')
            apply_plan(conn, user, change)
            after = inventory(conn, user_id, meta)
            require(preserved(inv) == preserved(after), 'Data preservation check failed; rolling back')
            after_roles = rows(conn, 'SELECT id,role_code,enabled FROM sys_user_role WHERE user_id=%s ORDER BY id', (user_id,))
            require(sum(r['enabled'] for r in after_roles) == (0 if action == 'bootstrap' else 1), 'Enabled role count mismatch')
            committing = True
            conn.commit()
            result = {k: audit[k] for k in ('operationId', 'userId', 'action', 'target', 'actorOsUser', 'actorUid', 'reason')}
            result.update(outcome='committed', at=now(), stage=change['stage'], roles=after_roles,
                          sessionsRevoked=count, dataPreserved=True)
            save_new(HOME / (operation_id + '-result.json'), result)
            return {'outcome': 'committed', 'operationId': operation_id, 'stage': change['stage'], 'sessionsRevoked': count}
        except Exception:
            conn.rollback()
            if prepared:
                # A lost COMMIT acknowledgement or post-commit audit failure cannot be called rollback.
                outcome = 'commit_outcome_requires_inspection' if committing else 'rolled_back'
                path = HOME / (operation_id + '-failure.json')
                if not path.exists():
                    save_new(path, {'operationId': operation_id, 'at': now(), 'userId': user_id,
                                    'outcome': outcome, 'note': 'Revoked sessions stay revoked; do not restore credentials or whole backup'})
            raise
        finally:
            if redis: redis.close()
            conn.close()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('action', choices=['inspect', 'bootstrap', 'switch', 'rollback-bootstrap'])
    p.add_argument('--to', choices=['demander', 'supplier'])
    p.add_argument('--profile', required=True)
    p.add_argument('--reason', required=True)
    p.add_argument('--account-paused', action='store_true')
    p.add_argument('--device-reset-confirmed', action='store_true',
                   help='Human confirmed old pages/login/pending local recovery state cleared before switching')
    p.add_argument('--operation', help='Original committed bootstrap operation, only for safe rollback')
    a = p.parse_args()
    try:
        require((a.action == 'switch') == (a.to is not None), '--to only required for switch')
        print(json.dumps(run(a.action, a.to, a.profile, a.reason, a.account_paused, a.operation,
                             a.device_reset_confirmed), ensure_ascii=False))
    except Refused as e:
        print(json.dumps({'outcome': 'refused', 'reason': str(e)}, ensure_ascii=False)); return 2
    except Exception as e:
        # No raw DB/Redis exception: drivers may include connection secrets or personal values.
        print(json.dumps({'outcome': 'failed', 'type': type(e).__name__, 'details': 'Check protected local audit; no automatic retry'})); return 3
    return 0


if __name__ == '__main__':
    sys.exit(main())
