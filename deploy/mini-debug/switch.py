#!/usr/bin/env python3
"""在原微信开发工程配对本机签发器，并切到选定测试账号。"""
import argparse
import fcntl
import ipaddress
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request

TOOLS = Path(__file__).resolve().parent
BACKEND = TOOLS.parents[1]
BUILD = BACKEND / 'target/local-mini-debug'
PRIVATE = BACKEND / '.local/mini-debug'
PROJECT = BACKEND.parent / 'mujian-frontend/miniprogram'
KEY = PRIVATE / 'pair.key'
ROLES = {'租赁方': 'demander', '工厂': 'supplier'}
LOCAL = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def ready(host, port, key):
    req = urllib.request.Request(f'http://{host}:{port}/ready',
                                 headers={'Authorization': 'Bearer ' + key.read_text().strip()})
    try:
        with LOCAL.open(req, timeout=1) as response:
            return json.load(response) == {'ready': True}
    except urllib.error.HTTPError as error:
        raise SystemExit(f'{host}:{port} 上的服务与当前配对凭据不一致。') from error
    except OSError:
        return False


def start_service(host, port, key, developer=None):
    if ready(host, port, key):
        return
    BUILD.mkdir(parents=True, exist_ok=True)
    cpfile = BUILD / 'classpath'
    subprocess.run(['mvn', '-q', '-DskipTests', '-Dmj.build.directory=' + str(BUILD),
                    '-Dmdep.outputFile=' + str(cpfile), 'compile', 'dependency:build-classpath'],
                   check=True, cwd=BACKEND)
    cp = os.pathsep.join([str(BUILD), str(BUILD / 'classes'), cpfile.read_text().strip()])
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', cp, '-d', BUILD,
                    TOOLS / 'LocalMiniSessions.java'], check=True)
    log = PRIVATE / ('phone-service.log' if developer else 'service.log')
    code = simulator('developer-code', capture=True) if developer and not developer.exists() else None
    with log.open('w') as stream:
        process = subprocess.Popen(['java', '-Djava.awt.headless=true', '-cp', cp,
                                    'devtools.LocalMiniSessions', key, host, str(port), *([developer] if developer else [])], cwd=BACKEND,
                                   start_new_session=True, stdin=subprocess.PIPE if code else subprocess.DEVNULL,
                                   stdout=stream, stderr=subprocess.STDOUT)
    if code:
        process.stdin.write(code.strip().encode() + b"\n")
        process.stdin.close()
    for _ in range(80):
        if process.poll() is not None:
            raise SystemExit('本机签发器启动失败，详见 ' + str(log))
        if ready(host, port, key):
            return
        time.sleep(.25)
    raise SystemExit('本机签发器尚未就绪，详见 ' + str(log))


def simulator(action, capture=False):
    # 依赖与私密配置均在原工程之外，微信构建不打包调试命令。
    dep = BUILD / 'automator'
    dep.mkdir(exist_ok=True, parents=True)
    stamp = dep / '.installed-lock'
    lockfile = TOOLS / 'package-lock.json'
    if not stamp.exists() or stamp.read_bytes() != lockfile.read_bytes() or not (dep / 'node_modules/miniprogram-automator').exists():
        for name in ('package.json', 'package-lock.json'):
            shutil.copyfile(TOOLS / name, dep / name)
        subprocess.run(['npm', 'ci', '--prefer-offline', '--ignore-scripts', '--no-audit', '--no-fund'],
                       cwd=dep, check=True)
        shutil.copyfile(lockfile, stamp)
    apps = [f for base in [Path.home()/'Applications', Path('/Applications')]
            for f in sorted(base.glob('微信开发*工具*.app/Contents/MacOS/cli'), reverse=True)]
    if not apps:
        raise SystemExit('未找到微信开发者工具')
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = str(sock.getsockname()[1])
    return subprocess.run(['node', TOOLS / 'pair.cjs', apps[0], PROJECT, KEY, port, action],
                          env={**os.environ, 'NODE_PATH': str(dep / 'node_modules')},
                          stdout=subprocess.PIPE if capture else None,
                          check=True, timeout=100, text=True).stdout


def enable_phone(network):
    host = str(ipaddress.IPv4Address(network['lanHost']))
    address = ipaddress.ip_address(host)
    allowed = ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')
    if network['mode'] != 'lan' or not any(address in ipaddress.ip_network(r) for r in allowed):
        raise SystemExit('请将开发配置设为 lan，并填写电脑当前的局域网 IPv4 地址。')
    with socket.socket() as probe:
        try:
            probe.bind((host, 0))
        except OSError as error:
            raise SystemExit('配置的局域网 IP 未绑定到本机，请更新 lanHost。') from error
    port = network['debugPort']
    key = PRIVATE / 'phone-health.key'
    if not key.exists():
        key.write_text(secrets.token_hex(32))
    start_service(host, port, key, PRIVATE / 'developer.sha256')
    print('手机调试服务已就绪。用开发者微信扫描小程序预览码后，可在“我的”选择测试账号。')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('role', choices=ROLES, nargs='?')
    parser.add_argument('--phone', action='store_true', help='开启本机手机调试服务，首次从原开发工具核对开发者微信身份')
    parser.add_argument('--ensure-phone', type=int, metavar='API_PORT',
                        help='开发环境启动时调用，仅处理与当前后端端口匹配的局域网预览')
    args = parser.parse_args()
    if args.ensure_phone is not None and (args.role or args.phone):
        parser.error('--ensure-phone 不能与账号或 --phone 同用')
    network = json.loads(subprocess.check_output([
        'node', '-pe', 'JSON.stringify(require(process.argv[1]))', PROJECT / 'config/development.js'
    ], text=True))
    for field in ['apiPort', 'debugPort']:
        if type(network[field]) is not int or not 1 <= network[field] <= 65535:
            parser.error(field + ' 端口无效')
    if args.ensure_phone is not None and (network['mode'] != 'lan' or args.ensure_phone != network['apiPort']):
        return
    os.umask(0o077)
    PRIVATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    if args.phone or args.ensure_phone is not None:
        if args.role:
            parser.error('手机在小程序中选择身份')
        with (PRIVATE / 'switch.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            enable_phone(network)
        return
    role = args.role or input('请选择测试账号（1 租赁方，2 工厂） ').strip()
    role = {'1': '租赁方', '2': '工厂'}.get(role, role)
    if role not in ROLES:
        parser.error('请选择租赁方或工厂')
    with (PRIVATE / 'switch.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if not KEY.exists():
            KEY.write_text(secrets.token_hex(32))
        start_service('127.0.0.1', network['debugPort'], KEY)
        if network['mode'] == 'lan':
            try:
                enable_phone(network)
            except (SystemExit, OSError, ValueError, subprocess.SubprocessError) as error:
                print(f'手机调试未就绪：{error}；继续使用本机模拟器。', file=sys.stderr)
        simulator(ROLES[role])


if __name__ == '__main__':
    main()
