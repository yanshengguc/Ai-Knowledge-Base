# 夜间自动巡检报告（2026-10-05）

> 生成时间：2026-10-05 00:35（Asia/Shanghai）；**订正时间：2026-10-05 00:50**
> 触发：PO 2026-10-05 睡前指令「先做需要长时间且不用我操作的任务」
> 性质：**全部为只读 / 本地构建类操作**；未改任何代码、未提交、未推送、未部署、未改生产数据
> 生产地址在本文档中以 `<SERVER_IP>` 占位（遵仓库 PII 清理约定，不落明文）

## 0. 结论速览

| # | 任务 | 结果 | 判定 |
|---|---|---|---|
| 1 | 后端全量单元回归（默认门禁） | `Tests run: 293, Failures: 0, Errors: 0, Skipped: 0` · BUILD SUCCESS · 49.4s | ✅ 绿 |
| 2 | 前端生产构建 `npm run build` | exit 0 · built in 16.85s | ✅ 绿 |
| 3 | 追加集成组（`-DexcludedGroups=e2e`） | 329 跑 / 0F / **3E**，3 个错误同因 = 向量库降级 BM25 | ⚠️ 环境受限（非回归） |
| 4 | 生产只读巡检 | 站点 200、匿名 401、demo 登录 OK、F1 接口正常、无凭据泄漏 | ✅ 全通 |
| 5 | 部署一致性核查（本地 dist vs 线上） | `index.html` SHA256 **逐位一致** | ✅ 一致 |
| 6 | F3 观察项复核（生产向量路） | 5 类 query 全部命中（4/4/3/4/1） | ✅ **F3 关闭** |
| 7 | backlog / sprint ↔ git 一致性审计 | B-124 / B-125 均已收口；B-120③ 早已完成 | ✅ 无悬空（订正见 7.3） |

## 1. 后端全量单元回归（默认门禁）

- 命令：`mvn -o test`（默认 `excludedGroups=integration,e2e`）
- 结果：**293 / 0 / 0 / 0**，BUILD SUCCESS，49.4s
- 与 F1/F2 修复版基线 **293** 一致 ⇒ 无回归。

## 2. 前端生产构建

- `npm run build` exit 0，16.85s。
- 产物 chunk `FilePreview-B4AKKtGX.js` 与线上已部署 chunk **同名**（部署留痕值一致），首屏包 `index-7a8AEwu8.js`。

## 3. 集成组追加运行（环境受限）

- 命令：`mvn -o test -DexcludedGroups=e2e`（只排 e2e，纳入 integration）
- 结果：**329 跑 / 0 Failures / 3 Errors / 0 Skipped**，BUILD FAILURE。
- 3 个错误**同一根因**：

```
java.lang.IllegalStateException: 向量库不可用，当前已降级为 BM25 检索
  at VectorStoreServiceImpl.requireCollection(VectorStoreServiceImpl.java:54)
```

| 用例 | 位置 |
|---|---|
| `ChunkIndexingIntegrationTest.testSearch` | :55 |
| `VectorSearchServiceTest.testSearch` | :31 |
| `VectorStoreDeleteTest.deleteByFileIdShouldRemoveVectors` | :50 |

- **判定**：本机公网 IP **不在 DashVector 集群白名单**（历史已知限制，SDK gRPC `Cluster whiteList validate fail`），向量库降级为 BM25 ⇒ `requireCollection` 抛异常。**非代码回归**（同批次其余 326 个用例全绿，其中含 33 个 MySQL 依赖的 integration 用例）。
- 副证：生产侧向量语义召回正常（见第 6 节 E 类 query）⇒ DashVector 白名单含生产、不含本开发机。

## 4. 生产只读巡检（HTTP，无 SSH）

