# 项目证据包(8/20 建,8/29 增补;面试展示"我做过"的证据)

> 用法:面试被问"怎么证明你做的是真的" → 从这份文档拿数据。
> 所有数字**均来自真实实测**,不编造。每条可讲"怎么测的"。

## 一、运行记录(端到端实测)

| 时间 | 场景 | 结果 |
|---|---|---|
| 2026-08-18 | MVP 全流程 9 步:注册→建知识(分类)→编辑(PUT)→上传(PDF 异步 SUCCESS)→SSE 流式对话(7 token+refs)→清空→非法 id("参数格式不正确")→删除知识级联(三表全删)→actuator UP | **9/9 通过** |
| 2026-08-18 | 三项缺口:刷新后对话恢复/删除级联实测/文件删除 | 全通过(DB 残留 0) |
| 2026-08-19 | 联网搜索真实调用:问"2026秋招时间"→ 博查返回真实结果 → 模型正确区分"2026届"vs"2026年秋招",未找到时诚实说明 | ✅ 全链路 |
| 2026-08-20 | Agent Loop:模型自主选工具(问时间→time_now,问统计→knowledge_stats) | 2/2 中靶 |
| 2026-08-20 | MCP 协议全链路:initialize → tools/list(3 工具)→ tools/call(time_now 返回真实时间) | ✅ 标准协议 |

## 二、评测数据(可量化)

| 指标 | 数值 | 怎么测的 |
|---|---|---|
| 单元/集成测试 | **146 用例 BUILD SUCCESS**(8/29 全量,默认跑批;e2e 子集 test-e2e.sh 单独跑) | mvn test;e2e 子集 scripts/test-e2e.sh |
| 工具选择准确率 | **100%**(15/15,8/20 Eval Harness) | 15 个真实用例(该调/不该调),ListAppender 统计 |
| 检索质量 | **recall@5 / MRR 达标**(18 查询/12 篇文档,阈值 0.80/0.70) | RetrievalQualityEvalTest:真实管线注入(切片→Embedding→DashVector→混合检索+Rerank 全链路,非 mock) |
| 端到端串联 | **9/9**(8/18 MVP) | 注册→问答→删除全流程 curl 实测 |
| SSE 流式 | token 流完整 + 连接正常关闭(curl exit=0) | 实测 /api/chat/stream |
| 搜索降级 | 无 key 时降级纯知识库,对话不中断 | 实测 enableWebSearch=true |
| 长期记忆治理 | 去重/过期/容量/截断四策略(8/24,commit 4dbf4f3) | LongTermMemoryGovernanceTest 4 用例 |
| Token 成本可观测 | token_usage 按用户/模型记账(8/24 上线) | TokenCostCalculationTest 10 用例 |
| 每日配额边界 | **>= 即拒/SUM 聚合/豁免对照/开关关闭跳过**(8/28) | ChatDailyQuotaTest 5 用例 + DisabledTest 1 用例 |
| Agent 模式可视化 | **工具时间线端到端**(8/29,本地+生产双实测) | 浏览器实测:问"现在几点了"→time_now 时间线→正确回答;Agent 模式 LLM 调用按 userId 记账(成本面板 2.8k→4.5k 实时可见) |

> 注:工具选择 100% 是**测试集**结果(用例清晰);真实场景更复杂,已在缺陷清单标注为持续迭代项。

## 三、失败复盘(每个:现象→排查→修复→教训)

