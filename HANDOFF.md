# AI-Knowledge-Base 项目交接文档

> ⚠️ **git 历史已重写(9/18)**: 为切 Public 做 PII 清理,git-filter-repo 全历史将服务器 IP(明文已隐去)替换为 `YOUR_SERVER_IP`(唯一敏感项;API key/密码/密钥扫描确认为 0 泄漏,properties 全环境变量占位)。**此前文档中引用的所有 commit hash 均已作废**(内容等价,新 hash 顺延,如 163694d→83ebf7f / c18896e→eff84e9);tag v0.1.0 与 open-source-narrative 分支已同步 force update;纯净重写前备份仅本地留存。**Public 已生效(9/21 晚 gh 实测 visibility=PUBLIC)**

> 更新: 2026-09-21 晚 | 线上前端 = `90b7a57` 构建(**知识树/图谱 + B-112 文件在线预览——点击文件节点看原文**,dist SHA256 `38fe3605…5cfd` 双端一致) + 后端 = `90b7a57`(B-112 /api/file/{id}/content,jar SHA256 `12e7adc3…4e17e` 双端一致) | 测试: **9/21 三组全绿 199(168+21+10)**——默认回归 162+FileContentTest 6=168/168,integration 21/21(B-112 改动后复跑亦绿),e2e 10/10 | 线上验证: verify_deploy 3/3 PASS,登录态实测 /api/file content 4/4(md 原文/越权拒/不存在报错/匿名 401) | 生产 `long_term_memory` 1024 维已验证恢复(9/15) | 文档收尾: **B-113 聊天管线合并已立 backlog**(`0e2a9a5`,普通 RAG vs Agent 双路径三差异与合并方案记录在 docs/backlog.md,PO 拍板暂不动码) | **仓库已切 Public(9/21 晚 gh 实测确认)**
> 更新: 2026-09-27 | **代码保持 `90b7a57` 未动**(线上 jar/dist 均此构建);main HEAD = `2a04c7d`(docs-only: `c20bc09` 立 B-114 知识树+检索索引 → `cd8cf7b` 措辞统一 → `2a04c7d` B-114 依据修正,详见 docs/backlog.md) | 测试/线上状态同 9/21 晚记录(199 三组全绿口径不变) | 下一开发窗口: 管理端(课设硬需求,10 月上旬)→ B-114 Phase1(10 月中)
> 更新: 2026-09-28 | **前端已发布 `3b1f77f` 构建**（知识树/网图第二轮适配：树刷新与空态、操作提示、ECharts aria、响应式布局；dist tar SHA256 `5b1dcc504c2acfc3bebab3544a4ef4cb06cad9fb28003a29972713dc933e6906`）;线上后端仍为 `90b7a57` | 前端构建 `vue-tsc + Vite` 2274 modules 通过（仅 Sass/Rollup/大 chunk 非阻断警告）| 部署验收 `verify_deploy 3/3 PASS`;首页及实际引用资源 HTTP 200 | 前端回滚点 `/var/www/aikb.bak-20260928-1725-3b1f77f` | 代码提交 `b4f74b8`（第一轮）→ `3b1f77f`（第二轮）;未执行后端构建/部署 | 下一开发窗口仍为管理端(课设硬需求,10 月上旬)→ B-114 Phase1(10 月中)
> 更新: 2026-09-28 17:45 | **前端已发布 `f90729a` 构建**（视觉层级修正：树/图状态标签本地化、文件图标辅助色、网图未选节点时铺满、选中后展示详情栏、结构边提高对比度；dist tar SHA256 `b42017f53512e741a5d4dcb3dff8eb8789c2ec9e08640a04e3c856b034c59458`）;线上后端仍为 `90b7a57` | 前端构建 `vue-tsc + Vite` 2274 modules 通过（仅 Sass/Rollup/大 chunk 非阻断警告）| 部署验收 `verify_deploy 3/3 PASS`;首页及实际引用 JS/CSS HTTP 200;后端 `aikb active`/health `UP` | 前端回滚点 `/var/www/aikb.bak-20260928-1745-f90729a` | 代码提交 `b4f74b8`→`3b1f77f`→`f90729a`;未执行后端构建/部署 | 下一开发窗口仍为管理端(课设硬需求,10 月上旬)→ B-114 Phase1(10 月中)
> 更正: 2026-09-28 18:02 | 首次发布命令因目标目录已存在，产物被放入 `/var/www/aikb/aikb.new-f90729a`，公网首页仍短暂指向 9/21 旧 dist；已于 18:02 将旧根目录保留为 `/var/www/aikb.old-live-20260928-1801-f90729a`，把新 dist 提升为实际 `/var/www/aikb` 根目录。更正后线上首页引用 `index-C7aYqfWw.js`、Tree/GraphPanel 新资源均 HTTP 200，后端健康 `UP`，`verify_deploy 3/3 PASS`。后续前端原子替换必须先移走旧 `/var/www/aikb`，再将新目录移入，不能直接 `mv new existing-dir`。
> 更新: 2026-09-29 | **知识图→知识树导航闭环已发布**：知识图选中知识节点后“进入知识树”，跳转 `/tree?knowledgeId=<id>`；知识树加载后读取 query，定位并展开对应知识条目；文件节点原文预览保持；独立 `/graph` 路由和树/图分离导航保持。提交 `8424fb8`；前端 build 2274 modules 通过，后端离线全量回归 `168/168` 通过，`git diff --check` 通过；线上 dist tar SHA256 `a33c3066522d958f7e0fa8a517b918e48b1679827a72e7e20994527b08a6de92`；`aikb active`/health `UP`；`verify_deploy 3/3 PASS`；首页及新 Tree/Graph/Layout 资源 HTTP 200；前端回滚点 `/var/www/aikb.bak-20260929-1956-8424fb8`。后端未重新构建/部署；未包含 B-114 标题层级树、后端/API/数据库改动。
> 更新: 2026-09-29 | **可靠性修复已发布**：`FilePreview.vue` 保存 activeFileId，重试不再请求 `/file/undefined/content`；`Tree.vue` 加载失败显示显式失败节点和重试入口，真实空态与失败态分离；树提示文案与点击行为一致；无效 `knowledgeId` 给出反馈。提交 `8647665`；前端 build 2274 modules 通过；后端离线全量回归 `168/168` 通过；`git diff --check` 通过；dist tar SHA256 `78a75170225c585cf4687fa87e5f65d4675526c939d9172046a38ce0c2a204ea`；线上 `aikb active`/health `UP`；`verify_deploy 3/3 PASS`；首页新资源引用 `index-e867MoKu.js`；前端回滚点 `/var/www/aikb.bak-20260929-2047-8647665`；未改后端/API/数据库。
> 更新: 2026-09-30 | **异步请求竞态保护已完成（未部署）**：FilePreview 增加 requestSeq，旧文件请求不能覆盖当前文件；GraphPanel 增加 requestSeq 和刷新按钮 loading 禁用，旧图请求不能覆盖新结果。提交 `c767e73`；前端 build 2274 modules 通过；后端离线全量回归 `168/168` 通过；diff check 通过；未改后端/API/数据库，未部署。下一步待确认后发布。
> 更新: 2026-09-30 | **树分支局部失败重试已完成（未部署）**：文件列表加载失败节点携带对应 `knowledgeId`，点击重试只重置并重新加载该知识节点，其他已展开分支不再被整树刷新折叠；根节点失败继续使用整树刷新兜底。提交 `16af5c1`；前端 build 2274 modules 通过；后端离线全量回归 `168/168` 通过；diff check 通过；未改后端/API/数据库，未部署。图谱重复加载和大 Markdown 渲染暂缓。
> 更新: 2026-09-30 | **OCP 扩展点探针完成（未部署）**：新增纯内存 `MarkdownOutlineParser` 和 `MarkdownOutlineParserTest`，输出标题 level/title/headingPath/source offsets；忽略代码围栏伪标题，支持闭合标题标记、CRLF 和无标题文本。未修改旧 `DocumentSplitter.split()`、数据库、索引链路、REST 或前端；专项测试 4/4、后端全量回归 172/172、diff check 通过。提交 `d951b8e`；后续可基于该探针设计 B-114 outline API。
> 更新: 2026-09-30 | **Markdown 围栏解析修复完成（未部署）**：收紧 `MarkdownOutlineParser` 围栏规则，闭围栏后带语言标记、混合围栏标记不再提前结束代码块；专项测试 `6/6`，后端全量回归 `174/174`，diff check 通过。提交 `c2f7023`；未修改旧 splitter、ParserFactory、FileService、数据库、REST 或前端。
> 更新: 2026-09-30 | **Markdown 换行兼容修复完成（未部署）**：`MarkdownOutlineParser` 现在支持 LF、CRLF 和单独 CR 换行，保留 Java UTF-16 偏移语义；新增 1 个专项测试，专项 `7/7`，默认 Maven 回归 `175/175`（排除 integration/e2e），diff check 通过。提交 `7c82402`；未修改旧 splitter、ParserFactory、FileService、数据库、REST 或前端。integration/e2e 未因本探针重新执行；探针已提交本地 main，尚未 push 到 origin，也未部署线上。
> 更新: 2026-09-30 | **StructureAwareSplitter 围栏兼容修复完成（未部署）**：在不改变 `DocumentSplitter` 接口、普通标题/段落/句子/硬切规则的前提下，新增与 OutlineParser 对齐的 fenced code 状态；代码围栏内伪标题不再开启章节，支持反引号/波浪号、未闭合围栏、非法闭合后缀和混合标记边界。切片专项 `10/10`，默认 Maven 回归 `178/178`（排除 integration/e2e），diff check 通过；回滚标签 `pre-structure-splitter-fence-fix-20260930`；代码提交 `933c31f`，未接入 B-114、未部署。
> 更新: 2026-09-30 | **StructureAwareSplitter 换行稳定性修复完成（未部署）**：新增统一 LF/CRLF/CR 逐行拆分，切片输出仍统一使用 LF，保留标题/段落/句子/硬切行为；专项 `11/11`，默认 Maven 回归 `179/179`（排除 integration/e2e），diff check 通过；回滚基线 `pre-structure-splitter-line-ending-fix-20260930`；代码提交 `d835b6e`，未接入 B-114、未部署。
> 更新: 2026-09-30 | **聊天模型已切换为 DeepSeek V4.1 Flash（未部署）**：根据 DeepSeek 官方 API 文档，使用官方模型 ID `deepseek-flash`（该 ID 当前路由到 V4.1 Flash；旧 `deepseek-v4-flash` 为兼容路由）。已同步 Spring AI chat 配置、TokenCostProperties 配置键、配额测试和评估数据集；Embedding 仍为 `text-embedding-v3`，Rerank 不变。专项模型/配额测试 `16/16`，默认 Maven 回归 `179/179`，diff check 通过；回滚标签 `pre-deepseek-v41-flash-20260930`；代码提交 `800c027`；未部署。
> 更新: 2026-09-30 | **回填一致性二次探针仍阻断**：代码围栏/波浪号/CR 样例已与 OutlineParser 对齐；但多级标题样例 `# 架构\n\n## 接入层...` 显示旧 `StructureAwareSplitter` 产出 2 个 chunk、OutlineParser 产出 3 个标题，且无正文的父标题没有进入后续 chunk 上下文。当前只能做内存探针，不能宣称与真实库内 `knowledge_chunk` 一致；本地无 mysql CLI/pymysql，未访问数据库/OSS。下一步需先定义“无正文父标题是否进入 Chunk 上下文”的兼容语义，再单独修复并回归，仍不接入 B-114。
> 更新: 2026-09-30 | **耦合度评估完成，暂不新增代码**：实际已降低的耦合包括 DocumentService 对 Parser/Splitter/Indexing 抽象的依赖、OutlineParser 独立探针、Tree/Graph 页面拆分和请求竞态隔离；仍存在的热点是 FileServiceImpl 协作者过多、Splitter/OutlineParser 标题规则重复、Graph 节点 ID/路由协议泄漏。当前最小高价值下一步是先完成真实语料只读对齐；在真实数据通道具备前，不为拆 Service 或统一解析器而重构。
> 更新: 2026-09-30 | **B-114 父标题语义已定：采用 B 方案**：保持现有 Chunk 正文、数量、顺序、chunkIndex、BM25/向量语义不变；所有合法标题可作为 Outline 导航节点；无正文父标题不制造空 Chunk，`source_chunks` 只关联真实正文 Chunk，并可聚合后代 Chunk。禁止把父路径直接拼进旧 Chunk，禁止按标题文本单独匹配，禁止在旧 topK 后再做节点过滤；后续先做只读真实语料对齐，再设计独立 outline API/关联模型。

