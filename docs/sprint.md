# Sprint 1 · 目标：线上与本地版本对齐，集成测试环境就绪度探明（"未部署/未验证"清零）

> 部署模式:一体模式(本会话 AI 兼任 SM+Dev,用户任 PO 拍板) | 开始:2026-09-15 | 依据:HANDOFF.md + docs/backlog.md

## 角色分工
- PO：用户（AI 起草，关键决策拍板）
- SM：会话-Kernel
- Dev：会话-Kernel

## 任务
- [x] T-1 环境就绪度探测:Redis 127.0.0.1:6379 **OPEN**(就绪)、56382 端口空闲、application-local.properties 含 dashvector.api-key/endpoint(白名单待实测) @SM
- [x] T-2 本地全量默认回归复跑确认 **152/152 全绿**(9/15 14:32,BUILD SUCCESS,1m4s) @Dev
- [x] T-3 后端 `70e1408` 构建打包:fat jar 117MB(14:33:05,BUILD SUCCESS,56382 无锁) @Dev（←B-101）
- [x] T-4 部署后端 jar 到生产:备份 bak-20260915-backend → 上传 117MB → 重启 active+UP → **双端 jar SHA256 一致(8a59eb15…7d85,=70e1408 产物)** → verify_deploy 3/3 PASS @Dev（←B-101 已闭环）
- [x] T-5 (条件)Redis/DashVector 就绪则跑 integration/e2e 剩余 28 项,冲 180 全绿 @Dev（←B-102 **已闭环**）
  - **9/15 晚达成: integration 21/21 + e2e 11/11 + 默认回归 152/152 = 184 项全绿,BUILD SUCCESS**
  - 收尾链路:①ChatIntegrationTest WRONGTYPE 修复(测试 bug:opsForValue 读 List key → 改 opsForList().range 对齐主代码);②DashVector region 根因:集群实际在 cn-shenzhen,本地配置/文档误写 cn-hangzhou → `Inexistent Cluster`(生产 env 一直是正确的深圳);③长期记忆维度根因:collection 1536 维(8 月 v2 时代)vs 现 embedding 1024 维 → 删坏集合自动重建,顺带发现并修复"生产长期记忆自上线即静默降级"的隐患;④RerankSmokeTest 断言对齐 min-score 特性
  - 遗留:**已清零(9/15 晚)**——生产 aikb 已重启(active+UP,启动 11.5s),init() 自动重建 long_term_memory 为 1024 维(describe 实测确认),长期记忆功能线上恢复

## 阻塞
- ~~T-5 环境依赖~~ **全部解除,无阻塞**。Sprint 1 T-1~T-5 全闭环,待评审/反思会。

## DoD
- 默认回归全绿(152/152) · verify_deploy.py PASS · 线上 jar = 70e1408 · HANDOFF.md 同步 · sprint.md 实时更新

## 约定（继承工作流铁律）
- 任何代码改动必须全量回归全绿才可提交部署；小批次交付 → E2E 核实 → commit+push
- 严禁服务器构建(OOM)；deploy.py 只读 DEPLOY_PASSWORD 环境变量；部署走 PowerShell(MSYS 路径改写坑)
- 节点/密钥/密码不落文档不进输出
