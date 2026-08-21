# AKB 测试策略报告(2026-08-21)

> 目标:锁定高风险、设计三类用例、一键回归、防假测试、攻防实测、避免过度测试。
> **诚实前提:本项目没有资金类功能(无支付/交易/余额),所以"高风险"= 权限 + 数据 两大类。** 下面所有"后果"都按这两类标注。

---

## 第 1 部分:高风险功能清单(按业务重要性)

### 🔴 P0 —— 必须重点测试(出错 = 权限/数据泄露或数据损坏)

| # | 功能 | 出错后果 | 影响类型 | 现状 |
|---|---|---|---|---|
| **1** | **JWT 认证与鉴权过滤器** | 伪造/篡改 token 可冒充任意用户 → 拿到全部数据;过期 token 仍放行 | **权限** | 有 JwtUtilTest(4 例,真断言)✅ |
| **2** | **知识/文件/切片的归属校验(越权)** | 用户 A 能读/改/删用户 B 的知识与文件 → 多用户上线即横向越权 | **权限+数据** | 有 KnowledgeAccessControlTest(7)+ UserSecurityTest(4)✅ |
| **3** | **上传 → 异步处理 → 状态机(含同名覆盖)** | 状态错乱、旧版被误删(数据丢)、新版本失败但旧版也没了 | **数据完整性** | 有 FileServiceImplTest(2)✅,缺边界用例 |
| **4** | **向量检索 + 用户隔离(MCP/工具)** | 检索返回他人内容;MCP 匿名可读全库(已修,需回归) | **数据** | 有 KnowledgeMcpSecurityTest(4)✅ |
| **5** | **级联删除(文件/知识)** | 删不干净(向量残留→搜到已删内容)或误删他人数据 | **数据** | ⚠️ 无专门用例(漏洞:删除不清理向量库) |
| **6** | **聊天生成链路(检索→重排→生成→历史/记忆)** | 答非所问(数据正确性)、历史串号(Redis key 错)、长期记忆泄漏 | **数据正确性** | 有链路 verify 类(真调用 LLM,成本高) |

### 🟡 P1 —— 应该测,次之

| # | 功能 | 出错后果 | 影响类型 |
|---|---|---|---|
| 7 | 限流(RateLimit) | 被刷爆 LLM 额度 | 资源 |
| 8 | 切片器(SimpleTextSplitter) | 切片错乱 → 检索质量下降(不致命) | 数据质量 |
| 9 | 检索阈值过滤 + 重排降级 | 相关结果被滤掉/排序错 | 数据正确性 |

### 🟢 P2 —— 可不测/低风险(出错影响小或可自愈)

```
知识 CRUD 基本增删改查(归属校验已覆盖越权,业务逻辑简单)
统计工具 / time_now / OSS 上传本身(第三方 SDK)/ Redis 缓存(可重建,有降级)/ HelloService(示例)
```

---

## 第 2 部分:三类测试用例设计(按 P0 功能)

> 原则:**验证"计算结果与数据变化是否正确",不是只看接口返回成功**。

### ① JWT 认证(4 用例)

| 类 | 用例 | 验证点 |
|---|---|---|
| 正常 | 生成→解析 roundtrip | id/username 必须一致,签名三段式 |
| 边界 | 超长 username(5000 字符)生成/解析;过期临界(exp=now-1s / now+1s) | 超长不崩;临界判定准确 |
| 异常 | 篡改签名 / 空 / null / 畸形字符串 | 全部拒绝,不进业务层 |

### ② 越权防护(每接口 3 用例)

| 类 | 用例 | 验证点 |
|---|---|---|
| 正常 | 用户 A 读自己知识/文件 → 200;**用户 B 读 A 的 id → 必须拒绝** | 归属校验生效 |
| 边界 | id=0 / 负数 / 不存在(如 999999) | 返回"不存在"而非越权/崩溃;`NULL` 缓存生效 |
| 异常 | 未登录(无 token)、token 过期、伪造 knowledgeId 的 fileId | 401 / 拒绝,不返回任何业务数据 |

### ③ 上传-状态机-同名覆盖(6 用例)

