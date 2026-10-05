#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DashVector 集群迁移脚本（预写 · 到期应对 · 2026-10-05）

用途：DashVector 免费集群 aikb-free-2026（2026-10-11 19:37:08 到期，到期即释放、数据全删）
     到期后，在 PO 重新申请的免费试用集群上重建两个集合并回灌全部向量。

依赖（执行前先安装）：
    pip install requests pymysql

用法：
    python dashvector-migrate.py \
        --endpoint <新集群endpoint> \
        --api-key <新集群api-key> \
        --dashscope-key <DashScope api-key> \
        --mysql-host <host> --mysql-port 3306 --mysql-user <user> \
        --mysql-password <pwd> --mysql-db ai_knowledge_base \
        --memory-backup <long_term_memory-2026-10-05.json>

    # 先空跑校验参数与数据量（不写任何数据）
    python dashvector-migrate.py ... --dry-run

范围：
    1) 建集合 knowledge_chunk_vector（1024 维 cosine）+ long_term_memory（1024 维 cosine）
    2) 从 MySQL knowledge_chunk 读全部 chunk，用 DashScope text-embedding-v3 重嵌入，
       以 chunk.id 为主键 upsert（fields: file_id / content）
    3) 从备份 JSON 恢复长期记忆（按 content 重嵌入，fields: user_id / content / created_at）

对齐口径（与线上一致，勿改）：
    - 集合名 / 维度 / metric：knowledge_chunk_vector 1024 cosine；long_term_memory 1024 cosine
    - 向量主键：chunk 用 str(chunk.id)；记忆用备份里的 id（形如 <userId>_<timestamp>）
    - 向量文本：chunk.content（原样）；embedding 用 text-embedding-v3 + text_type=document + 1024 维
    - DashVector REST：upsert = POST /v1/collections/{c}/docs/upsert；建集合 = POST /v1/collections；
      鉴权 header = dashvector-auth-token

注意：
    - 全程只写新集群，不触碰旧集群、不改生产库 / 生产配置。
    - 回灌完成后须人工改 /etc/aikb/aikb.env 的 DASHVECTOR_ENDPOINT / DASHVECTOR_API_KEY 并重启 aikb。
    - 幂等：upsert 按主键覆盖，可安全重复执行。