## 0. 当前演进原则（持续维护）

本项目进入“逐步优化、先验证再扩展”阶段，所有后续改动遵守以下边界：

1. **开闭原则**：优先新增独立实现或能力，不直接改坏已稳定的 `DocumentParser`、`DocumentSplitter.split()`、`IndexingService`、`RetrievalService`、现有 REST 契约和前端 `FilePreview.open()` 契约。新能力先以探针、适配层或新增 API 验证，再决定是否接入主链路。
2. **合成复用原则**：通过依赖注入和小能力组合流程，不用继承扩展核心服务；解析、切分、持久化、索引、检索、预览和图谱保持独立边界，禁止新增万能 `BaseNode`、`DocumentProcessor` 或大而全 Service。
3. **接口隔离原则**：调用方只依赖所需能力。`Parser`、`Splitter`、`Indexing`、`Retrieval` 等小接口保持稳定；`FileService` 虽偏胖，暂不为抽象而抽象，只有在新增真实用例时按查询/命令/预览逐步迁移。
4. **兼容优先**：旧接口、旧数据、旧索引和旧前端行为先保持可用；章节树不得直接塞进当前 `GraphVO(knowledge/file)`，多格式预览不得放宽现有作者校验和 Markdown 消毒边界。
5. **小步回归**：每轮只解决一个高价值问题；重大改动前先检查 Git 并建立回滚点；专项测试通过后再跑全量回归，未验证的 Agent 建议不得直接集成或部署。
6. **部署门禁**：本地构建和回归通过、`git diff --check` 通过、交接文档同步、线上备份可回滚后才允许发布；服务器禁止构建。
7. **开闭与替换关系**：开闭原则是目标；里氏代换保证新增实现遵守旧契约、可以安全替换；依赖倒转让高层依赖抽象。合成复用、接口隔离、单一职责和测试是辅助支撑，不为形式合规提前抽象。
8. **迪米特权衡**：模块只知道完成当前职责所需的直接协作者；第三方框架内部调用集中在适配函数中。为稳定性可以保留局部适配耦合，为效率不新增无真实收益的 Facade/Strategy 链；每增加一层抽象都要说明可替换点、测试收益和运行/维护成本。

