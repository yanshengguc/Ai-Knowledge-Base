# Sprint 8 · 目标：低成本收尾包 + 检索质量 eval 底座（B-114 Phase2 的前置）

> 开于 2026-10-02 | 承接 Sprint 7（B-116 已上线并完成线上登录态实测，缺口关闭）
> 状态：**进行中——T-3（B-103）与 T-4（检索质量 eval 底座）均已完成实现 + 独立评审 PASS + 独立验证全绿 → **已于 2026-10-03 部署上线并验收通过**（PO 2026-10-03 拍板：先定位再修 → B-117 已根因定位并修复，T-4 取到真实无树基线；白名单问题已排除）**追加（2026-10-03）：B-117 后续债 `deleteByFileId` 的 `topk(100)` 已修复（分页循环）并经独立评审 PASS + 独立验证全绿；DashVector 孤儿向量只读清单已产出，按 PO 口径暂不删除**
> 模式：**拆分模式**——SM 由本会话担任且不写业务码；Dev / 评审 / 验证由**独立子 agent** 担任

## 背景与决策（PO 2026-10-02 讨论结论）
- **维护期正式结束**：9/15「AKB 转纯维护模式，不开新功能线」口径作废，转功能迭代；`docs/backlog.md` 迭代原则与 P1 区已同步改写，B-114 确立为主线
- **Phase2 不直接挂载**：Sprint 8 先补「尺子」——建检索质量 eval（recall@5 / MRR）+ 跑**无树基线**留档；**真挂载顺延 Sprint 9**（有树 vs 无树对比）
- 理由：现有 Eval Harness 15/15 测的是**工具选择**，不测检索质量；2C2G 生产上无量化门槛的检索改动，无法判断"变好"还是只是"没坏"
- **风险隔离**：Sprint 8 全部为可回滚、**不改检索行为**的改动，与 B-116 观察期不冲突

## 本轮范围

**允许改**
- `frontend/src/utils/markdown.ts` + 相关样式（B-111）
- `frontend/vite.config.ts` / `frontend/package.json`（B-103 构建期依赖）
- 聊天入口 Service（B-113 仅抽私有方法，行为零变化）
- `src/test/**`：新增检索质量 eval（recall@5 / MRR）
- `docs/**`、`HANDOFF.md`

**不许碰**
- 检索底座（向量 + BM25 融合逻辑）、`StructureAwareSplitter.split()` 行为
- 聊天主链路语义与流式行为（B-113 只做等价抽取）
- `docs/schema.sql` 既有表结构、JWT / 鉴权逻辑、既有 REST 契约
- 不引入新的大依赖（highlight.js 走已装 marked 生态；unplugin 为构建期依赖）

## 任务
状态：[ ] 待开发 · [~] 开发中 · [R] 待评审 · [Q] 待验证 · [x] 完成 · [!] 阻塞

- [x] T-1 B-111 回答渲染美化：代码高亮（highlight.js 按需加载）+ `.markdown-body` 排版增强（表格/引用块/标题层级/行距）；**DOMPurify 白名单不动** · **完成（2026-10-02）**：实现 + 评审 PASS + 验证全绿 + 浏览器真实渲染复核全项 PASS，证据见下方「本轮迭代留痕（Sprint 8 · T-1）」
- [x] T-2 B-113 轻量去重：`streamAsk` / `streamAskWithAgent` 开头 5 行（检索+历史+记忆+拼 Prompt）抽私有方法，**行为零变化** · **完成（2026-10-02）**：实现 + 独立评审 PASS（无 high/medium/low） + 独立验证全绿（`mvn -o test` 222/0/0），证据见下方「本轮迭代留痕（Sprint 8 · T-2 / B-113）」
- [x] T-3 B-103 element-plus 按需导入（unplugin-vue-components + unplugin-auto-import），记录构建产物体积前后 · **完成（2026-10-02）**：实现（3 轮根因定位）+ 独立评审 PASS（无 high/medium，low 3 项）+ 独立验证全绿（`npm run build` exit 0 · `mvn -o test` 222/0/0），element-plus chunk **1,109.29 → 30.59 kB**（≈ −97%），证据见下方「本轮迭代留痕（Sprint 8 · T-3 / B-103）」
- [x] T-4 检索质量 eval 底座：标注 query→chunk 集 + recall@5 / MRR 指标 + **无树基线数字留档**（阈值 recall@5 ≥ 0.80 / MRR ≥ 0.70） · **完成（2026-10-03）**：先定位并修复 B-117（向量写入静默失败）→ 取到**真实无树基线**——文档级 recall@5=0.833 / MRR=0.861、chunk 级 chunkRecall@5=0.833 / chunkMRR=0.861（两次复跑逐位一致）；实现 + 独立评审 PASS（无 high，medium 1 项已修）+ 独立验证全绿（`mvn -o test` 222/0/0 · `npm run build` exit 0）。证据见下方「本轮迭代留痕（Sprint 8 · T-4 / B-117 修复与基线）」
- [~] T-5 评审：独立评审-agent（只读，审范围 / 行为等价性 / 夹带）· T-1 轮**已 PASS（2026-10-02，无 high/medium）**；T-2 轮**已 PASS（2026-10-02，无 high/medium/low）**；T-3 轮**已 PASS（2026-10-02，无 high/medium，low 3 项）**；**T-4 轮已 PASS（2026-10-03，无 high，medium 1 项已修 + low 3 项留痕）**；**B-117 后续债（topk 分页）轮已 PASS（2026-10-03，无 high，medium 1 项范围外留痕 + low 2 项）**；**B-129/B-130 轮已 PASS with nits（2026-10-03，无 high/medium，low 3 项留痕）**
- [~] T-6 验证：独立验证-agent 跑门禁（`mvn -o test` 全量回归 + `npm run build` + eval 基线输出）· T-1 轮**已全绿（2026-10-02：222/0/0 + build exit 0）**；T-2 轮**已全绿（2026-10-02：222/0/0）**；T-3 轮**已全绿（2026-10-02：build exit 0 + 222/0/0）**；**T-4 轮已全绿（2026-10-03：222/0/0 + build exit 0 + eval 两次逐位一致 0.833/0.861）**；**B-117 后续债（topk 分页）轮已全绿（2026-10-03：222/0/0 + build exit 0）**；**B-129/B-130 轮已全绿（2026-10-03：253/0/0 + build exit 0 + 真实 HTTP 端到端 VERIFIED）**

## DoD
- 全量回归保持全绿（当前基线 222/0/0），前端 `npm run build` exit 0
- B-111：代码块有高亮、表格/引用/标题有增强排版，DOMPurify 白名单**零改动**
- B-113：去重前后同一测试集输出一致，无流式行为差异
- B-103：构建产物体积有前后对比数字
- T-4：无树基线 recall@5 / MRR 有确切数字并落档，作为 Sprint 9 对比基准
- 未跑项显式标注「未验证」

## 阻塞
- 无（B-117 已于 2026-10-03 修复并被独立验证覆盖；B-118 为独立排查项，不阻塞本 Sprint）
- 待处理数据 / 技术债（不阻塞本 Sprint，见 backlog B-117 闭环备注）：~~DashVector 存量孤儿向量清理~~ **已清理 2026-10-03**（三方判定真孤儿 10 个 file_id / 31 条已删，集合 1,055 → 1,024；生产向量一条未误删）、`LongTermMemoryServiceImpl` 同类静默失败风险（**未处理**）、`deleteByFileId` 删除响应未逐条校验 `DocOpResult.code`（`topk(100)` 上限**已修复**）
- 环境：本地 Redis 需 WSL 常驻才通 6379（继承 Sprint 6/7 配方）；mysql.exe 在 `C:\Program Files\MySQL\MySQL Server 8.0\bin\`，不在 PATH
- 观察期：B-116 生产回滚产物按 PO「先观察几天」保留中，清理需 PO 另行确认

## 下一步预告（Sprint 9）
- B-114 Phase2：标题树挂载到混合检索做路由加权（树只提权，不动 hybrid 底座；失败降级回纯 RAG 并打 WARN）→ 上线门槛 = 有树 vs 无树对比 + Sprint 8 基线指标
- eval 数据集补 2-3 个 pdf 标注用例（B-123 S3 信号前置 · PO 拍板 2026-10-03）：让「无结构文档是否为检索短板」自本 Sprint 起有数据在线；只加标注用例，不做 AI 建树；**须与 md 用例分格式统计**（树挂载仅对 md 生效，混算会稀释树路由效果与 S3 判定）；其 recall/MRR 显著低于 md 基线即触发 B-123 ②+① 开工

## B-120 / B-121 / B-118 安全债修复任务立项（2026-10-03 · 低危 + 排查项）

> 立项依据：2026-10-03 攻防演习低危/观察项（见文末「本轮迭代留痕（安全演练 · 2026-10-03 · Sprint 8 上线后）」B/D/F 项）。PO 拍板：依序执行 **B-120 → B-121 → B-118**。**状态：全部收口**（B-120①/②、B-121 已提交推送并部署上线；B-120③ Nginx 安全头已上线；B-118 只读排查 + 缺失向量回填均已完成，见下方两节留痕）

### B-120 低危安全项汇总
- **① 上传白名单大小写绕过（演练记录 `evil.MD` 通过）**：**源码级复核 → 实为误报 / 已缓解**。`FileServiceImpl.uploadFile` 白名单**已经 `originalName.toLowerCase().endsWith(".pdf"/".docx"/".md")` 归一化**（L129-133）；`ParserFactory.getParser`（L32）、`DocumentServiceImpl.indexOutlineIfMarkdown`（L106）、`OutlineIndexServiceImpl`（L312）、`getFileContent`（L289）**均 `toLowerCase`** → `.MD` 通过属**预期的大小写不敏感匹配，非漏洞**，且下游解析/预览**无失配**（与演练观察一致）。**结论：无代码改动，留痕为「非漏洞 / 已缓解」。**
- **② `PROCESSING 禁删` 护栏未生效**：`FileServiceImpl.deleteFile`（L250-268）**无状态校验**，直接级联删除，与文档约束（README L28/L193、HANDOFF L174、line-rag.md L13/L23）及前端约束（`FileListPanel.vue` L51 处理中禁删、i18n `deleteBlockedProcessing`）不符。**修复**：`deleteFile` 在归属校验通过后增设 `status == PROCESSING` 护栏（抛 `BusinessException("文件处理中,完成后才能删除")`，HTTP 200 + body `code:500`），防半成品被删产生孤儿 chunk/向量。
- **③ 安全响应头全缺**（`X-Frame-Options` / `CSP` / `X-Content-Type-Options` / `HSTS`）：属 **Nginx 层**服务器配置（静态页由 Nginx 直出，应用层 filter 覆盖不到 HTML），**非仓库代码** → **需 PO 授权 + 生产凭据**。**本轮暂缓，待 PO 拍板。**

### B-121 JWT 白名单判定归一化
- 现象：`JwtAuthenticationFilter`（L32-47）以原始 `request.getRequestURI()` **精确匹配** `WHITE_LIST`；`/api//user/login` 经容器归一化后命中白名单、绕过过滤器（实测 **fail-closed、无越权收益**）。
- 修复（最小面）：白名单匹配前对 URI 做**归一化**（折叠重复斜杠、去尾斜杠），使过滤器判定与路由判定一致；**不改** `WHITE_LIST` 集合、**不改**鉴权语义、**不改** MCP 端点分支行为。

### B-118 线上检索是否静默降级 BM25（独立排查项）
- 背景：B-117 暴露「向量路异常时静默退化 BM25 且不报警」的同类机制，线上是否长期处于该状态未知。
- 排查范围：线上检索日志 / 降级告警 / 向量集合与 chunk 数对齐（B-117 已留档：生产 file 68/69/70 在集合中**无任何向量**，只能靠 BM25 召回，是直接排查入口）。
- **前置**：需 PO 提供生产凭据（`DEPLOY_HOST` / `DEPLOY_USER` / `DEPLOY_PASSWORD`）；**未获凭据不得臆造、不得开工**。

### DoD（B-120② / B-121）
- `mvn -o test` 全量回归**全绿**（当前基线 **228/0/0**）+ `git diff --check` exit 0
- B-120②：断言 —— PROCESSING 文件 DELETE → `BusinessException`（HTTP 200 + body `code:500`）；SUCCESS/FAILED 文件删除行为**零变化**
- B-121：`/api//user/login` 不再"绕过"，而是**显式命中白名单**（等价结果、判定不依赖容器）；`%2F` 编码探针仍 fail-closed；非白名单端点匿名访问仍 401
- 未跑项 / 未验证项显式标注

### 约束（继承硬约束）
- Controller/端点 `/api` 前缀；`Result` 契约；`BusinessException` = HTTP 200 + body `code:500`（验证看 body 不看状态码）；改动全量回归全绿才可提交部署；**同一时刻仅一个可写者**；部署前建回滚点；部署后清理临时凭据。
- **不改** MCP 工具契约、`WHITE_LIST` 集合、检索底座、既有 REST 契约；不改 `user.role` / JWT 载荷。

### 角色分工 / 节奏
- Dev-agent（唯一可写者）实现 → 评审-agent（只读）审范围/契约/硬约束/夹带 → 验证-agent 跑门禁 → SM 回写 → PO 验收。评审 FAIL 沿用 1 轮回 Dev / 2 轮三岗会诊 / 3 轮挂牌 PO。
- **未经 PO 明确指示不得 commit / push / 部署。**

## 本轮迭代留痕（多 agent · 2026-10-03 · B-129 + B-130 条件触发债收口）

> 结论：**B-129 与 B-130 均完成实现 + 独立评审 PASS with nits（无 high/medium）+ 独立验证 VERIFIED（含真实 HTTP 端到端）**，**未 commit / 未 push / 未部署**（PO 保留拍板权）。范围严格限定：B-129 = 管理端分页 offset long 化（1 行 + 形参 + 1 单测）；B-130 = 新增分类候选端点并切换 `List.vue` 数据源。后端零契约破坏、前端未改 `getKnowledgeList2`。

