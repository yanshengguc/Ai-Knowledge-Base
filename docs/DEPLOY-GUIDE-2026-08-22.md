# AKB 部署指南(2026-08-22,Docker 化就绪)

> 目标:一台云服务器(2C4G 即可)+ Docker,一键起全套(MySQL/Redis/后端/前端)。
> 前置:服务器已装 Docker + Docker Compose;域名或 IP 直连。

## 一、部署步骤(服务器上执行)

```bash
# 1. 把代码传到服务器(或用 git clone,注意 .env 不入库)
git clone <你的仓库> && cd Ai-Knowledge-Base

# 2. 准备环境变量(真实 key,不要提交)
cp .env.example .env
#   编辑 .env,填:MYSQL_ROOT_PASSWORD / JWT_SECRET_KEY / DASHSCOPE_API_KEY /
#   DASHVECTOR_API_KEY / DASHVECTOR_ENDPOINT / DEEPSEEK_API_KEY /
#   OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET / SILICONFLOW_API_KEY(可选)

# 3. 构建并启动(首次拉镜像较慢,服务器需能访问 Docker Hub;国内可配镜像加速)
docker compose up -d --build

# 4. 验证
docker compose ps            # 4 个服务都 healthy/up
curl http://localhost:18082/actuator/health   # {"status":"UP"}
# 浏览器访问 http://<服务器IP>:18000
```

## 二、端口与访问

| 服务 | 端口(默认) | 说明 |
|---|---|---|
| 前端(nginx) | 18000 | 浏览器入口,/api 反代到后端,SSE 已关缓冲 |
| 后端(Spring Boot) | 18082 | API + /api/mcp-endpoint(MCP 需带 JWT) |
| MySQL / Redis | 内部网络 | 不对外暴露(未映射端口) |

`.env` 可改 `BACKEND_PORT`/`FRONTEND_PORT`;生产环境建议再套一层安全组 + HTTPS(可选 nginx SSL)。

## 三、首次启动行为

- MySQL 首次启动自动执行 `docs/schema.sql`:建库建表 + **FULLTEXT 索引(混合检索 BM25 路)**。
- 若 MySQL 数据卷已存在(重复部署),init 脚本不执行——需要手动执行:
  `docker compose exec mysql mysql -uroot -p -e "CREATE FULLTEXT INDEX ft_content ON ai_knowledge_base.knowledge_chunk(content) WITH PARSER ngram;"`(已存在会报错,先 DROP)

## 四、生产 profile 说明

- 后端以 `--spring.profiles.active=prod` 启动,加载 `application-prod.properties`(全部环境变量化)。
- **application.properties 不再 include local**(防止 prod 加载本地真实 key);本地开发用 `bash scripts/run-dev.sh`。
- 安全默认:actuator 只暴露 health;MCP 工具需 JWT(匿名拒绝业务数据);检索按用户隔离。

## 五、验证清单(部署后必跑)

```
1. 注册新用户 → 登录 → 建知识 → 传 PDF → 状态 SUCCESS → 问答有引用 ✅
2. 新建笔记 → 问笔记内容 → 能召回 ✅(写优先)
3. 混合检索:问专有名词(如文档里的唯一标记词)→ 能召回 ✅
4. 导出:点击导出 → 下载 Markdown ✅
5. 越权:用户 B 读用户 A 知识 → 拒绝 ✅
6. 匿名访问 → 401 ✅
```
(以上每项都有对应测试:`bash scripts/test.sh` 86 个 + `bash scripts/test-e2e.sh` 深度验证)
