# -*- coding: utf-8 -*-
"""清理测试垃圾数据(attack*/bench-*/chat-* 用户及关联知识/文件/切片)
- 用途:演示/上线前执行,或测试污染后执行
- 只删测试特征用户名(attack/bench/chat 开头),不碰正常数据
"""
import re
import pymysql

def load_props(path):
    d = {}
    for line in open(path, encoding="utf-8"):
        m = re.match(r"\s*(\w+[.\w]*)=(.+)", line.strip())
        if m:
            d[m.group(1)] = m.group(2).strip()
    return d

base = load_props(r"C:/Users/yansheng/IdeaProjects/Ai-Knowledge-Base/src/main/resources/application.properties")
local = load_props(r"C:/Users/yansheng/IdeaProjects/Ai-Knowledge-Base/src/main/resources/application-local.properties")
base.update(local)  # local 覆盖

host = base.get("spring.datasource.url", "jdbc:mysql://localhost:3306/ai_knowledge_base")
user = base.get("spring.datasource.username", "root")
passwd = base.get("spring.datasource.password", "")
m = re.search(r"//([^:/]+):(\d+)/(\w+)", host)
dbhost, port, dbname = m.group(1), int(m.group(2)), m.group(3)

conn = pymysql.connect(host=dbhost, port=port, user=user, password=passwd, database=dbname, charset="utf8mb4")
cur = conn.cursor()

cur.execute("""SELECT id, username FROM user
               WHERE username LIKE 'attack%' OR username LIKE 'bench-%'
               OR username LIKE 'chat-%' OR username LIKE 'attA%' OR username LIKE 'attB%'
               OR username LIKE 'flowtest%' OR username LIKE 'fe_test%'
               OR username LIKE 'verify%'
               OR username LIKE 'sse_test%'""")
users = cur.fetchall()
print("待清理测试用户数:", len(users))

total_knowledge = 0
for uid, uname in users:
    cur.execute("SELECT id FROM knowledge WHERE user_id=%s", (uid,))
    kids = [r[0] for r in cur.fetchall()]
    if kids:
        kfmt = ",".join(["%s"] * len(kids))
        # chunk 按 file_id 关联:先取该用户 file 表 id
        cur.execute("SELECT id FROM knowledge_file WHERE knowledge_id IN (%s)" % kfmt, kids)
        fids = [r[0] for r in cur.fetchall()]
        if fids:
            ffmt = ",".join(["%s"] * len(fids))
            cur.execute("DELETE FROM knowledge_chunk WHERE file_id IN (%s)" % ffmt, fids)
        cur.execute("DELETE FROM knowledge_file WHERE knowledge_id IN (%s)" % kfmt, kids)
        cur.execute("DELETE FROM knowledge WHERE id IN (%s)" % kfmt, kids)
        total_knowledge += len(kids)
    cur.execute("DELETE FROM user WHERE id=%s", (uid,))

conn.commit()
print("清理完成:用户 %d 个,知识 %d 条" % (len(users), total_knowledge))
cur.close()
conn.close()
