# AI-Knowledge-Base 项目交接文档

> 更新: 2026-09-05 | 当前生产版本 = commit `385a3dd`(9/1 第六次部署:向量库不可用时服务可降级启动,@PostConstruct 兜底补齐降级最后一环;verify 3/3 + seed_demo 11/11 PASS)| 本地最新 = `ca977d0`(9/5:时间线摘要 + rerank 打磨 + 前端分包 + **file_search 用户隔离修复**,180/180 绿,**未部署**,下次部署窗口一并带上,前端注意另发 dist)

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
2. 设密码：`$env:DEPLOY_PASSWORD='密码'`（deploy.py 仅读此环境变量,不读密码文件;只在当前 shell 会话存在,不落盘）
3. `python scripts\deploy.py cmd "cp /opt/aikb/app.jar /opt/aikb/app.jar.bak-日期"` 备份
4. `python scripts\deploy.py put "target\Ai-Knowledge-Base-0.0.1-SNAPSHOT.jar" "/opt/aikb/app.jar.new"`
5. `deploy.py cmd 'mv /opt/aikb/app.jar.new /opt/aikb/app.jar && systemctl restart aikb && sleep 15 && systemctl is-active aikb && curl -s http://127.0.0.1:8080/actuator/health'`
6. `python scripts\verify_deploy.py`（注册关闭模式 3 项 PASS 即可,其余 5 项本地回归覆盖）

**坑（8/29 实测）**: ①Git Bash 下跑 deploy.py,独立路径参数会被 MSYS 改写成 Windows 路径 → SFTP 报 ENOENT（SSH cmd 不受影响,字符串里的路径没事）。Git Bash 一律前缀 `MSYS_NO_PATHCONV=1`,或回 PowerShell。②新机器需 `pip install paramiko`（历史版本 3.4.1 可用）。③前端改动要另发 dist:tar 打包 frontend/dist → put 到 /tmp → 解压到 **/var/www/aikb**（nginx 静态根,与 jar 不同目录）。

## 3. 关键提交节点

| commit | 内容 |
|---|---|
| `ca977d0` | **file_search 补用户隔离**(本地靶机实测新账号曾召回他人文件 fileId=178,横向越权;登录改走 searchForUser 与 RetrievalServiceImpl 同口径,backlog"DashVector 检索用户隔离 filter"就此闭环)+ 重排空响应(解析不出分数)降级粗排 + 工具失败文案去重;测试 +5 → 180/180 绿(9/5) |
| `5537f19` | Agent 时间线摘要友好化(ToolTraceSummarizer,5 工具 JSON→人话,失败/未知降级截断)+ rerank 分数下限淘汰(rerank.min-score 默认 0.3,全量取分本地淘汰,全淘汰返回空)+ 兜底排序方向修复(混合池分段有序,不再整体升序 sort);测试 +20 → 175/175 绿(9/5,**未部署**) |
| `de2c9d8` | 前端 vendor 三分包(element-plus/vue/markdown 独立 chunk,业务主包 1.27MB→12.6KB);HANDOFF 清除已修复的登录枚举遗留项(9/5,**未部署**,前端需发 dist) |
| `385a3dd` | 向量库不可用时服务可降级启动:VectorStoreServiceImpl/LongTermMemoryServiceImpl 的 @PostConstruct 加 try-catch(供应商故障不再阻断 Spring 启动);启动期降级测试 2 用例,155/155 绿(9/1 第六次部署) |
| `dd0b6e1` | 向量检索失败降级 BM25 兜底:主链路向量路异常退 BM25 单路,Agent file_search 与 MCP 出口返回空结果 JSON;VectorRetrievalDegradationTest 4 用例,153/153 绿(9/1 第五次部署) |
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

备注:
- file_trace 工具内部走 getFileById，现已自动继承作者校验（ReAct 测试需以作者身份执行，已改）
- （原"登录报错区分用户不存在/密码错误"遗留项已由 ab02ec1 修复:统一返回"用户名或密码错误",生产实测生效,2026-09-05 复核后移出遗留清单）

## 5. 测试体系

