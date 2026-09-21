# 产品待办（Product Backlog）

> 来源:HANDOFF.md(2026-09-15)待办与低优先 backlog 整理 | 维护者:PO=用户(AI 起草) | 更新:2026-09-15

## P0（本 Sprint 候选）
- [x] B-101 **已闭环(9/15)**: 后端 `70e1408` 部署上线(jar SHA256 双端一致,verify_deploy 3/3 PASS,回滚点 bak-20260915-backend),线上与本地对齐
- [x] B-102 **已闭环(9/15 晚)**: integration 21/21 + e2e 11/11 + 默认回归 152/152 = 184 项全绿。根因三连:Chat WRONGTYPE(测试 bug)/DashVector region 误写 cn-hangzhou(实为 cn-shenzhen)/长期记忆 collection 1536 维 vs embedding 1024 维(删坏集合重建+生产重启验证 dimension=1024)

## P1（**面试后解锁**——9/15 晚 Sprint 2 计划会 PO 拍板:AKB 转纯维护模式,不开新功能线）
- [ ] B-103 element-plus 按需导入（unplugin-vue-components + unplugin-auto-import），减 1MB+ 单 chunk
- [ ] B-104 后端分页（知识/文件列表服务端分页）

## P2（低优先，面试前不新开功能线）
- [ ] B-105 .doc 老格式上传支持
- [ ] B-106 统一 HTTP 连接池
- [ ] B-107 知识可视化（Obsidian 风格,9/15 晚 PO 提出升级方向）——分三档 MVP,按投入递增;**L1+L3 已完成（9/16 / 9/21）,仅剩 L2 反链**:
  - [x] **L1 树(9/16 完成)**: /tree 路由 + el-tree 懒加载两级树(知识条目→文件,复用 /knowledge 与 /file/list 接口,零后端改动),文件节点带状态标签(PROCESSING/SUCCESS/FAILED),点击直达知识详情;顺带品牌化:app.name=盐集 Distilled、index.html 标题、i18n zh/en。vue-tsc+vite 构建过,152 回归绿
  - **L2 反链（~1d）**: "反向链接"——chunk 被哪些 AI 回答引用过的溯源面板(数据现成:SearchResult.chunkId/ChatResponse 引用链路),对应 Obsidian Backlinks
  - [x] **L3 网图(9/21 完成)**: /graph 路由 + ECharts 5.6.0 力导向(Obsidian 风格)。边方案实际落地=**文件级向量相似边**(原计划共引边——chat references 未落库(B-110 未做)、DashVector 存量为 chunk 级近邻查询拿不到全量,改为文件名+首切片 500 字做 embedding 两两余弦,top-2 邻居+阈值 0.45+pairKey 去重无向边):后端 GET /api/graph(线上实况 68 节点=3 条目+65 文件/158 边=65 结构+93 相似;**DashScope text-embedding-v3 批量上限实测 10 条/请求**,EMBED_BATCH_SIZE=8 分批,embedding 失败降级空相似边图仍渲染)+前端力导向(条目配色分类图例/结构实线+相似虚线随权重/emphasis adjacency 邻居高亮/点击节点详情侧栏跳知识详情)+GraphServiceTest 8 用例;回归 162+integration 21+e2e 10=193 全绿,jar 0f27719c 部署+线上 /api/graph 验证通过;**9/21 晚结合知识树(PO 拍板)**:图体抽 GraphPanel 组件,/tree 页顶部"树形/网图"切换(defineAsyncComponent 按需加载,Tree chunk 仍 2.9KB),菜单收掉独立 /graph 入口(路由保留),dist 38341577 已发布
  - 技术备选: AntV G6/D3 更专业但重,MVP 用 ECharts;接口需新增 /api/graph 聚合端点(共引边可 MySQL 聚合 chat 引用记录)
  - 约束: 维持"面试后解锁",若面试前想展示,只做 L1 树(零后端改动)
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
  - **轻量去重（10min,可先行单独做）**: streamAsk / streamAskWithAgent 开头 5 行(检索+历史+记忆+拼 Prompt)抽私有方法,行为零变化
  - **面试价值**: "一条管线、模型自主路由"叙事优于"两个开关两条管线";流式 ReAct(边调工具边吐字)是 Agent 场景题的加分实现点
  - 约束: 面试冲刺期聊天主链路 199 全绿是护城河,动它必须整块时间窗

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
  - 面试价值: 知识库数据接入是 RAG 产品标配问点（仅文件上传偏薄）; SSRF 攻防是云安全高频题——feature+安全双向加持
  - 依赖注意: 章节树/结构感知对 md 有效，PDF 提取为裸文本; 教材类版权内容仅走线上库，永不进 git

- [ ] B-110 知识跳转（9/20 PO 提出，灵感:MC 任务 mod"缺什么→左键看怎么获得"）:
  - **交互**: 阅读文档/AI 回答时选中文本 → 指令创建跳转超链接（**仅选中部分标蓝**，非全文自动链）→ 点击跳转到知识库中解释该概念的章节/片段并高亮
  - **MVP（~3-4h，零 AI 参与，防幻觉最稳）**: 选中文本 →"在知识库定位"按钮 → 复用现有 RAG 混合检索（选中词当 query）→ 命中则跳 Detail 页 + 滚动定位高亮 chunk。目标永远来自真实检索结果
  - **进阶（+2-3h）**: AI 回答输出标记语法（如 [[概念]]），前端渲染蓝链；点击后端检索定位。**防幻觉铁律: 链接目标 chunk 必须来自真实检索，AI 只负责"判断哪些词值得链"，绝不许 AI 编造目标**
  - **定位实现**: chunk 级锚点——优先给 StructureAwareSplitter 补 offset 元数据（+1-2h）；或前端全文字符串匹配（chunk 有 overlap 需处理）
  - **一鱼两吃**: 跳转关系落库 = B-107 L3 网图天然需要"边"数据（节点=chunk，边=跳转/共引），做了 B-110 等于给 L3 铺路
  - **面试价值**: 需求来源故事极鲜活（MC 玩家视角的产品思维）;"RAG 检索当跳转定位器"是把检索复用到非问答场景的架构思考; 与 Obsidian 双链/Wikipedia 内链类比可展开
  - 约束: 投递收口后解锁（PO 9/20 意向,量级 MVP 半个开发日）