| # | 坑 | 复盘 |
|---|---|---|
| 1 | 密钥泄露事件 | 发现 key 误入库 → 立即轮换 → 仓库转私有 → **教训:key 只进 .env/local,推送前敏感检查** |
| 2 | 字段迁移坑(坑27) | DashVector 字段结构变更导致检索失败 → 全量重建索引 → **教训:向量库 schema 变更要迁移脚本** |
| 3 | 并行工具调用误判 | 模型把并行调用误判成多轮 → 改串行(序列依赖)→ **教训:并行需执行ID+日志+聚合校验** |
| 4 | SSE usage chunk 为 null | 流式末尾 chunk 空 → NPE 中断 → 判空过滤 → **教训:流式数据要防末尾脏数据** |
| 5 | emitter 未 complete | onDone 后没关连接 → curl 超时挂起 → 补 complete() → **教训:SSE 必须显式关闭** |
| 6 | MCP Bean 名冲突 | @Bean 方法名=@Service 类名 → 启动失败 → 改名 → **教训:Bean 命名注意冲突** |
| 7 | MCP Session ID 缺失 | Streamable HTTP 需 Mcp-Session-Id 头 → initialize 返回后携带 → **教训:协议细节要看规范** |
| 8 | 批量 replace 静默失败 | Python str.replace 不报错 → ChatController 历史接口没插上 → 404 才定位 → **教训:批量改代码必须 grep 验证每处** |

## 四、容错清单(被问"故障处理"逐条讲)

```
① Redis 挂了 → 会话记忆降级查 MySQL(对话不丢)
② 联网搜索失败 → 降级纯知识库回答(对话不中断)
③ 上传处理失败 → 状态机 FAILED + 前端提示重传
④ 死循环 → max_steps=5 强制终止(Agent Loop 第一重防护)
⑤ 工具执行失败 → 错误信息回传模型自愈(换参数/换工具)
⑥ 限流 → Redis INCR 每用户每分钟 10 次(防刷)
⑦ 越权 → 资源归属校验(用户 A 不能删用户 B 的)
⑧ 健康检查 → /actuator/health 免认证(探活)
```

## 五、安全证据

```
✅ JWT + BCrypt(密钥外置,gitignore)
✅ 越权修复(攻防实测通过)
✅ 登录限流 + 聊天限流 + 注册限流(8/24 新增)
✅ 路径白名单 + MCP 端点白名单
✅ 推送前敏感扫描(git grep 密钥)
✅ 仓库 PRIVATE
```

## 六、攻防实测(8/24 · 面试主推故事)

**工具**:`scripts/security_attack.py` —— 24 项检查的脚本化攻防套件(攻击者视角:注册受害者 A + 攻击者 B,B 拿自己的**合法 token** 打 A 的资源;输出逐条 [SAFE]/[VULN]+风险级+请求响应摘要,**退出码=漏洞数**,可进 CI 回归)。
**复跑方式**:起本地靶机 → `$env:ATTACK_BASE="http://127.0.0.1:56382/api"`(必须带 /api)→ `python scripts/security_attack.py`。**当前 0 漏洞**。

### 6 漏洞发现与修复全记录(commit f0c2c2a + 8a32b09,均已生产复测)