"""
import argparse
import json
import sys

import pymysql
import requests

DASHSCOPE_EMBED_URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding"
EMBED_MODEL = "text-embedding-v3"
EMBED_DIM = 1024
CHUNK_COLLECTION = "knowledge_chunk_vector"
MEMORY_COLLECTION = "long_term_memory"
UPSERT_SUB_BATCH = 20  # 单次 REST upsert 条数


def dv_headers(api_key):
    return {"dashvector-auth-token": api_key, "Content-Type": "application/json"}


def dv_create_collection(endpoint, api_key, name, dimension):
    url = "https://%s/v1/collections" % endpoint
    body = {"name": name, "dimension": dimension, "metric": "cosine", "dtype": "FLOAT"}
    data = requests.post(url, headers=dv_headers(api_key), json=body, timeout=60).json()
    if data.get("code") == 0:
        print("[create] %s 创建成功" % name)
        return True
    msg = str(data.get("message", ""))
    if "exist" in msg.lower() or "已存在" in msg:
        print("[create] %s 已存在，幂等跳过" % name)
        return True
    print("[create] %s 失败: %s" % (name, data))
    return False


def dv_upsert_batch(endpoint, api_key, collection, docs):
    url = "https://%s/v1/collections/%s/docs/upsert" % (endpoint, collection)
    data = requests.post(url, headers=dv_headers(api_key), json={"docs": docs}, timeout=120).json()
    if data.get("code") != 0:
        raise RuntimeError("upsert 顶层失败: code=%s message=%s" % (data.get("code"), data.get("message")))
    out = data.get("output")
    if isinstance(out, list):
        bad = [x for x in out if x.get("code") != 0]
        if bad:
            raise RuntimeError("upsert 逐条失败 %d 条, 首个=%s" % (len(bad), bad[0]))
    return data


def dv_stats(endpoint, api_key, collection):
    url = "https://%s/v1/collections/%s/stats" % (endpoint, collection)
    return requests.get(url, headers=dv_headers(api_key), timeout=60).json()


def embed_texts(dashscope_key, texts, text_type="document"):
    headers = {"Authorization": "Bearer %s" % dashscope_key, "Content-Type": "application/json"}
    body = {
        "model": EMBED_MODEL,
        "input": {"texts": texts},
        "parameters": {"text_type": text_type, "dimension": EMBED_DIM},
    }
    data = requests.post(DASHSCOPE_EMBED_URL, headers=headers, json=body, timeout=120).json()
    if data.get("code"):
        raise RuntimeError("embedding 失败: %s" % data)
    out = data.get("output", {}).get("embeddings", [])
    out = sorted(out, key=lambda e: e.get("text_index", 0))
    return [e["embedding"] for e in out]


def embed_in_batches(dashscope_key, texts, batch):
    vectors = []
    for i in range(0, len(texts), batch):
        vectors.extend(embed_texts(dashscope_key, texts[i:i + batch]))
    return vectors


def fetch_chunks(cfg):
    conn = pymysql.connect(host=cfg["host"], port=cfg["port"], user=cfg["user"],
                           password=cfg["password"], database=cfg["db"],
                           charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, file_id, content FROM knowledge_chunk ORDER BY id")
            return cur.fetchall()
    finally:
        conn.close()


def write_docs(endpoint, api_key, collection, docs, dry_run, label):
    if dry_run:
        print("[dry-run] %s: 跳过写入 %d 条" % (label, len(docs)))
        return
    for i in range(0, len(docs), UPSERT_SUB_BATCH):
        dv_upsert_batch(endpoint, api_key, collection, docs[i:i + UPSERT_SUB_BATCH])
    print("[%s] 已写入 %d 条" % (label, len(docs)))


def restore_chunks(args, chunks):
    docs = []
    for i in range(0, len(chunks), args.embed_batch):
        batch = chunks[i:i + args.embed_batch]
        texts = [(c["content"] or "") for c in batch]
        vectors = embed_texts(args.dashscope_key, texts)
        for c, v in zip(batch, vectors):
            docs.append({
                "id": str(c["id"]),
                "vector": v,
                "fields": {"file_id": int(c["file_id"]), "content": c["content"] or ""},
            })
        print("[chunk] 已嵌入 %d/%d" % (min(i + args.embed_batch, len(chunks)), len(chunks)))
    write_docs(args.endpoint, args.api_key, CHUNK_COLLECTION, docs, args.dry_run, "chunk")


def restore_memories(args):
    with open(args.memory_backup, "r", encoding="utf-8") as f:
        backup = json.load(f)
    src = backup.get("docs", [])
    if not src:
        print("[memory] 备份无 docs，跳过")
        return
    vectors = embed_in_batches(args.dashscope_key, [(d.get("fields", {}).get("content", "") or "") for d in src], 10)
    docs = []
    for d, v in zip(src, vectors):
        f_ = d.get("fields", {})
        docs.append({
            "id": str(d["id"]),
            "vector": v,
            "fields": {
                "user_id": int(f_.get("user_id")),
                "content": f_.get("content", "") or "",
                "created_at": int(f_.get("created_at", 0)),
            },
        })
    write_docs(args.endpoint, args.api_key, MEMORY_COLLECTION, docs, args.dry_run, "memory")


def main():
    p = argparse.ArgumentParser(description="DashVector 集群迁移（建集合 + 回灌 chunk 向量 + 恢复长期记忆）")
    p.add_argument("--endpoint", required=True, help="新集群 endpoint（不含 https://）")
    p.add_argument("--api-key", required=True, help="新集群 DashVector API key")
    p.add_argument("--dashscope-key", required=True, help="DashScope API key（同一账号）")
    p.add_argument("--mysql-host", required=True)
    p.add_argument("--mysql-port", type=int, default=3306)
    p.add_argument("--mysql-user", required=True)
    p.add_argument("--mysql-password", required=True)
    p.add_argument("--mysql-db", default="ai_knowledge_base")
    p.add_argument("--memory-backup", required=True, help="长期记忆备份 JSON 路径")
    p.add_argument("--embed-batch", type=int, default=10, help="单次 embedding 文本数（默认 10）")
    p.add_argument("--dry-run", action="store_true", help="只读校验，不写任何数据")
    args = p.parse_args()

    print("== 1/4 建集合 ==")
    if not args.dry_run:
        if not dv_create_collection(args.endpoint, args.api_key, CHUNK_COLLECTION, EMBED_DIM):
            sys.exit(1)
        if not dv_create_collection(args.endpoint, args.api_key, MEMORY_COLLECTION, EMBED_DIM):
            sys.exit(1)
    else:
        print("[dry-run] 跳过建集合")

    print("== 2/4 读取生产 chunk ==")
    chunks = fetch_chunks({
        "host": args.mysql_host, "port": args.mysql_port, "user": args.mysql_user,
        "password": args.mysql_password, "db": args.mysql_db,
    })
    print("[chunk] MySQL 读取 %d 条" % len(chunks))

    print("== 3/4 回灌 chunk 向量 ==")
    restore_chunks(args, chunks)

    print("== 4/4 恢复长期记忆 ==")
    restore_memories(args)

    print("== 复核 ==")
    if args.dry_run:
        print("[dry-run] 结束，未写入任何数据")
        return
    print("chunk stats :", dv_stats(args.endpoint, args.api_key, CHUNK_COLLECTION))
    print("memory stats:", dv_stats(args.endpoint, args.api_key, MEMORY_COLLECTION))
    print("期望：chunk total_doc_count=%d，memory total_doc_count=备份 docs 数" % len(chunks))
    print("后续：改 /etc/aikb/aikb.env 的 DASHVECTOR_ENDPOINT / DASHVECTOR_API_KEY → 建回滚点 → systemctl restart aikb → 验证向量召回与记忆召回")


if __name__ == "__main__":
    main()
