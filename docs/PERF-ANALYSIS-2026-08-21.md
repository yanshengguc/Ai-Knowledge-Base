# AKB 性能优化分析报告(2026-08-21)

> 基于代码通读(上传/索引/检索/生成/缓存全链路)+ GitHub/官方文档验证
> 结论先行:**3 个高优优化点(全部本地可验证,不依赖服务器),1 个是 5-10 倍级收益**

---

## 一、结论摘要

| 级别 | 数量 | 一句话 |
|---|---|---|
| 🔴 P0 高优 | 3 | 向量化串行(最大热点)、异步用错线程池、检索链路零缓存 |
| 🟡 P1 中优 | 4 | HTTP 无连接池、SQL 日志全量打印、Knowledge 缓存代码质量、大文件同步读内存 |
| 🟢 P2 可选 | 5 | rerank 排序瑕疵、SSE 超时、Redis 序列化、Hikari 显式配置、向量检索无用户过滤 |

**P0-1 单点收益最大:文档向量化从"逐条串行"改"批量",100 chunks 预计 30-100s → 5-10s(官方验证快 5-10 倍)。**

---

## 二、性能现状全景

```
【写路径】上传文件
  POST /api/file ──> OSS 上传(同步)
                └──> CompletableFuture.runAsync(默认 commonPool ⚠️)
                        ├─ 解析 PDF/Word(同步)
                        ├─ 切片(同步)
                        ├─ Chunk 落库(MyBatis)
                        └─ 向量化索引:for 循环逐 chunk 🔴
                              ├─ embed() × N(每次 1 文本,DashScope HTTP)
                              └─ insert() × N(每次 1 条,DashVector HTTP)
  前端轮询文件状态 PROCESSING → SUCCESS/FAILED

【读路径】问答
  POST /api/chat/stream(SSE)
    ├─ 检索:embed(query) ─> DashVector 粗召回 topK*3 ─> 阈值过滤 ─> rerank 🔴 无缓存
    ├─ 历史(Redis)+ 长期记忆
    ├─ Prompt 拼接
    └─ LLM 流式生成(重试/降级 ✅)
```

**亮点(面试可讲,别动)**:
- ✅ 上传异步化 + 状态机轮询
- ✅ 缓存三大防护齐全:击穿锁(Lua 原子释放)+ 穿透 NULL 缓存 + 雪崩随机 TTL(`KnowledgeServiceImpl`)
- ✅ SSE 流式 + 每用户限流 10 次/分 + 输入长度校验
- ✅ rerank / 联网搜索失败降级,主链路不中断
- ✅ HttpRetryUtil 重试 3 次 + 流式断连部分保存

---

## 三、🔴 P0 高优(强烈建议 8/25-26 机动日做,全本地可验证)

### P0-1 向量化索引串行 —— 最大热点 ⭐

**位置**:`IndexingServiceImpl.java:45-71`(for 循环逐 chunk)+ `EmbeddingServiceImpl.java:17-22`(单文本 embed)+ `VectorStoreServiceImpl.java:45-73`(单条 insert)

**问题**:100 chunks = 100 次 DashScope embedding HTTP + 100 次 DashVector insert HTTP,全程串行。每次网络往返 200-500ms → 大文档分钟级。

**优化(官方验证可行)**:
```java
// ① Spring AI 批量向量化(官方:比循环快 5-10 倍)
List<float[]> vectors = embeddingModel.embed(chunks.stream().map(ChunkEntity::getContent).toList());

// ② DashVector 批量插入(官方支持,一次请求多条)
List<Doc> docs = ...; // 组装所有 Doc
collection.insert(InsertDocRequest.builder().docs(docs).build());

// ③ 分批:50 条/批,避免单次请求过大
// ④ 可选:insertAsync(ListenableFuture) 进一步异步化
```

**收益**:100 chunks 从 ~30-100s → ~5-10s。**这是面试能讲出"性能优化 5-10 倍"的硬弹药。**

**参考**:Spring AI EmbeddingModel.embed(List)(CSDN SpringAI 系列实测);DashVector Java SDK 官方文档 `InsertDocRequest.builder().docs(...)`(阿里云官方帮助文档)。

### P0-2 上传异步用错线程池(commonPool)

**位置**:`FileServiceImpl.java:169` — `CompletableFuture.runAsync(...)` 无 executor 参数

**问题**:默认走 `ForkJoinPool.commonPool()`(线程数 ≈ CPU 核数-1)。文档处理是 IO 密集(解析 + 多次网络调用),多个并发上传会互相抢线程 → 状态长期 PROCESSING,甚至饥饿。

**优化**:自定义线程池,显式传入:
```java
@Bean("docProcessExecutor")
public ThreadPoolTaskExecutor docProcessExecutor() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(2); e.setMaxPoolSize(4);
    e.setQueueCapacity(100);
    e.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 满时退回调用线程,不丢任务
    e.setThreadNamePrefix("doc-"); return e;
}
// CompletableFuture.runAsync(() -> {...}, docProcessExecutor)
```

