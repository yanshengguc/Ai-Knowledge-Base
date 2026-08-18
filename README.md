# Ai-Knowledge-Base —— AI 智能图书馆（Spring Boot + RAG 知识库）

一个边学边做的 Spring Boot 项目：以 RAG（检索增强生成）为核心的个人知识库后端。用户上传自己的资料（PDF/Word），系统自动解析、切片、向量化入库，并提供语义检索能力；同时完整覆盖了后端工程基础能力——三层架构、JWT 认证授权、Redis 缓存治理、阿里云 OSS 文件存储。

## 功能特性

- 用户注册 / 登录：JWT 无状态认证 + ThreadLocal UserContext 保存当前用户
- 知识 CRUD：数据归属权限控制，仅作者本人可修改 / 删除
- 文件上传：存入阿里云 OSS，文件元信息入库并与知识条目关联，带处理状态（PROCESSING / SUCCESS / FAILED）
- 文档处理流水线：PDFBox / Apache POI 解析 → 滑动窗口切片（chunkSize=500，overlap=100）→ MyBatis foreach 批量入库
- 向量化：DashScope text-embedding-v3（1024 维）→ 存入阿里云 DashVector
- 语义检索：按余弦距离召回 Top-K 相关 Chunk
- Redis 缓存治理：Cache Aside、空值缓存防穿透、随机 TTL 防雪崩、Redis 分布式锁（setIfAbsent + 双重检查）防击穿
- RAG 问答：检索 → Prompt 拼接 → LLM 生成，回答附引用来源；检索为空时 Prompt 条件检查防幻觉
- Agent 工具链：基于 Spring AI function calling 实现 file_search / file_trace 工具，ReAct 多轮循环
- 多轮会话记忆：Redis List 原子追加（RPUSH+LTRIM），窗口截断 + TTL，聊天接口 /api/chat
- 安全加固：数据归属权限隔离（修复越权漏洞）、BCrypt 密码、登录限流、上传类型白名单、密钥环境变量化
- 测试质量：59+ 自动化用例（越权/缓存/JWT/切片回归），安全攻防实测 15 项

## 当前状态(2026-08)

- ✅ 后端完整：认证 / 知识 CRUD / 上传处理 / RAG 问答 / Agent 工具 / 会话记忆 / 安全加固
- 🚧 前端开发中：Vue3 + Vite + TS + Element Plus（登录/知识库/上传/对话页已可跑通，详见 docs/FRONTEND-PLAN-2026-08-18.md）
- 📚 详细开发记录见 docs/development-log.md（Day1-43）

## 快速开始

```bash
# 后端(本地配置在 application-local.properties,密钥不入库)
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=56382"

# 前端(开发)
cd frontend && npm install && npm run dev   # http://localhost:5173,proxy 到 56382
```

## 技术栈

| 类别 | 技术 |
|------|------|
| 核心框架 | Java 17、Spring Boot 3.3.4 |
| 持久层 | MyBatis、MySQL |
| 缓存 | Redis（Spring Data Redis） |
| 认证授权 | JWT + 自定义 Filter + 白名单机制 |
| 对象存储 | 阿里云 OSS |
| AI 能力 | Spring AI Alibaba（DashScope Embedding）、阿里云 DashVector |
| 文档解析 | Apache PDFBox、Apache POI |

## 核心链路

**资料入库（RAG 预处理）：**

```
上传文件 → JWT认证 + 归属权限校验 → OSS存储
    → knowledge_file 元信息入库（status=PROCESSING）
    → PDF/Word 解析为文本
    → 滑动窗口切片（保留上下文 overlap）
    → knowledge_chunk 批量入库
    → Embedding 向量化
    → 写入 DashVector 向量库（status=SUCCESS）
```

**语义检索：**

```
查询问题 → Embedding → DashVector 相似度检索 → Top-K Chunks
（接入 Rerank + LLM 生成回答为下一阶段目标）
```

## 项目结构

```
src/main/java/com/yansheng/aiknowledgebase/
├── controller/          # REST 接口层
├── service/             # 业务接口（含 parser 解析器工厂）
│   ├── impl/            # 业务实现
│   └── parser/          # DocumentParser 接口 + PDF/Word 实现 + 工厂
├── mapper/              # MyBatis 数据访问层
├── entity/ dto/ vo/     # 数据对象分层
├── config/              # Redis / OSS / JWT Filter / Web 配置
├── common/              # Result 统一返回、BusinessException
└── handler/             # 全局异常处理器
```

## 快速开始

环境要求：JDK 17、Maven、MySQL 8.x、Redis，以及阿里云账号（OSS / 百炼 DashScope / DashVector）。

1. 克隆项目：

```bash
git clone https://github.com/yanshengguc/Ai-Knowledge-Base.git
```

2. 初始化数据库：执行 `docs/schema.sql`

3. 复制配置模板并填入你自己的密钥：

```bash
cd src/main/resources
copy application-local.properties.example application-local.properties   # Windows
# cp application-local.properties.example application-local.properties   # Linux/macOS
```

4. 启动：

```bash
mvn spring-boot:run
```

## 配置说明

所有敏感配置集中在 `application-local.properties`（已被 .gitignore 忽略），主配置文件只保留 `${}` 占位符：

| 配置项 | 说明 |
|--------|------|
| `spring.datasource.password` | MySQL 密码 |
| `aliyun.oss.access-key-id` / `access-key-secret` | 阿里云 OSS AccessKey（建议用 RAM 子账号） |
| `spring.ai.dashscope.api-key` | 百炼平台 API Key（Embedding 模型） |
| `dashvector.api-key` / `dashvector.endpoint` | DashVector 向量库凭证 |

**请勿将任何真实密钥提交到仓库。**

## 接口一览

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | /api/user/register | 用户注册 | 否 |
| POST | /api/user/login | 登录，返回 JWT | 否 |
| GET | /api/user/{id} | 查询用户 | 是 |
| GET | /api/knowledge | 知识列表 | 是 |
| GET | /api/knowledge/{id} | 知识详情（Redis 缓存） | 是 |
| POST | /api/knowledge | 新增知识 | 是 |
| PUT | /api/knowledge/{id} | 修改知识（仅作者） | 是 |
| DELETE | /api/knowledge/{id} | 删除知识（仅作者） | 是 |
| POST | /api/file/upload/{knowledgeId} | 上传文件并进入 RAG 处理流水线 | 是 |

## 进度与路线图

已完成：后端基础与 JWT（Day1–14）→ Redis 缓存治理（Day15–21）→ 文件上传与 OSS（Day22–24）→ 文档解析 / 切片 / 入库（Day25–29）→ Embedding 与向量存储检索（Day31–34）

进行中 / 计划中：

- 接入 Rerank 与 LLM，基于检索结果生成带引用的回答
- 防幻觉设计：检索闸 + 提示词闸 + 来源标注
- 按置信分数决策"直答还是触发搜索"，配套评估集验证

## 开发日志

[docs/development-log.md](docs/development-log.md) 记录了逐日的完整开发过程：每天做了什么、踩了哪些坑、每个 Bug 的根因与修复方式，以及对应的面试知识点整理。

## 许可证

[MIT](LICENSE)。本项目为学习用途，按"现状"提供，不附带任何保证。