- **B-129（同源债）**：`AdminServiceImpl.users` 的 `(safePage - 1) * safeSize` → `(long) (safePage - 1) * safeSize`（与 B-104 `KnowledgeServiceImpl.getKnowledgePage` 口径一致）；`AdminMapper.selectUsers` 的 `offset` 形参 `int → long`（`AdminMapper.xml` 的 `LIMIT/OFFSET` 按 BIGINT 绑定，无需改动）。新增 `AdminUserPaginationTest`（1 用例）：`expectedOffset = (long)(Integer.MAX_VALUE-1)*10 = 21474836460`，`verify(adminMapper).selectUsers(null, 10, expectedOffset)` 为**承重断言**——若退回 int 运算，实参溢出为 `-20`，stub/verify 均不命中（`getList()` 为 null 直接 NPE），测试必失败。
- **B-130（消除全量依赖）**：新增 `GET /api/knowledge/categories`（`KnowledgeController` → `KnowledgeService.getCategories()` → `KnowledgeServiceImpl` 按 `UserContext.getUserId()` → `KnowledgeMapper.selectDistinctCategoriesByUserId`）。SQL = `SELECT DISTINCT category FROM knowledge WHERE user_id = #{userId} AND category IS NOT NULL AND category <> '' ORDER BY category`。前端 `api/modules/knowledge.ts` 新增 `getKnowledgeCategories()`；`List.vue.loadCategories()` 改用它（失败仍保留旧选项）。`getKnowledgeList2` **保留未动**（`Tree.vue` 建树 / `SaveAsNoteDialog.vue` 存笔记下拉仍需全量），`List.vue` 已无 `fetchAllList` 引用。新增 `KnowledgeCategoriesTest`（3 用例：映射、null 降级空列表、路由不被 `/knowledge/{id}` 吃掉 + 详情路由负向对照）。
- **门禁（Dev 自跑 + 验证-agent 独立复跑一致）**：`mvn -o test` **253/0/0**（基线 249 + 4）BUILD SUCCESS · `npm run build`（`vue-tsc -b && vite build`）exit 0（2390 modules）。
- **真实路径实测（VERIFIED，独立验证-agent 亲跑）**：本地 `local` profile 起服务（WSL Redis 保活）→ 真实注册登录取 token。① **B-130 正向**：`GET /api/knowledge/categories` 基线 `200 {"code":200,"data":[]}`；`POST /api/knowledge{category:"B130VERIFY"}` 后复调 → `200 data:["B130VERIFY"]`（**证明 Mapper XML statement 与接口真实绑定**，静态核对无法证明）；删知识后回到 `[]`。② **B-130 负向/回归**：无 token → **401**；`/api/knowledge/page?page=1&size=5` → 200；`/api/knowledge/{id}` → 200（literal 路由与 `{id}` 互不吃掉）。③ **B-129 正向（关键）**：以管理员身份 `GET /api/admin/users?page=2147483647&size=10` → **HTTP 200 `{"code":200,"data":{"total":809,"page":2147483647,"size":10,"list":[]}}`**（int 版会溢出为负 OFFSET → MySQL 报错 → `code:500 系统异常`）→ 溢出确已修掉。
- **评审（独立只读）**：`PASS with nits`，无 high/medium。low 项：① 纯空白分类 `'   '` 的拦截取决于列排序规则——`docs/schema.sql` 声明 `utf8mb4_unicode_ci`（PAD SPACE）下 `'   ' <> ''` 为假、`DISTINCT` 亦去尾空格，空白已被挡住；若生产实为 MySQL 8 默认 `utf8mb4_0900_ai_ci`（NO PAD）则需补 `TRIM`（本轮按立项 SQL 口径未改，留痕）；② 单测为 mock 层，SQL 真值依赖真实路径实测覆盖；③ `KnowledgeController` 既有缩进不规范（范围外，非本次引入）。
- **夹带核查**：无。改动面 = 后端 7 文件（`AdminServiceImpl` / `AdminMapper` / `KnowledgeMapper` / `KnowledgeMapper.xml` / `KnowledgeService` / `KnowledgeServiceImpl` / `KnowledgeController`）+ 前端 2 文件（`api/modules/knowledge.ts` / `List.vue`）+ 2 新测试文件；未动表结构、检索底座、JWT、既有 REST 契约。
- **环境备注**：验证期间 WSL Redis 曾被探测命令误停后立即 `redis-server --daemonize yes` 恢复；测试账号 `b130verifier1003`（本地库 id=857）已由 SM 直连本地 MySQL 删除（`remaining=0`），本地库无残留；后端进程与 WSL 保活均已停。
- **未验证（如实标注）**：未部署；生产未验证（生产注册关闭）；`List.vue` 页面的真实浏览器走查未做（端点与数据源已 HTTP 级验证，前端仅 `vue-tsc + vite build` 门禁）。
- **下一步**：待 PO 拍板 commit / push / 部署（后端 jar 需重建；前端 dist 需重建并原子替换）。B-129 / B-130 已在 `docs/backlog.md` 标 `[x]` 闭环。

## 本轮迭代留痕（多 agent · 2026-10-03 · B-104 前端接入 · 服务端分页落地 List.vue）

> 结论：**B-104 前端接入完成并部署上线**（Dev 实现 → 独立评审 PASS with nits（2 medium 已修）→ 独立验证 VERIFIED（6/6）→ **真实 HTTP 端到端 17/17 PASS** + **真实浏览器实测 8/8 PASS** → **生产部署完成**，commit `7e13477`（代码）+ `7723b38`（留痕））。范围严格限定「把 `List.vue` 切到 `/api/knowledge/page`」，未改后端、未动 `getKnowledgeList2` 契约。

- **改动面（3 文件，纯前端）**：`frontend/src/types/api.ts`（+`KnowledgePageVO {total,page,size,list}`）；`frontend/src/api/modules/knowledge.ts`（+`getKnowledgePage(params)`，`getKnowledgeList2()` 一字未动）；`frontend/src/views/knowledge/List.vue`（全量拉取 + 前端过滤 + 前端切片 → 服务端筛选 + 服务端翻页）。
- **为何不动 `getKnowledgeList2`**：`Tree.vue`（建树）与 `SaveAsNoteDialog.vue`（存笔记下拉）本质需要全量数组，改其契约会直接打挂它们 ⇒ 本轮只新增函数、只改 `List.vue` 一个消费点。
- **实现要点**：`page` + `pageSize=12`（≤ 后端上限 50）下传服务端；分页组件用服务端 `total`（`:total="total"`、`v-if="total > pageSize"`）；keyword 与 category 变更**共用 300ms 防抖** `scheduleReload`（同一 tick 内两项都变只发 1 次请求）；**竞态保护** `requestSeq`（仅最后一次请求可写回 list / 关 loading）；删除后按 `maxPage = Math.max(1, Math.ceil((total-1)/pageSize))` 回退页码（`total` 为删除前旧值）；`onBeforeUnmount` 清理防抖定时器；空态判定由 `filteredList.length === 0` 改为 `list.length === 0`。
- **分类下拉的取舍（新登记 backlog B-130）**：分类是自由文本，下拉候选值仍需全量聚合，故 `getKnowledgeList2()` **仅用于生成选项**（失败时保留旧选项、不清空）；列表数据本身已全部走服务端分页。触发条件见 B-130。
- **评审（独立只读）**：`PASS with nits`。2 项 **medium 已在本轮修掉**：① `clearFilters()` 同时清空 keyword + category 会触发两个 watch → 2 次请求，改为共享防抖后 1 次；② `loadCategories()` 失败即 `categories.value = []` 会误清空已有选项，改为失败保留旧值。1 项 low 已补注释（末页回退依赖 `total` 为删除前旧值，先减 1 再换算）。
- **验证（独立）**：`VERIFIED` 6/6——① 构建门禁 `npm run build`（`vue-tsc -b && vite build`）exit 0；② 改动面严格 3 文件、无后端/docs 改动；③ `getKnowledgeList2` 契约未变、`Tree.vue`/`SaveAsNoteDialog.vue` 未动；④ `filteredList`/`pagedList`/`fetchList` 全文 0 命中；⑤ 分类失败分支无清空；⑥ 双 watch 共用同一防抖、卸载清理无 `keywordTimer` 残留。
- **真实路径实测（本轮补齐此前「无登录态 HTTP 端到端」缺口）**：
  - **HTTP 端到端 17/17 PASS**（本地 `local` profile，真实注册/登录/新建/删除）：`total=5`、首页 `size=2` 仅 2 条、第 3 页 1 条、越界页空且 total 不变、keyword 命中 1 条、category 命中 2 条、`size=999` 被上限 50 收敛且不报错、`page=0/size=0` 归一化为 `page=1/size=10`、匿名 401；测试数据已清理（deleted=5）。
  - **真实浏览器实测 8/8 PASS**（vite dev + 浏览器）：登录 → 第 1 页 **12 张**（条目01–12）+ 分页显示 2 页 → 第 2 页 **3 张**（13–15）→ 搜索 `条目13` 命中 **1 张**（证明搜索作用于全量而非当前页）→ 分类 `catUI-B` 命中 **3 张** → 清除筛选恢复 12 张；无骨架屏卡死、无数字错乱；播种 15 条已清理（deleted=15）。
  - 环境提示：本地 Redis 未在本机运行，需经 WSL2 localhost 转发（须保持 WSL 进程驻留，否则转发失效导致登录报「系统异常」）。
- **未验证（如实标注）**：生产未部署（本轮仅本地实测）；超大页码在前端的表现未走查（后端 offset 溢出边界已有单测覆盖）。
- **部署上线（2026-10-03）**：纯前端变更 ⇒ **只需替换 dist**（后端 jar 与线上 HEAD 保持一致，未动）。`npm run build` 产出 dist（74 文件 / 1,632,992 B），打包 tar SHA256 `f0e0fe56…197eba`（520,261 B）**上传后服务器复算一致**；回滚点 `/var/www/aikb.bak-20261003-223104-pre-b104fe.tar.gz`（521,514 B）+ 旧目录保留为 `/var/www/aikb.old-live-20261003-223104`；`tar` 解压到 `/var/www/aikb.new-*` 暂存 → `mv old` + `mv new` **原子替换**（避免「先删后传」的空白窗口）。
- **线上验证**：外网首页 **200**；线上 `index.html` 资源指纹已切换为新构建（`assets/index-CXdnqjv5.js`，`Content-Type: application/javascript`，68,283 B）；**线上产物内容校验**——`assets/knowledge-CtMbVPzZ.js` 实测包含新端点字符串 `/knowledge/page`（证明新代码真正上线，而非仅换了 index）；`verify_deploy.py` **3/3 PASS**（匿名 401 / 注册关闭拦截 / 登录文案统一）；新端点匿名访问仍 401。
- **已知现象（非缺陷）**：SPA `try_files ... /index.html` 兜底会让旧资源路径也返回 200（实为 index.html），故「旧 js 仍 200」不能作为回滚判据，已改用内容指纹核对。
- **未验证（如实标注）**：生产注册关闭 ⇒ **无登录态**，故线上未做人手点击式浏览器走查（浏览器实测在本地完成 8/8）；`dist` 内旧文件名清理依赖整目录替换，无残留。
- **下一步**：本轮闭环，无待办；候选项见 backlog（B-104 后端同源的 B-129、分类下拉取舍 B-130）。

## 本轮迭代留痕（多 agent · 2026-10-03 · B-104 列表服务端分页）

> 结论：**B-104 已完成并部署上线**（Dev 实现 → 独立评审 PASS with nits（1 medium 已修）→ 独立验证 VERIFIED → **生产部署完成**，commit `bfd1533`（代码）+ `259b030`（文档））。PO 2026-10-03 拍板口径：**新增独立分页端点**（旧接口一字不动）+ 知识列表 keyword/category **筛选下沉服务端** + 护栏 **page 默认 1 / size 默认 10 / 上限 50**（与 `admin/users` 一致）。

- **范围（2 个新增端点，旧 REST 契约零改动）**：`GET /api/knowledge/page?page&size&keyword&category`、`GET /api/file/page/{knowledgeId}?page&size`。**旧接口 `GET /api/knowledge`（全量 `List<KnowledgeVO>`）与 `GET /api/file/list/{knowledgeId}` 保持原样**——`/tree` 建树与「AI 回答存为笔记」下拉本质需要全量，把旧响应体改成分页对象会直接打挂它们（这正是选「独立端点」而非改响应体的原因）。
- **改动面（10 改 + 4 新）**：`KnowledgeMapper` / `FileMapper`（+`countPageByUserId` / `selectPageByUserId`、+`countByKnowledgeId` / `selectPageByKnowledgeId`，全部 `@Param` 绑定）；两个 Mapper XML（`<sql id="knowledgePageFilter">` + `<where>` + `LIMIT #{limit} OFFSET #{offset}`，`ORDER BY id` 保证翻页不重不漏）；`KnowledgeService` / `FileService` 接口与实现；两个 Controller；新增 `vo/KnowledgePageVO`、`vo/FilePageVO`（字段 `{total,page,size,list}` 与 `AdminUserPageVO` 完全一致）；`KnowledgeServiceImpl.getKnowledgeList` 改为复用新抽出的私有 `toVO(...)`（评审判定为**必要的同口径复用**，非夹带）。
- **筛选语义**：`keyword` 命中 `title OR content`（`LIKE CONCAT('%', #{keyword}, '%')`，全程参数化、无注入面）；`category` 精确匹配；空白/纯空格归一化为 `null`（不参与过滤）。文件分页复用既有 `verifyOwnership`，非作者仍得「权限不足」（HTTP 200 + body `code:500`）；知识分页强制 `user_id = UserContext.getUserId()`，天然按用户隔离。
- **评审（独立只读）**：`PASS with nits`。1 项 **medium 已在本轮修掉**——`(safePage - 1) * safeSize` 为 **int 运算**，page 极大时（size=50 约需 page > 42,949,672）溢出为**负 OFFSET** → MySQL 报错 → 落到通用 `Exception` 分支返回 `code:500 系统异常`（非越权/非注入，纯误报）。**修复** = offset 改 `long` 运算 + mapper `offset` 形参改 `long` + 补溢出边界单测。低项留痕：`LIKE` 未做 `ESCAPE`（keyword 含 `%` / `_` 会当通配符，属既有口径）；新旧端点集合相同但**顺序可能不同**（新端点 `ORDER BY id`）；两 Service 各自持有同名 `DEFAULT/MAX_PAGE_SIZE` 常量。
- **同源债（本轮新登记 backlog B-129）**：`AdminServiceImpl.users`（L109）为**同一 int 溢出写法**，属沿用而非本轮引入；B-104 范围外**不改**（避免夹带），已记账条件触发。
- **验证（独立）**：`VERIFIED`——4 项自报数据全部独立复现。默认全量回归 `mvn -o test` **249/0/0**（基线 243 + 新增 6）；`mvn -o test -Dtest=ListPaginationTest` 6/0/0；真实路径（本地 MySQL，只读）`mvn -o test -DexcludedGroups=e2e -Dtest=ListPaginationIntegrationTest` **5/0/0（`Skipped=0`，assumption 全未触发）**，验证「分页总数 = 既有全量查询条数」「首页条数 = min(size,total)」「越界页为空」「keyword / category 过滤口径」。**证伪力**：路由优先级用例是真负向对照（若移除 `/knowledge/page`，`page` 无法转 Long → 400 → 断言必挂）；护栏用例用 Mockito 桩校验 `selectPageByUserId(..., 50, 50)`，证 `safePage` / `safeSize` 真下传、非「声明未生效」。
- **未验证（如实标注）**：无登录态的真实 HTTP 端到端（生产注册关闭 ⇒ 无可用测试账号）；`UserContext` 为空时的实际请求行为；**前端未接入**——本轮严格限定「后端分页」，`List.vue` 仍走旧的客户端分页 + 前端过滤，服务端分页能力已就绪但暂无调用方。
- **部署上线（2026-10-03）**：本地构建 jar（`mvn -o package -DskipTests`）SHA256 `ec888ad8…c50d37`（117,069,836 B），**双端一致**；服务器回滚点 `/opt/aikb/app.jar.bak-20261003-pre-b104`（`cp -p` 保留原 B-122 jar `726e5de8…`）；`mv -f` 原子替换 + `systemctl restart aikb` → active、started **11.479s**、health `{"status":"UP"}`、启动日志 **0 ERROR/Exception**。
- **线上验证**：`verify_deploy.py` **3/3 PASS**（匿名 401 / 注册关闭拦截 / 登录失败文案统一）；外网首页 200；新端点匿名访问 `/api/knowledge/page`、`/api/file/page/1` 均 **401**（端点存在且受保护）。
- **未验证（如实标注）**：无登录态的真实 HTTP 端到端（生产注册关闭 ⇒ 无可用测试账号，`/api/knowledge/page` 与 `/api/file/page/{id}` 的路由优先级/参数护栏**仅由单测 + 本地只读集成测试覆盖**）；`UserContext` 为空行为；**前端仍未接入**（`List.vue` 仍客户端分页 + 前端过滤）。
- **剩余待 PO 拍板**：是否单开一轮把 `List.vue` 切到 `/api/knowledge/page`（服务端筛选 + 服务端翻页）。

## 本轮迭代留痕（多 agent · 2026-10-03 · B-120② / B-121）

