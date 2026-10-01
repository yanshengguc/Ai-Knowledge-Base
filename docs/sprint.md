# Sprint 6 · 目标：B-114 Phase1 前端闭环——大纲导航面板 + 节点详情 + source_chunks 溯源页

> 开于 2026-10-01 | 承接 Sprint 5（后端闭环：代码已完成、验证通过、未提交未部署）
> 状态：前端闭环 实现→评审→验证 全流程通过（`npm run build` exit 0 / 2282 modules）；**端到端已打通**（WSL 常驻 → 本地 Redis 6379 通 → 集成整组 28 run/0 Failures/3 Errors，3 个为 DashVector 环境阻塞 → 本地后端 56382 真机 HTTP 验证 /api/outline 通过 → 前端点击联调 8/8 PASS → 回归守护 后端 200/200 · 前端 build exit 0）；未提交、未部署
> 模式：**拆分模式**——SM 由本会话担任且不写业务码；Dev / 评审 / 验证由**独立子 agent** 担任

## 角色分工
- PO：用户（拍板权归用户）
- SM：本会话（维护 `sprint.md` / `backlog.md`、跟阻塞、守流程；只改文档）
- Dev：子 agent（每轮迭代新起独立实例）
- 评审 / 验证：独立子 agent（禁止与 Dev 同一实例；只读，不写业务码）

## 迭代节奏（PO 要求：每次迭代都调多 agent，禁止自编自审自验）
1. **Dev-agent** 实现 → 交付三件套：改了什么 / 怎么跑 / 预期看到什么
2. **评审-agent**（只读）审范围、契约一致性、硬约束、夹带 → PASS / FAIL + 问题清单
3. **验证-agent** 跑门禁命令（`npm run build` 等）→ 只留真实结果，未跑项标「未验证」
4. SM 回写本文件，PO 验收
- 评审 FAIL 处理沿用协议：1 轮回 Dev、2 轮三岗会诊、3 轮挂牌 PO

## 并发写隔离（硬约束）
- 同一时刻只允许一个可写 agent；本轮只有前端一个写者，暂无 worktree 需求
- 若下一轮并行开「后端 B-116」第二写者，必须先用 git worktree 隔离分支，评审通过后再合并

## 本轮范围

**允许改**
- `frontend/src/**`（与大纲导航相关的 api、组件、路由与入口）

**不许碰**
- `src/main/java/**`、`src/test/**`（后端 Sprint 5 已完成，本轮不动）
- `docs/schema.sql`、任何 DDL
- 既有 REST 契约（Sprint 5 已冻结 `/api/outline/**` 字段）
- `DocumentSplitter.split()` 行为、检索链路、聊天链路
- 不引入新的大依赖（Element Plus / ECharts 已有）

## 任务
状态：[ ] 待开发 · [~] 开发中 · [R] 待评审 · [Q] 待验证 · [x] 完成 · [!] 阻塞

- [x] T-7 api 层：`frontend/src/api/outline.ts` 封装三端点；TS 类型与后端 4 个 VO 逐字段对齐 · 评审 PASS
- [x] T-8 组件：`OutlinePanel.vue` 标题树 + 节点详情 + sourceChunks 溯源；空态/失败态分离；竞态序号防覆盖 · 评审 PASS
- [x] T-9 接入：`FileListPanel.vue` md 行「查看大纲」入口（既有预览/删除语义不变）· 评审 PASS
- [x] T-10 验证：`npm run build` exit 0 · 2282 modules · 11.95s（独立验证-agent 复跑）· **真实接口联调已通过**（真机 HTTP 四端点 + 归属负向）· **前端点击联调 8/8 PASS**（浏览器验证-agent，见夜间工作区）
- [x] T-11 修复轮（评审低severity 2 项）：`OutlinePanel.vue` 详情 `data=null` 兜底空态 + 删除死 key `outline.chunkCount` · 复验 PASS

## 契约（后端已冻结，前端照抄字段名）
- `GET /api/outline/file/{fileId}` → 标题树（level / title / headingPath / 各节点 source_chunks 计数）
- `GET /api/outline/node/{nodeId}` → 节点详情 + 溯源项（preview 截断 200 字）
- 字段名以后端 VO 为准：`OutlineTreeVO` / `OutlineNodeVO` / `OutlineNodeDetailVO` / `OutlineSourceChunkVO`（Dev 必须先读这 4 个类再动手）
- 统一包在 `Result<T>` 里（`code` / `data` / `msg`）

## DoD
- `npm run build`（vue-tsc + Vite）通过
- 前端类型字段与后端 VO 逐一对齐（评审-agent 核对）
- 空态与失败态分离，无 mock 假数据
- 未跑项（真实接口联调：本地 Redis 6379 未监听、后端未起）显式标注「未验证」