当前已落地的可逆扩展点：`MarkdownOutlineParser` 仅做纯内存标题层级探针，不接入旧切片、数据库、索引或 REST；后续 B-114 必须先完成回填可行性验证，再设计独立 outline API。

## 1. 项目概览

个人 AI 知识库（RAG + Agent），前后端同仓库：
- 仓库: `C:\Users\yansheng\IdeaProjects\Ai-Knowledge-Base`
- 代码状态: B-114 探针已提交本地 `main`，工作区 clean，当前未 push 到 `origin/main`，未部署线上
- 后端: Java 17 + Spring Boot 3.3.4 + MyBatis + MySQL 8 + Redis + DashVector(向量库) + 阿里云 OSS + DashScope LLM(qwen 系列, V4-Flash)
- 前端: Vue 3 + TypeScript + Vite + Element Plus（`frontend/` 目录，v-html 渲染 markdown 已套 DOMPurify）
- 访问: http://<SERVER_IP> （Nginx 静态 + 反代 /api → 127.0.0.1:8080）

## 2. 生产环境

| 项 | 值 |
|---|---|
| 服务器 | 阿里云 ECS <SERVER_IP>, cn-hangzhou, 2C2G, Ubuntu 22.04, 年付至 2027-08-24 |
| 服务 | systemd `aikb`（`java -Xmx512m -jar /opt/aikb/app.jar`, env 在 `/etc/aikb/aikb.env`） |
| MySQL | 库 ai_knowledge_base, 用户 aikb, buffer_pool=128M, performance_schema=OFF |
| 4GB swap | 有；**严禁在服务器上 mvn package / npm build**（会 OOM），本地构建后传 jar |

**部署流程**（scripts/deploy.py, paramiko SSH）:
1. 本地 `mvn package -DskipTests`（注意：本地若跑着 56382 靶机会锁 jar，先停）
2. 设密码：`$env:DEPLOY_PASSWORD='密码'`（deploy.py 仅读此环境变量,不读密码文件;只在当前 shell 会话存在,不落盘）
3. `python scripts\deploy.py cmd "cp /opt/aikb/app.jar /opt/aikb/app.jar.bak-日期"` 备份
4. `python scripts\deploy.py put "target\Ai-Knowledge-Base-0.0.1-SNAPSHOT.jar" "/opt/aikb/app.jar.new"`
5. `deploy.py cmd 'mv /opt/aikb/app.jar.new /opt/aikb/app.jar && systemctl restart aikb && sleep 15 && systemctl is-active aikb && curl -s http://127.0.0.1:8080/actuator/health'`
6. `python scripts\verify_deploy.py`（注册关闭模式 3 项 PASS 即可,其余 5 项本地回归覆盖）