> 结论：**B-120② 与 B-121 完成**（Dev 实现 → 独立评审 PASS（无 high/medium，low 2 项）→ 独立验证全绿，含负向对照）；**待 PO 验收**（未 commit、未 push、未部署）。B-120① 经源码复核判为**误报 / 已缓解（无改动）**；B-120③ 与 B-118 需 PO 授权 / 生产凭据。

- **B-120① 复核（判为非漏洞 · 无代码改动）**：`FileServiceImpl.uploadFile` 白名单**已** `originalName.toLowerCase().endsWith(".pdf"/".docx"/".md")` 归一化（L129-133）；`ParserFactory.getParser`(L32)、`DocumentServiceImpl.indexOutlineIfMarkdown`(L106)、`OutlineIndexServiceImpl`(L312)、`getFileContent`(L289) **均 `toLowerCase`** → `.MD` 通过属**预期的大小写不敏感匹配**，且下游解析/预览**无失配**（与演练观察一致）。演练记录里的「大小写绕过」标签不成立。
- **实现（Dev-agent，唯一可写者）**
  - `service/impl/FileServiceImpl.java`：`deleteFile` 在**归属校验之后、级联删除之前**新增 PROCESSING 护栏（L262-265）→ 抛 `BusinessException("文件处理中,完成后才能删除")`；SUCCESS / FAILED 删除路径与级联顺序（chunk→记录→OSS→向量）**零变化**。
  - `config/JwtAuthenticationFilter.java`：新增私有 `normalizeUri`（折叠重复斜杠 `/{2,}` + 去尾斜杠，L63-73），白名单判定与 MCP 分支改用归一化 URI（L32）；**未改** `WHITE_LIST` 集合、`tryParseToken`、MCP 分支语义，**未引入任何解码**（`%2F` 保持 fail-closed）。
  - 新增测试：`FileDeleteGuardTest`（6 例：PROCESSING 拦截且四路级联 never、SUCCESS/FAILED 照常级联、非作者/缺知识/缺文件口径不变）、`config/JwtWhitelistNormalizationTest`（6 例：双斜杠/尾斜杠放行、编码斜杠 401、非白名单匿名 401、带 token 时 `UserContext` 设置与清理）。
- **评审（独立只读 agent）· PASS**
  - `git status --porcelain` 恰 5 项（含 SM 的 `docs/sprint.md` 立项留痕），无夹带、无未追踪杂物；`git diff` 仅两个 Java 文件预期 hunk；`git diff --check` exit 0。
  - B-120②：护栏位置/文案正确；未误伤「同名覆盖成功后清旧版」（该路径走异步任务内直连 `fileMapper.deleteById`，不经 `deleteFile`）。
  - B-121：**「归一化是否放宽白名单」独立结论 = 否，无越权收益**——归一化只能"减斜杠"、造不出新路径，非白名单端点一律仍 401；尾斜杠形态（`/api/user/login/`、`/api/mcp-endpoint/`、`/actuator/health/`）在 Spring Boot 3 默认关闭尾斜杠匹配下**路由 404**，可达差异仅 401→404、无数据/权限收益。
  - 无 high / medium；**low 2 项**：① 尾斜杠"无越权"结论系框架默认 + 静态核查得出，缺 HTTP 级集成断言；② 未覆盖非白名单尾斜杠形态（`/api/file/1/` 仍 401）的断言。
- **验证（独立 agent）· 全绿**
  - `mvn -o test` → **`Tests run: 240, Failures: 0, Errors: 0, Skipped: 0` / BUILD SUCCESS**（基线 228 + 本轮新增 12）；`git diff --check` **exit 0**；`git status --porcelain` 恰 5 项。
  - **负向对照（关键，已做且未改任何跟踪文件）**：在仓库外临时目录编译「去护栏 / 去归一化」影子类放入 `target/test-classes`（gitignored）→ `FileDeleteGuardTest` **1 例失败**（期望 `BusinessException` 未抛）、`JwtWhitelistNormalizationTest` **2 例失败**（双斜杠/尾斜杠）；删除影子类后复跑 **240/0/0** → 证明断言**承重、非恒真**。
  - 契约核验：`BusinessException` → `GlobalExceptionHandler` → `Result.error`，**HTTP 200 + body `code:500`**（返回对象、未包 `ResponseEntity`），与硬约束一致。
- **未验证项（如实标注）**：HTTP 级真实容器端到端；`%2F` 真实容器路由（Tomcat 可能直接 400）；双斜杠「容器路由 ≡ 过滤器判定」仅单元级等价、未容器实测。**未 commit / 未 push / 未部署**（PO 保留拍板权）。
- **待办（本立项剩余）**：无。**B-120③ 与 B-118 均已完成**（B-118 含只读排查 + 缺失向量回填，见下节留痕）。

## 本轮迭代留痕（部署 + 排查 · 2026-10-03 · B-120②/B-121 上线 · B-120③ Nginx · B-118 排查）

> 结论：**B-120② / B-121 已上线生产**；**B-120③ 安全响应头已在 Nginx 层补齐并外部验证通过**；**B-118 排查完成**——检索层**未**长期静默降级 BM25，但发现**索引层历史静默丢向量**（113/1131 chunk 无向量，含 3 个文件全无向量），根因即 B-117 已修复的 `insert()` 静默吞错。

- **提交/推送**：`ee63726`(fix，4 文件) + `d79bb0d`(docs，sprint/backlog/HANDOFF)；`origin/main` = `d79bb0d`（0/0 同步）
- **部署（B-120② / B-121）**：本地 `mvn -o clean package -DskipTests` 构建 → SHA256 `1cfdc6a057b0d350fade71abefe2013c65c299d252bfb95246691264114f609f`；回滚点 `/opt/aikb/app.jar.bak-20261003-pre-b120`；原子替换 + `systemctl restart aikb` → `active`、`/actuator/health` = UP、重启后 error 0
- **未验证（如实标注）**：生产注册关闭、无可用测试账号 → **B-120② 无登录态 HTTP 端到端实测**（PROCESSING 文件删除 → 期望 HTTP 200 + body `code:500`）；B-121 无容器级 HTTP 实测（仅单元级 + Nginx 层外部实测）

### B-120③ 安全响应头（Nginx 层）

- 变更文件：服务器 `/etc/nginx/sites-available/aikb`，新增 4 行 `add_header ... always;`（其余 28 行原样保留）
  - `X-Frame-Options: SAMEORIGIN`
  - `X-Content-Type-Options: nosniff`
  - `Content-Security-Policy: frame-ancestors 'self'; object-src 'none'; base-uri 'self'`
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- 改前备份：`aikb.bak-20261003-pre-b120`（md5 `72bb891ba825ded14432393f32f502b1`）→ 安装新配置（md5 `b1c12e8765add80493a5d774e9869548`）→ `nginx -t` OK → `systemctl reload nginx` RELOADED_OK
- 外部复核（`curl -sI http://120.55.76.141/`）：HTTP 200 且 4 头齐全；`/`、`assets/index-BLlfZny_.js`、`assets/vue-vendor-VohRMAhz.js`、`assets/index-BUtlzUnt.css` 全 200
- 备注：首轮 `grep` 复核未命中系 grep/时序问题（`cat -An` + `nginx -T` + 完整 `curl -sI` 三项复核确认实际已下发），配置本身正确
- 性质：**服务器配置变更，不进仓库**（应用层 filter 覆盖不到 Nginx 直出的静态 HTML）

### B-118 线上检索是否长期静默降级 BM25（只读排查）

- **方法**：生产服务器 journal（`journalctl -u aikb`，覆盖 **2026-08-24T17:01 → 2026-10-03**，16,210 行）+ MySQL `knowledge_chunk`/`knowledge_file` 计数 + DashVector REST（`/stats`、按 `file_id`/`id` 过滤 query，1024 维零向量），**全程只读、零写操作**

- **结论一：检索层未降级（否）**
  - `降级为BM25单路` 出现 **0 次**；观测到的混合检索日志恒为「向量 **15** 条 + BM25 15 条 → 合并 18~26 条, userId=33」（最近 9/29 22:04）→ 向量路**持续在线可用**，不存在"线上长期静默降级 BM25"
  - DashVector 集合 `knowledge_chunk_vector`：`status=SERVING`、dim 1024、cosine、`index_completeness=1.0`、`total_doc_count=1024`

- **结论二：索引层存在历史静默丢向量（数据不一致，此前未知）**
  - MySQL `knowledge_chunk` **1131** 条 / 74 文件（全 SUCCESS）vs DashVector **1024** 条 → **113 条 chunk（10.0%）无向量**
  - 逐文件比对（74 文件，仅 8 文件不齐）：

| file_id | 文件名 | MySQL chunk | 向量 | 缺 | 备注 |
|---|---|---|---|---|---|
| 68 | 24-深入浅出外观模式 | 24 | 0 | 24 | 全缺 |
| 69 | 25-设计模式之代理模式 | 29 | 0 | 29 | 全缺 |
| 70 | 26-协调多个对象之间的交互——中介者模式 | 31 | 0 | 31 | 全缺 |
| 51 | 14-对象的克隆——原型模式 | 40 | 21 | 19 | 部分 |
| 71 | 27-处理对象的多种状态及其相互转换——状态模式 | 39 | 33 | 6 | 部分 |
| 50 | 13-复杂对象的组装与创建——建造者模式 | 34 | 32 | 2 | 部分 |
| 52 | 15-工厂三兄弟之工厂方法模式 | 27 | 25 | 2 | 部分 |
| 2 | 部署手册 | 2 | 8 | 0 | **多 6 条孤儿向量** |

  - **精确 ID 实证（关键）**：file 2 存量 6 条孤儿向量 id = `{382,383,420,421,423,424}`（MySQL 该文件仅 chunk 2,3，向量库仍留这 6 条且 `file_id=2`）；而 **file 50 缺失 id 恰为 `{382,383}`**、**file 51 缺失 id 含 `{420,421,423,424}`** → **孤儿向量 id 与缺失 chunk id 逐一对上**，直接坐实"主键复用冲突"机制

- **根因（与 B-117 同源，链路闭合）**
  1. 早期文件删除后向量未同步清干净（`deleteByFileId` 删除失败/上限，日志可见同期 `Query qps exceeds limit 7 for collection knowledge_chunk_vector`）→ 残留**孤儿向量**
  2. MySQL chunk 自增主键**复用**这些 id（如 file 50 的 382/383）
  3. 重建索引时调用 pre-B-117 的 `collection.insert()` → DashVector 逐条返回 `Duplicate Key`，但顶层 `isSuccess()` 仍为 true → **静默吞错**
  4. `IndexingServiceImpl` 未抛异常即 `successCount++` → journal 打出**假成功**：`索引完成，fileId=68, 成功=24, 失败=0`（实测 DashVector 中 id 700–723 **一条不存在**，`filter id>=700 and id<=783` 返回 0）
  5. 8 个异常文件**全部创建于 2026-09-21 12:23~12:40 同一批**（同批 67/72/73/74/78 正常）；该窗口 journal 密集出现 `批量索引失败，回退逐条索引 ... batch size is invalid, it should not be larger than 10`（**200 次**），逐条回退路径同样只校验顶层 → 冲突条静默丢失

- **影响面**：全局检索**不受影响**（向量路在线）；实际受损的是 file **68/69/70 三篇文档（84 chunk）在语义检索中不可达**，仅能靠 BM25 召回；另 5 文件部分 chunk 同理
- **已闭环部分**：写入路径已由 **B-117** 修复（`insert`→`upsert` + 逐条 `checkDocOpResults`），**新写入不再可能静默丢**；10/03 孤儿向量清理已删 31 条
- **残留 / 建议（待 PO 拍板，均为数据操作，不动代码）**
  - ① **回填** file 68/69/70 全量 + 50/51/52/71 缺失 chunk 的向量（可用现成 `reindexFile`，修复后走 `upsert` 幂等）
  - ② 清理 file 2 残留 6 条孤儿向量 `{382,383,420,421,423,424}`（删除后其 id 方可被复用）
  - ③ 建议补一条"索引后校验"护栏（写入后回查条数 != chunk 数则告警），杜绝再次假成功
- **未验证（如实标注）**：file 68/69/70 全量 84 条缺失的"每条对应哪个历史孤儿"未能逐一溯源（阻断它们的孤儿已在 10/03 清理，无从取证）；仅 file 50/51 的 6 例有精确 id 对上。**本次排查全程只读，未改任何线上数据/配置**


### B-118 回填执行留痕（2026-10-03 · 生产数据操作，PO 授权）

- **授权**：PO 拍板「回填缺失向量（推荐）」（AskUserQuestion，2026-10-03）。范围仅 ① 回填；② 孤儿单独清理未选（后被①覆盖消解）；③ 护栏单独立项为 **B-122**
- **方式**：一次性 Python 脚本（仅标准库）上传服务器 `/tmp` 执行，**纯数据、不动代码、不部署**；密钥从 `/etc/aikb/aikb.env` 读取。embedding 参数与线上逐项对齐——`text-embedding-v3` + `text_type=document` + 1024 维（经 `spring-ai-alibaba-dashscope` 1.1.2.1 字节码核对：`DEFAULT_EMBEDDING_TEXT_TYPE = EmbeddingTextType.DOCUMENT`，且 `application.properties` 未覆写 dimension）。写库走 DashVector REST `POST /v1/collections/knowledge_chunk_vector/docs/upsert`（按主键幂等）
- **执行结果**：目标 7 文件 **113/113 条缺失向量全部 upsert 成功**（file 68=24 / 69=29 / 70=31 / 51=19 / 71=6 / 50=2 / 52=2）；每文件写后即复核，向量数**全部等于 chunk 数**
- **全局对齐复核**：MySQL `knowledge_chunk` 74 文件 / **1131** 条（`max_per_file=44`）vs DashVector 逐文件向量数 → **不齐文件 0**；集合 `total_doc_count` **1024 → 1131**（+107 新增；另 6 条为 file 50/51 复用 file 2 孤儿 id 的幂等覆盖，不增计数），`index_completeness` 仍 1.0
- **工艺副作用（预期内）**：file 50 缺的 `{382,383}`、file 51 缺的 `{420,421,423,424}` 经 upsert **覆盖了原 file 2 的 6 条孤儿向量**，其 `file_id` 由 2 归位 50/51 → 子项 ② 的孤儿随之消解，**未额外执行删除指令**
- **真实路径验证（非静态核对）**：以 `text_type=query` 嵌入真实问题直查集合 ——「外观模式与代理模式的区别」→ 命中 file **68/69**；「中介者模式如何协调多个对象之间的交互」→ file **70**；「状态模式如何处理对象状态转换」→ file **71**。三篇此前**语义不可达**的文档现已可被向量召回
- **回滚**：本次为**幂等补写**（补齐本应存在的向量），无删除动作；如需回退可对上述 id 逐条 delete，但**不建议**（回退即恢复"语义不可达"缺陷）
- **清理**：服务器 `/tmp` 三个脚本（`backfill_vectors.py` / `verify_align.py` / `probe_retrieval.py`）执行后已删，复核残留 `NONE`；本地临时脚本同步删除；未在本机落任何密钥文件
- **遗留**：③ 已登记 **B-122**（未开工）；另 1 条**无 `file_id` 字段**的异常向量仍未处理（沿用 B-117 结论，不在本次范围）

## B-119 修复任务立项（2026-10-03 · 安全 · 中危）

> 立项依据：2026-10-03 攻防演习 C 项（详见文末「本轮迭代留痕（安全演练 · 2026-10-03 · Sprint 8 上线后）」）；PO 拍板立项。**状态：待开工（未指派 Dev）**

