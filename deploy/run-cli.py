#!/usr/bin/env python3
"""从编译输出启动正式 CLI；配置值不进入命令行，不提供小程序注入入口。"""
from pathlib import Path
import argparse, subprocess
p=argparse.ArgumentParser();p.add_argument('command',choices=['migrate','admin']);p.add_argument('config');p.add_argument('secret_file',nargs='?');p.add_argument('--build-dir',default='target/d1');a=p.parse_args()
root=Path(__file__).resolve().parents[1]
build=(root/a.build_dir).resolve()
if not build.is_relative_to((root/'target').resolve()):raise SystemExit('build directory must be inside target')
cp=build/'classpath.txt'
if not cp.exists():raise SystemExit('先执行 README 中的 Maven classpath 构建命令')
main={'migrate':'DatabaseMigrationCli','admin':'AdminInitializeCli'}[a.command]
args=['java','-cp',str(build/'classes')+':'+cp.read_text().strip(),'com.mujian.backend.common.support.'+main,str(Path(a.config).resolve())]
if a.secret_file:args.append(str(Path(a.secret_file).resolve()))
raise SystemExit(subprocess.call(args,cwd=root))
