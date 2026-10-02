# Sprint 8 · 目标：低成本收尾包 + 检索质量 eval 底座（B-114 Phase2 的前置）

> 开于 2026-10-02 | 承接 Sprint 7（B-116 已上线并完成线上登录态实测，缺口关闭）
> 状态：**进行中——T-1（B-111）已完成实现+评审+验证，待 PO 验收后进 T-2**（PO 2026-10-02 拍板：一步步来）
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
- [ ] T-2 B-113 轻量去重：`streamAsk` / `streamAskWithAgent` 开头 5 行（检索+历史+记忆+拼 Prompt）抽私有方法，**行为零变化**
- [ ] T-3 B-103 element-plus 按需导入（unplugin-vue-components + unplugin-auto-import），记录构建产物体积前后
- [ ] T-4 检索质量 eval 底座：标注 query→chunk 集 + recall@5 / MRR 指标 + **无树基线数字留档**（阈值 recall@5 ≥ 0.80 / MRR ≥ 0.70）
- [~] T-5 评审：独立评审-agent（只读，审范围 / 行为等价性 / 夹带）· T-1 轮**已 PASS（2026-10-02，无 high/medium）**；T-2~T-4 待续
- [~] T-6 验证：独立验证-agent 跑门禁（`mvn -o test` 全量回归 + `npm run build` + eval 基线输出）· T-1 轮**已全绿（2026-10-02：222/0/0 + build exit 0）**；T-4 eval 基线待续

## DoD
- 全量回归保持全绿（当前基线 222/0/0），前端 `npm run build` exit 0
- B-111：代码块有高亮、表格/引用/标题有增强排版，DOMPurify 白名单**零改动**
- B-113：去重前后同一测试集输出一致，无流式行为差异
- B-103：构建产物体积有前后对比数字
- T-4：无树基线 recall@5 / MRR 有确切数字并落档，作为 Sprint 9 对比基准
- 未跑项显式标注「未验证」

## 阻塞
- 无（本地 Redis 需 WSL 常驻才通 6379，继承 Sprint 6/7 配方；mysql.exe 在 `C:\Program Files\MySQL\MySQL Server 8.0\bin\`，不在 PATH）
- 观察期：B-116 生产回滚产物按 PO「先观察几天」保留中，清理需 PO 另行确认

## 下一步预告（Sprint 9）
- B-114 Phase2：标题树挂载到混合检索做路由加权（树只提权，不动 hybrid 底座；失败降级回纯 RAG 并打 WARN）→ 上线门槛 = 有树 vs 无树对比 + Sprint 8 基线指标

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