| 类 | 用例 | 验证点 |
|---|---|---|
| 正常 | 上传合法 pdf → 最终状态 SUCCESS;**同名再传 → 新 SUCCESS 且旧切片/记录/OSS 被清** | 数据最终一致(成功才清旧) |
| 正常 | 上传后检索能搜到新内容 | 数据真的入库(不是假成功) |
| 边界 | 0 字节文件 / 恰好 20MB / 超 1 字节 / 空文件名 / .pdf 大写 / .txt 伪造 | 限制准确:空拒绝、超限拒绝、类型白名单 |
| 边界 | 并发两个同名上传(竞态) | 不双写冲突、状态机不串 |
| 异常 | 处理失败(解析抛错)→ 新记录 FAILED **且旧版保留** | B2 一致性窗口关闭 |
| 异常 | 无权上传(他人 knowledgeId) | 拒绝 |

### ④ 检索 + 用户隔离(4 用例)

| 类 | 用例 | 验证点 |
|---|---|---|
| 正常 | 问"Redis 缓存击穿"→ 召回相关文档,score 排序正确 | 召回质量(已有 VectorSearchServiceTest 弱断言,需加强) |
| 边界 | 空 query / 超长 query(10 万字符)/ 无结果(搜不存在主题) | 不崩、返回空而非报错 |
| 边界 | topK=0 / 1 / 超大(1000) | 参数校验 + 截断正确 |
| 异常 | `searchForUser` 传 null userId / 用户无任何文件 | 抛参数异常 / 返回空(不扫全库) |

### ⑤ 级联删除(3 用例)—— ⚠️ 当前缺口

| 类 | 用例 | 验证点 |
|---|---|---|
| 正常 | 删文件 → chunk 记录删净 + 记录删 + OSS 删 | 三处一致 |
| 正常 | **删除后检索不再返回该文件内容** | 向量库必须同步清理(当前只删 MySQL,是已知漏洞) |
| 异常 | 删不存在 id / 删他人文件 | 不崩 / 拒绝 |

### ⑥ 聊天链路(3 用例)

| 类 | 用例 | 验证点 |
|---|---|---|
| 正常 | 多轮上下文:第二问能引用第一问 | 历史(Redis List)正确进出 |
| 边界 | 历史满 10 条截断后仍可用 | LTRIM 窗口正确 |
| 异常 | Redis 挂了 → 降级为无历史,不报错 | 降级路径真实可用 |

---

## 第 4 部分:现有测试有效性审查(防假测试)

> 判定标准:① 是否真调用被测功能 ② 是否断言**业务结果**(值/数据变化),还是只断言"没抛异常/非空"。

| 测试类 | 真调用? | 断言质量 | 判定 |
|---|---|---|---|
| SimpleTextSplitterTest(5) | ✅ | 断言块数/内容/重叠/覆盖全文 | ✅ **有效** |
| JwtUtilTest(4) | ✅ | 篡改/过期/畸形全拒绝 | ✅ **有效** |
| RetrievalServiceImplTest(4) | ✅ mock | 断言过滤后排序/缓存命中/失效 | ✅ **有效** |
| KnowledgeAccessControlTest(7) | ✅ | 越权读写删全拒绝 | ✅ **有效** |
| UserSecurityTest(4) | ✅ | 认证/越权 | ✅ **有效** |
| FileServiceImplTest(2) | ✅ mock | 断言"成功清旧/失败保旧" | ✅ **有效**(B2) |
| KnowledgeMcpSecurityTest(4) | ✅ 真实 | 匿名拒绝/用户隔离 | ✅ **有效**(A1) |
| IndexingReindexTest(2) | ✅ mock | 断言批量路径/空不跑 | ✅ **有效**(B1) |
| RateLimitServiceTest(3) | ✅ mock | 首请求过期/超限抛错 | ✅ **有效**(C3) |
| FileTraceServiceImplTest / FileSearchServiceImplTest(3+4) | ✅ mock | 断言业务返回 | ✅ 有效 |
| GenerationServiceImplTest(3) | ✅ mock | 断言重试/降级 | ✅ 有效 |
| **VectorStoreTest / EmbeddingTest(1+5)** | ✅ 真实 | ⚠️ **只打印 + 断言非空** —— 结果错了也"通过" | ❌ **假测试(弱断言)** |
| **VectorSearchServiceTest(1)** | ✅ 真实 | ⚠️ 只断言 `size<=topK` —— 召回是否相关不验证 | ❌ **假测试(弱断言)** |
| RerankSmokeTest / ManualReActVerifyTest / FileSearchChainVerifyTest / LongTermMemoryVerifyTest / FunctionCallingReActVerifyTest / ToolExtensionVerifyTest | ✅ 真实 LLM | 多为"链路能跑通"冒烟 | ⚠️ 半有效(真调用但断言浅 + **烧钱**) |
| AiKnowledgeBaseApplicationTests(1) | ✅ | context 加载成功 | ✅ 有用(便宜) |

