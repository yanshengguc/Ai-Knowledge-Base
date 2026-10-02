# 产品待办（Product Backlog）

> 来源:HANDOFF.md(2026-09-15)待办与低优先 backlog 整理 | 维护者:PO=用户(AI 起草) | 更新:2026-10-02

## 迭代原则（当前 Sprint 生效）

- **先保持、后扩展**：先保护现有 Parser/Splitter/Indexing/Retrieval、REST 和前端预览契约，再新增能力；不为“未来可能支持”提前重构。
- **组合优先**：用注入的小能力组合流程，不用继承、万能基类或大而全 Service。
- **接口隔离**：新用例新增窄接口或适配层；`FileService` 暂不强拆，等真实的查询/命令/预览新增调用方后再渐进迁移。
- **树图分工**：知识图做总览，知识树做文档内部层级，原文/Source Chunk 做证据回溯；章节节点不直接复用当前 `GraphVO` 的 `knowledge/file` 契约。
- **B-114 门槛**：先用纯内存 `MarkdownOutlineParser` 验证标题层级、路径和偏移，并完成“库内 Chunk 与重跑结果逐条一致”的回填可行性验证；通过后才考虑独立 outline API、表结构和索引路由。
- **每轮收口**：一轮只做一个可验证问题，专项测试→全量回归→Git 存档→更新 HANDOFF；未通过门禁不部署。
- **原则关系**：开闭是目标；里氏代换保证扩展实现可替换；依赖倒转让高层依赖抽象；合成复用、接口隔离和单一职责辅助形成稳定边界。
- **迪米特权衡**：优先减少跨层和第三方内部知识的扩散，但不为“少知道”堆叠空 Facade；允许把框架适配集中在一个局部函数，换取更低的实现复杂度和更高的运行/维护效率。
- **变更门槛**：大改动前必须先检查 Git 并提交或建立可回滚存档；小改动也要专项测试、全量回归和交接记录，未验证不部署。
- **维护期已结束（2026-10-02 PO 拍板）**：9/15「AKB 转纯维护模式、不开新功能线」口径作废，转功能迭代，B-114 为主线。Phase2 挂载的前置 = 先建检索质量 eval 尺子（recall@5 ≥ 0.80 / MRR ≥ 0.70）+ 无树基线留档（Sprint 8），有树 vs 无树对比通过后再上线。

## P0（本 Sprint 候选）
- [x] B-101 **已闭环(9/15)**: 后端 `70e1408` 部署上线(jar SHA256 双端一致,verify_deploy 3/3 PASS,回滚点 bak-20260915-backend),线上与本地对齐
- [x] B-102 **已闭环(9/15 晚)**: integration 21/21 + e2e 11/11 + 默认回归 152/152 = 184 项全绿。根因三连:Chat WRONGTYPE(测试 bug)/DashVector region 误写 cn-hangzhou(实为 cn-shenzhen)/长期记忆 collection 1536 维 vs embedding 1024 维(删坏集合重建+生产重启验证 dimension=1024)