**前端单独发布流程**（本次 9/12 已验证）:
1. 本地 `cd frontend && npm run build`，确认 TypeScript 与 Vite 构建通过
2. `tar -czf aikb-frontend-<commit>.tar.gz dist`，记录本地 SHA-256
3. 线上先备份 `/var/www/aikb`，同时保留可直接回滚目录
4. `deploy.py put` 上传到 `/tmp`，线上校验 SHA-256 后解压到临时目录
5. 使用 `mv` 原子替换 `/var/www/aikb`，检查 `index.html` 与关键 Chat 资源 HTTP 200
6. 验证 `systemctl is-active aikb`、`/actuator/health`、首页和 `scripts/verify_deploy.py`
7. 本次回滚点: `/opt/aikb/app.jar.bak-20260921b-backend`(9/21 晚 B-112 发布前,即 B-107 jar 0f27719c)+ `/var/www/aikb.bak-20260921c-preview`(9/21 晚预览版发布前,即树/图谱切换版)+ `/var/www/aikb.bak-20260921b-graph`(图谱初版)+ `/var/www/aikb.bak-20260921-graph`(9/18 盐集 Distilled 版);历史:`/var/www/aikb.bak-20260918-distilled`、`/var/www/aikb.bak-20260915-emoji`、`/var/www/aikb.bak-20260912-095745`

**坑（8/29 实测）**: ①Git Bash 下跑 deploy.py,独立路径参数会被 MSYS 改写成 Windows 路径 → SFTP 报 ENOENT（SSH cmd 不受影响,字符串里的路径没事）。Git Bash 一律前缀 `MSYS_NO_PATHCONV=1`,或回 PowerShell。②新机器需 `pip install paramiko`（历史版本 3.4.1 可用）。③前端改动要另发 dist:tar 打包 frontend/dist → put 到 /tmp → 解压到 **/var/www/aikb**（nginx 静态根,与 jar 不同目录）。

## 3. 关键提交节点

| commit | 内容 |
|---|---|
| `90b7a57` | **B-112 文件在线预览(9/21 晚,已上线)**: 后端 `GET /api/file/{id}/content`(作者归属校验同 getFileById 口径;OssService.getContent 与 delete 共用 key 解析,**5MB 上限**;md 按文件名后缀返回原文,pdf/docx content=null——contentType 因浏览器而异不可靠)+ 前端 FilePreview.vue 抽屉(renderMarkdown/DOMPurify 管线复用)+ 三处入口(树文件节点点击/图谱侧栏"查看原文"/文件列表预览按钮)+ FileContentTest 6 用例;默认 168+integration 21+e2e 10=199 全绿;jar `12e7adc3` 部署,登录态实测 4/4 |
| `8449fb4` | **树页网图切换(9/21 晚,PO"图谱应结合知识树"拍板)**: 图体抽 GraphPanel 组件(showHeader prop,独立页/树页第二视图共用),Tree.vue 顶部"树形/网图"el-radio-button 切换(defineAsyncComponent 按需加载,Tree chunk 2.9KB 不背 echarts,GraphPanel 459KB 独立 chunk),Layout 收掉桌面+移动抽屉两处 /graph 菜单项(路由保留防外链 404);前端 dist `38341577` 发布 |
| `c3d2b4b` | **B-107 L3 知识图谱(9/21,已上线)**: 后端 `GET /api/graph`(节点=条目 k-{id}+文件 f-{id},边=结构边+文件级向量相似边——文件名+首切片 500 字 embedding 两两余弦,top-2 邻居+阈值 0.45+pairKey 去重;**DashScope text-embedding-v3 批量上限实测 10 条/请求**→EMBED_BATCH_SIZE=8 分批;embedding 失败降级空相似边)+ 前端 `/graph` Obsidian 风格力导向(ECharts 5.6.0,adjacency 邻居高亮/相似虚线随权重/详情侧栏/移动端断点)+ GraphServiceTest 8 用例;回归 162+integration 21+e2e 10=193 全绿;线上 68 节点/158 边/93 相似边 |
| `70e1408` | **后端降级健壮性与回归隔离(9/15)**:Redis 不可用时知识详情直接回源、锁释放/缓存清理容错;DashVector collection 不可用时不再 NPE;Redis/DashVector/长期记忆外部依赖测试重新标记 integration/e2e;默认回归 `152/152` 通过,未部署 |
| `65792fd` | **前端 Emoji 清理(9/15)**:用户可见 Emoji 改为 Element Plus 图标与纯文案,TypeScript/生产构建/本地预览通过;9/15 已随 dist 部署上线,9/18 起被盐集品牌版(163694d 构建)替代 |
| `5e617a0` | **移动端智能问答体验优化并已线上发布(9/12)**:Agent 分析/调用工具/完成状态、工具轨迹与调用计数、移动端工具栏折叠、消息气泡/Markdown 表格适配、回到底部按钮、流式跟随和安全区适配;前端构建通过,线上 Chat JS/CSS HTTP 200 |
| `0fc282e` | 前端交互韧性与无障碍:停止生成/重试、文件索引状态与失败重试、知识库列表筛选分页状态、详情 Markdown/错误重试、文件列表触屏操作、移动菜单语义;9/11 已线上发布 |
| `ca977d0` | **file_search 补用户隔离**(本地靶机实测新账号曾召回他人文件 fileId=178,横向越权;登录改走 searchForUser 与 RetrievalServiceImpl 同口径,backlog"DashVector 检索用户隔离 filter"就此闭环)+ 重排空响应(解析不出分数)降级粗排 + 工具失败文案去重;测试 +5 → 180 项基线(9/5) |
| `5537f19` | Agent 时间线摘要友好化(ToolTraceSummarizer,5 工具 JSON→人话,失败/未知降级截断)+ rerank 分数下限淘汰(rerank.min-score 默认 0.3,全量取分本地淘汰,全淘汰返回空)+ 兜底排序方向修复(混合池分段有序,不再整体升序 sort);测试 +20 → 175/175 绿(9/5,**未部署**) |
| `de2c9d8` | 前端 vendor 三分包(element-plus/vue/markdown 独立 chunk,业务主包 1.27MB→12.6KB);HANDOFF 清除已修复的登录枚举遗留项(9/5,**未部署**,前端需发 dist) |
| `385a3dd` | 向量库不可用时服务可降级启动:VectorStoreServiceImpl/LongTermMemoryServiceImpl 的 @PostConstruct 加 try-catch(供应商故障不再阻断 Spring 启动);启动期降级测试 2 用例,155/155 绿(9/1 第六次部署) |
| `dd0b6e1` | 向量检索失败降级 BM25 兜底:主链路向量路异常退 BM25 单路,Agent file_search 与 MCP 出口返回空结果 JSON;VectorRetrievalDegradationTest 4 用例,153/153 绿(9/1 第五次部署) |
| `4dbf4f3` | 长期记忆治理：去重(相似度阈值)/过期(TTL)/容量上限/超长截断，配置化于 application.properties（memory.governance.*） |
| `3265dd1` | 测试加固：新增 27 个 P0 用例；删除 9 个零断言假测试(FileSearchChainVerify 等 5 个类)；一键回归脚本 |
| `f0c2c2a` | 空密码可注册登录漏洞修复（register 非空校验） |
| `8a32b09` | 安全加固 5 漏洞：文件接口 IDOR×2（getFileById/listByKnowledgeId 补作者归属校验）、用户接口 IDOR（/user/{id} 仅自查）、标题 XSS 入库净化（script 块连内容删）、注册按 IP 限流 5 次/分（RateLimitService 支持通用身份维度）；getUserById 回填 id |