## 阻塞
- [x] **端到端联调已闭环**（2026-10-01 夜）：WSL 常驻后本地 Redis 6379 打通；本地后端 56382 运行中；`/api/outline` 真机 HTTP 验证通过；前端点击联调 8/8 PASS。
- [!] **Sprint 5 增量未提交**：`/api/outline/**` 代码仍在工作区未 commit，Dev-agent 直接读工作区文件即可。
- [!] **上线时机 + commit 待 PO 拍板**：见决策单。

## 约定（继承 + 本轮新增）
- 任何改动全量回归全绿才可提交部署；小批次交付 → 核实 → commit
- 严禁服务器构建(OOM)；节点/密钥/密码/名单不落档不进输出
- **每次迭代必须调用多 agent（Dev / 评审 / 验证分离），禁止 SM 自编自审自验**（本轮 PO 明确要求，长期生效）
- 每完成一个任务或一次交付，当场同步 `sprint.md` + `HANDOFF.md`；backlog 有状态变化同批更新
- 收口必须区分「逻辑层已证」与「真实数据/HTTP 层已验证」，未跑项显式标注

## Sprint 5 收口（上一轮 · 已完成 · 未提交）
- B-114 Phase1 后端闭环：2 张新表 + `OutlineIndexService` + `/api/outline/**` 三端点 + `DocumentServiceImpl` 仅 md 追加落库
- 验证：专项 11/11 · 默认回归 200/200 · 真实 MySQL 持久化 1/1 · `split()` 零变化；新增 TD-001
- 状态：**待 PO 拍板 commit / 上线**

## 夜间自主工作区（2026-10-01 夜 · PO 授权）
- PO 授权：**多轮会诊能定的事自行决定，不再上交**；仅 commit/push、生产部署、DDL 上线、产品方向保留给 PO
- 三岗会诊结论：①Redis 连通性按"常驻 WSL → bind 0.0.0.0+WSL IP → 挂起转静态项"三档推进，每档 ≤1 次；②夜间队列=打通集成测试→真机 HTTP 验证 /api/outline→前端点击联调→回归守护→文档回填；③不纳入=commit/push、生产部署、B-113 管线合并（PO 已拍板暂不动码）、B-116 角色模型（需 PO 定权限模型）
- 逐项结果（滚动追加）：
  - **n2 Redis 打通（三岗会诊方案生效）**：根因＝WSL2 localhost 转发在无 WSL 进程驻留时失效（非 IPv6、非 bind 配置问题）。修复＝`wsl -d Ubuntu-24.04 -u root sleep 5400` 保持 WSL 常驻后 `127.0.0.1:6379` 转发恢复，JVM 连接正常。每档 ≤1 次尝试，第 1 档即打通，未触发第 2/3 档。
  - **n3 集成测试整组**：`mvn test "-Dgroups=integration" "-DexcludedGroups=e2e"` → **28 run / 0 Failures / 3 Errors**（打通前为 28 run / 6 Failures / 9 Errors）。剩余 3 个 Error 全部为 `IllegalState 向量库不可用，当前已降级为 BM25 单路` —— DashVector 本地不可达（IP 白名单/网络出口），**环境阻塞，非代码问题**；`ChatIntegrationTest` 由 MalformedJwt 转为 1/1 绿。
  - **n4 真机 HTTP 验证 `/api/outline`（验证-agent 独立执行）**：`GET /api/outline/file/258`（outline_test.md，知识 1408）→ HTTP 200 / body `code:200` / nodeCount=4（架构 L1 → 接入层 L2 → 网关 L3 → 服务层 L2，parentId 正确）；`GET /api/outline/node/{51,52,55}` → 200/200，溯源各 1 条（chunkId 515/516/519），**node55 previewLen=203（200 字 + "..."）截断生效**，contentLength=695（UTF-8 字节）；`POST /api/outline/file/258/rebuild` 连调 2 次均 data=4（**幂等**，节点 id 51-54 → 60-63）。自动落库生效（日志 `Outline 导航层生成完成,fileId=258,nodeCount=4,refCount=4`）。归属校验负向：user2 token 访问三端点均 **HTTP 200 + body `{"code":500,"message":"权限不足"}`**（符合铁律：看 body 不看看状态码）。OSS 可达、两次上传成功。
  - **本轮环境事实（复现配方）**：本地后端 PID=33644 / 端口 56382，`/actuator/health=UP`，日志 `%TEMP%\aikb-verify\out.log`；前端 vite 代理默认目标即 `http://localhost:56382`（`frontend/vite.config.ts:32`），直接 `npm run dev` 即可联调。
  - **n5 前端点击联调（浏览器验证-agent 独立执行）**：前端 dev server（5173，vite `/api` 代理默认指向 56382）注入 verifier1 token 后走真实页面路径：`/knowledge/1408`（知识「Outline验证知识」）文件列表 → `outline_test.md` 行「查看大纲」按钮 → OutlinePanel 打开。**8/8 全 PASS**：①页面加载正常；②md 行有「查看大纲」入口、非 md 文件（long_test.md）无此按钮；③点击打开面板；④标题树 4 节点层级正确（架构 L1 → 接入层 L2 → 网关 L3 → 服务层 L2，缩进体现父子嵌套）；⑤点「架构」右侧出现「层级 1 架构」+「原文切片溯源」卡，点「接入层」显示「层级 2 架构 / 接入层」；⑥点溯源卡「查看原文」→ FilePreview 抽屉渲染 `outline_test.md` 原文（架构/接入层/网关/服务层四章可见）；⑦全程未误触发空态/失败态提示；⑧控制台无 Vue warn / 404 / 500（仅 Vite HMR 的 `net::ERR_ABORTED` 资源中断，非功能错误）。
  - **n6 回归守护**：后端默认回归 `mvn test` → **200 run / 0 Failures / 0 Errors / 0 Skipped**（BUILD SUCCESS，39.6s，与 Sprint 5 基线一致，`split()` 零变化）；前端 `npm run build` → **exit 0 / 2282 modules / 14.92s**（仅 Sass/Rollup/大 chunk 非阻断警告）。
  - **n7 文档回填**：`docs/sprint.md`（状态行 + T-10 + 阻塞区 + 本工作区）、`HANDOFF.md`（B-114 状态行「未联调」→「已端到端联调」）、`docs/backlog.md`（B-114 Phase1 前端闭环条目）三处同步完成。
  - **小结**：Sprint 6 遗留的「真实接口联调未验证」+「页面点击路径未验证」两个缺口 → **全部关闭**（接口层走真机 HTTP，UI 层走浏览器真实点击，回归层全绿）。剩余唯一未跑项＝DashVector 相关集成断言（环境阻塞，已定性，不投入）。