## P0（Sprint 3 已收口 · 2026-10-01）
- [x] B-115 管理端 MVP（只读）**Sprint 3 主线 · 已上线 2026-10-01**：课设硬需求（原计划 10 月上旬，已到期）。范围=配置驱动 admin 鉴权（`admin.usernames` 白名单，零 DDL）+ `GET /api/admin/overview`（用户/知识/文件/切片数 + 文件状态分布 + 全站 Token 汇总）+ `GET /api/admin/users`（服务端分页 + 关键词 + 不回传 password）+ 前端 `/admin` 路由守卫与只读页 + 导航项仅 admin 可见。**不含**用户增删改与 `user.role` 字段（跨 DDL，本地无 mysql 客户端无法验证，推迟下一轮）。**闭环(10-01)**：后端 jar SHA256 `3e10c9e9…afe4803`、前端 dist tar SHA256 `1eef2b2a…ecc0289c` 双端一致；`verify_deploy 3/3 PASS`；`/`、`/admin`、新资源 HTTP 200，`aikb active`；默认回归 189/189 绿；匿名与坏 token 访问 `/api/admin/*`、`/api/user/me` 均 401
- [x] B-116 管理端写操作（用户增删改 + `user.role` 字段）**Sprint 7 主线 · 已上线生产并完成线上登录态实测（2026-10-02，部署与实测留痕见 docs/sprint.md）**：范围=`user.role` 二值(admin/user) 叠加 `ADMIN_USERNAMES` 白名单 + 四个写端点(`POST /api/admin/users`、`PATCH /{id}/role`、`PUT /{id}/password`、`DELETE /{id}`) + 六条安全护栏(禁自操作/防移除最后生效管理员/删前校验名下无 knowledge·file/role 白名单/写前 requireAdmin/响应无 password) + 前端 Admin.vue 角色列与操作列。**闭环证据**：`mvn -o test` 222/0/0 · `AdminWriteIntegrationTest` 3/0/0(真实 MySQL 建→改→重→删) · 前端 `npm run build` exit 0 · 本地真实 HTTP 四端点正/负向 body code 全部符合契约(非 admin/自己/非法 role/不存在 id/名下有数据 均 HTTP 200 + code:500) · `AdminUserVO` 实测无 password。**原「本地无 mysql 客户端」阻塞已解除**(mysql.exe 8.0.44，已有本机迁移通道)。**已上线**：生产 DDL `ALTER TABLE user ADD COLUMN role` 已执行（34 用户全 user）、jar/dist 已部署；**线上登录态实测通过**（临时管理员 `aikb_s7_verify`，测后彻底清理：user 34→35→34、`aikb_%`=0、孤儿 0）。护栏 2 的 HTTP 口径不可达，仅逻辑层单测覆盖
## P1（**功能迭代**——2026-10-02 PO 拍板:维护期正式结束,转功能迭代,B-114 为主线）
- [ ] B-103 element-plus 按需导入（unplugin-vue-components + unplugin-auto-import），减 1MB+ 单 chunk
- [ ] B-104 后端分页（知识/文件列表服务端分页）

## P2（低优先，维护期不新开功能线）
- [ ] B-105 .doc 老格式上传支持
- [ ] B-106 统一 HTTP 连接池
- [ ] B-107 知识可视化（Obsidian 风格,9/15 晚 PO 提出升级方向）——分三档 MVP,按投入递增;**L1+L3 已完成（9/16 / 9/21）,仅剩 L2 反链**:
  - [x] **L1 树(9/16 完成)**: /tree 路由 + el-tree 懒加载两级树(知识条目→文件,复用 /knowledge 与 /file/list 接口,零后端改动),文件节点带状态标签(PROCESSING/SUCCESS/FAILED),点击直达知识详情;顺带品牌化:app.name=盐集 Distilled、index.html 标题、i18n zh/en。vue-tsc+vite 构建过,152 回归绿
  - **L2 反链（~1d）**: "反向链接"——chunk 被哪些 AI 回答引用过的溯源面板(数据现成:SearchResult.chunkId/ChatResponse 引用链路),对应 Obsidian Backlinks
  - [x] **L3 网图(9/21 完成)**: /graph 路由 + ECharts 5.6.0 力导向(Obsidian 风格)。边方案实际落地=**文件级向量相似边**(原计划共引边——chat references 未落库(B-110 未做)、DashVector 存量为 chunk 级近邻查询拿不到全量,改为文件名+首切片 500 字做 embedding 两两余弦,top-2 邻居+阈值 0.45+pairKey 去重无向边):后端 GET /api/graph(线上实况 68 节点=3 条目+65 文件/158 边=65 结构+93 相似;**DashScope text-embedding-v3 批量上限实测 10 条/请求**,EMBED_BATCH_SIZE=8 分批,embedding 失败降级空相似边图仍渲染)+前端力导向(条目配色分类图例/结构实线+相似虚线随权重/emphasis adjacency 邻居高亮/点击节点详情侧栏跳知识详情)+GraphServiceTest 8 用例;回归 162+integration 21+e2e 10=193 全绿,jar 0f27719c 部署+线上 /api/graph 验证通过。**当前产品分工(9/29)**：知识图独立做总览，知识节点“进入知识树”跳转 `/tree?knowledgeId=<id>`，文件节点可直接查看原文；树和图不再在 `/tree` 内互相切换，桌面/移动导航均保留独立 `/tree` 与 `/graph` 入口，前端导航闭环已发布。
  - 技术备选: AntV G6/D3 更专业但重,MVP 用 ECharts;接口需新增 /api/graph 聚合端点(共引边可 MySQL 聚合 chat 引用记录)
  - 约束: 维持"维护期解锁",若维护期想展示,只做 L1 树(零后端改动)