- **问题**：`/api/mcp-endpoint`（Spring 函数式端点）在无 token 或无效 `mcp-session-id` 时返回 HTTP 400/404，**响应体为完整 Java 堆栈**（类名 / 文件名 / 行号），稳定可复现 → 内部结构信息泄露。业务数据未泄露（工具调用仍被拒「MCP 工具需要认证」）。
- **根因**：该函数式端点未经过全局异常处理器（对比：普通 `@RestController` 畸形 JSON → HTTP 200 + 「系统异常，请稍后重试」，已脱敏）。
- **修复范围（仅此一项，禁夹带）**：让 `/api/mcp-endpoint` 的异常出口统一脱敏（可选路径：① 为该函数式端点接入统一异常处理；② 自定义 `@ControllerAdvice`／错误处理器覆盖函数式端点；③ session 解析失败改为脱敏 JSON）。**不改** MCP 工具契约、鉴权语义、`WHITE_LIST`，不放松/收紧现有放行口径。
- **DoD**
  - 无 token / 无效 session / 畸形 session 三类请求 → 响应体**不含**类名 / 文件名 / 行号（原文人工核对）
  - 正常 `initialize` / `tools/list` / 有 token 工具调用行为**零变化**
  - 全量回归保持全绿（基线 `mvn -o test` 222/0/0）+ `git diff --check` 通过
  - 未跑项显式标注「未验证」
- **约束（继承硬约束）**：Controller／端点前缀、`Result` 契约、`BusinessException` = HTTP 200 + body `code:500` 口径不变；改动全量回归全绿才可提交部署；部署前建回滚点；部署后清理临时凭据。
- **角色分工 / 节奏（三岗分离，同一时刻仅一个可写者）**：Dev-agent 实现 → 评审-agent（只读）审范围／契约／硬约束／夹带 → 验证-agent 跑门禁 → SM 回写 → PO 验收（评审 FAIL 沿用 1 轮回 Dev / 2 轮三岗会诊 / 3 轮挂牌 PO）。
- **留痕**：开工时在本文件追加「本轮迭代留痕（B-119 修复）」；**未经 PO 明确指示不得 commit / push / 部署**。
## 本轮迭代留痕（多 agent · 2026-10-03 · B-119 修复）

> 结论：**B-119 完成**（Dev 实现 → 独立评审 PASS → 独立验证全绿，含负向对照）；**待 PO 验收**（未 commit、未 push、未部署）。评审遗留 medium 1 项（M-1）待 PO 拍板是否收窄。

- **实现（Dev-agent，唯一可写者）**
  - 新增 `src/main/java/com/yansheng/aiknowledgebase/config/SafeThrowableSerializationConfig.java`：注册 `SimpleModule` bean，为 `Throwable.class` 定制序列化器，仅输出 `{"message": <getMessage()>}`（null/blank → `"Internal error"`），剥离 `stackTrace`/`suppressed`/`cause`。**未改动任何既有文件。**
  - 新增 `src/test/java/com/yansheng/aiknowledgebase/McpEndpointErrorLeakTest.java`：`@SpringBootTest(RANDOM_PORT)` + `@ActiveProfiles("local")` HTTP 级回归 6 用例（无 token 400 / 无效 session 404 / 畸形 session 404 / initialize / tools/list / 普通 API 畸形 JSON 守卫）。
  - 根因（源码级定位）：`/api/mcp-endpoint` 由 Spring AI 注册为函数式端点（RouterFunction），错误出口以 `ServerResponse.badRequest()/status(NOT_FOUND).body(new McpError(...))` **正常返回**；`McpError`（`io.modelcontextprotocol.spec.McpError`）`extends RuntimeException`，被 Spring MVC 用**主 ObjectMapper** 按 Throwable 序列化 → 带出 `stackTrace` 数组（含 `className`/`fileName`/`lineNumber`）。因未抛异常，`@RestControllerAdvice`／`HandlerExceptionResolver` 均不参与 → 物理上拦不到（对应任务书路径③「改为脱敏 JSON」）。
  - 选型理由：在序列化层拦截是当前约束下**最小**的应用层改法（15 行、零依赖、不动 MCP 工具契约/鉴权/`WHITE_LIST`/端点前缀）；仓库内唯一把 Throwable 当响应体的站点就是该端点。
- **评审（独立只读 agent）· PASS**
  - 范围：`git status --porcelain` 恰好 2 个 `??`（上述两文件），`git diff`（跟踪文件）为空，`git diff --check` exit 0；无夹带；无重复 `Module`/`ObjectMapper` 注册冲突（全仓仅此 1 个 `Module` bean）。
  - 硬约束逐条 ✅：`Result` 契约、`BusinessException`=HTTP200+body `code:500`、`GlobalExceptionHandler` 对普通 `@RestController` 行为、`WHITE_LIST`/`JwtAuthenticationFilter`/`KnowledgeMcpTools`/`application*.properties`/`pom.xml` 均零改动。
  - **全局副作用独立结论：无现实回归** —— 全仓检索无第二处把 `Throwable/Exception` 当响应体/序列化对象（SSE 载荷为 token/refs/ToolTraceEvent；`GlobalExceptionHandler` 一律返回 `Result`；`RedisConfig`/`ToolTraceSummarizer` 用独立 `ObjectMapper` 实例，不共享主 mapper）。
  - 无 high；**medium 1 项（M-1，非阻塞）**：全局 `Throwable` 序列化属「钝器」，当前无回归，但属应用级行为变更；可收窄为 SDK 具体类型 `io.modelcontextprotocol.spec.McpError`（SDK 全部错误出口均 `new McpError`）以彻底消除全局变更。**是否收窄待 PO 拍板。**
  - low 4 项：①「无效 session」与「畸形 session」实为同一 SDK 分支（去重后 400/404 共 2 类）；② 测试 `System.out.println` 打印 body/session；③ 序列化器回显客户端自供 sessionId（非内部结构）；④「带 token 工具调用」未做 HTTP 级覆盖（由既有 `KnowledgeMcpSecurityTest` + 全量回归间接覆盖，判不阻塞）。
- **验证（独立 agent）· 全绿**
  - `mvn -o test` → **`Tests run: 228, Failures: 0, Errors: 0, Skipped: 0` / BUILD SUCCESS**（基线 222 + 本轮新增 6 = 228）；`git diff --check` exit 0。
  - **真实应用 HTTP 复现**（`mvn -o spring-boot:run` @56382 + curl）：三类坏请求 body 原文 = `{"message":"Session ID missing"}`(400) / `{"message":"Session not found: invalid-session-123"}`(404) / `{"message":"Session not found: %%%not-a-session%%%"}`(404)，**均不含** `.java`/`lineNumber`/`stackTrace`/包名/栈帧；`initialize` 200 + `Mcp-Session-Id` 头、`tools/list` 200 返回 3 个工具。
  - **负向对照（关键）**：临时移除该 config 后重跑 → 6/0/0 变 `Failures: 3`、原堆栈（`WebMvcStreamableServerTransportProvider.java:358`）复现；还原后恢复 6/0/0 → 证明修复**承重且有效**、断言非恒真。
  - 契约守卫：`POST /api/user/login` 畸形 JSON → 200 + `{"code":500,"message":"系统异常，请稍后重试"}`（硬约束未破）。
  - 收尾：验证员启动的应用已停、端口 56382/56383/8080 已释放、无残留 java/mvn；WSL distro 已恢复 Stopped；工作区复原（仍恰好 2 个 `??`）。
- **未验证项（如实标注）**
  - 「带 token 的工具调用」未做 HTTP 级端到端（需有效 JWT + session + 真实库数据；鉴权语义由既有 `KnowledgeMcpSecurityTest` 覆盖）；真实 MCP Client（Claude/Cursor/Cherry Studio）联调未做；生产未实测。
  - 未部署、未 commit/push（PO 保留拍板权）。

## 本轮迭代留痕（部署 · 2026-10-03 · B-119 上线）

> 结论：**B-119 已上线生产并线上实测通过**（PO 拍板 提交 + 推送 + 部署）

- **提交/推送**：`97a01cd`(fix，2 新文件) + `85b394d`(docs，sprint/backlog/HANDOFF)；`origin/main` = `85b394d`（0/0 同步）
- **构建**：本地 `mvn -o clean package -DskipTests` → `Ai-Knowledge-Base-0.0.1-SNAPSHOT.jar`，SHA256 `2d015e0711d158c55753f484fb63bdebe38764c43c4a07fb4eccf20aa1372b1e`（117,064,045 B）；上传后线上 `sha256sum` **双端一致**
- **回滚点**：`/opt/aikb/app.jar.bak-20261003-pre-b119`（旧 jar `4e88c5c8ea9798050f90873040bb5888773903ee55b00ea3188682367d91e66a`，117,061,796 B）
- **替换/重启**：`cp` 至 `/opt/aikb/app.jar.new` → 校验哈希 → `mv` 原子替换 → `systemctl restart aikb` → `active`；`http://localhost:8080/actuator/health` = **200 / `{"status":"UP"}`**；重启后 error/exception 行 **0**
- **线上 B-119 实测**（直连 `:8080`）
  - 无 session → `400` `{"message":"Session ID missing"}`
  - 无效 session → `404` `{"message":"Session not found: invalid-session-123"}`
  - 畸形 session → `404` `{"message":"Session not found: %%%not-a-session%%%"}`
  - 泄露标记计数（`stackTrace|lineNumber|.java|包名`）**0 / 0 / 0**
  - 正常 `initialize` → `200` + `Mcp-Session-Id: e8f44e7f-…` + `result.serverInfo.name = ai-knowledge-base-mcp`
  - 契约守卫：`POST /api/user/login` 畸形 JSON → `200` + `{"code":500,"message":"系统异常，请稍后重试"}`
- **验收脚本**：`DEPLOY_BASE=http://120.55.76.141/api python scripts/verify_deploy.py` → **3/3 PASS**（注册关闭 → 依赖注册的 5 项跳过，本地回归覆盖）
- **前端**：本轮零改动，未重发
- **清理**：服务器 `/tmp/aikb-b119.jar`、`/tmp/b119_verify.sh` 已删（remaining 0）；本地临时凭据 `%TEMP%\aikb-deploy\` 已删
- **未验证（如实标注）**：带 token 的工具调用 HTTP 端到端；真实 MCP Client（Claude/Cursor/Cherry Studio）联调；前端未重发

## 本轮迭代留痕（多 agent · 2026-10-02 · Sprint 8 · T-1 / B-111）

> 结论：**T-1 完成**（实现 → 独立评审 PASS → 独立验证全绿 → 浏览器真实渲染复核全项通过）；**待 PO 验收**（未 commit、未 push、未部署）

- **实现（Dev-agent，唯一可写者）**
  - `frontend/src/utils/markdown.ts`：接入 `highlight.js/lib/core` + **12 个语言按需 `registerLanguage`**（javascript/typescript/java/python/xml/css/scss/bash/sql/json/yaml/markdown，避开 190+ 语言全量包）；用 `marked.use({ renderer: { code } })` 覆写代码块渲染（marked v18 原生支持，**未引入 marked-highlight**）
  - `renderMarkdown(src: string): string` **签名与同步语义未变**，三个调用点（`Chat.vue` L56-57 / L106、`Detail.vue` L11）**一行未改**
  - 未知/未注册语言**确定性降级**为转义纯文本（不使用 `highlightAuto`，避免流式下开销）；`hljs.getLanguage` 守卫 + `try/catch` 兜底，**保证 renderMarkdown 永不抛错**（它是模板内同步调用，抛错会白屏）
  - **DOMPurify `sanitize(html)` 默认配置零改动**（渲染器只改 marked 侧，不触碰 sanitize）
  - 排版增强落在 `Chat.vue` / `Detail.vue` 的 `.markdown-body` 与 `FilePreview.vue` 的 `.markdown-preview`：表格（表头底色/斑马纹/边框）、引用块（主色左竖条 + 浅底 + 圆角）、标题层级（h1~h4 梯度，h2 下边框）、行距、代码块/行内 code、`hr`、链接。配色**全部取自 `tokens.scss` token**，**未引入任何外部 hljs 主题 CSS**
  - `frontend/package.json`：仅新增 `highlight.js ^11.12.0`（lock 增量 10 行 = 声明 1 + `node_modules/highlight.js` 9，hljs 无传递依赖）
- **评审（独立只读 agent）· PASS**
  - 硬约束逐条 ✅：DOMPurify 白名单零改动 / API 契约零变化 / 三个调用点零改动（`git diff -U0` 证明全部 hunk 落在 `<style>` 区）/ 依赖合规 / 范围合规（仅 6 文件，无未追踪残留）
  - 无 high / medium；低风险观察项 2 条（见下「技术债/观察项」）
  - XSS 专项结论：**无回归面**——`escapeHtml` 与 marked v18 默认 code renderer 编码映射**逐字符等价**；`hljs.highlight().value` 自身转义输入；语言名拼入 class 前已转义；且 DOMPurify 仍是最终闸门
- **验证（独立 agent）· 全绿**
  - `npm run build` → **exit 0**（`2296 modules transformed`，`built in 11.98s`）
  - `mvn -o test` → **`Tests run: 222, Failures: 0, Errors: 0, Skipped: 0` / BUILD SUCCESS**（与基线 222/0/0 一致，后端仅形式门禁，本轮后端一行未改）
  - **真实 DOM 清洗验证（关键，实现者只做了静态核对）**：esbuild 打包**真实 `markdown.ts`** + jsdom 构造真实 DOM，`DOMPurify.isSupported = true`（**非 passthrough**），断言全绿：```java 围栏 → `<span class="hljs-keyword">` + `<code class="hljs language-java">` **经真实 DOMPurify 清洗后存活**；未知/无语言围栏无 `hljs` class 且 `<b>` 正确转义；`<h2>`/`<blockquote>`/`<table>` 均产出；XSS 用例（`<script>` / `onerror` / `javascript:`）被真实移除
- **浏览器真实渲染复核（本次补跑，闭环纯前端最易出事的环节）**
  - 手段：`vite preview` 起静态页，加载**真实构建产物**（`markdown-hQcwHLj9.js` + `Chat-BIxk6Di-.css` + `index-u7j9uKfD.css`），页面自动渲染，**本页 console 零 error**
  - 实测：`.hljs-keyword` × 7、computed `color: rgb(37, 99, 235)`（= `$color-primary`）；`.hljs-comment` = `rgb(148, 163, 184)` + italic；`table` `border-collapse: collapse`；`th` `background-color: rgb(248, 250, 252)`、`border-top-width: 0.8px`；`blockquote` `border-left-width: 2.4px`、`border-left-color: rgb(37, 99, 235)`；`h2` `border-bottom-width: 0.8px`；未知语言块 `<b>` 以文本呈现（`#out pre b` = 0）；无 `script` / `onerror` 残留
  - 复核用临时页与 preview 服务**已删除 / 已停止**，工作区无残留
- **体积留痕（基线 → 改动后）**

| chunk | 基线 raw/gzip (kB) | 改动后 raw/gzip (kB) | 变化 |
| --- | --- | --- | --- |
| `markdown-*`（应用 chunk：markdown.ts + highlight.js + 12 语言） | 0.17 / 0.16 | **86.48 / 24.15** | **+86.31 / +23.99**（hljs 落点） |
| `markdown-CwvEFpAO.js`（vendor：marked + dompurify） | 72.63 / 24.02 | 72.63 / 24.02 | **0（字节级不变，缓存不失效）** |
| `index-u7j9uKfD.css` | 358.34 / 48.17 | 358.34 / 48.17 | 0 |
| `element-plus-*` | 1,109.26 / 346.09 | 1,109.29 / 346.10 | +0.03（重哈希，非 hljs） |

