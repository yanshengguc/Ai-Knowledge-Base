#!/usr/bin/env python3
"""扫描 git 全历史中疑似真实密钥(值形态,非 ${ENV} 引用)。只读,不修改。"""
import re
import subprocess
import sys

PATTERNS = [
    (re.compile(r"sk-[A-Za-z0-9]{16,}"), "sk- API key"),
    (re.compile(r"LTAI[A-Za-z0-9]{12,}"), "Aliyun AccessKeyId"),
    (re.compile(r"AK[A-Za-z0-9]{14,}"), "Volcano/other AK"),
    (re.compile(r"(?i)(secret|password|api[-._]?key|apikey|token)['\"]?\s*[=:]\s*['\"]?[A-Za-z0-9+/_-]{24,}"), "assignment>=24"),
]

def main():
    p = subprocess.run(
        ["git", "log", "--all", "-p", "--no-color"],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    hits = {}
    for line in p.stdout.splitlines():
        if not line.startswith("+") or line.startswith("+++"):
            continue
        for rx, name in PATTERNS:
            m = rx.search(line)
            if m and "${" not in m.group(0):  # 排除 ${ENV:...} 引用
                key = (name, m.group(0)[:8] + "...")
                hits.setdefault(key, []).append(line.strip()[:100])
    if not hits:
        print("CLEAN: 未发现真实密钥值形态的字符串")
        return 0
    for (name, frag), lines in hits.items():
        print(f"[HIT] {name} {frag} x{len(lines)}")
        for l in lines[:3]:
            print(f"      {l}")
    return 1

if __name__ == "__main__":
    sys.exit(main())