- [ ] B-108 Python+LangGraph 多 Agent 复刻版（简历方向，独立仓库，不在本仓库 Sprint 内）
- [ ] B-111 AI 回答渲染美化（9/20 PO 试用反馈"回答格式应该更好看"）:
  - **现状实锤**: utils/markdown.ts = 裸 marked + DOMPurify，注释明写"代码高亮不做(轻量)"；表格/引用块/标题无增强排版
  - **改动（纯前端 ~1-2h）**: ①代码块高亮（highlight.js，marked 已装零新增生态风险）；②.markdown-body 排版增强（表格边框/引用块样式/h 标题层级/行距）；③references 引用内容同套样式已复用，顺带受益
  - 注意: 流式渲染时 renderMarkdown 每帧全量重跑，高亮库注意按需加载；DOMPurify 白名单不动（防 XSS 回归）
  - 量级小，投递收口后可与 B-110 MVP 同批做
- [x] B-112 文件在线预览 **MVP 完成(9/21 晚,PO"点击对应位置看原文")**: 后端 `GET /api/file/{id}/content`(作者归属校验同 getFileById 口径;OssService 新增 getContent,key 解析与 delete 共用,**5MB 上限防大文件拖垮 2C2G**;md 按文件名后缀返回原文,pdf/docx content=null——contentType 因浏览器而异不可靠);前端 FilePreview.vue 抽屉(renderMarkdown/DOMPurify 管线复用)+三处入口(树文件节点点击/图谱侧栏"查看原文"/文件列表预览按钮);FileContentTest 6 用例;默认 168+integration 21+e2e 10=199 全绿;线上 jar 12e7adc3 实测 4/4(md 原文/越权拒/不存在报错/匿名 401)。pdf/docx 二进制渲染不在 MVP(返回元信息由前端提示)
  - **现状实锤**: FileController 仅 upload/GET {id}/GET list 三端点——**无内容/下载端点**；前端 Detail.vue 与 FileListPanel.vue 零预览/下载入口，上传后内容黑盒
  - **MVP（~2-3h）**: 后端 `GET /api/file/{id}/content`（校验作者归属==当前用户，返回原始 md 文本）+ 前端 Detail 抽屉 renderMarkdown 展示——**md 文件直接复用 B-111 渲染管线，教材 11 章全是 md，学习场景立即可用**
  - **扩展**: pdf 浏览器原生 inline 预览（Content-Type: application/pdf，~1h）；docx 用 docx-preview 前端库（单独评估，暂缓）
  - **协同**: 预览页是 B-110 知识跳转的天然落点（跳转到章节片段→高亮 chunk）；安全上已有 JWT+作者校验覆盖，教材版权内容仅限本人账户可见（符合"不进 git/不外泄"红线）
  - 依赖: 先做 B-111（渲染），预览直接受益
