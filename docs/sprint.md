# Sprint 4 · 目标：B-114 回填可行性验证（只读对账）——真实 md 原文重跑切片器 vs 库内 knowledge_chunk 逐条对账，判定 Phase1 开工前置是否成立

> 开于 2026-10-01 | 计划会由 SM 主持，PO 拍板范围为选项 A（只读对账；B-116 跨 DDL 与 B-103 打包优化让位）
> 收于 2026-10-01 | **结论：完成，但结论是"前提不成立"** —— 对存量 chunk 无法用"逐条一致"验收，B-114 Phase1 的对齐语义必须改写

## 角色分工
- PO：用户（AI 起草，拍板权归用户）
- SM：本会话
- DEV：本会话（一体模式）

## 本轮范围（计划会第 5 步：划定边界）

**允许改**
- 新增只读探针测试类 `src/test/java/com/yansheng/aiknowledgebase/B114BackfillAlignmentProbeTest.java`（`@Tag("integration")`，默认回归排除；只做 SELECT + OSS GET，不写库、不调 LLM、不启 Spring 上下文）
- 文档：`docs/sprint.md`、`docs/backlog.md`、`HANDOFF.md`

**不许碰**
- `DocumentSplitter` 接口及 `StructureAwareSplitter` / `SimpleTextSplitter` 的**行为**（本轮只读，发现问题只记录不改）
- 聊天/检索/切片主链路、`IndexingService`、`RetrievalService`
- 任何 DDL、`docs/schema.sql`、线上数据
- REST 契约与前端

## 开工前侦察（已完成 · 2026-10-01）

只读实测（本地库 `ai_knowledge_base` @ MySQL 8.0.44）：

- `knowledge_chunk` 表**无 `knowledge_id` 列**，归属链为 `knowledge.id ← knowledge_file.knowledge_id`、`knowledge_file.id ← knowledge_chunk.file_id`
- `knowledge.content` 只存短描述（数十字符），**不是**文件原文；原文唯一来源是 OSS（bucket 非公开读，匿名 HEAD 403，需 SDK 签名）
- **口径混杂实证**：
  - `file_id=52`（上课讲义.md，08-24 创建，20 chunk）：`CHAR_LENGTH(content)` 恒为 **500** = `splitter.chunk-size`，且 chunk 0 头部同时含 `#`/`##`/`###` 三级标题 → 判定为**固定窗口（SimpleTextSplitter）口径**，非结构感知
  - `file_id=145/146/147`（08-28 创建）：每 chunk = 单个 `## 小节 + 正文`，42~165 字符 → 判定为**结构感知口径**

## 任务
状态：[ ] 待开发 · [~] 开发中 · [R] 待评审 · [Q] 待验证 · [x] 完成 · [!] 阻塞

- [x] T-1 只读探针（←B-114）：OSS 取原文 → 当前 `StructureAwareSplitter(500,100)` 重跑 → 与库内逐条对账 · 交付物：`B114BackfillAlignmentProbeTest`（4 个文件实跑，12/46 逐条一致）
- [x] T-2 判定：B-114 Phase1 开工前置**不成立**，对齐语义需改写（见下）
- [x] T-3 收口：sprint.md / backlog.md / HANDOFF.md 同步 + commit

## 实测结果（T-1 输出 · 可复跑）

复跑命令：`mvn test -Dtest=B114BackfillAlignmentProbeTest -DexcludedGroups=e2e -DfailIfNoTests=false`
筛选口径：本地库中 `file_name LIKE '%.md'` + `file_url IS NOT NULL` + `status=SUCCESS` + 有 chunk 的文件（共 4 个）

| file_id | 文件名 | 原始字符数 | 库内 chunk | 重跑 chunk | 逐条一致 |
|---|---|---|---|---|---|
| 52 | 上课讲义.md | 7949 | 20 | 34 | **0/34** |
| 146 | redis-cache-aside-design.md | 269 | 4 | 4 | **4/4** |
| 148 | jvm-memory-and-gc.md | 347 | 4 | 4 | **4/4** |
| 150 | hybrid-retrieval-notes.md | 327 | 4 | 4 | **4/4** |

汇总：处理 4 个文件，完全一致 3 个，逐条一致率 **12/46**。

