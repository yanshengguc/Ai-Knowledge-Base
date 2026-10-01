# Sprint 2 · 目标：维护期收口——线上与本地对齐，未部署改动清零

> 补记 2026-10-01（原文件停在 9/15 Sprint 1 评审会；9/16~9/30 十余轮交付未落盘，本次按 Scrum 协议回填）

## 角色分工
- PO：用户（AI 起草）
- SM：TraeCode 会话
- Dev：TraeCode 会话（一体模式）

## 任务
状态：[ ] 待开发 · [~] 开发中 · [R] 待评审 · [Q] 待验证 · [x] 完成 · [!] 阻塞

### 已完成（9/16~9/30，均已上线；本次回填记录）
- [x] T-1 B-107 L1 知识树 `/tree`（9/16，152 回归绿）
- [x] T-2 盐集 Distilled 品牌版（9/18，`163694d` 构建，回滚点 `aikb.bak-20260918-distilled`）
- [x] T-3 B-107 L3 知识图谱 `/graph` ECharts 力导向（9/21，`c3d2b4b`，193 全绿）
- [x] T-4 B-112 文件在线预览（9/21 晚，`90b7a57`，199 全绿，jar `12e7adc3` / dist `38fe3605`）
- [x] T-5 树/图导航分离 + 图谱节点跳知识树（9/21 `8449fb4` / 9/29 `8424fb8`，168 回归绿）
- [x] T-6 前端韧性：树与预览错误态、树分支局部失败重试、请求竞态保护（9/29 `8647665`、9/30 `c767e73` / `16af5c1`）

### 本轮（2026-10-01）
- [x] T-7 回填 sprint.md + 同步 HANDOFF 状态
- [x] T-8 后端发布：HEAD `76ba7cf` jar 已上线（SHA256 `39e6c765…27819` 双端一致，179/179 回归绿，verify_deploy 3/3 PASS，回滚点 `/opt/aikb/app.jar.bak-20261001-pre-model-switch`）
- [x] T-9 前端发布：`76ba7cf` dist 已上线（tar SHA256 `7f8e5517…4be55`，index `index-hQXR4-7W.js`，Tree/Graph/Layout/FilePreview/Chat/vendor 资源全 200，回滚点 `/var/www/aikb.bak-20261001-76ba7cf`）
- [!] T-10 B-114 真实语料只读回填对齐（阻塞，见下）

## 阻塞
- [!] **B-114 只读回填一致性门槛阻断**：旧 `StructureAwareSplitter` 与 `MarkdownOutlineParser` 语义不一致（含围栏样例 3 chunk vs 1 标题；多级标题样例 2 chunk vs 3 标题）
  - 缺什么：本地无 mysql CLI / pymysql，摸不到真实库内 `knowledge_chunk`
  - 需要谁：PO 提供只读数据通道（装 mysql 客户端，或允许走 SSH 只读脚本）
  - 语义已定（9/30 B 方案）：Outline 仅作导航层；无正文父标题不生成空 Chunk；`source_chunks` 只关联真实正文 Chunk

## 本次发布的夹带项（显式留痕，不悄悄带过）
- `StructureAwareSplitter` 行为变更：`933c31f` 围栏内伪标题不再开章节、`d835b6e` 输出统一 LF
  - 决定：随 HEAD 一并上线
  - 理由：保住"线上 jar = main HEAD"可追溯铁律；cherry-pick 剥离会造出偏离 main 的生产构建，代价更大
  - 影响面：仅影响**新上传** md 的切片；不改接口、不改库内存量数据
  - 已知代价：库内将并存"旧口径存量 chunk / 新口径新增 chunk"，B-114 对齐需按新口径重跑

## DoD
- 默认回归全绿（排除 integration/e2e）· jar/dist SHA256 双端一致 · verify_deploy PASS · 回滚点就绪 · HANDOFF 与 sprint.md 同步

## 约定（继承工作流铁律）
- 任何代码改动必须全量回归全绿才可提交部署；小批次交付 → E2E 核实 → commit+push
- 严禁服务器构建(OOM)；deploy.py 只读 DEPLOY_PASSWORD 环境变量；部署走 PowerShell(MSYS 路径改写坑)
- 节点/密钥/密码不落文档不进输出
- 新增（本次反思会）：授权范围外的改动若因技术原因必须夹带，必须在本文件"夹带项"区显式留痕

## 反思会（2026-10-01）
- Keep：9/16~9/29 连续多轮前端交付零回滚，每轮专项 + 全量回归 + 回滚点齐备
- Improve：sprint.md 停更 15 天（9/15→10/1），交付只记在 HANDOFF → 行动项：把"sprint.md 实时落盘"钉进每轮收口清单
- Improve：未部署积压到 36 个提交，含影响线上行为的模型切换 → 行动项：Sprint 目标必须显式包含"未部署清零"