- [ ] B-113 聊天管线合并（9/21 晚 PO 看完 Agent 模式链路图后提出"要不要直接合并,感觉有点那啥了";**PO 拍板:先记 backlog,现在不动**）:
  - **现状三差异（= 合并的真实门槛,不是删个开关那么简单）**: ①普通=真流式逐 token 打字机,Agent=非流式循环跑完一次性吐全文——直接全走 Agent 会**所有消息失去打字机效果**;②联网:普通=用户开关注入上下文,Agent=web_search 作为工具由模型自决;③成本:普通 1 次 LLM 调用,Agent 1~6 次+每轮 prompt 都背 5 个工具 schema
  - **彻底合并方案（~半天+全量回归+部署）**: ①FunctionCallingService 流式化——第一轮无 tool_calls 即边生成边吐 token,有工具调用则执行后继续循环;②去掉 Agent 开关,所有消息走 ReAct 循环,模型自主路由(普通 RAG 退化为"第 1 轮不调工具"的特例);③ChatController 分支/Chat.vue 开关/locales 清理;④199 全量回归+部署验证
  - **轻量去重（10min,可先行单独做）**: streamAsk / streamAskWithAgent 开头 5 行(检索+历史+记忆+拼 Prompt)抽私有方法,行为零变化 · **已完成（T-2, 2026-10-02）**：抽 `buildChatContext` + 私有 record `ChatContext`，独立评审 PASS + 独立验证 `mvn -o test` 222/0/0，行为零变化、无夹带
  - **技术价值**: "一条管线、模型自主路由"叙事优于"两个开关两条管线";流式 ReAct(边调工具边吐字)是 Agent 场景题的加分实现点
  - 约束: 冲刺期聊天主链路 199 全绿是护城河,动它必须整块时间窗

## 已完成
- [x] B-000 前端 Emoji 清理 `65792fd` 部署+线上验收（2026-09-15,3/3 PASS,回滚点 bak-20260915-emoji）
- [x] B-000 移动端智能问答体验发布（2026-09-12,5e617a0）
- [x] B-000 后端默认回归恢复全绿 152/152（2026-09-15,70e1408）
- [x] B-000 DashVector 新免费集群恢复+27 切片重建（2026-09-12）

- [ ] B-109 URL 导入（import-url，9/20 PO 提出，覆盖两形态）:
  - **形态①直链文件**（GitHub raw .md/.pdf 等可下载 URL）: 新增 `POST /api/file/import-url`，后端 HttpClient 下载字节流 → 复用 Parser→StructureAwareSplitter→向量化全流水线（~1-2h）
  - **形态②网页正文**（在线教程/文档站）: jsoup 去 nav/footer 正文提取 + 转 md 再入库（+2-3h）
  - **SSRF 防护（必做，攻防套件 +1）**: scheme 白名单 http/https、解析目标 IP 拒绝私网段（127.0.0.1/10.x/172.16.x/192.168.x/169.254.169.254）、重定向逐跳校验、下载大小上限（建议 20MB）、复用 PROCESSING 状态机异步处理
  - 拆分: PR1=直链+SSRF（含攻防用例），PR2=网页提取；各配 mvn test 全量回归（改代码铁律）
  - 技术价值: 知识库数据接入是 RAG 产品标配问点（仅文件上传偏薄）; SSRF 攻防是云安全高频题——feature+安全双向加持
  - 依赖注意: 章节树/结构感知对 md 有效，PDF 提取为裸文本; 教材类版权内容仅走线上库，永不进 git

- [ ] B-110 知识跳转（9/20 PO 提出，灵感:MC 任务 mod"缺什么→左键看怎么获得"）:
  - **交互**: 阅读文档/AI 回答时选中文本 → 指令创建跳转超链接（**仅选中部分标蓝**，非全文自动链）→ 点击跳转到知识库中解释该概念的章节/片段并高亮
  - **MVP（~3-4h，零 AI 参与，防幻觉最稳）**: 选中文本 →"在知识库定位"按钮 → 复用现有 RAG 混合检索（选中词当 query）→ 命中则跳 Detail 页 + 滚动定位高亮 chunk。目标永远来自真实检索结果
  - **进阶（+2-3h）**: AI 回答输出标记语法（如 [[概念]]），前端渲染蓝链；点击后端检索定位。**防幻觉铁律: 链接目标 chunk 必须来自真实检索，AI 只负责"判断哪些词值得链"，绝不许 AI 编造目标**
  - **定位实现**: chunk 级锚点——优先给 StructureAwareSplitter 补 offset 元数据（+1-2h）；或前端全文字符串匹配（chunk 有 overlap 需处理）
  - **一鱼两吃**: 跳转关系落库 = B-107 L3 网图天然需要"边"数据（节点=chunk，边=跳转/共引），做了 B-110 等于给 L3 铺路
  - **技术价值**: 需求来源故事极鲜活（MC 玩家视角的产品思维）;"RAG 检索当跳转定位器"是把检索复用到非问答场景的架构思考; 与 Obsidian 双链/Wikipedia 内链类比可展开
  - 约束: 投递收口后解锁（PO 9/20 意向,量级 MVP 半个开发日）