file_id=52 首个不一致（index=0）对比：
- 库内：`len=499  head=# 药品管理系统 - 前端开发课程讲义\n\n## 一、项目概述\n\n### 1.1 项目架构...`
- 重跑：`len=209  head=### 1.1 项目架构\n本项目采用前后端分离架构:\n| 层级 | 技术栈 | 职责 |...`

## 判定（T-2）

1. **"重跑产出与库内 chunk 逐条一致"作为 Phase1 开工门槛，对存量数据不成立。** 库内 chunk 是**两套口径并存**：08-24 批为固定窗口（`CHAR_LENGTH` 恒 500），08-28 批为结构感知（每 chunk 一个小节）。用当前结构感知口径重跑，前者 0% 一致。
2. **结构感知口径本身已自洽。** 08-28 批（146/148/150）用当前 `StructureAwareSplitter(500,100)` 重跑 **逐条 100% 一致** —— 说明切片器对"结构清晰的小节式文档"是可复现的，B 方案（空父标题不生成空 Chunk）在该批数据上成立。
3. **Phase1 前的正确动作不是"对齐存量"，而是"定义迁移边界"**：存量固定窗口 chunk 与其向量不可回填改写（改写即需重算 embedding，且会让已引用它的 chat 历史/图谱边失配）。可行路径是**新增 Outline 导航层与 source_chunks 关联，不改旧 chunk**——这与 9/30 已定的 B 方案一致，本轮为其提供了数据侧证据。

## 阻塞
- [!] **集测组整组未跑**（环境性 NO-GO，非本轮改动引入）：本机 Redis 6379 未监听、无 Docker Desktop/Memurai。本轮唯一跑通的 integration 用例是 B114 探针（**刻意不启 Spring 上下文，故不依赖 Redis**），其余 `@Tag("integration")` 用例不可跑
- [!] **生产库存量口径未知**：本轮只覆盖本地库，未访问线上 `knowledge_chunk`（1131 条）。线上 74 个文件/1131 条 chunk 的口径分布未实测，需只读数据通道（PO 提供）才能把结论外推到生产

## DoD
- 探针可在本机复跑（命令 + 数字现场可复现）· 只读，不改库不改主链路 · 默认回归不受影响 · 收口报告区分「逻辑层已证 / 真实数据已验证」· 结论先行
- 集测组整组不可跑为环境性 NO-GO，已在收口报告显式标注，未用单测冒充端到端

## 约定（继承工作流铁律 + Sprint 3 反思会行动项）
- 任何代码改动必须全量回归全绿才可提交部署；小批次交付 → 核实 → commit
- 严禁服务器构建(OOM)；deploy.py 只读 `DEPLOY_HOST`/`DEPLOY_USER`/`DEPLOY_PASSWORD` 环境变量
- 节点/密钥/密码/管理端用户名名单不落文档不进输出
- 授权范围外的改动若因技术原因必须夹带，必须在本文件"夹带项"区显式留痕
- sprint.md 实时落盘（状态一变就更新）
- 收口必须区分"逻辑层已证"与"真实数据/HTTP 层已验证"，禁止用单测结果冒充端到端验证（Sprint 3 反思会行动项）
- 凭据类输入在阻塞区只登记"需要哪些键"，不登记值（Sprint 3 反思会行动项）

## 夹带项（显式留痕）
- 暂无

## 反思会（Sprint 4 · 2026-10-01 收口）
- Keep：探针**刻意不启 Spring 上下文**（裸 JDBC + OSS SDK），绕开了 Redis 缺失这一环境性 NO-GO，使本轮在"集测全废"的机况下仍产出真实数据证据——本轮唯一的端到端可信结论来自这个设计
- Keep：只读边界守住了——未改任何主链路代码，发现问题只记录（口径混杂）不改，避免在无真实语料全貌时误改切片器
- Improve（→ 写入约定）：**对账类任务必须先跑"口径普查"再谈"逐条一致"**。本轮 5 分钟就能发现 `CHAR_LENGTH` 恒等于 chunk-size，却是在建了对账探针之后才看到；顺序反了会浪费一轮探针开发
- Improve（→ 写入 backlog）：**"与库内一致"不是可验收表述**，必须写成"与库内 X 批（口径/时间窗）一致"，否则验收标准本身不可判定