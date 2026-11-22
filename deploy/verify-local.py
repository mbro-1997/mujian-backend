#!/usr/bin/env python3
"""完整回归入口：未提供隔离环境明确 NOT RUN，禁止将跳过当作验收通过。"""
import os
from pathlib import Path
import subprocess
import sys
import time
for name in ("MJ_TEST_CONFIG", "MJ_COMPAT_CONFIG"):
    value = os.environ.get(name)
    if not value or not Path(value).is_file():
        print(f"NOT RUN: 缺少 {name} 的可读配置文件；未执行完整回归，不算 PASS。")
        sys.exit(2)
root = Path(__file__).resolve().parents[1]
started_ns = time.time_ns()
result = subprocess.run(["mvn", "test", *sys.argv[1:]], cwd=root)
if result.returncode:
    sys.exit(result.returncode)
import xml.etree.ElementTree as ET
build = next((x.split("=",1)[1] for x in sys.argv[1:] if x.startswith("-Dmj.build.directory=")), "target/d1")
report_dir = (root / build / "surefire-reports").resolve()
if not report_dir.is_relative_to((root / "target").resolve()):
    sys.exit("NOT VERIFIED: reports must be inside the project target directory")
reports = [p for p in report_dir.glob("TEST-*.xml") if p.stat().st_mtime_ns >= started_ns]
if not reports or any(any(int(ET.parse(p).getroot().get(k, "0")) for k in ("skipped", "failures", "errors")) for p in reports):
    print("NOT VERIFIED: 有跳过的测试，不能认定完整回归通过。")
    sys.exit(2)
print("Executed tests:", sum(int(ET.parse(p).getroot().get("tests", "0")) for p in reports))
print("VERIFIED: 本次选定测试已执行，无跳过。" if any(x.startswith("-Dtest=") for x in sys.argv[1:]) else "VERIFIED: 完整回归已执行，无跳过。")