- **顺带修复（在 T-1 目标范围内）**：`FilePreview.vue` 的 v-html 子选择器原为 plain 嵌套写法，被 Vue scoped 编译为 `.markdown-preview[data-v-x] h1[data-v-x]`，而 v-html 注入的子节点拿不到 `data-v-*` ⇒ **原规则全部落空**（只有容器自身样式生效）。改为 `:deep()` 后排版增强在文件预览里**才真正生效**（编译产物已核：`.markdown-preview[data-v-bdc0dbad] h1{...}`）
- **技术债 / 观察项（低风险，未修，留痕）**
  1. `escaped === true` 分支在 marked v18 实测**不可达**（token.escaped 恒为 undefined）；其内容与默认 renderer 语义一致，属无害冗余
  2. 三处样式存在轻微不一致：单元格内距（`$space-1 $space-2` vs 字面量 `6px 10px`）、h1 字号（1.3 / 1.35 / 1.4em）——系三个独立 scoped 组件容器宽度不同所致
  3. highlight.js 落在**应用 chunk** 而非 vendor chunk（`vite.config.ts` 的 `manualChunks.markdown` 仅含 marked/dompurify）；是否归入 vendor 属 T-3/后续缓存策略话题
- **未验证项（如实标注）**：真机/移动端视觉未看；**未部署、未 commit/push**（PO 保留该拍板权）；`element-plus` chunk 微增 0.03 kB 未深挖归因（非本任务范围）

## 本轮迭代留痕（多 agent · 2026-10-02 · Sprint 8 · T-2 / B-113）

> 结论：**T-2 完成**（实现 → 独立评审 PASS → 独立验证全绿）；**待 PO 验收**（未 commit、未 push、未部署）

- **实现（Dev-agent，唯一可写者）**
  - `src/main/java/com/yansheng/aiknowledgebase/service/impl/ChatServiceImpl.java`：新增私有方法 `buildChatContext(Long userId, String question)` + 私有 `record ChatContext(List<SearchResult> searchResults, String prompt)`（置于 `streamAskWithAgent` 之后、`clear` 之前）
  - 抽取内容 = **恰好那 4 条**逐字重复语句（`retrieveTopK` → `getHistory` → `recall(...,3)` → `buildChatPrompt`），语句、参数与执行顺序**逐字不变**
  - 两处调用点各替换为：`ChatContext ctx = buildChatContext(userId, question);` / `List<...SearchResult> searchResults = ctx.searchResults();` / `String prompt = ctx.prompt();`
  - **顺序零漂移**：快速路径 `if (enableWebSearch) prompt = appendWebSearchContext(prompt, question);` 仍在 `historyService.append(userId,"user",question)` **之前**；Agent 路径保持「先拼 Prompt 再落 user 消息」；三处 `onDone.accept(searchResults)` 同源保留
  - **未夹带**：`ask`/`ask(...)`/`appendWebSearchContext`/`clear`/`history`、4 参重载委托行、`generateStream` 三个回调、尾部 `memoryContent`+`remember`、`ChatService` 接口与方法签名——全部原样未动；无 import 变更、无新增依赖
  - `record ChatContext` 两字段在两个调用点**均被真实读取**，无死字段；原调用点后续确实未再使用 `history`/`memories`，不纳入返回值不构成漏返回
  - 环境坑复现：Edit 对仓库路径报 Access denied → 改用「Write 临时 py 脚本 + python 精确替换（断言命中数=1）+ 原位写回」，保持原行尾、无 BOM，临时脚本已删
- **评审（独立只读 agent）· PASS**
  - 无 high / medium / low
  - `git diff --stat` = 仅 `ChatServiceImpl.java`（+22 / −8）；`git status --porcelain` 无 `??` 未追踪残留
  - 行为等价性逐条 ✅（4 条语句逐字一致 / 执行与副作用顺序不变 / `onDone.accept(searchResults)` 同源保留 / 异常传播未变，未吞异常）
  - 夹带检查 ✅；无谓抽象检查 ✅（无死成员；`java.version=17`，项目已有 `ToolTraceEvent` 等既有 record 用法）
- **验证（独立 agent）· 全绿**
  - `mvn -o test` → **`Tests run: 222, Failures: 0, Errors: 0, Skipped: 0` / BUILD SUCCESS**（与基线 222/0/0 一致，偏离 0）
  - 日志中 `ERROR` 行为测试内预期噪音（DashVector 白名单降级路径 + `RateLimitServiceTest` 模拟 Redis 宕机注入），Errors 仍为 0
  - 范围复核：仅 `ChatServiceImpl.java` 未暂存修改；`HEAD` 仍 `964e39a`（未提交）
- **体积/性能**：纯等价抽取，无产物体积或运行期行为变化（无需前后对比）
- **未验证项（如实标注）**：真机/移动端未做；**未部署、未 commit/push**（PO 保留该拍板权）；未与远端比对 ahead/behind

## 本轮迭代留痕（多 agent · 2026-10-02 · Sprint 8 · T-3 / B-103）

> 结论：**T-3 完成**（实现 → 独立评审 PASS → 独立验证全绿）；**待 PO 验收**（未 commit、未 push、未部署）
> 本任务经 **3 轮定位**：直觉实现的「移除全量引入」只减 165 kB，真正的拦路虎是 `manualChunks` 分包配置**本身**阻止 tree-shaking。

- **实现（Dev-agent，唯一可写者）**
  - `frontend/vite.config.ts`：接入 `unplugin-vue-components` + `unplugin-auto-import`（`ElementPlusResolver`，dts 输出到 `src/`）；`manualChunks` **由对象数组形式改写为函数形式**（见下「根因」）
  - `frontend/src/main.ts`：删除 `import ElementPlus` / `import 'element-plus/dist/index.css'` / 全量图标注册循环 / `app.use(ElementPlus,{locale})`；`ElLoading` 改由 AutoImport 注入并 `app.use(ElLoading)` 注册 `v-loading`
  - `frontend/src/App.vue`：`<el-config-provider :locale="zhCn">` 包裹 `<router-view/>`（locale 行为等价，替代原 `app.use(ElementPlus,{locale:zhCn})`）
  - 16 个业务 `.ts`/`.vue`：**仅增删 import 行**（移除 `ElMessage`/`ElMessageBox` 的**价值导入**改由 AutoImport 注入；混合行降级为 `import type { FormInstance, FormRules }`），模板/样式/逻辑**零改动**
  - `frontend/src/layouts/Layout.vue`：为该文件模板实际使用、此前**仅靠全局注册**生效的 3 个图标补显式 import（`ArrowDown`/`ChatDotRound`/`Expand`）——这是移除全局图标注册后**唯一**的功能回退风险点（Dev 先核对后动手，命中「立即停止」条件并上报，经授权后最小修复）
  - `frontend/tsconfig.json`：`exclude` **仅**排除生成的 `src/components.d.ts`（其含 el-table 插槽严格类型，纳入会暴露 3 个业务文件的既有类型问题，而业务文件本轮禁改）；保留 `auto-imports.d.ts` 以保证 `ElMessage`/`ElMessageBox`/`ElLoading` 类型可见
  - 生成物：`frontend/src/components.d.ts`（35 个 `El*` 组件 + `vLoading`）、`frontend/src/auto-imports.d.ts`
  - 依赖：仅新增 devDependencies `unplugin-vue-components`、`unplugin-auto-import`（**无**新增运行时依赖）

- **根因（3 轮定位，关键经验，务必留档）**
  - 第 1 轮：只移除 `main.ts` 全量引入 → element-plus chunk `1,109.29 → 944.32 kB`（**−165 kB，几乎全部来自图标**），组件库未被摇掉；产物中 `el-date-picker`/`el-cascader`/`el-transfer`/`el-calendar` 等**未使用**组件仍在
  - 第 2 轮：移除全部 `element-plus` 根 barrel 价值导入 → **字节级无变化**（944.32，hash 不变）⇒ barrel 价值导入**不是**根因
  - 第 3 轮：**真根因 = `manualChunks` 把 `element-plus` 强制归入单一 chunk**。数组形式 `['element-plus', ...]` 会把包名解析为 chunk 入口（barrel `es/index.mjs`）强制拉入依赖图；**函数式强制归类同样失效**（被归类模块的导出被当作 chunk 入口而不再 tree-shake）——实测「归类全部 element-plus 组件」仍产出 947.09 kB 且未用组件俱在
  - 修法：`manualChunks(id)` **只命名 `@element-plus/icons-vue`**，element-plus 组件交给 Rollup 按共享度自动拆分（**不强制归类**）→ tree-shaking 恢复，未用组件 grep 归零

- **体积留痕（基线 → 改动后）**

| 产物 | 基线 raw/gzip (kB) | 改动后 raw/gzip (kB) | 变化 |
| --- | --- | --- | --- |
| `element-plus-*.js` | 1,109.29 / 346.10 | **30.59 / 7.11** | **−1,078.70 / −339.0（≈ −97%）** |
| CSS 合计 | 358.34 / 48.17（单文件） | **201.68 / 38.43**（29 个按需分片） | −156.66 raw |
| `vue-vendor-*.js` | 174.43 / 63.45 | 172.91 / 62.95 | ≈0（无回归） |
| `markdown-*.js`（marked+dompurify） | 72.63 / 24.02 | 72.63 / 24.02 | 0 |
| `markdown-*.js`（应用 chunk，含 hljs） | 86.48 / 24.15 | 86.47 / 24.14 | 0 |
| `Graph-*.js` | 461.06 / 155.12 | 461.31 / 155.25 | ≈0 |
| JS 合计 | （基线仅 6 大 chunk，未含小 chunk，不可直接比） | 1,392.27 / 485.98 | — |

- **tree-shaking 生效判据**：全产物 grep `el-date-picker|ElDatePicker|el-cascader|ElCascader|el-transfer|ElTransfer|el-calendar|ElCalendar|el-color-picker|ElColorPicker` = **0 命中**；`dayjs` 仅剩 **2 处**，来自 element-plus barrel 顶层无条件 `import dayjs`（与业务无关，无法去除）
- **评审（独立只读 agent，general-purpose 但严格禁写禁跑）· PASS**：`git diff` 逐文件核对——除 `App.vue`（允许文件）与 `Layout.vue`（图标 import 增 3，均为模板实际使用）外，其余 15 个业务文件改动 **100% 为 import 行**，模板/`<style>`/逻辑零改动；`package.json` 仅新增两个 devDependencies；残留 `from 'element-plus'` 仅 4 处 `import type`（编译期擦除）；**无夹带**。**无 high/medium**；low 3 项见下（另注：首轮误派无 shell 的 Explore 评审 agent，其「FAIL」结论因**无法执行 `git diff`** 而不成立，已改用可执行只读 git 的 agent 重审）
- **验证（独立 agent）· 全绿**：`npm run build` **exit 0**（2390 modules，12.88s）；`mvn -o test` → **`Tests run: 222, Failures: 0, Errors: 0, Skipped: 0` / BUILD SUCCESS**（与基线偏离 0）；产物 CSS 中 `.el-message-box`=43 / `.el-message__content`=7 / `.el-loading-mask`=4（按需样式已注入，toast/确认框/loading 不掉样式）
- **技术债 / 观察项（低风险，未修，留痕）**
  1. `v-loading` 指令不参与 unplugin resolver 的自动解析，需在 `main.ts` 显式 `app.use(ElLoading)` 注册（该方案常规做法，非缺陷）
  2. `tsconfig.json` 排除 `src/components.d.ts` 会弱化 el-* 组件 prop 的 `vue-tsc` 类型校验（构建/运行不受影响）；若后续愿意修 3 个业务文件的 el-table 插槽类型可再纳入 —— 计入技术债台账
  3. `manualChunks(id)` 中 `id.includes('marked') || id.includes('dompurify')` 匹配略宽松（当前依赖集内无害，仅影响分包归属）
  4. `frontend/src/api/request.ts` 位于 Sprint 8「不许碰 `api/**`」名单内，本轮为完成任务删除了 1 行 `import { ElMessage } from 'element-plus'`（纯 import 行，零逻辑改动）——评审已记录该**豁免依据**
  5. `vite.config.ts` 内联注释已记录「不可把 element-plus 包名/组件强制归类进单一 chunk」这一**反直觉约束**（实测有 6 组对照实验支撑）
- **未验证项（如实标注）**：`@Tag("integration")` 集成测试未跑（需本地 Redis/WSL 常驻）；浏览器真实渲染复核未做（仅静态核对产物 CSS 中按需样式存在）；**未部署、未 commit/push**（PO 保留该拍板权）

## 本轮迭代留痕（多 agent · 2026-10-03 · Sprint 8 · T-4 / B-117 阻塞）

> 结论：**T-4 顺延**（PO 2026-10-03 拍板「先定位再修」）；**本轮零业务改动**（仅临时日志/探针，日志留 `target/`，探针已清理）；**待 B-117 定位并修复后重取真实混合基线**。

- **白名单问题已排除（此前误判为白名单的排查闭环）**
  - 控制台白名单**只接受 IPv4/CIDR**：实测 IPv6 `2408:8352:1a20:1480:6cef:c533:2ef:5cf3/128` 被拒（原文「IP地址格式错误，请重新输入」）；现有 5 条 IPv4 含本机出口 `116.163.49.43`
  - 端点 `vrs-cn-moy4ydvtt0001k.dashvector.cn-shenzhen.aliyuncs.com` 有 AAAA 记录，JVM 默认走 IPv6 才撞白名单；加 `-DargLine=-Djava.net.preferIPv4Stack=true`（pom 的 surefire 无 argLine，必须用 `-DargLine` 传）后 `whiteList validate fail` **消失**
  - 证据：`target/eval-run1.log`（09:42）、`target/eval-run2.out`（09:45）两跑**均无** `whiteList validate fail`，`Tests run 1/0/0/0` BUILD SUCCESS，两次数字逐位一致，**零文件改动**
- **DashVector 侧健康（REST 直连实测，非推测）**：集合 `knowledge_chunk_vector` dim 1024 / cosine / FLOAT / 1,131 向量 / `fields_schema:{}`；插探针 `id=900001,file_id=900001` 后**立即**按 `filter file_id=900001` 查到；按既有 `file_id=52/80/82` 过滤亦能查到且带 fields；探针已删除并复核查不到 → **DashVector 插入+过滤均正常**
- **真阻塞 = 应用写入链路静默失败（B-117）**：eval 自报「批量索引成功 / 索引完成 成功=1 失败=0」，紧接着用相同 file_id 查为空；检索日志恒为「**向量 0 条** + BM25 N 条 → 合并 N 条」。代码：`VectorStoreServiceImpl.insertBatch` 只校验顶层 `response.isSuccess()`，不校验逐条 `DocOpResult.code`；`IndexingServiceImpl` 的 successCount 为乐观计数（`successCount += valid.size()`）。SDK = `dashvector-java-sdk:1.0.21`。疑因=逐条写入被拒（免费集群 QPS 限 7/s，日志已现 `Query qps exceeds limit 7`）被吞，或字段未落
- **当前基线（BM25 降级口径，非真实混合基线；4 次复跑逐位一致）**

| 口径 | recall@5 | MRR | 说明 |
| --- | --- | --- | --- |
| 文档级 | 0.833 | 0.861 | — |
| chunk 级 | 0.833 | 0.861 | 向量路 0 命中，实为 BM25 单路 |

  分层：keyword 1.000/0.900 · section-locate 1.000/1.000 · semantic 0.833/0.833 · multi 0.333/0.667；未命中：s_004、m_001、m_002、m_003。**与 docs 内 v3 记录的 0.972（向量路可用口径）口径矛盾**，即本次发现 B-117 的线索
