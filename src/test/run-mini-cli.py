#!/usr/bin/env python3
"""本机 dev/test 工具；不进入生产 jar。仅接收配置与新秘密文件路径。"""
from pathlib import Path
import sys,subprocess
if len(sys.argv)!=3:raise SystemExit('用法：run-mini-cli.py 本机配置路径 新的凭据文件路径')
root=Path(__file__).resolve().parents[2]
cp=':'.join([str(root/'target/d1/test-classes'),str(root/'target/d1/classes'),(root/'target/d1/classpath.txt').read_text().strip()])
raise SystemExit(subprocess.call(['java','-cp',cp,'com.mujian.backend.tools.MiniSessionCli',str(Path(sys.argv[1]).resolve()),str(Path(sys.argv[2]).resolve())],cwd=root))
