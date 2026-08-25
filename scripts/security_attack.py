#!/usr/bin/env python3
"""安全攻防测试: 以攻击者视角对运行中的后端发起真实请求。

靶机: 默认 http://localhost:56382/api (本地实例), 可用 ATTACK_BASE 覆盖。
     切勿对未授权环境运行。
模型: 注册受害者A与攻击者B, A 造数据, B 拿自己的合法 token 发起横向越权/注入/绕过等攻击。
输出: 逐条 [SAFE]/[VULN]/[INFO] + 风险级 + 请求响应摘要; 退出码 = 发现的漏洞数。

注意: BusinessException 走 HTTP 200 + body{"code":500}, 鉴权失败是 HTTP 401, 判定需看两层。
"""
import json
import os
import sys
import time
import urllib.request
import urllib.error

BASE = os.environ.get("ATTACK_BASE", "http://localhost:56382/api")
FINDINGS = []
VULN_COUNT = 0


def req(method, path, body=None, token=None, raw_body=None, timeout=15):
    url = BASE + path
    data = raw_body if raw_body is not None else (
        json.dumps(body).encode() if body is not None else None)
    r = urllib.request.Request(url, data=data, method=method)
    if raw_body is None and data is not None:
        r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            try:
                return resp.status, json.loads(raw)
            except ValueError:
                return resp.status, raw
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8", "replace"))
        except Exception:
            return e.code, {}
    except Exception as e:
        return -1, {"__exception__": str(e)}


def record(safe, risk, name, detail):
    global VULN_COUNT
    tag = "[SAFE]" if safe else "[VULN]"
    if not safe:
        VULN_COUNT += 1
        FINDINGS.append((risk, name, detail))
    print(f"{tag} {risk:<4} {name} | {detail}")


def body_code(resp):
    """BusinessException → HTTP 200 + code 500; 取业务 code 与 message。"""
    if isinstance(resp, dict):
        return resp.get("code"), resp.get("message", resp.get("error", ""))
    return None, str(resp)[:80]


def register_login(name, password):
    ts = int(time.time() * 1000)
    username = f"sec-{name}-{ts}"
    req("POST", "/user/register", {"username": username, "password": password,
                                   "nickname": name})
    status, resp = req("POST", "/user/login",
                       {"username": username, "password": password})
    token = resp.get("data") if isinstance(resp, dict) else None
    return username, token