| 检查 | 结果 |
|---|---|
| 站点可达 | `GET /` → **200**（HTTP） |
| 匿名契约 | `POST /api/retrieval/locate` → **401** ✅ |
| demo 登录（只读） | HTTP 200，token 长度 167 ✅ |
| **F1 线上回归** | `GET /api/file/4/content` → 200 / `code:200` / `fileType=text/markdown;source=manual` / 正文 **316 字** ✅ |
| 凭据泄漏体检 | 工作区 grep `Kb...`/`Deploy...` → **0 命中**（唯一匹配是 `HANDOFF.md:115` 的占位符文档 `DEPLOY_PASSWORD='密码'`）✅ |
| 工作区/远端 | `git status --porcelain` 空；`origin/main` 与本地 **0/0** ✅ |

**安全响应头实测（与项目留痕一致，非缺口）**

```
X-Frame-Options: SAMEORIGIN
Content-Security-Policy: frame-ancestors 'self'; object-src 'none'; base-uri 'self'
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
Referrer-Policy: (未配置，不在 B-120③ 范围内)
Permissions-Policy: (未配置，不在 B-120③ 范围内)
```

- 实测 4 头与 `docs/sprint.md` L589–599 记载的 B-120③ 交付物**逐项吻合** ⇒ **B-120③ 已于 2026-10-03 完成**（Nginx 层 `add_header ... always;`，配置不入仓库，属服务器侧变更）。
- **补充观察（新）**：`https://<SERVER_IP>/` → **443 连接被拒**，且 HTTP 无 `Location` 跳转 ⇒ 当前站点**无 TLS 监听**，故 `Strict-Transport-Security` 头**实际不生效**（未来上 HTTPS 后会自动生效，属正常预留）。

**未执行（刻意保守）**：未对生产运行 `scripts/verify_deploy.py` —— 该脚本含「注册探针」，若注册开关意外为开，会在生产**真实建号**（写操作）。夜间无人值守不承担该风险。

## 5. 部署一致性核查（本地 main HEAD 构建产物 vs 线上）

| 侧 | index.html SHA256 | 大小 |
|---|---|---|
| 本地 `frontend/dist`（本次构建） | `611112168073E372FDB1948056E129B200AA785935215C5AE852A490270327C0` | 512 B |
| 线上 `http://<SERVER_IP>/` | `611112168073E372FDB1948056E129B200AA785935215C5AE852A490270327C0` | 512 B |

- **逐位一致** ✅，且与 F4 部署留痕记录的前端 hash 完全相同 ⇒ 线上前端**无漂移**、等于本地 `main` HEAD 构建产物。
- 线上 `index.html` 引用的 3 个静态资源全部 **200**。
- **未覆盖**：后端 jar 一致性需 SSH 取线上文件比对（未做，无凭据入场，故不擅自连服务器）。

## 6. F3 观察项复核 → 结论：F3 关闭（生产不存在该缺陷）

> F3 原文：「低·环境相关 —— 长/整句选中易 0 命中（本地 BM25 单路 + rerank 0.3 下限）」

生产（向量路正常）实测 `POST /api/retrieval/locate`（`topK=5`，demo 登录态）：

| 类别 | query 长度 | 命中数 |
|---|---|---|
| A 短词「缓存击穿」 | 4 | **4** |
| B 中句「热点 key 过期瞬间大量并发回源数据库」 | 20 | **4** |
| C 长整句（带标点，60 字） | 60 | **3** |
| D 长整句（概述式，45 字） | 45 | **4** |
| E 低词面重叠（语义型）「同一时刻很多键失效把数据库压垮」 | 15 | **1** |

- 本地 BM25 单路下「长整句易 0 命中」，生产同长度 query 命中 **3–4 条** ⇒ **F3 确认为本地环境降级所致，生产无此缺陷**。
- E 类（与原文词汇几乎无重叠）仍命中 1 条 ⇒ **向量语义召回在生效**，佐证生产向量路存活（DashVector 在到期日前仍可用）。

## 7. 风险与待 PO 决策

### 7.1 【最高优先·时间敏感】DashVector 免费集群到期
- 集群 `aikb-free-2026` **到期时间：2026-10-11 19:37**（距今约 **6 天**）。
- 影响：到期后向量路失效 → 检索**降级为 BM25 单路**（有降级兜底，站点不崩，但召回质量下降，且长/整句 query 可能 0 命中，即 F3 现象会在生产复现）。
- 预案（三选一，需 PO 定）：
  1. **续期/更换集群** → 更新服务器 `/etc/aikb/aikb.env` 中的 `DASHVECTOR_*` → 重启 `aikb` → 跑一次索引一致性核查；
  2. **接受降级** → 到期前记录一份检索基线快照，降级后对比观察；
  3. **迁移到其他向量方案**（成本更高，需立项）。
