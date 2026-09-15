# 产品待办（Product Backlog）

> 来源:HANDOFF.md(2026-09-15)待办与低优先 backlog 整理 | 维护者:PO=用户(AI 起草) | 更新:2026-09-15

## P0（本 Sprint 候选）
- [ ] B-101 后端 `70e1408` 部署上线（Redis 降级回源/DashVector 防 NPE/外部依赖测试隔离），消除线上(5e617a0 时代的后端)与本地不一致
- [ ] B-102 真实 Redis + DashVector 白名单环境下跑剩余 28 项 integration/e2e，目标历史 180 项全绿（依赖：WSL Redis 起服务、DashVector 本机白名单）

## P1
- [ ] B-103 element-plus 按需导入（unplugin-vue-components + unplugin-auto-import），减 1MB+ 单 chunk
- [ ] B-104 后端分页（知识/文件列表服务端分页）

## P2（低优先，面试前不新开功能线）
- [ ] B-105 .doc 老格式上传支持
- [ ] B-106 统一 HTTP 连接池
- [ ] B-107 知识图谱（README 路线图项，面试前优先"数字+故事"）
- [ ] B-108 Python+LangGraph 多 Agent 复刻版（简历方向，独立仓库，不在本仓库 Sprint 内）

## 已完成
- [x] B-000 前端 Emoji 清理 `65792fd` 部署+线上验收（2026-09-15,3/3 PASS,回滚点 bak-20260915-emoji）
- [x] B-000 移动端智能问答体验发布（2026-09-12,5e617a0）
- [x] B-000 后端默认回归恢复全绿 152/152（2026-09-15,70e1408）
- [x] B-000 DashVector 新免费集群恢复+27 切片重建（2026-09-12）