- 全量: `mvn test`（当前 **180/180 绿**；默认跑批排除 integration/e2e 分组，e2e 子集 scripts/test-e2e.sh 会真实调 LLM/向量库产生少量费用。180 = 9/1 基线 155 + 时间线摘要 12 + 重排分数淘汰 7 + 兜底排序 2 个新用例及 1 个用例修正 + file_search 隔离 4)
- e2e 子集: `scripts/test-e2e.sh`（@Tag("e2e")）
- 关键测试类: FileAccessControlTest / UserSelfAccessAndRegisterLimitTest / KnowledgeDeleteCascadeTest / LoginLockoutBoundaryTest / RateLimitBoundaryTest / ChatDailyQuotaTest / TokenCostCalculationTest / RetrievalQualityEvalTest / KnowledgeAddValidationTest / ToolTraceSummarizerTest / RerankScoreFilterTest / RetrievalServiceImplTest
- **约定: 任何代码改动必须全量回归全绿才可提交部署**
- 坑: MockMvc 断言中文需 `new String(resp.getBytes(ISO_8859_1), UTF_8)` 重解码；BusinessException 是 HTTP 200 + body code 500，断言要看 body 层；**本地库曾漏建 token_usage 表（recordChat 吞异常不报错，8/28 补建）——新环境初始化务必执行最新 docs/schema.sql 全量**
- 坑(9/5): WorkBuddy 终端 bash 预设 `MSYS_NO_PATHCONV=1`,Git Bash 下 `mvn`/`./mvnw` shell 脚本拼出的 `/c/...` classpath 不被转成 Windows 路径,java 报"找不到主类 org.codehaus.plexus.classworlds.launcher.Launcher"(与 8/29 deploy.py 的路径改写坑互为反面)。绕法:直接调 java 跑 Launcher——`"$JAVA_HOME/bin/java" -classpath "C:/apps/maven/apache-maven-3.9.11/boot/plexus-classworlds-2.9.0.jar" -Dclassworlds.conf="C:/apps/maven/apache-maven-3.9.11/bin/m2.conf" -Dmaven.home="C:/apps/maven/apache-maven-3.9.11" -Dmaven.multiModuleProjectDirectory="<项目Windows路径>" org.codehaus.plexus.classworlds.launcher.Launcher test`(正斜杠 Windows 路径 java 可接受);或回 PowerShell 跑 mvn
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
- [x] **DashVector 免费额度 9/6 到期——已闭环（9/1）**:三层降级代码已部署(dd0b6e1,向量挂→BM25 单路,Agent/MCP 出口空结果);阿里云账户已充值 ¥7,实例为 Serverless 按量(本站流量月均 ¥1-2,余额够撑数月),到期自动转付费无需操作。万一实例被冻结的预案:新建 Serverless Cluster→改 endpoint 环境变量→MySQL 是向量数据源,重跑入库流水线全量重建
- [x] 服务器 /opt/aikb 备份 jar 已清理（9/1 第六次部署验收通过后:删除 8/24-8/29 的 7 个旧备份 ~811MB;保留 bak-0901(无降级代码版)/bak-0901b(降级第一版)两个回滚点,/ 分区占用降至 23%）
- [ ] 前端 chunk 优化:vendor 已三分包(element-plus/vue/markdown 独立 chunk,主包 1.27MB→12.6KB,de2c9d8);element-plus 单 chunk 仍 >500kB(gzip 339KB),要再减需引入 unplugin 按需导入(加构建依赖,未做)
- [x] Agent 时间线工具结果摘要已友好化(9/5:ToolTraceSummarizer 按工具名把结果 JSON 翻译成人话,如 file_search→"检索到 N 个相关文件";解析失败/未知工具降级截断原文,回传模型的原始结果不变)
- [x] rerank 打磨完成(9/5):①分数下限淘汰——rerank.min-score 配置(默认 0.3),请求改拿全量候选分数后本地先淘汰再截 topN,全淘汰返回空让上层如实作答,置 0 关闭;②兜底排序方向修复——rerank 关闭时不再对混合池整体升序 sort(两路 score 语义相反:向量=距离、BM25=相关度,整体排序必错一路),改为保持合并顺序(向量段距离升序在前 + BM25 段相关度降序在后,各自天然有序)
- [x] 登录错误信息统一 + register.enabled（ab02ec1 已完成;该批时点回归 140 用例,当前基线 155 见第 5 节）

低优先 backlog:
- 后端分页 / .doc 老格式支持 / 统一 HTTP 连接池
- ~~DashVector 检索用户隔离 filter~~(已闭环:file_search 9/5 改走 searchForUser,ca977d0;RetrievalServiceImpl 一直是隔离的)
- 知识图谱(README 路线图已列;面试前优先"数字+故事"而非新功能)
- 简历方向: Python+LangGraph 多 Agent 复刻版(强化"场景"维度)

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