**结论:核心业务(认证/越权/上传/检索/MCP/缓存/限流)的测试都是真的、断言业务值。弱的是 3 个"集成冒烟"(VectorStore/Embedding/VectorSearch),和第 2 部分 ⑤ 级联删除完全没有用例。** 下面通过"故意改错代码→测试爆红"来实测验证(见下节执行结果)。

---

## 第 6 部分:过度测试审查(精简清单)

### ❌ 建议删除/压缩(重复或低价值)

| 项 | 理由 | 处理 |
|---|---|---|
| EmbeddingTest 5 例 | `testEmbeddingModelDirect` 与 `testEmbeddingServiceNormal` 几乎重复(一个直接调 model,一个调 service),且全是打印+非空断言 | 压成 1 例(保留 service 正常 + 空/空格校验 2 个即可) |
| VectorStoreTest(1) | 打印型冒烟,断言=非空 | 保留但**加强**:插入后能搜到(否则删) |
| RerankSmokeTest | 真实 API 冒烟,断言浅,还烧钱 | 加 @Tag("e2e") 移出常规回归 |

### 🎯 建议标记 @Tag("e2e") 移出常规回归(省时省钱,单独命令跑)

以下都是**真实调用 LLM/外部 API**的验证型测试,每次常规回归都跑会烧 DeepSeek 额度且慢(全量 2 分多钟,大部分是它们):

```
EvalHarnessTest / ManualReActVerifyTest / LongTermMemoryVerifyTest
FileSearchChainVerifyTest / FunctionCallingReActVerifyTest / RerankSmokeTest / ToolExtensionVerifyTest
```

→ 常规 `mvn test` 只跑**单元+本地集成**(快、免费),`mvn test -Dgroups=e2e` 单独跑深度验证。

### ⚠️ 必须补的缺口(比精简更重要)

```
① 级联删除后向量库清理验证(当前删除不删向量 = 已知 bug,测试应先红)
② 上传边界(0 字节/20MB/类型白名单)
③ 检索召回相关性断言(加强 VectorSearchServiceTest)
```

---

## 附:如何一键回归 + 看结果

- 常规回归(推荐每次改动后跑):`bash scripts/test.sh` → 输出 `Tests run: N, Failures: 0, Errors: 0`
- 深度 E2E(周末/发布前):`bash scripts/test-e2e.sh`
- **看结果的标准:Failures=0 且 Errors=0 才算绿;任何非 0 都是红,必须修。**

---

## 附 2:前后端对应检查(2026-08-21 通读前端 12 文件)

### ✅ 全部对应(主体)

| 项 | 前端 | 后端 | 一致? |
|---|---|---|---|
| 知识列表 | GET /api/knowledge(List.vue 用 getKnowledgeList2) | KnowledgeController GET /knowledge | ✅ |
| 知识详情/增改删 | GET/POST/PUT/DELETE /api/knowledge/{id} | 同路径同方法 | ✅ |
| 文件上传 | POST /api/file/upload/{knowledgeId} FormData(file),pdf/docx+20MB 前端校验 | 同路径,后端同白名单+20MB | ✅ |
| 状态轮询 | GET /api/file/{id} 每 2s,SUCCESS/FAILED 停止 | 状态机 PROCESSING/SUCCESS/FAILED | ✅ |
| 文件列表/删除 | GET /api/file/list/{knowledgeId}、DELETE /api/file/{id} | 同路径 | ✅ |
| 聊天(流式) | fetch POST /api/chat/stream,body {message,enableWebSearch},解析 event:token/refs | SseEmitter 同名事件,DTO 字段一致 | ✅ |
| 历史/清空 | GET /api/chat/history、POST /api/chat/clear | 同路径 | ✅ |
| 鉴权 | 拦截器 `Authorization: Bearer <token>` | JwtAuthenticationFilter 同格式 | ✅ |
| 响应 | Result{code,message,data},code=200 成功,401 跳登录 | Result.success=200 / error=500,401 HTTP | ✅ |
| 代理 | vite /api → localhost:56382 | server.port=56382(local) | ✅ |

