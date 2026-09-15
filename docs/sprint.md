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
- [ ] T-5 (条件)Redis/DashVector 就绪则跑 integration/e2e 剩余 28 项,冲 180 全绿 @Dev（←B-102,依赖 T-1）
  - 进展:9/15 首跑 integration 组 21 项 = 6F+9E,**根因定位完毕(见阻塞区),待用户解锁两个环境前置后重跑;e2e 子集未跑**

## 阻塞
- **T-5 两大环境根因(9/15 实测定位,21 项失败 0 代码缺陷)**:
  1. **127.0.0.1:6379 是 Python RESP 测试替身,不是真 Redis**(证据:PING→+PONG 但 DBSIZE→+OK、INFO 超时;真 Redis 必回 :N。HANDOFF 8/28 记录的"临时替身"仍在运行)→ 影响 15 项 + Chat 测试 JWT 连锁
  2. **DashVector 本机不可达**(向量库不可用已降级 BM25,降级逻辑工作正常)→ 影响 3 项 Vector 类;TUN 全局接管流量,阿里云看到境外出口 34.228.66.24(AWS),不在白名单
- 解锁动作(全在用户侧,WSL 被沙箱拦):
  ① WSL 里停替身进程 + `service redis-server start` 起真 Redis
  ② DashVector 白名单加本机出口 IP(关 TUN 后重查:直连出口=宽带 IP;或把 34.228.66.24 加白)
- 已尝试无效:host=127.0.0.1/preferIPv4Stack/超时 5s(替身该卡还是卡,根因不在网络参数)

## DoD
- 默认回归全绿(152/152) · verify_deploy.py PASS · 线上 jar = 70e1408 · HANDOFF.md 同步 · sprint.md 实时更新

## 约定（继承工作流铁律）
- 任何代码改动必须全量回归全绿才可提交部署；小批次交付 → E2E 核实 → commit+push
- 严禁服务器构建(OOM)；deploy.py 只读 DEPLOY_PASSWORD 环境变量；部署走 PowerShell(MSYS 路径改写坑)
- 节点/密钥/密码不落文档不进输出
