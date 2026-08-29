# AI-Knowledge-Base 项目交接文档

> 更新: 2026-08-29 | 当前生产版本 = commit `a49efeb`(8/29 第四次部署:失败路径二级故障兜底)

## 1. 项目概览

个人 AI 知识库（RAG + Agent），前后端同仓库：
- 仓库: `C:\Users\yansheng\IdeaProjects\Ai-Knowledge-Base`
- 后端: Java 17 + Spring Boot 3.3.4 + MyBatis + MySQL 8 + Redis + DashVector(向量库) + 阿里云 OSS + DashScope LLM(qwen 系列, V4-Flash)
- 前端: Vue 3 + TypeScript + Vite + Element Plus（`frontend/` 目录，v-html 渲染 markdown 已套 DOMPurify）
- 访问: http://120.55.76.141 （Nginx 静态 + 反代 /api → 127.0.0.1:8080）

## 2. 生产环境

| 项 | 值 |
|---|---|
| 服务器 | 阿里云 ECS 120.55.76.141, cn-hangzhou, 2C2G, Ubuntu 22.04, 年付至 2027-08-24 |
| 服务 | systemd `aikb`（`java -Xmx512m -jar /opt/aikb/app.jar`, env 在 `/etc/aikb/aikb.env`） |
| MySQL | 库 ai_knowledge_base, 用户 aikb, buffer_pool=128M, performance_schema=OFF |
| 4GB swap | 有；**严禁在服务器上 mvn package / npm build**（会 OOM），本地构建后传 jar |

**部署流程**（scripts/deploy.py, paramiko SSH）:
1. 本地 `mvn package -DskipTests`（注意：本地若跑着 56382 靶机会锁 jar，先停）
2. 设密码：`Set-Content "$env:USERPROFILE\.aikb_deploy_pass" '密码' -NoNewline`；设 `$env:DEPLOY_PASSWORD`
3. `python scripts\deploy.py cmd "cp /opt/aikb/app.jar /opt/aikb/app.jar.bak-日期"` 备份
4. `python scripts\deploy.py put "target\Ai-Knowledge-Base-0.0.1-SNAPSHOT.jar" "/opt/aikb/app.jar.new"`
5. `deploy.py cmd 'mv /opt/aikb/app.jar.new /opt/aikb/app.jar && systemctl restart aikb && sleep 15 && systemctl is-active aikb && curl -s http://127.0.0.1:8080/actuator/health'`
6. `python scripts\verify_deploy.py`（注册关闭模式 3 项 PASS 即可,其余 5 项本地回归覆盖）
7. 用完删密码文件 `Remove-Item "$env:USERPROFILE\.aikb_deploy_pass"`（**现已删除，下次部署需重新写入**）

**坑（8/29 实测）**: ①Git Bash 下跑 deploy.py,独立路径参数会被 MSYS 改写成 Windows 路径 → SFTP 报 ENOENT（SSH cmd 不受影响,字符串里的路径没事）。Git Bash 一律前缀 `MSYS_NO_PATHCONV=1`,或回 PowerShell。②新机器需 `pip install paramiko`（历史版本 3.4.1 可用）。③前端改动要另发 dist:tar 打包 frontend/dist → put 到 /tmp → 解压到 **/var/www/aikb**（nginx 静态根,与 jar 不同目录）。

## 3. 最近四笔提交（本次会话产出）

| commit | 内容 |
|---|---|
| `4dbf4f3` | 长期记忆治理：去重(相似度阈值)/过期(TTL)/容量上限/超长截断，配置化于 application.properties（memory.governance.*） |
| `3265dd1` | 测试加固：新增 27 个 P0 用例；删除 9 个零断言假测试(FileSearchChainVerify 等 5 个类)；一键回归脚本 |
| `f0c2c2a` | 空密码可注册登录漏洞修复（register 非空校验） |
| `8a32b09` | 安全加固 5 漏洞：文件接口 IDOR×2（getFileById/listByKnowledgeId 补作者归属校验）、用户接口 IDOR（/user/{id} 仅自查）、标题 XSS 入库净化（script 块连内容删）、注册按 IP 限流 5 次/分（RateLimitService 支持通用身份维度）；getUserById 回填 id |

## 4. 安全状态（攻防实测）

工具: `scripts/security_attack.py`，24 项检查，退出码=漏洞数。
**当前: 6 个漏洞全部修复，生产实测拦截正常。**

运行方式（本地靶机为例）:
```
# 起靶机: java -jar target\*.jar --server.port=56382 --spring.profiles.active=local
$env:ATTACK_BASE="http://127.0.0.1:56382/api"   # 必须带 /api 后缀！
python scripts\security_attack.py
```

遗留观察项（低风险，未修）:
- 登录报错区分"用户不存在/密码错误"（可枚举账号名）——修法：统一返回"用户名或密码错误"
- file_trace 工具内部走 getFileById，现已自动继承作者校验（ReAct 测试需以作者身份执行，已改）

## 5. 测试体系

- 全量: `mvn test`（当前 **146/146 绿**；默认跑批排除 integration/e2e 分组，e2e 子集 scripts/test-e2e.sh 会真实调 LLM/向量库产生少量费用）
- e2e 子集: `scripts/test-e2e.sh`（@Tag("e2e")）
- 关键测试类: FileAccessControlTest / UserSelfAccessAndRegisterLimitTest / KnowledgeDeleteCascadeTest / LoginLockoutBoundaryTest / RateLimitBoundaryTest / ChatDailyQuotaTest / TokenCostCalculationTest / RetrievalQualityEvalTest / KnowledgeAddValidationTest
- **约定: 任何代码改动必须全量回归全绿才可提交部署**
- 坑: MockMvc 断言中文需 `new String(resp.getBytes(ISO_8859_1), UTF_8)` 重解码；BusinessException 是 HTTP 200 + body code 500，断言要看 body 层；**本地库曾漏建 token_usage 表（recordChat 吞异常不报错，8/28 补建）——新环境初始化务必执行最新 docs/schema.sql 全量**
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
- [ ] 服务器 /opt/aikb 下 6 个备份 jar（~700MB），稳定运行几天后清理
- [ ] 前端 chunk >500kB 警告（vite 构建提示,可做 manualChunks 分包,非紧急）
- [ ] 备选小打磨:Agent 时间线的工具结果摘要目前是原始 JSON,可按工具定制友好文案
- [x] 登录错误信息统一 + register.enabled（ab02ec1 已完成;默认回归现为 140 用例 = 旧 137 基线 + 本批新增 3 个）

低优先 backlog:
- 后端分页 / .doc 老格式支持 / DashVector 检索用户隔离 filter / 统一 HTTP 连接池
- 知识图谱（README 路线图已列;面试前优先"数字+故事"而非新功能）
- 简历方向: Python+LangGraph 多 Agent 复刻版（强化"场景"维度）

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
# 全量回归（改代码后必跑）
mvn test

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