## 4. 安全状态（攻防实测）

工具: `scripts/security_attack.py`，24 项检查，退出码=漏洞数。
**当前: 6 个漏洞全部修复，生产实测拦截正常。**

9/12 线上基础验收: 未授权访问拦截 PASS、注册关闭拦截 PASS、登录失败文案统一 PASS，共 **3/3 PASS**。由于生产 `register.enabled=false`，依赖新建双账号的知识/越权/详情等 5 项未在线执行；本地完整回归需在 Redis 与 DashVector 测试环境准备后重跑。

运行方式（本地靶机为例）:
```
# 起靶机: java -jar target\*.jar --server.port=56382 --spring.profiles.active=local
$env:ATTACK_BASE="http://127.0.0.1:56382/api"   # 必须带 /api 后缀！
python scripts\security_attack.py
```

备注:
- file_trace 工具内部走 getFileById，现已自动继承作者校验（ReAct 测试需以作者身份执行，已改）
- （原"登录报错区分用户不存在/密码错误"遗留项已由 ab02ec1 修复:统一返回"用户名或密码错误",生产实测生效,2026-09-05 复核后移出遗留清单）

## 5. 测试体系

### B-114 只读回填一致性门槛

进入 outline API、表结构或索引路由前，必须对 SUCCESS Markdown 文件使用生产实际 `MarkdownParser` 与当前 `StructureAwareSplitter` 配置重跑，并只读比较库内 `knowledge_chunk`：按 `chunk_index` 逐条校验数量、顺序、正文和 `content_length`；同时校验标题 level/path、Java UTF-16 source offsets、代码围栏与 LF/CRLF/CR 边界。验证前后不得写库、改状态、重建切片或调用向量写入；任一不一致立即阻断后续接入并记录原因。

**2026-09-30 只读语义探针结果：阻断。** 对含代码围栏的 Markdown（```` ```md\n# 代码标题\n```\n# 真标题\n\n正文。 ````）使用已编译的生产类比较：`StructureAwareSplitter` 产出 3 个 chunk，并把代码围栏拆开；`MarkdownOutlineParser` 只识别 1 个真实标题。说明两者当前语义不一致，不能直接按标题回填现有 Chunk，也不能进入 outline API/表结构/索引路由。下一步应单独评估旧 splitter 的兼容修复与检索回归，不能在 B-114 中悄悄改变旧 Chunk。