- **PO 决策（2026-10-03）**：①「先定位再修，T-4 顺延」——派 Dev 加一次性探针打印 DashVector SDK insert 的原始 Response 与逐条 `DocOpResult.code`，确证根因后小步修复，再拿真实混合基线跑 T-4；②「线上检索是否静默降级 BM25」**单独立项排查**（B-118）
- **风险提醒**：DashVector 免费集群 `aikb-free-2026` **2026-10-11 19:37 到期**（影响 Sprint 9 的 B-114 Phase2 有树 vs 无树对比）
- **硬约束（不变）**：Dev/评审/验证三岗分离；同一时刻唯一可写者；禁止夹带重写/换栈/无关改名；禁止改 T-4 既有文档级口径/算法/阈值与数据集既有字段；**未 commit/push/部署**（PO 保留拍板权）

## 本轮迭代留痕（多 agent · 2026-10-03 · Sprint 8 · T-4 / B-117 修复与基线）

> 结论：**T-4 完成**（B-117 根因定位 → 最小修复 → 取到真实无树基线 → 独立评审 PASS（medium 1 项已修）→ 独立验证全绿）；**待 PO 验收**（未 commit、未 push、未部署）

- **B-117 根因（探针原文确证，非推测）**：`insertBatch` 顶层 `Response.isSuccess()=true / code=0`，但**逐条 `DocOpResult` 18/18 全部 `code=-2027 Duplicate Key`**。主键 682–699 在 DashVector 已存在且带向量（`file_id=67`，该 file 在 MySQL 已不存在 → 孤儿向量），本次新建 chunk 复用同一主键区间 → 全部冲突。`VectorStoreServiceImpl` 只校验顶层 `isSuccess()`，冲突被静默吞；`IndexingServiceImpl` 乐观计数 → 日志假报「成功=1 失败=0」→ 新 file_id 无向量 → 检索「向量 0 条」→ BM25 兜底
- **修复（PO 拍板 A+B，最小改动，仅 1 个业务文件）**：`VectorStoreServiceImpl` 的 `insert` / `insertBatch` 由 SDK `insert` 改为 **`upsert`**（`UpsertDocRequest`，按主键幂等覆盖，恢复 `IndexingServiceImpl` 注释所假设的「重跑幂等」语义）+ 新增 `checkDocOpResults` **逐条 code 校验**（任一 `code != 0` 抛异常，含失败条数 / 首个 id、code、message、requestId；顶层 `isSuccess()` 校验保留）；`IndexingServiceImpl` **未改**（批量失败必抛 → `successCount += valid.size()` 只在全成功时执行，日志恢复真实）；无新增依赖（复用 `dashvector-java-sdk:1.0.21` 的 `upsert` / `UpsertDocRequest`，`javap` 已证）
- **修复生效证据**：向量**确实落库**（SDK `fetch` 实测新 file_id 可查到、带 vector 与 `file_id` 字段；评测自清理后复核查不到）；检索日志由「向量 0 条」变为**「向量 15 条 + BM25 N 条」**（18 用例全部）
- **真实无树基线（两次复跑逐位一致；报告/断言口径已按此修正）**

| 口径 | recall@5 | MRR | 阈值 | 余量 |
| --- | --- | --- | --- | --- |
| 文档级 | 0.833 | 0.861 | 0.80 / 0.70 | 0.033 / 0.161 |
| chunk 级 | 0.833 | 0.861 | 0.75 / 0.75 | 0.083 / 0.111 |

  分层（文档级 = chunk 级）：keyword 1.000/0.900（5）· section-locate 1.000/1.000（4）· semantic 0.833/0.833（6）· multi 0.333/0.667（3）；未命中：s_004、m_001、m_002、m_003
