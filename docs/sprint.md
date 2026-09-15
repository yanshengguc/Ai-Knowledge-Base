
## Sprint 1 评审会(2026-09-15 晚,PO=用户 + SM/Dev=AI)

**Sprint 目标"未部署/未验证清零"——达成。** DoD 逐项核对:

| DoD 项 | 结果 |
|---|---|
| 默认回归全绿 | ✅ 152/152 |
| verify_deploy.py PASS | ✅ 3/3(后端部署当日) |
| 线上 jar = 70e1408 | ✅ SHA256 双端一致 |
| HANDOFF.md 同步 | ✅ 头部状态+第 5 节根因全记录 |
| sprint.md 实时更新 | ✅ |

增量交付:①前端 65792fd 上线(Emoji 清理)②后端 70e1408 上线(降级健壮性)③**184 项测试全绿**(152+21+11,历史首次含真实 Redis/DashVector/LLM 的集成与 e2e)④生产长期记忆修复(1536→1024 维重建,describe 实测)⑤测试代码 2 处修正(ChatIntegrationTest/RerankSmokeTest)。commits: d749309 / 93568aa / 1eac476 / 388890c。

## 反思会(2026-09-15 晚)

**做得好(Keep)**:
- 根因定位方法论:逐层排除(裸 socket→netty→Lettuce→DBSIZE 鉴别替身;SDK 探测排除 endpoint 格式/鉴权),每个结论都有铁证
- 降级设计经受住考验:向量库/Redis 全挂时主流程无感,但也要警惕它**掩盖故障**(维度失配沉默 16 天)
- PO 一次直觉输入("深圳那个嘛")即破案——文档记录 vs 生产实配,永远信生产实配

**待改进(Improve)**:
- HANDOFF 记录与生产实配曾不一致(region 抄错)→ **行动项 A**:交接文档中的环境配置须标注"来源:生产实配核对"而非凭记忆转写
- 换 embedding 模型时未同步重建向量集合→ **行动项 B**:MEMORY-CARDS 增加"embedding 模型与 collection 维度绑定"坑位(面试可讲:降级逻辑的双刃剑)
- 测试断言漂移(全量返回 vs min-score 特性)→ **行动项 C**:新特性上线时同步审查既有 e2e 断言语义

**Sprint 2 建议(待 PO 拍板)**:主线已切面试冲刺(8/30 后 AKB ≤1h/日仅维护),建议 **AKB 转纯维护模式**,B-103/B-104(P1)延后到面试后;今日三大根因沉淀为面试素材后,主力回算法线(哈希表 217)+面试准备。
�解除,无阻塞**。Sprint 1 T-1~T-5 全闭环,待评审/反思会。

## DoD
- 默认回归全绿(152/152) · verify_deploy.py PASS · 线上 jar = 70e1408 · HANDOFF.md 同步 · sprint.md 实时更新

## 约定（继承工作流铁律）
- 任何代码改动必须全量回归全绿才可提交部署；小批次交付 → E2E 核实 → commit+push
- 严禁服务器构建(OOM)；deploy.py 只读 DEPLOY_PASSWORD 环境变量；部署走 PowerShell(MSYS 路径改写坑)
- 节点/密钥/密码不落文档不进输出
