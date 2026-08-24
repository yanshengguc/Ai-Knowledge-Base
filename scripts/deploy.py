#!/usr/bin/env python3
"""部署辅助:paramiko 免交互 SSH/SFTP。
用法:
  python scripts/deploy.py cmd  "<远程命令>"
  python scripts/deploy.py put  <本地路径> <远程路径>
凭据从环境变量读取(部署服务器/密码),缺省为本机部署目标。
"""
import os
import sys

import paramiko

HOST = os.environ.get("DEPLOY_HOST", "120.55.76.141")
USER = os.environ.get("DEPLOY_USER", "root")
PASSWORD = os.environ.get("DEPLOY_PASSWORD", "")


def client():
    if not PASSWORD:
        sys.exit("DEPLOY_PASSWORD 未设置")
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASSWORD, timeout=20, look_for_keys=False, allow_agent=False)
    return c


def run(c, cmd):
    stdin, stdout, stderr = c.exec_command(cmd, timeout=580)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    print(f"$ {cmd}\n{out}", end="")
    if err.strip():
        print(f"[stderr] {err}", end="", file=sys.stderr)
    print(f"[exit {code}]")
    return code


def put(c, local, remote):
    sftp = c.open_sftp()
    sftp.put(local, remote)
    st = sftp.stat(remote)
    print(f"uploaded {local} -> {remote} ({st.st_size} bytes)")


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    c = client()
    try:
        if sys.argv[1] == "cmd":
            sys.exit(run(c, sys.argv[2]))
        elif sys.argv[1] == "put":
            put(c, sys.argv[2], sys.argv[3])
        else:
            sys.exit(__doc__)
    finally:
        c.close()


if __name__ == "__main__":
    main()