**面试讲法**:IO 密集型任务用独立线程池 + 有界队列 + CallerRunsPolicy,避免污染公共池、避免丢任务。

### P0-3 检索链路零缓存(读多写少的典型场景)

**位置**:`RetrievalServiceImpl.java:36-54` + `VectorSearchServiceImpl.java:21-23`

**问题**:每次 query 都重新 embed(query)(DashScope 付费调用)+ 全链路检索 + rerank(付费)。相同/相似问题重复问 = 重复付费 + 重复等待。

**优化(两级缓存,Redis)**:
```java
// ① query 向量缓存:相同 query 的 embedding 复用(省 1 次 DashScope,TTL 1h)
// ② 检索结果缓存:key=归一化 query(去空格/小写),TTL 5-10min,value=List<SearchResult>
// ③ 可选:LLM 响应缓存(同问题同答案,成本敏感时开)
// ④ 失效:同名文件覆盖上传成功时,清理该知识库相关缓存 key
```

**面试讲法**:知识库查询 = 典型读多写少;缓存三大问题(穿透/击穿/雪崩)在这里的实际应用,配合已有的 Knowledge 缓存防护一起讲。

---

## 四、🟡 P1 中优(部署前顺手做)

| # | 位置 | 问题 | 优化 | 工作量 |
|---|---|---|---|---|
| P1-1 | `HttpClientConfig.java:17-23` | `ClientHttpRequestFactories.get()` 默认工厂**无连接池**,每次请求建 TCP 连接;且 `RerankServiceImpl:53` 另 new 一个 HttpClient,双实例 | 统一 Apache HttpClient5 连接池(maxTotal=200, maxPerRoute=50)+ RestClient/rerank 共用 | 2h |
| P1-2 | `application.properties:12` | `StdOutImpl` 每个 SQL 全量打 stdout,上传 100 chunks 刷屏+损耗 | 删除该行,改 logback 按级别控制(演示留 INFO 去 SQL) | 10min |
| P1-3 | `KnowledgeServiceImpl.java:80-181` | `System.out.println` 裸打印;拿锁失败 sleep(100ms) 自旋 3 次抛"系统繁忙";`"NULL".equals(obj)` 字符串魔术值与 VO 混存 | 清理 println;自旋上限收敛(改 1 次快速失败+客户端重试或 Redisson);NULL 标记用独立常量/枚举。**三大防护逻辑保留** | 2h |
| P1-4 | `FileServiceImpl.java:165` | 20MB 文件同步 `getBytes()` 全读内存,并发上传 OOM 风险 | 限制上传并发(信号量/线程池队列天然限流)或流式落临时文件;大文件走 OSS 分片直传(可选) | 2h |

---

## 五、🟢 P2 可选(记入面试弹药,不用急着做)

| # | 位置 | 问题 | 备注 |
|---|---|---|---|
| P2-1 | `RerankServiceImpl.java:119-123` | comparator 里 `candidates.indexOf(a)` → O(n²)(候选仅 15 条无感) | 预建 index→score 的 Map 即可 |
| P2-2 | `ChatController.java:88` | SseEmitter 120s 超时,长回答可能被截断 | 调大或加心跳 |
| P2-3 | `RedisConfig.java:34-37` | `activateDefaultTyping` 类型信息冗余 + 理论 RCE 风险(数据自控,风险低) | 数据源可控可接受;面试可讲"知道风险与取舍" |
| P2-4 | `application.properties` | HikariCP 未显式配置(默认 10) | 显式配 maximum-pool-size 等,体现意识 |
| P2-5 | `VectorStoreServiceImpl.java:76-108` | 检索无用户隔离 filter,collection 增长后全库扫描 | DashVector 支持 filter 参数;同时是**功能问题**(跨用户数据泄露风险,安全优先级更高,建议一并加 file_id 过滤) |

---

## 六、执行建议(与冲刺计划衔接)

```
8/21-24  主线不变(模拟面试/Docker/部署)——不动代码
8/25-26  机动日:做 P0-1 + P0-2(0.5 天)+ P0-3(0.5 天)
9 月周末  P1 按需 + P2-5 用户隔离(安全优先)
面试话术:批量向量化 5-10x + 检索缓存 + 缓存三大防护 = 完整的性能优化故事
```

> ⚠️ 注意:P0/P1 改动后跑 `mvn test`(66 测试)回归;P0-1 改动会动索引链路,确认 Evals 100% 仍通过。

---

## 七、参考来源

| 方案 | 来源 |
|---|---|
| Spring AI 批量 embedding(快 5-10 倍) | SpringAI 系列博客(CSDN)、Spring AI 官方 EmbeddingModel API |
| DashVector 批量 insert `InsertDocRequest.builder().docs(...)` | 阿里云官方文档 doc-detail/2510223、help.aliyun.com/2573586 |
| DashVector insertAsync(ListenableFuture) | 阿里云官方 Java SDK 文档 |
| 缓存三大问题防护 | 小林 coding 八股库 redis/02(本地已有) |