- 全量历史基线: **180 项**；默认 Maven 回归排除 `integration,e2e`，真实集成/E2E 会调用 Redis、DashVector、LLM/Embedding，可能产生少量费用。180 = 9/1 基线 155 + 时间线摘要 12 + 重排分数淘汰 7 + 兜底排序 2 个新用例及 1 个用例修正 + file_search 隔离 4。
- **2026-09-15 默认回归已通过**: 清理旧 `target` 产物后执行 `-DexcludedGroups=integration,e2e test`，结果 `Tests run: 152, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。本次提交为 `70e1408`，未部署。
- **2026-09-15 后端 `70e1408` 已部署线上**（jar SHA256 双端一致 `8a59eb15…7d85`，verify_deploy 3/3 PASS，回滚点 `/opt/aikb/app.jar.bak-20260915-backend`）。
- **2026-09-15 integration 组首跑(21 项,6F+9E)根因定位——0 代码缺陷**：①`127.0.0.1:6379` 挂的是 **Python RESP 测试替身**(PING→+PONG 但 DBSIZE→+OK、INFO 超时)，HANDOFF 8/28 记录的替身仍在运行，须停替身并 `service redis-server start` 起真 Redis；②DashVector 本机不可达(降级 BM25 正常触发)，**v2rayN TUN 全局接管流量**致阿里云看到境外出口(AWS 34.228.66.24)，白名单需加真实出口 IP(或测试时关 TUN)。正确跑法:`mvn test "-Dgroups=integration" "-DexcludedGroups=e2e"`(groups 必须同时清空 excludedGroups,否则 0 项匹配)。e2e 子集尚未跑。
- **2026-09-15(晚) 三组全绿达成——184 项全绿**: 默认回归 **152/152** + integration **21/21** + e2e **11/11**,均 BUILD SUCCESS。当日环境链路:①用户停 Python 替身起真 Redis → Redis 类 15 项转绿;②DashVector 白名单加直连出口(关 TUN 后宽带 IP)→ 网络通;③ChatIntegrationTest 修 WRONGTYPE(测试 bug);④DashVector region 根因修复(见下);⑤删 1536 维坏 collection → 长期记忆链路 6 项全绿;⑥RerankSmokeTest 断言对齐 min-score 特性。
- **DashVector region 根因(9/15 实锤,重要教训)**: 集群 `vrs-cn-moy4ydvtt0001k` 实际在 **cn-shenzhen**,此前配置/HANDOFF 记录误写为 cn-hangzhou → API 报 `ABORTED: Inexistent Cluster`(集群 ID 对但 region 错)。生产 `/etc/aikb/aikb.env` 的 DASHVECTOR_ENDPOINT 一直是正确的深圳值,**只有本地 application-local.properties 与文档记录错**。已改为深圳。SDK 排查工具: `target/MiniDash3.java`(list 探测,构造参数顺序为 **(apiKey, endpoint)**)/MiniDash4(删 collection)。
- **长期记忆维度根因(9/15 实锤,已修复)**: `long_term_memory` collection 为 8 月 text-embedding-v2 时代建的 **1536 维**,现 embedding(text-embedding-v3)输出 **1024 维** → 写入/检索全报 `-2019 Vector length(1024) is different with collection dimension(1536)` → **生产长期记忆自上线起即降级失效**(WARN 静默)。9/15 晚已删除坏集合 → 重启生产 aikb(active+UP)→ init() 自动重建 **1024 维**(describe 实测确认),长期记忆功能线上恢复。教训:换 embedding 模型必须同步重建向量集合,降级逻辑会掩盖这类维度失配。
- **RerankSmokeTest 断言修正(9/15)**: min-score 分数下限淘汰(0.3,宁缺毋滥)为 70e1408 引入特性,英文弱相关候选对中文 query 会被淘汰,测试从"必须返回全部候选"改为"最相关者排第一 + 至少保留 1 条"。
- **ChatIntegrationTest 修复(9/15)**: 测试用 `opsForValue().get()` 读 List 类型 key(主代码 rightPush+trim 存 LIST)→ 真 Redis 严格类型校验报 WRONGTYPE(替身宽松未暴露);改为 `opsForList().range()` 对齐主代码。
- **2026-09-21 三组全绿刷新——193 项全绿(154 基线+GraphServiceTest 8=默认 162/162 + integration 21/21 + e2e 10/10)**: B-107 L3 知识图谱上线当日。后端 jar SHA256 `0f27719c…fd25ec`、前端 dist `ac09a5b3…d8848` 双端一致;线上 /api/graph 实测 68 节点(3+65)/158 边(65 结构+93 相似,weight 0.545~0.836),匿名 401,verify_deploy 3/3 PASS(注册关闭模式)。回滚点 `app.jar.bak-20260921-backend` + `aikb.bak-20260921-graph`。
- **DashScope text-embedding-v3 批量上限(9/21 实测,重要)**: 65 条整包→网关报 `The input texts limit 25`;20 条→算法层报 `batch size is invalid, it should not be larger than 10`——**真实上限 10 条/请求**,GraphServiceImpl 取 EMBED_BATCH_SIZE=8 留余量。教训:网关报错数字(25)≠算法真实限制(10),必须二分实测。
- **application-local.properties 丢失恢复路径(9/21 实战,gitignored 不入库)**: 9/18 git 清理会话副作用文件丢失致 33 个 context 加载失败。三路恢复:①服务器 `/etc/aikb/aikb.env`(DASHSCOPE/DASHVECTOR/DEEPSEEK/SILICONFLOW/OSS/BOCHA + JWT_SECRET_KEY);②备份镜像 `Ai-Knowledge-Base-backup-mirror.git` 的 Day6 commit 明文 application.properties(本地 MySQL 密码);③`JWT_SECRET_KEY` 的 @Value 无默认值,漏了必报 `Could not resolve placeholder`。
- **2026-09-21(晚) B-112 文件在线预览——199 项全绿(默认 168=162+FileContentTest 6 + integration 21(B-112 改动后复跑) + e2e 10)**: 后端 GET /api/file/{id}/content(md 后缀白名单返回原文,pdf/docx content=null;OssService.getContent 5MB 上限);前端 FilePreview 抽屉+树/图谱/文件列表三处入口。jar `12e7adc3…4e17e`/dist `38fe3605…5cfd` 双端一致;verify_deploy 3/3 PASS;登录态服务器端签 JWT 实测 4/4(md 269 字/越权拒 code=500/不存在报错/匿名 401)。回滚点 `app.jar.bak-20260921b-backend` + `aikb.bak-20260921c-preview`。
- **坑(9/21): verify_deploy.py 默认 BASE=localhost:8080**——从本地跑必须传 `DEPLOY_BASE=http://<SERVER_IP>/api`,否则打本机空端口:有代理环境变量时报 502(代理拒连 localhost),清代理后报 10061 拒绝,极易误判为"服务挂了"。另: 本地会话代理变量(HTTP_PROXY=127.0.0.1:59665)会被 urllib/requests 自动使用,验真实连通性时一律 `--noproxy '*'` 或清代理。
- **本机 git refs/remotes/origin/* 异常(9/21 发现,待观察)**: `git fetch`/`git update-ref` 对 origin/* 引用**报成功但不落盘**(跨进程/跨调用消失,非沙箱复现同样);raw 文件写入(手工 echo ref 文件)可持久。**9/21 晚仍复现**(status 显示 [gone]),处置已流程化:push 后 `git ls-remote origin main` 核对远端 SHA 即算收口(远端恒正确),或手工补 ref 文件消除 [gone] 显示。**推送本身不受影响**(ls-remote 独立证实),仅影响本地 ahead/behind 显示。
- **2026-09-18 三组全绿刷新——186 项全绿**: 巡检发现 RateLimitService 裸调 Redis 无兜底 → PO 拍板 **fail-open + WARN** 降级(163694d,新增 failOpenWhenRedisDown/failOpenWhenExpireFails 两测试)后默认回归 **154/154**;用户起真 Redis 后同日重跑 integration **21/21**(47s) + e2e **11/11**(2m03s),均 BUILD SUCCESS。同日攻防套件 **24/24 零漏洞**(靶机 56382,`ATTACK_BASE=http://127.0.0.1:56382/api`,退出码 0=漏洞数);同日盐集 Distilled 品牌版前端已发布(见头部)。
- 本地历史遗留问题(替身 Redis/旧集群/TUN 出口)均已在 9/15 当日解决,后续新环境初始化可参考当日根因清单。
- 前端回归: `cd frontend && npm run build`（vue-tsc + Vite）通过；移动端问答发布前已验证首页、Chat JS/CSS HTTP 200。构建仍有 Sass legacy API、Rollup PURE 注释和 Element Plus 大包警告，均为非阻断警告。
- e2e 子集: `scripts/test-e2e.sh`（@Tag("e2e")）
- 关键测试类: FileAccessControlTest / UserSelfAccessAndRegisterLimitTest / KnowledgeDeleteCascadeTest / LoginLockoutBoundaryTest / RateLimitBoundaryTest / ChatDailyQuotaTest / TokenCostCalculationTest / RetrievalQualityEvalTest / KnowledgeAddValidationTest / ToolTraceSummarizerTest / RerankScoreFilterTest / RetrievalServiceImplTest
- **约定: 任何代码改动必须全量回归全绿才可提交部署**
- 坑: MockMvc 断言中文需 `new String(resp.getBytes(ISO_8859_1), UTF_8)` 重解码；BusinessException 是 HTTP 200 + body code 500，断言要看 body 层；**本地库曾漏建 token_usage 表（recordChat 吞异常不报错，8/28 补建）——新环境初始化务必执行最新 docs/schema.sql 全量**
- 坑(9/5): WorkBuddy 终端 bash 预设 `MSYS_NO_PATHCONV=1`,Git Bash 下 `mvn`/`./mvnw` shell 脚本拼出的 `/c/...` classpath 不被转成 Windows 路径,java 报"找不到主类 org.codehaus.plexus.classworlds.launcher.Launcher"(与 8/29 deploy.py 的路径改写坑互为反面)。绕法:直接调 java 跑 Launcher——`"$JAVA_HOME/bin/java" -classpath "C:/apps/maven/apache-maven-3.9.11/boot/plexus-classworlds-2.9.0.jar" -Dclassworlds.conf="C:/apps/maven/apache-maven-3.9.11/bin/m2.conf" -Dmaven.home="C:/apps/maven/apache-maven-3.9.11" -Dmaven.multiModuleProjectDirectory="<项目Windows路径>" org.codehaus.plexus.classworlds.launcher.Launcher test`(正斜杠 Windows 路径 java 可接受);或回 PowerShell 跑 mvn
- ManualReActVerifyTest 用自建 fixture（knowledge+file 临时插入清理），勿再改回硬编码 fileId

## 6. 硬约束（改动前必读）

- 中间件全部 async/await，禁 callback 风格
- Redis 反序列化用 BasicPolymorphicTypeValidator 白名单（com.yansheng., java.util., java.time.）
- 知识/文件访问必须带作者归属校验（author == 当前用户名）——本次 IDOR 修复即补齐此口径，新接口勿遗漏
- 上传白名单仅 .pdf/.docx/.md；PROCESSING 状态文件禁删
- LLM 主内容不得含原始 JSON/代码块/[source: ...] 标记
- SSE 事件匹配用正则 `/^event:\s*refs$/m`（兼容带/不带空格）
- Controller 必须 /api 前缀；配置集中 application.properties；前端 API 地址走 .env
- BusinessException → HTTP 200 + {"code":500,...}，验证脚本看 body code 不看 HTTP 状态

## 7. 待办与建议

近期可做:
- [x] 生产演示数据已播种（8/29,seed_demo.py 11/11,demo 账号可登录;问答验证 5 条引用）
- [x] 每日配额生产生效（8/29:CHAT_QUOTA_TOKEN_LIMIT=20000 tokens/日,豁免 yan,在 /etc/aikb/aikb.env 可调）
- [x] Agent 模式可视化上线（8/29 第二次部署 ffbd271:聊天"🤖 Agent"开关走 ReAct 循环,SSE tool 事件 → 前端工具时间线;Agent 模式 LLM 调用已按 userId 记账;生产实测 time_now 时间线+正确回答）
- [x] 识图盲区修复上线（8/29 第三次部署 24957d1:扫描件显式失败+error_msg 落库+前端悬浮展示;生产实测通过。演进项:OCR 补全/多模态 qwen-vl 按 JD 再定）
- [x] **DashVector 新免费集群已恢复(9/12)**:集群 `aikb-free-2026` / ID `vrs-cn-moy4ydvtt0001k`，生产白名单已加入 `<SERVER_IP>`；已创建 `long_term_memory` 与 `knowledge_chunk_vector`，从线上 MySQL 重建 27 个切片，`index_completeness=1.0`，语义查询冒烟返回 3 条结果。免费试用约 30 天，届满前必须迁移到长期付费方案或新集群；不要把试用集群当作永久生产方案。
- [x] 服务器 /opt/aikb 备份 jar 已清理（9/1 第六次部署验收通过后:删除 8/24-8/29 的 7 个旧备份 ~811MB;保留 bak-0901(无降级代码版)/bak-0901b(降级第一版)两个回滚点,/ 分区占用降至 23%）
- [ ] 前端 chunk 优化:vendor 已三分包(element-plus/vue/markdown 独立 chunk,主包 1.27MB→12.6KB,de2c9d8);element-plus 单 chunk 仍 >500kB(gzip 339KB),要再减需引入 unplugin 按需导入(加构建依赖,未做)
- [x] Agent 时间线工具结果摘要已友好化(9/5:ToolTraceSummarizer 按工具名把结果 JSON 翻译成人话,如 file_search→"检索到 N 个相关文件";解析失败/未知工具降级截断原文,回传模型的原始结果不变)
- [x] **移动端智能问答体验已完成并发布(9/12)**:Chat 页面已适配窄屏消息气泡、工具栏折叠、Agent 工作状态/工具轨迹、引用与 Markdown 表格、流式自动跟随、回到底部和安全区;线上基础验收 3/3 PASS。回滚目录见第 2 节。
- [x] **后端默认回归恢复全绿(9/15)**:Redis 降级回源、DashVector collection 缺失防 NPE、外部依赖测试隔离;默认集合 `152/152` 通过,提交 `70e1408`。剩余 28 项历史基线需真实 Redis/DashVector/LLM 环境复核。
- [x] rerank 打磨完成(9/5):①分数下限淘汰——rerank.min-score 配置(默认 0.3),请求改拿全量候选分数后本地先淘汰再截 topN,全淘汰返回空让上层如实作答,置 0 关闭;②兜底排序方向修复——rerank 关闭时不再对混合池整体升序 sort(两路 score 语义相反:向量=距离、BM25=相关度,整体排序必错一路),改为保持合并顺序(向量段距离升序在前 + BM25 段相关度降序在后,各自天然有序)
- [x] 登录错误信息统一 + register.enabled（ab02ec1 已完成;该批时点回归 140 用例,当前基线 155 见第 5 节)
- [x] 在真实 Redis 上运行 Redis 集成测试，并为本机配置可访问 DashVector 测试白名单后运行剩余集成/E2E，目标历史 180 项 `0 failures / 0 errors`（9/15 首次三组全绿达成,此后持续保持;当前口径 **199 项全绿**,见第 5 节 9/21 记录）
- [x] **前端 Emoji 清理已部署(9/15)**:`65792fd` 重新 build(vue-tsc+Vite 通过) → tar(SHA256 `4a5dbe70…b88` 双端校验一致) → 备份 `/var/www/aikb.bak-20260915-emoji` → 原子替换 → 验收:aikb active、/actuator/health UP、内外网 index/Chat JS/CSS 200、verify_deploy 3/3 PASS(注册关闭跳过 5 项);/tmp 临时包已清理
- [x] **盐集 Distilled 品牌版已部署(9/18)**:`163694d` 构建(含知识树 /tree、盐集品牌、树加载失败反馈;vue-tsc+Vite 12.1s 通过) → tar(SHA256 `37db84ad…70ff` 双端一致) → 备份 `/var/www/aikb.bak-20260918-distilled` → 原子替换 → 验收:线上首页 `<title>盐集 Distilled · AI 知识库</title>`、index 200、aikb active、verify_deploy 3/3 PASS;Pre-Flight 探针先行的第一次实战

低优先 backlog:
- 后端分页 / .doc 老格式支持 / 统一 HTTP 连接池
- ~~DashVector 检索用户隔离 filter~~(已闭环:file_search 9/5 改走 searchForUser,ca977d0;RetrievalServiceImpl 一直是隔离的)
- 知识可视化 B-107:**L1 树+L3 网图已上线(9/21,树页顶栏切换)**,仅剩 L2 反链(数据现成:SearchResult.chunkId 引用链路);完整三档记录见 docs/backlog.md
- **聊天管线合并 B-113(9/21 立,PO 拍板暂不动码)**: 普通 RAG vs Agent 双路径三差异(真流式 vs 非流式/联网注入 vs 工具自决/1 次 vs 1~6 次 LLM 调用),彻底合并前置=FunctionCallingService 流式化(~半天+199 回归);轻量去重(两处前置 5 行抽方法)可先行;方案详见 docs/backlog.md B-113
- 简历方向: Python+LangGraph 多 Agent 复刻版(强化"场景"维度)

**本地环境变化（2026-08-28）**: Docker Desktop 端口转发损坏（容器内 PONG 但宿主 6379 不可达,重置 WSL/重启均未恢复）;已改在 WSL Ubuntu-24.04 安装并 `service redis-server start` 起 Redis（apt 装了 redis-server 包）,本地靶机/回归均正常。恢复 Docker 转发后两条路径可并存。

## 8.5 周边工具与知识库(跨仓库生态)

| 位置 | 用途 | 交接文档 |
|---|---|---|
| `IdeaProjects/chlog` | git 历史→中文周报/changelog 的零依赖 CLI(Python 练手) | chlog/HANDOFF.md |
| `IdeaProjects/study-vault` | 备考学习库(四线计划+SRS 调度 srs.py) | study-vault/HANDOFF.md |
| `~/.zcode/skills/{scaffold,modforge,study}` | 立项/游戏 mod/学习计划三个 skill(各带 HANDOFF) | 各自目录内 HANDOFF.md |
| `D:/StudyMaterials` | 游戏与方向学习资料库(39 仓库,INDEX.md 总目录) | INDEX.md |

提交本仓库时若动了上述生态,记得去对应仓库各自提交(它们是独立 git 仓库)。

## 8. 常用命令速查

```powershell
# 默认回归（改代码后必跑；当前排除 integration/e2e）
mvn test

# Windows + Git Bash 若 Maven Launcher classpath 报错，直接调用 Java 17 + Maven Launcher
# 具体 maven.home / plexus-classworlds 路径以本机 ~/.m2/wrapper/dists 下实际版本为准
# 参考本次 9/15 命令：-DexcludedGroups=integration,e2e test

# 起本地靶机
java -jar target\Ai-Knowledge-Base-0.0.1-SNAPSHOT.jar --server.port=56382 --spring.profiles.active=local

# 安全攻防（ATTACK_BASE 必须带 /api）
$env:ATTACK_BASE="http://127.0.0.1:56382/api"; python scripts\security_attack.py

# 生产验收
python scripts\verify_deploy.py

# 部署（密码文件就绪后）
python scripts\deploy.py cmd "<命令>"
python scripts\deploy.py put "<本地路径>" "<服务器路径>"
```
