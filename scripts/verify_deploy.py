#!/usr/bin/env python3
"""部署验证清单:9 项核心检查
生产 REGISTER_ENABLED=false 时:先验证注册被拒(第 2 项),再跳过依赖注册的 A/B 双账号项;
其余场景(注册开放)自动走全量 9 项。"""
import json
import sys
import time
import urllib.request
import urllib.error

BASE = "http://120.55.76.141/api"
RESULTS = []


def req(method, path, body=None, token=None, timeout=30):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read().decode()
            try:
                return resp.status, json.loads(raw)
            except ValueError:
                return resp.status, raw
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def check(name, ok, detail=""):
    RESULTS.append((name, "PASS" if ok else "FAIL", detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}")


def summary():
    fails = [x for x in RESULTS if x[1] == "FAIL"]
    print(f"\n=== {len(RESULTS) - len(fails)}/{len(RESULTS)} PASS ===")
    return len(fails) == 0


ts = int(time.time())

# 1. 匿名访问被拒
code, _ = req("GET", "/knowledge")
check("匿名访问拦截", code == 401, f"(HTTP {code})")

# 2. 注册行为(开关注感知):开放 → 注册成功;关闭 → 返回"注册已关闭"
ua, ub = f"deploy_a_{ts}", f"deploy_b_{ts}"
code, r = req("POST", "/user/register", {"username": ua, "password": "Deploy#2026", "nickname": "验证A"})
reg_closed = isinstance(r, dict) and "注册已关闭" in str(r.get("message", ""))
if reg_closed:
    check("注册开关关闭拦截", code == 200 and r.get("code") == 500, f"(message={r.get('message')})")
else:
    check("用户注册", code == 200, f"(HTTP {code})")

if reg_closed:
    # 注册关闭模式:无法自建账号,验证登录文案统一(不存在的账号 → "用户名或密码错误")
    code, r = req("POST", "/user/login", {"username": f"nouser_{ts}", "password": "wrong"})
    unified = isinstance(r, dict) and r.get("message") == "用户名或密码错误"
    check("登录失败文案统一(防枚举)", unified, f"(message={r.get('message') if isinstance(r,dict) else r})")
    print("[SKIP] 知识/笔记/导出/越权/详情 5 项需要可注册账号,生产注册关闭时跳过(本地回归已覆盖)")
    ok = summary()
    sys.exit(0 if ok else 1)

# 3. 登录
code, r = req("POST", "/user/login", {"username": ua, "password": "Deploy#2026"})
tok_a = r.get("data", "") if isinstance(r, dict) else ""
check("用户登录", code == 200 and bool(tok_a), f"(token {'ok' if tok_a else 'MISSING'})")

# 3. 创建知识条目(接口返回 data:null,从列表反查 id)
code, _ = req("POST", "/knowledge", {"title": "部署验证条目", "content": "RAG 混合检索验证:向量召回与 BM25 关键词召回结合。", "category": "deploy-test"}, tok_a)
code, r = req("GET", "/knowledge", token=tok_a)
items = r.get("data") if isinstance(r, dict) else []
kid = next((i.get("id") for i in items if i.get("title") == "部署验证条目"), None)
check("知识条目创建+列表", code == 200 and kid is not None, f"(id={kid}, 共{len(items)}条)")

# 4. 笔记保存
code, _ = req("POST", f"/knowledge/{kid}/note", {"content": "部署验证笔记:AI 生成内容标记测试。"}, tok_a)
check("笔记保存", code == 200, f"(HTTP {code})")

# 5. 导出(Markdown 文件流)
code, raw = req("GET", "/knowledge/export", token=tok_a)
ok = code == 200 and isinstance(raw, str) and "部署验证条目" in raw
check("知识导出", ok, f"(HTTP {code}, markdown={'yes' if ok else 'no'})")

# 6. 越权:用户 B 访问 A 的条目(HTTP 200 + body code 500 表示被业务层拦截)
req("POST", "/user/register", {"username": ub, "password": "Deploy#2026", "nickname": "验证B"})
code, r = req("POST", "/user/login", {"username": ub, "password": "Deploy#2026"})
tok_b = r.get("data", "")
code, r = req("GET", f"/knowledge/{kid}", token=tok_b)
denied = isinstance(r, dict) and r.get("code") == 500 and "权限不足" in str(r.get("message"))
check("越权访问拦截", denied, f"(HTTP {code}, body={r.get('code') if isinstance(r,dict) else r})")

# 7. 混合检索通道(注册用户的知识条目全文检索由 chat 走,这里验证条目内容可被列表查询到)
code, r = req("GET", f"/knowledge/{kid}", token=tok_a)
detail_ok = code == 200 and "混合检索" in json.dumps(r, ensure_ascii=False)
check("知识详情查询", detail_ok, f"(HTTP {code})")

# 清理验证数据
if tok_a and kid:
    req("DELETE", f"/knowledge/{kid}", token=tok_a)

sys.exit(0 if summary() else 1)