## 决策单（待 PO 拍板）
1. **Sprint 5 增量是否 commit**：推荐 = commit（回归全绿 + 文档已同步，留可追溯增量、明确续接起点）；选项 B = 暂不提交，留在工作区。
2. **本轮是否上线生产**：推荐 = 不上线，与前端同批上线（上线需 `DEPLOY_HOST/USER/PASSWORD` + 线上 DDL 执行通道，DDL 不可逆）。

## 夜间收口（2026-10-01 夜 · PO 授权自主执行 · SM 汇总）
- **结论：done**（夜间队列 n1–n7 全部完成；仅 commit/push 与生产部署按 PO 授权保留）
- **需求**：B-114 Phase1 前端闭环从「仅构建验证」推进到「接口层 + UI 层 + 回归层」三层真机验证全通过
- **实现**：夜间未改任何业务码（只做验证 + 文档回填，符合 SM 边界）
- **验证**：n3 集成整组 `28 run/0 Failures/3 Errors`（3E=DashVector 环境阻塞）· n4 真机 HTTP `/api/outline` 四端点 200 + 归属负向 `code:500` · n5 浏览器点击 **8/8 PASS** · n6 后端 **200/200** + 前端 build exit 0
- **技术债**：无新增（TD-001 仍在册）
- **决策（保留给 PO）**：① Sprint 5 + Sprint 6 增量是否 commit（推荐 commit）② 是否上线生产（推荐与前端同批上线；需 `DEPLOY_HOST/USER/PASSWORD` + 线上 DDL 通道）
- **风险**：DashVector 本地不可达 → 向量索引降级 BM25（环境问题，非代码）；线上 DDL 未执行（未部署故无影响）
- **下一步**：PO 拍板 ①②；若要上线，先备回滚点再执行 `docs/schema.sql` 两表 DDL


## 本轮迭代留痕（多 agent · 2026-10-01）
- 迭代 1：Dev-agent 实现 T-7~T-9 → 评审-agent **PASS**（2 个低severity 观察项）→ 验证-agent **PASS**（build exit 0 / 2282 modules / 11.87s）
- 迭代 2：Dev-agent 修复（T-11）→ 验证-agent **PASS**（build exit 0 / 2282 modules / 11.95s）
- 缺口补齐（2026-10-01 夜）：上述「真实 HTTP 调用与页面点击路径未跑」已由夜间队列 n4（真机 HTTP 四端点）+ n5（浏览器点击 8/8 PASS）关闭；仅「`data=null` 兜底空态」仍为静态+构建确认（需人为构造异常态才能触发，本轮未构造）
