#!/usr/bin/env python3
"""演示数据播种:给演示账号一键铺一套"有东西可看"的知识库(幂等,重复执行自动跳过已有条目)。

流程:登录(账号不存在且注册开放时自动注册)→ 建 3 个知识条目 → createNote 写笔记
(真实切片+向量化管线)→ 上传 .md 文件 → 轮询至 SUCCESS → 可选发 1 条问答验证检索链路。

用法(风格同 security_attack.py,参数走环境变量):
  python scripts/seed_demo.py                                        # 默认打生产演示账号
  DEMO_BASE=http://127.0.0.1:56382/api python scripts/seed_demo.py   # 本地靶机
  DEMO_ASK=1 python scripts/seed_demo.py                             # 追加问答验证(消耗少量 LLM 费用)

生产首次建 demo 账号(register.enabled=false 拦截陌生人注册,需临时放开):
  1. /etc/aikb/aikb.env 临时设 REGISTER_ENABLED=true 并 systemctl restart aikb
  2. 注册:curl -s -X POST http://120.55.76.141/api/user/register \
       -H "Content-Type: application/json" -d '{"username":"demo","password":"demo123"}'
  3. REGISTER_ENABLED 改回 false 并重启(README 公开了 demo 密码,注册口必须关)
  4. python scripts/seed_demo.py

退出码 = 失败步骤数,0 表示全部通过(可接 CI/部署后自动验收)。
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("DEMO_BASE", "http://120.55.76.141/api")
DEMO_USER = os.environ.get("DEMO_USER", "demo")
DEMO_PASS = os.environ.get("DEMO_PASS", "demo123")
DO_ASK = os.environ.get("DEMO_ASK") == "1"

FILE_WAIT_SECONDS = 90
ASK_QUESTION = "缓存穿透、缓存击穿、缓存雪崩分别是什么?各自怎么防?"

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


def upload_md(path, filename, content, token, timeout=60):
    """multipart 上传 Markdown(与前端一致的字段名 file)。"""
    boundary = "----aikbSeed" + str(int(time.time() * 1000))
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: text/markdown\r\n\r\n"
    ).encode() + content.encode("utf-8") + f"\r\n--{boundary}--\r\n".encode()
    r = urllib.request.Request(BASE + path, data=body, method="POST")
    r.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}


def check(name, ok, detail=""):
    RESULTS.append((name, "PASS" if ok else "FAIL", detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}")


def body_ok(resp):
    """项目约定:BusinessException 返回 HTTP 200 + body code 500,必须看 body 层。"""
    return isinstance(resp, dict) and resp.get("code") == 200


def login_or_register():
    status, resp = req("POST", "/user/login", {"username": DEMO_USER, "password": DEMO_PASS})
    if body_ok(resp):
        return resp["data"]
    print(f"[INFO] 登录失败({resp.get('message', '')}),尝试自动注册(注册开放时)…")
    status, resp = req("POST", "/user/register", {"username": DEMO_USER, "password": DEMO_PASS})
    if not body_ok(resp):
        check("演示账号", False, f"注册被拒:{resp.get('message', '')}——生产请按脚本头部说明临时放开注册")
        return None
    status, resp = req("POST", "/user/login", {"username": DEMO_USER, "password": DEMO_PASS})
    if not body_ok(resp):
        check("演示账号", False, "注册后登录失败")
        return None
    return resp["data"]


# ---- 演示数据(知识条目 + 笔记 + .md 上传文件;## 标题供结构感知切片) ----

DEMO_ITEMS = [
    {
        "title": "Redis 缓存实战",
        "category": "数据库",
        "content": "Redis 在高并发读多写少场景下的缓存治理笔记:三大问题防护、缓存与数据库一致性、TTL 设计。",
        "note": {
            "title": "缓存三大问题:穿透、击穿、雪崩",
            "content": (
                "# 缓存三大问题\n\n"
                "## 缓存穿透\n\n"
                "查询**不存在**的数据,缓存永远未命中,请求全部打到数据库。\n\n"
                "防护:空值缓存(短 TTL)、参数合法性校验、布隆过滤器拦截。\n\n"
                "## 缓存击穿\n\n"
                "某个**热点 key 过期瞬间**,大量并发同时回源数据库。\n\n"
                "防护:互斥锁(setIfAbsent 分布式锁 + 双重检查,拿不到锁的线程短暂等待后读缓存)、热点 key 逻辑过期。\n\n"
                "## 缓存雪崩\n\n"
                "**大批 key 同时到期**或 Redis 宕机,流量整体压向数据库。\n\n"
                "防护:过期时间加随机抖动(随机 TTL)、多级缓存、集群高可用与限流降级。\n\n"
                "## 小结\n\n"
                "穿透防\"查不到\",击穿防\"热点过期\",雪崩防\"集体过期\"——本项目的 Knowledge 详情缓存三防护齐全。"
            ),
        },
        "upload": {
            "filename": "redis-cache-aside-design.md",
            "content": (
                "# Cache Aside 模式设计要点\n\n"
                "## 读路径\n\n"
                "先读缓存,未命中读库并回填;回填必须设置过期时间,避免脏数据长期驻留。\n\n"
                "## 写路径\n\n"
                "先更新数据库,再删除缓存(而非更新缓存)——并发写时更新缓存易写入旧值,删除缓存让下次读自然回源。\n\n"
                "## 过期时间设计\n\n"
                "固定 TTL 会造成雪崩风险:同一批 key 同时写入、同时过期。\n\n"
                "工程做法:基础 TTL + 随机抖动(例如 30min ± 5min),把到期时间摊开。\n\n"
                "## 空值缓存\n\n"
                "对确定不存在的 key 缓存空标记并配短 TTL,挡住反复查询不存在数据的穿透流量。"
            ),
        },
    },
    {
        "title": "Java 后端面试笔记",
        "category": "面试",
        "content": "Java 后端工程与面试知识整理:并发、JVM、线程池。",
        "note": {
            "title": "线程池参数怎么定",
            "content": (
                "# 线程池参数设计\n\n"
                "## 核心参数\n\n"
                "corePoolSize / maximumPoolSize / queueCapacity / rejectionPolicy 四件套。\n\n"
                "## IO 密集 vs CPU 密集\n\n"
                "CPU 密集型:核数 + 1;IO 密集型:核数 × 2 或按等待比估算——文档解析、HTTP 调用都是 IO 密集。\n\n"
                "## 为什么禁止默认 commonPool\n\n"
                "CompletableFuture.runAsync 不传 executor 会走 ForkJoinPool.commonPool,线程数 ≈ 核数-1,"
                "多个 IO 任务互相抢线程会饥饿。本项目文档处理用独立线程池(核心 2/最大 4/队列 100/CallerRunsPolicy)。\n\n"
                "## 拒绝策略\n\n"
                "有界队列满时的兜底:CallerRunsPolicy 让提交线程自己执行,天然反压不丢任务。"
            ),
        },
        "upload": {
            "filename": "jvm-memory-and-gc.md",
            "content": (
                "# JVM 内存结构与 GC\n\n"
                "## 运行时数据区\n\n"
                "堆、虚拟机栈、本地方法栈、程序计数器、方法区(元空间)。对象实例在堆上,线程私有的栈保存局部变量与栈帧。\n\n"
                "## 堆的分代\n\n"
                "新生代(Eden + 两个 Survivor)与老年代。对象优先在 Eden 分配,Minor GC 后存活对象进 Survivor,年龄达标晋升老年代。\n\n"
                "## 常见收集器\n\n"
                "Parallel 注重吞吐,CMS 已废弃,G1 面向大堆可预期停顿,ZGC/Shenandoah 追求亚毫秒停顿。\n\n"
                "## 容器环境的坑\n\n"
                "小内存机器(如 2C2G)要显式设 -Xmx 并预留系统内存,否则 OOM Killer 直接杀进程;"
                "必要时配置 swap 兜底——本项目生产 512m 堆 + 4GB swap 就是这么定的。"
            ),
        },
    },
    {
        "title": "RAG 与 LLM 应用",
        "category": "AI",
        "content": "检索增强生成的工程实践:切片、检索、重排、防幻觉、Agent 工具调用。",
        "note": {
            "title": "切片策略:固定窗口 vs 结构感知",
            "content": (
                "# 切片策略对比\n\n"
                "## 固定滑动窗口\n\n"
                "chunkSize=500 / overlap=100,实现简单、分布均匀;缺点是可能在句子中间切断,丢失上下文语义。\n\n"
                "## 结构感知切片\n\n"
                "优先按 Markdown 标题切,再按段落/句子边界兜底,并把章节标题前置到每个 chunk——"
                "检索命中的切片自带\"它属于哪一章\"的语境,长文档中段内容的召回明显更稳。\n\n"
                "## 评估驱动\n\n"
                "切片改动必须用评估集验证:构造\"答案位于长文档中段章节\"的用例(section-locate),"
                "看 recall@5 与 MRR 是否退化,不能凭感觉。"
            ),
        },
        "upload": {
            "filename": "hybrid-retrieval-notes.md",
            "content": (
                "# 混合检索与 Rerank\n\n"
                "## 为什么单路向量检索不够\n\n"
                "向量检索擅长语义相似,但对精确关键词(报错码、专有名词)不敏感;BM25 相反。两路召回取并集互补。\n\n"
                "## Rerank 的位置\n\n"
                "粗召回(topK×3)只求不漏,精排交给 Rerank 模型对 query-切片对逐一打分,取 TopK 进上下文——"
                "排序质量直接决定 MRR。\n\n"
                "## 检索缓存\n\n"
                "问答是典型读多写少:query embedding 与检索结果都可以进 Redis(按用户 + 归一化 query 做 key),"
                "上传新文件时按用户失效,避免重复付费调用。\n\n"
                "## 防幻觉\n\n"
                "检索为空时 Prompt 明确要求模型回答\"资料中未找到\",禁止编造;回答必须附切片级引用可溯源。"
            ),
        },
    },
]


def existing_titles(token):
    status, resp = req("GET", "/knowledge", token=token)
    if not body_ok(resp):
        return None, resp
    titles = {item.get("title") for item in (resp.get("data") or [])}
    return titles, resp


def seed_knowledge(item, token, existed):
    """建知识(已存在则跳过)+ 补齐缺失的笔记/文件(按 fileName 对齐,保证半途失败重跑可续)。"""
    title = item["title"]
    if title in existed:
        check(f"知识[{title}]", True, "已存在,跳过创建")
    else:
        status, resp = req("POST", "/knowledge",
                           {"title": title, "content": item["content"], "category": item["category"]},
                           token=token)
        check(f"知识[{title}]", body_ok(resp), str(resp.get("message", "")) if not body_ok(resp) else "已创建")

    status, resp = req("GET", "/knowledge", token=token)
    kid = None
    if body_ok(resp):
        kid = next((k.get("id") for k in resp["data"] if k.get("title") == title), None)
    if kid is None:
        check(f"知识[{title}] 定位 id", False, "列表中未找到,跳过笔记/文件")
        return None

    # 已有文件清单(笔记与上传文件都会落 knowledge_file,fileName 对齐去重)
    status, files = req("GET", f"/file/list/{kid}", token=token)
    have = {f.get("fileName") for f in files.get("data") or []} if body_ok(files) else set()

    note_title = item["note"]["title"]
    if note_title in have:
        check(f"笔记[{note_title}]", True, "已存在,跳过")
    else:
        status, resp = req("POST", f"/knowledge/{kid}/note",
                           {"title": note_title, "content": item["note"]["content"], "source": None},
                           token=token)
        check(f"笔记[{note_title}]", body_ok(resp), str(resp.get("message", "")) if not body_ok(resp) else "已写入(同步切片+向量化)")

    up = item["upload"]
    if up["filename"] in have:
        check(f"文件[{up['filename']}]", True, "已存在,跳过")
    else:
        status, resp = upload_md(f"/file/upload/{kid}", up["filename"], up["content"], token)
        check(f"文件[{up['filename']}]", body_ok(resp), str(resp.get("message", "")) if not body_ok(resp) else "已上传,进入处理流水线")
    return kid


def wait_all_success(token):
    """轮询文件状态至 SUCCESS;note 是同步管线,只有上传文件需要等。"""
    status, resp = req("GET", "/knowledge", token=token)
    if not body_ok(resp):
        check("文件处理轮询", False, "知识列表不可用")
        return
    deadline = time.time() + FILE_WAIT_SECONDS
    pending = True
    while time.time() < deadline:
        pending = False
        failed = []
        for k in resp["data"]:
            if k.get("title") not in {i["title"] for i in DEMO_ITEMS}:
                continue
            status, files = req("GET", f"/file/list/{k['id']}", token=token)
            for f in files.get("data") or []:
                st = (f.get("status") or "").upper()
                if st == "PROCESSING":
                    pending = True
                elif st != "SUCCESS":
                    failed.append(f"{f.get('fileName')}={st or 'UNKNOWN'}")
        if not pending:
            break
        time.sleep(3)
    check("文件处理流水线", pending is False and not failed,
          "超时仍有 PROCESSING" if pending else (f"失败文件:{failed}" if failed else "全部 SUCCESS"))


def verify_ask(token):
    status, resp = req("POST", "/chat", {"message": ASK_QUESTION, "enableWebSearch": False}, token=token, timeout=90)
    if not body_ok(resp):
        check("问答验证", False, str(resp.get("message", "")))
        return
    data = resp.get("data") or {}
    answer = (data.get("answer") or "")[:120].replace("\n", " ")
    refs = data.get("references") or []
    check("问答验证", bool(answer) and len(refs) > 0,
          f"引用 {len(refs)} 条 | 答案摘录:{answer}…")


def main():
    print(f"目标: {BASE} | 演示账号: {DEMO_USER}")
    token = login_or_register()
    if token is None:
        return 1
    print()

    titles, _ = existing_titles(token)
    if titles is None:
        check("知识列表", False, "接口不可用")
        return 1

    for item in DEMO_ITEMS:
        seed_knowledge(item, token, titles)
    print()

    wait_all_success(token)

    if DO_ASK:
        print()
        verify_ask(token)

    fails = sum(1 for _, ok, _ in RESULTS if not ok)
    print(f"\n=== {len(RESULTS) - fails}/{len(RESULTS)} PASS ===")
    return fails


if __name__ == "__main__":
    sys.exit(main())
