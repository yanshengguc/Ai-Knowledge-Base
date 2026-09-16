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
- [ ] B-107 知识可视化（Obsidian 风格,9/15 晚 PO 提出升级方向）——分三档 MVP,按投入递增:
  - [x] **L1 树(9/16 完成)**: /tree 路由 + el-tree 懒加载两级树(知识条目→文件,复用 /knowledge 与 /file/list 接口,零后端改动),文件节点带状态标签(PROCESSING/SUCCESS/FAILED),点击直达知识详情;顺带品牌化:app.name=盐集 Distilled、index.html 标题、i18n zh/en。vue-tsc+vite 构建过,152 回归绿
  - **L2 反链（~1d）**: "反向链接"——chunk 被哪些 AI 回答引用过的溯源面板(数据现成:SearchResult.chunkId/ChatResponse 引用链路),对应 Obsidian Backlinks
  - **L3 网图（~2-3d）**: 力导向图(ECharts graph force,零新增生态)——节点=文件/chunk,边=**共引关系**(同一回答引用过的 chunk 之间连边,免 LLM 实体抽取,成本最低的图谱);支持点节点高亮邻居(Obsidian local graph 交互)
  - 技术备选: AntV G6/D3 更专业但重,MVP 用 ECharts;接口需新增 /api/graph 聚合端点(共引边可 MySQL 聚合 chat 引用记录)
  - 约束: 维持"面试后解锁",若面试前想展示,只做 L1 树(零后端改动)
- [ ] B-108 Python+LangGraph 多 Agent 复刻版（简历方向，独立仓库，不在本仓库 Sprint 内）

## 已完成
- [x] B-000 前端 Emoji 清理 `65792fd` 部署+线上验收（2026-09-15,3/3 PASS,回滚点 bak-20260915-emoji）
- [x] B-000 移动端智能问答体验发布（2026-09-12,5e617a0）
- [x] B-000 后端默认回归恢复全绿 152/152（2026-09-15,70e1408）
- [x] B-000 DashVector 新免费集群恢复+27 切片重建（2026-09-12）