def main():
    print(f"== 安全攻防测试 | 靶机 {BASE} ==\n")

    # ── 准备: 受害者A造数据 ──────────────────────────────
    user_a, token_a = register_login("victim", "Victim-Pass-123")
    user_b, token_b = register_login("attacker", "Attacker-Pass-123")
    assert token_a and token_b, "注册/登录前置失败"
    status, resp = req("GET", "/user/login", token=token_a)  # 占位:无影响
    req("POST", "/knowledge", {"title": "受害者私有知识", "content": "机密:只有A能看的内容",
                               "category": "私密"}, token=token_a)
    status, resp = req("GET", "/knowledge", token=token_a)
    kid_a = resp["data"][0]["id"]
    req("POST", f"/knowledge/{kid_a}/note",
        {"title": "私有笔记", "content": "笔记机密内容"}, token=token_a)
    status, resp = req("GET", f"/file/list/{kid_a}", token=token_a)
    fid_a = resp["data"][0]["id"] if resp.get("data") else None
    uid_a = None
    status, resp = req("GET", "/chat/history", token=token_a)
    print(f"前置: A={user_a} kid={kid_a} fid={fid_a}; B={user_b}\n")

    # ── 一、数据越权(IDOR) ──────────────────────────────
    s, r = req("GET", f"/knowledge/{kid_a}", token=token_b)
    code, msg = body_code(r)
    record(code != 200 or "权限" in str(msg), "高", "越权读知识详情",
           f"GET /knowledge/{kid_a}(B的token) → {s} {msg}")

    if fid_a:
        s, r = req("GET", f"/file/{fid_a}", token=token_b)
        code, msg = body_code(r)
        leaked = isinstance(r, dict) and isinstance(r.get("data"), dict) and r["data"].get("fileName")
        record(not leaked and "权限" in str(msg), "高", "越权读文件详情(IDOR)",
               f"GET /file/{fid_a}(B的token) → {s} {json.dumps(r.get('data'), ensure_ascii=False) if leaked else msg}")

        s, r = req("GET", f"/file/list/{kid_a}", token=token_b)
        listed = isinstance(r, dict) and isinstance(r.get("data"), list) and len(r["data"]) > 0
        record(not listed, "高", "越权列文件清单(IDOR)",
               f"GET /file/list/{kid_a}(B的token) → {s} 返回{len(r.get('data') or [])}条")

    # B 猜 A 的 userId 尝试(从1递增)
    for guess in range(1, 4):
        s, r = req("GET", f"/user/{guess}", token=token_b)
        code, msg = body_code(r)
        data = r.get("data") if isinstance(r, dict) else None
        if isinstance(data, dict) and data.get("username"):
            record(False, "低", "任意用户信息可查(IDOR)",
                   f"GET /user/{guess}(B的token) → {s} 拿到 {data}")
            break
    else:
        record(True, "低", "任意用户信息可查(IDOR)", "枚举 user/1..3 未泄漏用户名")

    # ── 二、功能越权 ──────────────────────────────
    s, r = req("PUT", f"/knowledge/{kid_a}",
               {"title": "被攻击者篡改", "content": "hack", "category": "x"}, token=token_b)
    code, msg = body_code(r)
    record("权限" in str(msg), "高", "越权改知识", f"PUT /knowledge/{kid_a}(B) → {s} {msg}")

    s, r = req("DELETE", f"/knowledge/{kid_a}", token=token_b)
    code, msg = body_code(r)
    record("权限" in str(msg), "高", "越权删知识", f"DELETE /knowledge/{kid_a}(B) → {s} {msg}")

    s, r = req("POST", f"/knowledge/{kid_a}/note",
               {"title": "B的笔记", "content": "注入内容"}, token=token_b)
    code, msg = body_code(r)
    record("权限" in str(msg), "高", "越权写笔记", f"POST /knowledge/{kid_a}/note(B) → {s} {msg}")

    if fid_a:
        s, r = req("DELETE", f"/file/{fid_a}", token=token_b)
        code, msg = body_code(r)
        record("权" in str(msg) or "无权" in str(msg), "高", "越权删文件",
               f"DELETE /file/{fid_a}(B) → {s} {msg}")

    # ── 三、身份认证 ──────────────────────────────
    s, r = req("GET", "/knowledge")  # 无 token
    record(s == 401, "高", "无token访问被拒", f"GET /knowledge(裸) → HTTP {s}")
    s, r = req("GET", "/knowledge", token="garbage.token.value")
    record(s == 401, "高", "伪造token被拒", f"GET /knowledge(伪造) → HTTP {s}")
    parts = token_a.split(".")
    # 篡改 payload 中间段(保持 base64url 合法字符)
    parts[1] = ("A" if parts[1][0] != "A" else "B") + parts[1][1:]
    s, r = req("GET", "/knowledge", token=".".join(parts))
    record(s == 401, "高", "篡改payload被拒", f"GET /knowledge(篡改) → HTTP {s}")
    s, r = req("GET", "/knowledge", token=token_a[:-2] + "xy")  # 篡改签名
    record(s == 401, "高", "篡改签名被拒", f"GET /knowledge(改签名) → HTTP {s}")

    # ── 四、偷改字段(权限提升) ──────────────────────────────
    ts = int(time.time() * 1000)
    s, r = req("POST", "/user/register",
               {"username": f"sec-elev-{ts}", "password": "x",
                "role": "admin", "id": 1, "userId": 1})
    s2, r2 = req("POST", "/user/login",
                 {"username": f"sec-elev-{ts}", "password": "x"})
    tk = r2.get("data") if isinstance(r2, dict) else None
    if tk:
        s3, r3 = req("GET", f"/knowledge/{kid_a}", token=tk)
        code, msg = body_code(r3)
        record("权限" in str(msg), "高", "注册注入role/id字段提权",
               f"带role=admin注册+登录后访问A的知识 → {s3} {msg}")
    # ── 五、恶意输入与注入 ──────────────────────────────
    s, r = req("POST", "/user/login", {"username": "' OR '1'='1' --", "password": "anything"})
    code, msg = body_code(r)
    record("不存在" in str(msg) or s == 401 or code != 200, "高", "登录SQL注入",
           f"username=' OR '1'='1 -- → {s} {msg} (未登录成功即安全)")
    s, r = req("POST", "/user/login", raw_body=b"{invalid json!!")
    code, msg = body_code(r)
    record("at com.yansheng" not in str(r), "中", "非法JSON不泄堆栈",
           f"POST /user/login(坏JSON) → {s} {str(msg)[:60]}")
    xss = "<script>alert(1)</script>"
    s, r = req("POST", "/knowledge", {"title": f"XSS{xss}", "content": "c"}, token=token_b)
    stored = code == 200 or (isinstance(r, dict) and r.get("code") == 200)
    s2, r2 = req("GET", "/knowledge", token=token_b)
    reflected = stored and any(xss in k.get("title", "") for k in r2.get("data", []))
    record(not reflected, "中", "标题XSS原样存储回显",
           f"存储{'并回显' if reflected else '被拒或未回显'}: {s} → 列表回显={reflected}")
    s, r = req("POST", "/chat", {"message": "a" * 2001}, token=token_b)
    code, msg = body_code(r)
    record("过长" in str(msg), "中", "超长消息(2001字)被拒",
           f"POST /chat → {s} {msg}")
    s, r = req("POST", "/chat", {"message": ""}, token=token_b)
    code, msg = body_code(r)
    record("不能为空" in str(msg) or "为空" in str(msg), "中", "空消息被拒",
           f"POST /chat(空) → {s} {msg}")
    s, r = req("POST", "/knowledge", {"title": "", "content": "x"}, token=token_b)
    code, msg = body_code(r)
    record("标题" in str(msg), "低", "空标题被拒", f"POST /knowledge(空标题) → {s} {msg}")

    # ── 六、绕过前端限制(直连API) ──────────────────────────────
    boundary = "----SecBoundary"
    exe_body = (
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
        f"filename=\"shell.exe\"\r\nContent-Type: application/x-msdownload\r\n\r\nMZ\x90\x00\r\n"
        f"--{boundary}--\r\n").encode()
    up = urllib.request.Request(
        BASE + f"/file/upload/{kid_a}", data=exe_body, method="POST")
    up.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    up.add_header("Authorization", "Bearer " + token_b)
    try:
        with urllib.request.urlopen(up, timeout=15) as resp:
            s, r = resp.status, json.loads(resp.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        s = e.code
        r = json.loads(e.read().decode("utf-8", "replace"))
    code, msg = body_code(r)
    record("仅支持" in str(msg), "高", "直传.exe绕过前端白名单",
           f"POST /file/upload(shell.exe,B) → {s} {msg}")

    # 空密码注册(前端会拦,后端呢?)
    ts = int(time.time() * 1000)
    s, r = req("POST", "/user/register", {"username": f"sec-emptypw-{ts}", "password": ""})
    code, msg = body_code(r)
    s2, r2 = req("POST", "/user/login", {"username": f"sec-emptypw-{ts}", "password": ""})
    tk2 = r2.get("data") if isinstance(r2, dict) else None
    record(tk2 is None, "高", "空密码可注册并登录",
           f"空密码注册 → {s}{msg}; 空密码登录 → {'成功!' if tk2 else '被拒'}")

    # ── 七、敏感信息泄露 ──────────────────────────────
    for path, expect_safe, name, risk in [
        ("/actuator/env", True, "actuator env 未暴露", "中"),
        ("/actuator/health", False, "health 可访问(预期)", "信息"),
        ("/files/../application-local.properties", True, "静态目录穿越", "高"),
        ("/api/mcp-endpoint", False, "MCP端点匿名可达(设计如此)", "信息"),
    ]:
        full = BASE.replace("/api", "") + path if not path.startswith("/api") else BASE + path[3:]
        rr = urllib.request.Request(full)
        try:
            with urllib.request.urlopen(rr, timeout=10) as resp:
                s, body = resp.status, resp.read().decode("utf-8", "replace")[:60]
        except urllib.error.HTTPError as e:
            s, body = e.code, ""
        except Exception as e:
            s, body = -1, str(e)[:40]
        if expect_safe:
            record(s in (401, 404), risk, name, f"GET {path} → HTTP {s} {body}")
        else:
            record(True, risk, name, f"GET {path} → HTTP {s} {body}")

    s, r = req("POST", "/user/login", {"username": user_a, "password": "wrong-password"})
    code, msg = body_code(r)
    # 统一文案校验:错密码与不存在用户必须返回同一文案,否则可枚举账号
    s2, r2 = req("POST", "/user/login", {"username": f"nouser-{int(time.time())}", "password": "wrong-password"})
    code2, msg2 = body_code(r2)
    same_msg = (str(msg) == str(msg2)) and ("用户名或密码错误" in str(msg))
    record(same_msg, "低", "登录错误区分用户存在性(枚举)",
           f"错密码→{s} {msg} / 不存在用户→{s2} {msg2} (文案不一致可枚举账号)")

    # ── 八、资源滥用与防刷 ──────────────────────────────
    t0 = time.time()
    spammed = 0
    for i in range(5):
        ts = int(time.time() * 1000)
        s, r = req("POST", "/user/register",
                   {"username": f"sec-spam-{ts}-{i}", "password": "x"})
        code, msg = body_code(r)
        if code == 200:
            spammed += 1
    record(spammed < 5, "中", "注册接口无速率限制",
           f"5秒内注册{spammed}/5个账号成功 (耗时{time.time()-t0:.1f}s, 无限流/验证码)")

    # 清理: 删A的测试知识(笔记/切片级联)
    req("DELETE", f"/knowledge/{kid_a}", token=token_a)
    # 攻防测试账号(sec-*)留在库中,无删除用户API

    print("\n== 汇总 ==")
    if FINDINGS:
        for risk, name, detail in FINDINGS:
            print(f"  [{risk}] {name}")
    else:
        print("  未发现漏洞")
    print(f"共 {VULN_COUNT} 个漏洞")
    sys.exit(0 if VULN_COUNT == 0 else VULN_COUNT)


if __name__ == "__main__":
    main()