- [ ] B-114 知识树+检索索引（9/24 晚 PO 提案，源自其"知识树+检索索引"学习方法论；**PO+双方 AI 评审一致:作为项目后期特色功能，不打断主线，开工窗口 10 月中/Python demo 之后**）:
  - **提案核心**: 文档→Chunk→知识树（节点含 parent/children/definition/why/mechanism/related_nodes/检索关键词/面试问题索引/场景索引/source_chunks）→用户问题先经索引定位节点→取 source_chunks 融合现有 hybrid 检索→RAG。定位=从"AI 知识库"到"AI 学习型知识库";知识树负责导航定位，Vector Search 负责语义召回，Source Chunk 保证可溯源
  - **五关评审（通过）**: 作者即用户✓(PO 自己用 AKB 学八股,dogfood 成立)/非 devtool✓/让第一个变强✓/JD 有"知识图谱/混合检索"原话✓(9/26 实测修正:GraphRAG 仅社招算法岗/研究院层,RAPTOR 全渠道 0 命中系公司名假阳性)/动机非"练手"✓——几周来首个全过提案;命名纪律:对外叫「标题层级分块+混合检索」,主动放弃 GraphRAG/RAPTOR/层次化检索词
  - **去过时项（9/24 评审砍掉）**: ①B-107 相似度图谱(68 节点/158 边)不再迭代,降级为横向关联素材;其可视化"效果不行"的根因=只有圆点连线无语义,优化方向是改造成 RAG 交互层（搜索→高亮定位节点→节点详情→source 溯源）,不是单纯美化 ②GraphRAG 完整架构过度设计,只读思想 ③"学习路径/复习 Agent"远期冻结 ④LeanRAG/RAGTree 略读;主参考=RAPTOR 层次聚合+结构感知切片
  - **关键技术决策——规则树打底**: LLM 直接从 chunk 抽树必碎片化(同概念多节点/父子矛盾);MVP 走 md 标题层级规则解析出锚定树(=B-109 提到的 StructureAwareSplitter 结构感知;**docx 不在 Phase1 范围**——9/26 实测 WordParser.java:23-27 只取 getText() 丢 Heading 样式,docx 长不出树;层级可从 chunk 正文前缀离线反推回填,铁律=split() 输出逐字节不变+回归测试钉死),LLM 只补 definition/why/mechanism+横向关联,成本降一个量级且树稳定
  - **检索融合铁律**: 树只做路由加权层(索引命中→source_chunks 提权/前置),不动 hybrid(向量+BM25)底座;树检索失败降级回纯 RAG 并打 WARN;上线门槛=RetrievalQualityEvalTest recall@5/MRR 阈值 0.80/0.70(Eval Harness 15/15 测的是工具选择不测检索质量,9/26 修正)+先跑无树基线留档+有树 vs 无树对比
  - **拆阶段**: Phase1 MVP(4-6 晚,9/26 修正——工作面=新表+Mapper+回填脚本+切片器+节点 API+详情 UI+溯源页+全量回归,回归验证单列一晚;开工前置 1h=回填可行性验证:取 1 篇 md 用 OSS 原文重跑 StructureAwareSplitter,产出须与库内 knowledge_chunk 逐条一致)=标题树解析+节点详情+source_chunks 溯源页;Phase2=LLM 语义层+索引挂载;Phase3=Graph View 交互层升级(参考 Obsidian 交互思想不抄界面:点击展开/双击进入/搜索高亮/点击 source 回原文)
  - **当前状态(9/30)**: 已完成纯内存 `MarkdownOutlineParser` 探针及围栏/换行边界修复，并完成 `StructureAwareSplitter` 的最小围栏与换行兼容修复；切片专项 `11/11`、默认回归 `179/179`（Maven 默认排除 integration/e2e）。现有 `split()` 接口、普通段落/句子/硬切行为保持，输出统一 LF；代码围栏伪标题不再开启章节。二次只读语义探针确认围栏/波浪号/CR 已对齐，但多级标题中“无正文父标题是否进入后续 Chunk 上下文”仍未定义：旧 splitter 产出 2 个 chunk，OutlineParser 产出 3 个标题，不能宣称真实库内 Chunk 已一致。当前本地无 mysql CLI/pymysql，未访问数据库/OSS；**语义决策已确定采用 B 方案**：Outline 作为导航层，空父标题不生成空 Chunk，`source_chunks` 只关联真实正文/后代 Chunk，旧 Chunk 与检索语义不变。耦合度评估显示主流程和树/图边界已实际降低，但 FileServiceImpl、标题规则重复、Graph 节点协议仍是热点；下一步优先真实只读语料对齐，不为拆 Service 或统一解析器而重构。当前改动已提交本地 main，尚未 push 到 origin，也未部署线上。
  - **回填可行性实证（Sprint 4 · 2026-10-01 · 只读对账）**: 新增只读探针 `B114BackfillAlignmentProbeTest`（裸 JDBC + OSS SDK，不启 Spring 上下文故不依赖 Redis；`@Tag("integration")` 默认回归排除；只 SELECT + OSS GET）。对本地库筛出 4 个可对账文件（md + file_url 非空 + SUCCESS），用当前 `StructureAwareSplitter(500,100)` 重跑原文与库内 chunk 逐条比对：**file_id=52 上课讲义.md → 0/34 一致**（库内 20 条 `CHAR_LENGTH` 恒为 500，即固定窗口口径；重跑 34 条为结构感知口径）；**file_id=146/148/150 → 各 4/4 全一致**（08-28 创建，结构感知口径）。汇总逐条一致率 **12/46**。**结论：① "与库内逐条一致"作为 Phase1 门槛对存量数据不成立——库内两套口径并存（08-24 固定窗口 / 08-28 结构感知）；② 结构感知口径自身可复现（新批 100% 一致），B 方案（空父标题不生成空 Chunk）在新批数据上成立；③ Phase1 前正确动作是"定义迁移边界"而非"对齐存量"——旧 chunk 与其向量不回填改写，改为新增 Outline 导航层 + source_chunks 关联，与 9/30 已定 B 方案一致。** 未覆盖线上库（1131 条 chunk 口径分布未知，需只读数据通道）。复跑：`mvn test -Dtest=B114BackfillAlignmentProbeTest -DexcludedGroups=e2e -DfailIfNoTests=false`  - **Phase1 后端闭环（Sprint 5 · 2026-10-01 · 已上线 2026-10-01 夜）**: 新增两表 `knowledge_outline_node`（标题树，含 parent_id/node_index/level/title/heading_path/源偏移）与 `knowledge_outline_chunk`（节点↔真实 chunk 关联，双外键 ON DELETE CASCADE），DDL 已追加进 `docs/schema.sql` 且本地库已建表；新增 `OutlineIndexServiceImpl`（先序建树 + 幂等重建 + 顺序对齐关联）+ `OutlineController`（`GET /api/outline/file/{id}`、`GET /api/outline/node/{id}`、`POST /api/outline/file/{id}/rebuild`），并在 `DocumentServiceImpl` 仅对 md 文件追加落库（失败降级 WARN，不影响主链路）。**关联判据实测修正**：`StructureAwareSplitter` 给同节每个 chunk 都前置标题（不只首块），故判据=「标题重复即同节续块；同名新节与续块无法区分时停止关联并打 WARN」；空父标题入树但 source_chunks 为空（B 方案）。证据：专项 `11/11`、默认回归 `200/200`（基线 189 全绿，`split()` 行为零变化）；真实 MySQL 持久化验证（裸 MyBatis 不启 Spring、事务内 rollback）**1/1 全绿**（含级联删空断言；此前误报根因=裸 JDBC 执行 DELETE 不经 MyBatis 一级缓存，修正=断言前 `session.clearCache()`）。**未覆盖**：前端节点详情/溯源页（Sprint 6）、Phase2 检索索引挂载、存量固定窗口批（预期降级为空关联）  - **技术价值**: 设计动机自然——"传统 RAG 只解决找相关文本,学习者还需要层级与关联";比"调了 Embedding API 存了向量库"多一层架构思考;与 B-110 跳转、B-107 图谱形成产品闭环

  - **Phase1 前端闭环（Sprint 6 · 2026-10-01 · 实现+评审+验证通过，未提交未部署）**: 新增 `frontend/src/api/outline.ts`（封装 /api/outline 三端点，TS 类型逐字段对齐后端 4 个 VO）+ `frontend/src/views/knowledge/components/OutlinePanel.vue`（标题树导航 + 节点详情 + sourceChunks 溯源；空态与失败态分离；竞态序号防覆盖；点击溯源卡复用 FilePreview 打开原文）；`FileListPanel.vue` md 文件行新增「查看大纲」入口；`locales/zh.ts`/`en.ts` 文案。多 agent 迭代：Dev → 评审 PASS（2 个低severity 项）→ Dev 修复轮 → 验证 PASS。证据：`npm run build` exit 0 · 2282 modules · 11.95s。**已联调（2026-10-01 夜）**：本地后端 56382 真机 HTTP 验证通过（file/258 nodeCount=4；node 端点 200 + 溯源 1 条 + preview 截断 200 字；rebuild 幂等 data=4；非作者 token → body code:500 权限不足）；集成整组 `28 run / 0 Failures / 3 Errors`（3 个为 DashVector 环境阻塞）。**页面点击路径已通过**：浏览器验证-agent 走 `/knowledge/1408` → `outline_test.md`「查看大纲」→ 标题树 4 节点层级正确（架构 L1/接入层 L2/网关 L3/服务层 L2）+ 节点详情 + 溯源卡「查看原文」打开 FilePreview，8/8 PASS。**已上线（2026-10-01 夜）**：后端 jar `3e2d5d9f…c8377`、前端 dist `88a94ec7…f341`、两表 DDL 已在生产执行（仅新增、执行时 0 行）。**生产实测（2026-10-01 夜补测）**：登录态 `GET /api/outline/file/{id}` → 200 / nodeCount=4、节点详情 200、`rebuild` 幂等 data=4、归属负向 code:500「权限不足」；临时账号与临时知识/文件测后已清理（级联校验 0 行）。

## Sprint 8 计划（2026-10-02 定 · 详见 docs/sprint.md）
- T-1 B-111 回答渲染美化（纯前端）· T-2 B-113 轻量去重（行为零变化）· T-3 B-103 element-plus 按需导入 · T-4 检索质量 eval 底座（recall@5 / MRR + **无树基线留档**）
- 特征：全部可回滚、不改检索行为；与 B-116 生产观察期不冲突
- B-114 Phase2（真挂载）顺延 **Sprint 9**，门槛 = 有树 vs 无树对比 + Sprint 8 基线指标

## 技术债（TD）
- TD-001 Outline 归属校验口径重复：`FileServiceImpl.verifyOwnership` 与 `OutlineIndexServiceImpl.verifyOwnership` 各写一份（语义相同，可能漂移）。影响=安全口径一致性；决定=本轮先记，不为此抽公共件重构主链路；触发条件=出现第三处同口径校验，或任一处口径需变更；回滚=无（纯组织性）