| # | 风险 | 漏洞(怎么发现的) | 攻击请求 → 响应 | 修法 |
|---|---|---|---|---|
| 1 | 高 | 文件详情 IDOR:B 的 token `GET /file/{A的id}` | → `200 {"fileName":"私有笔记","fileUrl":"OSS链接..."}` **任意人可拖库** | getFileById 补 verifyOwnership(knowledge.author==当前用户名,与 deleteFile 同口径) |
| 2 | 高 | 文件清单 IDOR:`GET /file/list/{A的knowledgeId}` | → 200 返回 A 的全部文件(配合#1 逐个拖) | listByKnowledgeId 同上校验 |
| 3 | 高 | 空密码可注册并登录 | `POST /register {"password":""}` → success;空密码登录 → **拿到 token** | register 补用户名/密码非空校验 |
| 4 | 中 | 标题存储型 XSS:`<script>alert(1)</script>` 入库原样回显 | 列表接口返回带标签原文(前端 v-html 有 DOMPurify,但存储侧裸奔) | 三处写入点(建知识/更新/笔记)入库前净化:script 块**连内容整块删**+剥其余标签(只剥标签会残留 alert(1) 文本——这是回归测试逮出来的真缺陷) |
| 5 | 中 | 注册无防刷:0.4 秒批量注册 5 账号 | 5 连发 /register → 5/5 成功 | 按 IP 限流 5 次/分(RateLimitService 扩展通用身份维度;生产经 Nginx 取 X-Forwarded-For 首段) |
| 6 | 低 | 用户信息可枚举:`GET /user/1` 拿任意用户名 | B 的 token → 200 `{"username":"yan"}` | /user/{id} 仅允许自查 |

**防御有效项(18 项抽查全过)**:JWT 四连(无 token/伪造/篡改 payload/篡改签名全 401)、知识读改删写笔记四向越权全拒、SQL 注入登录无效、`.exe` 白名单拒、2001 字超长拒、非法 JSON 不泄堆栈、actuator 仅 health、role/id 字段注入无法提权。
**附带加固**:file_trace 工具内部走 getFileById → 自动继承作者校验(工具层纵深)。
**生产复测**(部署后真实请求 120.55.76.141):B 列 A 清单→权限不足;B 读 A 文件→权限不足;`<script>alert(1)</script>T` 入库→净化为 `T`。✅

**讲法**:「攻击测试我做了两轮:第一轮手工 15 项,发现 3 个 bug;第二轮我把它脚本化成 24 项可回归的套件,用攻击者视角(合法 token 打别人资源)又发现 6 个漏洞——包括两个能拖库的文件 IDOR。全部修复后不只本地复测,还在生产环境上重放攻击确认拦截生效。」

## 七、变异测试(8/24 · 防"假测试")

**方法**:故意往代码里注入真实错误 → 跑对应测试 → **必须爆红**才算有效;改错了还绿 = 假测试,重写。
**结果 5/5 全红(测试全部有效)**:

| # | 注入的错误 | 模拟的现实事故 | 测试反应 |
|---|---|---|---|
| 1 | 计费换算基数 `1_000_000`→`100_000` | 成本账本虚报 10 倍 | TokenCostCalculationTest 5/10 例红 ✅ |
| 2 | 删除越权校验条件反转 | 任何人可删他人知识 | KnowledgeDeleteCascadeTest 2/3 例红 ✅ |
| 3 | 上传白名单放行 `.exe` | 恶意文件进 OSS | FileUploadBoundaryTest 2/10 例红 ✅ |
| 4 | 登录锁定阈值 5→5000 | 永不锁定,无限撞库 | LoginLockoutBoundaryTest 1/2 例红 ✅ |
| 5 | 限流 off-by-one(`>` 改 `>=`) | 用户第 10 次被误拒 | RateLimitBoundaryTest 2/2 例红 ✅ |

所有注入测试后已 `git checkout` 还原,工作区与提交版本一致。
**同日清理**:删除 9 个零断言"假测试"(纯 System.out 打印,每轮回归还烧真实 LLM 调用费用)——FileSearchChainVerifyTest / FunctionCallingReActVerifyTest / FunctionCallingServiceTest / EmbeddingTest(5 例) / VectorStoreTest / ToolExtensionVerifyTest 裁 2 例。

**讲法**:「87 到 137 个测试,数量不说明质量——我担心的是'接口 200 就算过'的假测试,所以做了变异测试:故意改错计费基数、反转越权判断,看测试会不会红。5 个错误全部被逮住,同时揪出 9 个零断言用例直接删掉。」

## 八、部署验收(8/24 · 生产上线证据)

**环境**:阿里云 ECS cn-hangzhou 2C2G(Ubuntu 22.04,年付)+ systemd 服务 `aikb`(java -Xmx512m)+ MySQL 8(buffer_pool=128M 低内存调优)+ Redis + Nginx(静态 dist + 反代 /api,**SSE 专用 proxy_buffering off**)+ 4GB swap。
**公开地址**:http://120.55.76.141(手机可访问,移动端 viewport 适配)
**部署流程**:`scripts/deploy.py`(paramiko SSH/SFTP,密码经本地临时文件不进代码)→ 备份旧 jar → 上传新 jar(115MB,中转名防半写)→ 原子替换 mv → `systemctl restart` → `scripts/verify_deploy.py` 8 项验收。**严禁在服务器上构建**(2C2G 会 OOM),本地构建传 artifact。
**安全收敛**:安全组仅开 80/443/22;deploy 后删除本地密码文件。

### 8 项自动化验收输出(8/24 两次部署均 8/8)

```
[PASS] 匿名访问拦截 (HTTP 401)
[PASS] 用户注册 (HTTP 200)
[PASS] 用户登录 (token ok)
[PASS] 知识条目创建+列表 (id=15, 共1条)
[PASS] 笔记保存 (HTTP 200)
[PASS] 知识导出 (HTTP 200, markdown=yes)
[PASS] 越权访问拦截 (HTTP 200, body=500)
[PASS] 知识详情查询 (HTTP 200)
=== 8/8 PASS ===
```

> 注:验收脚本检查 body 层 code 而非 HTTP 状态(BusinessException 返回 HTTP 200+code 500,只看 HTTP 会误判)。

**上线节奏证据**:8/22 部署调研 → 8/23 生产首次上线 → 8/24 两次迭代部署(记忆治理+安全加固),每次全量回归 137/137 绿 → 提交 → 构建 → 部署 → 8/8 验收 → 生产攻防复测,完整 CI 心智闭环。

## 九、8/29 双迭代增量(成本治理闭环 + Agent 可视化)

**上午(fd7a5f0)**:
- **成本治理三件套补齐**:ChatQuotaService 每日配额(按 token 计,复用记账聚合做事实源;豁免用户/上限/开关配置化),生产配 20000 tokens/日;6 个配额边界新用例,全量回归 146/146
- **性能三修**:httpclient5 连接池化(RestClient/Spring AI 非流式调用复用 TCP/TLS)、rerank 重排 O(n²)→下标排序(稳定性语义不变)、Hikari 显式收敛 8 连接(对齐 2C2G+buffer_pool 128M)
- 删除 Hello 死代码三件套(全仓库零引用);**本地库补建缺失的 token_usage 表**(recordChat 吞异常导致漏建一直未暴露——新配额测试直接逮住)

**下午(ffbd271)**:
- **Agent 模式可视化(面试主推新故事)**:诚实起点是"生产聊天链路此前没接 ReAct 循环"(只有 MCP/验证服务在用)→ 以显式开关接入:FunctionCallingService 带轨迹回调(步数/工具/参数/结果摘要),SSE 新增 tool 事件,前端渲染时间线;**Agent 的多次模型调用全部按 userId 记账**(否则绕过成本治理);前端另加回答复制按钮与流式光标

**部署与验证(两次)**:备份→传 jar→原子替换→重启→验收 3/3(注册关闭模式)→**真实浏览器全流程实测**(登录→知识列表→提问→流式回答→切片引用展开→成本面板实时跳动→Agent 时间线),生产复验"现在几点了"触发 time_now 时间线并正确回答;发现并如实记录:登录回车提交代码已存在(合成按键未触发属测试工具层问题)、工具结果摘要暂为原始 JSON(打磨项)

**Git 卫生**:每次推送前跑密钥扫描;8/29 推送 8+3 提交,扫描 3 命中逐一排除(测试常量早已公开/部署密码 0 出现/integrity 哈希误报)

**讲法**:「这两天的迭代我在练'把简历上的话变成产品里的事实':简历写手写 ReAct,我就把循环以开关形式接进聊天并让调用轨迹肉眼可见;简历写成本治理,我就把记账→限流→配额补成闭环,连 Agent 模式的多次调用都纳入记账——每加一个功能,先问它让哪句话从'说过'变成'可验证'。」