### ⚠️ 发现的问题(不是全部对应就无 bug)

| # | 问题 | 风险 | 说明 |
|---|---|---|---|
| **F1** | 前端 `getKnowledgeList()`(POST /knowledge {action:'list'})是**死代码地雷** | 🟡 中 | 后端 POST /knowledge 只认 KnowledgeAddDTO,`{action:'list'}` 会被当"新增知识"→ **插入一条空知识**!当前 List.vue 用 GET 版(安全),但任何人误调 POST 版就会写脏数据。应删掉或后端校验 action |
| **F2** | 前端 `send()`(非流式 POST /chat)与 `getChatHistory` 未使用 | 🟢 低 | 死代码,可删;且 request 超时 60s,非流式长回答易超时(幸好没用) |
| F3 | 前端无自动化测试(纯手工验证) | 🟡 中 | 本次只做了静态对应检查,未跑真实浏览器链路 |

### 与"测试全绿 ≠ 无 bug"相关的已知后端 bug(测试没覆盖到)

```
1. 级联删除不清理 DashVector → 删除后仍能检索到旧内容(数据残留,测试无用例,是真实 bug)
2. web 检索链路未按用户隔离 → 多用户上线时横向越权(MCP 口已修,web 未修)
3. SSE 120s 超时 → 超长回答可能被截断
4. KnowledgeServiceImpl 并发击穿时自旋 400ms 后抛"系统繁忙"(体验问题)
```

**结论:前后端接口层面对应良好;但"测试全绿"只证明已覆盖的逻辑正确,以上 4 个未覆盖项 + 前端 F1 地雷仍可能在真实使用中暴露。**

---

## 附 3:全流程实测 + 修复记录(2026-08-21 晚)

### 实测方法

启动真实后端(含 local 配置的 MySQL/Redis/DashScope/DashVector/OSS),用 curl 跑完整用户旅程:
注册→登录→建知识→上传真实 PDF→轮询 SUCCESS→SSE 流式问答→非流式问答→越权(用户B读/删A)→匿名→删除→清理。
**上传→解析→切片→批量向量化→检索→生成全链路 1 秒内 SUCCESS,SSE 流式正常。**

### 实测确认的 3 个 bug(已修复)

| # | Bug | 实测证据 | 修复 | 验证 |
|---|---|---|---|---|
| **A** | **web 检索未按用户隔离** | 用户 A 问答 references 混入其他文件的 fileId(16/17/12)——全库检索 | `RetrievalServiceImpl` 登录用户走 `searchForUser`(file_id 过滤) | 复验:references 只含自己文件 ✅ |
| **B** | **POST /knowledge {action:list} 返回 500 系统异常**(F1 地雷,语义错误) | 实测返回"系统异常,请稍后重试" | `addKnowledge` 校验标题/内容非空 → 明确"标题不能为空" | 复验:明确拒绝 ✅ |
| **C** | **级联删除不清理向量库**(删了还能搜到) | 代码审查:deleteFile/deleteKnowledge 无向量删除调用 | `VectorStoreService.deleteByFileId`(按 filter 查 id → 按 ids 删,因 DashVector delete 不支持 filter)+ 3 处调用(删文件/删知识/同名覆盖清旧) | 复验:删除后不再召回 ✅ |

### 修复过程踩到的坑(记录)

- **DashVector delete 不支持 filter**:官方文档参数表只有 ids/id/partition/deleteAll——按 filter 删是静默无效的。正确姿势:**先 filter 查询拿主键(chunkId),再按 ids 删**(零向量 + filter 查询只取主键,无需 embedding 依赖)。
- 删除生效后**立即查询仍有短暂可见**(最终一致),测试用 15s 轮询断言消失。
- 新增 4 个测试:`RetrievalServiceImplTest.shouldScopeSearchByUserWhenLoggedIn`、`KnowledgeAddValidationTest`(3 例)、`VectorStoreDeleteTest`(真实 DashVector,专用 fileId=9999991 防污染)、`FileServiceImplTest` 补向量清理断言。
- 全量常规回归:**75 个测试 0 失败**(70 + 新增 5)。
