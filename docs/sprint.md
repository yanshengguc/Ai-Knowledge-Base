# Sprint 3 · 目标：管理端 MVP（只读）——配置驱动 admin 鉴权 + 管理概览 + 用户列表，上线可演示

> 开于 2026-10-01 | 计划会由 SM 主持，PO 拍板范围为选项 A（只读 MVP，DDL 与写操作推迟到下一轮）
> 收于 2026-10-01 | 目标达成：后端 jar + 前端 dist 已上线，回滚点齐备

## 角色分工
- PO：用户（AI 起草）
- SM：TraeCode 会话
- DEV：TraeCode 会话（一体模式）

## 本轮范围（计划会第 5 步：划定边界）

**允许改**
- 后端：`application.properties` 新增 `admin.usernames` 配置项；新增 `mapper/AdminMapper`(+xml)、`service/AdminService`(+impl)、`controller/AdminController`、`vo/AdminOverviewVO`、`vo/AdminUserVO`、`vo/AdminUserPageVO`、`vo/UserProfileVO`；`UserController` 新增 `GET /api/user/me`
- 前端：`api/modules/admin.ts`、`api/modules/user.ts` 增 `getMe`、`stores/user.ts` 增 `isAdmin`、`router/index.ts` 增 `/admin` 守卫、`views/admin/Admin.vue`、`layouts/Layout.vue` 增导航项、`locales/zh.ts` `locales/en.ts` 增词条

**不许碰**
- 聊天/检索/切片主链路（ChatController、RetrievalService、StructureAwareSplitter、MarkdownOutlineParser）
- 任何建表/改表语句（DDL 一律不做，`docs/schema.sql` 不动）
- 现有接口的响应结构与鉴权口径（`/api/user/{id}` 不变，仅在 `/me` 上新增）

## 任务
状态：[ ] 待开发 · [~] 开发中 · [R] 待评审 · [Q] 待验证 · [x] 完成 · [!] 阻塞

- [x] T-1 后端 admin 鉴权底座（←B-115）：`admin.usernames` 配置 + `AdminService.requireAdmin()` + `GET /api/user/me`（带 admin 标识）· Dev 交付：AdminServiceImpl + UserController./me，专项 AdminServiceTest 10/10
- [x] T-2 后端只读聚合：`GET /api/admin/overview` + `GET /api/admin/users?page&size` · Dev 交付：AdminMapper(+xml)/AdminController/4 个 VO，专项 AdminServiceTest 10/10（含拒绝发生在查库之前、分页收敛、无 password 字段）
- [x] T-3 前端管理端：`/admin` 路由与守卫（非 admin 拦截）+ 概览与用户列表页 + 导航项仅 admin 可见 · Dev 交付：admin.ts/user.ts/store/router/Admin.vue/Layout.vue/zh+en 词条，vue-tsc + vite 构建通过（Admin chunk 5.25 kB）
- [x] T-4 回归：默认回归 **189/189** 绿（179+AdminServiceTest 10）· AdminMapperIntegrationTest **5/5** 绿（连真实本地 MySQL 实跑四个只读 SQL）· 前端构建通过 · **集测整组未跑**（本机 Redis 未启动，属环境性 NO-GO，非本次改动引入）· e2e 未跑（真实 LLM 烧钱，按 DoD 不要求）
- [x] T-5 部署上线 + verify + 回滚点（后端 jar / 前端 dist）· 验证：jar SHA256 双端一致 `3e10c9e9…afe4803`；dist SHA256 双端一致 `1eef2b2a…ecc0289c`；`verify_deploy` **3/3 PASS**；`/actuator/health` 200；匿名与坏 token 访问 `/api/admin/overview`、`/api/admin/users`、`/api/user/me` 均 **401**；`/`、`/admin`、全部新资源 HTTP **200**；进程环境已含 `ADMIN_USERNAMES` · 回滚点：`app.jar.bak-20261001-pre-admin-mvp`、`aikb.env.bak-20261001`、`/var/www/aikb.bak-20261001-192125`
- [x] T-6 收口：sprint.md / backlog.md / HANDOFF.md 同步（2026-10-01）

## 阻塞
- [x] ~~部署输入未就绪~~ → **已解除**：PO 提供 `DEPLOY_HOST`/`DEPLOY_USER`/`DEPLOY_PASSWORD`；`ADMIN_USERNAMES` 已写入服务器 `/etc/aikb/aikb.env`（原文件已备份，值不落档）并重启生效，进程环境变量已实测确认
- [!] **admin 端点已登录态 HTTP 验证缺口（本 Sprint 遗留）**：生产 `REGISTER_ENABLED=false` 无法自建账号，且无现有账号口令，故「管理员 token → 200」「非管理员 token → 业务拒绝」两条路径**未做 HTTP 实测**；逻辑层已由 AdminServiceTest（白名单命中 / 拒绝先于查库）覆盖。解锁条件=PO 提供可登录测试账号
- [!] **本地集测组跑不起来（环境依赖，非本次改动）**：本机 MySQL 3306 通，但 Redis 6379 未监听、无 Docker Desktop/Memurai，所有 @Tag("integration") 用例在登录/限流处失败（RegisterSwitchTest 2 项中 1 项因 Redis 拒绝连接失败）
- [!] **B-114 只读回填一致性门槛**（不排入本 Sprint）：本地无 mysql CLI/pymysql，摸不到真实 `knowledge_chunk`；解锁条件=PO 提供只读数据通道

## DoD
- 默认回归全绿（排除 integration/e2e）· 非 admin 调用 `/api/admin/*` 返回业务拒绝且不泄露数据 · jar/dist SHA256 双端一致 · verify_deploy PASS · 回滚点就绪 · HANDOFF 与 sprint.md 同步

## 约定（继承工作流铁律 + Sprint 2 反思会行动项）
- 任何代码改动必须全量回归全绿才可提交部署；小批次交付 → E2E 核实 → commit+push
- 严禁服务器构建(OOM)；deploy.py 只读 `DEPLOY_HOST`/`DEPLOY_USER`/`DEPLOY_PASSWORD` 环境变量
- 节点/密钥/密码/管理端用户名名单不落文档不进输出（`ADMIN_USERNAMES` 只写进服务器 env 文件）
- 授权范围外的改动若因技术原因必须夹带，必须在本文件"夹带项"区显式留痕
- **sprint.md 实时落盘**（Sprint 2 反思会行动项：状态一变就更新，不攒到收口）
- **Sprint 目标必须显式包含"未部署清零"**（Sprint 2 反思会行动项：本轮 T-5 即为此项）

## 夹带项（显式留痕）
- 暂无

## 反思会（Sprint 3 · 2026-10-01 收口）
- Keep：六闸门探针前置（Redis 缺失在开工即暴露，未污染功能判断）；零 DDL 设计（规避本地无 mysql CLI 的验证盲区）；回滚点三件套齐备
- Improve（→ 写入约定）：**收口必须区分"逻辑层已证"与"HTTP 层已验证"**，本轮 admin 正向鉴权只到单测层，收口报告须显式标注，禁止用单测结果冒充端到端验证
- Improve（→ 写入约定）：**凭据类输入在阻塞区只登记"需要哪些键"，不登记值**，减少往返