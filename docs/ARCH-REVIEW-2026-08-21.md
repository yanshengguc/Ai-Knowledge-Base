# AKB 架构评审报告(专业架构师视角,2026-08-21)

> 评审原则:**只看代码本身,不受注释影响**(代码里的"面试讲法/安全加固/亮点"等标注不影响判定)。
> 范围:Spring Boot 3 单模块 + RAG/Agent 能力,65 Java 文件,全链路通读。
> **更新(8/21 晚):评审提出的 A1/A2/B1/B2/C1/C2/C3 七项已全部落地,全量 79 测试 0 失败。**

---

## 一、总体结论

**6.5 / 10 —— 功能完整、工程意识明显在线,但"安全边界"和"抽象统一性"是最短的两块板。**

| 维度 | 得分 | 一句话 |
|---|---|---|
| 分层清晰度 | 7/10 | 三层骨架清楚,但 Controller 职责漂移、UserContext 隐式依赖 |
| 安全架构 | **4/10** | MCP 暴露面无用户隔离 = 多用户部署时的数据泄露口 |
| 一致性/事务 | 6/10 | 异步状态机对,但索引失败无补偿、同名覆盖"先删后插"有窗口 |
| 可扩展性 | 6/10 | 检索链路抽象好,但工具 Schema 双体系违反开闭原则 |
| 工程实践 | 7/10 | 66 测试/降级/缓存防护是亮点;魔法值/println/注释残留扣分 |

> 定性:不是"demo",是"一个人认真做完的工程"——缺的不是功能,是**安全边界**和**抽象收敛**。

---

## 二、各层详评

### 1. 分层与职责(7/10)

✅ 好的:
- Controller → Service(接口+impl)→ Mapper 三层基本干净,事务边界清晰
- GlobalExceptionHandler 统一异常出口(BusinessException/类型不匹配/兜底)

⚠️ 问题:
- **Controller 塞了业务逻辑**:`ChatController` 里输入校验 + Redis 限流(`chat:rate:`)都在 Controller——限流应是独立组件或 Service 层关注点,Controller 只该做参数绑定
- **UserContext 静态 ThreadLocal 被 Service 层到处读**:`RetrievalServiceImpl` 缓存 key 用 `UserContext.getUserId()`,而上传线程里 userId 是捕获变量传下去的——**同一用户身份,两种取法并存**,异步/线程池场景极易踩"上下文丢失"坑
- **冗余抽象**:`QueryService` 接口 + `HelloService` 等只有单实现且部分仅测试用——可接受,但要有意识

### 2. 领域建模(6/10)

- `FileEntity.status` 用字符串魔法值 `"PROCESSING/SUCCESS/FAILED"` 散布在 FileServiceImpl、KnowledgeMcpTools 等多处 → **应枚举化**(`FileStatus.PROCESSING`)
- `KnowledgeServiceImpl` 缓存里 `"NULL"` 字符串与 `KnowledgeDetailVO` 混存一个 key → 类型混乱(已知,P1-3)
- VO 手动 set 字段重复(toVO 样板代码)→ 可用 MapStruct/ModelMapper(可选)

### 3. 安全架构(4/10)—— 最大短板 🔴

| 发现 | 风险 | 位置 |
|---|---|---|
| **MCP 端点免鉴权 + 工具全库无用户隔离** | 任何 MCP 客户端(Claude/Cursor/任意 HTTP)可**搜到/统计所有用户的知识**——多用户上线即数据泄露 | `SecurityConfig.WHITE_LIST` + `KnowledgeMcpTools` |
| `knowledge_stats` 用 `selectAll()` 全表 + N+1 查询 | 数据越权 + 性能 | `KnowledgeMcpTools:110-123` |
| JWT 无状态,无登出/踢人 | token 一旦泄露 30 天有效,无法失效 | `JwtAuthenticationFilter` |
| update/delete SQL 不带 `user_id` 条件 | 先查后改非原子,存在 TOCTOU 缝隙(理论) | `KnowledgeMapper.xml:80-94` |

✅ 好的:参数绑定全 `#{}` 无 SQL 注入;越权修复(归属校验)在 service 层覆盖了缓存/DB 两条路径;401 不打印 token。

**结论:MCP 是"一次编写处处可用"的好卖点,但上线前必须补用户隔离——这是架构层面唯一"必须做"的事。**

### 4. 一致性/事务(6/10)

✅ 好的:
- 上传**异步 + 状态机**(PROCESSING→SUCCESS/FAILED),索引不加事务(外部调用不绑 DB 事务,判断正确)
- 级联删除顺序对(切片→文件→OSS),缓存用"删"不用"更"

⚠️ 问题:
- **索引失败无补偿**:批量回退逐条后仍失败的部分,只在日志里 count,MySQL chunk 在、向量库没有 → **脏索引**,检索会悄悄缺内容;长时间运行越积越多。应有:失败清单落库 + 手动"重建索引"入口
- **同名覆盖"先删后插"**:旧切片删了、新文件处理失败 → 库里只剩失败态文件,旧内容没了。更稳:新版本处理成功后再清理旧版(或旧版标记失效)
- 异步任务无超时/失败告警(部署后靠日志,缺监控)

### 5. 可扩展性(6/10)