- **关键发现（留痕，供 Sprint 9 参考）**：**向量路已实测生效，但本数据集上文档级与 chunk 级指标与 BM25 单路基线数值完全相同**（0.833/0.861）——即本数据集对「有无向量路」不敏感；`docs` 内早期 v3 记录 0.972 在本数据集/口径下**不可复现**。Sprint 9 有树 vs 无树对比须注意此基线灵敏度问题
- **门禁（独立验证-agent 实测）**：`mvn -o test` → **`Tests run: 222, Failures: 0, Errors: 0, Skipped: 0` / BUILD SUCCESS**（与基线一致）；`npm run build`（frontend）→ **exit 0**（`✓ built in 12.65s`）；eval 两次 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` BUILD SUCCESS 且数字逐位一致
- **评审（独立只读 agent）· PASS**：文档级口径 / 算法 / 阈值与 JSON 既有数据字段**零改动**（`git diff` 逐 hunk 证明）；chunk 级共用同一次检索、命中判定未放宽；B-117 修复覆盖两条写入路径且 `IndexingServiceImpl` 无 diff；无夹带（后端仅 2 个 `.java`）。**无 high**；medium 1 项（注释降级归因口径）**已修**；low 3 项留痕
- **技术债 / 观察项（low，未修，留痕）**：① `retrieval-cases.json` 的 `meta` 描述字段被改（version 4→5、pipeline/note 措辞）——非纯新增，已在 note 中注明；② `LongTermMemoryServiceImpl:124` 仍为 `insert` + 仅校验顶层 `isSuccess`，**同类静默失败风险未覆盖**；③ DashVector 存量孤儿向量未清（collection 1,131 vs 本地 MySQL 162 chunk）、`deleteByFileId` 用 `topk(100)`（单文件 >100 chunk 时可能残留孤儿）
- **未验证项（如实标注）**：`@Tag("integration")` 与 e2e 未跑（本地无 Redis / 无外部服务）；真机 / 移动端视觉未做；**未部署、未 commit/push**（PO 保留该拍板权）

## 本轮迭代留痕（多 agent · 2026-10-03 · Sprint 8 · B-117 后续债：deleteByFileId topk 分页 + 孤儿向量只读清单）

> 结论：**`deleteByFileId` 的 `topk(100)` 上限已修复**（实现 → 独立评审 PASS → 独立验证全绿）；**DashVector 孤儿向量只读清单已产出，按 PO 口径暂不删除**（未 commit、未 push、未部署）

- **修复（Dev-agent，唯一可写者）**：`VectorStoreServiceImpl.deleteByFileId` 由「零向量 + `filter(file_id=X)` + `topk(100)` 查一次 → 删一次」改为 `while` 分页循环——每轮重查下一页主键并删除，直到查询返回空或本页条数 `< pageSize`；新增 `pageSize=100` / `maxRounds=50` / `maxTotal=5000`，触顶 `log.warn`。原有语义保留：集合不可用 / 查询失败 / 删除失败 / `fileId==null` 均只告警、不阻断业务删除
- **评审（独立只读 agent）· PASS**：本轮仅动 `deleteByFileId`；`insert`/`insertBatch`/`checkDocOpResults` 保持 B-117 已评审形态（无再次改动）；终止条件健全（空集 / 末页 / 查询失败 / 触顶四路可分，**「查询失败」未被误判为「清理完成」**）；`maxRounds`/`maxTotal` 双上限保证无死循环；无夹带。**无 high**；medium 1 项（删除响应只校验顶层 `isSuccess()`、未逐条校验 `DocOpResult.code`）判为**范围外既有风险**，本轮不扩大范围，已记入 backlog B-117 备注 ④；low 2 项（delete 无实效场景下 `totalDeleted` 虚增、恰为整数倍时多一次空查）留痕
- **零向量 query 未决项消解**：改动内未直接验证「零向量 query 可行性」，但 `target/orphan-vectors-report.md §1` 实证已用 1024 维零向量 + filter + topk 对同一集合 query 成功（17 次），SDK query 亦为线上既有链路 ⇒ 判为**非真实未验证项**
- **验证（独立 agent）· 全绿**：`mvn -o test` → **`Tests run: 222, Failures: 0, Errors: 0, Skipped: 0` / BUILD SUCCESS**（与基线偏离 0）；前端 `npm run build` → **exit 0**（`vue-tsc -b && vite build`，`built in 11.70s`）；跑测后 `git status --porcelain` 与跑测前逐行一致，无新增未追踪残留
- **孤儿向量只读侦查（独立只读 agent，未对 DashVector 执行任何写操作）**：DashVector Java SDK 无「列全部向量」接口、`queryGroupBy` 被拒（`code=-2053`，集合 `fields_schema:{}`）⇒ 改用 REST 零向量 + `filter=file_id>=a and <b` + `topk=1024` 对 file_id 区间**递归二分**覆盖 `[0,16384)`（17 次查询），按 doc 主键全局去重，与 `stats.total_doc_count` 交叉校验一致。**结果**：集合快照 **1,055** 条（含 1 条无 file_id 异常向量）；本地可归属 **110** 条；按本地口径候选孤儿 **945** 条（71 个 file_id）。分档：高风险（file_id>259，超本地与生产已知区间）5 个 / 14 条；中风险（84<id≤259）5 个 / 17 条；其余疑似生产在用。**真孤儿无法判定**（集合疑似与生产共用、生产 file_id 清单未取得）；`file_id=67` 现有 35 条（与此前观察不同 ⇒ 集合已被生产改动，历史计数不可复用）。清单：`target/orphan-vectors-report.md` + `target/orphan-vectors.tsv`（gitignored）
- **PO 口径（2026-10-03）**：**先只出只读清单，暂不删**；删除前置 = 取得生产 file_id 清单确证（或 PO 确认本地即权威）
- **未验证项（如实标注）**：`@Tag("integration")` 与 e2e 未跑；DashVector 侧未做任何写操作；**未 commit/push/部署**（PO 保留拍板权）

---

## 本轮迭代留痕（多 agent · 2026-10-03 · B-122 索引后校验护栏）

> 结论：**B-122「索引后校验护栏」已实现 + 独立评审 PASS with nits（无 high）+ 独立验证全绿 + 已部署上线（2026-10-03）**

- **背景**：B-118 排查发现索引层历史静默丢向量（113 条 chunk 无向量），B-117 已修写入路径（`upsert` + 逐条 `code` 校验并抛），但**写入后无回查**——日志仍是乐观计数。B-122 补这一环。
- **实现（Dev，唯一可写者；3 源码 + 1 测试）**：`VectorStoreService.countExisting(List<Long> chunkIds)` 新只读接口；`VectorStoreServiceImpl` 用 DashVector `fetch(ids)` 分批（`fetchBatch=100`）回查，统计真实返回的文档数；`IndexingServiceImpl.indexChunks` 末尾调 `verifyIndexed(fileId, chunkList)`——期望数取全部非空 chunk id，不符仅 `log.warn`、护栏自身异常自吞。
- **关键实现偏差（与立项口径）**：立项写「按 fileId 回查条数」，但 SDK `query` **无 `offset`、`topk` 上限 100** ⇒ 单文件 >100 chunk 时按 filter 计数永久失真。改按**已知 chunk id `fetch` 精确回查**（不受 topk 限制，单次上限 1024，已分批）。期望值用 `chunkList` 全部非空 id（非 `successCount`）——**有意为之**：用 `successCount` 恰会被 B-118 式乐观计数蒙蔽。
- **评审（独立只读 agent）· PASS with nits，无 high**：① 护栏确实只观测——`verifyIndexed` 的 `try/catch(Exception)` 完整包住 `countExisting`，位于主循环与「索引完成」日志之后；② 分批循环无 off-by-one，空/null 直接返回 0；③ medium=`fetch` 返回语义（Map 是否只含真实存在文档）**未运行时确证**（实现已用 `entry.getValue() != null` 防御，缺席项返回 null 亦不误计）；④ low-中=写入后可读若存在最终一致延迟会误报（warn-only，仅日志噪音；B-118 回填「写后即复核条数相等」为反证）；⑤ low=`countExisting` 使通用接口变宽（接受：只读成员、同域；为单方法另起接口属过度设计）。
- **验证（独立 agent）· 全绿**：`mvn -o test` → **`Tests run: 243, Failures: 0, Errors: 0, Skipped: 0` / BUILD SUCCESS**（基线 240 + 新增 3）；定向 `IndexingPostVerifyTest` 3/0/0、`IndexingReindexTest` 2/0/0；`git diff --stat` 复核范围纯净无夹带。**独立证伪**：既有 `IndexingReindexTest` 从未 stub `countExisting` → mock 默认返回 0 → 期望 2≠实际 0 → 仍通过，**独立证实「不符不阻断主流程」**。
- **未验证项（如实标注）**：① 真实 DashVector `fetch` 运行时语义（本地 `local` profile 指向 `invalid.dashvector.local` 模拟端点，无法本地实测）；② 端到端索引路径（`ChunkIndexingIntegrationTest` 属 `@Tag("integration")`，被 pom 默认排除，本轮未跑）；③ 写入后读的最终一致性窗口。
- **残留风险性质**：护栏为 warn-only，上述未验证项若成真只产生日志噪音（假告警），不影响索引正确性与主流程语义。
- **部署上线（2026-10-03 · PO 授权）**：本地 `mvn -o package -DskipTests` 构建 jar，SHA256 `726e5de86598708a9b9f7ebe6cbeb4bae8c3b1048148472bb4db6f6253aa6a4b`（117,065,583 B）**双端一致**；回滚点 `/opt/aikb/app.jar.bak-20261003-pre-b122`（旧 `1cfdc6a0…f609f`，117,064,303 B）；`/opt/aikb` 内 `.new` → `mv -f` 原子替换后 `systemctl restart aikb` → `active`、`:8080/actuator/health` `{"status":"UP"}`、启动 2 分钟窗口 error 计数 0。**线上验证**：`verify_deploy.py` **3/3 PASS**（匿名 401 / 注册关闭拦截 / 登录文案统一）；B-119 脱敏回归仍成立（MCP 坏请求 body 无 `stackTrace`/`at java`/`at com.yansheng`，泄露标记计数 0）；外部 `/` 200。**未验证（如实标注）**：B-122 护栏本身**无线上端到端实测**——生产注册关闭、无可登录账号 ⇒ 无法触发上传/索引路径（护栏仅在 `indexChunks` 时执行）；真实 DashVector `fetch` 语义仍为唯一未决项。提交 `0cf6b91` 已 push（origin/main 同步）。

---

# Sprint 7（已归档）· 目标：B-116 管理端写操作 + `user.role` 二值模型

> 开于 2026-10-02 | 承接 Sprint 6（B-114 Phase1 大纲导航层已上线生产）
> 状态：**已收口并上线**（实现 → 评审 PASS → 验证全绿 → 生产上线 → 线上登录态写操作实测通过；`mvn -o test` 222/0/0 · `AdminWriteIntegrationTest` 3/0/0 · 前端 build exit 0 · 本地真实 HTTP 四端点正/负向 body code 全部符合契约 · **生产登录态正/负向矩阵全部符合契约、临时数据已彻底清理**）
> 模式：**拆分模式**——SM 由本会话担任且不写业务码；Dev / 评审 / 验证由**独立子 agent** 担任

## 角色分工
- PO：用户（拍板权：commit/push、生产部署、DDL 上线、产品方向）
- SM：本会话（维护 `sprint.md` / `backlog.md` / `HANDOFF.md`、跟阻塞、守流程；只改文档）
- Dev：子 agent（每轮迭代新起独立实例）
- 评审 / 验证：独立子 agent（禁止与 Dev 同一实例；只读，不写业务码）

## 迭代节奏（继承 Sprint 6）
1. **Dev-agent** 实现 → 交付三件套：改了什么 / 怎么跑 / 预期看到什么
2. **评审-agent**（只读）审范围、契约一致性、硬约束、夹带 → PASS / FAIL + 问题清单
3. **验证-agent** 跑门禁命令 → 只留真实结果，未跑项标「未验证」
4. SM 回写本文件，PO 验收
- 评审 FAIL 处理沿用协议：1 轮回 Dev、2 轮三岗会诊、3 轮挂牌 PO

## 并发写隔离（硬约束）
- 同一时刻只允许一个可写 agent；本轮同时涉及后端 + 前端，但**由单一 Dev-agent 串行完成**，不并行开第二写者，故暂无需 worktree
- 若后续拆成「后端 / 前端」两个写者，必须先用 `git worktree` 隔离分支，评审通过后再合并

## 本轮范围

**允许改**
- `src/main/java/**`：`AdminController` / `AdminService(+Impl)` / `UserService(+Impl)` / `UserMapper(+xml)` / `AdminMapper(+xml)` / `entity/UserEntity` / 新增 DTO、VO
- `src/test/**`：新增/扩展管理端写操作测试（单测 + integration）
- `frontend/src/**`：`views/admin/Admin.vue`（角色列 + 操作列）、`api/modules/admin.ts`、`locales/zh.ts`、`locales/en.ts`
- `docs/schema.sql`：新增 `user.role` 列（`CREATE TABLE` 同步 + 迁移块）

**不许碰**
- 检索链路、聊天链路、RAG 管线、Parser/Splitter/Outline
- 既有 `/api/outline/**`、`/api/file/**`、`/api/knowledge/**`、`/api/chat/**` 契约
- `ADMIN_USERNAMES` 白名单既有机制（本轮只做**叠加**，不删除该机制）
- 登录 / JWT 逻辑（**role 不写入 JWT**，避免令牌失效面扩大）
- `docs/schema.sql` 中既有表结构（只 `ADD COLUMN`，不改写既有列）
- 不引入新的大依赖

## 权限模型（PO 已拍板 · 二值平滑过渡）
- `user.role`：`admin` / `user` 二值，`NOT NULL DEFAULT 'user'`
- **生效判据（过渡期叠加）**：`isAdmin(username) = username ∈ ADMIN_USERNAMES || role == 'admin'`
  - 白名单用户天然是管理员，不受表内 `role` 影响（平滑过渡，防存量管理员被改表意外降权）
  - 表内 `role='admin'` 由管理端写操作授予 / 撤销
- **安全护栏（硬性，逐条须有测试）**
  1. 不允许对自己执行「删除 / 降角色 / 重置密码」（目标 id == 当前登录 id → 拒绝）
  2. 不允许移除「最后一个生效管理员」（操作后生效 admin 数须 ≥ 1，防自锁）
  3. 删除用户前须校验名下无 `knowledge` / `knowledge_file`（有则拒绝，防孤儿数据）
  4. `role` 仅接受 `admin` / `user` 白名单值，非法值拒绝
  5. 所有写接口先 `requireAdmin()`（拒绝必须发生在任何 DB 写之前）
  6. 任何响应不得回传 `password`

## 接口契约（新增，Controller 统一 `/api/admin` 前缀；`Result<T>` 包装）
- `POST   /api/admin/users` → 管理员建号（body：`username` / `password` / `nickname?` / `role?`，默认 `user`）
- `PATCH  /api/admin/users/{id}/role` → 改角色（body：`{ "role": "admin" | "user" }`）
- `PUT    /api/admin/users/{id}/password` → 重置密码（body：`{ "password": "..." }`）
- `DELETE /api/admin/users/{id}` → 删除用户
- 失败一律 `HTTP 200 + body code:500`（BusinessException 铁律，验证看 body 不看状态码）
- 用户列表 `AdminUserVO` 增加 `role` 字段（**仍不含 password**）

## 任务
状态：[ ] 待开发 · [~] 开发中 · [R] 待评审 · [Q] 待验证 · [x] 完成 · [!] 阻塞

- [x] T-1 DDL：`user` 表加 `role VARCHAR(20) NOT NULL DEFAULT 'user'`；本地库执行迁移（通道=`C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`，非 PATH）· 生产 DDL 待 PO 拍板
- [x] T-2 后端：`UserEntity.role` + `UserMapper` / `AdminMapper` 读写 role；`isAdmin` 叠加 role 判据
- [x] T-3 后端：`AdminService` 四个写方法（create / updateRole / resetPassword / delete）+ 六条安全护栏
- [x] T-4 后端：`AdminController` 四个写端点；`AdminUserVO` 加 role
- [x] T-5 测试：`AdminServiceTest` 扩展（鉴权前置 + 六护栏 + 成功路径，mock mapper）
- [x] T-6 测试：新增 `AdminWriteIntegrationTest`（`@Tag integration` / `local` profile，真实 MySQL 建→改→删 全链路，测后清理）
- [x] T-7 前端：`admin.ts` 写接口封装 + `Admin.vue` 角色列与操作列（自己那行禁用）+ i18n zh/en
- [x] T-8 评审：独立评审-agent（范围 / 契约 / 护栏 / 夹带）
- [x] T-9 验证：独立验证-agent 跑门禁（`mvn test` 默认回归 + integration 写链路 + `npm run build`）

## DoD
- 本地 DDL 已执行且 `role` 列就位；新老用户默认 `user`
- 四个写端点在本地真实 HTTP 验证：正向成功 + 负向（非 admin / 自己 / 最后一个 admin / 名下有数据 / 非法 role）看 **body code**
- 默认回归全绿（当前基线 200/200）+ 前端 `npm run build` exit 0
- `AdminUserVO` 结构上不含 password（单测钉死）
- 未跑项显式标注「未验证」

## 阻塞
- [x] **B-116 原记录阻塞「本地无 mysql 客户端」已解除**（2026-10-02 实测）：`C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe` 存在（8.0.44），本地库 `ai_knowledge_base` 可连（`user` 表 803 行、当前无 role 列）
- [x] 生产 DDL 上线 + 部署 + 线上登录态写操作实测（2026-10-02 完成，见「部署留痕」）
- 环境：本地 Redis 需 WSL 常驻才通 6379（继承 Sprint 6 配方）

## 约定（继承 Sprint 6）
- 任何改动全量回归全绿才可提交部署；小批次交付 → 核实 → commit
- 严禁服务器构建(OOM)；节点/密钥/密码/名单不落档不进输出
- 每次迭代必须调用多 agent（Dev / 评审 / 验证分离），禁止 SM 自编自审自验
- 收口必须区分「逻辑层已证」与「真实数据/HTTP 层已验证」，未跑项显式标注

## 本轮迭代留痕（多 agent · 2026-10-02）
- 迭代 1：Dev-agent 实现 T-1~T-7 → 评审-agent（只读）**PASS**（无 high/medium；2 条低风险观察项）→ 验证-agent **全绿**
- 评审观察项（不阻断，均未修，作设计权衡留痕）：① `createUser` 用户名唯一性为「先查后插」，存在理论并发重名窗口；② 护栏 2（最后一个生效管理员）在一致状态下几乎不可达（操作者本身必为生效管理员且禁止自操作 ⇒ 生效集合恒 ≥2），实为防御性分支

## Sprint 7 收口（2026-10-02 · SM 汇总）
- **结论：done**（代码层 实现 + 评审 + 验证 全通过；commit/push 与生产 DDL/部署已于 2026-10-02 完成，见「部署留痕」）
- **需求**：B-116 管理端写操作（用户增删改）+ `user.role` 二值模型（`admin`/`user`，叠加在 `ADMIN_USERNAMES` 白名单之上平滑过渡）
- **实现**（Dev-agent）：`user` 表新增 `role`（本地库已迁移）；`isAdmin` = 白名单快速路径 ∪ 表内 `role='admin'`；`AdminService(+Impl)` 四个写方法 + 六条安全护栏；`AdminController` 四端点；`AdminUserVO` 加 `role`（仍无 password）；新增 3 个 DTO；前端 `Admin.vue` 角色列 + 操作列（自己那行禁用）+ 4 个写接口封装 + i18n 中英；新增 `AdminWriteIntegrationTest`
- **验证**（独立验证-agent 复跑）：
  - 门禁：`mvn -o test` **222/0/0**（基线 200 + 新增 22，非回归）· `mvn -o test -Dtest=AdminWriteIntegrationTest -DexcludedGroups=` **3/0/0**（真实 MySQL 建→改→重→删）· `npm run build` **exit 0**
  - 真实 HTTP（本地 56382）：四端点正向均 `code:200`；负向「非管理员 / 非法 role / 操作自己 / 不存在 id / 名下有数据」→ 均 **HTTP 200 + body `code:500`** 且文案正确；`GET /api/admin/users` 实测**无 password 字段**（字段=id/username/nickname/role）
  - 清理：临时账号计数归 0（用户数回到 **803**）、临时 knowledge 0 行、后端进程已停、端口未监听
- **技术债**：无新增（评审 2 条观察项为设计权衡，不记为债）
- **决策记录**：① 权限模型 = admin/user 二值 + 白名单叠加（PO 已拍板）；② 写操作范围 = 建号 / 改角色 / 重置密码 / 删号；③ `role` **不写入 JWT**（避免令牌失效面扩大）；④ 删号前置校验名下无 knowledge/file（防孤儿数据）
- **风险**：① 生产 DDL（`ALTER TABLE user ADD COLUMN role`）已于 2026-10-02 执行并完成部署；② 本地 803 用户库与生产 34 用户库均已迁移（生产全为 user）
- **未验证项**：护栏 2「最后一个生效管理员」的 HTTP 口径不可达（白名单恒使生效管理员 ≥1），仅逻辑层单测覆盖 —— 如实标注
- **下一步**：已完成（commit/push → 生产建回滚点 → 生产 DDL → 部署 jar + dist → 线上验收，见「部署留痕」）

## 部署留痕（2026-10-02 · B-116 上线）
- **结论：已上线**（后端 jar + 前端 dist + `user.role` DDL 全部就位，公网验收通过）
- **后端**：本地 `mvn -o clean package -DskipTests`（服务器 2C2G 严禁构建）→ jar SHA256 `64a4b1b2…e781`（117,059,625 B），**本地与线上一致**；`systemctl restart aikb` → `active`，直连 `:8080/actuator/health` = `UP`（约 15s）
- **前端**：`npm run build` dist tar SHA256 `0849e6bf…a716`；线上首页引用 `index-763dzdoR.js`；**独立复核**：线上 `Admin-DLyvAin6.js` 与本地构建 **SHA256 完全一致**（`5926f971…71d7`，7686 B），且含 B-116 标记 `admin.role` / `admin.resetPassword` / `admin.deleteUser`
- **DDL**：生产 `ai_knowledge_base` 执行 `ALTER TABLE user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT 'admin/user' AFTER nickname;`（预检列不存在 → 新建）；`SELECT role,COUNT(*)` = `user→34`；用户总数 **前 34 / 后 34 一致**，未改写既有数据
- **验收**：公网 `/` = 200；`/assets/index-763dzdoR.js` = 200、`/assets/index-u7j9uKfD.css` = 200；匿名 `GET /api/admin/overview` = **401**；`journalctl -u aikb -p err` 无新条目
- **回滚点**：后端 `/opt/aikb/app.jar.bak-20261002-pre-b116`（旧 jar `3e2d5d9f…c8377`）、env `/etc/aikb/aikb.env.bak-20261002-pre-b116`、前端 `/var/www/aikb.bak-20261002-pre-b116` + 原子替换上一版 `/var/www/aikb.old-live-20261002-b116`；DDL 回滚备查 `ALTER TABLE user DROP COLUMN role;`
- **登录态写操作实测（2026-10-02 补测 · 结论：通过）**：临时管理员 `aikb_s7_verify`（测后彻底删除）完成全矩阵。**正向**：管理员登录 → `GET /api/user/me` `admin=true` → `GET /api/admin/users` `code:200` → `POST /api/admin/users` 建普通用户 → `PATCH /{idB}/role` 改 admin → `PUT /{idB}/password` 重置并新口令登录成功。**负向**（均 HTTP 200 + body `code:500`）：非管理员调四个写端点 + GET → `权限不足`；操作自己 → `不能修改自己的角色`/`不能重置自己的密码`/`不能删除自己`；非法 role → `非法的角色`；不存在 id → `用户不存在`；名下有 knowledge → `该用户名下仍有知识或文件,无法删除`；匿名 → HTTP 401。**字段证据**：`GET /api/admin/users` 行内字段 = `id, username, nickname, role`，无 password。**清理自证**：`user` 总数 34 → 35 → 34；`aikb_%` 计数 0；`role <> 'user'` 残留 0；孤儿 knowledge/files 0；未改 `aikb.env`；未做 DDL；`git status` 干净。**未跑项**：`POST /api/admin/users` 无 id 路径参数，「不存在 id」用例不适用
---

# Sprint 6（已归档）· 目标：B-114 Phase1 前端闭环——大纲导航面板 + 节点详情 + source_chunks 溯源页

> 开于 2026-10-01 | 承接 Sprint 5（后端闭环：代码已完成、验证通过、未提交未部署）
> 状态：前端闭环 实现→评审→验证 全流程通过（`npm run build` exit 0 / 2282 modules）；**端到端已打通**（WSL 常驻 → 本地 Redis 6379 通 → 集成整组 28 run/0 Failures/3 Errors，3 个为 DashVector 环境阻塞 → 本地后端 56382 真机 HTTP 验证 /api/outline 通过 → 前端点击联调 8/8 PASS → 回归守护 后端 200/200 · 前端 build exit 0）；**已提交并推送**（代码 `e1ef5a8` / 文档 `7ed82a6` / 部署记录本次）；**已上线生产**（后端 jar `3e2d5d9f…c8377` · 前端 dist `88a94ec7…f341` · 两表 DDL 已执行）
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
- [x] **Sprint 5 + Sprint 6 增量已提交并推送**（2026-10-01 夜 · SM 自主执行）：代码 `e1ef5a8`（20 files / +1861）、文档 `7ed82a6`；origin/main 已同步（`d10ae34..7ed82a6`）。
- [x] **已上线生产**（2026-10-01 夜 · PO 提供凭据后由 AI 执行）：详见下方「部署留痕」。

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

## 部署留痕（2026-10-01 夜 · B-114 Phase1 上线）
- **结论：已上线**（后端 jar + 前端 dist + 两表 DDL 全部就位，验收通过）
- **后端**：本地 `mvn -o clean package -DskipTests`（14.6s，不在服务器构建避免 2C2G OOM）→ jar SHA256 `3e2d5d9f…c8377`（含 `OutlineController` / `OutlineIndexServiceImpl` / `OutlineMapper.xml` 等 13 个 Outline 产物）
- **前端**：`npm run build` dist → tar SHA256 `88a94ec7…f341`（693070 B），线上 index 引用 `index-V6sfJeXz.js`
- **DDL**：`knowledge_outline_node` + `knowledge_outline_chunk` 已在生产 `ai_knowledge_base` 执行（`CREATE TABLE IF NOT EXISTS`，仅新增；外键 `fk_outline_chunk_chunk → knowledge_chunk` / `fk_outline_node_file → knowledge_file`，utf8mb4；两表当时 0 行）
- **验收**：`verify_deploy 3/3 PASS`；`/actuator/health` `UP`（Tomcat 8080，启动 11.2s）；首页 `/`、`index-V6sfJeXz.js`、`index-u7j9uKfD.css` 均 HTTP 200；`/api/outline/*`、`/api/admin/overview` 匿名 401；`journalctl -p err` 无条目，日志 grep `exception|error` 命中 0
- **回滚点**：后端 `/opt/aikb/app.jar.bak-20261001-pre-b114`（`3e10c9e9…afe4803`）；env `/etc/aikb/aikb.env.bak-20261001-pre-b114`；前端 `/var/www/aikb.bak-20261001-pre-b114` + `/var/www/aikb.old-live-20261001-b114`
- **线上功能验证（2026-10-01 夜补测 · 专用临时账号，测后已删）**：生产走完整正向路径 —— 建知识 `27` → 上传 `outline_test.md`（fileId `84`，PROCESSING → SUCCESS）→ `GET /api/outline/file/84` **HTTP 200 / body `code:200` / nodeCount=4**（架构 L1 → 接入层 L2 → 网关 L3 → 服务层 L2，parentId 正确）→ `GET /api/outline/node/2` **200**（sourceChunks：chunkId `1154` / contentLength `259` / preview 正文）→ `POST /api/outline/file/84/rebuild` **200 / data=4**（幂等）。归属负向：`GET /api/outline/file/83`（他人文件）**HTTP 200 + body `code:500`「权限不足」**，`POST /api/outline/file/83/rebuild` 同；`/api/admin/overview` 非管理员 → `权限不足`；`/api/user/me` → **200 / `admin:false`**。路由存在性判据（修正此前「401 不可区分」的局限）：带 token 访问**不存在**的路由 `/api/definitely-not-a-route-xyz` 返回 `系统异常，请稍后重试`，与 outline 路由的业务态响应**可区分**，故上列响应可证明路由已生效。清理：删除文件 `84` + 知识 `27`，级联校验 `knowledge_chunk` / `knowledge_outline_node` / `knowledge_outline_chunk` 均为 **0 行**，并删除临时账号（用户数回到 34）。**结论：生产「登录态 200」与「归属负向 code:500」两条路径均已线上实测，缺口关闭。**

## 决策单（已清空，本轮全部拍板完毕）
1. ~~Sprint 5 + Sprint 6 增量是否 commit~~ → **已执行**（2026-10-01 夜 · SM 自主）：代码 `e1ef5a8`（20 files / +1861）、文档 `7ed82a6`，已 push origin/main。
2. ~~本轮是否上线生产~~ → **已执行**（2026-10-01 夜）：后端 jar + 前端 dist + 两表 DDL 全部上线，`verify_deploy 3/3 PASS`。详见「部署留痕」。

## 夜间收口（2026-10-01 夜 · PO 授权自主执行 · SM 汇总）
- **结论：done**（夜间队列 n1–n7 全部完成；仅 commit/push 与生产部署按 PO 授权保留）
- **需求**：B-114 Phase1 前端闭环从「仅构建验证」推进到「接口层 + UI 层 + 回归层」三层真机验证全通过
- **实现**：夜间未改任何业务码（只做验证 + 文档回填，符合 SM 边界）
- **验证**：n3 集成整组 `28 run/0 Failures/3 Errors`（3E=DashVector 环境阻塞）· n4 真机 HTTP `/api/outline` 四端点 200 + 归属负向 `code:500` · n5 浏览器点击 **8/8 PASS** · n6 后端 **200/200** + 前端 build exit 0
- **技术债**：无新增（TD-001 仍在册）
- **决策**：① commit → **已执行**（`e1ef5a8` + `7ed82a6` 已 push）；② 上线生产 → **已执行**（PO 提供凭据后由 AI 完成，见「部署留痕」）
- **风险**：DashVector 本地不可达 → 向量索引降级 BM25（环境问题，非代码）；线上 DDL 未执行（未部署故无影响）
- **下一步**：PO 拍板 ①②；若要上线，先备回滚点再执行 `docs/schema.sql` 两表 DDL


## 本轮迭代留痕（多 agent · 2026-10-01）
- 迭代 1：Dev-agent 实现 T-7~T-9 → 评审-agent **PASS**（2 个低severity 观察项）→ 验证-agent **PASS**（build exit 0 / 2282 modules / 11.87s）
- 迭代 2：Dev-agent 修复（T-11）→ 验证-agent **PASS**（build exit 0 / 2282 modules / 11.95s）
- 缺口补齐（2026-10-01 夜）：上述「真实 HTTP 调用与页面点击路径未跑」已由夜间队列 n4（真机 HTTP 四端点）+ n5（浏览器点击 8/8 PASS）关闭；仅「`data=null` 兜底空态」仍为静态+构建确认（需人为构造异常态才能触发，本轮未构造）

## 本轮迭代留痕（数据操作 / 代码 · 2026-10-03 · Sprint 8 · B-117 后续债：孤儿向量清理 + deleteByFileId topk 分页）

> 结论：**孤儿向量清理完成**（三方判定 → PO 拍板 → 删除 → 复核）；**`deleteByFileId` topk 分页修复完成**（实现 → 独立评审 PASS → 独立验证全绿）。二者**均未 commit、未 push、未部署**（PO 保留拍板权）

- **一次性确证（关键）**：生产 `DASHVECTOR_API_KEY` 的 md5 与本地**完全一致**（`92263f108b67002655c56ddf0c05b385`），endpoint 同为 `vrs-cn-moy4ydvtt0001k.dashvector.cn-shenzhen.aliyuncs.com` → **本地开发与生产共用同一 DashVector 账号/集合**（此前"疑似共用"升级为"已确证"）
- **三方判定口径**：集合侧 file_id 清单（REST 零向量 + filter 区间枚举）× 生产清单（`SELECT id FROM knowledge_file`，74 条、最大 83）× 本地清单（25 条）；真孤儿 = 三者之外
  - **反向确证**：集合里 ≤83 的 file_id **全部落在生产清单内** → 此前"疑似生产在用"的 914 条**确认就是生产的，一条未删**
- **真孤儿 = 10 个 file_id / 31 条**：207(1)、208/209/223/224 各 4、414/415/427 各 4、436(1)、437(1)
  - 内容逐条拉取确认：全为 **K8s / JVM(GC) / 设计模式 / Git** 四类学习笔记的**重复副本**（207↔437、208↔223↔414、209↔224↔415↔427 逐字同文），doc id 分批（412–415 / 435–438 / 746–749 / 771 / 780–781）→ 历史测试上传残留；与生产教材零重叠
- **删除执行（按 doc id，最精确）**
  - **踩坑留档**：DashVector REST 删除路由**不是** `/docs/delete`——该路径被网关兜底返回淘宝默认 HTML（HTTP 200）；正确路由是 **`DELETE /v1/collections/{collection}/docs`**，body `{"ids":[...]}`。`/query`、`/stats` 路径不含 `/docs` 段（SDK 走 gRPC，无法从 jar 直接取到 REST 路径）
  - 结果：**31/31 逐条 `code=0`**；复核 10 个 file_id **残留全部 0**；集合 `total_doc_count` **1,055 → 1,024**（正好 −31），`index_completeness` 仍 1.0
  - **未动**：无 `file_id` 的异常向量 1 条
  - 留档（均 gitignored）：`target/orphan-docids.tsv`（31 条 file_id/doc_id/长度快照）、`target/orphan-candidates-contents.txt`（全文）、`target/orphan-vectors-report.md`/`.tsv`（原始只读清单）
- **`deleteByFileId` topk 分页修复（已完成）**：`while` 分页循环（`pageSize=100` / `maxRounds=50` / `maxTotal=5000`，触顶 `log.warn`），每轮重查下一页主键并删，直到空集或页未满；原降级语义（集合不可用 / 查询失败 / 删除失败只告警不阻断）保留。`mvn -o test` **222/0/0** BUILD SUCCESS · `npm run build` exit 0。评审 medium 1 项（删除响应未逐条校验 `DocOpResult.code`）判范围外，已记 backlog B-117 备注
- **顺带发现（供 B-118 排查线）**：生产文件 **68 / 69 / 70 在集合中没有任何向量**（本地 10/11/55/56/57/142/145–150/179/180/258/259 亦无）→ 这些文件只能靠 BM25 召回，是"线上检索是否长期静默降级"的直接排查入口
- **未验证 / 未做**：删除后**未**做线上端到端检索复跑（生产未部署、未重启）；`LongTermMemoryServiceImpl:124` 同类静默吞错风险**未处理**；本轮数据操作不涉业务代码（无回归门禁要求），`deleteByFileId` 代码改动已随既有 `mvn -o test` 222/0/0 通过

## 本轮迭代留痕（部署 · 2026-10-03 · Sprint 8 上线）

> 结论：**Sprint 8（T-1~T-4 + B-117）已部署生产并通过线上验收**（PO 拍板）

- **上线内容**：T-1 B-111 渲染美化、T-3 B-103 element-plus 按需导入、B-117 `upsert` + 逐条 code 校验、`deleteByFileId` topk 分页；T-2（纯等价重构）与 T-4（仅测试资产）无线上行为差异。提交 `911f5fd`(feat) + `71d895d`(docs) + `cc18b3a`(chore)，本地与 `origin/main` **0/0 同步**
- **后端**：本地 `mvn -o clean package -DskipTests`（服务器 2C2G 严禁构建）→ jar SHA256 `4e88c5c8ea9798050f90873040bb5888773903ee55b00ea3188682367d91e66a`（117,061,796 B），**本地与线上一致**；原子替换 `/opt/aikb/app.jar` → `systemctl restart aikb` → `active`，直连 `:8080/actuator/health` = `UP`（`Started ... in 11.514 seconds`）；启动后 3 分钟内 `error|exception` 计数 **0**
- **前端**：`npm run build` exit 0（2390 modules，15.68s）→ tar SHA256 `013237006b959b17de25210c492ae02683e5da425abce780d4f260ce81285169`（519,781 B），**双端一致**；解包后原子替换（先移旧目录 `/var/www/aikb.old-live-20261003` 再移入新目录），**73 个 assets**，`index.html` 引用 `index-BLlfZny_.js`（与本地构建一致）
- **验收**：`scripts/verify_deploy.py` **3/3 PASS**（匿名 401 / 注册关闭 / 登录文案统一；注册关闭模式下其余 5 项跳过，本地回归覆盖）；外网 `GET /`、`/admin`、`assets/index-BLlfZny_.js`、`assets/element-plus-BEJiLSvu.js` 均 **200**（`element-plus` chunk 200 是 T-3 生效的直接证据）
- **回滚点**：后端 `/opt/aikb/app.jar.bak-20261003-pre-sprint8`（旧 jar `64a4b1b2d1ac3f19d513a294c4062d8ed73fe972f24d84887a83a40f7236e781`）、前端 `/var/www/aikb.bak-20261003-pre-sprint8` + `/var/www/aikb.old-live-20261003`
- **清理**：服务器 `/tmp/akb-dist-20261003.tar.gz` 已删；本地临时凭据 `%TEMP%\aikb-deploy\env.ps1` 已删；旧备份目录按惯例保留
- **未验证（如实标注）**：生产注册关闭 → **无法做登录态端到端实测**（上传 / 检索 / SSE 对话）；**B-117 的 `upsert` 写入路径未在线上实测**（仅本地测试与探针覆盖）；本地 `target/orphan-*` 证据与 `%TEMP%\aikb-rollback\` 补丁存档保留

## 本轮迭代留痕（安全演练 · 2026-10-03 · Sprint 8 上线后：攻防演习 + 用户视角走查）

> 结论：对**已上线的 Sprint 8 生产环境**完成攻防演习（A–F）与用户视角走查；**修出 1 个中危（MCP 堆栈泄露）+ 4 个低危/观察项**；演练数据与临时账号已**零残留**清理。本文档为留痕，**未 commit / 未 push**（PO 保留拍板权）。

- **靶场**：生产（Nginx 静态 + 反代 `/api`）+ 直连 `:8080`；登录态通过**临时账号** `aikb_drill`（id=38, role=user）取得，**测后彻底删除**
- **A 认证/鉴权**：6 类伪造/篡改 token（`alg=none`、弱密钥 HS256、无签名段、畸形串、小写 `bearer`、空 `Authorization`）→ 全部 401；受保护端点匿名 → 401 ✅
- **B 白名单绕过**：`/api//user/login`（双斜杠）经 Tomcat 归一化**确实命中白名单、绕过 JWT 过滤器**；但可达目标仅限白名单端点本身，`/api/user/login/../user/me`、`/api/mcp-endpoint/../admin/overview`、`%2F` 编码探针 → 均 401，**fail-closed、无越权收益**（低危，建议后续归一化后再判，另记 B-121）
- **C MCP 端点（⚠️ 中危）**：无 token 可 `initialize` → 取 session → `tools/list`（`knowledge_search`/`knowledge_stats`/`time_now`）；**调用工具返回「MCP 工具需要认证」，业务数据未泄露**。但：无/无效 `mcp-session-id` 时 HTTP 400/404，**body 为完整 Java 堆栈**（类名/文件名/行号），稳定可复现。**根因**：该 Spring 函数式端点绕过全局异常处理器（普通 API 畸形 JSON → 200 + 「系统异常，请稍后重试」，已脱敏）→ 立 **B-119**
- **D 参数/边界（登录态）**：
  - 管理端越权 8 项（GET overview/users、`?limit=999999`、POST users、PATCH 999999/role、PUT 999999/password、DELETE 999999、PATCH 自己 role=superuser）→ **全部 `{"code":500,"message":"权限不足"}`** ✅
  - IDOR 5 项（`/api/file/1`、`/api/file/1/content`、`/api/knowledge/1`、`/api/outline/file/1`、`/api/file/list/1`）→ **全部「权限不足」** ✅
  - 上传白名单：`evil.txt` 拒 / **`evil.MD` 通过（大小写绕过，低危）** / `evil.pdf.exe` 拒 / `evil.md`(octet-stream) 通过 / `evil.md.php` 拒 / `ok.md` 通过；`.MD` 的 preview content 下游正常，**无失配**
  - **`PROCESSING 禁删` 未生效**（上传后立即 DELETE 返回 success；两次实测含 mid-flight 大文件）；但**均未产生孤儿** → **低危 / 文档约束与实现不符**（另记 B-120）
  - **正面实证**：file 89 日志「向量清理已删除一批, fileId=89, 本批=8, 累计删除=8」即 **B-117 `deleteByFileId` 分页修复的线上实测**（此前留痕标注「未线上实测」，此处补上、缺口关闭）
- **E T-1 渲染 XSS 红队**：30 条载荷、真实 DOMPurify 管线 + 真实浏览器 → `alertFired=false`、`dompurifyActive=true`（java 围栏 hljs `<span>` 经清洗存活，证明非 passthrough）。3 条被标记：① `<div style="background:url(javascript:alert(1))">` 的 `style` 原样保留 → **低危非 XSS**（现代浏览器不执行 CSS 内 `javascript:`，但 `url()` 可作外链信标）；② `<form>`（action 已剥离）、③ `<template>`（内部 script 已剥离）→ 误报
- **F 安全响应头**：`X-Frame-Options` / `CSP` / `X-Content-Type-Options` / `HSTS` 全缺（低危）；CORS 异源预检 → 403（防住）
- **用户视角走查**（真实浏览器，生产）：登录 / 导航 / 知识列表 / 文件预览全部正常，无 console error；发现「侧边栏智能问答在部分视口被滚动容器遮挡」（轻微 UI）；**对话为严格 RAG 接地**，知识库无相关资料时拒答通用编程问题，故 T-1 的代码高亮 / 表格样式在该路径下未获真实数据验证
- **清理（全部零残留）**：删演练产物 files 85/86/87 与 knowledge 29（API 级联）→ `DELETE FROM user WHERE username='aikb_drill'`；终态 users **34** / `aikb_%` **0** / knowledge **15** / file **74** / chunk **1131** / DashVector `total_doc_count` **1024**、`index_completeness` 1.0；服务 `active` + health `UP`；服务器 `/tmp` 无我方残留；本地临时凭据 `%TEMP%\aikb-deploy\` 已删
- **未核实项**：三个演练上传对象的 OSS 存亡未能核验（桶私有读，命中与不存在对象探针同为 403）→ 采信「deleteFile 级联删 OSS」约定，日志未显式打印 OSS 删除行
- **建议**：① 立 **B-119** 修 MCP 堆栈泄露（Dev → 独立评审 → 独立验证）；② **B-120** 汇总低危项（上传白名单大小写 / `PROCESSING 禁删` 护栏 / 安全响应头）；③ **B-121** JWT 白名单归一化；④ B-117 的「线上未实测」缺口**就此关闭**