- 阻塞点：查/续 DashVector 需其控制台凭据，**本轮无凭据，未做**。

### 7.2 本机 IP 未入 DashVector 白名单（长期）
- 后果：`integration` 组中 3 个 DashVector 依赖用例在本机**必然失败**；`scripts/regression.ps1` 全量模式（含 e2e）在本机同样无法全绿。
- 建议（后续任务）：开白名单，或为这些用例加「向量库不可用则 skip」的环境前置。

### 7.3 【订正】B-120③ 无缺口；B-124 / B-125 无悬空
> **本节订正了本报告初版与先前口头汇报中的错误判断**：初版称「B-120③ 实测与 backlog 记载不符、需核对」，系误引**过期的项目记忆**所致；实测与仓库留痕本已一致。

- **B-120③ 安全响应头**：`docs/sprint.md` L589–599 记载 2026-10-03 已补齐 4 头并外部验证通过；本次实测 4 头齐全 ⇒ **一致，无需收口**。backlog 中 B-120 标 `[x]` **准确**。
- **B-124 树节点标题忠实度抽样**：git 已入库 —— `afcfb35`(test) + `2734239`(docs 收口, backlog 标 `[x]` + HANDOFF) ⇒ **已收口**。sprint.md L409「待 PO 验收（未 commit/未 push/未部署）」为**当时的历史状态记录**，已被后续收口提交覆盖。
- **B-125 长期记忆静默吞错**：git 已入库 —— `27f1dc6`(fix) + `9cba8ad`(docs 收口) + `f7ad7f4`(docs 部署上线留痕) ⇒ **已提交、已推送、已部署**。sprint.md L439 同为历史状态记录。
- 结论：backlog 的状态标记与 git 实际**一致**；sprint.md 中「待验收」字样属按时间顺序留痕的历史条目，非当前状态。

### 7.4 未做项（需你操作 / 决策）
- **e2e 组未跑**：会真实调用 LLM（烧钱），未获授权不擅自跑。
- **真机原生长按选词**：需物理设备。
- **TD-001 / B-106 / `Chat.vue` 拆分**：均属需立项的重构，未擅自开工（避免夜间无人值守改生产代码）。

## 8. 未验证 / 未执行声明（如实标注）

- 未做后端 `mvn -o package` 与 jar 双端 SHA256 核对（需 SSH 凭据）。
- **TLS 侧未验证**：443 端口关闭（连接被拒），故 HTTPS 行为、证书、`http→https` 跳转均未覆盖；`Strict-Transport-Security` 当前不生效。
- 生产巡检为**单次抽样**（非持续监控）；`/actuator/health` 被 Nginx SPA 兜底返回 index.html（说明 actuator 未对外暴露，属安全正面，但也意味着无法从外部读服务健康）。
- 集成组 3 个错误为环境受限，**未修复、未绕过、未改动测试代码**。
- 一致性审计为**抽样式**（聚焦 B-120③ / B-124 / B-125），未逐条复核 backlog 全部条目。

## 9. 证据文件

| 文件 | 内容 |
|---|---|
| `%TEMP%\aikb-night\probe.json` | 生产巡检：安全头、401 契约、登录、F1 接口、F3 五类 query 命中数 |
| `%TEMP%\aikb-night\deploy_match.json` | 本地/线上 index.html SHA256、资源可达性 |
| `%TEMP%\aikb-night\https_probe.json` | HTTPS 443 连接被拒、HTTP 无跳转 |
| 单元回归日志 | 后台 job `job-8736818ee5944e449e216e48261e2943` output.log |
| 集成组日志 | 后台 job `job-0629c4b18bec45fb9d066498ff1c16cd` output.log |
| 前端构建日志 | 后台 job `job-6129c45a0ce64a249994b502f0c8be6b` output.log |