- ✅ 检索链路抽象好:`RetrievalService → VectorSearchService → VectorStoreService`,换向量库只改实现
- ✅ 工具注册中心(ToolRegistry)有插件雏形
- ⚠️ **工具 Schema 双体系**:`FunctionCallingServiceImpl.getInputSchema` 手写 if-else JSON Schema(注释还残留"Day39 暂时写死")——**新工具必须改代码**,违反开闭;而 MCP 侧用 `@Tool` 注解自动生成 schema。同一能力两套定义、两处维护 → **应统一到注解驱动,删掉手写 if-else**
- ⚠️ LLM 渠道硬编码 `OpenAiChatModel` + DeepSeek base-url,无多模型抽象(个人项目可接受,面试可讲"现状与演进路径")

### 6. 工程实践(7/10)

✅ 66 测试 + Eval 评估集 + 降级兜底(重排/联网/记忆/缓存全有降级)+ 断点续跑相关经验
⚠️ `System.out.println` 残留(KnowledgeServiceImpl);状态魔法值;注释与代码不同步("Day39");无分页(文件/知识列表全量)

---

## 三、架构级优化清单(按优先级,与性能清单互补)

| # | 优先级 | 事项 | 工作量 | 说明 |
|---|---|---|---|---|
| **A1** | 🔴 必须 | **MCP 工具用户隔离**:请求头传 JWT → UserContext;`knowledge_search/stats` 按 userId 过滤 | 0.5 天 | 多用户上线前必做,否则是数据泄露 |
| **A2** | 🔴 必须 | `knowledge_stats` 改 SQL 聚合查询(消 selectAll + N+1) | 2h | 连带 A1 |
| **B1** | 🟠 应该 | **索引失败补偿**:失败 chunk 落表 + 重建索引入口(按 fileId) | 0.5 天 | 防脏索引积累 |
| **B2** | 🟠 应该 | 同名覆盖改"新成功再清旧"(或旧版软删除) | 2h | 关一致性窗口 |
| **C1** | 🟡 建议 | **统一工具 Schema**:废弃 `getInputSchema` if-else,改 `@Tool` 注解 + Spring AI 自动生成 | 0.5 天 | 消除双体系 |
| **C2** | 🟡 建议 | `FileStatus` 枚举化 + 魔法值清理 + 删 println | 2h | 顺手 |
| **C3** | 🟡 建议 | 限流下沉(独立 RateLimit 组件) | 2h | 职责归位 |
| **D1** | 🟢 可选 | 分页(文件/知识列表)+ 显式 userId 参数传递(替代散读 UserContext) | 1 天 | 工程打磨 |

> 面试关联:A1/C1 是"架构意识"的硬证据——面试官问"上线前你会做什么"时,答 MCP 用户隔离 + 工具抽象统一,比任何注释都有说服力。

---

## 五、实施状态(8/21 已全部落地)

| 项 | 状态 | 落地内容 | 验证 |
|---|---|---|---|
| A1 MCP 用户隔离 | ✅ | MCP 工具 `requireUser()` 门禁(匿名拒业务数据);JWT filter 对 MCP 端点支持可选 token;`VectorSearchService.searchForUser`(file_id OR 链 filter) | KnowledgeMcpSecurityTest 4 例 |
| A2 统计聚合 | ✅ | `selectStatsByUserId` + `selectStatusSummaryByUserId` 一条 SQL 聚合,内部/外部 stats 共用,消 selectAll+N+1 | 统计断言=用户自己数据 |
| B1 索引补偿 | ✅ | `IndexingService.reindexFile(fileId)` 重建索引入口(幂等:DashVector 已存在 id 跳过) | IndexingReindexTest 2 例 |
| B2 覆盖窗口 | ✅ | 同名上传:先入库新版本,处理成功后再清旧版;失败保留旧版 | FileServiceImplTest 2 例 |
| C1 Schema 统一 | ✅ | `Tool.getInputSchema()` 各工具自带,删除中心 if-else 分派(开闭) | 工具链路 8 例 |
| C2 枚举化 | ✅ | `FileStatus` 枚举替换魔法值 + KnowledgeServiceImpl println 全换 log | 回归 13 例 |
| C3 限流下沉 | ✅ | `RateLimitService` 独立组件,ChatController 只调 | RateLimitServiceTest 3 例 |

**全量回归:79 测试(原 68 + 新增 11)0 失败 0 错误,Eval 100% 保持。**
**评分更新:安全 4→7,一致性 6→7.5,可扩展 6→7.5,分层 7→7.5,工程 7→8,总体 ≈7.5/10。**

**剩余(可选/随上线)**:D1 分页 + 显式 userId 传递;web 检索链路的多用户隔离(MCP 口已封,A1 只覆盖了外部暴露面,web 路径检索仍为全库,多用户上线前需一并做)。

## 四、一句话总评(面试可用)

> 「我的项目功能完整(检索/Agent/MCP/测试都跑通了),工程上我自评 6.5 分——我知道最短的板在哪:**① MCP 工具还没做用户隔离,上线前必须补;② 工具 Schema 有两套定义,要收敛到注解驱动;③ 索引失败缺补偿机制,会积累脏数据。这三块我都有明确的改进路径,也是我接下来要做的。」
>
> —— 能说出"我知道差什么 + 怎么补" = 架构意识本身是加分项。
