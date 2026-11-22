#!/bin/sh
# 用法：sh deploy/verify-database.sh --defaults-extra-file /绝对路径/安全配置.cnf
# 或：  sh deploy/verify-database.sh --login-path 已配置名称
# 密码只从本机安全配置读取；禁止 set -x、命令行明文密码或把凭据提交到 Git。
set -eu
if [ "$#" -ne 2 ]; then
  echo '需要 --defaults-extra-file <路径> 或 --login-path <名称>' >&2
  exit 2
fi
case "$1" in
  --defaults-extra-file) option="--defaults-extra-file=$2" ;;
  --login-path) option="--login-path=$2" ;;
  *) echo '不支持的配置选项' >&2; exit 2 ;;
esac
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
mysql "$option" --batch --database=darkmujian < "$script_dir/verify-database.sql"
